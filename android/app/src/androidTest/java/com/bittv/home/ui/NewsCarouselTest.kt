package com.bittv.home.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.bittv.home.model.AppEntry
import com.bittv.home.model.NewsItem
import com.bittv.home.model.Title
import com.bittv.home.ui.home.ActiveSection
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NewsCarouselTest {
    @get:Rule val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val actions = object : HomeActions {
        override fun openTitle(t: Title) {}
        override fun openApp(a: AppEntry) {}
        override fun openSearch() {}
        override fun openSettings() {}
        override fun toggleWatchLater(t: Title) {}
        override fun openNews(item: NewsItem) { opened += item.headline }
    }
    private val news = listOf(NewsItem("First story", "BBC News · 5 min ago"), NewsItem("Second story", "NASA · 1 hr ago"),
        NewsItem("Third story", "ESA · 2 hr ago"))

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun headline(text: String) = rule.onNodeWithTag("news:headline", useUnmergedTree = true).assert(hasText(text))

    /** The card with something to its left, as the banner is on Home. */
    private fun setCard() = rule.setContent {
        BitTvTheme {
            Row {
                androidx.compose.foundation.layout.Box(Modifier.testTag("left").size(40.dp).focusable())
                ActiveSection(null, news, emptyList(), actions, FocusRequester(), 260.dp, 220.dp)
            }
        }
    }

    @Test
    fun rightAndLeftStepThroughTheStoriesAndOkOpensTheOneOnShow() {
        setCard()
        rule.onNodeWithTag("news").performSemanticsAction(SemanticsActions.RequestFocus)
        headline("First story")
        rule.onNodeWithTag("news:count", useUnmergedTree = true).assert(hasText("‹ 1 / 3 ›"))
        press(Key.DirectionRight); headline("Second story")
        press(Key.DirectionRight); headline("Third story")
        press(Key.DirectionRight); headline("First story")   // round to the first after the last
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("Second story"), opened)
        press(Key.DirectionLeft); headline("First story")
        rule.onNodeWithTag("news").assertIsFocused()
        press(Key.DirectionLeft)                               // on the first story, Left leaves the card
        rule.onNodeWithTag("left").assertIsFocused()
        headline("First story")
    }
}
