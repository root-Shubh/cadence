package com.stepwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.stepwise.data.ProfileState
import com.stepwise.data.ProfileStore
import com.stepwise.domain.Profile
import com.stepwise.domain.Sex
import com.stepwise.domain.computeDay
import com.stepwise.ui.AppShell
import com.stepwise.ui.CalibrateScreen
import com.stepwise.ui.Destination
import com.stepwise.ui.StepwiseTheme
import com.stepwise.ui.TodayScreen
import com.stepwise.ui.ProfileScreen
import com.stepwise.ui.TrendScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileStore = ProfileStore(applicationContext)

        setContent {
            StepwiseTheme {
                var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
                var profileState by remember { mutableStateOf(profileStore.load()) }
                var trackingActive by rememberSaveable { mutableStateOf(false) }

                // Step count is a placeholder until the step counter is wired in by the integrator.
                val todaySteps = 0

                AppShell(selected = selected, onSelect = { selected = it }) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (selected) {
                        Destination.TODAY -> {
                            val profile = profileState?.toDomainProfile()
                            TodayScreen(
                                day = profile?.let { computeDay(it, todaySteps) },
                                profileMissing = profile == null,
                                trackingActive = trackingActive,
                                onStartTracking = { trackingActive = true },
                                modifier = modifier,
                            )
                        }
                        Destination.TREND -> TrendScreen(
                            history = emptyList(),
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
                            currentSteps = { todaySteps },
                            modifier = modifier,
                        )
                    }
                }
            }
        }
    }
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
