package com.cadence.steps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.data.ProfileState
import com.cadence.data.ProfileStore
import com.cadence.data.StepDatabase
import com.cadence.data.StepDayEntity
import com.cadence.MainActivity
import com.cadence.domain.Profile
import com.cadence.domain.Sex
import com.cadence.domain.computeDay
import com.cadence.widget.StepsWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service that counts steps from the hardware TYPE_STEP_COUNTER sensor, or from the
 * accelerometer when no step counter exists.
 *
 * Three paths, kept apart:
 * - Sensor path (main thread, no storage I/O): updates [LiveSteps] on every event and records
 *   the newest raw reading. This is what the UI shows, so it is never delayed by storage.
 * - Consumer (one coroutine): writes today's row to Room at most every [STORE_MIN_INTERVAL_MS],
 *   and once more when the service stops. The raw counter prefs are updated only after a
 *   successful write, so an unsaved step is counted again after a crash.
 * - Notification: a foreground notification showing today's steps against the daily goal,
 *   refreshed at most once per second from [LiveSteps].
 *
 * Permission: ACTIVITY_RECOGNITION is required. If it is missing, the service stops itself
 * without crashing. The UI requests the permission; this service does not.
 *
 * Missing sensor: the service stops itself and sets "sensor_missing" in the tracker
 * SharedPreferences so the UI can explain why counting is unavailable.
 */
/** Shortest time between two home-widget refresh broadcasts, in milliseconds. */
private const val WIDGET_MIN_INTERVAL_MS: Long = 30_000L

class StepCounterService : Service(), SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Wake-ups for the consumer. CONFLATED: many wake-ups collapse into one pending signal. */
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var listenerRegistered = false
    private var accelSource: AccelStepSource? = null
    private var sensorFree = false
    private var consumerJob: Job? = null
    private var tickerJob: Job? = null
    private var notifyJob: Job? = null

    // Walking pace and vehicle check. Touched only on the main thread.
    private val walkingPace = WalkingPace()

    // GPS stride learning and recent accelerometer step times for the cadence. Main thread only,
    // except learnedCm, which the consumer reads when it computes the stored distance.
    private val strideStore by lazy { StrideStore(this) }
    private var strideEstimator = StrideEstimator()
    private val recentStepMillis = ArrayDeque<Long>()
    @Volatile private var learnedCm: Double? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var lastPlan: ServicePlan? = null

    // Notification helpers. Main thread only.
    private var lastNotifiedSteps: Int? = null
    private var ringPercent: Int = -1
    private var ringIcon: android.graphics.Bitmap? = null

    /** True from a vehicle start until counting resumes: the step sources are off. */
    private var vehiclePaused = false

    /** Start time of a vehicle detected on the main thread, handled by the consumer. */
    private val pendingVehicleStart = AtomicReference<Long?>(null)

    // Step counts for today. Written by the sensor path (main thread) and read by the consumer,
    // so every field below is guarded by countLock.
    private val countLock = Any()

    /** The day (yyyy-MM-dd) the counts belong to. */
    private var liveDay: String? = null

    /** Stored total for today when counting started. 0 after a day change. */
    private var storedBase = 0L

    /** Part of [storedBase] that the hardware counter had not yet reached (see [readExcessFor]). */
    private var excessBase = 0L

    /** Steps from the hardware counter since the start of today. */
    private var counterSteps = 0L

    /**
     * Accelerometer steps since the hardware counter last reported. These are the provisional
     * lead shown above the counter total (see [displayTotal]). Reset by every counter event.
     */
    private var accelUnmatched = 0L

    /**
     * True when the hardware step counter is the step source (plan COUNT). False on devices with
     * no step counter, where the accelerometer is the only source and its steps count directly.
     */
    private var counterActive = false

    /** Newest hardware reading. Deltas are taken against it. */
    private var sensorRaw: Long? = null

    /** When the hardware counter last reported, or counting started. Read under [countLock]. */
    private var lastCounterMillis = 0L

    @Volatile private var started = false
    @Volatile private var destroyed = false

    // Consumer-only state. Only used inside the consumer coroutine.
    /** What the last successful write stored: the day, the total and the raw reading. */
    private var flushedDay: String? = null
    private var flushedTotal: Int? = null
    private var flushedRaw: Long? = null
    private var lastWriteMillis = 0L
    private var lastWidgetBroadcastMillis = 0L

    /** Set when the app is swiped away: the next consumer pass stores pending steps at once. */
    @Volatile private var forceFlush = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // The step counter only reports while the processor is awake. The lock is taken only
        // while counting, with a timeout that is renewed on each event.
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
        strideEstimator = strideStore.load()
        learnedCm = strideEstimator.usableCm()

        consumerJob = scope.launch {
            for (ignored in wakeups) {
                try {
                    val now = System.currentTimeMillis()
                    val force = forceFlush
                    forceFlush = false
                    if (force || shouldWriteNow(lastWriteMillis, now)) flush(now)
                    // Runs after the flush, so the rollback sees every step counted before the stop.
                    pendingVehicleStart.getAndSet(null)?.let { rollBackSince(it, now) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to store steps", e)
                }
            }
            // The channel was closed because the service stops: store the final value once.
            try {
                flush(System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to store steps on stop", e)
            }
        }

        // Wakes the consumer every interval so pending steps are stored within one interval.
        tickerJob = scope.launch {
            while (isActive) {
                delay(STORE_MIN_INTERVAL_MS)
                wakeups.trySend(Unit)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val plan = servicePlan(
            hasPermission = hasActivityRecognition(this),
            hasSensor = stepSensorState(this) is StepSensorState.Available,
            hasAccelerometer = hasAccelerometer(this),
        )
        prefs().edit().putBoolean(KEY_SENSOR_MISSING, plan == ServicePlan.NO_SENSOR).apply()

        // Android requires startForeground after startForegroundService, even when the
        // service is about to stop (STEPW-23). So the notice is posted first, then we stop.
        try {
            startForegroundCompat(
                when (plan) {
                    ServicePlan.COUNT -> buildNotification(LiveSteps.steps.value)
                    ServicePlan.ACCELEROMETER -> {
                        sensorFree = true
                        buildNotification(LiveSteps.steps.value)
                    }
                    else -> buildNoticeNotification(plan)
                },
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot start foreground service; stopping", e)
            stopSelf()
            return START_NOT_STICKY
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Foreground start not allowed; stopping", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (plan != ServicePlan.COUNT && plan != ServicePlan.ACCELEROMETER) {
            // Not counting: clear the flag so the app does not claim counting is on.
            Log.w(TAG, "Not counting steps: $plan")
            prefs().edit().putBoolean(KEY_COUNTING_ON, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        lastPlan = plan
        if (!started) {
            started = true
            beginCounting(plan)
        } else if (vehiclePaused) {
            // The user pressed record again after a vehicle stop. Detection starts fresh.
            vehiclePaused = false
            walkingPace.reset()
            beginCounting(plan)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The app was swiped away from recents. Counting keeps going: the foreground service and
     * its notification stay up, so the service is not stopped here. Pending steps are stored
     * now, so the swipe loses nothing.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (started && !destroyed) {
            forceFlush = true
            wakeups.trySend(Unit)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val now = System.currentTimeMillis()
        val raw = event.values[0].toLong()
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        // Diagnostics are written before the count, so events are visible even if storage fails.
        recordEventDiagnostics(now, raw)
        val live = synchronized(countLock) {
            rollDayLocked(now)
            counterSteps += StepDelta.nextDelta(sensorRaw, raw)
            sensorRaw = raw
            // The counter has reported, so the accelerometer lead is matched up to here.
            accelUnmatched = 0L
            lastCounterMillis = now
            liveLocked()
        }
        LiveSteps.reset(live)
        wakeups.trySend(Unit)
    }

    private fun recordEventDiagnostics(nowMillis: Long, raw: Long) {
        val prefs = prefs()
        val received = prefs.getLong(KEY_EVENTS_RECEIVED, 0L) + 1L
        prefs.edit()
            .putLong(KEY_LAST_EVENT_MILLIS, nowMillis)
            .putLong(KEY_LAST_EVENT_RAW, raw)
            .putLong(KEY_EVENTS_RECEIVED, received)
            .apply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used: step counts do not depend on accuracy.
    }

    override fun onDestroy() {
        destroyed = true
        notifyJob?.cancel()
        tickerJob?.cancel()
        if (listenerRegistered) {
            sensorManager?.unregisterListener(this)
            listenerRegistered = false
        }
        accelSource?.stop()
        accelSource = null
        stopLocation()
        LiveSteps.setTracking(false)
        wakeups.close()

        // The consumer stores the final value after the channel closes. Wait for it, then
        // release the wake lock and the scope.
        val consumer = consumerJob
        scope.launch {
            try {
                consumer?.join()
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = null
                scope.cancel()
            }
        }
        super.onDestroy()
    }

    /**
     * Starts counting for [plan]. The database total for today is loaded first, so the live
     * count starts from the stored value and no sensor event is handled before that.
     */
    private fun beginCounting(plan: ServicePlan) {
        scope.launch {
            val total = try {
                todayStoredSteps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read today's steps", e)
                0
            }
            mainHandler.post {
                if (destroyed) return@post
                val today = todayIso()
                val live = synchronized(countLock) {
                    liveDay = today
                    storedBase = total.toLong()
                    excessBase = readExcessFor(today).coerceIn(0L, storedBase)
                    counterSteps = 0L
                    accelUnmatched = 0L
                    counterActive = plan == ServicePlan.COUNT
                    lastCounterMillis = System.currentTimeMillis()
                    sensorRaw = readStoredRaw()
                    liveLocked()
                }
                LiveSteps.reset(live)
                updateNotification(live)
                startSource(plan)
            }
        }
    }

    /**
     * Registers the sensor listeners, once. Runs on the main thread. While counting with a step
     * counter, the accelerometer also runs for the whole session, so steps show without the
     * counter's batching delay.
     */
    private fun startSource(plan: ServicePlan) {
        val ok = when (plan) {
            ServicePlan.COUNT -> registerListener().also { registered ->
                if (registered && hasAccelerometer(this)) startAccelerometer()
            }
            ServicePlan.ACCELEROMETER -> startAccelerometer()
            else -> false
        }
        if (!ok) {
            Log.w(TAG, "Could not register a step source; stopping")
            prefs().edit().putBoolean(KEY_COUNTING_ON, false).apply()
            stopSelf()
            return
        }
        LiveSteps.setTracking(true)
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        startNotificationUpdates()
        startLocation()
    }

    /** Hardware path: one listener for the whole service lifetime. */
    private fun registerListener(): Boolean {
        if (listenerRegistered) return true
        val manager = getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false
        sensorManager = manager
        // maxReportLatencyUs = 0: deliver each event as it happens, without batching.
        listenerRegistered = manager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            0,
        )
        return listenerRegistered
    }

    /** Software path: one accelerometer listener for the whole service lifetime. */
    private fun startAccelerometer(): Boolean {
        if (accelSource != null) return true
        val source = AccelStepSource(this) { _ -> onSoftwareStep() }
        accelSource = source
        source.start()
        return true
    }

    /** Accelerometer step (main thread): counts it and updates the live total. */
    private fun onSoftwareStep() {
        // A vehicle has been detected: accelerometer steps are not counted until counting resumes.
        if (vehiclePaused) return
        val now = System.currentTimeMillis()
        recordStepForCadence(now)
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        val live = synchronized(countLock) {
            rollDayLocked(now)
            if (counterActive) {
                accelUnmatched++
            } else {
                // No hardware counter: the accelerometer is the step source.
                counterSteps++
            }
            liveLocked()
        }
        LiveSteps.reset(live)
        wakeups.trySend(Unit)
    }

    /** Keeps the accelerometer step times of the last [CADENCE_WINDOW_MS] for the cadence. Main thread. */
    private fun recordStepForCadence(nowMillis: Long) {
        recentStepMillis.addLast(nowMillis)
        while (recentStepMillis.isNotEmpty() && nowMillis - recentStepMillis.first() > CADENCE_WINDOW_MS) {
            recentStepMillis.removeFirst()
        }
    }

    /** Steps per second over the cadence window, or null when there are too few steps to judge. Main thread. */
    private fun cadenceHz(nowMillis: Long): Double? {
        val recent = recentStepMillis.count { nowMillis - it <= CADENCE_WINDOW_MS }
        return if (recent >= 2) recent.toDouble() / (CADENCE_WINDOW_MS / 1000.0) else null
    }

    /** Starts a new day's counts from zero when the calendar day changes. Call under [countLock]. */
    private fun rollDayLocked(nowMillis: Long) {
        val day = StepDelta.dateIsoFor(nowMillis, ZoneId.systemDefault())
        if (day == liveDay) return
        liveDay = day
        storedBase = 0L
        excessBase = 0L
        counterSteps = 0L
        accelUnmatched = 0L
    }

    /**
     * Today's displayed total: the counter based total plus the capped accelerometer lead.
     * Call under [countLock].
     */
    private fun liveLocked(): Int {
        val now = System.currentTimeMillis()
        val cap = accelLeadCap(now, lastCounterMillis, StepDetector.MAX_STEPS_PER_SECOND)
        return displayTotal(
            counterTotalLocked(),
            accelUnmatched.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            cap,
        )
    }

    /** Counter based total: the stored base, less its excess, plus hardware steps. */
    private fun counterTotalLocked(): Int = clampToInt(storedBase - excessBase + counterSteps)

    private fun clampToInt(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /** The counts to store for the current day, read under [countLock]. */
    private class Snapshot(val day: String, val counterTotal: Int, val displayed: Int, val raw: Long?)

    private fun snapshotLocked(): Snapshot? {
        val day = liveDay ?: return null
        return Snapshot(day, counterTotalLocked(), liveLocked(), sensorRaw)
    }

    /**
     * Stores today's counter based total, never lower than the stored value. The accelerometer
     * lead is not stored. Skips the write when nothing changed since the last successful write.
     * The raw prefs are written only after the database write succeeds. Runs only on the consumer.
     */
    private suspend fun flush(nowMillis: Long) {
        val snap = synchronized(countLock) { snapshotLocked() } ?: return
        // Store what the screen shows, so stopping does not drop the accelerometer steps. The
        // excess (stored minus counter total) is kept so the counter still reconciles on reload.
        val candidate = snap.displayed
        if (snap.day == flushedDay && candidate == flushedTotal && snap.raw == flushedRaw) return
        try {
            val state: ProfileState? = ProfileStore(applicationContext).load()
            val profileId = state?.id ?: DEFAULT_PROFILE_ID
            val dao = StepDatabase.getInstance(applicationContext).stepDayDao()
            val stored = dao.getByDate(profileId, snap.day)?.steps ?: 0
            val merged = mergeStored(stored, candidate)
            dao.upsert(toEntity(state, profileId, snap.day, merged))
            appendVehicleLog(nowMillis, merged)
            val edit = prefs().edit()
                .putString(KEY_EXCESS_DAY, snap.day)
                .putLong(KEY_EXCESS, (merged - snap.counterTotal).coerceAtLeast(0).toLong())
            snap.raw?.let { edit.putLong(KEY_LAST_RAW, it) }
            edit.apply()
            flushedDay = snap.day
            flushedTotal = candidate
            flushedRaw = snap.raw
            lastWriteMillis = nowMillis
            sendWidgetUpdateIfDue(nowMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing is marked as written, so the next attempt stores it again.
            Log.w(TAG, "Failed to record step reading", e)
        }
    }

    /**
     * The part of the stored total for [day] that the counter had not reached when it was last
     * written (accelerometer steps the counter had not reported). Only valid for the same day.
     */
    private fun readExcessFor(day: String): Long {
        val prefs = prefs()
        return if (prefs.getString(KEY_EXCESS_DAY, null) == day) prefs.getLong(KEY_EXCESS, 0L) else 0L
    }

    /** Asks the home widget to redraw, at most once per [WIDGET_MIN_INTERVAL_MS]. */
    private fun sendWidgetUpdateIfDue(nowMillis: Long) {
        val elapsed = nowMillis - lastWidgetBroadcastMillis
        if (elapsed in 0 until WIDGET_MIN_INTERVAL_MS) return
        lastWidgetBroadcastMillis = nowMillis
        sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(ComponentName(this, StepsWidgetProvider::class.java)),
        )
    }

    private suspend fun todayStoredSteps(): Int {
        val state: ProfileState? = ProfileStore(applicationContext).load()
        val profileId = state?.id ?: DEFAULT_PROFILE_ID
        return StepDatabase.getInstance(applicationContext).stepDayDao()
            .getByDate(profileId, todayIso())?.steps ?: 0
    }

    private fun todayIso(): String =
        StepDelta.dateIsoFor(System.currentTimeMillis(), ZoneId.systemDefault())

    private fun readStoredRaw(): Long? {
        val prefs = prefs()
        return if (prefs.contains(KEY_LAST_RAW)) prefs.getLong(KEY_LAST_RAW, 0L) else null
    }

    /** Without a saved profile, steps are stored with zero distance and kcal. */
    private fun toEntity(state: ProfileState?, profileId: String, dateIso: String, steps: Int): StepDayEntity {
        val profile = toDomainProfile(state)
            ?: return StepDayEntity(dateIso, steps, 0.0, 0.0, 0.0, 0.0, 0.0, profileId)
        val m = computeDay(profile, steps, learnedCm)
        return StepDayEntity(
            dateIso = dateIso,
            steps = m.steps,
            distanceM = m.distanceM,
            activeKcal = m.activeKcal,
            bmrKcal = m.bmrKcal,
            rmrKcal = m.rmrKcal,
            totalKcal = m.totalKcal,
            profileId = profileId,
        )
    }

    private fun toDomainProfile(state: ProfileState?): Profile? {
        if (state == null) return null
        val sex = Sex.values().firstOrNull { it.name == state.sex } ?: return null
        return runCatching {
            Profile(state.heightCm, state.weightKg, state.age, sex, state.strideOverrideCm)
        }.getOrNull()
    }

    /**
     * GPS listener for the vehicle check. Runs only while counting, and after a vehicle stop
     * until the vehicle ends. Without fine location the check is off and counting is unchanged.
     */
    private fun startLocation() {
        if (locationListener != null || destroyed) return
        if (!hasFineLocation(this)) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onGpsFix(location)
        }
        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_INTERVAL_MS,
                0f,
                listener,
            )
            locationManager = manager
            locationListener = listener
        } catch (e: SecurityException) {
            Log.w(TAG, "No location permission for the vehicle check", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "GPS provider not available for the vehicle check", e)
        }
    }

    private fun stopLocation() {
        val listener = locationListener ?: return
        locationManager?.removeUpdates(listener)
        locationListener = null
        locationManager = null
    }

    /**
     * Main thread. Only the speed and accuracy are used; coordinates are never kept. The walking
     * pace check finds vehicles. While walking, a fix can also give a stride sample.
     */
    private fun onGpsFix(location: Location) {
        if (destroyed) return
        if (!location.hasSpeed() || !location.hasAccuracy()) return
        val kmh = location.speed.toDouble() * MPS_TO_KMH
        val accuracy = location.accuracy.toDouble()
        val now = System.currentTimeMillis()
        val update = walkingPace.onFix(kmh, accuracy, now) ?: return
        when (val event = update.vehicleEvent) {
            is VehicleStarted -> onVehicleStarted(event.startTimeMillis)
            VehicleEnded -> onVehicleEnded()
            null -> Unit
        }
        if (update.state == PaceState.WALKING && !vehiclePaused) learnStride(kmh, accuracy, now)
    }

    /** Main thread. Feeds one walking fix to the stride learner and stores the result when it changes. */
    private fun learnStride(kmh: Double, accuracy: Double, nowMillis: Long) {
        val accepted = strideEstimator.onFix(kmh, accuracy, nowMillis, cadenceHz(nowMillis))
        if (accepted == null) return
        learnedCm = strideEstimator.usableCm()
        strideStore.save(strideEstimator)
    }

    /**
     * Main thread. Stops the step source and turns counting off, but keeps the service up so
     * the GPS listener can see the vehicle end. The consumer then removes the ride's steps.
     */
    private fun onVehicleStarted(startMillis: Long) {
        if (vehiclePaused || destroyed) return
        vehiclePaused = true
        if (listenerRegistered) {
            sensorManager?.unregisterListener(this)
            listenerRegistered = false
        }
        accelSource?.stop()
        accelSource = null
        notifyJob?.cancel()
        notifyJob = null
        LiveSteps.setTracking(false)
        prefs().edit().putBoolean(KEY_COUNTING_ON, false).apply()
        pendingVehicleStart.set(startMillis)
        forceFlush = true
        wakeups.trySend(Unit)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildPausedNotification())
        }
    }

    /** Main thread. The vehicle has ended, so the GPS listener is no longer needed. */
    private fun onVehicleEnded() {
        if (!vehiclePaused) return
        stopLocation()
        stopSelf()
    }

    /**
     * Consumer. Removes the steps counted since [startMillis] from today's total, then raises
     * the vehicle alert. The dialog shows when the app is open; otherwise a notification is posted.
     */
    private suspend fun rollBackSince(startMillis: Long, nowMillis: Long) {
        val dateIso = todayIso()
        val state: ProfileState? = ProfileStore(applicationContext).load()
        val profileId = state?.id ?: DEFAULT_PROFILE_ID
        val dao = StepDatabase.getInstance(applicationContext).stepDayDao()
        val current = dao.getByDate(profileId, dateIso)?.steps ?: 0
        val remove = stepsToRemove(loadVehicleLog(), startMillis, current)
        val newTotal = current - remove
        if (remove > 0) dao.upsert(toEntity(state, profileId, dateIso, newTotal))
        // Keeps the in-memory counts in step with the rolled-back total, so no later write undoes it.
        synchronized(countLock) {
            storedBase = newTotal.toLong()
            excessBase = 0L
            counterSteps = 0L
            accelUnmatched = 0L
        }
        appendVehicleLog(nowMillis, newTotal)
        mainHandler.post { if (!destroyed) LiveSteps.reset(newTotal) }
        VehicleAlert.raise()
        if (!VehicleAlert.appVisible) postVehicleNotification()
    }

    private fun postVehicleNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(VEHICLE_ALERT_TITLE)
            .setContentText(VEHICLE_ALERT_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(VEHICLE_ALERT_TEXT))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(VEHICLE_NOTIFICATION_ID, notification)
    }

    private fun buildPausedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Cadence has paused counting")
            .setContentText("Vehicle speed detected. Press record in the app to resume.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun loadVehicleLog(): List<Pair<Long, Int>> =
        decodeVehicleLog(vehiclePrefs().getString(KEY_VEHICLE_LOG, null))

    /** Keeps the (time, total) log for today and the last two hours only. */
    private fun appendVehicleLog(nowMillis: Long, total: Int) {
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val keepFrom = maxOf(nowMillis - VEHICLE_LOG_WINDOW_MS, dayStart)
        val updated = appendToVehicleLog(loadVehicleLog(), nowMillis, total, keepFrom)
        vehiclePrefs().edit().putString(KEY_VEHICLE_LOG, encodeVehicleLog(updated)).apply()
    }

    private fun vehiclePrefs(): SharedPreferences =
        getSharedPreferences(VEHICLE_PREFS_NAME, Context.MODE_PRIVATE)
    
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Refreshes the notification from the live count, at most once per [NOTIFY_INTERVAL_MS]. */
    @OptIn(FlowPreview::class)
    private fun startNotificationUpdates() {
        if (notifyJob != null) return
        notifyJob = scope.launch {
            LiveSteps.steps.sample(NOTIFY_INTERVAL_MS).collect { steps -> updateNotification(steps) }
        }
    }

    private fun updateNotification(steps: Int) {
        if (destroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Rebuild only when the count actually moved; the flow already limits this to once a second.
        if (steps == lastNotifiedSteps) return
        lastNotifiedSteps = steps
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(steps))
    }

    private fun buildNotification(steps: Int, sensorFree: Boolean = this.sensorFree): Notification {
        val goal = dailyGoalOrNull()
        val lines = notificationLines(steps, goal ?: 0)
        val color = androidx.core.content.ContextCompat.getColor(this, com.cadence.R.color.brand_primary)
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            android.content.Intent(this, com.cadence.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bigText = listOfNotNull(
            lines.text + if (goal == null) " (default goal)" else "",
            lines.remainingLine,
            if (sensorFree) "Counting from the accelerometer" else null,
        ).joinToString("\n")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.cadence.R.drawable.ic_launcher_monochrome)
            .setColor(color)
            .setLargeIcon(ringIconFor(lines.percent, color))
            .setContentTitle(lines.title)
            .setContentText(lines.text)
            .setSubText(if (sensorFree) "Sensor-free counting" else "Cadence")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setProgress(lines.goal, progressFor(steps, lines.goal), false)
            .setContentIntent(open)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }


    /** Ring icon for [percent], regenerated only when the whole percent changes. */
    private fun ringIconFor(percent: Int, color: Int): android.graphics.Bitmap {
        ringIcon?.takeIf { percent == ringPercent }?.let { return it }
        val bitmap = buildRingIcon(percent, color)
        ringPercent = percent
        ringIcon = bitmap
        return bitmap
    }

    /** The daily goal from the goals prefs, or null when none is set. Never throws. */
    private fun dailyGoalOrNull(): Int? = runCatching {
        val prefs = getSharedPreferences(GOALS_PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(GOALS_KEY_DAILY)) null
        else prefs.getInt(GOALS_KEY_DAILY, 0).takeIf { it > 0 }
    }.getOrNull()

    /** Shown briefly when counting cannot run; the service then stops. */
    private fun buildNoticeNotification(plan: ServicePlan): Notification {
        val text = if (plan == ServicePlan.NO_SENSOR) {
            "This phone has no step counter"
        } else {
            "Activity permission is needed to count steps"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Cadence is not counting")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Steps", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "StepCounterService"
        const val PREFS_NAME = "stepwise_tracker"
        const val KEY_LAST_RAW = "last_raw"
        const val KEY_SENSOR_MISSING = "sensor_missing"
        const val KEY_LAST_EVENT_MILLIS = "last_event_millis"
        const val KEY_LAST_EVENT_RAW = "last_event_raw"
        const val KEY_EVENTS_RECEIVED = "events_received"

        /** Persisted user intent: true while the user has counting turned on. */
        const val KEY_COUNTING_ON = "counting_on"

        /** Accelerometer steps not yet reflected in the counter, stored with the day they belong to. */
        private const val KEY_EXCESS = "accel_excess"
        private const val KEY_EXCESS_DAY = "accel_excess_day"

        private const val GOALS_PREFS_NAME = "stepwise_goals"
        private const val GOALS_KEY_DAILY = "daily"
        private const val WAKE_LOCK_TAG = "stepwise:steps"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val CHANNEL_ID = "steps"
        private const val NOTIFICATION_ID = 1301
        private const val NOTIFY_INTERVAL_MS = 1000L

        /** GPS fixes used for the vehicle check: at most this inaccurate, in metres. */
        private const val GPS_MAX_ACCURACY_M = 20f
        private const val GPS_INTERVAL_MS = 1000L
        private const val MPS_TO_KMH = 3.6

        /** Window over which the step cadence is measured, in milliseconds. */
        private const val CADENCE_WINDOW_MS = 4_000L
        private const val VEHICLE_PREFS_NAME = "stepwise_vehicle"
        private const val KEY_VEHICLE_LOG = "log"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StepCounterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }
}
