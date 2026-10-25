package com.bittv.home.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens honouring Settings → Animations. Off: every change is instant ([snap]) and nothing loops.
 * Page movement is a critically damped spring, so a new key press redirects it without restarting.
 */
// a data class so an equal value provided again does not invalidate everything that reads LocalMotion
data class Motion(val enabled: Boolean) {
    fun <T> tween(ms: Int): FiniteAnimationSpec<T> = if (enabled) tween(ms, easing = FastOutSlowInEasing) else snap()
    fun <T> pageSpring(): FiniteAnimationSpec<T> = if (enabled) spring(dampingRatio = 1f, stiffness = 340f) else snap()
    val loops: Boolean get() = enabled
}

val LocalMotion = staticCompositionLocalOf { Motion(enabled = true) }
