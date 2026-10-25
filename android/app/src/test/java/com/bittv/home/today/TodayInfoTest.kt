package com.bittv.home.today

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayInfoTest {
    private fun items(d: String) = TodayInfo.items(LocalDate.parse(d))

    @Test
    fun clocksWentBackOnTheDayThenHalloween() = assertEquals(
        listOf(TodayItem(TodayKind.CLOCK, "Clocks went back 1 hr last night"), TodayItem(TodayKind.CALENDAR, "6 days until Halloween")),
        items("2026-10-25"),
    )

    @Test
    fun clocksGoBackTonightTheDayBefore() = assertEquals("Clocks go back 1 hr tonight", items("2026-10-24").first().text)

    @Test
    fun holidayToday() = assertEquals(TodayItem(TodayKind.CALENDAR, "It's Christmas today"), items("2026-12-25").first())

    @Test
    fun clocksGoForwardCountdown() = assertEquals("Clocks go forward in 2 days", items("2026-03-27").first().text)

    @Test
    fun noClockChangeShowsTheTwoNearestHolidays() = assertEquals(
        listOf("4 days until Bonfire Night", "54 days until Christmas"),
        items("2026-11-01").map { it.text },
    )

    @Test
    fun oneDayIsSingular() = assertEquals("1 day until Halloween", items("2026-10-30").first().text)
}
