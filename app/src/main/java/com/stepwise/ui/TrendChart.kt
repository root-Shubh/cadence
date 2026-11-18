package com.stepwise.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepwise.domain.DayPoint
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val CHART_HEIGHT = 160.dp
private val BASELINE_THICKNESS = 1.dp
private const val BAR_FILL = 0.7f

/**
 * Bar chart of steps per day. Missing days are drawn as a thin baseline segment with no bar.
 * The Week view labels bars with the weekday; any other window uses day-of-month.
 * [averageSteps] is only used for the accessibility description.
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
    val description = buildString {
        append("Bar chart of steps per day over ${series.size} days. ")
        if (averageSteps != null) {
            append("Average ${averageSteps.toInt()} steps per day.")
        } else {
            append("No average available.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        ) {
            val slot = size.width / series.size.coerceAtLeast(1)
            val barWidth = slot * BAR_FILL
            val baselineY = size.height - BASELINE_THICKNESS.toPx() / 2f
            val baselineStroke = BASELINE_THICKNESS.toPx()

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
                    val barHeight = (size.height - baselineStroke) * fraction
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
            series.forEach { point ->
                Text(
                    text = axisLabel(point.dateIso, weekdayLabels),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontSize = if (weekdayLabels) 12.sp else 9.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun axisLabel(dateIso: String, weekdayLabels: Boolean): String {
    val date = LocalDate.parse(dateIso)
    return if (weekdayLabels) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
    } else {
        date.dayOfMonth.toString()
    }
}
