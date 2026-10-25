package app.kintime.family.core

import org.junit.Assert.*
import org.junit.Test

class VideoCounterTest {
    private val a = VideoObservation("youtube", "a")
    private val b = VideoObservation("youtube", "b")
    @Test fun `repeated events and loops count one appearance`() {
        val counter = VideoCounter()
        assertNull(counter.observe(a, 0))
        assertEquals(0, counter.observe(a, 1_000)!!.views)
        assertEquals(1, counter.observe(a, 2_000)!!.views)
        assertEquals(0, counter.observe(a, 3_000)!!.views)
        assertEquals(0, counter.observe(a, 4_000)!!.views)
    }
    @Test fun `rapid skips do not qualify as views`() {
        val counter = VideoCounter()
        counter.observe(a, 0)
        assertEquals(0, counter.observe(b, 500)!!.views)
        assertEquals(0, counter.observe(null, 1_000)!!.views)
        assertNull(counter.observe(null, 10_000))
    }
    @Test fun `leaving screen stops accumulating time and revisits can count`() {
        val counter = VideoCounter()
        counter.observe(a, 0)
        assertEquals(1, counter.observe(null, 2_000)!!.views)
        assertNull(counter.observe(null, 50_000))
        assertNull(counter.observe(a, 60_000))
        assertEquals(1, counter.observe(a, 62_000)!!.views)
    }
    @Test fun `delayed heartbeat cannot invent unbounded duration`() {
        val counter = VideoCounter()
        counter.observe(a, 0)
        assertEquals(2_000L, counter.observe(a, 600_000)!!.millis)
    }
}
