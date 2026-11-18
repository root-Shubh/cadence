package com.stepwise.steps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stepwise.data.DEFAULT_PROFILE_ID
import com.stepwise.data.ProfileState
import com.stepwise.data.ProfileStore
import com.stepwise.data.StepDatabase
import com.stepwise.data.StepDayEntity
import com.stepwise.domain.Profile
import com.stepwise.domain.Sex
import com.stepwise.domain.computeDay
import com.stepwise.widget.StepsWidgetProvider
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
import java.time.ZoneId

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

    /** Steps from the accelerometer since the start of today. */
    private var accelSteps = 0L

    /** Newest hardware reading. Deltas are taken against it. */
    private var sensorRaw: Long? = null

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

        consumerJob = scope.launch {
            for (ignored in wakeups) {
                try {
                    val now = System.currentTimeMillis()
                    val force = forceFlush
                    forceFlush = false
                    if (force || shouldWriteNow(lastWriteMillis, now)) flush(now)
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

        if (!started) {
            started = true
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
                    accelSteps = 0L
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
        val now = System.currentTimeMillis()
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        val live = synchronized(countLock) {
            rollDayLocked(now)
            accelSteps++
            liveLocked()
        }
        LiveSteps.reset(live)
        wakeups.trySend(Unit)
    }

    /** Starts a new day's counts from zero when the calendar day changes. Call under [countLock]. */
    private fun rollDayLocked(nowMillis: Long) {
        val day = StepDelta.dateIsoFor(nowMillis, ZoneId.systemDefault())
        if (day == liveDay) return
        liveDay = day
        storedBase = 0L
        excessBase = 0L
        counterSteps = 0L
        accelSteps = 0L
    }

    /**
     * Today's live total: the larger of the counter based total and the accelerometer based
     * total. Call under [countLock].
     */
    private fun liveLocked(): Int = liveTotal(counterTotalLocked(), accelTotalLocked())

    /** Counter based total: the stored base, less its excess, plus hardware steps. */
    private fun counterTotalLocked(): Int = clampToInt(storedBase - excessBase + counterSteps)

    /** Accelerometer based total: the stored total at start plus accelerometer steps. */
    private fun accelTotalLocked(): Int = clampToInt(storedBase + accelSteps)

    private fun clampToInt(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /** The counts to store for the current day, read under [countLock]. */
    private class Snapshot(val day: String, val counterTotal: Int, val accelTotal: Int, val raw: Long?)

    private fun snapshotLocked(): Snapshot? {
        val day = liveDay ?: return null
        return Snapshot(day, counterTotalLocked(), accelTotalLocked(), sensorRaw)
    }

    /**
     * Stores today's total, max(counter total, accelerometer total), never lower than the stored
     * value. Skips the write when nothing changed since the last successful write. The raw
     * prefs are written only after the database write succeeds. Runs only on the consumer.
     */
    private suspend fun flush(nowMillis: Long) {
        val snap = synchronized(countLock) { snapshotLocked() } ?: return
        val candidate = liveTotal(snap.counterTotal, snap.accelTotal)
        if (snap.day == flushedDay && candidate == flushedTotal && snap.raw == flushedRaw) return
        try {
            val state: ProfileState? = ProfileStore(applicationContext).load()
            val profileId = state?.id ?: DEFAULT_PROFILE_ID
            val dao = StepDatabase.getInstance(applicationContext).stepDayDao()
            val stored = dao.getByDate(profileId, snap.day)?.steps ?: 0
            val merged = mergeStored(stored, candidate)
            dao.upsert(toEntity(state, profileId, snap.day, merged))
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
        val m = computeDay(profile, steps)
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
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(steps))
    }

    private fun buildNotification(steps: Int, sensorFree: Boolean = this.sensorFree): Notification {
        val goal = dailyGoalOrNull()
        val target = goal ?: DEFAULT_DAILY_GOAL
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(
                if (sensorFree) "Counting steps (sensor-free)" else "Stepwise is counting steps",
            )
            .setContentText("Today: $steps steps")
            .setSubText(if (goal == null) "default goal" else "daily goal")
            .setProgress(target, progressFor(steps, target), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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
            .setContentTitle("Stepwise is not counting")
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StepCounterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }
}
