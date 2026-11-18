package com.cadence

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.data.DataReset
import com.cadence.data.resetToFreshInstall
import com.cadence.data.PrivacyPrefs
import com.cadence.data.ProfileState
import com.cadence.data.ProfileStore
import com.cadence.data.StepDatabase
import com.cadence.data.StepDayDao
import com.cadence.data.StepDayEntity
import com.cadence.export.CsvDailyRow
import com.cadence.export.mergeImported
import com.cadence.export.parseDailyCsv
import com.cadence.goals.effectiveGoals
import com.cadence.steps.LiveSteps
import com.cadence.steps.StrideStore
import com.cadence.steps.appNotificationSettingsIntent
import com.cadence.steps.notificationsEnabled
import com.cadence.ui.rememberNotificationRequest
import com.cadence.ui.rememberLocationRequest
import com.cadence.ui.VehicleCheatingDialog
import com.cadence.steps.VEHICLE_NOTIFICATION_ID
import com.cadence.steps.VehicleAlert
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cadence.data.ThemeChoice
import com.cadence.data.ThemePrefs
import com.cadence.data.shouldShowPrivacyNotice
import com.cadence.data.toCsvRows
import com.cadence.domain.DailyRecord
import com.cadence.domain.DayMetrics
import com.cadence.domain.Profile
import com.cadence.domain.Sex
import com.cadence.domain.computeDay
import com.cadence.export.CsvExporter
import com.cadence.export.buildDailyCsv
import com.cadence.export.csvFileName
import com.cadence.steps.StepCounterService
import com.cadence.steps.StepSensorState
import com.cadence.steps.resumeCountingIfNeeded
import com.cadence.steps.setCountingOn
import com.cadence.steps.StepTrackingSource
import com.cadence.steps.countingStatus
import com.cadence.steps.describeCountingStatus
import com.cadence.steps.stepSensorState
import com.cadence.steps.stepTrackingSource
import com.cadence.data.GoalsStore
import com.cadence.data.OnboardingPrefs
import com.cadence.ui.OnboardingFlow
import com.cadence.ui.missingProfileFields
import com.cadence.ui.shouldShowOnboarding
import com.cadence.ui.shouldShowReviewBanner
import com.cadence.goals.StepPoint
import com.cadence.ui.AppShell
import com.cadence.ui.CalibrateScreen
import com.cadence.ui.Destination
import com.cadence.ui.PrivacyNotice
import com.cadence.ui.ProfileScreen
import com.cadence.ui.StepwiseTheme
import com.cadence.ui.TodayScreen
import com.cadence.ui.TrendScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val HISTORY_DAYS = 60L

/** Outcome of the last CSV import, shown under the Import CSV button until the next import. */
private data class ImportStatus(val message: String, val failed: Boolean)

private const val CSV_UNREADABLE_MESSAGE =
    "Could not read this file. Export a CSV from Cadence and try again."

private fun MainActivity.isGpsOn(): Boolean =
    (getSystemService(android.location.LocationManager::class.java)?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true)

@Composable
private fun GpsOffBanner(onTurnOn: () -> Unit) {
    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Text("GPS is off. Turn location back on for accurate distance and vehicle detection.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onTurnOn, modifier = Modifier.heightIn(min = 48.dp)) { androidx.compose.material3.Text("Turn on") }
        }
    }
}

class MainActivity : ComponentActivity() {
    /** Result of the latest CSV import. Null before any import and while one is running. */
    private var importStatus by mutableStateOf<ImportStatus?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Leave the splash theme for the normal app theme once the Activity is up.
        setTheme(R.style.Theme_Stepwise)
        // The launch theme sets the splash mark as the window background; replace it so it never shows behind the content.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(getColor(R.color.app_background)))
        val profileStore = ProfileStore(applicationContext)
        val database = StepDatabase.getInstance(applicationContext)
        val dao = database.stepDayDao()
        val dataReset = DataReset(profileStore, dao)
        val themePrefs = ThemePrefs(applicationContext)
        val privacyPrefs = PrivacyPrefs(applicationContext)
        val goalsStore = GoalsStore(applicationContext)
        val onboardingPrefs = OnboardingPrefs(applicationContext)
        // Restarts counting if the user left it on and the permission and sensor are present.
        val countingResumed = resumeCountingIfNeeded(applicationContext)

        setContent {
            var themeChoice by remember { mutableStateOf(themePrefs.load()) }
            val darkTheme = when (themeChoice) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
            }

            StepwiseTheme(darkTheme = darkTheme) {
                val scope = rememberCoroutineScope()
                // App-wide GPS state: warn whenever location is off while the app is open.
                var gpsOff by remember { mutableStateOf(!isGpsOn()) }
                val lifecycleForGps = LocalLifecycleOwner.current
                DisposableEffect(lifecycleForGps) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                            gpsOff = !isGpsOn()
                        }
                    }
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) gpsOff = !isGpsOn()
                    }
                    lifecycleForGps.lifecycle.addObserver(observer)
                    onDispose {
                        unregisterReceiver(receiver)
                        lifecycleForGps.lifecycle.removeObserver(observer)
                    }
                }
                var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
                var profileState by remember { mutableStateOf(profileStore.load()) }
                var profiles by remember { mutableStateOf(profileStore.listProfiles()) }
                var activeId by remember { mutableStateOf(profileStore.activeProfileId()) }
                // Reloads the profile list, the active id and the active profile after any change.
                fun refreshProfiles() {
                    profiles = profileStore.listProfiles()
                    activeId = profileStore.activeProfileId()
                    profileState = profileStore.load()
                }
                var trackingActive by rememberSaveable { mutableStateOf(countingResumed) }
                var sensorMissing by rememberSaveable { mutableStateOf(false) }
                var privacyHidden by rememberSaveable { mutableStateOf(false) }
                var goals by remember { mutableStateOf(goalsStore.load()) }
                // First-launch gate. A user who already has a profile skips the flow and is marked done.
                var showOnboarding by remember {
                    mutableStateOf(
                        run {
                            val hasProfile = profileStore.load() != null
                            if (hasProfile) onboardingPrefs.markDone()
                            shouldShowOnboarding(hasProfile, onboardingPrefs.isDone())
                        },
                    )
                }
                val today = LocalDate.now().toString()

                // Checks for a step counter or an accelerometer first, so the service never starts
                // with no way to count (STEPW-23, STEPW-43).
                // True while notifications are off, so Today can say the tray does not show steps.
                var notificationsOff by remember { mutableStateOf(!notificationsEnabled(this@MainActivity)) }
                // Asks for the notification permission (Android 13+) before counting starts. Counting
                // starts after the answer, granted or denied, so the service still runs without it.
                // Asks for location (optional; it enables the vehicle check) after notifications.
                // Counting starts after that answer too, granted or denied.
                val requestLocation = rememberLocationRequest {
                    setCountingOn(applicationContext, true)
                    trackingActive = true
                    notificationsOff = !notificationsEnabled(this@MainActivity)
                }
                val requestNotifications = rememberNotificationRequest { requestLocation() }
                fun startTracking() {
                    if (stepTrackingSource(this@MainActivity) is StepTrackingSource.None) {
                        sensorMissing = true
                        return
                    }
                    requestNotifications()
                }
                // Set when a vehicle stopped counting. Recording is off until the user presses record.
                val vehiclePending by VehicleAlert.pending.collectAsState()
                LaunchedEffect(vehiclePending) {
                    if (vehiclePending) trackingActive = false
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    // Re-read on return, for example from the notification settings screen.
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME ->
                                notificationsOff = !notificationsEnabled(this@MainActivity)
                            Lifecycle.Event.ON_START -> { VehicleAlert.appVisible = true }
                            Lifecycle.Event.ON_STOP -> { VehicleAlert.appVisible = false }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Live row for today, updated by Room whenever the step service writes.
                val todayRow by produceState<StepDayEntity?>(initialValue = null, today, activeId) {
                    dao.getRangeFlow(activeId ?: DEFAULT_PROFILE_ID, today, today)
                        .collect { rows -> value = rows.firstOrNull() }
                }
                val todaySteps = todayRow?.steps ?: 0
                // Live count from the step service while it runs; the Room row otherwise.
                val liveSteps by LiveSteps.steps.collectAsState()
                val liveTracking by LiveSteps.tracking.collectAsState()
                // The GPS-learned stride, read again each time counting changes.
                val learnedStrideCm = remember(liveTracking) { StrideStore(applicationContext).usableCm() }
                // Goals with weekly and monthly values filled in from the daily goal.
                val eff = effectiveGoals(goals)
                val countingStatusText by produceState<String?>(initialValue = null, trackingActive) {
                    while (true) {
                        value = currentCountingStatusText()
                        delay(5_000L)
                    }
                }

                // Last 60 days of stored rows: covers the 30-day window and the one before it.
                val history by produceState<List<DailyRecord>>(initialValue = emptyList(), today, activeId) {
                    val from = LocalDate.now().minusDays(HISTORY_DAYS).toString()
                    dao.getRangeFlow(activeId ?: DEFAULT_PROFILE_ID, from, today).collect { rows ->
                        value = rows.map { DailyRecord(it.dateIso, it.steps, it.totalKcal) }
                    }
                }

                // Requests the activity-recognition permission, then starts the service.
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) startTracking()
                }
                // Turns recording off, or on through the same permission and sensor checks as Start.
                val toggleRecording: () -> Unit = {
                    if (trackingActive) {
                        setCountingOn(applicationContext, false)
                        trackingActive = false
                    } else if (hasActivityRecognition()) {
                        startTracking()
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }

                if (vehiclePending) VehicleCheatingDialog(onDismiss = {
                    VehicleAlert.dismiss()
                    NotificationManagerCompat.from(this@MainActivity).cancel(VEHICLE_NOTIFICATION_ID)
                })

                if (showOnboarding) OnboardingFlow(
                    onSaveProfile = { state ->
                        profileStore.save(state)
                        onboardingPrefs.markProfileSaved(System.currentTimeMillis())
                        refreshProfiles()
                    },
                    onFinish = {
                        onboardingPrefs.markDone()
                        refreshProfiles()
                        showOnboarding = false
                    },
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                ) else AppShell(selected = selected, onSelect = { selected = it }) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    if (gpsOff) {
                        GpsOffBanner(onTurnOn = { startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
                    }
                    val modifier = Modifier.weight(1f)
                    when (selected) {
                        Destination.TODAY -> {
                            val profile = profileState?.toDomainProfile()
                            Column(modifier = modifier.fillMaxSize()) {
                                if (!privacyHidden && shouldShowPrivacyNotice(privacyPrefs)) {
                                    PrivacyNotice(
                                        modifier = Modifier.padding(16.dp),
                                        onGotIt = {
                                            privacyPrefs.acknowledge()
                                            privacyHidden = true
                                        },
                                    )
                                }
                                TodayScreen(
                                    day = todayDayMetrics(
                                        todayRow,
                                        profile,
                                        if (liveTracking) liveSteps else null,
                                        learnedStrideCm,
                                    ),
                                    profileMissing = profile == null,
                                    dailyGoal = eff.dailySteps,
                                    weeklyGoal = eff.weeklySteps,
                                    monthlyGoal = eff.monthlySteps,
                                    history = history,
                                    trackingActive = trackingActive,
                                    isRecording = trackingActive,
                                    onToggleRecording = toggleRecording,
                                    notificationsOff = notificationsOff,
                                    onOpenNotificationSettings = {
                                        startActivity(appNotificationSettingsIntent(this@MainActivity))
                                    },
                                    onStartTracking = {
                                        if (hasActivityRecognition()) {
                                            startTracking()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    sensorMissing = sensorMissing,
                                    countingStatusText = countingStatusText,
                                )
                            }
                        }
                        Destination.TREND -> TrendScreen(
                            history = history,
                            endDate = LocalDate.now(),
                            modifier = modifier,
                            dailyGoal = eff.dailySteps,
                            weeklyGoal = eff.weeklySteps,
                        )
                        Destination.PROFILE -> key(activeId) { ProfileScreen(
                            initial = profileState,
                            hasRecordedData = history.isNotEmpty(),
                            importMessage = importStatus?.message,
                            importFailed = importStatus?.failed == true,
                            onPickCsv = { uri ->
                                scope.launch {
                                    importCsv(uri, dao, activeId ?: DEFAULT_PROFILE_ID, today)
                                }
                            },
                            profiles = profiles,
                            activeId = activeId,
                            onSelectProfile = { id ->
                                profileStore.select(id)
                                refreshProfiles()
                            },
                            onAddProfile = { name ->
                                profileStore.addProfile(name)
                                refreshProfiles()
                            },
                            onRenameProfile = { id, name ->
                                profileStore.rename(id, name)
                                refreshProfiles()
                            },
                            onDeleteActiveProfile = {
                                scope.launch {
                                    val id = activeId ?: DEFAULT_PROFILE_ID
                                    dao.deleteProfileHistory(id)
                                    dataReset.deleteProfile()
                                    refreshProfiles()
                                }
                            },
                            onSave = { saved ->
                                profileStore.save(saved)
                                refreshProfiles()
                            },
                            modifier = modifier,
                            theme = themeChoice,
                            onThemeChange = { choice ->
                                themePrefs.save(choice)
                                themeChoice = choice
                            },
                            onDeleteProfile = {
                                scope.launch {
                                    val id = activeId ?: DEFAULT_PROFILE_ID
                                    dao.deleteProfileHistory(id)
                                    dataReset.deleteProfile()
                                    refreshProfiles()
                                }
                            },
                            onDeleteAllData = {
                                scope.launch { dataReset.deleteAllRecordedData() }
                            },
                            onResetToFreshInstall = {
                                scope.launch {
                                    resetToFreshInstall(applicationContext, dataReset)
                                    this@MainActivity.recreate()
                                }
                            },
                            onExportCsv = {
                                scope.launch {
                                    val rows = dao.getRange(
                                        profileStore.activeProfileId() ?: DEFAULT_PROFILE_ID,
                                        "0000-01-01",
                                        today,
                                    )
                                    val csv = buildDailyCsv(rows.toCsvRows())
                                    startActivity(CsvExporter.export(applicationContext, csv, csvFileName(today)))
                                }
                            },
                            showReviewBanner = shouldShowReviewBanner(
                                savedAtMillis = onboardingPrefs.profileSavedAt(),
                                nowMillis = System.currentTimeMillis(),
                                missingFields = missingProfileFields(profileState),
                            ),
                            goals = goals,
                            onSaveGoals = { updated ->
                                goalsStore.save(updated)
                                goals = updated
                            },
                            goalPoints = history.map { StepPoint(it.dateIso, it.steps) },
                            goalsTodayIso = today,
                        ) }
                        Destination.CALIBRATE -> CalibrateScreen(
                            saveStride = { cm ->
                                val current = profileStore.load()
                                if (current == null) {
                                    false
                                } else {
                                    // The calibrated stride becomes the explicit override, so distance uses it now.
                                    val updated = current.copy(strideOverrideCm = cm)
                                    profileStore.save(updated)
                                    profileState = updated
                                    true
                                }
                            },
                            savedStrideCm = profileState?.strideOverrideCm,
                            modifier = modifier,
                        )
                    }
                    }
                }
            }
        }
    }

    /** Reads the tracker prefs written by [StepCounterService] and describes counting. */
    private fun currentCountingStatusText(): String {
        val prefs = getSharedPreferences(StepCounterService.PREFS_NAME, MODE_PRIVATE)
        val lastEvent = if (prefs.contains(StepCounterService.KEY_LAST_EVENT_MILLIS)) {
            prefs.getLong(StepCounterService.KEY_LAST_EVENT_MILLIS, 0L)
        } else {
            null
        }
        val status = countingStatus(
            hasPermission = hasActivityRecognition(),
            hasSensor = stepSensorState(this) is StepSensorState.Available,
            lastEventMillis = lastEvent,
            nowMillis = System.currentTimeMillis(),
        )
        return describeCountingStatus(status)
    }

    /**
     * Reads a CSV picked by the user, merges it with the stored history and saves the imported
     * rows for [profileId]. The outcome is kept in [importStatus] for the Profile screen. The app
     * never crashes on a bad file.
     */
    private suspend fun importCsv(uri: Uri, dao: StepDayDao, profileId: String, today: String) {
        importStatus = null
        val status = try {
            withContext(Dispatchers.IO) {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext ImportStatus(CSV_UNREADABLE_MESSAGE, failed = true)
                val parsed = parseDailyCsv(text)
                if (parsed.rows.isEmpty()) return@withContext ImportStatus(CSV_UNREADABLE_MESSAGE, failed = true)
                val existing = dao.getRange(profileId, "0000-01-01", today).toCsvRows()
                val importedDates = parsed.rows.map { it.dateIso }.toSet()
                mergeImported(existing, parsed.rows)
                    .filter { it.dateIso in importedDates }
                    .forEach { dao.upsert(it.toStepDayEntity(profileId)) }
                importSummary(importedDates.size, parsed.errors)
            }
        } catch (e: Exception) {
            Log.w("StepwiseImport", "CSV import failed", e)
            ImportStatus(CSV_UNREADABLE_MESSAGE, failed = true)
        }
        importStatus = status
    }

    /** Message for a finished import: days imported, and the skipped rows with the first error. */
    private fun importSummary(days: Int, errors: List<String>): ImportStatus {
        val imported = "Imported $days ${if (days == 1) "day" else "days"}."
        val first = errors.firstOrNull()
            ?: return ImportStatus(imported, failed = false)
        val skipped = errors.size
        val rows = if (skipped == 1) "row" else "rows"
        return ImportStatus("$imported $skipped $rows skipped: $first", failed = false)
    }

    private fun hasActivityRecognition(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Today's metrics: the stored row when it exists (the step service keeps it up to date),
 * otherwise a zero-step estimate from the profile. Null when there is no usable profile.
 */
private fun todayDayMetrics(
    row: StepDayEntity?,
    profile: Profile?,
    liveSteps: Int?,
    learnedStrideCm: Double? = null,
): DayMetrics? {
    // While counting, the live count wins: recomputed from the profile, or steps only without one.
    if (liveSteps != null) {
        return if (profile != null) {
            computeDay(profile, liveSteps, learnedStrideCm)
        } else {
            DayMetrics(
                steps = liveSteps,
                distanceM = 0.0,
                activeKcal = 0.0,
                bmrKcal = 0.0,
                rmrKcal = 0.0,
                totalKcal = 0.0,
            )
        }
    }
    // A stored row always wins for steps. Without a profile its distance and calories are 0.
    if (row != null && (row.totalKcal > 0.0 || profile == null)) {
        return DayMetrics(
            steps = row.steps,
            distanceM = row.distanceM,
            activeKcal = row.activeKcal,
            bmrKcal = row.bmrKcal,
            rmrKcal = row.rmrKcal,
            totalKcal = row.totalKcal,
        )
    }
    return profile?.let { computeDay(it, row?.steps ?: 0, learnedStrideCm) }
}

/** Maps an imported CSV row to a stored day for [profileId]. */
private fun CsvDailyRow.toStepDayEntity(profileId: String): StepDayEntity = StepDayEntity(
    dateIso = dateIso,
    steps = steps,
    distanceM = distanceM,
    activeKcal = activeKcal,
    bmrKcal = bmrKcal,
    rmrKcal = rmrKcal,
    totalKcal = totalKcal,
    profileId = profileId,
)

/** Converts the stored profile to the domain type; null if the stored values are not usable. */
private fun ProfileState.toDomainProfile(): Profile? {
    val sexValue = runCatching { Sex.valueOf(sex) }.getOrNull() ?: return null
    return runCatching {
        Profile(
            heightCm = heightCm,
            weightKg = weightKg,
            age = age,
            sex = sexValue,
            strideOverrideCm = strideOverrideCm,
        )
    }.getOrNull()
}
