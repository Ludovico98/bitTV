package com.bittv.home.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AssistantState {
    data object Idle : AssistantState
    data class Listening(val partial: String) : AssistantState
    data class Thinking(val query: String) : AssistantState
    /** [counting] turns true once the answer has been read out; the 30 s countdown runs from then. */
    data class Showing(val query: String, val answer: Answer, val counting: Boolean) : AssistantState
}

/**
 * The answer area's state machine: Idle → Listening → Thinking → Showing (speaking) → Showing (counting) → Idle.
 * A new question or a dismissal cancels whatever was speaking or counting.
 */
class AssistantController(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val provider: AnswerProvider,
    private val showForMs: Long = 30_000,
) {
    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state
    private var job: Job? = null

    fun startListening() {
        job?.cancel(); job = null
        _state.value = AssistantState.Listening("")
    }

    fun transcript(partial: String) {
        if (_state.value is AssistantState.Listening) _state.value = AssistantState.Listening(partial)
    }

    fun submit(query: String) {
        job?.cancel()
        job = scope.launch { answer(query) }
    }

    private suspend fun answer(query: String) {
        _state.value = AssistantState.Thinking(query)
        val answer = provider.answer(query)
        _state.value = AssistantState.Showing(query, answer, counting = false)
        speaker.speak(answer.say)
        _state.value = AssistantState.Showing(query, answer, counting = true)
        delay(showForMs)
        _state.value = AssistantState.Idle
    }

    /**
     * Ask a question as if it were spoken: "Listening…", the words appear one by one, then the answer. The whole ask
     * belongs to one job, so a dismissal or a new question also stops the words still being "heard".
     */
    fun ask(query: String) {
        job?.cancel()
        job = scope.launch {
            _state.value = AssistantState.Listening("")
            val words = query.split(" ")
            delay(700)
            for (i in words.indices) { _state.value = AssistantState.Listening(words.take(i + 1).joinToString(" ")); delay(220) }
            delay(500)
            answer(query)
        }
    }

    fun dismiss() {
        job?.cancel(); job = null
        _state.value = AssistantState.Idle
    }
}
