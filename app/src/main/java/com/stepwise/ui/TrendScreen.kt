package com.stepwise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stepwise.domain.DailyRecord
import com.stepwise.domain.TrendSummary
import com.stepwise.domain.computeTrend
import java.time.LocalDate
import kotlin.math.roundToLong

private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30

/**
 * Trend view with a Week (7 days) / Month (30 days) choice. Summary figures and the
 * bar chart are computed by [computeTrend] for the selected window ending on [endDate].
 */
@Composable
fun TrendScreen(
    history: List<DailyRecord>,
    endDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    var windowDays by remember { mutableStateOf(WEEK_DAYS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
                Text("Trend", style = MaterialTheme.typography.headlineSmall)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().selectableGroup(),
            ) {
                listOf(WEEK_DAYS to "Week", MONTH_DAYS to "Month").forEach { (days, label) ->
                    FilterChip(
                        selected = windowDays == days,
                        onClick = { windowDays = days },
                        label = { Text(label) },
                        modifier = Modifier.height(48.dp),
                    )
                }
            }

            if (history.isEmpty()) {
                Text(
                    text = "No recordings yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Switching the window crossfades the whole block, so the bars regrow from zero.
                AnimatedContent(
                    targetState = windowDays,
                    transitionSpec = {
                        fadeIn(tween(MOTION_FAST_MS)) togetherWith fadeOut(tween(MOTION_FAST_MS))
                    },
                    label = "trend window",
                ) { days ->
                    TrendContent(
                        trend = computeTrend(history, endDate, days),
                        weekdayLabels = days == WEEK_DAYS,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendContent(trend: TrendSummary, weekdayLabels: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
            TrendChart(
                series = trend.series,
                weekdayLabels = weekdayLabels,
                averageSteps = trend.avgSteps,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Reveal(index = 2, modifier = Modifier.weight(1f)) {
                StatCard(
                    label = "Avg steps / day",
                    value = trend.avgSteps?.let { formatCount(it.roundToLong()) } ?: "-",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    tone = Tone.PRIMARY,
                )
            }
            Reveal(index = 3, modifier = Modifier.weight(1f)) {
                StatCard(
                    label = "Avg kcal / day",
                    value = trend.avgKcal?.let { formatKcal(it) } ?: "-",
                    icon = Icons.Filled.LocalFireDepartment,
                    tone = Tone.SECONDARY,
                )
            }
        }
        Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Total steps",
                value = formatCount(trend.totalSteps),
                icon = Icons.Filled.Route,
                tone = Tone.TERTIARY,
            )
        }
        Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Change vs previous ${trend.windowDays} days",
                value = trend.percentChangeSteps?.let { formatPercent(it) },
                icon = Icons.AutoMirrored.Filled.ShowChart,
                tone = Tone.PRIMARY,
            )
        }
    }
}

/** Tile with an icon chip, a coloured accent bar on the left, a label and a value. */
@Composable
private fun StatCard(
    label: String,
    value: String?,
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(tone.accent()),
            )
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconChip(icon = icon, tone = tone)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (value != null) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = "no earlier data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
