package com.stepwise.steps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import android.os.IBinder
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service that counts steps from the hardware TYPE_STEP_COUNTER sensor and
 * adds each delta to today's row in the Room database.
 *
 * Permission: ACTIVITY_RECOGNITION is required. If it is missing, the service stops itself
 * without crashing. The UI requests the permission (another agent wires the UI); this
 * service does not request it.
 *
 * Missing sensor: the service stops itself and sets "sensor_missing" in the tracker
 * SharedPreferences so the UI can explain why counting is unavailable.
 *
 * Sensor events are queued on a channel and handled by one consumer coroutine, so writes
 * never race.
 */
class StepCounterService : Service(), SensorEventListener {

    /** Work for the single consumer: a hardware counter reading, or one software step. */
    private sealed class StepEvent {
        class Cumulative(val raw: Long) : StepEvent()
        object SoftwareStep : StepEvent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<StepEvent>(Channel.UNLIMITED)

    private var sensorManager: SensorManager? = null
    private var listenerRegistered = false
    private var accelSource: AccelStepSource? = null
    private var sensorFree = false
    private var lastSteps = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // The step counter only reports while the processor is awake. The lock has a timeout
        // so it cannot stay held forever; it is renewed on each sensor event.
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        scope.launch {
            for (event in events) {
                try {
                    when (event) {
                        is StepEvent.Cumulative -> record(event.raw)
                        StepEvent.SoftwareStep -> addSteps(1L)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record step reading", e)
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

        // Android requires startForeground after startForegroundService, even when the
        // service is about to stop (STEPW-23). So the notice is posted first, then we stop.
        try {
            startForegroundCompat(
                when (plan) {
                    ServicePlan.COUNT -> buildNotification(lastSteps)
                    ServicePlan.ACCELEROMETER -> {
                        sensorFree = true
                        buildNotification(lastSteps)
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

        when (plan) {
            ServicePlan.COUNT -> registerListener()
            ServicePlan.ACCELEROMETER -> startAccelerometer()
            else -> {
                Log.w(TAG, "Not counting steps: $plan")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values[0].toLong()
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        // Diagnostics are written before queueing, so events are visible even if the
        // later database write fails.
        recordEventDiagnostics(System.currentTimeMillis(), raw)
        Log.d(TAG, "Step counter event raw=$raw")
        events.trySend(StepEvent.Cumulative(raw))
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
        if (listenerRegistered) {
            sensorManager?.unregisterListener(this)
            listenerRegistered = false
        }
        accelSource?.stop()
        accelSource = null
        events.close()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun registerListener() {
        if (listenerRegistered) return
        val manager = getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (manager == null || sensor == null) {
            stopSelf()
            return
        }
        sensorManager = manager
        // maxReportLatencyUs = 0: deliver each event as it happens, without batching.
        listenerRegistered = manager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            0,
        )
    }

    /** Runs on the single consumer coroutine. */
    private suspend fun record(raw: Long) {
        val prefs = prefs()
        val last = if (prefs.contains(KEY_LAST_RAW)) prefs.getLong(KEY_LAST_RAW, 0L) else null
        val delta = StepDelta.nextDelta(last, raw)

        addSteps(delta)

        // Store the raw value only after the write succeeded.
        prefs.edit().putLong(KEY_LAST_RAW, raw).apply()
    }

    /** Adds [delta] steps to today's row. Runs on the single consumer coroutine. */
    private suspend fun addSteps(delta: Long) {
        val dateIso = StepDelta.dateIsoFor(System.currentTimeMillis(), ZoneId.systemDefault())

        // Writes go to the active profile's history; "default" when no profile exists.
        val state: ProfileState? = ProfileStore(applicationContext).load()
        val profileId = state?.id ?: DEFAULT_PROFILE_ID

        val dao = StepDatabase.getInstance(applicationContext).stepDayDao()
        val current = dao.getByDate(profileId, dateIso)
        val steps = ((current?.steps ?: 0).toLong() + delta)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        dao.upsert(toEntity(state, profileId, dateIso, steps))

        lastSteps = steps
        updateNotification(steps)
    }

    /** Starts the software step source. Each detected step is queued for the single consumer. */
    private fun startAccelerometer() {
        if (accelSource != null) return
        val source = AccelStepSource(this) { _ -> events.trySend(StepEvent.SoftwareStep) }
        accelSource = source
        source.start()
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

    private fun updateNotification(steps: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(steps))
    }

    private fun buildNotification(steps: Int, sensorFree: Boolean = this.sensorFree): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(
                if (sensorFree) "Counting steps (sensor-free)" else "Stepwise is counting steps",
            )
            .setContentText("Today: $steps steps")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

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
        private const val WAKE_LOCK_TAG = "stepwise:steps"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val CHANNEL_ID = "steps"
        private const val NOTIFICATION_ID = 1301

        fun start(context: Context) {
            context.startForegroundService(Intent(context, StepCounterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }
}
