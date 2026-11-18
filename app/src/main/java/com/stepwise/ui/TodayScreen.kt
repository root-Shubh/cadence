package com.stepwise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = todayStatusText(profileMissing, trackingActive),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (sensorMissing) {
            Text(
                text = "This phone has no step counter, so steps cannot be counted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Hero card: the step count on a subtly tinted surface.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = day?.steps?.let { "%,d".format(Locale.US, it) } ?: "-",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "steps today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
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

        if (profileMissing) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Add your profile to see calories",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Calories need your height, weight, age and sex. Set them in Profile.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else if (day != null) {
            CalorieCard("${Math.round(day.bmrKcal)} kcal", "BMR: calories your body burns at complete rest", MaterialTheme.colorScheme.primary)
            CalorieCard("${Math.round(day.rmrKcal)} kcal", "RMR: resting calories for the whole day", MaterialTheme.colorScheme.primary)
            CalorieCard("${Math.round(day.activeKcal)} kcal", "Active: extra calories from walking", MaterialTheme.colorScheme.secondary)
            CalorieCard("${Math.round(day.totalKcal)} kcal", "Total: resting plus active calories", MaterialTheme.colorScheme.secondary)
        }

        if (!trackingActive) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStartTracking,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Start counting")
            }
        }
    }
}

@Composable
private fun CalorieCard(value: String, label: String, valueColor: androidx.compose.ui.graphics.Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
