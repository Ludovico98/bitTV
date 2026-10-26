package com.bittv.home.weather

import com.bittv.home.model.WeatherKind
import com.bittv.home.model.WeatherNow
import org.json.JSONObject

/** The weather right now (Open-Meteo) for the clock line on Home. [fetch] returns a body or null (offline); nothing is ever invented. */
class LiveWeather(
    private val fetch: suspend (String) -> String?,
    private val lat: Double = 51.51, private val lon: Double = -0.13,
) {
    suspend fun current(): WeatherNow? = fetch(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=Europe%2FLondon",
    )?.let { body ->
        runCatching { JSONObject(body).getJSONObject("current").let { c ->
            WeatherNow(Math.round(c.getDouble("temperature_2m")).toInt(), kind(c.getInt("weather_code"))) } }.getOrNull()
    }

    companion object {
        /** Open-Meteo / WMO weather codes. */
        fun kind(code: Int): WeatherKind = when {
            code <= 1 -> WeatherKind.SUN
            code <= 48 || code in 71..77 || code in 85..86 -> WeatherKind.CLOUD
            else -> WeatherKind.RAIN
        }
    }
}
