package com.cadence.steps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Pixel size of the generated ring icon. */
const val RING_ICON_SIZE_PX: Int = 256

/** Ring sweep in degrees for [percent], clamped to 0..100 so the ring never overfills. */
fun ringSweepDegrees(percent: Int): Float = percent.coerceIn(0, 100) * 3.6f

/** Text in the middle of the ring: the clamped percent, for example "42%". */
fun ringCenterText(percent: Int): String = "${percent.coerceAtLeast(0)}%"

/**
 * Draws a progress ring of [percent] in [color] on a transparent bitmap, with the percent in the
 * middle. Caller decides when to regenerate; the bitmap depends only on [percent].
 */
fun buildRingIcon(percent: Int, color: Int, sizePx: Int = RING_ICON_SIZE_PX): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * 0.12f
    val inset = stroke / 2f + sizePx * 0.04f
    val oval = RectF(inset, inset, sizePx - inset, sizePx - inset)

    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        this.color = Color.argb(60, Color.red(color), Color.green(color), Color.blue(color))
    }
    canvas.drawArc(oval, 0f, 360f, false, track)

    val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }
    val sweep = ringSweepDegrees(percent)
    if (sweep > 0f) canvas.drawArc(oval, -90f, sweep, false, arc)

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = sizePx * 0.26f
    }
    val baseline = sizePx / 2f - (text.descent() + text.ascent()) / 2f
    canvas.drawText(ringCenterText(percent), sizePx / 2f, baseline, text)
    return bitmap
}
