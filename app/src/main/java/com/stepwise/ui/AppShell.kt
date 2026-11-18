package com.stepwise.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val label: String, val glyph: String) {
    TODAY("Today", "T"),
    TREND("Trend", "~"),
    PROFILE("Profile", "P"),
    CALIBRATE("Calibrate", "C"),
}

private fun Destination.icon(): ImageVector = when (this) {
    Destination.TODAY -> Icons.Filled.DirectionsWalk
    Destination.TREND -> Icons.AutoMirrored.Filled.TrendingUp
    Destination.PROFILE -> Icons.Filled.Person
    Destination.CALIBRATE -> Icons.Filled.Straighten
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.values().forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        icon = { Icon(destination.icon(), contentDescription = null) },
                        label = { Text(destination.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        content = content,
    )
}
