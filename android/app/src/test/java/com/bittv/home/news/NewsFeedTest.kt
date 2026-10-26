package com.bittv.home.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeedTest {
    // the shape of https://feeds.bbci.co.uk/news/rss.xml (2026-10-26): CDATA titles, escaped links, RFC 1123 dates
    private val rss = """<?xml version="1.0" encoding="UTF-8"?><rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0"><channel>
        <title><![CDATA[BBC News]]></title><ttl>15</ttl>
        <item><title><![CDATA[Watch: How Lula turned it around]]></title>
          <link>https://www.bbc.co.uk/news/videos/cmp8g0x4l11yo?at_medium=RSS&amp;at_campaign=rss</link>
          <pubDate>Mon, 26 Oct 2026 02:53:00 GMT</pubDate></item>
        <item><title><![CDATA[Lula re-elected for a fourth term in Brazil]]></title>
          <link>https://www.bbc.co.uk/news/articles/c1?at_medium=RSS&amp;at_campaign=rss</link>
          <pubDate>Mon, 26 Oct 2026 07:30:00 GMT</pubDate></item>
        <item><title>Storm warning for the south-east</title>
          <link>https://www.bbc.co.uk/news/articles/c2</link>
          <pubDate>Sun, 25 Oct 2026 21:00:00 GMT</pubDate></item>
        </channel></rss>"""

    private val at0745 = java.time.Instant.parse("2026-10-26T07:45:00Z").toEpochMilli()

    @Test fun readsTheFeedsStoriesInOrder() {
        val items = NewsFeed.parse(rss)
        assertEquals(listOf("Watch: How Lula turned it around", "Lula re-elected for a fourth term in Brazil", "Storm warning for the south-east"),
            items.map { it.title })
        assertEquals("https://www.bbc.co.uk/news/articles/c1?at_medium=RSS&at_campaign=rss", items[1].link)
        assertEquals(java.time.Instant.parse("2026-10-26T07:30:00Z").toEpochMilli(), items[1].publishedMs)
    }

    @Test fun theTopStoryIsTheFirstWrittenOneNotAVideo() =
        assertEquals("Lula re-elected for a fourth term in Brazil", NewsFeed.top(NewsFeed.parse(rss))?.title)

    @Test fun onlyVideosMeansTheFirstVideo() {
        val videos = NewsFeed.parse(rss).filter { it.title.startsWith("Watch") }
        assertEquals("Watch: How Lula turned it around", NewsFeed.top(videos)?.title)
    }

    @Test fun nothingToReadIsNoStory() {
        assertTrue(NewsFeed.parse("<html>Service unavailable</html>").isEmpty())
        assertTrue(NewsFeed.parse("not xml at all").isEmpty())
        assertNull(NewsFeed.top(emptyList()))
    }

    @Test fun ageIsSaidLikeAPerson() {
        val min = 60_000L
        assertEquals("just now", NewsFeed.ago(at0745 - 30_000, at0745))
        assertEquals("15 min ago", NewsFeed.ago(at0745 - 15 * min, at0745))
        assertEquals("1 hr ago", NewsFeed.ago(at0745 - 61 * min, at0745))
        assertEquals("10 hr ago", NewsFeed.ago(at0745 - 645 * min, at0745))
        assertEquals("1 day ago", NewsFeed.ago(at0745 - 25 * 60 * min, at0745))
        assertEquals("just now", NewsFeed.ago(at0745 + 5 * min, at0745))   // a clock a little behind the feed's
    }

    @Test fun theHomeItemNamesTheSourceAndAge() {
        val item = NewsFeed.item(NewsFeed.top(NewsFeed.parse(rss))!!, at0745)
        assertEquals("Lula re-elected for a fourth term in Brazil", item.headline)
        assertEquals("BBC News · 15 min ago", item.source)
        assertEquals("https://www.bbc.co.uk/news/articles/c1?at_medium=RSS&at_campaign=rss", item.link)
    }
}
