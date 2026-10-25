package com.bittv.home.platform

import com.bittv.home.assistant.GeneralReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeAnswersTest {
    @Test fun replyJsonIsRead() {
        assertEquals(GeneralReply("It was finished in 80 AD.", "Colosseum", false),
            ClaudeAnswers.parse("""{"say":"It was finished in 80 AD.","subject":"Colosseum","needs_google":false}"""))
        assertEquals(GeneralReply("Forty-two.", null, false), ClaudeAnswers.parse("""{"say":"Forty-two.","subject":null,"needs_google":false}"""))
        assertEquals(GeneralReply("", null, true), ClaudeAnswers.parse("""{"say":"","subject":null,"needs_google":true}"""))
    }

    @Test fun brokenReplyIsNull() {
        assertNull(ClaudeAnswers.parse("not json"))
        assertNull(ClaudeAnswers.parse("""{"subject":"x"}"""))
    }

    @Test fun theQuestionIsTheOnlyThingSent() {
        // privacy: the request carries the spoken question and today's date, nothing about the TV or the household
        val body = ClaudeAnswers.system(java.time.LocalDate.of(2026, 10, 25))
        assertEquals(false, body.contains("Redhill")); assertEquals(true, body.contains("2026-10-25"))
    }
}
