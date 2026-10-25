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
) : AnswerProvider {

    override suspend fun answer(query: String): Answer = when {
        AnswerClassifier.kind(query) == AnswerKind.WEATHER -> weather(query)
        AnswerClassifier.kind(query) == AnswerKind.TIME -> clock()
        QUESTION.containsMatchIn(query.trim()) -> wiki(query)
        else -> Answer.Handoff(query)
    }

    private fun offline() = Answer.Text("No internet", "BitTV can't reach the internet right now.", "Sorry, I can't reach the internet right now.")

    private fun clock(): Answer.Clock {
        val n = now()
        return Answer.Clock(n.format(DateTimeFormatter.ofPattern("HH:mm")),
            n.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)) + " · $place",
            "It's ${n.format(DateTimeFormatter.ofPattern("h:mm a", Locale.UK)).lowercase()}.")
    }

    private suspend fun weather(q: String): Answer {
        val body = fetch("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Europe%2FLondon&forecast_days=4") ?: return offline()
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
                    "Tomorrow in $place it will be ${feel(k)}, with a high of $h and a low of $l degrees.")
            } else {
                val k = kind(cur.getInt("weather_code")); val t = Math.round(cur.getDouble("temperature_2m")).toInt()
                Answer.Weather(k, t, "Now · ${feel(k).replaceFirstChar { it.uppercase() }}", days, "Right now in $place it's ${feel(k)} and $t degrees.")
            }
        }.getOrElse { offline() }
    }

    private suspend fun wiki(q: String): Answer {
        val term = term(q).ifBlank { return Answer.Handoff(q) }
        val search = fetch("https://en.wikipedia.org/w/rest.php/v1/search/title?q=${enc(term)}&limit=1") ?: return offline()
        val key = runCatching { JSONObject(search).getJSONArray("pages").getJSONObject(0).getString("key") }.getOrNull() ?: return Answer.Handoff(q)
        val body = fetch("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(key)}") ?: return offline()
        return runCatching {
            val j = JSONObject(body)
            if (j.optString("type") == "disambiguation") return Answer.Handoff(q)
            val title = j.getString("title")
            val say = j.getString("extract").split(Regex("(?<=[.!?])\\s+")).take(2).joinToString(" ")
            val thumb = j.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
            if (thumb != null) Answer.Picture(thumb.replace(Regex("/\\d+px-"), "/960px-"), thumb, title, say)
            else Answer.Text(title, say, say)
        }.getOrElse { Answer.Handoff(q) }
    }

    companion object {
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
