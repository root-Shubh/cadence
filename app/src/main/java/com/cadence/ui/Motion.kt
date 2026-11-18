package com.cadence.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Quick transitions (colour, fades, press feedback). */
const val MOTION_FAST_MS = 200

/** Standard transitions (slides, entrances, numbers). */
const val MOTION_MEDIUM_MS = 300

/** Bar growth on the trend chart. */
const val MOTION_BAR_MS = 350

/** Scale applied to buttons while they are pressed. */
const val PRESSED_SCALE = 0.97f

private const val STAGGER_STEP_MS = 60L
private const val STAGGER_MAX_MS = 480L

/**
 * Start delay for the [index]-th item of a staggered entrance. Items are spaced
 * [STAGGER_STEP_MS] apart and capped at [STAGGER_MAX_MS] so long screens still settle quickly.
 */
fun staggerDelayMillis(index: Int): Long =
    (index.coerceAtLeast(0) * STAGGER_STEP_MS).coerceAtMost(STAGGER_MAX_MS)

/**
 * Fades the content in and slides it up 16 dp on first composition, after a stagger delay.
 * The content stays laid out and hit-testable throughout, so input is never blocked.
 */
@Composable
fun Reveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(staggerDelayMillis(index))
        started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(MOTION_MEDIUM_MS, easing = FastOutSlowInEasing),
        label = "reveal",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 16.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * Scales the element to [PRESSED_SCALE] with a spring while [interactionSource] reports a press.
 * Pass the same source to the clickable (for example Button) so the ripple still plays.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
