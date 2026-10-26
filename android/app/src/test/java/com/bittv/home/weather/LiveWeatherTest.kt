package com.bittv.home.weather

import com.bittv.home.model.WeatherKind
import com.bittv.home.model.WeatherNow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWeatherTest {
    private val meteo = """{"current":{"temperature_2m":11.4,"weather_code":61}}"""

    @Test fun currentWeatherForTheClockLine() = runTest {
        assertEquals(WeatherNow(11, WeatherKind.RAIN), LiveWeather({ meteo }).current())
    }

    @Test fun offlineGivesNothingInsteadOfInventing() = runTest {
        assertEquals(null, LiveWeather({ null }).current())
        assertEquals(null, LiveWeather({ "<html>" }).current())
    }

    @Test fun weatherIsForRedhill() = runTest {
        val asked = mutableListOf<String>()
        LiveWeather({ asked += it; meteo }, lat = 51.24, lon = -0.17).current()
        assertTrue(asked.single(), "latitude=51.24&longitude=-0.17" in asked.single())
    }

    @Test fun weatherCodes() {
        assertEquals(WeatherKind.SUN, LiveWeather.kind(0)); assertEquals(WeatherKind.SUN, LiveWeather.kind(1))
        assertEquals(WeatherKind.CLOUD, LiveWeather.kind(3)); assertEquals(WeatherKind.CLOUD, LiveWeather.kind(45))
        assertEquals(WeatherKind.CLOUD, LiveWeather.kind(73)); assertEquals(WeatherKind.RAIN, LiveWeather.kind(61))
        assertEquals(WeatherKind.RAIN, LiveWeather.kind(95))
    }
}
