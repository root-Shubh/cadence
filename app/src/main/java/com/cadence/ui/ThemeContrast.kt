package com.cadence.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x relative luminance of an ARGB colour (alpha ignored). */
fun relativeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG 2.x contrast ratio between two ARGB colours, from 1.0 to 21.0. */
fun contrastRatio(foregroundArgb: Long, backgroundArgb: Long): Double {
    val l1 = relativeLuminance(foregroundArgb)
    val l2 = relativeLuminance(backgroundArgb)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}
