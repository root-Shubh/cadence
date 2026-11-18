package com.stepwise.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21.0, contrastRatio(0xFF000000L, 0xFFFFFFFFL), 0.01)
    }

    @Test
    fun contrastIsSymmetric() {
        val a = contrastRatio(0xFF1B7F6BL, 0xFFF7F9F8L)
        val b = contrastRatio(0xFFF7F9F8L, 0xFF1B7F6BL)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun lightBodyTextMeetsAa() {
        assertTrue(contrastRatio(LightPalette.onBackground, LightPalette.background) >= 4.5)
        assertTrue(contrastRatio(LightPalette.onSurface, LightPalette.surface) >= 4.5)
        assertTrue(contrastRatio(LightPalette.onSurfaceVariant, LightPalette.surfaceVariant) >= 4.5)
    }

    @Test
    fun darkBodyTextMeetsAa() {
        assertTrue(contrastRatio(DarkPalette.onBackground, DarkPalette.background) >= 4.5)
        assertTrue(contrastRatio(DarkPalette.onSurface, DarkPalette.surface) >= 4.5)
        assertTrue(contrastRatio(DarkPalette.onSurfaceVariant, DarkPalette.surfaceVariant) >= 4.5)
    }

    @Test
    fun privacyNoticeTextMeetsAaInBothThemes() {
        // PrivacyNotice uses surfaceVariant with onSurface text.
        assertTrue(contrastRatio(LightPalette.onSurface, LightPalette.surfaceVariant) >= 4.5)
        assertTrue(contrastRatio(DarkPalette.onSurface, DarkPalette.surfaceVariant) >= 4.5)
    }

    @Test
    fun cardLabelMeetsAaOnSurfaceInBothThemes() {
        // Calibrate stat cards: onSurfaceVariant label on the default card surface.
        assertTrue(contrastRatio(LightPalette.onSurfaceVariant, LightPalette.surface) >= 4.5)
        assertTrue(contrastRatio(DarkPalette.onSurfaceVariant, DarkPalette.surface) >= 4.5)
    }

    @Test
    fun accentTextMeetsAaOnBackground() {
        assertTrue(contrastRatio(LightPalette.primary, LightPalette.background) >= 4.5)
        assertTrue(contrastRatio(LightPalette.secondary, LightPalette.surface) >= 4.5)
        assertTrue(contrastRatio(DarkPalette.primary, DarkPalette.background) >= 4.5)
        assertTrue(contrastRatio(DarkPalette.secondary, DarkPalette.surface) >= 4.5)
    }
}
