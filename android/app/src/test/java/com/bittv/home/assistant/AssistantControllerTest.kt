package com.bittv.home.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantControllerTest {
    private class FakeSpeaker(val ms: Long) : Speaker {
        var spoken = mutableListOf<String>(); var cancelled = 0
        override suspend fun speak(text: String) {
            spoken += text
            try { delay(ms) } catch (e: CancellationException) { cancelled++; throw e }
        }
    }
    private val answer = Answer.Text("Eiffel Tower", "A lattice tower in Paris.", "The Eiffel Tower is a lattice tower in Paris.")
    private fun TestScope.controller(speaker: Speaker) = AssistantController(backgroundScope, speaker, { answer })

    private fun AssistantState.counting() = (this as? AssistantState.Showing)?.counting == true

    @Test
    fun countdownStartsOnlyAfterSpeech() = runTest {
        val c = controller(FakeSpeaker(5_000))
        c.submit("how tall is the eiffel tower"); runCurrent()
        assertTrue(c.state.value is AssistantState.Showing)
        advanceTimeBy(4_000); runCurrent()
        assertFalse(c.state.value.counting())
        advanceTimeBy(1_001); runCurrent()
        assertTrue(c.state.value.counting())
    }

    @Test
    fun autoDismissAfter30sOfCounting() = runTest {
        val c = controller(FakeSpeaker(2_000))
        c.submit("q"); runCurrent()
        advanceTimeBy(2_000 + 29_900); runCurrent()
        assertTrue(c.state.value is AssistantState.Showing)
        advanceTimeBy(200); runCurrent()
        assertEquals(AssistantState.Idle, c.state.value)
    }

    @Test
    fun dismissDuringSpeechCancels() = runTest {
        val s = FakeSpeaker(5_000); val c = controller(s)
        c.submit("q"); runCurrent(); advanceTimeBy(1_000); runCurrent()
        c.dismiss(); runCurrent()
        assertEquals(1, s.cancelled)
        assertEquals(AssistantState.Idle, c.state.value)
    }

    @Test
    fun newQuestionCancelsPreviousAnswer() = runTest {
        val s = FakeSpeaker(5_000); val c = controller(s)
        c.submit("first"); runCurrent(); advanceTimeBy(1_000)
        c.submit("second"); runCurrent()
        assertEquals(1, s.cancelled)
        assertEquals("second", (c.state.value as AssistantState.Showing).query)
        advanceTimeBy(5_000 + 30_000 + 10); runCurrent()
        assertEquals(AssistantState.Idle, c.state.value)   // only the second answer's timer ran
    }

    @Test
    fun listeningShowsWhatHasBeenHeard() = runTest {
        val c = controller(FakeSpeaker(0))
        c.startListening(); c.transcript("who was")
        assertEquals(AssistantState.Listening("who was"), c.state.value)
    }

    @Test
    fun askFeedsTheQuestionWordByWordThenAnswers() = runTest {
        val c = controller(FakeSpeaker(1_000))
        c.ask("who was leonardo"); runCurrent()
        assertEquals(AssistantState.Listening(""), c.state.value)
        advanceTimeBy(700 + 220 + 10); runCurrent()
        assertEquals(AssistantState.Listening("who was"), c.state.value)
        advanceTimeBy(3_000); runCurrent()
        assertTrue(c.state.value is AssistantState.Showing)
    }

    @Test
    fun dismissWhileListeningStopsTheAsk() = runTest {
        val s = FakeSpeaker(1_000); val c = controller(s)
        c.ask("who was leonardo"); runCurrent(); advanceTimeBy(800); runCurrent()
        c.dismiss(); advanceTimeBy(60_000); runCurrent()
        assertEquals(AssistantState.Idle, c.state.value)
        assertTrue(s.spoken.isEmpty())   // no answer appears after it was closed
    }

    @Test
    fun secondAskReplacesTheFirst() = runTest {
        val s = FakeSpeaker(1_000); val c = controller(s)
        c.ask("first question here"); runCurrent(); advanceTimeBy(800)
        c.ask("second"); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals("second", (c.state.value as AssistantState.Showing).query)
        assertEquals(1, s.spoken.size)
    }
}

class AssistantListenTest {
    @Test fun spokenQuestionIsAnsweredAndPartialsShown() = kotlinx.coroutines.test.runTest {
        var whileHearing: AssistantState? = null
        val c = AssistantController(this, EstimatedSpeaker, AnswerProvider { Answer.Text("t", "b", "ok") }, showForMs = 60_000)
        c.listen { onPartial -> onPartial("who was"); whileHearing = c.state.value; "who was Leonardo" }
        advanceTimeBy(10_000)
        assertEquals(AssistantState.Listening("who was"), whileHearing)
        val s = c.state.value
        assertTrue("$s", s is AssistantState.Showing && s.query == "who was Leonardo")
        c.dismiss()
    }

    @Test fun nothingHeardGoesQuietlyBackToIdle() = kotlinx.coroutines.test.runTest {
        val c = AssistantController(this, EstimatedSpeaker, AnswerProvider { error("not asked") })
        c.listen { null }; advanceUntilIdle()
        assertEquals(AssistantState.Idle, c.state.value)
    }

    @Test fun handoffGoesToGoogleAndLeavesTheSectionAlone() = kotlinx.coroutines.test.runTest {
        val handed = mutableListOf<String>()
        val c = AssistantController(this, EstimatedSpeaker, AnswerProvider { Answer.Handoff(it) }, onHandoff = { handed += it })
        c.listen { "play Dune" }; advanceUntilIdle()
        assertEquals(listOf("play Dune"), handed); assertEquals(AssistantState.Idle, c.state.value)
    }
}
