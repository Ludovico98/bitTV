package com.bittv.home.assistant

import com.bittv.home.model.WeatherKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LiveAnswersTest {
    private val meteo = """{"current":{"temperature_2m":11.4,"weather_code":61},
        "daily":{"time":["2026-10-25","2026-10-26","2026-10-27","2026-10-28"],"weather_code":[61,3,0,80],
        "temperature_2m_max":[14.2,15.8,17.1,13.0],"temperature_2m_min":[8.1,9.4,7.7,6.0]}}"""
    private val search = """{"pages":[{"key":"Leonardo_da_Vinci","title":"Leonardo da Vinci"}]}"""
    private val summary = """{"type":"standard","title":"Leonardo da Vinci","extract":"Leonardo da Vinci was an Italian polymath of the High Renaissance. He is widely regarded as one of the greatest painters. More text here.",
        "thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Leo.jpg/330px-Leo.jpg"}}"""
    private fun answers(fetch: suspend (String) -> String?) = LiveAnswers(fetch, now = { LocalDateTime.of(2026, 10, 25, 14, 5) })
    private fun fake(map: Map<String, String?>): suspend (String) -> String? = { url -> map.entries.firstOrNull { it.key in url }?.value }
    private val online = fake(mapOf("open-meteo" to meteo, "search/title" to search, "page/summary" to summary))

    @Test fun weatherNowFromOpenMeteo() = runTest {
        val a = answers(online).answer("what's the weather like?") as Answer.Weather
        assertEquals(WeatherKind.RAIN, a.kind); assertEquals(11, a.tempC)
        assertEquals(listOf("Mon", "Tue", "Wed"), a.days.map { it.label })
        assertTrue(a.say, a.say.contains("11 degrees"))
    }

    @Test fun tomorrowUsesTheDailyForecast() = runTest {
        val a = answers(online).answer("will it rain tomorrow") as Answer.Weather
        assertEquals(WeatherKind.CLOUD, a.kind); assertEquals(16, a.tempC)
        assertTrue(a.say, a.say.startsWith("Tomorrow"))
    }

    @Test fun weatherCodes() {
        assertEquals(WeatherKind.SUN, LiveAnswers.kind(0)); assertEquals(WeatherKind.SUN, LiveAnswers.kind(1))
        assertEquals(WeatherKind.CLOUD, LiveAnswers.kind(3)); assertEquals(WeatherKind.CLOUD, LiveAnswers.kind(45))
        assertEquals(WeatherKind.CLOUD, LiveAnswers.kind(73)); assertEquals(WeatherKind.RAIN, LiveAnswers.kind(61))
        assertEquals(WeatherKind.RAIN, LiveAnswers.kind(95))
    }

    @Test fun timeIsAnsweredLocally() = runTest {
        val a = answers { null }.answer("what time is it") as Answer.Clock
        assertEquals("14:05", a.time)
    }

    @Test fun whoQuestionGivesAPictureReadAloud() = runTest {
        val a = answers(online).answer("who was Leonardo da Vinci") as Answer.Picture
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Leo.jpg/960px-Leo.jpg", a.imageUrl)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Leo.jpg/330px-Leo.jpg", a.fallbackUrl)
        assertEquals("Leonardo da Vinci was an Italian polymath of the High Renaissance. He is widely regarded as one of the greatest painters.", a.say)
    }

    @Test fun noThumbnailGivesText() = runTest {
        val plain = summary.replace(Regex(""",\s*"thumbnail":\{[^}]*\}"""), "")
        val a = answers(fake(mapOf("search/title" to search, "page/summary" to plain))).answer("what is the Renaissance")
        assertTrue(a is Answer.Text)
    }

    @Test fun disambiguationHandsOverToGoogle() = runTest {
        val dis = """{"type":"disambiguation","title":"Mercury","extract":"Mercury may refer to:"}"""
        val a = answers(fake(mapOf("search/title" to search, "page/summary" to dis))).answer("what is mercury")
        assertEquals(Answer.Handoff("what is mercury"), a)
    }

    @Test fun termStripsQuestionWords() {
        assertEquals("Leonardo da Vinci", LiveAnswers.term("Who was Leonardo da Vinci?"))
        assertEquals("the Eiffel Tower", LiveAnswers.term("how tall is the Eiffel Tower"))
        assertEquals("Ferrari", LiveAnswers.term("tell me about Ferrari"))
        assertEquals("Colosseo", LiveAnswers.term("cos'è il Colosseo"))
    }

    @Test fun commandsAndUnknownsGoToGoogle() = runTest {
        for (q in listOf("play Stranger Things on Netflix", "turn off the lights", "open YouTube", "set a timer for 5 minutes"))
            assertEquals(q, Answer.Handoff(q), answers(online).answer(q))
    }

    @Test fun offlineSaysSoInsteadOfInventing() = runTest {
        val a = answers { null }.answer("what's the weather")
        assertTrue(a is Answer.Text); assertTrue(a.say, a.say.contains("internet"))
    }
}
