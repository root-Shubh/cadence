package com.cadence.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val label: String, val glyph: String) {
    TODAY("Today", "T"),
    TREND("Trend", "~"),
    PATTERN("Pattern", "D"),
    PROFILE("Profile", "P"),
    CALIBRATE("Calibrate", "C"),
}

private fun Destination.icon(): ImageVector = when (this) {
    Destination.TODAY -> Icons.AutoMirrored.Filled.DirectionsWalk
    Destination.TREND -> Icons.AutoMirrored.Filled.TrendingUp
    Destination.PATTERN -> Icons.Filled.Explore
    Destination.PROFILE -> Icons.Filled.Person
    Destination.CALIBRATE -> Icons.Filled.Straighten
}

/** Each tab has its own tone so the selection pill shifts colour when the tab changes. */
private fun Destination.tone(): Tone = when (this) {
    Destination.TODAY -> Tone.PRIMARY
    Destination.TREND -> Tone.TERTIARY
    Destination.PATTERN -> Tone.SECONDARY
    Destination.PROFILE -> Tone.SECONDARY
    Destination.CALIBRATE -> Tone.PRIMARY
}

/**
 * Scaffold with a bottom NavigationBar. [content] receives the padding to apply to the screen.
 * Switching tabs slides the content in the direction of the tab order and fades it.
 */
@Composable
fun AppShell(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val pillColor by animateColorAsState(
        targetValue = selected.tone().container(),
        animationSpec = tween(MOTION_MEDIUM_MS),
        label = "pill",
    )
    val pillIconColor by animateColorAsState(
        targetValue = selected.tone().onContainer(),
        animationSpec = tween(MOTION_MEDIUM_MS),
        label = "pillIcon",
    )

    // Edge-to-edge: the top sits below the status bar and camera cutout; the navigation bar
    // stays handled by the bottom NavigationBar's own inset.
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            selectedIconColor = pillIconColor,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = pillColor,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (
                    slideInHorizontally(tween(MOTION_MEDIUM_MS)) { full -> if (forward) full / 6 else -full / 6 } +
                        fadeIn(tween(MOTION_MEDIUM_MS))
                    ) togetherWith (
                    slideOutHorizontally(tween(MOTION_MEDIUM_MS)) { full -> if (forward) -full / 6 else full / 6 } +
                        fadeOut(tween(MOTION_FAST_MS))
                    )
            },
            label = "tabs",
        ) { target ->
            // The caller's content lambda reads the live selection, so only the current tab renders
            // here; the outgoing tab slides and fades out as an empty frame.
            if (target == selected) {
                content(padding)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}
