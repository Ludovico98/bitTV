package com.bittv.home.platform

import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_AUDIBLE
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_BRAILLE
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_HAPTIC
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeA11yTest {
    @Test fun noServicesNeedNothing() = assertFalse(composeA11yNeeded(touchExploration = false, feedbackTypes = emptyList()))

    // BitTV's Home helper (generic) and Button Mapper (haptic) filter keys; neither reads the screen out
    @Test fun keyFiltersNeedNothing() = assertFalse(composeA11yNeeded(false, listOf(FEEDBACK_GENERIC, FEEDBACK_HAPTIC)))

    @Test fun aScreenReaderNeedsIt() {
        assertTrue(composeA11yNeeded(false, listOf(FEEDBACK_GENERIC, FEEDBACK_SPOKEN)))
        assertTrue(composeA11yNeeded(false, listOf(FEEDBACK_BRAILLE)))
        assertTrue(composeA11yNeeded(false, listOf(FEEDBACK_AUDIBLE or FEEDBACK_HAPTIC)))
    }

    // canary: a Compose upgrade that renames these internals must fail here, not silently bring the jank back
    @Test fun composeStillHasTheInternalsComposeA11yUses() {
        val loader = javaClass.classLoader
        val view = Class.forName("androidx.compose.ui.platform.AndroidComposeView", false, loader)
        val delegate = Class.forName("androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat", false, loader)
        assertTrue(view.getDeclaredField("composeAccessibilityDelegate").type == delegate)
        assertTrue(delegate.getDeclaredField("_enabledServices").type == List::class.java)
    }

    @Test fun touchExplorationNeedsIt() = assertTrue(composeA11yNeeded(touchExploration = true, feedbackTypes = emptyList()))
}
