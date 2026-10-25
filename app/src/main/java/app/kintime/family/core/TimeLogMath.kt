package app.kintime.family.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

data class DaySlice(val day: LocalDate, val start: Long, val end: Long) {
    val millis get() = (end - start).coerceAtLeast(0)
}

object TimeLogMath {
    fun clock(millis: Long): String {
        val seconds = millis.coerceAtLeast(0) / 1_000
        return "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3_600, seconds / 60 % 60, seconds % 60)
    }
    fun splitByDay(start: Long, end: Long, zone: ZoneId): List<DaySlice> {
        if (end <= start) return emptyList()
        return buildList {
            var cursor = start
            while (cursor < end) {
                val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val stop = minOf(end, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                add(DaySlice(date, cursor, stop)); cursor = stop
            }
        }
    }
    fun overlap(start: Long, end: Long, rangeStart: Long, rangeEnd: Long): Long =
        (minOf(end, rangeEnd) - maxOf(start, rangeStart)).coerceAtLeast(0)

    fun validate(start: Long, end: Long, now: Long) {
        require(end > start) { "End time must be after start time." }
        require(start >= 0 && end <= now + 1_000) { "Log a time in the past, or start the timer for a new activity." }
        require(end - start <= 7 * 24 * 60 * 60 * 1_000L) { "Keep each entry within seven days." }
    }
}
