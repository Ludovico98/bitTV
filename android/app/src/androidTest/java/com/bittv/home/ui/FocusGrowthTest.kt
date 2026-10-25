package com.bittv.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.bittv.home.ui.components.PillButton
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A focused control lifts; it must never grow into the control next to it, however wide it is. */
class FocusGrowthTest {
    @get:Rule val rule = createComposeRule()

    @Test fun wideFocusedButtonStaysClearOfItsNeighbour() {
        rule.setContent {
            BitTvTheme {
                Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton("Play on Disney+ with a long label", {}, Modifier.width(320.dp).testTag("a"))
                    PillButton("Next", {}, Modifier.width(320.dp).testTag("b"))
                }
            }
        }
        rule.onNodeWithTag("a").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        val a = rule.onNodeWithTag("a").fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithTag("b").fetchSemanticsNode().boundsInRoot
        val gap = b.left - a.right
        assertTrue("focused button grew to ${a.width}px; gap left $gap px", gap >= with(rule.density) { 3.dp.toPx() })
    }

    @Test fun smallButtonStillLiftsTheFullTenPercent() {
        rule.setContent { BitTvTheme { Row(Modifier.padding(40.dp)) { PillButton("Go", {}, Modifier.width(80.dp).testTag("s")) } } }
        val before = rule.onNodeWithTag("s").fetchSemanticsNode().boundsInRoot.width
        rule.onNodeWithTag("s").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        val after = rule.onNodeWithTag("s").fetchSemanticsNode().boundsInRoot.width
        assertTrue("scale ${after / before}", after / before > 1.09f)
    }
}
