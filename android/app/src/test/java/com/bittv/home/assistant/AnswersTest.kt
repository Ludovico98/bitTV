package com.bittv.home.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswersTest {
    @Test fun fitModeKeepsPortraitWhole() = assertEquals(FitMode.FIT, fitMode(1286, 1842, 432, 296))
    @Test fun fitModeCropsExtremePanorama() = assertEquals(FitMode.CROP, fitMode(4000, 300, 432, 296))
    @Test fun fitModeKeepsLandscapePhotoWhole() = assertEquals(FitMode.FIT, fitMode(960, 640, 432, 296))

    @Test fun weatherQuestion() = assertEquals(AnswerKind.WEATHER, AnswerClassifier.kind("Will it rain tomorrow?"))
    @Test fun cloudyIsWeather() = assertEquals(AnswerKind.WEATHER, AnswerClassifier.kind("Is it cloudy outside?"))
    @Test fun timeQuestion() = assertEquals(AnswerKind.TIME, AnswerClassifier.kind("What time is it?"))
    @Test fun generalQuestion() = assertEquals(AnswerKind.QUESTION, AnswerClassifier.kind("Who was Leonardo da Vinci?"))

    @Test
    fun demoCyclesThroughTheSixExamples() {
        val d = DemoAnswers()
        val qs = List(7) { d.nextQuestion() }
        assertEquals("What’s the weather like today?", qs[0])
        assertEquals(qs[0], qs[6])
        assertEquals(6, qs.take(6).toSet().size)
    }
}
