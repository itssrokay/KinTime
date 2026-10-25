package app.kintime.family

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kintime.family.data.KinDatabase
import app.kintime.family.data.MIGRATION_1_2
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KinDatabase::class.java)
    @Test fun addingTimeLogsPreservesExistingUsageAndEnforcesOneRunningTimer() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO usage_days VALUES ('2026-10-25', 'Asia/Kolkata', 123456, 1000, 1)")
            execSQL("INSERT INTO app_usage VALUES ('2026-10-25', 'com.example.reader', 9000)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).apply {
            query("SELECT screenMillis FROM usage_days").use { assertTrue(it.moveToFirst()); assertEquals(123456L, it.getLong(0)) }
            query("SELECT millis FROM app_usage").use { assertTrue(it.moveToFirst()); assertEquals(9000L, it.getLong(0)) }
            execSQL("INSERT INTO time_entries VALUES ('first', 'Reading', '', 1000, NULL, 'Asia/Kolkata', 1)")
            var rejected = false
            try { execSQL("INSERT INTO time_entries VALUES ('second', 'Walking', '', 2000, NULL, 'Asia/Kolkata', 1)") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
            assertTrue("A second active timer must be rejected", rejected)
            execSQL("UPDATE time_entries SET endedAt = 3000, activeSlot = NULL WHERE id = 'first'")
            execSQL("INSERT INTO time_entries VALUES ('second', 'Walking', '', 4000, NULL, 'Asia/Kolkata', 1)")
            query("SELECT COUNT(*) FROM time_entries").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            close()
        }
    }
}
