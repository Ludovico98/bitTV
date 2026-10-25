package com.bittv.home.ui.boot

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Ludovico's boot animation (BitTV Boot Animation.html), ported 1:1: four cubes pop in, wind up, fly along bezier
 * arcs with squash and stretch, land with a damped spring and nudge each other; the letters rise in; a blue light
 * sweeps the logo. Times are in the original's seconds; [RATE] plays it twice as fast, as in the current version.
 * Coordinates are in logo space (origin = left edge of the cubes).
 */
object BootMath {
    const val RATE = 2f
    const val END = 4.9f
    private const val HOLD = 0.35f   // seconds the finished logo stays before Home
    val totalSeconds = END / RATE + HOLD

    private const val AT = 0.26f
    private const val FT = 0.82f
    private const val POP = 0.45f
    private const val SHEEN_START = 3.55f
    private const val SHEEN_DUR = 1.1f

    const val WORD = "BitTV"
    const val CAP = 419f
    const val BASE = 453f
    const val GAP = 96f
    const val CUBES_W = 462f

    class Cube(
        val w: Float, val h: Float, val r: Float, val destX: Float, val destY: Float,
        val sx: Float, val sy: Float, val cx: Float, val cy: Float, val th0: Float, val appear: Float, val launch: Float,
        val gradTop: Long, val gradBottom: Long, val multiply: Boolean = false,
    ) {
        val size = (w + h) / 2
        private val d0 = norm(cx - sx, cy - sy)
        val d0ang = deg(d0.first, d0.second)
        val p0x = sx - d0.first * size * 0.16f; val p0y = sy - d0.second * size * 0.16f
        private val n = norm(destX - cx, destY - cy)
        val nx = n.first; val ny = n.second; val nang = deg(nx, ny)
        val ov = 0.12f * size
        val p2x = destX + nx * ov; val p2y = destY + ny * ov
        val thOv = -sign(th0) * 7f
        val tLand = launch + AT + FT
        val vmax: Float = (0..200).maxOf { k -> val x = k / 200f; val d = bezD(this, eio(x)); hypot(d.first, d.second) * eioD(x) }
    }

    val cubes = listOf(
        Cube(242f, 243f, 42f, 121f, 174.5f, 560f, 760f, 640f, 80f, -180f, 0.50f, 1.15f, 0xFF76B2E4, 0xFF5B97D1),
        Cube(301f, 300f, 46f, 237.5f, 291f, -10f, 800f, 480f, 620f, 180f, 0.60f, 1.38f, 0xFF72AEE1, 0xFF5994CE, multiply = true),
        Cube(83f, 83f, 13f, 346.5f, 41.5f, 820f, -150f, 700f, 40f, 360f, 0.70f, 1.60f, 0xFF70ABDF, 0xFF5E9BD4),
        Cube(55f, 55f, 9f, 434.5f, 432.5f, 180f, 940f, 700f, 700f, -270f, 0.80f, 1.78f, 0xFF86CDF6, 0xFF4A86C8),
    )

    data class CubeState(val x: Float, val y: Float, val axis: Float, val stretch: Float, val rot: Float, val pop: Float, val visible: Boolean)
    data class LetterState(val dy: Float, val alpha: Float)

    // easing helpers, as in the original
    private fun clamp(x: Float, a: Float = 0f, b: Float = 1f) = min(b, max(a, x))
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun smooth(a: Float, b: Float, x: Float): Float { val s = clamp((x - a) / (b - a)); return s * s * (3 - 2 * s) }
    private fun eio(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    private fun eioD(x: Float) = if (x < 0.5f) 12 * x * x else 3 * (-2 * x + 2).pow(2)
    fun eioSine(x: Float) = (-(cos(PI * x) - 1) / 2).toFloat()
    private fun eoc(x: Float) = 1 - (1 - x).pow(3)
    private fun eob(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2) }
    private fun settle(tau: Float, b: Float, w: Float) = (exp((-b * tau).toDouble()) * (cos((w * tau).toDouble()) + (b / w) * sin((w * tau).toDouble()))).toFloat()
    private fun norm(x: Float, y: Float): Pair<Float, Float> { val l = hypot(x, y).takeIf { it != 0f } ?: 1f; return x / l to y / l }
    private fun deg(x: Float, y: Float) = (atan2(y, x) * 180 / PI).toFloat()
    private fun bez(c: Cube, u: Float): Pair<Float, Float> { val m = 1 - u
        return (m * m * c.p0x + 2 * m * u * c.cx + u * u * c.p2x) to (m * m * c.p0y + 2 * m * u * c.cy + u * u * c.p2y) }
    private fun bezD(c: Cube, u: Float): Pair<Float, Float> { val m = 1 - u
        return (2 * m * (c.cx - c.p0x) + 2 * u * (c.p2x - c.cx)) to (2 * m * (c.cy - c.p0y) + 2 * u * (c.p2y - c.cy)) }

    fun cubeState(index: Int, t: Float): CubeState {
        val c = cubes[index]
        var x = c.destX; var y = c.destY; var axis = 0f; var al = 1f; var rot = 0f; var pop = 1f
        val tFly = c.launch + AT
        when {
            t < c.appear -> { pop = 0f; x = c.sx; y = c.sy; rot = c.th0 }
            t < c.launch -> { pop = eob(clamp((t - c.appear) / POP)); x = c.sx; y = c.sy; rot = c.th0 }
            t < tFly -> { val p = eioSine((t - c.launch) / AT); x = lerp(c.sx, c.p0x, p); y = lerp(c.sy, c.p0y, p); al = 1 - 0.20f * p; axis = c.d0ang; rot = c.th0 }
            t < c.tLand -> {
                val k = (t - tFly) / FT; val e = eio(k); val p = bez(c, e); val d = bezD(c, e)
                val sp = hypot(d.first, d.second) * eioD(k) / c.vmax
                x = p.first; y = p.second
                al = lerp(0.80f, 1 + 0.34f * sp, smooth(0f, 0.2f, k))
                al *= 1 - 0.26f * smooth(0.86f, 1f, k)
                axis = deg(d.first, d.second); rot = c.th0 * (1 - e) + c.thOv * e
            }
            else -> {
                val tau = t - c.tLand
                if (tau < 2) {
                    val s = settle(tau, 6.5f, 13.2f)
                    x = c.destX + c.nx * c.ov * s; y = c.destY + c.ny * c.ov * s
                    al = 1 - 0.26f * s; rot = c.thOv * settle(tau, 5.5f, 10.5f); axis = c.nang
                }
                // follow-through: later landings nudge cubes that already settled
                for (o in cubes) {
                    if (o === c || o.tLand <= c.tLand) continue
                    val tt = t - o.tLand - 0.03f
                    if (tt <= 0 || tt > 1.8f) continue
                    val dir = norm(c.destX - o.destX, c.destY - o.destY)
                    val r = (exp((-5 * tt).toDouble()) * sin(2 * PI * 2.2 * tt)).toFloat() * 0.05f * o.size
                    x += dir.first * r; y += dir.second * r
                }
            }
        }
        return CubeState(x, y, axis, al, rot, max(pop, 0.0001f), pop > 0)
    }

    fun letterState(i: Int, t: Float): LetterState {
        val p = clamp((t - (0.25f + i * 0.10f)) / 0.8f)
        return LetterState(dy = (1 - eoc(p)) * 70f, alpha = 1 - (1 - clamp(p * 1.6f)).pow(2))
    }

    /** 0..1 progress of the final light sweep, or null outside it. */
    fun sheenProgress(t: Float): Float? {
        val sp = (t - SHEEN_START) / SHEEN_DUR
        return if (sp > 0 && sp < 1) eioSine(sp) else null
    }

}
