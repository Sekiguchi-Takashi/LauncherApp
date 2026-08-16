package com.appathy.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private fun squirclePath(size: Float): Path {
    val path = Path()
    val radius = size * 0.225f
    path.addRoundRect(RectF(0f, 0f, size, size), radius, radius, Path.Direction.CW)
    return path
}

fun Drawable.toSquircleBitmap(sizePx: Int): ImageBitmap {
    val source = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val sourceCanvas = Canvas(source)

    if (this is AdaptiveIconDrawable) {
        val bg = background
        val fg = foreground
        if (bg != null) {
            bg.bounds = Rect(0, 0, sizePx, sizePx)
            bg.draw(sourceCanvas)
        } else {
            sourceCanvas.drawColor(Color.WHITE)
        }
        if (fg != null) {
            fg.bounds = Rect(0, 0, sizePx, sizePx)
            fg.draw(sourceCanvas)
        }
    } else {
        sourceCanvas.drawColor(Color.WHITE)
        val pad = (sizePx * 0.16f).toInt()
        bounds = Rect(pad, pad, sizePx - pad, sizePx - pad)
        draw(sourceCanvas)
    }

    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    outCanvas.drawPath(squirclePath(sizePx.toFloat()), paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    outCanvas.drawBitmap(source, 0f, 0f, paint)

    source.recycle()
    return output.asImageBitmap()
}
