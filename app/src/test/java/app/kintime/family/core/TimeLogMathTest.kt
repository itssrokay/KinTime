package app.kintime.family.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TimeLogMathTest {
    @Test fun `clock preserves hours over a day and clamps negative values`() {
        assertEquals("27:01:02", TimeLogMath.clock((27 * 3600 + 62) * 1000L))
        assertEquals("00:00:00", TimeLogMath.clock(-1000))
    }
    @Test fun `cross midnight entries split without losing duration`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = LocalDateTime.of(2026, 10, 25, 23, 45).atZone(zone).toInstant().toEpochMilli()
        val end = start + 30 * 60_000
        val slices = TimeLogMath.splitByDay(start, end, zone)
        assertEquals(2, slices.size)
        assertEquals(LocalDate.of(2026, 10, 25), slices[0].day)
        assertEquals(LocalDate.of(2026, 10, 26), slices[1].day)
        assertEquals(15 * 60_000L, slices[0].millis)
        assertEquals(30 * 60_000L, slices.sumOf { it.millis })
    }
    @Test fun `day splitting honours daylight saving changes`() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 3, 9).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(23 * 3_600_000L, TimeLogMath.splitByDay(start, end, zone).single().millis)
    }
    @Test fun `totals clip activity to the displayed range`() {
        assertEquals(50L, TimeLogMath.overlap(0, 100, 50, 150))
        assertEquals(0L, TimeLogMath.overlap(0, 100, 200, 300))
    }
    @Test(expected = IllegalArgumentException::class) fun `manual log rejects backwards times`() { TimeLogMath.validate(100, 50, 200) }
    @Test(expected = IllegalArgumentException::class) fun `manual log rejects future times`() { TimeLogMath.validate(0, 10_000, 100) }
}
