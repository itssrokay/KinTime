package app.kintime.family.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import app.kintime.family.core.*
import app.kintime.family.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class UsageCollector(private val context: Context, private val dao: StatsDao, private val preferences: Preferences) {
    private val mutex = Mutex()
    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    suspend fun collect() = mutex.withLock {
        if (!hasAccess()) { preferences.collector("Usage Access is off · saved history remains on this phone"); return@withLock }
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return@withLock
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val first = today.minusDays(2)
        val queryStart = first.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val source = context.getSystemService(UsageStatsManager::class.java).queryEvents(queryStart, now)
            ?: return@withLock
        val events = buildList {
            val e = UsageEvents.Event()
            while (source.hasNextEvent()) {
                source.getNextEvent(e)
                val kind = when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> EventKind.RESUME
                    UsageEvents.Event.ACTIVITY_PAUSED -> EventKind.PAUSE
                    UsageEvents.Event.ACTIVITY_STOPPED -> EventKind.STOP
                    UsageEvents.Event.SCREEN_INTERACTIVE -> EventKind.SCREEN_ON
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> EventKind.SCREEN_OFF
                    UsageEvents.Event.KEYGUARD_SHOWN -> EventKind.LOCK
                    UsageEvents.Event.KEYGUARD_HIDDEN -> EventKind.UNLOCK
                    UsageEvents.Event.DEVICE_STARTUP -> EventKind.STARTUP
                    UsageEvents.Event.DEVICE_SHUTDOWN -> EventKind.SHUTDOWN
                    else -> null
                }
                if (kind != null) add(TimelineEvent(e.timeStamp, kind, e.packageName.orEmpty(), e.className.orEmpty().hashCode()))
            }
        }
        if (events.isEmpty()) { preferences.collector("No recent events returned · history may be incomplete"); return@withLock }
        for (offset in 0L..2L) {
            val date = first.plusDays(offset)
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            // Do not replace older records if Android has already pruned this day's events.
            if (events.first().at > start && date != today) continue
            val result = UsageTimeline.calculate(events, start, end)
            if (result.hasEvents || result.screenMillis > 0) {
                dao.replace(UsageDay(date.toString(), zone.id, result.screenMillis, now, result.hasEvents),
                    result.appMillis.map { AppUsage(date.toString(), it.key, it.value) })
            }
        }
        preferences.collector("Collecting · screen on and unlocked")
    }
}
