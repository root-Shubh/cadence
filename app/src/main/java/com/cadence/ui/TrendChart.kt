package com.cadence.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.domain.DayPoint
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToLong

private val CHART_HEIGHT = 160.dp
private val BASELINE_THICKNESS = 1.dp
private val LINE_STROKE = 3.dp
private val GOAL_DASH = 8.dp
private val INSPECT_HEIGHT = 24.dp
private val HEATMAP_CELL_HEIGHT = 64.dp
private const val BAR_FILL = 0.7f
private const val LIGHT_BAR_FILL = 0.3f
private const val MONTH_AXIS_STEP = 5
private const val MOVING_AVERAGE_DAYS = 7
private const val LINE_DRAW_MS = 600
private const val AREA_ALPHA = 0.12f
private const val WEEK_DAYS_IN_HEATMAP = 7
private const val HEAVY_CELL_ALPHA = 0.8f

/**
 * Bar chart of steps per day. Missing days are drawn as a thin baseline segment with no bar.
 * The Week view labels every bar with its weekday ("Today" for the current date, in bold
 * primary). Any other window labels every 5th day plus the last day of the window.
 * [averageSteps] is only used for the accessibility description.
 * Tapping a column shows its date and steps above the chart.
 */
@Composable
fun TrendChart(
    series: List<DayPoint>,
    weekdayLabels: Boolean,
    averageSteps: Double?,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val missingColor = MaterialTheme.colorScheme.outlineVariant
    val fractions = barFractions(series.map { it.steps })
    // Bars grow from zero on first show: this factor eases from 0 to 1 once.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val growth by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(MOTION_BAR_MS),
        label = "barGrowth",
    )
    val todayIso = remember { LocalDate.now().toString() }
    val labelIndices = remember(series.size, weekdayLabels) {
        if (weekdayLabels) emptySet() else axisLabelIndices(series.size, MONTH_AXIS_STEP).toSet()
    }
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val description = buildString {
        append("Bar chart of steps per day over ${series.size} days. ")
        if (averageSteps != null) {
            append("Average ${formatCount(averageSteps.roundToLong())} steps per day.")
        } else {
            append("No average available.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        InspectLabel(series = series, index = selected)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .pointerInput(series) {
                    detectTapGestures { offset ->
                        val tapped = tapIndex(offset.x, size.width.toFloat(), series.size)
                        selected = if (selected == tapped) null else tapped
                    }
                },
        ) {
            val slot = size.width / series.size.coerceAtLeast(1)
            val barWidth = slot * BAR_FILL
            val baselineY = size.height - BASELINE_THICKNESS.toPx() / 2f
            val baselineStroke = BASELINE_THICKNESS.toPx()
            val grow = growth

            series.forEachIndexed { index, point ->
                val left = index * slot + (slot - barWidth) / 2f
                if (point.missing) {
                    drawLine(
                        color = missingColor,
                        start = Offset(index * slot + slot * 0.2f, baselineY),
                        end = Offset(index * slot + slot * 0.8f, baselineY),
                        strokeWidth = baselineStroke,
                    )
                } else {
                    val fraction = fractions[index]
                    val barHeight = (size.height - baselineStroke) * fraction * grow
                    drawRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - baselineStroke - barHeight),
                        size = Size(barWidth, barHeight.coerceAtLeast(0f)),
                    )
                }
            }
            drawLine(
                color = baselineColor,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = baselineStroke,
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            series.forEachIndexed { index, point ->
                val isToday = weekdayLabels && point.dateIso == todayIso
                val text = when {
                    weekdayLabels -> weekdayLabel(point.dateIso, todayIso)
                    index in labelIndices -> LocalDate.parse(point.dateIso).dayOfMonth.toString()
                    else -> ""
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isToday) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontSize = if (weekdayLabels) 12.sp else 9.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Quarter view: light daily bars with a 7-day moving average drawn as a line over an area,
 * and a dashed goal line when [goalPerDay] is set. The line draws on from left to right.
 * Tapping a column shows its date and steps above the chart.
 */
@Composable
fun TrendLineChart(
    series: List<DayPoint>,
    goalPerDay: Int?,
    averageSteps: Double?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val movingAverage = remember(series) { movingAverage(series, MOVING_AVERAGE_DAYS) }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val growth by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(MOTION_BAR_MS),
        label = "quarterBarGrowth",
    )
    val lineDraw by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(LINE_DRAW_MS),
        label = "quarterLineTrim",
    )
    var selected by remember(series) { mutableStateOf<Int?>(null) }

    val scaleMax = max(series.maxOfOrNull { it.steps } ?: 0, goalPerDay ?: 0).coerceAtLeast(1)
    val description = buildString {
        append("Quarter view of steps per day over ${series.size} days, ")
        append("with a 7-day average line. ")
        if (averageSteps != null) {
            append("Average ${formatCount(averageSteps.roundToLong())} steps per day. ")
        }
        if (goalPerDay != null) {
            append("Daily goal ${formatCount(goalPerDay.toLong())} steps shown as a dashed line.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        InspectLabel(series = series, index = selected)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .pointerInput(series) {
                    detectTapGestures { offset ->
                        val tapped = tapIndex(offset.x, size.width.toFloat(), series.size)
                        selected = if (selected == tapped) null else tapped
                    }
                },
        ) {
            val n = series.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val baselineStroke = BASELINE_THICKNESS.toPx()
            val baselineY = size.height - baselineStroke / 2f
            val plotHeight = size.height - baselineStroke
            val grow = growth

            series.forEachIndexed { index, point ->
                if (!point.missing) {
                    val barHeight = plotHeight * (point.steps.toFloat() / scaleMax) * grow
                    drawRect(
                        color = primary.copy(alpha = LIGHT_BAR_FILL),
                        topLeft = Offset(index * slot + slot * 0.15f, plotHeight - barHeight),
                        size = Size(slot * 0.7f, barHeight.coerceAtLeast(0f)),
                    )
                }
            }

            val points = movingAverage.mapIndexedNotNull { index, value ->
                value?.let {
                    val y = plotHeight - plotHeight * (it.toFloat() / scaleMax)
                    Offset(index * slot + slot / 2f, y)
                }
            }
            val visible = trimPolyline(points, lineDraw)
            if (visible.size >= 2) {
                val line = Path().apply {
                    moveTo(visible.first().x, visible.first().y)
                    visible.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = Path().apply {
                    moveTo(visible.first().x, plotHeight)
                    visible.forEach { lineTo(it.x, it.y) }
                    lineTo(visible.last().x, plotHeight)
                    close()
                }
                drawPath(path = area, color = primary.copy(alpha = AREA_ALPHA))
                drawPath(
                    path = line,
                    color = primary,
                    style = Stroke(width = LINE_STROKE.toPx(), cap = StrokeCap.Round),
                )
            }

            goalPerDay?.let { goal ->
                val y = plotHeight - plotHeight * (goal.toFloat() / scaleMax)
                drawLine(
                    color = goalColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = baselineStroke * 2f,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(GOAL_DASH.toPx(), GOAL_DASH.toPx()),
                    ),
                )
            }

            drawLine(
                color = baselineColor,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = baselineStroke,
            )
        }

        if (series.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = dayMonthLabel(series.first().dateIso),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dayMonthLabel(series.last().dateIso),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Seven cells, Monday to Sunday. Each tint is the average steps for that weekday across the
 * window, scaled from 0.15 to 1.0 alpha of the primary colour. Labels show the weekday initial
 * and the average. Each cell has a spoken description.
 */
@Composable
fun WeekdayHeatmap(
    series: List<DayPoint>,
    modifier: Modifier = Modifier,
) {
    val averages = remember(series) { weekdayAverages(series) }
    val maxAverage = averages.filterNotNull().maxOrNull()
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val initials = listOf("M", "T", "W", "T", "F", "S", "S")
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (index in 0 until WEEK_DAYS_IN_HEATMAP) {
            val average = averages[index]
            val alpha = heatmapAlpha(average, maxAverage)
            val spoken = if (average == null) {
                "${names[index]}, no data"
            } else {
                "${names[index]}, average ${formatCount(average.roundToLong())} steps"
            }
            val textColor = if (alpha >= HEAVY_CELL_ALPHA) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(HEATMAP_CELL_HEIGHT)
                    .background(
                        color = if (average == null) emptyColor else primary.copy(alpha = alpha),
                        shape = MaterialTheme.shapes.small,
                    )
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = initials[index],
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = average?.let { compactSteps(it) } ?: "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Single line above a chart: the tapped day's date and steps, or a hint when nothing is picked. */
@Composable
private fun InspectLabel(series: List<DayPoint>, index: Int?) {
    val point = index?.let { series.getOrNull(it) }
    val text = when {
        point == null -> "Tap a day to see its steps"
        point.missing -> "${dayLabelLong(point.dateIso)}: no recording"
        else -> "${dayLabelLong(point.dateIso)}: ${formatCount(point.steps.toLong())} steps"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (point == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(INSPECT_HEIGHT)
            .padding(bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
