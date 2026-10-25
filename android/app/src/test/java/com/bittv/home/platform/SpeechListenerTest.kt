package com.bittv.home.platform

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechListenerTest {
    private class FakeEngine(val available: Boolean = true, private val script: (SpeechEngine.Callback) -> Unit) : SpeechEngine {
        var cancelled = false
        override fun isAvailable() = available
        override fun start(callback: SpeechEngine.Callback) = script(callback)
        override fun cancel() { cancelled = true }
    }

    @Test fun bestResultIsReturnedAndPartialsForwarded() = runTest {
        val partials = mutableListOf<String>()
        val said = SpeechListener(FakeEngine { cb -> cb.partial("breaking"); cb.result(listOf("breaking bad", "breaking bud")) }).listen { partials += it }
        assertEquals("breaking bad", said); assertEquals(listOf("breaking"), partials)
    }

    @Test fun errorReturnsNull() = runTest {
        assertNull(SpeechListener(FakeEngine { cb -> cb.error(7) }).listen {})
    }

    @Test fun noEngineReturnsNullWithoutStarting() = runTest {
        var started = false
        assertNull(SpeechListener(FakeEngine(available = false) { started = true }).listen {})
        assertTrue(!started)
    }

    @Test fun emptyResultIsNull() = runTest {
        assertNull(SpeechListener(FakeEngine { cb -> cb.result(emptyList()) }).listen {})
    }
}
