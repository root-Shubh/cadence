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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stepwise.domain.DailyRecord
import com.stepwise.domain.TrendSummary
import com.stepwise.domain.computeTrend
import java.time.LocalDate
import kotlin.math.roundToLong

private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30
private const val QUARTER_DAYS = 90
private const val DAYS_PER_WEEK = 7
private const val INSIGHT_LIMIT = 6

/**
 * Trend view with Week (7), Month (30) and Quarter (90) windows. Summary figures and the chart
 * are computed by [computeTrend] for the selected window ending on [endDate]. [dailyGoal] and
 * [weeklyGoal] feed the goal line and the insight cards. Both are optional.
 */
@Composable
fun TrendScreen(
    history: List<DailyRecord>,
    endDate: LocalDate,
    modifier: Modifier = Modifier,
    dailyGoal: Int? = null,
    weeklyGoal: Int? = null,
) {
    var windowDays by remember { mutableStateOf(WEEK_DAYS) }
    val insights = remember(history, endDate, dailyGoal, weeklyGoal) {
        buildInsights(history, endDate.toString(), dailyGoal, weeklyGoal).take(INSIGHT_LIMIT)
    }
    // Goal line on the quarter chart: the daily goal, or the weekly goal spread over 7 days.
    val goalPerDay = dailyGoal?.takeIf { it > 0 }
        ?: weeklyGoal?.takeIf { it > 0 }?.let { it / DAYS_PER_WEEK }

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
                listOf(WEEK_DAYS to "Week", MONTH_DAYS to "Month", QUARTER_DAYS to "Quarter")
                    .forEach { (days, label) ->
                        FilterChip(
                            selected = windowDays == days,
                            onClick = { windowDays = days },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f).height(48.dp),
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
                        windowDays = days,
                        goalPerDay = goalPerDay,
                    )
                }

                InsightsSection(insights = insights)
            }
        }
    }
}

@Composable
private fun TrendContent(trend: TrendSummary, windowDays: Int, goalPerDay: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
            if (windowDays == QUARTER_DAYS) {
                TrendLineChart(
                    series = trend.series,
                    goalPerDay = goalPerDay,
                    averageSteps = trend.avgSteps,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TrendChart(
                    series = trend.series,
                    weekdayLabels = windowDays == WEEK_DAYS,
                    averageSteps = trend.avgSteps,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Average by weekday",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekdayHeatmap(series = trend.series, modifier = Modifier.fillMaxWidth())
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Reveal(index = 3, modifier = Modifier.weight(1f)) {
                StatCard(
                    label = "Avg steps / day",
                    value = trend.avgSteps?.let { formatCount(it.roundToLong()) } ?: "-",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    tone = Tone.PRIMARY,
                )
            }
            Reveal(index = 4, modifier = Modifier.weight(1f)) {
                StatCard(
                    label = "Avg kcal / day",
                    value = trend.avgKcal?.let { formatKcal(it) } ?: "-",
                    icon = Icons.Filled.LocalFireDepartment,
                    tone = Tone.SECONDARY,
                )
            }
        }
        Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Total steps",
                value = formatCount(trend.totalSteps),
                icon = Icons.Filled.Route,
                tone = Tone.TERTIARY,
            )
        }
        Reveal(index = 6, modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "Change vs previous ${trend.windowDays} days",
                value = trend.percentChangeSteps?.let { formatPercent(it) },
                icon = Icons.AutoMirrored.Filled.ShowChart,
                tone = Tone.PRIMARY,
            )
        }
    }
}

/** Insight cards under the chart, staggered in. Shows a hint when there is nothing to say. */
@Composable
private fun InsightsSection(insights: List<Insight>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Reveal(index = 7, modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Insights", icon = Icons.Filled.Lightbulb)
        }
        if (insights.isEmpty()) {
            Reveal(index = 8, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Keep recording a few days to see insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            insights.forEachIndexed { index, insight ->
                Reveal(index = 8 + index, modifier = Modifier.fillMaxWidth()) {
                    InsightCard(insight = insight)
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val container = when (insight.tone) {
        InsightTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
        InsightTone.NUDGE -> MaterialTheme.colorScheme.tertiaryContainer
        InsightTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (insight.tone) {
        InsightTone.POSITIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        InsightTone.NUDGE -> MaterialTheme.colorScheme.onTertiaryContainer
        InsightTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chipTone = when (insight.tone) {
        InsightTone.POSITIVE -> Tone.PRIMARY
        InsightTone.NUDGE -> Tone.TERTIARY
        InsightTone.NEUTRAL -> Tone.SECONDARY
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(
                icon = insightIcon(insight.kind),
                tone = chipTone,
                containerColor = MaterialTheme.colorScheme.surface,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
            }
        }
    }
}

private fun insightIcon(kind: InsightKind): ImageVector = when (kind) {
    InsightKind.WEEKDAY_PATTERN -> Icons.Filled.CalendarMonth
    InsightKind.BEST_DAY -> Icons.Filled.EmojiEvents
    InsightKind.STREAK -> Icons.Filled.LocalFireDepartment
    InsightKind.TREND -> Icons.AutoMirrored.Filled.TrendingUp
    InsightKind.GOAL_PACE -> Icons.Filled.Flag
    InsightKind.CONSISTENCY -> Icons.Filled.Event
    InsightKind.CALORIES -> Icons.Filled.Bolt
    InsightKind.ADVICE -> Icons.Filled.Lightbulb
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
