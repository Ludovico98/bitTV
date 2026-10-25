package com.bittv.home.net

import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class HttpTest {
    @Test
    fun userAgentIdentifiesBitTv() {
        // Wikimedia answers 403 to library user agents such as okhttp/x.y; requests must say who they are
        val server = ServerSocket(0)
        var request = ""
        val t = thread {
            server.accept().use { s ->
                request = s.getInputStream().bufferedReader().let { r -> generateSequence { r.readLine() }.takeWhile { it.isNotEmpty() }.joinToString("\n") }
                s.getOutputStream().write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
        }
        Http.client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()).execute().close()
        t.join(); server.close()
        val ua = request.lines().first { it.startsWith("User-Agent", ignoreCase = true) }
        assertTrue(ua, ua.contains("BitTV/"))
        assertTrue(ua, !ua.contains("@"))   // no personal details in requests
    }
}
