package com.bittv.home.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One HTTP client for the whole app (images, and later weather/news/Wikipedia). Requests identify themselves:
 * Wikimedia rejects generic library user agents. No personal details are sent.
 */
object Http {
    const val USER_AGENT = "BitTV/0.1 (personal Android TV home screen)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
            .build()
    }
}
