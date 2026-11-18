package com.stepwise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.stepwise.data.ProfileState
import com.stepwise.data.ProfileStore
import com.stepwise.data.StepDatabase
import com.stepwise.data.StepDayEntity
import com.stepwise.domain.DailyRecord
import com.stepwise.domain.DayMetrics
import com.stepwise.domain.Profile
import com.stepwise.domain.Sex
import com.stepwise.domain.computeDay
import com.stepwise.steps.StepCounterService
import com.stepwise.steps.StepSensorState
import com.stepwise.steps.stepSensorState
import com.stepwise.ui.AppShell
import com.stepwise.ui.CalibrateScreen
import com.stepwise.ui.Destination
import com.stepwise.ui.ProfileScreen
import com.stepwise.ui.StepwiseTheme
import com.stepwise.ui.TodayScreen
import com.stepwise.ui.TrendScreen
import java.time.LocalDate

private const val HISTORY_DAYS = 60L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileStore = ProfileStore(applicationContext)
        val dao = StepDatabase.getInstance(applicationContext).stepDayDao()

        setContent {
            StepwiseTheme {
                var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
                var profileState by remember { mutableStateOf(profileStore.load()) }
                var trackingActive by rememberSaveable { mutableStateOf(false) }
                var sensorMissing by rememberSaveable { mutableStateOf(false) }
                val today = LocalDate.now().toString()

                // Checks for a step counter first, so the service never starts without one (STEPW-23).
                fun startTracking() {
                    if (stepSensorState(this@MainActivity) is StepSensorState.NoSensor) {
                        sensorMissing = true
                        return
                    }
                    StepCounterService.start(applicationContext)
                    trackingActive = true
                }

                // Live row for today, updated by Room whenever the step service writes.
                val todayRow by produceState<StepDayEntity?>(initialValue = null, today) {
                    dao.getRangeFlow(today, today).collect { rows -> value = rows.firstOrNull() }
                }
                val todaySteps = todayRow?.steps ?: 0

                // Last 60 days of stored rows: covers the 30-day window and the one before it.
                val history by produceState<List<DailyRecord>>(initialValue = emptyList(), today) {
                    val from = LocalDate.now().minusDays(HISTORY_DAYS).toString()
                    dao.getRangeFlow(from, today).collect { rows ->
                        value = rows.map { DailyRecord(it.dateIso, it.steps, it.totalKcal) }
                    }
                }

                // Requests the activity-recognition permission, then starts the service.
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) startTracking()
                }

                AppShell(selected = selected, onSelect = { selected = it }) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (selected) {
                        Destination.TODAY -> {
                            val profile = profileState?.toDomainProfile()
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
                                modifier = modifier,
                                sensorMissing = sensorMissing,
                            )
                        }
                        Destination.TREND -> TrendScreen(
                            history = history,
                            endDate = LocalDate.now(),
                            modifier = modifier,
                        )
                        Destination.PROFILE -> ProfileScreen(
                            initial = profileState,
                            onSave = { saved ->
                                profileStore.save(saved)
                                profileState = saved
                            },
                            modifier = modifier,
                        )
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
