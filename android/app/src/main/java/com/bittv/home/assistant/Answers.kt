package com.bittv.home.assistant

import com.bittv.home.model.WeatherKind
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayForecast(val label: String, val kind: WeatherKind, val hi: Int, val lo: Int)

/** What the Assistant shows; [say] is what the voice reads out. */
sealed interface Answer {
    val say: String
    data class Weather(val kind: WeatherKind, val tempC: Int, val label: String, val days: List<DayForecast>, override val say: String,
                       val place: String = "London") : Answer
    data class Clock(val time: String, val date: String, override val say: String) : Answer
    /** A picture answer has no text on screen: it is spoken. */
    data class Picture(val imageUrl: String, val fallbackUrl: String?, val title: String, override val say: String) : Answer
    data class Text(val title: String, val body: String, override val say: String) : Answer
    /** Not BitTV's to answer (play something, smart home, apps…): handed to Google Assistant as spoken. */
    data class Handoff(val query: String) : Answer { override val say get() = "" }
}

/** Reads an answer aloud and returns when it has finished (or immediately if cancelled). */
fun interface Speaker { suspend fun speak(text: String) }

fun interface AnswerProvider { suspend fun answer(query: String): Answer }

enum class AnswerKind { WEATHER, TIME, QUESTION }

object AnswerClassifier {
    private val WEATHER = Regex("weather|rain|sunny|cloudy|temperature|umbrella|forecast|\\bcold\\b|\\bhot\\b", RegexOption.IGNORE_CASE)
    private val TIME = Regex("\\btime\\b", RegexOption.IGNORE_CASE)
    fun kind(query: String) = when {
        WEATHER.containsMatchIn(query) -> AnswerKind.WEATHER
        TIME.containsMatchIn(query) -> AnswerKind.TIME
        else -> AnswerKind.QUESTION
    }
}

/** No voice engine (or none installed): wait roughly as long as reading the text would take. */
object EstimatedSpeaker : Speaker {
    override suspend fun speak(text: String) = delay(1_500L + text.split(Regex("\\s+")).size * 380L)
}

/**
 * The six scripted examples (weather sun/rain/cloud, two picture answers, the time), cycled by the remote's mic
 * button in the demo. Plan 2 swaps in live weather, Wikipedia and speech.
 */
class DemoAnswers(private val now: () -> LocalDateTime = { LocalDateTime.now() }) : AnswerProvider {
    private val questions = listOf(
        "What’s the weather like today?", "Will it rain tomorrow?", "Is it cloudy outside?",
        "Who was Leonardo da Vinci?", "How tall is the Eiffel Tower?", "What time is it?",
    )
    private var i = 0
    fun nextQuestion(): String = questions[i++ % questions.size]

    override suspend fun answer(query: String): Answer = when (AnswerClassifier.kind(query)) {
        AnswerKind.WEATHER -> weather(query)
        AnswerKind.TIME -> {
            val n = now()
            Answer.Clock(n.format(DateTimeFormatter.ofPattern("HH:mm")),
                n.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)) + " · London",
                "It's ${n.format(DateTimeFormatter.ofPattern("h:mm", Locale.UK))}.")
        }
        AnswerKind.QUESTION -> when {
            "leonardo" in query.lowercase() -> Answer.Picture(LEONARDO, LEONARDO_SMALL, "Leonardo da Vinci",
                "Leonardo di ser Piero da Vinci was an Italian polymath of the High Renaissance, active as a painter, draughtsman, engineer, scientist and architect.")
            "eiffel" in query.lowercase() -> Answer.Picture(EIFFEL, null, "Eiffel Tower",
                "The Eiffel Tower is a lattice tower on the Champ de Mars in Paris. It is 330 metres tall.")
            else -> Answer.Text("I can’t look that up yet", "Questions are answered from Wikipedia once the TV is online.", "Sorry, I can't look that up yet.")
        }
    }

    private fun weather(q: String): Answer.Weather {
        val tomorrow = "tomorrow" in q.lowercase()
        val kind = when { "rain" in q.lowercase() -> WeatherKind.RAIN; "cloud" in q.lowercase() -> WeatherKind.CLOUD; else -> WeatherKind.SUN }
        val days = listOf(DayForecast("Mon", WeatherKind.RAIN, 16, 9), DayForecast("Tue", WeatherKind.RAIN, 16, 10), DayForecast("Wed", WeatherKind.CLOUD, 16, 9))
        val feel = when (kind) { WeatherKind.SUN -> "sunny"; WeatherKind.CLOUD -> "cloudy"; WeatherKind.RAIN -> "rainy" }
        return if (tomorrow) Answer.Weather(kind, 16, "Tomorrow · ${feel.replaceFirstChar { it.uppercase() }}", days,
            "Tomorrow in London it will be $feel, with a high of 16 and a low of 9 degrees.")
        else Answer.Weather(kind, 11, "Now · ${feel.replaceFirstChar { it.uppercase() }}", days, "Right now in London it's $feel and 11 degrees.")
    }

    private companion object {
        const val LEONARDO = "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/16/Francesco_Melzi_-_Portrait_of_Leonardo_%28colour_correction%29.png/960px-Francesco_Melzi_-_Portrait_of_Leonardo_%28colour_correction%29.png"
        const val LEONARDO_SMALL = "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/16/Francesco_Melzi_-_Portrait_of_Leonardo_%28colour_correction%29.png/330px-Francesco_Melzi_-_Portrait_of_Leonardo_%28colour_correction%29.png"
        const val EIFFEL = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Tour_Eiffel_Wikimedia_Commons_%28cropped%29.jpg/960px-Tour_Eiffel_Wikimedia_Commons_%28cropped%29.jpg"
    }
}
