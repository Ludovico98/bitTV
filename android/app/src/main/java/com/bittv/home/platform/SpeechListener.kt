package com.bittv.home.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The speech engine BitTV listens with; faked in tests. */
interface SpeechEngine {
    interface Callback { fun partial(text: String); fun result(texts: List<String>); fun error(code: Int) }
    fun isAvailable(): Boolean
    fun start(callback: Callback)
    fun cancel()
}

/** One spoken phrase: the best guess, or null on silence, error or no speech service (the caller goes back to typing). */
class SpeechListener(private val engine: SpeechEngine) {
    suspend fun listen(onPartial: (String) -> Unit): String? {
        if (!engine.isAvailable()) return null
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { engine.cancel() }
            engine.start(object : SpeechEngine.Callback {
                override fun partial(text: String) = onPartial(text)
                override fun result(texts: List<String>) { if (cont.isActive) cont.resume(texts.firstOrNull()?.takeIf { it.isNotBlank() }) }
                override fun error(code: Int) { if (cont.isActive) cont.resume(null) }
            })
        }
    }
}

/** Android's SpeechRecognizer: on the TV that is Google's service, listening through the remote's microphone. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null
    override fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)
    override fun start(callback: SpeechEngine.Callback) {
        cancel()
        val r = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                callback.result(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()); cancel()
            }
            override fun onPartialResults(partial: Bundle) {
                partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(callback::partial)
            }
            override fun onError(error: Int) { callback.error(error); cancel() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3))
    }
    override fun cancel() { recognizer?.let { runCatching { it.cancel(); it.destroy() } }; recognizer = null }
}
