package com.stepwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stepwise.domain.DayMetrics
import java.util.Locale

@Composable
fun TodayScreen(
    day: DayMetrics?,
    profileMissing: Boolean,
    trackingActive: Boolean,
    onStartTracking: () -> Unit,
    modifier: Modifier = Modifier,
    sensorMissing: Boolean = false,
) {
    val startSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = todayStatusText(profileMissing, trackingActive),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (sensorMissing) {
            Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This phone has no step counter, so steps cannot be counted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
            HeroStepCard(steps = day?.steps)
        }

        Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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

        if (profileMissing) {
            Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconChip(icon = Icons.Filled.Person, tone = Tone.SECONDARY)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        } else if (day != null) {
            CalorieCard(
                kcal = Math.round(day.bmrKcal).toInt(),
                label = "BMR: calories your body burns at complete rest",
                icon = Icons.Filled.LocalFireDepartment,
                tone = Tone.PRIMARY,
                index = 4,
            )
            CalorieCard(
                kcal = Math.round(day.rmrKcal).toInt(),
                label = "RMR: resting calories for the whole day",
                icon = Icons.Filled.LocalFireDepartment,
                tone = Tone.PRIMARY,
                index = 5,
            )
            CalorieCard(
                kcal = Math.round(day.activeKcal).toInt(),
                label = "Active: extra calories from walking",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                tone = Tone.SECONDARY,
                index = 6,
            )
            CalorieCard(
                kcal = Math.round(day.totalKcal).toInt(),
                label = "Total: resting plus active calories",
                icon = Icons.Filled.LocalFireDepartment,
                tone = Tone.SECONDARY,
                index = 7,
            )
        }

        if (!trackingActive) {
            Reveal(index = 8, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartTracking,
                    interactionSource = startSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(startSource),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Start counting")
                }
            }
        }
    }
}

/** Step count on a soft vertical gradient built from the palette's container tokens. */
@Composable
private fun HeroStepCard(steps: Int?) {
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(gradient)
            .padding(20.dp),
    ) {
        Column {
            if (steps != null) {
                AnimatedCountText(
                    value = steps,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.displayLarge,
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
    }
}

@Composable
private fun CalorieCard(
    kcal: Int,
    label: String,
    icon: ImageVector,
    tone: Tone,
    index: Int,
) {
    Reveal(index = index, modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconChip(icon = icon, tone = tone)
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedCountText(
                        value = kcal,
                        suffix = " kcal",
                        style = MaterialTheme.typography.titleLarge,
                        color = tone.accent(),
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
