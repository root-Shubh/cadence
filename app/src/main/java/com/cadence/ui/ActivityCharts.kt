package com.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cadence.activity.HOUR_BRISK
import com.cadence.activity.HOUR_MODERATE
import com.cadence.activity.HOUR_PEAK
import com.cadence.activity.Heat
import com.cadence.activity.WALK_MIN_STEPS
import com.cadence.activity.QUARTER_BRISK
import com.cadence.activity.QUARTER_MODERATE
import com.cadence.activity.QUARTER_PEAK
import com.cadence.activity.busiestHour
import com.cadence.activity.clockOf
import com.cadence.activity.findWalks
import com.cadence.activity.hourHeat
import com.cadence.activity.hourTotals
import com.cadence.activity.periodOfDay
import com.cadence.activity.quarterHeat
import com.cadence.activity.quarterHourTotals
import com.cadence.activity.untimedSteps
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The heat ramp for the current theme, LIGHT..PEAK. */
@Composable
private fun heatRamp(): List<Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return (if (dark) HeatPalette.dark else HeatPalette.light).map { Color(it) }
}

private fun heatColor(heat: Heat, ramp: List<Color>, empty: Color): Color =
    if (heat == Heat.NONE) empty else ramp[heat.ordinal - 1]

/**
 * Today's steps by time of day (STEPW-63): a 24-hour strip of quarter-hour bars, height and colour
 * by steps, then the day's walks. Touch or drag across the strip to read a bar.
 */
@Composable
fun ActivityTimelineCard(slots: Map<Int, Int>, modifier: Modifier = Modifier) {
    val quarters = remember(slots) { quarterHourTotals(slots) }
    val walks = remember(slots) { findWalks(slots) }
    val untimed = untimedSteps(slots)
    var selected by remember { mutableStateOf<Int?>(null) }
    val ramp = heatRamp()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activity by time of day", style = MaterialTheme.typography.titleMedium)
            val pick = selected
            Text(
                text = if (pick != null) {
                    "${clockOf(pick * 15)}–${clockOf(pick * 15 + 15)} · ${formatCount(quarters[pick].toLong())} steps"
                } else {
                    "Each bar is 15 minutes. Touch a bar to see its steps."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (quarters.all { it == 0 } && untimed == 0) {
                Text(
                    "No steps saved yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            QuarterBars(
                quarters = quarters,
                selected = selected,
                onSelect = { selected = it },
                ramp = ramp,
                description = timelineDescription(quarters, walks.size),
            )
            HourAxis()
            HeatLegend(
                ramp = ramp,
                labels = heatLabels(QUARTER_MODERATE, QUARTER_BRISK, QUARTER_PEAK),
                unit = "steps per 15 min",
            )
            if (walks.isEmpty()) {
                Text(
                    "No walks yet: a walk is at least $WALK_MIN_STEPS steps at a steady pace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Walks", style = MaterialTheme.typography.labelLarge)
                walks.forEach { walk ->
                    val perQuarter = walk.steps * 15 / walk.minutes.coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.size(12.dp)
                                .background(heatColor(quarterHeat(perQuarter), ramp, Color.Transparent), RoundedCornerShape(3.dp)),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(periodOfDay(walk.startMinute), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${clockOf(walk.startMinute)}–${clockOf(walk.endMinute)} · ${walk.minutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${formatCount(walk.steps.toLong())} steps", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Walk times are to the nearest 5 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (untimed > 0) {
                Text(
                    "${formatCount(untimed.toLong())} of today's steps have no time of day: they were counted " +
                        "while Cadence was not running, or before this version. They are in the day total, not in the chart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuarterBars(
    quarters: IntArray,
    selected: Int?,
    onSelect: (Int) -> Unit,
    ramp: List<Color>,
    description: String,
) {
    // Cards use surfaceVariant, so empty cells and bar stubs need a visible but quiet tone.
    val empty = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ring = MaterialTheme.colorScheme.onSurface
    val count = quarters.size
    fun indexAt(x: Float, width: Float): Int = (x / width * count).toInt().coerceIn(0, count - 1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = description }
            .pointerInput(count) { detectTapGestures { onSelect(indexAt(it.x, size.width.toFloat())) } }
            .pointerInput(count) {
                detectHorizontalDragGestures { change, _ -> onSelect(indexAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        val gap = 1.dp.toPx()
        val barW = (size.width - gap * (count - 1)) / count
        // Fixed floor for the scale, so a quiet day does not look like a busy one.
        val maxV = maxOf(quarters.maxOrNull() ?: 0, QUARTER_PEAK).toFloat()
        val minH = 2.dp.toPx()
        // Recessive guides at 06, 12 and 18.
        for (h in listOf(6, 12, 18)) {
            val x = h * 4 * (barW + gap) - gap / 2
            drawLine(grid.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        quarters.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            val h = if (v > 0) maxOf(minH, v / maxV * size.height) else minH
            val color = if (v > 0) heatColor(quarterHeat(v), ramp, empty) else empty
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(minOf(barW / 2, 2.dp.toPx())),
            )
            if (i == selected) {
                drawRoundRect(
                    color = ring,
                    topLeft = Offset(x - gap, size.height - h - gap),
                    size = Size(barW + 2 * gap, h + gap),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00", "06", "12", "18", "24").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun heatLabels(moderate: Int, brisk: Int, peak: Int): List<String> = listOf(
    "Light, under ${formatCount(moderate.toLong())}",
    "Moderate, ${formatCount(moderate.toLong())}–${formatCount((brisk - 1).toLong())}",
    "Brisk, ${formatCount(brisk.toLong())}–${formatCount((peak - 1).toLong())}",
    "Peak, ${formatCount(peak.toLong())}+",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatLegend(ramp: List<Color>, labels: List<String>, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(ramp[i], RoundedCornerShape(2.dp)))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Screen-reader text for the strip: total timed steps, busiest quarter hour and walk count. */
private fun timelineDescription(quarters: IntArray, walkCount: Int): String {
    val total = quarters.sum()
    val best = quarters.indices.maxByOrNull { quarters[it] }
    val busiest = if (best != null && quarters[best] > 0) {
        ", busiest ${clockOf(best * 15)} to ${clockOf(best * 15 + 15)} with ${formatCount(quarters[best].toLong())} steps"
    } else {
        ""
    }
    return "Steps by quarter hour today: ${formatCount(total.toLong())} timed steps$busiest, $walkCount walks."
}

private const val HEAT_CELL_DP = 22
private const val HEAT_GAP_DP = 2
private const val HEAT_LABEL_DP = 40

/**
 * Steps per hour for the last days (STEPW-63): one row per day, one cell per hour, shaded by
 * steps. [days] runs oldest first. Touch a cell to read it.
 */
@Composable
fun HourlyHeatmapCard(days: List<Pair<LocalDate, Map<Int, Int>>>, modifier: Modifier = Modifier) {
    val rows = remember(days) { days.map { (date, slots) -> date to hourTotals(slots) } }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val ramp = heatRamp()
    // Cards use surfaceVariant, so empty cells and bar stubs need a visible but quiet tone.
    val empty = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val ring = MaterialTheme.colorScheme.onSurface
    val busiest = remember(rows) { busiestHour(rows.map { it.second }) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Steps by hour", style = MaterialTheme.typography.titleMedium)
            val pick = selected
            Text(
                text = if (pick != null) {
                    val (row, hour) = pick
                    val date = rows[row].first
                    "${dayLabel(date)} ${date.dayOfMonth} · ${clockOf(hour * 60)}–${clockOf(hour * 60 + 60)} · " +
                        "${formatCount(rows[row].second[hour].toLong())} steps"
                } else {
                    "Last ${rows.size} days. Touch a cell to see its steps."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                Column(
                    modifier = Modifier.width(HEAT_LABEL_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(HEAT_GAP_DP.dp),
                ) {
                    rows.forEach { (date, _) ->
                        Box(Modifier.height(HEAT_CELL_DP.dp), contentAlignment = Alignment.CenterStart) {
                            Text(dayLabel(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val gridHeight = (HEAT_CELL_DP * rows.size + HEAT_GAP_DP * (rows.size - 1).coerceAtLeast(0)).dp
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(gridHeight)
                        .semantics { contentDescription = heatmapDescription(rows, busiest) }
                        .pointerInput(rows.size) {
                            detectTapGestures { p ->
                                if (rows.isEmpty()) return@detectTapGestures
                                val hour = (p.x / size.width * 24).toInt().coerceIn(0, 23)
                                val row = (p.y / size.height * rows.size).toInt().coerceIn(0, rows.size - 1)
                                selected = row to hour
                            }
                        },
                ) {
                    val gap = HEAT_GAP_DP.dp.toPx()
                    val cellW = (size.width - gap * 23) / 24
                    val cellH = HEAT_CELL_DP.dp.toPx()
                    rows.forEachIndexed { r, (_, hours) ->
                        for (h in 0 until 24) {
                            val topLeft = Offset(h * (cellW + gap), r * (cellH + gap))
                            drawRoundRect(
                                color = heatColor(hourHeat(hours[h]), ramp, empty),
                                topLeft = topLeft,
                                size = Size(cellW, cellH),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            if (selected == r to h) {
                                drawRoundRect(
                                    color = ring,
                                    topLeft = topLeft,
                                    size = Size(cellW, cellH),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
            HourAxis(modifier = Modifier.padding(start = HEAT_LABEL_DP.dp))
            HeatLegend(ramp = ramp, labels = heatLabels(HOUR_MODERATE, HOUR_BRISK, HOUR_PEAK), unit = "steps per hour")
            busiest?.let {
                Text(
                    "Most active hour: ${clockOf(it * 60)}–${clockOf(it * 60 + 60)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (rows.any { (_, hours) -> hours.all { it == 0 } }) {
                Text(
                    "Empty rows are days with no steps, or saved before this version, which had no time of day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dayLabel(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun heatmapDescription(rows: List<Pair<LocalDate, IntArray>>, busiest: Int?): String {
    val total = rows.sumOf { it.second.sum() }
    val hour = busiest?.let { ", most active hour ${clockOf(it * 60)} to ${clockOf(it * 60 + 60)}" } ?: ""
    return "Steps by hour for the last ${rows.size} days: ${formatCount(total.toLong())} timed steps$hour."
}
