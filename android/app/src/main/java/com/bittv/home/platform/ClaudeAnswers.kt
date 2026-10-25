package com.bittv.home.platform

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.MessageCreateParams
import com.bittv.home.assistant.GeneralReply
import com.bittv.home.assistant.GeneralSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/**
 * General knowledge for the active section: Claude answers the spoken question in one or two sentences, names the
 * subject for a picture, and says when only Google can answer. Only the question and today's date are sent.
 */
class ClaudeAnswers(private val apiKey: String) : GeneralSource {
    // built on the first question, not at start-up: the SDK (and its JSON library) stays out of BitTV's cold start
    private val client: AnthropicClient by lazy { AnthropicOkHttpClient.builder().apiKey(apiKey).build() }

    override suspend fun ask(question: String): GeneralReply? = withContext(Dispatchers.IO) {
        runCatching {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2048L)
                .system(system(LocalDate.now()))
                .addUserMessage(question)
                // a declined request is retried on the model the API picks for it, instead of just stopping
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                // quick answers, and always in the shape the TV reads
                .putAdditionalBodyProperty("output_config", JsonValue.from(mapOf(
                    "effort" to "low",
                    "format" to mapOf("type" to "json_schema", "schema" to SCHEMA),
                )))
                .build()
            val msg = client.beta().messages().create(params)
            if (msg.stopReason().map { it.toString() }.orElse("") == "refusal") return@runCatching null
            val text = msg.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("")
            parse(text)
        }.getOrNull()
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        private val SCHEMA = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "say" to mapOf("type" to "string"),
                "subject" to mapOf("type" to listOf("string", "null")),
                "needs_google" to mapOf("type" to "boolean"),
            ),
            "required" to listOf("say", "subject", "needs_google"),
            "additionalProperties" to false,
        )

        fun system(today: LocalDate) = """
            You answer questions spoken to a TV in a living room in the United Kingdom. Today is $today.
            say: the answer as it will be read aloud — one or two short sentences, plain words, no lists or markdown,
            in the language of the question.
            subject: the English Wikipedia article title of the person, place, thing or work the answer is about when a
            picture of it would help; otherwise null.
            needs_google: true only when answering needs the person's own accounts or devices, or live information you
            don't have (their calendar, reminders, smart home, live scores, today's news, current opening times); then
            leave say empty.
        """.trimIndent()

        fun parse(json: String): GeneralReply? = runCatching {
            val j = JSONObject(json)
            GeneralReply(j.getString("say"), if (j.isNull("subject")) null else j.getString("subject").ifBlank { null },
                j.getBoolean("needs_google"))
        }.getOrNull()
    }
}
