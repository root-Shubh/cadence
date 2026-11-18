package com.cadence.ui

/**
 * Cadence colour tokens as ARGB Long values. Pure Kotlin (no Compose imports) so that
 * contrast tests can read the exact values that [CadenceTheme] uses.
 */
object LightPalette {
    const val primary = 0xFF1B7F6BL
    const val onPrimary = 0xFFFFFFFFL
    const val primaryContainer = 0xFFC9EFE3L
    const val onPrimaryContainer = 0xFF00382DL
    const val secondary = 0xFF9A6212L
    const val onSecondary = 0xFFFFFFFFL
    const val secondaryContainer = 0xFFFBE7C2L
    const val onSecondaryContainer = 0xFF3D2A00L
    const val tertiary = 0xFF2F6DB5L
    const val onTertiary = 0xFFFFFFFFL
    const val tertiaryContainer = 0xFFD6E6FFL
    const val onTertiaryContainer = 0xFF0B2A4AL
    const val error = 0xFFB3261EL
    const val onError = 0xFFFFFFFFL
    const val errorContainer = 0xFFF9DEDCL
    const val onErrorContainer = 0xFF410E0BL
    const val background = 0xFFF7F9F8L
    const val onBackground = 0xFF1A2420L
    const val surface = 0xFFFCFEFDL
    const val onSurface = 0xFF1A2420L
    const val surfaceVariant = 0xFFE4ECE9L
    const val onSurfaceVariant = 0xFF3F4B47L
    const val outline = 0xFF6F7B77L
    const val outlineVariant = 0xFFC4CFCBL
}

object DarkPalette {
    const val primary = 0xFF6FD6BCL
    const val onPrimary = 0xFF003829L
    const val primaryContainer = 0xFF0F4D40L
    const val onPrimaryContainer = 0xFFB7F5E0L
    const val secondary = 0xFFF2C16BL
    const val onSecondary = 0xFF3D2A00L
    const val secondaryContainer = 0xFF4A3A12L
    const val onSecondaryContainer = 0xFFFBE7C2L
    const val tertiary = 0xFF9CC4FFL
    const val onTertiary = 0xFF0B2A4AL
    const val tertiaryContainer = 0xFF1F3B5CL
    const val onTertiaryContainer = 0xFFD6E6FFL
    const val error = 0xFFFFB4ABL
    const val onError = 0xFF690005L
    const val errorContainer = 0xFF8C1D18L
    const val onErrorContainer = 0xFFFFDAD6L
    const val background = 0xFF0E1513L
    const val onBackground = 0xFFE3EDEAL
    const val surface = 0xFF16201DL
    const val onSurface = 0xFFE3EDEAL
    const val surfaceVariant = 0xFF24302CL
    const val onSurfaceVariant = 0xFFB9C8C3L
    const val outline = 0xFF8A9893L
    const val outlineVariant = 0xFF3A4742L
}
