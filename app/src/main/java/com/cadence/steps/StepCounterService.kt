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
import com.cadence.debug.DebugLog
import com.cadence.domain.Profile
import com.cadence.domain.Sex
import com.cadence.domain.computeDay
import com.cadence.patterns.DEFAULT_DRAW_STRIDE_M
import com.cadence.patterns.HeadingSource
import com.cadence.patterns.LiveTrack
import com.cadence.patterns.PatternStore
import com.cadence.patterns.TrackStore
import com.cadence.patterns.dayIsoOf
import com.cadence.patterns.drawStrideMeters
import com.cadence.widget.WIDGET_REDRAW_INTERVAL_MS
import com.cadence.widget.WidgetThrottle
import com.cadence.widget.requestAllWidgetsRefresh
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
    private var lead = ProvisionalLead()

    /** Heading for the walk drawing (no GPS). Main thread only. */
    private var headingSource: HeadingSource? = null

    /** Stride used for the walk drawing, in metres. Set when counting begins. */
    @Volatile private var drawStrideM = DEFAULT_DRAW_STRIDE_M

    /**
     * True when the hardware step counter is the step source (plan COUNT). False on devices with
     * no step counter, where the accelerometer is the only source and its steps count directly.
     */
    private var counterActive = false

    /**
     * True when TYPE_STEP_DETECTOR drives the live total (see [liveLocked]). The counter still
     * drives the saved total. Set in [beginCounting]; false on devices without a detector.
     */
    private var detectorActive = false

    /** Steps reported by TYPE_STEP_DETECTOR since counting started today. Guarded by [countLock]. */
    private var detectedSinceStart = 0L

    /** Detector step times (epoch millis) not yet written to the day file. Guarded by [countLock]. */
    private val pendingStepMillis = ArrayList<Long>()

    /** Detector listener, registered only while counting with a detector. Main thread only. */
    private var detectorSource: StepDetectorSource? = null

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
    private val widgetThrottle = WidgetThrottle()

    /** Set when the app is swiped away: the next consumer pass stores pending steps at once. */
    @Volatile private var forceFlush = false

    override fun onCreate() {
        super.onCreate()
        DebugLog.attach(filesDir)
        DebugLog.add("service", "created (process ${android.os.Process.myPid()})")
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
                    // The last redraw of a walk may have been held back by the throttle: send it now.
                    if (widgetThrottle.takePendingIfDue(now)) refreshWidgetsNow()
                    // Runs after the flush, so the rollback sees every step counted before the stop.
                    pendingVehicleStart.getAndSet(null)?.let { rollBackSince(it, now) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to store steps", e)
                    DebugLog.add("storage", "write failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            // The channel was closed because the service stops: store the final value once.
            try {
                flush(System.currentTimeMillis())
                // Counting stopped: the widgets show the final count and the Start button.
                refreshWidgetsNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to store steps on stop", e)
                DebugLog.add("storage", "final write failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // Wakes the consumer every interval so pending steps are stored within one interval.
        tickerJob = scope.launch {
            var lastHeartbeat = System.currentTimeMillis()
            while (isActive) {
                delay(STORE_MIN_INTERVAL_MS)
                wakeups.trySend(Unit)
                val now = System.currentTimeMillis()
                // Lets an expired provisional lead leave the screen even when no step arrives.
                if (LiveSteps.tracking.value) {
                    val live = synchronized(countLock) { if (liveDay != null) liveLocked() else null }
                    if (live != null && live != LiveSteps.steps.value) LiveSteps.reset(live)
                }
                if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                    lastHeartbeat = now
                    // Renews the wake lock well before its timeout while counting, so it never
                    // lapses during a session (it used to lapse ten minutes after the last step).
                    if (started && !destroyed && !vehiclePaused && LiveSteps.tracking.value) {
                        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
                    }
                    DebugLog.add(
                        "heartbeat",
                        "alive; counter listener=$listenerRegistered detector=${detectorSource != null} " +
                            "accelerometer=${accelSource != null} vehiclePaused=$vehiclePaused " +
                            "lastCounterRaw=$sensorRaw " +
                            synchronized(countLock) {
                                "detectorSteps=$detectedSinceStart counterSteps=$counterSteps " +
                                    "shown=${liveLocked()} counterTotal=${counterTotalLocked()}"
                            } +
                            " wakeLockHeld=${wakeLock?.isHeld}",
                        now,
                    )
                }
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
        DebugLog.add(
            "service",
            "start command (${if (intent == null) "system restart after the process was killed" else "from app"}, " +
                "already started=$started, vehiclePaused=$vehiclePaused): " +
                "plan=$plan activityPermission=${hasActivityRecognition(this)} " +
                "stepSensor=${stepSensorState(this) is StepSensorState.Available} accelerometer=${hasAccelerometer(this)}",
        )

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
        DebugLog.add("service", "app swiped away from recents; service keeps running")
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
        DebugLog.add("counter", "raw=$raw (previous=$sensorRaw)", now)
        val live = synchronized(countLock) {
            rollDayLocked(now)
            // The counter is the source of truth: it keeps counting in the sensor hub while the
            // phone is locked, so its total includes steps the detector never delivered.
            // The counter has reported, so the detector or accelerometer lead is matched up to here.
            counterSteps += StepDelta.counterDelta(sensorRaw, raw, lead.takeForCounter(now))
            sensorRaw = raw
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
        Log.w("CadenceSteps", "service destroyed; counting flag=${prefs().getBoolean(KEY_COUNTING_ON, false)}", Throwable("destroy"))
        val countingFlag = prefs().getBoolean(KEY_COUNTING_ON, false)
        DebugLog.add("service", "destroyed; counting flag=$countingFlag")
        // A user stop closes the session: the next start takes a fresh counter baseline.
        if (!countingFlag) prefs().edit().putBoolean(KEY_SESSION_OPEN, false).apply()
        destroyed = true
        notifyJob?.cancel()
        tickerJob?.cancel()
        if (listenerRegistered) {
            sensorManager?.unregisterListener(this)
            listenerRegistered = false
        }
        accelSource?.stop()
        accelSource = null
        detectorSource?.stop()
        detectorSource = null
        headingSource?.stop()
        headingSource = null
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
            // The walk drawing continues from today's saved points, and uses the user's stride.
            runCatching {
                val state = ProfileStore(applicationContext).load()
                drawStrideM = drawStrideMeters(state?.strideOverrideCm, learnedCm, state?.heightCm)
                val saved = TrackStore(filesDir).read(state?.id ?: DEFAULT_PROFILE_ID, todayIso())
                LiveTrack.load(todayIso(), saved)
            }.onFailure { Log.w(TAG, "Could not load the walk drawing", it) }
            mainHandler.post {
                if (destroyed) return@post
                val today = todayIso()
                val live = synchronized(countLock) {
                    liveDay = today
                    storedBase = total.toLong()
                    excessBase = readExcessFor(today).coerceIn(0L, storedBase)
                    counterSteps = 0L
                    counterActive = plan == ServicePlan.COUNT
                    detectorActive = plan == ServicePlan.COUNT && hasStepDetector(this@StepCounterService)
                    // With a detector the counter follows within seconds, so an unconfirmed lead is a
                    // rejected burst and expires. Without one the counter can stay silent for long.
                    lead = if (detectorActive) {
                        ProvisionalLead(expiryMs = LEAD_EXPIRY_MS, maxAccelLead = WARMUP_LEAD_CAP)
                    } else {
                        ProvisionalLead(expiryMs = Long.MAX_VALUE)
                    }
                    detectedSinceStart = 0L
                    lastCounterMillis = System.currentTimeMillis()
                    // Keep the stored reading only when a session was cut short (process killed while
                    // counting). After a user stop or a vehicle pause the first reading is a fresh
                    // baseline, so steps walked while counting was off are not added.
                    val sessionOpen = prefs().getBoolean(KEY_SESSION_OPEN, false)
                    sensorRaw = if (sessionOpen) readStoredRaw() else null
                    DebugLog.add("service", "counting begins: stored today=$total, counter baseline=${sensorRaw ?: "next reading"}")
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
                if (registered) startLiveSource()
            }
            ServicePlan.ACCELEROMETER -> startAccelerometer().also {
                Log.d(TAG_STEPS, "Step source: accelerometer fallback (no step detector)")
                DebugLog.add("sensor", "source: accelerometer fallback started (no step counter or detector)")
            }
            else -> false
        }
        if (!ok) {
            Log.w(TAG, "Could not register a step source; stopping")
            DebugLog.add("sensor", "no step source registered for plan=$plan; stopping")
            prefs().edit().putBoolean(KEY_COUNTING_ON, false).apply()
            stopSelf()
            return
        }
        LiveSteps.setTracking(true)
        prefs().edit().putBoolean(KEY_SESSION_OPEN, true).apply()
        // The start/stop widget shows Stop at once.
        refreshWidgetsNow()
        // Direction for the walk drawing, from the rotation sensors (no GPS).
        if (headingSource == null) {
            val heading = HeadingSource(this)
            if (heading.start()) {
                headingSource = heading
                DebugLog.add("sensor", "heading for the walk drawing: ${heading.sensorName}, stride ${"%.2f".format(java.util.Locale.ROOT, drawStrideM)} m")
            } else {
                DebugLog.add("sensor", "no rotation sensor: the walk drawing is off")
            }
        }
        // Held for the whole session and renewed by the heartbeat, so the processor stays able to
        // receive step events while the phone is locked.
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        startNotificationUpdates()
        startLocation()
    }

    /** Hardware path: one listener for the whole service lifetime. */
    private fun registerListener(): Boolean {
        if (listenerRegistered) return true
        val manager = getSystemService(SensorManager::class.java)
        // Prefer the wake-up variant: it wakes the processor to deliver steps while the phone is locked.
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true)
            ?: manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            DebugLog.add("sensor", "TYPE_STEP_COUNTER not present on this device")
            return false
        }
        sensorManager = manager
        // maxReportLatencyUs = 0: deliver each event as it happens, without batching.
        listenerRegistered = manager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            0,
        )
        DebugLog.add(
            "sensor",
            "TYPE_STEP_COUNTER registered=$listenerRegistered (${sensor.name}, wakeUp=${sensor.isWakeUpSensor})",
        )
        return listenerRegistered
    }

    /**
     * The live source while counting with a step counter. With a TYPE_STEP_DETECTOR the detector
     * drives the live total and the accelerometer stays off. Without one, the accelerometer runs
     * alongside the counter as before. Main thread only.
     */
    private fun startLiveSource() {
        if (detectorActive) {
            val source = StepDetectorSource(this) { nanos -> onDetectorStep(nanos) }
            if (source.start()) {
                detectorSource = source
                Log.d(TAG_STEPS, "Step source: TYPE_STEP_DETECTOR (live), TYPE_STEP_COUNTER (saved total)")
                DebugLog.add("sensor", "live source: TYPE_STEP_DETECTOR registered")
                // The accelerometer covers the warm-up, when the phone holds the first steps back.
                if (hasAccelerometer(this)) {
                    startAccelerometer()
                    DebugLog.add("sensor", "accelerometer started for instant first steps")
                }
                return
            }
            // Registration refused: fall back to the accelerometer path, as on a device without a detector.
            Log.w(TAG_STEPS, "Step detector registration failed; using accelerometer fallback")
            DebugLog.add("sensor", "TYPE_STEP_DETECTOR registration refused; falling back to accelerometer")
            detectorActive = false
            synchronized(countLock) { liveLocked() }.let { LiveSteps.reset(it) }
        }
        Log.d(TAG_STEPS, "Step source: TYPE_STEP_COUNTER (saved total) with accelerometer fallback")
        DebugLog.add("sensor", "live source: TYPE_STEP_COUNTER (saved total) with accelerometer fallback")
        if (hasAccelerometer(this)) startAccelerometer()
    }

    /**
     * Detector step (main thread): one event is one step. Updates the live total at once and
     * wakes the consumer, which stores it on its throttled schedule.
     */
    private fun onDetectorStep(timestampNanos: Long) {
        if (vehiclePaused || destroyed) return
        val now = System.currentTimeMillis()
        recordStepForCadence(now)
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        val (live, running) = synchronized(countLock) {
            rollDayLocked(now)
            detectedSinceStart++
            // Shown at once as a lead over the counter total, and matched when the counter reports.
            lead.onDetectorStep(now)
            pendingStepMillis.add(now)
            liveLocked() to detectedSinceStart
        }
        drawStep(now)
        if (running <= DETECTOR_LOG_EVENTS) {
            Log.d(TAG_STEPS, "Detector event $running at ${timestampNanos / NANOS_PER_MILLI} ms, running count $running")
        }
        DebugLog.add("detector", "step $running since start, live total $live")
        LiveSteps.reset(live)
        wakeups.trySend(Unit)
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
        if (vehiclePaused || destroyed) return
        val now = System.currentTimeMillis()
        val live = synchronized(countLock) {
            rollDayLocked(now)
            val counted = if (counterActive) {
                // Provisional only while the detector is silent (warm-up); ignored once it reports.
                lead.onAccelStep(now)
            } else {
                // No hardware counter: the accelerometer is the step source.
                counterSteps++
                true
            }
            if (counted) liveLocked() else null
        } ?: return
        recordStepForCadence(now)
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        if (counterActive) DebugLog.add("accel", "provisional step, live total $live", now)
        drawStep(now)
        LiveSteps.reset(live)
        wakeups.trySend(Unit)
    }

    /** Adds one step to the walk drawing, in the current heading. Main thread. */
    private fun drawStep(nowMillis: Long) {
        val day = StepDelta.dateIsoFor(nowMillis, ZoneId.systemDefault())
        LiveTrack.onStep(day, nowMillis, headingSource?.heading, drawStrideM)
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
        lead.reset()
        detectedSinceStart = 0L
    }

    /**
     * Today's displayed total: the counter based total plus the capped lead of detector (or
     * accelerometer) steps the counter has not reported yet. The detector only makes the number
     * move at once; the counter decides the total, so steps the detector misses (phone locked,
     * sensor-hub warm-up, turning) are still counted. Call under [countLock].
     */
    private fun liveLocked(): Int {
        val now = System.currentTimeMillis()
        val cap = accelLeadCap(now, lastCounterMillis, StepDetector.MAX_STEPS_PER_SECOND)
        return displayTotal(
            counterTotalLocked(),
            lead.current(now).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
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
    /**
     * Moves the detector step times since the last write into the day files (one timestamp per
     * step). Failed days go back in the buffer for the next pass. Runs only on the consumer.
     */
    private suspend fun writeStepTimes() {
        val state: ProfileState? = ProfileStore(applicationContext).load()
        val profileId = state?.id ?: DEFAULT_PROFILE_ID
        val times = synchronized(countLock) {
            if (pendingStepMillis.isEmpty()) return
            ArrayList(pendingStepMillis).also { pendingStepMillis.clear() }
        }
        val store = PatternStore(filesDir)
        val zone = ZoneId.systemDefault()
        times.groupBy { dayIsoOf(it, zone) }.forEach { (day, list) ->
            try {
                store.appendSteps(profileId, day, list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record step times", e)
                synchronized(countLock) { pendingStepMillis.addAll(0, list) }
            }
        }
    }

    /** Saves the walk drawing points added since the last pass. Failed points are retried. Consumer only. */
    private fun writeTrack() {
        val points = LiveTrack.drainPending()
        if (points.isEmpty()) return
        val profileId = ProfileStore(applicationContext).load()?.id ?: DEFAULT_PROFILE_ID
        val store = TrackStore(filesDir)
        val zone = ZoneId.systemDefault()
        points.groupBy { dayIsoOf(it.timeMillis, zone) }.forEach { (day, list) ->
            try {
                store.append(profileId, day, list)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save the walk drawing", e)
                LiveTrack.requeue(list)
            }
        }
    }

    private suspend fun flush(nowMillis: Long) {
        writeStepTimes()
        writeTrack()
        val (snap, withDetector) = synchronized(countLock) { snapshotLocked() to detectorActive }
        if (snap == null) return
        // With a detector, store only what the counter confirmed: provisional steps the phone later
        // rejects must not be saved. Without one, store what the screen shows, so stopping does not
        // drop the accelerometer steps; the excess (stored minus counter total) keeps the counter
        // reconciling on reload.
        val candidate = if (withDetector) snap.counterTotal else snap.displayed
        if (snap.day == flushedDay && candidate == flushedTotal && snap.raw == flushedRaw) return
        try {
            val state: ProfileState? = ProfileStore(applicationContext).load()
            val profileId = state?.id ?: DEFAULT_PROFILE_ID
            val dao = StepDatabase.getInstance(applicationContext).stepDayDao()
            val stored = dao.getByDate(profileId, snap.day)?.steps ?: 0
            val merged = mergeStored(stored, candidate)
            dao.upsert(toEntity(state, profileId, snap.day, merged))
            appendVehicleLog(nowMillis, merged)
            DebugLog.add("storage", "stored ${snap.day} total $merged (display ${snap.displayed}, counter ${snap.counterTotal})", nowMillis)
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

    /**
     * Asks every home widget to redraw, at most once per [WIDGET_REDRAW_INTERVAL_MS]. A request
     * inside the interval is kept and sent by the consumer once due, so the last count always shows.
     */
    private fun sendWidgetUpdateIfDue(nowMillis: Long) {
        if (widgetThrottle.request(nowMillis)) refreshWidgetsNow()
    }

    /** Redraws every widget now, for example when counting starts or stops. Never throws. */
    private fun refreshWidgetsNow() {
        runCatching { requestAllWidgetsRefresh(this) }
            .onFailure { Log.w(TAG, "Could not refresh widgets", it) }
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
        detectorSource?.stop()
        detectorSource = null
        headingSource?.stop()
        headingSource = null
        notifyJob?.cancel()
        notifyJob = null
        LiveSteps.setTracking(false)
        prefs().edit()
            .putBoolean(KEY_COUNTING_ON, false)
            // Steps during the ride must not be added when counting resumes.
            .putBoolean(KEY_SESSION_OPEN, false)
            .apply()
        wakeLock?.let { if (it.isHeld) it.release() }
        refreshWidgetsNow()
        DebugLog.add("vehicle", "paused: speed above $VEHICLE_SPEED_KMH km/h sustained since ${startMillis}; counting stopped")
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
        DebugLog.add("vehicle", "ended; service stopping")
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
            lead.reset()
            detectedSinceStart = 0L
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
        private const val TAG_STEPS = "CadenceSteps"
        private const val DETECTOR_LOG_EVENTS = 5L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** How often the debug log records that the service is still alive and what it holds. */
        const val HEARTBEAT_INTERVAL_MS = 60_000L
        const val PREFS_NAME = "cadence_tracker"
        const val KEY_LAST_RAW = "last_raw"
        const val KEY_SENSOR_MISSING = "sensor_missing"
        const val KEY_LAST_EVENT_MILLIS = "last_event_millis"
        const val KEY_LAST_EVENT_RAW = "last_event_raw"
        const val KEY_EVENTS_RECEIVED = "events_received"

        /**
         * True from the moment counting starts until the user stops it or a vehicle pauses it.
         * Still true after the process is killed, so a restart keeps the stored counter reading.
         */
        const val KEY_SESSION_OPEN = "session_open"

        /** Persisted user intent: true while the user has counting turned on. */
        const val KEY_COUNTING_ON = "counting_on"

        /** Accelerometer steps not yet reflected in the counter, stored with the day they belong to. */
        private const val KEY_EXCESS = "accel_excess"
        private const val KEY_EXCESS_DAY = "accel_excess_day"

        private const val GOALS_PREFS_NAME = "cadence_goals"
        private const val GOALS_KEY_DAILY = "daily"
        private const val WAKE_LOCK_TAG = "cadence:steps"
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
        private const val VEHICLE_PREFS_NAME = "cadence_vehicle"
        private const val KEY_VEHICLE_LOG = "log"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StepCounterService::class.java))
        }

        fun stop(context: Context) {
            Log.w("CadenceSteps", "stop requested by app", Throwable("stop call site"))
            DebugLog.add("service", "stop requested by app")
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }
}
