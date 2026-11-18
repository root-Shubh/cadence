package com.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.gps.GpsFix
import com.cadence.gps.RouteRecording
import com.cadence.gps.fitRoute
import com.cadence.gps.formatRouteDistance
import com.cadence.gps.routeDistanceMeters
import com.cadence.patterns.DayPatterns
import com.cadence.patterns.MAX_ZOOM
import com.cadence.patterns.MIN_ZOOM
import com.cadence.patterns.PatternStore
import com.cadence.patterns.activeHours
import com.cadence.patterns.bucketCounts
import com.cadence.patterns.bucketMinutesForZoom
import com.cadence.patterns.peakIndex
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Shown when no day has a file yet. */
internal const val PATTERN_EMPTY_TEXT = "No daily patterns yet. Walk with Record on to build them."

/** Shown on a day page with no kept GPS points. */
internal const val ROUTE_EMPTY_TEXT = "No route for this day. Record with GPS on to map your walk."

private const val LIST_REFRESH_MS = 30_000L
private val GALLERY_HEIGHT = 480.dp
private val CHART_HEIGHT = 170.dp
private val ROUTE_HEIGHT = 170.dp
private val MIN_BAR_WIDTH = 6.dp
private val LABEL_HEIGHT = 20.dp

/**
 * Daily pattern gallery: a horizontal pager with one day per page, most recent first. Each day
 * shows an hourly step chart (pinch, or the buttons, zooms to finer buckets), the GPS route, and
 * a totals row. Days come from the files in patterns/<profileId>/, read off the main thread.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyPatternGallery(
    profileId: String,
    recording: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { PatternStore(context.filesDir) }
    val dates by produceState<List<String>?>(initialValue = null, profileId, recording) {
        while (true) {
            value = withContext(Dispatchers.IO) { store.listDates(profileId) }
            delay(LIST_REFRESH_MS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(GALLERY_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Daily pattern",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val list = dates
        when {
            list == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading patterns", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = PATTERN_EMPTY_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { list.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    DayPatternPage(store = store, profileId = profileId, date = list[page])
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    list.indices.forEach { i ->
                        val selected = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.small,
                                ),
                        )
                    }
                }
                Text(
                    text = dayLabel(list[pagerState.currentPage]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DayPatternPage(store: PatternStore, profileId: String, date: String) {
    val day by produceState<DayPatterns?>(initialValue = null, profileId, date) {
        value = withContext(Dispatchers.IO) { store.read(profileId, date) }
    }
    val loaded = day
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val zone = remember { ZoneId.systemDefault() }
    val isToday = date == LocalDate.now().toString()
    var zoom by remember(date) { mutableFloatStateOf(MIN_ZOOM.toFloat()) }
    val bucketMinutes = bucketMinutesForZoom(zoom.toDouble())
    // Totals and the peak always come from hourly buckets, so they match at every zoom level.
    val hourly = remember(loaded, date) { bucketCounts(loaded.stepMillis, date, zone, 60) }
    val buckets = remember(loaded, date, bucketMinutes) {
        bucketCounts(loaded.stepMillis, date, zone, bucketMinutes)
    }
    val totalSteps = hourly.sum()
    val peak = peakIndex(hourly)
    val rawPoints = loaded.points.map { GpsFix(it.lat, it.lon, it.accuracyM, it.timeMillis) }
    val smoothPoints = loaded.points.map { GpsFix(it.smoothLat, it.smoothLon, 0f, it.timeMillis) }
    val distance = remember(rawPoints) { routeDistanceMeters(rawPoints) }
    val gps by RouteRecording.status.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (peak < 0) {
                    "No steps yet"
                } else {
                    "Peak hour ${"%02d".format(peak)}:00, ${formatCount(hourly[peak])} steps"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { zoom = (zoom / 2f).coerceAtLeast(MIN_ZOOM.toFloat()) },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
            IconButton(
                onClick = { zoom = (zoom * 2f).coerceAtMost(MAX_ZOOM.toFloat()) },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
        }

        val unit = if (bucketMinutes == 60) "hour" else "$bucketMinutes minutes"
        val peakText = if (peak < 0) "No steps yet." else
            "Peak hour ${"%02d".format(peak)} with ${formatCount(hourly[peak])} steps."
        HourChart(
            buckets = buckets,
            bucketMinutes = bucketMinutes,
            currentMinute = if (isToday) LocalTime.now().let { it.hour * 60 + it.minute } else -1,
            onPinch = { factor -> zoom = (zoom * factor).coerceIn(MIN_ZOOM.toFloat(), MAX_ZOOM.toFloat()) },
            description = "Steps by $unit. Total ${formatCount(totalSteps)} steps. $peakText",
        )

        if (isToday) {
            Text(
                text = gps.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RouteMap(points = smoothPoints, rawCount = rawPoints.size, distance = distance)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TotalCell(label = "Steps", value = formatCount(totalSteps))
            TotalCell(
                label = "Route",
                value = if (rawPoints.size < 2) "0 m" else formatRouteDistance(distance),
            )
            TotalCell(label = "Active hours", value = activeHours(hourly).toString())
        }
    }
}

@Composable
private fun HourChart(
    buckets: LongArray,
    bucketMinutes: Int,
    currentMinute: Int,
    onPinch: (Float) -> Unit,
    description: String,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val currentColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)
    val scroll = rememberScrollState()
    val maxValue = (buckets.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val currentIndex = if (currentMinute >= 0) currentMinute / bucketMinutes else -1

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minContent = MIN_BAR_WIDTH * buckets.size
        val contentWidth = if (maxWidth > minContent) maxWidth else minContent
        Text(
            text = "Steps per ${if (bucketMinutes == 60) "hour" else "$bucketMinutes min"}, max ${formatCount(maxValue)}",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .horizontalScroll(scroll)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) onPinch(zoomChange)
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxSize()
                    .semantics { contentDescription = description },
            ) {
                val labelPx = LABEL_HEIGHT.toPx()
                val plotH = size.height - labelPx
                val slot = size.width / buckets.size
                val barW = slot * 0.7f
                drawLine(
                    color = axisColor,
                    start = Offset(0f, plotH),
                    end = Offset(size.width, plotH),
                    strokeWidth = 1.dp.toPx(),
                )
                buckets.forEachIndexed { i, value ->
                    if (value <= 0L) return@forEachIndexed
                    val h = (value.toFloat() / maxValue) * plotH
                    val x = i * slot + (slot - barW) / 2f
                    drawRect(color = barColor, topLeft = Offset(x, plotH - h), size = Size(barW, h))
                    if (i == currentIndex) {
                        drawRect(
                            color = currentColor,
                            topLeft = Offset(x, plotH - h),
                            size = Size(barW, h),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                if (currentIndex in buckets.indices && buckets[currentIndex] == 0L) {
                    val x = currentIndex * slot + slot / 2f
                    drawLine(currentColor, Offset(x, 0f), Offset(x, plotH), strokeWidth = 1.dp.toPx())
                }
                listOf(0, 6, 12, 18, 23).forEach { hour ->
                    val index = hour * 60 / bucketMinutes
                    val text = hour.toString()
                    val width = measurer.measure(text, labelStyle).size.width
                    val x = (index * slot + slot / 2f - width / 2f).coerceIn(0f, size.width - width)
                    drawText(measurer, text, topLeft = Offset(x, plotH + 4.dp.toPx()), style = labelStyle)
                }
            }
        }
        Text(
            text = "Hour of day",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}

@Composable
private fun RouteMap(points: List<GpsFix>, rawCount: Int, distance: Double) {
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = if (rawCount < 2) {
        ROUTE_EMPTY_TEXT
    } else {
        "Route: ${formatRouteDistance(distance)}, $rawCount GPS points"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROUTE_HEIGHT)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (rawCount < 2) {
            Text(
                text = ROUTE_EMPTY_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                textAlign = TextAlign.Center,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val plotted = fitRoute(points, size.width, size.height, paddingPx = 16.dp.toPx())
                val path = Path()
                plotted.forEachIndexed { index, p ->
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val start = plotted.first()
                val end = plotted.last()
                drawCircle(color = startColor, radius = 6.dp.toPx(), center = Offset(start.x, start.y))
                drawCircle(color = endColor, radius = 6.dp.toPx(), center = Offset(end.x, end.y))
            }
        }
    }
}

@Composable
private fun TotalCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayLabel(date: String): String =
    runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))
    }.getOrDefault(date)
