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
import java.util.concurrent.atomic.AtomicLong

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

    // Sensor path state. Only touched on the main thread.
    /** Last raw reading seen by the sensor path, for the live count. */
    private var sensorRaw: Long? = null

    /** The day (yyyy-MM-dd) the live count belongs to. */
    private var liveDay: String? = null

    // Shared between the sensor path and the consumer.
    /** Newest hardware reading. The consumer stores the difference since the last stored raw. */
    @Volatile private var latestRaw: Long? = null

    /** Software steps (accelerometer) not yet stored. */
    private val softPending = AtomicLong(0L)

    @Volatile private var started = false
    @Volatile private var destroyed = false

    // Consumer-only state. Only used inside the consumer coroutine.
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
                    if (isDirty() && (force || shouldWriteNow(lastWriteMillis, now))) flush(now)
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
        countLive(now, StepDelta.nextDelta(sensorRaw, raw))
        sensorRaw = raw
        latestRaw = raw
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
        sensorRaw = readStoredRaw()
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
                liveDay = todayIso()
                LiveSteps.reset(total)
                updateNotification(total)
                startSource(plan)
            }
        }
    }

    /** Registers the sensor or accelerometer listener, once. Runs on the main thread. */
    private fun startSource(plan: ServicePlan) {
        val ok = when (plan) {
            ServicePlan.COUNT -> registerListener()
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

    private fun onSoftwareStep() {
        val now = System.currentTimeMillis()
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        countLive(now, 1L)
        softPending.incrementAndGet()
        wakeups.trySend(Unit)
    }

    /** Sensor path: adds [delta] to the live count, with no I/O. Resets at the day boundary. */
    private fun countLive(nowMillis: Long, delta: Long) {
        val day = StepDelta.dateIsoFor(nowMillis, ZoneId.systemDefault())
        if (day != liveDay) {
            liveDay = day
            LiveSteps.reset(0)
        }
        if (delta > 0) LiveSteps.add(delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun isDirty(): Boolean {
        val raw = latestRaw
        return softPending.get() != 0L || (raw != null && raw != flushedRaw)
    }

    /**
     * Stores the steps not yet saved: the raw counter difference since the last stored reading,
     * plus software steps. The raw prefs are written only after the database write succeeds.
     * Runs only on the consumer coroutine.
     */
    private suspend fun flush(nowMillis: Long) {
        val raw = latestRaw
        val prefs = prefs()
        val stored = readStoredRaw()
        val rawDelta = if (raw != null) StepDelta.nextDelta(stored, raw) else 0L
        val software = softPending.getAndSet(0L)
        val delta = rawDelta + software
        if (delta == 0L && (raw == null || raw == stored)) {
            flushedRaw = raw
            return
        }
        try {
            addToToday(delta)
            if (raw != null) prefs.edit().putLong(KEY_LAST_RAW, raw).apply()
            flushedRaw = raw
            lastWriteMillis = nowMillis
            sendWidgetUpdateIfDue(nowMillis)
        } catch (e: CancellationException) {
            softPending.addAndGet(software)
            throw e
        } catch (e: Exception) {
            // Put the software steps back so they are stored on the next attempt.
            softPending.addAndGet(software)
            Log.w(TAG, "Failed to record step reading", e)
        }
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

    /** Adds [delta] steps to today's row. Runs on the consumer coroutine. */
    private suspend fun addToToday(delta: Long) {
        val dateIso = todayIso()
        val state: ProfileState? = ProfileStore(applicationContext).load()
        val profileId = state?.id ?: DEFAULT_PROFILE_ID
        val dao = StepDatabase.getInstance(applicationContext).stepDayDao()
        val current = dao.getByDate(profileId, dateIso)
        val steps = ((current?.steps ?: 0).toLong() + delta)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        dao.upsert(toEntity(state, profileId, dateIso, steps))
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
