package com.cadence.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun argb(value: Long): Color = Color(value.toInt())

/** Fixed palette (no dynamic colour) so the look is the same on every device. */
private val LightScheme: ColorScheme = lightColorScheme(
    primary = argb(LightPalette.primary),
    onPrimary = argb(LightPalette.onPrimary),
    primaryContainer = argb(LightPalette.primaryContainer),
    onPrimaryContainer = argb(LightPalette.onPrimaryContainer),
    secondary = argb(LightPalette.secondary),
    onSecondary = argb(LightPalette.onSecondary),
    secondaryContainer = argb(LightPalette.secondaryContainer),
    onSecondaryContainer = argb(LightPalette.onSecondaryContainer),
    tertiary = argb(LightPalette.tertiary),
    onTertiary = argb(LightPalette.onTertiary),
    tertiaryContainer = argb(LightPalette.tertiaryContainer),
    onTertiaryContainer = argb(LightPalette.onTertiaryContainer),
    error = argb(LightPalette.error),
    onError = argb(LightPalette.onError),
    errorContainer = argb(LightPalette.errorContainer),
    onErrorContainer = argb(LightPalette.onErrorContainer),
    background = argb(LightPalette.background),
    onBackground = argb(LightPalette.onBackground),
    surface = argb(LightPalette.surface),
    onSurface = argb(LightPalette.onSurface),
    surfaceVariant = argb(LightPalette.surfaceVariant),
    onSurfaceVariant = argb(LightPalette.onSurfaceVariant),
    outline = argb(LightPalette.outline),
    outlineVariant = argb(LightPalette.outlineVariant),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = argb(DarkPalette.primary),
    onPrimary = argb(DarkPalette.onPrimary),
    primaryContainer = argb(DarkPalette.primaryContainer),
    onPrimaryContainer = argb(DarkPalette.onPrimaryContainer),
    secondary = argb(DarkPalette.secondary),
    onSecondary = argb(DarkPalette.onSecondary),
    secondaryContainer = argb(DarkPalette.secondaryContainer),
    onSecondaryContainer = argb(DarkPalette.onSecondaryContainer),
    tertiary = argb(DarkPalette.tertiary),
    onTertiary = argb(DarkPalette.onTertiary),
    tertiaryContainer = argb(DarkPalette.tertiaryContainer),
    onTertiaryContainer = argb(DarkPalette.onTertiaryContainer),
    error = argb(DarkPalette.error),
    onError = argb(DarkPalette.onError),
    errorContainer = argb(DarkPalette.errorContainer),
    onErrorContainer = argb(DarkPalette.onErrorContainer),
    background = argb(DarkPalette.background),
    onBackground = argb(DarkPalette.onBackground),
    surface = argb(DarkPalette.surface),
    onSurface = argb(DarkPalette.onSurface),
    surfaceVariant = argb(DarkPalette.surfaceVariant),
    onSurfaceVariant = argb(DarkPalette.onSurfaceVariant),
    outline = argb(DarkPalette.outline),
    outlineVariant = argb(DarkPalette.outlineVariant),
)

private val StepwiseTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 64.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/** Cards use [Shapes.medium] (16 dp); buttons use [Shapes.extraLarge] (28 dp) as a pill. */
private val StepwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Stepwise Material3 theme. [darkTheme] defaults to the system setting; MainActivity passes
 * the user's System/Light/Dark choice.
 */
@Composable
fun StepwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = StepwiseTypography,
        shapes = StepwiseShapes,
        content = content,
    )
}
