package com.bittv.home.today

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class TodayKind { CLOCK, CALENDAR }

data class TodayItem(val kind: TodayKind, val text: String)

/** "Information about the day" for the active section: UK clock changes first, then the nearest holidays. */
object TodayInfo {
    private val HOLIDAYS = listOf(
        MonthDay.of(Month.OCTOBER, 31) to "Halloween",
        MonthDay.of(Month.NOVEMBER, 5) to "Bonfire Night",
        MonthDay.of(Month.DECEMBER, 25) to "Christmas",
        MonthDay.of(Month.DECEMBER, 31) to "New Year's Eve",
        MonthDay.of(Month.FEBRUARY, 14) to "Valentine's Day",
    )

    fun items(today: LocalDate, max: Int = 2): List<TodayItem> {
        val out = mutableListOf<TodayItem>()
        // UK clocks change on the last Sunday of March (forward) and October (back), at 01:00/02:00.
        for ((month, way) in listOf(Month.OCTOBER to "back", Month.MARCH to "forward")) {
            val change = today.withMonth(month.value).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))
            val n = ChronoUnit.DAYS.between(today, change)
            when (n) {
                0L -> out += TodayItem(TodayKind.CLOCK, "Clocks went $way 1 hr last night")
                1L -> out += TodayItem(TodayKind.CLOCK, "Clocks go $way 1 hr tonight")
                in 2L..7L -> out += TodayItem(TodayKind.CLOCK, "Clocks go $way in $n days")
            }
        }
        HOLIDAYS
            .map { (md, name) ->
                var d = md.atYear(today.year)
                if (d.isBefore(today)) d = md.atYear(today.year + 1)
                ChronoUnit.DAYS.between(today, d) to name
            }
            .sortedBy { it.first }
            .take((max - out.size).coerceAtLeast(0))
            .forEach { (n, name) ->
                out += TodayItem(
                    TodayKind.CALENDAR,
                    when (n) { 0L -> "It's $name today"; 1L -> "1 day until $name"; else -> "$n days until $name" },
                )
            }
        return out.take(max)
    }
}
