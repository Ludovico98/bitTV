package com.bittv.home.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation
import kotlin.math.max

/**
 * Bakes what every tile drew on top of its artwork on every frame into the decoded bitmap, once: cropped to the tile
 * (centre, like ContentScale.Crop), rounded corners of [cornerPx], and the bottom shade (Transparent → Black 60 % from
 * 40 % height) that keeps the title readable. The tile then draws one image.
 */
class ShadeTransformation(private val cornerPx: Float) : Transformation() {
    override val cacheKey = "shade:$SHADE_FROM:$SHADE_ALPHA:$cornerPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = size.width.pxOrElse { input.width }; val h = size.height.pxOrElse { input.height }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = max(w / input.width.toFloat(), h / input.height.toFloat())
        val art = BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate((w - input.width * scale) / 2, (h - input.height * scale) / 2) })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerPx, cornerPx, paint.apply { shader = art })
        paint.shader = LinearGradient(0f, h * SHADE_FROM, 0f, h.toFloat(), 0, android.graphics.Color.argb((SHADE_ALPHA * 255).toInt(), 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerPx, cornerPx, paint)
        return out
    }

    private companion object { const val SHADE_FROM = 0.4f; const val SHADE_ALPHA = 0.6f }
}
