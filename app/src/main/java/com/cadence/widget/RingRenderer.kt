package com.cadence.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Draws a progress ring as a square bitmap for RemoteViews.setImageViewBitmap. A full track circle
 * is drawn first, then an arc starting at the top that covers [fraction] (0..1) of the circle.
 */
fun renderRing(
    sizePx: Int,
    fraction: Float,
    progressColor: Int,
    trackColor: Int,
    strokePx: Float,
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
    }
    val inset = strokePx / 2f + 1f
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
    paint.color = trackColor
    canvas.drawArc(rect, 0f, 360f, false, paint)
    val sweep = 360f * fraction.coerceIn(0f, 1f)
    if (sweep > 0f) {
        paint.color = progressColor
        canvas.drawArc(rect, -90f, sweep, false, paint)
    }
    return bitmap
}
