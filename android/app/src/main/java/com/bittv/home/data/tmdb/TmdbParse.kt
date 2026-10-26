package com.bittv.home.data.tmdb

import com.bittv.home.model.Progress
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

/** Pure parsing of TMDB v3 responses into BitTV's model; no network here, so it is tested on the JVM. */
object TmdbParse {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** The BitTV service a TMDB/JustWatch provider name belongs to (ad tiers and renamed brands included). */
    fun svcFor(providerName: String): Svc? {
        val n = norm(providerName)
        return when {
            n.startsWith("netflix") -> Svc.NETFLIX
            n.startsWith("amazonprimevideo") -> Svc.PRIME
            n in setOf("now", "nowtv", "nowtvcinema") -> Svc.NOW
            n.startsWith("paramount") -> Svc.PARAMOUNT
            n == "appletv" || n == "appletvplus" -> Svc.APPLETV
            n == "disneyplus" -> Svc.DISNEY
            n == "tubi" || n == "tubitv" -> Svc.TUBI
            n == "itvx" -> Svc.ITVX
            n == "la7" -> Svc.LA7
            n == "raiplay" -> Svc.RAIPLAY
            else -> null
        }
    }

    /** /watch/providers/{tv|movie} → provider ids per service. */
    fun providerIds(json: String): Map<Svc, Set<Int>> {
        val out = mutableMapOf<Svc, MutableSet<Int>>()
        val arr = JSONObject(json).optJSONArray("results") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val svc = svcFor(o.optString("provider_name")) ?: continue
            out.getOrPut(svc) { mutableSetOf() } += o.getInt("provider_id")
        }
        return out
    }

    /** /genre/{tv|movie}/list → id to name. */
    fun genres(json: String): Map<Int, String> {
        val arr = JSONObject(json).optJSONArray("genres") ?: return emptyMap()
        return (0 until arr.length()).associate { arr.getJSONObject(it).let { g -> g.getInt("id") to g.getString("name") } }
    }

    /** One /discover page → titles on [svc]. Low vote counts are damped so a 10/10 from three people can't top the banner. */
    fun discover(json: String, type: TitleType, svc: Svc, genres: Map<Int, String>): List<Title> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i -> item(arr.getJSONObject(i), type, listOf(svc), genres) }
    }

    /** One TMDB movie or series object (discover or search) → a Title on [services]. */
    fun item(o: JSONObject, type: TitleType, services: List<Svc>, genres: Map<Int, String>): Title {
        val id = o.getInt("id")
        val name = listOf("name", "title", "original_name", "original_title").map { o.optString(it) }.firstOrNull { it.isNotBlank() } ?: "—"
        val date = o.optString("first_air_date").ifBlank { o.optString("release_date") }
        val ids = o.optJSONArray("genre_ids") ?: JSONArray()
        val votes = o.optDouble("vote_average", 0.0); val count = o.optInt("vote_count", 0)
        val (c1, c2) = fallbackColours(id)
        return Title(
            id = (if (type == TitleType.SERIES) "tv" else "mv") + id, name = name, type = type,
            genres = (0 until ids.length()).mapNotNull { genres[ids.getInt(it)] }, services = services,
            c1 = c1, c2 = c2, year = date.take(4).toIntOrNull() ?: 0, score = votes * min(1.0, count / 200.0),
            font = TitleFont.entries[abs(id) % TitleFont.entries.size], blurb = o.optString("overview"),
            art = if (o.isNull("backdrop_path")) null else o.optString("backdrop_path").ifBlank { null },
            lang = o.optString("original_language"),
        )
    }

    /** One list per (service, type, page or keyword) → one title each, with every service it is on and every tag, in first-seen order. */
    fun merge(lists: List<List<Title>>): List<Title> {
        val byId = LinkedHashMap<String, Title>()
        lists.flatten().forEach { t ->
            val have = byId[t.id]
            byId[t.id] = if (have == null) t else have.copy(services = (have.services + t.services).distinct(), tags = (have.tags + t.tags).distinct())
        }
        return byId.values.toList()
    }

    /**
     * Continue Watching for the demo: a few real titles with made-up positions (the TV's Watch Next replaces this).
     * It skips the five best-rated, which seed Watch Later, so the two rows don't repeat each other.
     */
    fun demoProgress(titles: List<Title>): List<Progress> {
        val f = listOf(0.62f, 0.35f, 0.8f, 0.15f)
        val ranked = titles.sortedByDescending { it.score }
        return ranked.drop(minOf(5, ranked.size / 2)).take(f.size).mapIndexed { i, t ->
            Progress(t.id, f[i], if (t.type == TitleType.SERIES) "S1 · E${i + 2} · ${((1 - f[i]) * 50).toInt()} min left"
                                 else "${((1 - f[i]) * 110).toInt()} min left")
        }
    }

    /** Dark, distinct poster colours from the id until the artwork's own colours are known. */
    private fun fallbackColours(id: Int): Pair<Long, Long> {
        val h = (abs(id) * 47 % 360).toFloat()
        return hsv(h, 0.55f, 0.32f) to hsv((h + 35) % 360, 0.5f, 0.62f)
    }

    private fun hsv(h: Float, s: Float, v: Float): Long {
        val c = v * s; val x = c * (1 - abs((h / 60) % 2 - 1)); val m = v - c
        val (r, g, b) = when ((h / 60).toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        fun ch(f: Float) = ((f + m) * 255).toLong().coerceIn(0, 255)
        return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun toCache(titles: List<Title>): String = JSONArray().apply {
        titles.forEach { t ->
            put(JSONObject().put("id", t.id).put("name", t.name).put("type", t.type.name).put("genres", JSONArray(t.genres))
                .put("services", JSONArray(t.services.map { it.name })).put("c1", t.c1).put("c2", t.c2).put("year", t.year)
                .put("score", t.score).put("font", t.font.name).put("blurb", t.blurb).put("art", t.art ?: JSONObject.NULL)
                .put("buy", JSONArray(t.buy.map { it.name })).put("elsewhere", JSONArray(t.elsewhere)).put("tags", JSONArray(t.tags)).put("lang", t.lang))
        }
    }.toString()

    /** Null when the cache is unreadable (then the catalogue is fetched again). */
    fun fromCache(json: String): List<Title>? = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun strings(k: String) = (o.optJSONArray(k) ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) } }
            Title(o.getString("id"), o.getString("name"), TitleType.valueOf(o.getString("type")), strings("genres"),
                strings("services").map { Svc.valueOf(it) }, o.getLong("c1"), o.getLong("c2"), o.getInt("year"),
                o.getDouble("score"), TitleFont.valueOf(o.getString("font")), o.getString("blurb"),
                if (o.isNull("art")) null else o.getString("art"),
                // older caches have neither
                strings("buy").map { Svc.valueOf(it) }, strings("elsewhere"), strings("tags"), o.optString("lang"))
        }
    }.getOrNull()
}
