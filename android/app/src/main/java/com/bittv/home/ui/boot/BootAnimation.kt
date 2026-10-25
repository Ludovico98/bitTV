package com.bittv.home.ui.boot

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.res.ResourcesCompat
import com.bittv.home.R

import kotlin.math.min

/** Letter metrics and the logo transform, computed once from the real Open Sans glyphs (as the HTML does). */
private class BootLayout(typeface: Typeface) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 100f }
    val fontSize: Float
    val starts: FloatArray
    val shift: Float
    val leftTarget: Float
    val clip = Path()

    init {
        val b = android.graphics.Rect()
        paint.getTextBounds("T", 0, 1, b)
        val capAsc = (-b.top).toFloat().takeIf { it > 0 } ?: 71.4f
        fontSize = 100f * BootMath.CAP / capAsc
        paint.textSize = fontSize
        val w = android.graphics.Rect(); paint.getTextBounds(BootMath.WORD, 0, BootMath.WORD.length, w)
        val inkL = w.left.toFloat(); val inkW = (w.right - w.left).toFloat()
        starts = FloatArray(BootMath.WORD.length) { i -> paint.measureText(BootMath.WORD, 0, i) }
        leftTarget = -(inkW + BootMath.GAP)
        shift = leftTarget - inkL
        // clip for the light sweep: the letters plus the cubes at rest
        paint.getTextPath(BootMath.WORD, 0, BootMath.WORD.length, shift, BootMath.BASE, clip)
        BootMath.cubes.forEach { c ->
            clip.addRoundRect(RectF(c.destX - c.w / 2, c.destY - c.h / 2, c.destX + c.w / 2, c.destY + c.h / 2), c.r, c.r, Path.Direction.CW)
        }
        paint.shader = LinearGradient(0f, 34f, 0f, 453f, 0xFFFBFCFE.toInt(), 0xFF98A1AD.toInt(), Shader.TileMode.CLAMP)
    }
}

/**
 * The boot animation on a 1920x1080 design canvas, scaled to the screen. Calls [onDone] after the finished logo
 * has held for a moment; the caller cross-fades to Home and any key skips it.
 */
@Composable
fun BootAnimation(onDone: () -> Unit) {
    val context = LocalContext.current
    val layout = remember {
        val base = ResourcesCompat.getFont(context, R.font.open_sans) ?: Typeface.DEFAULT
        BootLayout(Typeface.create(base, 500, false))
    }
    val cubePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val sheenPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 190f, 0f, intArrayOf(0x006CC4FF, 0xF26CC4FF.toInt(), 0x006CC4FF), null, Shader.TileMode.CLAMP)
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val real = (now - start) / 1e9f
            t = min(real * BootMath.RATE, BootMath.END)
            if (real >= BootMath.totalSeconds) { onDone(); break }
        }
    }
    Canvas(
        Modifier.fillMaxSize().testTag("boot")
            .background(Brush.radialGradient(listOf(Color(0xFF151B26), Color(0xFF05070B)), center = Offset.Unspecified, radius = 1300f)),
    ) {
        val s = min(size.width / 1920f, size.height / 1080f)
        val totalW = BootMath.CUBES_W - layout.leftTarget
        val sc = 1180f / totalW
        val cx = (layout.leftTarget + BootMath.CUBES_W) / 2
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.translate((size.width - 1920f * s) / 2, (size.height - 1080f * s) / 2)
            nc.scale(s, s)
            nc.translate(960f, 540f); nc.scale(sc, sc); nc.translate(-cx, -230f)
            // letters rise in, staggered
            BootMath.WORD.forEachIndexed { i, ch ->
                val ls = BootMath.letterState(i, t)
                layout.paint.alpha = (ls.alpha * 255).toInt().coerceIn(0, 255)
                nc.drawText(ch.toString(), layout.starts[i] + layout.shift, BootMath.BASE + ls.dy, layout.paint)
            }
            layout.paint.alpha = 255
            // cubes, in their own layer so cube B's multiply only mixes with the other cubes. CSS's mix-blend-mode multiply
            // (the HTML) is BlendMode.MULTIPLY: the Porter-Duff MULTIPLY used before also multiplies alpha, which
            // erased cube B wherever it did not overlap cube A (only the overlap showed)
            nc.saveLayer(null, null)
            BootMath.cubes.forEachIndexed { i, c ->
                val st = BootMath.cubeState(i, t)
                if (!st.visible) return@forEachIndexed
                cubePaint.shader = LinearGradient(0f, -c.h / 2, 0f, c.h / 2, c.gradTop.toInt(), c.gradBottom.toInt(), Shader.TileMode.CLAMP)
                cubePaint.blendMode = if (c.multiply) BlendMode.MULTIPLY else null
                nc.save()
                nc.translate(st.x, st.y)
                nc.rotate(st.axis); nc.scale(st.stretch, 1f / st.stretch); nc.rotate(-st.axis)
                nc.rotate(st.rot); nc.scale(st.pop, st.pop)
                nc.drawRoundRect(-c.w / 2, -c.h / 2, c.w / 2, c.h / 2, c.r, c.r, cubePaint)
                nc.restore()
            }
            nc.restore()
            // the final light sweep, clipped to the logo
            BootMath.sheenProgress(t)?.let { p ->
                val x0 = layout.leftTarget - 260f; val x1 = BootMath.CUBES_W + 120f
                nc.save()
                nc.clipPath(layout.clip)
                nc.translate(x0 + (x1 - x0) * p, 0f)
                nc.skew(-0.3249f, 0f)   // skewX(-18deg)
                nc.drawRect(0f, -60f, 190f, 520f, sheenPaint)
                nc.restore()
            }
            nc.restore()
        }
    }
}

