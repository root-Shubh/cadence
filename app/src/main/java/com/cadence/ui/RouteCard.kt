package com.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cadence.gps.GpsFix
import com.cadence.gps.fitRoute
import com.cadence.gps.formatRouteDistance
import com.cadence.gps.routeDescription
import com.cadence.gps.routeDistanceMeters

/** Shown when there are too few points to draw a line. */
internal const val ROUTE_EMPTY_TEXT = "No route yet. Start Record with GPS on to map your walk."

/**
 * Today's route card: the recorded GPS points drawn as a polyline on a Canvas, with start and end
 * dots. Works offline; no map SDK. Distance is the haversine sum of the points.
 */
@Composable
fun RouteCard(points: List<GpsFix>, modifier: Modifier = Modifier) {
    val distance = remember(points) { routeDistanceMeters(points) }
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.onSurface
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hasRoute = points.size >= 2

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = routeDescription(points.size, distance)
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Today's route",
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
        if (!hasRoute) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ROUTE_EMPTY_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val plotted = fitRoute(points, size.width, size.height, paddingPx = 12f)
                val path = Path()
                plotted.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                val start = plotted.first()
                val end = plotted.last()
                drawCircle(color = startColor, radius = 6.dp.toPx(), center = Offset(start.x, start.y))
                drawCircle(color = endColor, radius = 6.dp.toPx(), center = Offset(end.x, end.y))
            }
        }
        Text(
            text = if (hasRoute) {
                "${formatRouteDistance(distance)} · ${points.size} points"
            } else {
                "0 m · ${points.size} points"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}
