package app.kintime.family.core

import org.junit.Assert.*
import org.junit.Test

class UsageTimelineTest {
    private fun event(at: Long, kind: EventKind, pkg: String = "", id: Int = 0) = TimelineEvent(at, kind, pkg, id)
    @Test fun `screen off and lock close sessions`() {
        val r = UsageTimeline.calculate(listOf(event(0, EventKind.SCREEN_ON), event(10, EventKind.RESUME, "video"),
            event(100, EventKind.LOCK), event(150, EventKind.SCREEN_OFF)), 0, 200)
        assertEquals(100L, r.screenMillis); assertEquals(90L, r.appMillis["video"])
    }
    @Test fun `split screen overlaps apps but never doubles device time`() {
        val r = UsageTimeline.calculate(listOf(event(0, EventKind.RESUME, "a"), event(20, EventKind.RESUME, "b"),
            event(80, EventKind.PAUSE, "a"), event(100, EventKind.SCREEN_OFF)), 0, 120)
        assertEquals(100L, r.screenMillis); assertEquals(80L, r.appMillis["a"]); assertEquals(80L, r.appMillis["b"])
    }
    @Test fun `same package multiple activities counted as union`() {
        val r = UsageTimeline.calculate(listOf(event(0, EventKind.RESUME, "a", 1), event(20, EventKind.RESUME, "a", 2),
            event(40, EventKind.PAUSE, "a", 1), event(80, EventKind.PAUSE, "a", 2)), 0, 100)
        assertEquals(80L, r.appMillis["a"])
    }
    @Test fun `carry session across midnight without double counting`() {
        val events = listOf(event(0, EventKind.RESUME, "a"), event(150, EventKind.SCREEN_OFF))
        assertEquals(100L, UsageTimeline.calculate(events, 0, 100).screenMillis)
        assertEquals(50L, UsageTimeline.calculate(events, 100, 200).screenMillis)
    }
    @Test fun `shutdown does not accumulate time while phone is off`() {
        val r = UsageTimeline.calculate(listOf(event(0, EventKind.RESUME, "a"), event(50, EventKind.SHUTDOWN),
            event(200, EventKind.STARTUP), event(250, EventKind.SCREEN_ON), event(260, EventKind.RESUME, "b")), 0, 300)
        assertEquals(100L, r.screenMillis); assertEquals(50L, r.appMillis["a"]); assertEquals(40L, r.appMillis["b"])
    }
    @Test fun `no events means unknown rather than invented usage`() {
        val r = UsageTimeline.calculate(emptyList(), 0, 100)
        assertFalse(r.hasEvents); assertEquals(0L, r.screenMillis); assertTrue(r.appMillis.isEmpty())
    }
}
