package com.bittv.home.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
}
