package app.kintime.family.sync

import android.content.Context
import androidx.work.*
import app.kintime.family.KinTimeApp
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as KinTimeApp).container
        return try { c.collector.collect(); c.family.sync(); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) {
            // Local collection also runs offline. Network failures retry without dropping local data.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("collect-and-sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}
