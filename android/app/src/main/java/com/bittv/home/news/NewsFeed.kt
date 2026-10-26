package com.bittv.home.news

import com.bittv.home.model.NewsItem
import org.w3c.dom.Element
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** One story from the feed. Times are epoch milliseconds. */
data class Headline(val title: String, val link: String, val publishedMs: Long)

/**
 * The headline on Home: BBC News's front page (its RSS feed: free, no key, editorial order, refreshed every
 * 15 min — the feed's own ttl). The top written story is shown; "Watch:" video clips only when there is nothing else.
 */
object NewsFeed {
    const val URL = "https://feeds.bbci.co.uk/news/rss.xml"
    const val SOURCE = "BBC News"
    const val REFRESH_MS = 15 * 60_000L

    fun parse(xml: String): List<Headline> = try {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val items = doc.getElementsByTagName("item")
        (0 until items.length).mapNotNull { i ->
            val e = items.item(i) as Element
            fun text(tag: String) = e.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val title = text("title"); val link = text("link")
            val published = runCatching { ZonedDateTime.parse(text("pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            if (title.isEmpty() || published == null) null else Headline(title, link, published)
        }
    } catch (e: Exception) { emptyList() }   // not the feed (an error page, a captive portal): no story

    fun top(items: List<Headline>): Headline? = items.firstOrNull { !isVideo(it) } ?: items.firstOrNull()

    private fun isVideo(h: Headline) = h.title.startsWith("Watch:") || "/videos/" in h.link

    fun ago(publishedMs: Long, nowMs: Long): String {
        val min = (nowMs - publishedMs) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 24 * 60 -> "${min / 60} hr ago"
            min < 48 * 60 -> "1 day ago"
            else -> "${min / (24 * 60)} days ago"
        }
    }

    fun item(h: Headline, nowMs: Long) = NewsItem(h.title, "$SOURCE · ${ago(h.publishedMs, nowMs)}", h.link)
}

/** The feed over the network; null when it can't be read (offline): the last headline then stays. */
class LiveNews(private val get: suspend (String) -> String?) {
    suspend fun headlines(): List<Headline>? = get(NewsFeed.URL)?.let(NewsFeed::parse)?.takeIf { it.isNotEmpty() }
}
