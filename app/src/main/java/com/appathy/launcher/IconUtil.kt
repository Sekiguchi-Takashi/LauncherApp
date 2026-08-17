package com.appathy.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class IconStyle {
    DEFAULT,
    DARK,
    TINTED,
    CLEAR_LIGHT,
    CLEAR_DARK;

    companion object {
        fun from(name: String?): IconStyle =
            entries.find { it.name == name } ?: DEFAULT
    }
}

fun iconStyleLabel(style: IconStyle): String = when (style) {
    IconStyle.DEFAULT -> "デフォルト"
    IconStyle.DARK -> "ダーク"
    IconStyle.TINTED -> "色合い調整"
    IconStyle.CLEAR_LIGHT -> "クリア（ライト）"
    IconStyle.CLEAR_DARK -> "クリア（ダーク）"
}

private fun squirclePath(size: Float): Path {
    val path = Path()
    val radius = size * IconCorner
    path.addRoundRect(RectF(0f, 0f, size, size), radius, radius, Path.Direction.CW)
    return path
}

private fun grayscaleFilter(brightness: Float): ColorMatrixColorFilter {
    val matrix = ColorMatrix()
    matrix.setSaturation(0f)
    val scale = ColorMatrix(
        floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    matrix.postConcat(scale)
    return ColorMatrixColorFilter(matrix)
}

private fun tintFilter(tint: Int): ColorMatrixColorFilter {
    val r = Color.red(tint) / 255f
    val g = Color.green(tint) / 255f
    val b = Color.blue(tint) / 255f
    val matrix = ColorMatrix()
    matrix.setSaturation(0f)
    val colorize = ColorMatrix(
        floatArrayOf(
            r, 0f, 0f, 0f, 0f,
            0f, g, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    matrix.postConcat(colorize)
    return ColorMatrixColorFilter(matrix)
}

private fun drawGlyph(
    drawable: Drawable,
    canvas: Canvas,
    sizePx: Int,
    filter: ColorMatrixColorFilter?,
    inset: Float
) {
    val pad = (sizePx * inset).toInt()
    val saved = drawable.colorFilter
    drawable.colorFilter = filter
    drawable.bounds = Rect(pad, pad, sizePx - pad, sizePx - pad)
    drawable.draw(canvas)
    drawable.colorFilter = saved
}

fun Drawable.toStyledBitmap(
    sizePx: Int,
    style: IconStyle,
    tint: Int = 0xFF7FA6D8.toInt()
): ImageBitmap {
    val source = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(source)
    val adaptive = this as? AdaptiveIconDrawable
    val glyph = adaptive?.foreground ?: this
    val glyphInset = if (adaptive != null) 0f else 0.16f

    when (style) {
        IconStyle.DEFAULT -> {
            if (adaptive != null) {
                val bg = adaptive.background
                if (bg != null) {
                    bg.bounds = Rect(0, 0, sizePx, sizePx)
                    bg.draw(canvas)
                } else {
                    canvas.drawColor(Color.WHITE)
                }
                drawGlyph(glyph, canvas, sizePx, null, 0f)
            } else {
                canvas.drawColor(Color.WHITE)
                drawGlyph(glyph, canvas, sizePx, null, glyphInset)
            }
        }
        IconStyle.DARK -> {
            canvas.drawColor(0xFF1C1C1E.toInt())
            drawGlyph(glyph, canvas, sizePx, null, glyphInset)
        }
        IconStyle.TINTED -> {
            canvas.drawColor(0xFF16181C.toInt())
            drawGlyph(glyph, canvas, sizePx, tintFilter(tint), glyphInset)
        }
        IconStyle.CLEAR_LIGHT -> {
            canvas.drawColor(0x40FFFFFF)
            drawGlyph(glyph, canvas, sizePx, grayscaleFilter(2.4f), glyphInset)
        }
        IconStyle.CLEAR_DARK -> {
            canvas.drawColor(0x40000000)
            drawGlyph(glyph, canvas, sizePx, grayscaleFilter(2.4f), glyphInset)
        }
    }

    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    outCanvas.drawPath(squirclePath(sizePx.toFloat()), paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    outCanvas.drawBitmap(source, 0f, 0f, paint)
    paint.xfermode = null

    if (style == IconStyle.CLEAR_LIGHT || style == IconStyle.CLEAR_DARK) {
        val edge = Paint(Paint.ANTI_ALIAS_FLAG)
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = sizePx * 0.02f
        edge.color = if (style == IconStyle.CLEAR_LIGHT) 0x66FFFFFF else 0x33FFFFFF
        outCanvas.drawPath(squirclePath(sizePx.toFloat()), edge)
    }

    source.recycle()
    return output.asImageBitmap()
}

object IconCache {
    private val map = ConcurrentHashMap<String, ImageBitmap>()

    fun peek(key: String): ImageBitmap? = map[key]

    suspend fun load(context: Context, app: AppEntry, style: IconStyle): ImageBitmap? {
        val key = cacheKey(app, style)
        map[key]?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val component = ComponentName(app.packageName, app.activityName)
                val info = context.packageManager.getActivityInfo(component, 0)
                info.loadIcon(context.packageManager).toStyledBitmap(160, style)
            }.getOrNull()
        }
        if (bitmap != null) map[key] = bitmap
        return bitmap
    }

    fun cacheKey(app: AppEntry, style: IconStyle): String =
        app.packageName + "/" + app.activityName + "#" + style.name

    fun clear() = map.clear()
}
