package app.kintime.family.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `time_entries` (`id` TEXT NOT NULL, `description` TEXT NOT NULL, `project` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `zoneId` TEXT NOT NULL, `activeSlot` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_time_entries_activeSlot` ON `time_entries` (`activeSlot`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_entries_startedAt` ON `time_entries` (`startedAt`)")
    }
}
