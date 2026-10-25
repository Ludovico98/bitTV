package com.bittv.home.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/** GET through the shared client: the body on HTTP 2xx, null on any failure (offline, timeout, error status). */
suspend fun httpGet(url: String, auth: String? = null): String? = httpBytes(url, auth)?.decodeToString()

suspend fun httpBytes(url: String, auth: String? = null): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder().url(url).apply { if (auth != null) header("Authorization", auth) }.build()
        Http.client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
    } catch (e: IOException) { null } catch (e: IllegalArgumentException) { null }
}
