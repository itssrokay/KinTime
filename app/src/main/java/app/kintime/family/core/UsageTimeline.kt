package app.kintime.family.core

enum class EventKind { RESUME, PAUSE, STOP, SCREEN_ON, SCREEN_OFF, LOCK, UNLOCK, STARTUP, SHUTDOWN }
data class TimelineEvent(val at: Long, val kind: EventKind, val packageName: String = "", val instance: Int = 0)
data class UsageResult(val screenMillis: Long, val appMillis: Map<String, Long>, val hasEvents: Boolean)

/** Uses interval unions for device time; simultaneous resumed activities may overlap per app. */
object UsageTimeline {
    fun calculate(events: List<TimelineEvent>, start: Long, end: Long): UsageResult {
        require(end >= start)
        val active = mutableMapOf<String, String>()
        val totals = mutableMapOf<String, Long>()
        var interactive: Boolean? = null
        var locked = false
        var cursor = start
        var screen = 0L
        fun accrue(until: Long) {
            val clipped = until.coerceIn(start, end)
            val duration = (clipped - cursor).coerceAtLeast(0)
            if (interactive == true && !locked) {
                screen += duration
                active.values.toSet().forEach { totals[it] = (totals[it] ?: 0) + duration }
            }
            cursor = maxOf(cursor, clipped)
        }
        events.sortedBy { it.at }.takeWhile { it.at <= end }.forEach { event ->
            if (event.at >= start) accrue(event.at)
            val key = "${event.packageName}:${event.instance}"
            when (event.kind) {
                EventKind.RESUME -> {
                    if (interactive == null) interactive = true
                    active[key] = event.packageName
                }
                EventKind.PAUSE, EventKind.STOP -> active.remove(key)
                EventKind.SCREEN_ON -> interactive = true
                EventKind.SCREEN_OFF -> { interactive = false; active.clear() }
                EventKind.LOCK -> { locked = true; active.clear() }
                EventKind.UNLOCK -> { locked = false; if (interactive == null) interactive = true }
                EventKind.STARTUP, EventKind.SHUTDOWN -> { active.clear(); interactive = false; locked = false }
            }
        }
        accrue(end)
        return UsageResult(screen, totals.filterValues { it > 0 }, events.any { it.at in start..end })
    }
}
