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

@Entity(tableName = "step_days")
data class StepDay(@PrimaryKey val day: String, val zoneId: String, val count: Long, val readAt: Long)

@Dao
abstract class StatsDao {
    @Query("SELECT * FROM step_days WHERE day = :day") abstract fun watchSteps(day: String): Flow<StepDay?>
    @Query("SELECT * FROM step_days ORDER BY day DESC LIMIT 7") abstract fun watchStepsWeek(): Flow<List<StepDay>>
    @Query("SELECT * FROM step_days WHERE day >= :since ORDER BY day") abstract suspend fun stepDays(since: String): List<StepDay>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putSteps(day: StepDay)
    @Query("DELETE FROM step_days WHERE day = :day") abstract suspend fun removeStepDay(day: String)
    @Query("DELETE FROM step_days") abstract suspend fun clearSteps()
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
    @Transaction open suspend fun clearAll() { clearDays(); clearApps(); clearVideos(); clearSteps() }
}

@Database(entities = [UsageDay::class, AppUsage::class, VideoSample::class, StepDay::class], version = 4, exportSchema = true)
abstract class KinDatabase : RoomDatabase() { abstract fun stats(): StatsDao }
