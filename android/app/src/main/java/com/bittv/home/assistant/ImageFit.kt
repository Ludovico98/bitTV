package com.bittv.home.assistant

import kotlin.math.min

enum class FitMode { FIT, CROP }

/**
 * Pictures are shown whole; they are cropped only when their shape is so extreme that the whole picture would cover
 * less than 35 % of the box.
 */
fun fitMode(imgW: Int, imgH: Int, boxW: Int, boxH: Int): FitMode {
    if (imgW <= 0 || imgH <= 0 || boxW <= 0 || boxH <= 0) return FitMode.FIT
    val ar = imgW.toFloat() / imgH; val boxAr = boxW.toFloat() / boxH
    return if (min(ar / boxAr, boxAr / ar) < 0.35f) FitMode.CROP else FitMode.FIT
}
