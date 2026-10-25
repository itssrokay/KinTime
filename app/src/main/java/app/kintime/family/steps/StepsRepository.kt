package app.kintime.family.steps

import app.kintime.family.data.Preferences
import app.kintime.family.data.StatsDao
import app.kintime.family.data.StepDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

data class StepsState(val access: StepAccess = StepAccess(false), val loading: Boolean = false,
                      val message: String = "Connect Health Connect to see your steps", val error: String? = null)

class StepsRepository(private val source: StepSource, private val dao: StatsDao,
                      private val preferences: Preferences, private val clock: Clock = Clock.systemDefaultZone()) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(StepsState())
    val state = mutable.asStateFlow()
    suspend fun canShare(): Boolean = preferences.state.value.stepsEnabled && source.access().readAllowed
    fun enable() { preferences.stepsEnabled(true) }
    suspend fun refresh(inBackground: Boolean = false) = mutex.withLock {
        try {
            val access = source.access()
            mutable.value = StepsState(access = access)
            when {
                !access.available -> { mutable.value = StepsState(access, message = "Health Connect needs a system update or is unavailable on this profile"); return@withLock }
                !preferences.state.value.stepsEnabled -> { mutable.value = StepsState(access, message = "Connect Health Connect to see your steps"); return@withLock }
                !access.readAllowed -> { mutable.value = StepsState(access, message = "Steps permission is off · saved readings remain on this phone"); return@withLock }
                inBackground && !access.backgroundAllowed -> { mutable.value = StepsState(access, message = "Refreshes when you open KinTime · background permission is off"); return@withLock }
            }
            mutable.value = StepsState(access, loading = true, message = "Refreshing step totals…")
            val now = clock.instant()
            val zone = clock.zone
            val today = now.atZone(zone).toLocalDate()
            for (offset in 6L downTo 0L) {
                val day = today.minusDays(offset)
                val start = day.atStartOfDay(zone).toInstant()
                val end = minOf(now, day.plusDays(1).atStartOfDay(zone).toInstant())
                if (end <= start) continue
                val count = source.total(start, end)
                // Null means no records; it must not become a fabricated zero or retain a deleted total.
                if (count == null) dao.removeStepDay(day.toString())
                else {
                    require(count >= 0) { "Health Connect returned an invalid step count." }
                    dao.putSteps(StepDay(day.toString(), zone.id, count, now.toEpochMilli()))
                }
            }
            mutable.value = StepsState(access, message = if (access.backgroundAllowed) "Connected · periodic background refresh enabled" else "Connected · refreshes when you open KinTime")
        } catch (e: CancellationException) { mutable.value = mutable.value.copy(loading = false); throw e }
        catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, message = "Could not refresh · last successful readings may be stale", error = e.localizedMessage) }
    }
    suspend fun disconnect() = mutex.withLock {
        preferences.stepsEnabled(false)
        try {
            source.revoke()
            mutable.value = StepsState(access = mutable.value.access.copy(readAllowed = false, backgroundAllowed = false), message = "Disconnected · saved readings remain on this phone")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, message = "Stopped locally · remove access in Health Connect settings", error = e.localizedMessage) }
    }
}
