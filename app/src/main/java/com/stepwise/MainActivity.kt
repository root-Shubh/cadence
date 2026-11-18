package com.stepwise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.stepwise.data.DEFAULT_PROFILE_ID
import com.stepwise.data.DataReset
import com.stepwise.data.PrivacyPrefs
import com.stepwise.data.ProfileState
import com.stepwise.data.ProfileStore
import com.stepwise.data.StepDatabase
import com.stepwise.data.StepDayEntity
import com.stepwise.data.ThemeChoice
import com.stepwise.data.ThemePrefs
import com.stepwise.data.shouldShowPrivacyNotice
import com.stepwise.data.toCsvRows
import com.stepwise.domain.DailyRecord
import com.stepwise.domain.DayMetrics
import com.stepwise.domain.Profile
import com.stepwise.domain.Sex
import com.stepwise.domain.computeDay
import com.stepwise.export.CsvExporter
import com.stepwise.export.buildDailyCsv
import com.stepwise.export.csvFileName
import com.stepwise.steps.StepCounterService
import com.stepwise.steps.StepTrackingSource
import com.stepwise.steps.stepTrackingSource
import com.stepwise.data.GoalsStore
import com.stepwise.data.OnboardingPrefs
import com.stepwise.ui.OnboardingFlow
import com.stepwise.ui.missingProfileFields
import com.stepwise.ui.shouldShowOnboarding
import com.stepwise.ui.shouldShowReviewBanner
import com.stepwise.goals.StepPoint
import com.stepwise.ui.AppShell
import com.stepwise.ui.GoalsCard
import com.stepwise.ui.GoalsEditor
import com.stepwise.ui.CalibrateScreen
import com.stepwise.ui.Destination
import com.stepwise.ui.PrivacyNotice
import com.stepwise.ui.ProfileScreen
import com.stepwise.ui.StepwiseTheme
import com.stepwise.ui.TodayScreen
import com.stepwise.ui.TrendScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val HISTORY_DAYS = 60L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileStore = ProfileStore(applicationContext)
        val database = StepDatabase.getInstance(applicationContext)
        val dao = database.stepDayDao()
        val dataReset = DataReset(profileStore, dao)
        val themePrefs = ThemePrefs(applicationContext)
        val privacyPrefs = PrivacyPrefs(applicationContext)
        val goalsStore = GoalsStore(applicationContext)
        val onboardingPrefs = OnboardingPrefs(applicationContext)

        setContent {
            var themeChoice by remember { mutableStateOf(themePrefs.load()) }
            val darkTheme = when (themeChoice) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
            }

            StepwiseTheme(darkTheme = darkTheme) {
                val scope = rememberCoroutineScope()
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
                var trackingActive by rememberSaveable { mutableStateOf(false) }
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
                fun startTracking() {
                    if (stepTrackingSource(this@MainActivity) is StepTrackingSource.None) {
                        sensorMissing = true
                        return
                    }
                    StepCounterService.start(applicationContext)
                    trackingActive = true
                }

                // Live row for today, updated by Room whenever the step service writes.
                val todayRow by produceState<StepDayEntity?>(initialValue = null, today, activeId) {
                    dao.getRangeFlow(activeId ?: DEFAULT_PROFILE_ID, today, today)
                        .collect { rows -> value = rows.firstOrNull() }
                }
                val todaySteps = todayRow?.steps ?: 0

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
                    modifier = Modifier.fillMaxSize(),
                ) else AppShell(selected = selected, onSelect = { selected = it }) { padding ->
                    val modifier = Modifier.padding(padding)
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
                                GoalsCard(
                                    points = history.map { StepPoint(it.dateIso, it.steps) },
                                    goals = goals,
                                    todayIso = today,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                TodayScreen(
                                    day = todayDayMetrics(todayRow, profile),
                                    profileMissing = profile == null,
                                    trackingActive = trackingActive,
                                    onStartTracking = {
                                        if (hasActivityRecognition()) {
                                            startTracking()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    sensorMissing = sensorMissing,
                                )
                            }
                        }
                        Destination.TREND -> TrendScreen(
                            history = history,
                            endDate = LocalDate.now(),
                            modifier = modifier,
                            dailyGoal = goals.dailySteps,
                            weeklyGoal = goals.weeklySteps,
                        )
                        Destination.PROFILE -> key(activeId) { ProfileScreen(
                            initial = profileState,
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
                            goalsSection = {
                                GoalsEditor(
                                    current = goals,
                                    onSave = { updated ->
                                        goalsStore.save(updated)
                                        goals = updated
                                    },
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            },
                        ) }
                        Destination.CALIBRATE -> CalibrateScreen(
                            saveStride = { cm ->
                                profileStore.load()?.let { current ->
                                    val updated = current.copy(strideOverrideCm = cm)
                                    profileStore.save(updated)
                                    profileState = updated
                                }
                            },
                            currentSteps = { todayRow?.steps ?: 0 },
                            modifier = modifier,
                        )
                    }
                }
            }
        }
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
private fun todayDayMetrics(row: StepDayEntity?, profile: Profile?): DayMetrics? {
    if (row != null && row.totalKcal > 0.0) {
        return DayMetrics(
            steps = row.steps,
            distanceM = row.distanceM,
            activeKcal = row.activeKcal,
            bmrKcal = row.bmrKcal,
            rmrKcal = row.rmrKcal,
            totalKcal = row.totalKcal,
        )
    }
    return profile?.let { computeDay(it, row?.steps ?: 0) }
}

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
