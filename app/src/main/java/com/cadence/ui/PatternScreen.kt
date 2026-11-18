package com.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.patterns.LiveTrack
import com.cadence.patterns.TrackPoint
import com.cadence.patterns.TrackStore
import com.cadence.patterns.fitTrack
import com.cadence.patterns.latestDrawing
import com.cadence.steps.LiveSteps
import java.time.LocalDate
import java.util.Locale
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Space between the drawing and the edge of its square, in pixels. */
private const val TRACK_PADDING_PX = 36f

/**
 * Pattern tab: the walk drawn step by step, with no GPS. Each step moves the pen one stride in
 * the direction the phone faces (rotation sensors), so the line follows the shape walked and can
 * spell words. The drawing grows as you walk and always scales to fit the square. Pen up moves
 * without drawing; New drawing starts again at the centre. Earlier and Later show the last drawing
 * of other days.
 */
@Composable
fun PatternScreen(
    profileId: String = DEFAULT_PROFILE_ID,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { TrackStore(context.filesDir) }
    val today = remember { LocalDate.now().toString() }
    val liveTodayPoints by LiveTrack.points.collectAsState()
    val penDown by LiveTrack.penDown.collectAsState()
    val recording by LiveSteps.tracking.collectAsState()

    val dates by produceState(initialValue = listOf(today), profileId) {
        val saved = withContext(Dispatchers.IO) { store.listDates(profileId) }
        value = (saved + today).distinct().sortedDescending()
    }
    var index by remember { mutableIntStateOf(0) }
    val date = dates.getOrElse(index) { today }

    // Today comes live from the step service; other days from their saved file.
    val savedPoints by produceState<List<TrackPoint>>(initialValue = emptyList(), profileId, date) {
        value = withContext(Dispatchers.IO) { store.read(profileId, date) }
    }
    val allPoints = if (date == today && LiveTrack.day() == today) liveTodayPoints else savedPoints
    val points = latestDrawing(allPoints)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Daily pattern", style = MaterialTheme.typography.titleLarge)
            CarryInfoButton(
                text = "The drawing follows the direction the phone faces, so hold it in front of you. " +
                    "Held steady like that, the step sensor may not recognise your steps: the line is " +
                    "still drawn, but those steps may not be added to your count. For the best count, " +
                    "carry the phone in a pocket or on your arm.",
            )
        }
        Text(
            if (date == today) "Today, $date" else date,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WalkDrawing(points = points, live = date == today && recording)


        Text(
            when {
                points.size < 2 && date == today && !recording ->
                    "Press Record on Today, then walk. The line is drawn step by step in the direction the " +
                        "phone faces. Hold it in front of you, pointing where you walk."
                points.size < 2 -> "No walk drawn for this day yet."
                else -> drawingSummary(points)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (date == today) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { LiveTrack.setPenDown(!penDown) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (penDown) "Pen up" else "Pen down") }
                OutlinedButton(
                    onClick = { LiveTrack.newDrawing() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("New drawing") }
            }
            Text(
                if (penDown) "Pen is down: steps draw." else "Pen is up: steps move without drawing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (index < dates.size - 1) index++ },
                enabled = index < dates.size - 1,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Earlier") }
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Later") }
        }
    }
}

/** The drawing in a square that scales it to fit. Pen-up moves show as a faint dashed line. */
@Composable
private fun WalkDrawing(points: List<TrackPoint>, live: Boolean) {
    val lineColor = MaterialTheme.colorScheme.primary
    val moveColor = MaterialTheme.colorScheme.outlineVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val frameColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "Walk drawing, ${points.size} points" },
    ) {
        drawRect(color = frameColor, style = Stroke(width = 1.dp.toPx()))
        if (points.isEmpty()) return@Canvas
        val screen = fitTrack(points, size.width, size.height, TRACK_PADDING_PX)
        val drawn = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val moved = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
        )
        // Consecutive pen-down steps join into one path, so corners look smooth.
        var path: Path? = null
        for (i in 1 until screen.size) {
            val a = screen[i - 1]
            val b = screen[i]
            if (points[i].pen) {
                val p = path ?: Path().apply { moveTo(a.x, a.y) }.also { path = it }
                p.lineTo(b.x, b.y)
            } else {
                path?.let { drawPath(it, lineColor, style = drawn) }
                path = null
                drawLine(moveColor, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = moved.width, pathEffect = moved.pathEffect)
            }
        }
        path?.let { drawPath(it, lineColor, style = drawn) }
        val start = screen.first()
        val end = screen.last()
        drawCircle(color = startColor, radius = 6.dp.toPx(), center = Offset(start.x, start.y))
        drawCircle(color = endColor, radius = 7.dp.toPx(), center = Offset(end.x, end.y))
    }
}

/** "24 steps, 16.8 m walked, drawing 5.2 m by 3.1 m." */
private fun drawingSummary(points: List<TrackPoint>): String {
    val steps = points.size - 1
    var walked = 0.0
    for (i in 1 until points.size) walked += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    val width = points.maxOf { it.x } - points.minOf { it.x }
    val height = points.maxOf { it.y } - points.minOf { it.y }
    return String.format(Locale.ROOT, "%d steps, %.1f m walked, drawing %.1f m by %.1f m.", steps, walked, width, height)
}
