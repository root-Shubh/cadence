package com.stepwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stepwise.domain.DailyRecord
import com.stepwise.domain.DayMetrics
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
    history: List<DailyRecord> = emptyList(),
    endDate: LocalDate = LocalDate.now(),
) {
    val startSource = remember { MutableInteractionSource() }
    val steps = day?.steps
    val caloriesKnown = day != null && day.totalKcal > 0.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Everything above the button scrolls; the button stays pinned at the bottom.
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
                StatChipRow(
                    goalValue = goalPercent(steps ?: 0, dailyGoal)?.let { "$it%" } ?: "Set goal",
                    averageValue = sevenDayAverage(history, endDate)
                        ?.let { formatCount(it.toLong()) } ?: "-",
                    streakValue = streakDays(history, endDate, dailyGoal).toString(),
                )
            }

            Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStepCard(steps = steps, dailyGoal = dailyGoal)
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

            Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
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

            if (profileMissing && !caloriesKnown) {
                Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
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
                Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                    CalorieGrid(
                        totalKcal = Math.round(day.totalKcal).toInt(),
                        activeKcal = Math.round(day.activeKcal).toInt(),
                        bmrKcal = Math.round(day.bmrKcal).toInt(),
                        rmrKcal = Math.round(day.rmrKcal).toInt(),
                    )
                }
            }
        }

        if (trackingActive) {
            Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Counting",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartTracking,
                    interactionSource = startSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressScale(startSource),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Start counting")
                }
            }
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

/** Step count on a soft vertical gradient, with a goal progress ring on the right. */
@Composable
private fun HeroStepCard(steps: Int?, dailyGoal: Int?) {
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
