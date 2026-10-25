package app.kintime.family.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) { /* Screen-time schema is unchanged. */ }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `time_entries`")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `step_days` (`day` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `count` INTEGER NOT NULL, `readAt` INTEGER NOT NULL, PRIMARY KEY(`day`))")
    }
}
