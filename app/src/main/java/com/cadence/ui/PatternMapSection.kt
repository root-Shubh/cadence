package com.cadence.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cadence.maps.GeoPoint
import com.cadence.maps.MAP_ATTRIBUTION
import com.cadence.maps.MapFile
import com.cadence.maps.MapImportResult
import com.cadence.maps.MapStore
import com.cadence.maps.anchorDrawing
import com.cadence.maps.drawingOnMap
import com.cadence.patterns.PatternStore
import com.cadence.patterns.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How often today's route is re-read while the map is shown. */
private const val ROUTE_REFRESH_MS = 10_000L

/**
 * The map view of the Pattern tab (STEPW-65): the offline map with the day's GPS route and the
 * walk drawing placed on it, the map's name and attribution, and a picker to switch or import maps.
 */
@Composable
fun PatternMapSection(
    profileId: String,
    date: String,
    isToday: Boolean,
    drawing: List<TrackPoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { MapStore(context) }
    val scope = rememberCoroutineScope()
    var mapsVersion by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    val maps by produceState<Pair<List<MapFile>, MapFile?>?>(initialValue = null, mapsVersion) {
        value = withContext(Dispatchers.IO) {
            runCatching { store.ensureBundled() }
            store.list() to store.active()
        }
    }

    // The day's GPS route, re-read every few seconds for today (RouteRecording appends while walking).
    var route by remember(date) { mutableStateOf<List<GeoPoint>>(emptyList()) }
    LaunchedEffect(profileId, date) {
        val patterns = PatternStore(context.filesDir)
        while (true) {
            route = withContext(Dispatchers.IO) {
                patterns.read(profileId, date)?.points.orEmpty()
                    .map { GeoPoint(it.smoothLat, it.smoothLon, it.timeMillis) }
            }
            if (!isToday) break
            delay(ROUTE_REFRESH_MS)
        }
    }

    val anchor = remember(drawing, route) { anchorDrawing(drawing, route) }
    val drawingOnMap = remember(drawing, anchor) {
        if (anchor == null) emptyList() else drawingOnMap(drawing, anchor).zip(drawing) { g, t -> MapDrawingPoint(g, t.pen) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = "Importing map…"
            val result = withContext(Dispatchers.IO) { store.import(uri) }
            message = when (result) {
                is MapImportResult.Imported -> "Imported ${result.map.label}. It is now the active map."
                is MapImportResult.Rejected -> result.reason
            }
            mapsVersion++
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val active = maps?.second
        if (active == null) {
            Text(
                if (maps == null) "Loading the map…" else "No map file. Import a .pmtiles map to see walks on a map.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            WalkMap(
                map = active,
                route = route,
                drawing = drawingOnMap,
                fitKey = date to active.fileName,
                description = mapDescription(route.size, anchor != null),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Text(
                placementText(route.size, drawing.size >= 2, anchor?.gapMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Map: ${active.label}. Works offline. $MAP_ATTRIBUTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change map") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    if (picking) {
        MapPickerDialog(
            maps = maps?.first.orEmpty(),
            activeName = maps?.second?.fileName,
            onSelect = { store.setActive(it.fileName); mapsVersion++ },
            onDelete = { m -> scope.launch { withContext(Dispatchers.IO) { store.delete(m) }; mapsVersion++ } },
            onImport = { picking = false; importLauncher.launch(arrayOf("*/*")) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun MapPickerDialog(
    maps: List<MapFile>,
    activeName: String?,
    onSelect: (MapFile) -> Unit,
    onDelete: (MapFile) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                maps.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = m.fileName == activeName, role = Role.RadioButton, onClick = { onSelect(m) }),
                    ) {
                        RadioButton(selected = m.fileName == activeName, onClick = null)
                        Text(m.label, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        if (!m.bundled) {
                            IconButton(onClick = { onDelete(m) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${m.label}")
                            }
                        }
                    }
                }
                Text(
                    "Import a map file in PMTiles format (vector, Protomaps basemap), for example an extract " +
                        "of your city made with the pmtiles tool. The file stays on this phone; Cadence never downloads maps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onImport) { Text("Import map file") } },
    )
}

/** What the caption under the map says about the drawing's placement. */
internal fun placementText(routeFixes: Int, hasDrawing: Boolean, gapMillis: Long?): String {
    val route = when {
        routeFixes == 0 -> ""
        routeFixes == 1 -> "Only one GPS fix on this day, so there is no route line. "
        else -> "Blue line: the GPS route ($routeFixes fixes). "
    }
    val pinnedTo = when {
        gapMillis == null -> ""
        gapMillis < 60_000L -> "a GPS fix taken during the walk"
        else -> "the nearest GPS fix, ${gapMillis / 60_000} min from the walk"
    }
    return when {
        !hasDrawing && routeFixes == 0 -> "No walk recorded for this day."
        routeFixes == 0 -> "No GPS fix on this day, so the drawing cannot be placed on the map. GPS needs open sky and rarely fixes indoors."
        !hasDrawing -> route.trim()
        gapMillis == null -> route + "The drawing is too far in time from any GPS fix to be placed."
        else -> route + "Green line: the drawing, pinned to $pinnedTo. It is drawn from the compass, so it can drift from the streets."
    }
}

private fun mapDescription(routePoints: Int, placed: Boolean): String =
    "Offline map with $routePoints GPS route points" + if (placed) " and the walk drawing" else ""
