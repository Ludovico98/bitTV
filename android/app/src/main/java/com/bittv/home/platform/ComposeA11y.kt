package com.bittv.home.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager

/** Only a screen reader (spoken, braille or audible feedback) or touch exploration reads Compose's semantics out. */
fun composeA11yNeeded(touchExploration: Boolean, feedbackTypes: List<Int>): Boolean =
    touchExploration || feedbackTypes.any { it and READER_FEEDBACK != 0 }

/** What Compose's cached service list is set to: empty to quiet it, null to make it re-read the real list. */
fun servicesCacheFor(needed: Boolean): List<AccessibilityServiceInfo>? = if (needed) null else emptyList()

private const val READER_FEEDBACK =
    AccessibilityServiceInfo.FEEDBACK_SPOKEN or AccessibilityServiceInfo.FEEDBACK_BRAILLE or AccessibilityServiceInfo.FEEDBACK_AUDIBLE

/**
 * Compose diffs its whole semantics tree and sends accessibility events on every frame as soon as ANY accessibility
 * service is on — on this TV that is BitTV's own Home helper and Button Mapper, which only filter keys. A trace on
 * the TV put it at ~6 ms of the main thread per frame; without it ~2.5 ms less per frame on average and a tenth
 * fewer animation steps late. Unless a screen reader is on, Compose is shown an empty service list (its own cache, by reflection; R8 keeps the two names, see proguard-rules.pro). Compose
 * re-reads the real list on attach and on accessibility on/off, so [apply] runs again after those and whenever the
 * enabled services change (a screen reader added while the Home helper keeps accessibility on fires only that).
 * If Compose renames these internals the reflection fails quietly and everything works as before, only slower.
 */
object ComposeA11y {
    fun apply(root: View, am: AccessibilityManager) {
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val needed = composeA11yNeeded(am.isTouchExplorationEnabled, services.map { it.feedbackType })
        val compose = find(root) ?: return
        runCatching {
            val delegate = compose.javaClass.getDeclaredField("composeAccessibilityDelegate").apply { isAccessible = true }.get(compose)
            // a screen reader turned on later gets the real list back (Compose only re-reads it when it is null)
            delegate.javaClass.getDeclaredField("_enabledServices").apply { isAccessible = true }.set(delegate, servicesCacheFor(needed))
        }
    }

    private fun find(v: View): View? {
        if (v.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
}
