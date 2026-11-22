package com.cadence.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.data.DEFAULT_PROFILE_ID
import com.cadence.patterns.DrawSegment
import com.cadence.patterns.LiveTrack
import com.cadence.patterns.TrackPoint
import com.cadence.patterns.TrackStore
import com.cadence.patterns.drawingSegments
import com.cadence.patterns.drawingSizeMeters
import com.cadence.patterns.latestDrawing
import com.cadence.steps.LiveSteps
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext

/** Space between the drawing and the edge of its square, in pixels. */
private const val TRACK_PADDING_PX = 36f

/** How often the live drawing is redrawn while walking (STEPW-64). Steps are 1-2 a second. */
private const val LIVE_REDRAW_MS = 250L

/**
 * Pattern tab: the walk drawn step by step, with no GPS. Each step moves the pen one stride in
 * the direction the phone faces (rotation sensors), so the line follows the shape walked and can
 * spell words. The drawing grows as you walk and always scales to fit the square. Pen up moves
 * without drawing; New drawing starts again at the centre. The row of thumbnails below picks the
 * day. The drawing is smoothed for display; the step count lives on Today only.
 */
@OptIn(FlowPreview::class)
@Composable
fun PatternScreen(
    profileId: String = DEFAULT_PROFILE_ID,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { TrackStore(context.filesDir) }
    val today = remember { LocalDate.now().toString() }
    val penDown by LiveTrack.penDown.collectAsState()
    val recording by LiveSteps.tracking.collectAsState()

    // Today's live drawing, read at most every LIVE_REDRAW_MS instead of on every step.
    var livePoints by remember { mutableStateOf<List<TrackPoint>?>(null) }
    LaunchedEffect(today) {
        suspend fun readLive() = withContext(Dispatchers.Default) {
            if (LiveTrack.day() == today) latestDrawing(LiveTrack.snapshot()) else null
        }
        livePoints = readLive() // at once, without waiting for the first sample window
        LiveTrack.version.sample(LIVE_REDRAW_MS).collect { livePoints = readLive() }
    }

    val dates by produceState(initialValue = listOf(today), profileId) {
        val saved = withContext(Dispatchers.IO) { store.listDates(profileId) }
        value = (saved + today).distinct().sortedDescending()
    }
    var date by remember { mutableStateOf(today) }

    // Each saved day is read from disk once, then kept.
    val savedByDate = remember(profileId) { mutableStateMapOf<String, List<TrackPoint>>() }
    LaunchedEffect(profileId, dates) {
        for (d in dates) {
            if (savedByDate.containsKey(d)) continue
            savedByDate[d] = withContext(Dispatchers.IO) { latestDrawing(store.read(profileId, d)) }
        }
    }
    fun pointsFor(d: String): List<TrackPoint> =
        if (d == today) livePoints ?: savedByDate[d].orEmpty() else savedByDate[d].orEmpty()

    val points = pointsFor(date)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

        WalkDrawing(
            points = points,
            live = date == today && recording,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )

        Text(
            when {
                points.size < 2 && date == today && !recording ->
                    "Press Record on Today, then walk. The line is drawn step by step in the direction the " +
                        "phone faces. Hold it in front of you, pointing where you walk."
                points.size < 2 -> "No walk drawn for this day."
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

        if (dates.size > 1) {
            Text("Days", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dates, key = { it }) { d ->
                    DayThumbnail(
                        date = d,
                        isToday = d == today,
                        points = pointsFor(d),
                        selected = d == date,
                        onClick = { date = d },
                    )
                }
            }
        }
    }
}

/** One day in the gallery: a small drawing and the day. */
@Composable
private fun DayThumbnail(
    date: String,
    isToday: Boolean,
    points: List<TrackPoint>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val label = if (isToday) "Today" else runCatching {
        val d = LocalDate.parse(date)
        "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth}"
    }.getOrDefault(date)
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label drawing${if (selected) ", selected" else ""}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(12.dp)),
        ) {
            WalkDrawing(points = points, live = false, modifier = Modifier.size(88.dp), thumbnail = true)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * The drawing in a square that scales it to fit. Pen-up moves show as a faint dashed line. The
 * stroked paths are built once per change of points or size ([drawWithCache]), not on every frame.
 */
@Composable
private fun WalkDrawing(
    points: List<TrackPoint>,
    live: Boolean,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = false,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val moveColor = MaterialTheme.colorScheme.outlineVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val frameColor = MaterialTheme.colorScheme.surfaceVariant
    val lineWidth: Dp = if (thumbnail) 1.5.dp else 3.dp
    Box(
        modifier = modifier
            .semantics { if (!thumbnail) contentDescription = "Walk drawing, ${drawingSummary(points)}" }
            .drawWithCache {
                val padding = if (thumbnail) 8.dp.toPx() else TRACK_PADDING_PX
                val segments = drawingSegments(points, size.width, size.height, padding)
                val drawnPaths = segments.filter { it.pen }.map { it.toPath() }
                val movePaths = segments.filter { !it.pen }.map { it.toPath() }
                val drawn = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val moved = Stroke(
                    width = (lineWidth / 2).toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
                )
                val start = segments.firstOrNull()?.points?.firstOrNull()
                val end = segments.lastOrNull()?.points?.lastOrNull()
                onDrawBehind {
                    if (!thumbnail) drawRect(color = frameColor, style = Stroke(width = 1.dp.toPx()))
                    movePaths.forEach { drawPath(it, moveColor, style = moved) }
                    drawnPaths.forEach { drawPath(it, lineColor, style = drawn) }
                    val dot = if (thumbnail) 2.5.dp.toPx() else 6.dp.toPx()
                    start?.let { drawCircle(color = startColor, radius = dot, center = Offset(it.x, it.y)) }
                    end?.let { drawCircle(color = endColor, radius = dot * 1.15f, center = Offset(it.x, it.y)) }
                }
            },
    )
}

private fun DrawSegment.toPath(): Path = Path().apply {
    val first = points.first()
    moveTo(first.x, first.y)
    for (k in 1 until points.size) lineTo(points[k].x, points[k].y)
}

/**
 * "Drawing 498 m by 372 m." The drawing shows shape and direction only; steps and distance are on
 * Today, so this screen does not show a second step count (one number, one source).
 */
private fun drawingSummary(points: List<TrackPoint>): String {
    val (w, h) = drawingSizeMeters(points)
    return String.format(Locale.ROOT, "Drawing %.0f m by %.0f m. Steps and distance are on Today.", w, h)
}
