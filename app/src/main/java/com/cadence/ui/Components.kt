package com.cadence.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Colour family used for icon chips, accent bars and value text. */
enum class Tone { PRIMARY, SECONDARY, TERTIARY }

@Composable
fun Tone.accent(): Color = when (this) {
    Tone.PRIMARY -> MaterialTheme.colorScheme.primary
    Tone.SECONDARY -> MaterialTheme.colorScheme.secondary
    Tone.TERTIARY -> MaterialTheme.colorScheme.tertiary
}

@Composable
fun Tone.container(): Color = when (this) {
    Tone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    Tone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    Tone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
fun Tone.onContainer(): Color = when (this) {
    Tone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    Tone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    Tone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

/** A 40 dp circular chip holding a decorative icon (the adjacent text names it). */
@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.PRIMARY,
    containerColor: Color = tone.container(),
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.onContainer(),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Icon chip followed by a titleMedium heading. Used at the top of each section. */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.PRIMARY,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconChip(icon = icon, tone = tone)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Card with a [SectionHeader] and 12 dp between the header and [content]. */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.PRIMARY,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = title, icon = icon, tone = tone)
            content()
        }
    }
}

/** Integer that eases to each new [value] (count up or down) instead of jumping. */
@Composable
fun AnimatedCountText(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(MOTION_MEDIUM_MS),
        label = "count",
    )
    Text(
        text = formatCount(animated.toLong()) + suffix,
        style = style,
        color = color,
        modifier = modifier,
    )
}
