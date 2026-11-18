package com.cadence.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cadence.debug.DebugLog
import com.cadence.debug.debugStatusLines
import com.cadence.debug.describeSensors
import com.cadence.debug.readStepSensors
import com.cadence.steps.StepCounterService
import kotlinx.coroutines.delay

/** Newest log lines shown in the panel. The full log is still available through Copy. */
private const val SHOWN_LOG_LINES = 80

/** How often the open panel re-reads the log and the service state. */
private const val PANEL_REFRESH_MS = 2_000L

/**
 * Debug card for telling a software fault from a hardware fault. Collapsed by default; opened, it
 * lists which step sensors the phone has, the service's live state, and the step-counting log,
 * which can be copied out. While open it refreshes every [PANEL_REFRESH_MS] and on every resume.
 */
@Composable
fun DebugPanel(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = "Debug", icon = Icons.Filled.BugReport, tone = Tone.TERTIARY, modifier = modifier) {
        if (!expanded) {
            Text(
                "Step sensors, service state and the counting log, for troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Show debug details")
            }
        } else {
            DebugDetails()
            TextButton(onClick = { expanded = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Hide debug details")
            }
        }
    }
}

@Composable
private fun DebugDetails() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PANEL_REFRESH_MS)
            refresh++
        }
    }

    val sensorLines = remember(refresh) {
        describeSensors(readStepSensors(context.getSystemService(SensorManager::class.java)))
    }
    val statusLines = remember(refresh) { readStatusLines(context) }
    val logLines = remember(refresh) {
        // Loads the saved log from earlier sessions when the service has not run in this process.
        DebugLog.attach(context.filesDir)
        DebugLog.lines().takeLast(SHOWN_LOG_LINES)
    }

    Text("Sensors", style = MaterialTheme.typography.titleSmall)
    sensorLines.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }

    Text("Service", style = MaterialTheme.typography.titleSmall)
    statusLines.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }

    Text(
        "Hardware or software? If a sensor says \"not present\", the phone has no such sensor. " +
            "If the sensor is present, counting is switched on and \"Counter events received\" stays " +
            "at 0 while you walk, the sensor is not delivering steps. Phones hold back the first 10 " +
            "or so steps of a walk and drop very short walks; that is the sensor, not the app. If " +
            "events keep arriving but Today does not change, the fault is in the app. Heartbeat " +
            "lines show the service was running each minute.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh") }
        OutlinedButton(onClick = { copyLog(context) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Copy log") }
        OutlinedButton(
            onClick = {
                DebugLog.clear()
                refresh++
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Clear log") }
    }

    Text(
        "Log (newest last, ${logLines.size} of up to ${DebugLog.DEFAULT_CAPACITY} lines)",
        style = MaterialTheme.typography.titleSmall,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (logLines.isEmpty()) {
            Text("No events yet. Start counting to record them.", style = MaterialTheme.typography.bodySmall)
        }
        logLines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

private fun readStatusLines(context: Context): List<String> {
    val prefs = context.getSharedPreferences(StepCounterService.PREFS_NAME, Context.MODE_PRIVATE)
    return debugStatusLines(
        countingOn = prefs.getBoolean(StepCounterService.KEY_COUNTING_ON, false),
        sensorMissing = prefs.getBoolean(StepCounterService.KEY_SENSOR_MISSING, false),
        eventsReceived = prefs.getLong(StepCounterService.KEY_EVENTS_RECEIVED, 0L),
        lastEventMillis = prefs.getLong(StepCounterService.KEY_LAST_EVENT_MILLIS, 0L),
        lastRaw = prefs.getLong(StepCounterService.KEY_LAST_EVENT_RAW, 0L),
        nowMillis = System.currentTimeMillis(),
    )
}

private fun copyLog(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Cadence debug log", DebugLog.text()))
}
