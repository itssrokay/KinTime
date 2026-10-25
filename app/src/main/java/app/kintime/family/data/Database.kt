package app.kintime.family.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "usage_days")
data class UsageDay(@PrimaryKey val day: String, val zoneId: String, val screenMillis: Long,
                    val collectedAt: Long, val hasEvents: Boolean)
@Entity(tableName = "app_usage", primaryKeys = ["day", "packageName"])
data class AppUsage(val day: String, val packageName: String, val millis: Long)
@Entity(tableName = "video_samples", indices = [Index("day")])
data class VideoSample(@PrimaryKey val id: String, val day: String, val packageName: String,
                       val millis: Long, val views: Int, val recordedAt: Long, val detectorVersion: Int = 1)
data class VideoTotal(val packageName: String, val millis: Long, val views: Int)

@Entity(tableName = "time_entries", indices = [Index(value = ["activeSlot"], unique = true), Index("startedAt")])
data class TimeEntry(@PrimaryKey val id: String, val description: String, val project: String,
                     val startedAt: Long, val endedAt: Long?, val zoneId: String, val activeSlot: Int? = null)

@Dao
abstract class StatsDao {
    @Query("SELECT * FROM time_entries ORDER BY startedAt DESC") abstract fun watchTimeEntries(): Flow<List<TimeEntry>>
    @Query("SELECT * FROM time_entries WHERE activeSlot = 1 LIMIT 1") abstract suspend fun runningEntry(): TimeEntry?
    @Insert abstract suspend fun insertTimeEntry(entry: TimeEntry)
    @Query("UPDATE time_entries SET endedAt = :end, activeSlot = NULL WHERE id = :id AND activeSlot = 1")
    abstract suspend fun finishTimeEntry(id: String, end: Long)
    @Query("UPDATE time_entries SET description = :description, project = :project, startedAt = :start, endedAt = :end WHERE id = :id AND activeSlot IS NULL")
    abstract suspend fun editTimeEntry(id: String, description: String, project: String, start: Long, end: Long)
    @Query("DELETE FROM time_entries WHERE id = :id AND activeSlot IS NULL") abstract suspend fun deleteTimeEntry(id: String)
    @Transaction open suspend fun startTimeEntry(entry: TimeEntry) {
        check(runningEntry() == null) { "Stop your running timer before starting another." }
        insertTimeEntry(entry)
    }
    @Transaction open suspend fun stopTimeEntry(now: Long) {
        runningEntry()?.let { finishTimeEntry(it.id, maxOf(now, it.startedAt + 1)) }
    }
    @Query("SELECT * FROM usage_days WHERE day = :day") abstract fun watchDay(day: String): Flow<UsageDay?>
    @Query("SELECT * FROM usage_days ORDER BY day DESC LIMIT 7") abstract fun watchWeek(): Flow<List<UsageDay>>
    @Query("SELECT * FROM app_usage WHERE day = :day ORDER BY millis DESC") abstract fun watchApps(day: String): Flow<List<AppUsage>>
    @Query("SELECT packageName, SUM(millis) AS millis, SUM(views) AS views FROM video_samples WHERE day = :day GROUP BY packageName")
    abstract fun watchVideos(day: String): Flow<List<VideoTotal>>
    @Query("SELECT * FROM usage_days WHERE day >= :since ORDER BY day") abstract suspend fun days(since: String): List<UsageDay>
    @Query("SELECT * FROM app_usage WHERE day = :day ORDER BY millis DESC") abstract suspend fun apps(day: String): List<AppUsage>
    @Query("SELECT packageName, SUM(millis) AS millis, SUM(views) AS views FROM video_samples WHERE day = :day GROUP BY packageName")
    abstract suspend fun videos(day: String): List<VideoTotal>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putDay(day: UsageDay)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putApps(apps: List<AppUsage>)
    @Insert abstract suspend fun addVideo(sample: VideoSample)
    @Query("DELETE FROM app_usage WHERE day = :day") abstract suspend fun removeApps(day: String)
    @Transaction open suspend fun replace(day: UsageDay, apps: List<AppUsage>) {
        putDay(day); removeApps(day.day); putApps(apps)
    }
    @Query("DELETE FROM usage_days") abstract suspend fun clearDays()
    @Query("DELETE FROM app_usage") abstract suspend fun clearApps()
    @Query("DELETE FROM video_samples") abstract suspend fun clearVideos()
    @Query("DELETE FROM time_entries") abstract suspend fun clearTimeEntries()
    @Transaction open suspend fun clearAll() { clearDays(); clearApps(); clearVideos(); clearTimeEntries() }
}

@Database(entities = [UsageDay::class, AppUsage::class, VideoSample::class, TimeEntry::class], version = 2, exportSchema = true)
abstract class KinDatabase : RoomDatabase() { abstract fun stats(): StatsDao }
