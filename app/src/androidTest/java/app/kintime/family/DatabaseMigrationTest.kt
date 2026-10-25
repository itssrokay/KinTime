package app.kintime.family

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kintime.family.data.KinDatabase
import app.kintime.family.data.MIGRATION_1_3
import app.kintime.family.data.MIGRATION_2_3
import app.kintime.family.data.MIGRATION_3_4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KinDatabase::class.java)
    @Test fun stepsUpgradePreservesScreenAndShortVideoHistory() {
        helper.createDatabase("migration-v3", 3).apply {
            execSQL("INSERT INTO usage_days VALUES ('2026-10-25', 'Asia/Kolkata', 123456, 1000, 1)")
            execSQL("INSERT INTO video_samples VALUES ('sample', '2026-10-25', 'com.google.android.youtube', 2000, 1, 1000, 1)")
            close()
        }
        helper.runMigrationsAndValidate("migration-v3", 4, true, MIGRATION_3_4).apply {
            query("SELECT screenMillis FROM usage_days").use { assertTrue(it.moveToFirst()); assertEquals(123456L, it.getLong(0)) }
            query("SELECT views FROM video_samples").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)) }
            execSQL("INSERT INTO step_days VALUES ('2026-10-25', 'Asia/Kolkata', 2171, 1000)")
            query("SELECT count FROM step_days").use { assertTrue(it.moveToFirst()); assertEquals(2171L, it.getLong(0)) }
            close()
        }
    }

    @Test fun originalVersionUpgradesWithoutLosingScreenTime() {
        helper.createDatabase("migration-v1", 1).apply {
            execSQL("INSERT INTO usage_days VALUES ('2026-10-25', 'Asia/Kolkata', 123456, 1000, 1)")
            execSQL("INSERT INTO app_usage VALUES ('2026-10-25', 'com.example.reader', 9000)")
            close()
        }
        helper.runMigrationsAndValidate("migration-v1", 3, true, MIGRATION_1_3).apply {
            query("SELECT screenMillis FROM usage_days").use { assertTrue(it.moveToFirst()); assertEquals(123456L, it.getLong(0)) }
            query("SELECT millis FROM app_usage").use { assertTrue(it.moveToFirst()); assertEquals(9000L, it.getLong(0)) }
            close()
        }
    }

    @Test fun previousVersionRemovesObsoleteTableAndPreservesUsageAndVideoSamples() {
        helper.createDatabase("migration-v2", 2).apply {
            execSQL("INSERT INTO usage_days VALUES ('2026-10-25', 'Asia/Kolkata', 123456, 1000, 1)")
            execSQL("INSERT INTO app_usage VALUES ('2026-10-25', 'com.example.reader', 9000)")
            execSQL("INSERT INTO video_samples VALUES ('sample', '2026-10-25', 'com.google.android.youtube', 2000, 1, 1000, 1)")
            execSQL("INSERT INTO time_entries VALUES ('obsolete', 'Example', '', 1000, NULL, 'Asia/Kolkata', 1)")
            close()
        }
        helper.runMigrationsAndValidate("migration-v2", 3, true, MIGRATION_2_3).apply {
            query("SELECT screenMillis FROM usage_days").use { assertTrue(it.moveToFirst()); assertEquals(123456L, it.getLong(0)) }
            query("SELECT millis FROM app_usage").use { assertTrue(it.moveToFirst()); assertEquals(9000L, it.getLong(0)) }
            query("SELECT views FROM video_samples").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)) }
            query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'time_entries'").use {
                assertTrue(it.moveToFirst()); assertEquals(0L, it.getLong(0))
            }
            close()
        }
    }
}
