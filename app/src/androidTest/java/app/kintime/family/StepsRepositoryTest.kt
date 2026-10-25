package app.kintime.family

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kintime.family.data.*
import app.kintime.family.steps.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StepsRepositoryTest {
    private class Source(var granted: Boolean = true, var background: Boolean = false) : StepSource {
        var calls = 0
        var revoked = false
        val totals = mutableMapOf<String, Long?>()
        override suspend fun access() = StepAccess(true, granted, true, background, true)
        override suspend fun total(start: Instant, end: Instant): Long? {
            calls++
            return totals[start.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString()]
        }
        override suspend fun revoke() { revoked = true; granted = false }
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneId.of("Asia/Kolkata"))

    @Test fun requiresConsentAndPermissionBeforeReadingHealthData() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, KinDatabase::class.java).build()
        try {
            val prefs = Preferences(context, "steps-${UUID.randomUUID()}")
            val source = Source()
            val repo = StepsRepository(source, database.stats(), prefs, clock)
            repo.refresh(); assertEquals(0, source.calls)
            repo.enable(); source.granted = false
            repo.refresh(); assertEquals(0, source.calls); assertFalse(repo.canShare())
        } finally { database.close() }
    }
    @Test fun importsAggregateTotalAndKeepsMissingDataDistinctFromZero() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, KinDatabase::class.java).build()
        try {
            val source = Source().apply { totals["2026-10-25"] = 2171; totals["2026-10-24"] = 0 }
            val repo = StepsRepository(source, database.stats(), Preferences(context, "steps-${UUID.randomUUID()}"), clock)
            repo.enable(); repo.refresh()
            val rows = database.stats().stepDays("2026-10-01")
            assertEquals(7, source.calls); assertEquals(2, rows.size)
            assertEquals(2171L, rows.first { it.day == "2026-10-25" }.count)
            assertEquals(0L, rows.first { it.day == "2026-10-24" }.count)
            source.totals["2026-10-25"] = null
            repo.refresh()
            assertEquals(listOf("2026-10-24"), database.stats().stepDays("2026-10-01").map { it.day })
        } finally { database.close() }
    }
    @Test fun backgroundReadNeedsAdditionalPermissionAndDisconnectStopsReading() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, KinDatabase::class.java).build()
        try {
            val source = Source()
            val repo = StepsRepository(source, database.stats(), Preferences(context, "steps-${UUID.randomUUID()}"), clock)
            repo.enable(); repo.refresh(inBackground = true); assertEquals(0, source.calls)
            source.background = true
            repo.refresh(inBackground = true); assertEquals(7, source.calls)
            repo.disconnect(); assertTrue(source.revoked)
            repo.refresh(); assertEquals(7, source.calls); assertFalse(repo.canShare())
        } finally { database.close() }
    }
}
