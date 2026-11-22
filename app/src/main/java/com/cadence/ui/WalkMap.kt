package com.cadence.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cadence.maps.DARK_MAP
import com.cadence.maps.GeoPoint
import com.cadence.maps.LIGHT_MAP
import com.cadence.maps.MapFile
import com.cadence.maps.boundsOf
import com.cadence.maps.offlineStyleJson
import java.util.Locale
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** One drawing point on the map and whether the pen was down on the step that reached it. */
data class MapDrawingPoint(val point: GeoPoint, val pen: Boolean)

/**
 * The offline map (STEPW-65) with the day's GPS route and, when it could be placed, the walk
 * drawing on top. [fitKey] changes when the view should re-frame (another day or another map);
 * live updates while walking keep the camera where the user left it. Network access is turned
 * off for MapLibre, so only the local map file and bundled fonts are read.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun WalkMap(
    map: MapFile,
    route: List<GeoPoint>,
    drawing: List<MapDrawingPoint>,
    fitKey: Any,
    description: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()
    val routeColor = MaterialTheme.colorScheme.tertiary.hex()
    val drawColor = MaterialTheme.colorScheme.primary.hex()
    val moveColor = MaterialTheme.colorScheme.outline.hex()
    val endColor = MaterialTheme.colorScheme.error.hex()
    val haloColor = MaterialTheme.colorScheme.surface.hex()

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        // Offline only: MapLibre must not reach any server.
        MapLibre.setConnected(false)
        MapView(context).apply {
            onCreate(null)
            // The Pattern screen scrolls; drags on the map pan the map instead.
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    var style by remember { mutableStateOf<Style?>(null) }
    LaunchedEffect(map.file.absolutePath, dark) {
        style = null
        mapView.getMapAsync { m ->
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setMaxZoomPreference(MAX_ZOOM)
            m.setLatLngBoundsForCameraTarget(
                LatLngBounds.from(map.info.maxLat, map.info.maxLon, map.info.minLat, map.info.minLon),
            )
            val json = offlineStyleJson(map.file.absolutePath, if (dark) DARK_MAP else LIGHT_MAP)
            m.setStyle(Style.Builder().fromJson(json)) { loaded ->
                addOverlays(loaded, routeColor, drawColor, moveColor, endColor, haloColor)
                style = loaded
            }
        }
    }

    // Overlay data; re-framed only when fitKey or the style changes.
    LaunchedEffect(style, route, drawing) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(lineCollection(listOf(route.map { it to true })))
        s.getSourceAs<GeoJsonSource>(SRC_DRAWING)?.setGeoJson(lineCollection(penRuns(drawing)))
        val ends = buildList {
            drawing.firstOrNull()?.let { add(pointFeature(it.point, "start")) }
            drawing.lastOrNull()?.let { add(pointFeature(it.point, "end")) }
        }
        s.getSourceAs<GeoJsonSource>(SRC_ENDS)?.setGeoJson(FeatureCollection.fromFeatures(ends))
    }
    // Re-frame for a new day or map, and once more when the drawing or a route line first arrives
    // (they load after the map; framing only the first GPS fix cut the drawing off).
    LaunchedEffect(style, fitKey, drawing.isNotEmpty(), route.size >= 2) {
        if (style == null) return@LaunchedEffect
        mapView.getMapAsync { m ->
            val all = route + drawing.map { it.point }
            val bounds = boundsOf(all)
            when {
                bounds == null -> m.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(map.info.centerLat, map.info.centerLon),
                        map.info.centerZoom.toDouble().coerceAtLeast(DEFAULT_ZOOM),
                    ),
                )
                bounds.first == bounds.second || all.size < 2 -> m.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(bounds.first.lat, bounds.first.lon), WALK_ZOOM),
                )
                else -> m.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.from(bounds.second.lat, bounds.second.lon, bounds.first.lat, bounds.first.lon),
                        FIT_PADDING_PX,
                    ),
                )
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.semantics { contentDescription = description },
    )
}

private const val SRC_ROUTE = "cadence-route"
private const val SRC_DRAWING = "cadence-drawing"
private const val SRC_ENDS = "cadence-ends"
private const val MAX_ZOOM = 19.0
private const val DEFAULT_ZOOM = 11.0
private const val WALK_ZOOM = 16.0
private const val FIT_PADDING_PX = 64

private fun addOverlays(style: Style, route: String, draw: String, move: String, end: String, halo: String) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    style.addSource(GeoJsonSource(SRC_ROUTE, empty))
    style.addSource(GeoJsonSource(SRC_DRAWING, empty))
    style.addSource(GeoJsonSource(SRC_ENDS, empty))
    style.addLayer(
        LineLayer("route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(route),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("drawing-moves", SRC_DRAWING).withProperties(
            PropertyFactory.lineColor(move),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        ).withFilter(Expression.eq(Expression.get("pen"), Expression.literal(false))),
    )
    style.addLayer(
        LineLayer("drawing-line", SRC_DRAWING).withProperties(
            PropertyFactory.lineColor(draw),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(Expression.eq(Expression.get("pen"), Expression.literal(true))),
    )
    style.addLayer(
        CircleLayer("drawing-ends", SRC_ENDS).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(
                Expression.match(Expression.get("role"), Expression.literal(route), Expression.stop("end", end)),
            ),
            PropertyFactory.circleStrokeColor(halo),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

/** Splits the drawing into runs drawn with the pen down and pen-up moves, each a line. */
private fun penRuns(points: List<MapDrawingPoint>): List<List<Pair<GeoPoint, Boolean>>> {
    val runs = ArrayList<List<Pair<GeoPoint, Boolean>>>()
    var i = 1
    while (i < points.size) {
        val pen = points[i].pen
        val run = arrayListOf(points[i - 1].point to pen)
        while (i < points.size && points[i].pen == pen) {
            run.add(points[i].point to pen)
            i++
        }
        runs.add(run)
    }
    return runs
}

private fun lineCollection(lines: List<List<Pair<GeoPoint, Boolean>>>): FeatureCollection =
    FeatureCollection.fromFeatures(
        lines.filter { it.size >= 2 }.map { line ->
            Feature.fromGeometry(LineString.fromLngLats(line.map { Point.fromLngLat(it.first.lon, it.first.lat) }))
                .apply { addBooleanProperty("pen", line.first().second) }
        },
    )

private fun pointFeature(p: GeoPoint, role: String): Feature =
    Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply { addStringProperty("role", role) }

private fun Color.hex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())

private fun Color.luminanceIsDark(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
