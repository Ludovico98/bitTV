package com.bittv.home.assistant

import com.bittv.home.model.WeatherKind
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Real answers: weather (Open-Meteo, London), the time, and "who/what is…" from Wikipedia with its picture.
 * Anything else — commands, smart home, a question Wikipedia can't settle — is handed to Google Assistant.
 * [fetch] returns a body or null (offline); nothing is ever invented.
 */
class LiveAnswers(
    private val fetch: suspend (String) -> String?,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val place: String = "London", private val lat: Double = 51.51, private val lon: Double = -0.13,
    /** General knowledge (Claude) for any question; null without a key — then Wikipedia handles who/what questions. */
    private val general: GeneralSource? = null,
) : AnswerProvider {

    override suspend fun answer(query: String): Answer = when {
        AnswerClassifier.kind(query) == AnswerKind.WEATHER -> weather(query)
        AnswerClassifier.kind(query) == AnswerKind.TIME -> clock()
        COMMAND.containsMatchIn(query.trim()) -> Answer.Handoff(query)   // only Google can play, open or switch things
        else -> general?.ask(query)?.let { fromGeneral(query, it) }
            ?: if (QUESTION.containsMatchIn(query.trim())) wiki(query) else Answer.Handoff(query)
    }

    private suspend fun fromGeneral(q: String, r: GeneralReply): Answer {
        if (r.needsGoogle || r.say.isBlank()) return Answer.Handoff(q)
        val pic = r.subject?.let { summary(it) }?.thumb
        return if (pic != null) Answer.Picture(pic.replace(Regex("/\\d+px-"), "/960px-"), pic, r.subject, r.say) else Answer.Text(q, r.say, r.say)
    }

    private class Summary(val title: String, val extract: String, val thumb: String?, val disambiguation: Boolean)

    /** Wikipedia's best page for [term]: title, first sentences and picture; null when offline or nothing matches. */
    private suspend fun summary(term: String): Summary? {
        val search = fetch("https://en.wikipedia.org/w/rest.php/v1/search/title?q=${enc(term)}&limit=1") ?: return null
        val key = runCatching { JSONObject(search).getJSONArray("pages").getJSONObject(0).getString("key") }.getOrNull() ?: return null
        val body = fetch("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(key)}") ?: return null
        return runCatching {
            val j = JSONObject(body)
            Summary(j.getString("title"), j.optString("extract"), j.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
                j.optString("type") == "disambiguation")
        }.getOrNull()
    }

    private fun offline() = Answer.Text("No internet", "BitTV can't reach the internet right now.", "Sorry, I can't reach the internet right now.")

    private fun clock(): Answer.Clock {
        val n = now()
        return Answer.Clock(n.format(DateTimeFormatter.ofPattern("HH:mm")),
            n.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)) + " · $place",
            "It's ${n.format(DateTimeFormatter.ofPattern("h:mm a", Locale.UK)).lowercase()}.")
    }

    private fun forecastUrl() = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Europe%2FLondon&forecast_days=4"

    /** The weather right now, for the clock line on Home; null when offline (never invented). */
    suspend fun current(): com.bittv.home.model.WeatherNow? = fetch(forecastUrl())?.let { body ->
        runCatching { JSONObject(body).getJSONObject("current").let { c ->
            com.bittv.home.model.WeatherNow(Math.round(c.getDouble("temperature_2m")).toInt(), kind(c.getInt("weather_code"))) } }.getOrNull()
    }

    private suspend fun weather(q: String): Answer {
        val body = fetch(forecastUrl()) ?: return offline()
        return runCatching {
            val j = JSONObject(body); val cur = j.getJSONObject("current"); val d = j.getJSONObject("daily")
            val codes = d.getJSONArray("weather_code"); val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min")
            val dates = d.getJSONArray("time")
            val days = (1 until minOf(4, dates.length())).map { i ->
                DayForecast(LocalDate.parse(dates.getString(i)).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK),
                    kind(codes.getInt(i)), Math.round(hi.getDouble(i)).toInt(), Math.round(lo.getDouble(i)).toInt())
            }
            if ("tomorrow" in q.lowercase() || "domani" in q.lowercase()) {
                val k = kind(codes.getInt(1)); val h = Math.round(hi.getDouble(1)).toInt(); val l = Math.round(lo.getDouble(1)).toInt()
                Answer.Weather(k, h, "Tomorrow · ${feel(k).replaceFirstChar { it.uppercase() }}", days,
                    "Tomorrow in $place it will be ${feel(k)}, with a high of $h and a low of $l degrees.", place)
            } else {
                val k = kind(cur.getInt("weather_code")); val t = Math.round(cur.getDouble("temperature_2m")).toInt()
                Answer.Weather(k, t, "Now · ${feel(k).replaceFirstChar { it.uppercase() }}", days, "Right now in $place it's ${feel(k)} and $t degrees.", place)
            }
        }.getOrElse { offline() }
    }

    private suspend fun wiki(q: String): Answer {
        val term = term(q).ifBlank { return Answer.Handoff(q) }
        if (fetch("https://en.wikipedia.org/w/rest.php/v1/search/title?q=${enc(term)}&limit=1") == null) return offline()
        val s = summary(term) ?: return Answer.Handoff(q)
        if (s.disambiguation) return Answer.Handoff(q)
        val say = s.extract.split(Regex("(?<=[.!?])\\s+")).take(2).joinToString(" ")
        return if (s.thumb != null) Answer.Picture(s.thumb.replace(Regex("/\\d+px-"), "/960px-"), s.thumb, s.title, say)
        else Answer.Text(s.title, say, say)
    }

    companion object {
        /** Things to do rather than know: only Google Assistant can play, open, switch or set them. */
        private val COMMAND = Regex("^(play|open|launch|start|put on|turn|switch|set|remind|call|show me|watch|pause|stop|resume|" +
            "skip|volume|mute|record|cast|metti|apri|accendi|spegni|guarda|riproduci|imposta)\\b", RegexOption.IGNORE_CASE)
        /** Questions Wikipedia can answer (English and Italian). Commands and anything else go to Google. */
        private val QUESTION = Regex("^(who|what|where|when|which|how|why|tell me about|chi|cos|cosa|dove|quando|quanto|com'è|come)\\b",
            RegexOption.IGNORE_CASE)
        private val LEAD = Regex("^(who (was|is|were|are)|what (was|is|are|were)|what's|where (is|was)|when (was|is|did)|which is|" +
            "how (tall|old|big|long|high|far|deep) (is|was|are)|tell me about|chi (era|è|e)|cos'è|cosa è|cos è|dov'è|dove (si )?trova)\\s+",
            RegexOption.IGNORE_CASE)

        fun term(q: String): String = q.trim().trimEnd('?', '.', '!').replace(LEAD, "")
            .replace(Regex("^(il|lo|la|i|gli|le|l')\\s+", RegexOption.IGNORE_CASE), "").trim()

        /** Open-Meteo / WMO weather codes. */
        fun kind(code: Int): WeatherKind = when {
            code <= 1 -> WeatherKind.SUN
            code <= 48 || code in 71..77 || code in 85..86 -> WeatherKind.CLOUD
            else -> WeatherKind.RAIN
        }
        private fun feel(k: WeatherKind) = when (k) { WeatherKind.SUN -> "sunny"; WeatherKind.CLOUD -> "cloudy"; WeatherKind.RAIN -> "rainy" }
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

/** A general-knowledge answer: [say] is read aloud; [subject] names what a picture should show; [needsGoogle] when only
 *  Google can answer (the user's own accounts, devices, live scores, bookings…). */
data class GeneralReply(val say: String, val subject: String?, val needsGoogle: Boolean)

fun interface GeneralSource { suspend fun ask(question: String): GeneralReply? }
