package com.bittv.home.news

import com.bittv.home.model.NewsItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.w3c.dom.Element
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** One story from a feed. Times are epoch milliseconds. */
data class Headline(val title: String, val link: String, val publishedMs: Long, val source: String = NewsFeed.SOURCE)

/**
 * The headline on Home, taking turns between several sites' RSS feeds (free, no key): the news, and science and
 * space stories worth a look. The feeds are read every half hour; the story shown changes every 7 minutes, never
 * twice running from the same site while another has something. "Watch:" video clips only when there is nothing else.
 * (Ground News has no public feed, so it isn't one of them.)
 */
object NewsFeed {
    data class Source(val name: String, val url: String)

    val SOURCES = listOf(
        Source("BBC News", "https://feeds.bbci.co.uk/news/rss.xml"),
        Source("NASA", "https://www.nasa.gov/news-release/feed/"),
        Source("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"),
        Source("Guardian Science", "https://www.theguardian.com/science/rss"),
        Source("ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml"),
        Source("Smithsonian", "https://www.smithsonianmag.com/rss/latest_articles/"),
        Source("NPR", "https://feeds.npr.org/1001/rss.xml"),
        Source("ESA", "https://www.esa.int/rssfeed/Our_Activities/Space_Science"),
    )
    const val SOURCE = "BBC News"
    const val REFRESH_MS = 30 * 60_000L
    const val ROTATE_MS = 7 * 60_000L
    /** Stories per site in one turn round, and how old one may be (a slow site's week-old story would stick out). */
    private const val PER_SOURCE = 5
    private const val FRESH_MS = 48 * 3_600_000L

    fun parse(xml: String, source: String = SOURCE): List<Headline> = try {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val items = doc.getElementsByTagName("item")
        (0 until items.length).mapNotNull { i ->
            val e = items.item(i) as Element
            fun text(tag: String) = e.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
            val title = text("title"); val link = text("link")
            val published = runCatching { ZonedDateTime.parse(text("pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            if (title.isEmpty() || published == null) null else Headline(title, link, published, source)
        }
    } catch (e: Exception) { emptyList() }   // not the feed (an error page, a captive portal): no story

    fun top(items: List<Headline>): Headline? = items.firstOrNull { !isVideo(it) } ?: items.firstOrNull()

    /**
     * The order the stories take turns in: each site's freshest written stories in its own order, one site after
     * another (the site with the newest story first), so the same site never shows twice running while another
     * still has one. The same story on two sites shows once.
     */
    fun rotation(bySource: List<List<Headline>>, nowMs: Long): List<Headline> {
        val lists = bySource.map { items ->
            val written = items.filter { !isVideo(it) && nowMs - it.publishedMs <= FRESH_MS }
            written.ifEmpty { listOfNotNull(top(items)) }.take(PER_SOURCE)
        }.filter { it.isNotEmpty() }.sortedByDescending { l -> l.maxOf { it.publishedMs } }
        val seen = mutableSetOf<String>()
        return (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
            .filter { seen.add(it.title.lowercase()) }
    }

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

    fun item(h: Headline, nowMs: Long) = NewsItem(h.title, "${h.source} · ${ago(h.publishedMs, nowMs)}", h.link)
}

/**
 * The feeds over the network, all at once. A site that can't be read keeps its last stories; null when none has
 * ever been read (offline): the last headline then stays.
 */
class LiveNews(private val get: suspend (String) -> String?, private val sources: List<NewsFeed.Source> = NewsFeed.SOURCES) {
    private val last = mutableMapOf<String, List<Headline>>()

    suspend fun rotation(nowMs: Long): List<Headline>? = coroutineScope {
        val read = sources.map { s -> async { s.name to get(s.url)?.let { NewsFeed.parse(it, s.name) }?.takeIf { it.isNotEmpty() } } }.awaitAll()
        read.forEach { (name, items) -> if (items != null) last[name] = items }
        NewsFeed.rotation(sources.mapNotNull { last[it.name] }, nowMs).takeIf { it.isNotEmpty() }
    }
}
