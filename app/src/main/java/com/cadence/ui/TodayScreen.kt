package com.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.gps.RouteRecording
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cadence.domain.DailyRecord
import com.cadence.domain.DayMetrics
import com.cadence.goals.daysMonthToDate
import com.cadence.goals.periodSummary
import com.cadence.goals.stepsInLastDays
import com.cadence.goals.stepsMonthToDate
import java.time.LocalDate
import java.util.Locale

@Composable
fun TodayScreen(
    day: DayMetrics?,
    profileMissing: Boolean,
    trackingActive: Boolean,
    onStartTracking: () -> Unit,
    modifier: Modifier = Modifier,
    sensorMissing: Boolean = false,
    countingStatusText: String? = null,
    dailyGoal: Int? = null,
    weeklyGoal: Int? = null,
    monthlyGoal: Int? = null,
    history: List<DailyRecord> = emptyList(),
    endDate: LocalDate = LocalDate.now(),
    onToggleRecording: () -> Unit = {},
    isRecording: Boolean = trackingActive,
    notificationsOff: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    activeProfileId: String = DEFAULT_PROFILE_ID,
    activitySlots: Map<Int, Int> = emptyMap(),
) {
    val steps = day?.steps
    val caloriesKnown = day != null && day.totalKcal > 0.0

    // GPS route mapping follows the Record session only. It never changes the step count.
    val context = LocalContext.current
    DisposableEffect(isRecording, activeProfileId) {
        if (isRecording) RouteRecording.start(context, activeProfileId)
        onDispose { RouteRecording.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (sensorMissing) {
                Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "This phone has no step counter, so steps cannot be counted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                DailyGoalLine(
                    steps = steps ?: 0,
                    dailyGoal = dailyGoal,
                )
            }

            Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
                StatChipRow(
                    goalValue = goalPercent(steps ?: 0, dailyGoal)?.let { "$it%" } ?: "Set goal",
                    averageValue = sevenDayAverage(history, endDate)
                        ?.let { formatCount(it.toLong()) } ?: "-",
                    streakValue = streakDays(history, endDate, dailyGoal).toString(),
                )
            }

            Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalCardsPager(
                        dailyCard = {
                            HeroStepCard(
                                steps = steps,
                                dailyGoal = dailyGoal,
                                isRecording = isRecording,
                                onToggleRecording = onToggleRecording,
                            )
                        },
                        weekly = periodSummary(
                            steps = stepsInLastDays(history, endDate, 7),
                            goal = weeklyGoal,
                            days = 7,
                        ),
                        monthly = periodSummary(
                            steps = stepsMonthToDate(history, endDate),
                            goal = monthlyGoal,
                            days = daysMonthToDate(endDate),
                        ),
                    )
                    if (notificationsOff) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Notifications are off, so the step count is not shown in the tray. " +
                                    "Turn them on in settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onOpenNotificationSettings,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Notification settings")
                            }
                        }
                    }
                    countingStatusText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        IconChip(icon = Icons.Filled.Route, tone = Tone.TERTIARY)
                        Column {
                            Text(
                                text = day?.let { String.format(Locale.US, "%.2f km", it.distanceM / 1000.0) } ?: "-",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                "distance walked",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
                ActivityTimelineCard(slots = activitySlots)
            }

            if (profileMissing && !caloriesKnown) {
                Reveal(index = 6, modifier = Modifier.fillMaxWidth()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            IconChip(icon = Icons.Filled.Person, tone = Tone.SECONDARY)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Add your profile to see calories",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Calories need your height, weight, age and sex. Set them in Profile.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            } else if (day != null && caloriesKnown) {
                Reveal(index = 6, modifier = Modifier.fillMaxWidth()) {
                    CalorieGrid(
                        totalKcal = Math.round(day.totalKcal).toInt(),
                        activeKcal = Math.round(day.activeKcal).toInt(),
                        bmrKcal = Math.round(day.bmrKcal).toInt(),
                        rmrKcal = Math.round(day.rmrKcal).toInt(),
                    )
                }
            }
        }
    }
}

/**
 * One compact line for the daily goal: a bar and "N of M steps today". Without a daily goal it
 * points to Profile instead. Weekly and monthly goals are shown on Profile only.
 */
@Composable
private fun DailyGoalLine(steps: Int, dailyGoal: Int?, modifier: Modifier = Modifier) {
    val validGoal = dailyGoal?.takeIf { it > 0 }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (validGoal != null) {
            val summary = "${formatCount(steps.toLong())} of ${formatCount(validGoal.toLong())} steps today"
            LinearProgressIndicator(
                progress = { progressFraction(steps, validGoal) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = summary },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Set a daily goal in Profile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Three equal stat chips: goal percent, 7-day average and streak. */
@Composable
private fun StatChipRow(goalValue: String, averageValue: String, streakValue: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(value = goalValue, label = "Goal", modifier = Modifier.weight(1f))
        StatChip(value = averageValue, label = "7-day avg", modifier = Modifier.weight(1f))
        StatChip(value = streakValue, label = "Streak", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Step count on a soft vertical gradient, with a goal progress ring on the right.
 * The record chip sits under the step count.
 */
@Composable
private fun HeroStepCard(
    steps: Int?,
    dailyGoal: Int?,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
) {
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
    val fraction = if (steps != null && dailyGoal != null && dailyGoal > 0) {
        (steps.toFloat() / dailyGoal).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percentText = goalPercent(steps ?: 0, dailyGoal)?.let { "$it percent of daily goal" }
        ?: "No daily goal set"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(gradient)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (steps != null) {
                    AnimatedCountText(
                        value = steps,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconChip(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        tone = Tone.PRIMARY,
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                    Text(
                        text = "steps today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecordChip(
                        isRecording = isRecording,
                        onToggle = onToggleRecording,
                    )
                    // How to carry the phone: one tap away, beside Record.
                    CarryInfoButton(tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .semantics(mergeDescendants = true) { contentDescription = percentText },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeWidth = 8.dp,
                )
            }
        }
    }
}

/**
 * Small record control: a 40 dp round chip with a record icon plus its label, inside a 48 dp
 * touch target. Idle shows an outlined chip and "Record"; counting shows a red dot and "Recording".
 * Tapping it toggles counting.
 */
@Composable
private fun RecordChip(isRecording: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (isRecording) "Recording" else "Record"
    val actionText = if (isRecording) "Stop recording" else "Start recording"
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = actionText,
                onClick = onToggle,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = actionText
                stateDescription = if (isRecording) "Recording" else "Not recording"
                role = Role.Button
            }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** 2x2 grid of calorie cards: Total and Active on top, BMR and RMR below. */
@Composable
private fun CalorieGrid(totalKcal: Int, activeKcal: Int, bmrKcal: Int, rmrKcal: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalorieCard(
                kcal = totalKcal,
                label = "Total kcal",
                description = "Total: resting plus active calories",
                modifier = Modifier.weight(1f),
            )
            CalorieCard(
                kcal = activeKcal,
                label = "Active kcal",
                description = "Active: extra calories from walking",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalorieCard(
                kcal = bmrKcal,
                label = "BMR kcal",
                description = "BMR: calories your body burns at complete rest",
                modifier = Modifier.weight(1f),
            )
            CalorieCard(
                kcal = rmrKcal,
                label = "RMR kcal",
                description = "RMR: resting calories for the whole day",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalorieCard(
    kcal: Int,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${formatCount(kcal.toLong())} kcal. $description"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedCountText(
                value = kcal,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
