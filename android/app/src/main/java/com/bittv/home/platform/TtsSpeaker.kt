package com.bittv.home.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.bittv.home.assistant.EstimatedSpeaker
import com.bittv.home.assistant.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Reads answers aloud with the TV's text-to-speech (Google's), in the voice chosen in Settings, and returns when it
 * has finished — the answer's 30 s countdown starts then. No engine or no voice: waits the estimated reading time.
 */
class TtsSpeaker(context: Context, private val voiceName: () -> String?) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    /** English voices on the TV, British first (the Settings picker). */
    suspend fun voices(): List<String> = if (!ready.await()) emptyList() else runCatching {
        tts.voices.orEmpty().filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "GB" }.thenBy { it.name }).map { it.name }
    }.getOrDefault(emptyList())

    override suspend fun speak(text: String) {
        if (!ready.await() || text.isBlank()) return EstimatedSpeaker.speak(text)
        val v = voiceName()?.let { n -> tts.voices?.firstOrNull { it.name == n } }
        if (v != null) tts.voice = v else tts.language = Locale.UK
        val id = "bittv-${System.nanoTime()}"
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tts.stop() }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
                override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
            })
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
        }
    }
}
