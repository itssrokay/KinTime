package app.kintime.family

import android.app.Application
import androidx.room.Room
import app.kintime.family.data.*
import app.kintime.family.sync.*
import app.kintime.family.tracking.UsageCollector
import kotlinx.coroutines.*

class KinTimeApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
    }
}

class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val preferences = Preferences(app)
    val database = Room.databaseBuilder(app, KinDatabase::class.java, "kintime.db").addMigrations(MIGRATION_1_2).build()
    val dao = database.stats()
    val collector = UsageCollector(app, dao, preferences)
    val family = FamilyRepository(app, preferences, dao)
}
