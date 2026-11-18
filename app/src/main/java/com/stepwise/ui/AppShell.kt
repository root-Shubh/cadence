package com.stepwise.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class Destination(val label: String, val glyph: String) {
    TODAY("Today", "T"),
    TREND("Trend", "~"),
    PROFILE("Profile", "P"),
    CALIBRATE("Calibrate", "C"),
}

/** Material3 theme: dynamic colour on Android 12+, default colour scheme otherwise. */
@Composable
fun StepwiseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Scaffold with a bottom NavigationBar. [content] receives the padding to apply to the screen. */
@Composable
fun AppShell(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.values().forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        icon = { Text(destination.glyph) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
        content = content,
    )
}
