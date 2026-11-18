package com.stepwise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

        Text(
            text = day?.steps?.let { "%,d".format(Locale.US, it) } ?: "-",
            style = MaterialTheme.typography.displayLarge,
        )
        Text("steps today", style = MaterialTheme.typography.labelLarge)

        Text(
            text = day?.let { String.format(Locale.US, "%.2f km", it.distanceM / 1000.0) } ?: "-",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text("distance walked", style = MaterialTheme.typography.labelLarge)

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
            CalorieLine("${Math.round(day.bmrKcal)} kcal", "BMR: calories your body burns at complete rest")
            CalorieLine("${Math.round(day.rmrKcal)} kcal", "RMR: resting calories for the whole day")
            CalorieLine("${Math.round(day.activeKcal)} kcal", "Active: extra calories from walking")
            CalorieLine("${Math.round(day.totalKcal)} kcal", "Total: resting plus active calories")
        }

        if (!trackingActive) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStartTracking, modifier = Modifier.fillMaxWidth()) {
                Text("Start counting")
            }
        }
    }
}

@Composable
private fun CalorieLine(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
