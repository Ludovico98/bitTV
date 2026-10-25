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

    @Test fun weatherIsForRedhill() = runTest {
        var asked = ""
        val a = LiveAnswers({ url -> asked = url; meteo }, place = "Redhill", lat = 51.24, lon = -0.17).answer("what's the weather") as Answer.Weather
        assertTrue(asked, "latitude=51.24" in asked && "longitude=-0.17" in asked)
        assertEquals("Redhill", a.place); assertTrue(a.say, "Redhill" in a.say)
    }

    @Test fun currentWeatherForTheClockLine() = runTest {
        assertEquals(com.bittv.home.model.WeatherNow(11, WeatherKind.RAIN), answers(online).current())
        assertEquals(null, answers { null }.current())
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

class GeneralAnswersTest {
    private val summary = """{"type":"standard","title":"Colosseum","extract":"The Colosseum is an amphitheatre in Rome.",
        "thumbnail":{"source":"https://upload.wikimedia.org/x/330px-Colosseo.jpg"}}"""
    private val search = """{"pages":[{"key":"Colosseum","title":"Colosseum"}]}"""
    private fun fetch(url: String): String? = when { "search/title" in url -> search; "page/summary" in url -> summary; else -> null }
    private fun answers(reply: GeneralReply?, asked: MutableList<String> = mutableListOf()) =
        LiveAnswers({ fetch(it) }, now = { java.time.LocalDateTime.of(2026, 10, 25, 14, 5) },
            general = GeneralSource { q -> asked += q; reply })

    @Test fun anyQuestionIsAnsweredInTheSectionWithItsPicture() = runTest {
        val a = answers(GeneralReply("It was finished in 80 AD under Titus.", subject = "Colosseum", needsGoogle = false))
            .answer("when was the colosseum built") as Answer.Picture
        assertEquals("It was finished in 80 AD under Titus.", a.say)
        assertEquals("https://upload.wikimedia.org/x/960px-Colosseo.jpg", a.imageUrl)
    }

    @Test fun answerWithoutASubjectIsText() = runTest {
        val a = answers(GeneralReply("A marathon is 42.195 kilometres.", subject = null, needsGoogle = false)).answer("how long is a marathon")
        assertEquals(Answer.Text("how long is a marathon", "A marathon is 42.195 kilometres.", "A marathon is 42.195 kilometres."), a)
    }

    @Test fun somethingOnlyGoogleKnowsIsHandedOver() = runTest {
        val q = "what's on my calendar today"
        assertEquals(Answer.Handoff(q), answers(GeneralReply("", null, needsGoogle = true)).answer(q))
    }

    @Test fun commandsNeverReachTheModel() = runTest {
        val asked = mutableListOf<String>()
        for (q in listOf("play Stranger Things on Netflix", "turn off the lights", "open YouTube", "metti Rai 1"))
            assertEquals(Answer.Handoff(q), answers(GeneralReply("x", null, false), asked).answer(q))
        assertTrue(asked.isEmpty())
    }

    @Test fun weatherAndTimeStayLocal() = runTest {
        val asked = mutableListOf<String>()
        answers(GeneralReply("x", null, false), asked).answer("what time is it")
        assertTrue(asked.isEmpty())
    }

    @Test fun noModelReplyFallsBackToWikipedia() = runTest {
        val a = answers(null).answer("what is the Colosseum")
        assertTrue(a is Answer.Picture)
    }
}
