package app.kintime.family

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.*
import app.kintime.family.data.*
import app.kintime.family.ui.KinTimeRoot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import app.kintime.family.steps.HealthConnectStepSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[DashboardModel::class.java]
        val healthPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            model.container.steps.enable(); model.refresh()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) { model.refresh(); delay(30_000) }
            }
        }
        setContent { KinTimeRoot(model) { action ->
            val intent = when (action) {
                "steps-read" -> {
                    model.container.steps.enable()
                    runCatching { healthPermissions.launch(setOf(HealthConnectStepSource.READ_STEPS)) }
                        .onFailure { model.error.value = "Could not open Health Connect permissions. Update Health Connect in system settings." }
                    return@KinTimeRoot
                }
                "steps-background" -> {
                    runCatching { healthPermissions.launch(setOf(HealthConnectStepSource.READ_STEPS, HealthConnectStepSource.BACKGROUND)) }
                        .onFailure { model.error.value = "Background step access is unavailable on this device." }
                    return@KinTimeRoot
                }
                "health" -> Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                "health-privacy" -> Intent(this, HealthPrivacyActivity::class.java)
                "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
            }
            runCatching { startActivity(intent) }.onFailure { model.error.value = "Could not open settings on this device." }
        } }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardModel(application: Application) : AndroidViewModel(application) {
    val container = (application as KinTimeApp).container
    val preferences = container.preferences.state
    val family = container.family.state
    val selectedDay = MutableStateFlow(LocalDate.now().toString())
    val day = selectedDay.flatMapLatest { container.dao.watchDay(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val apps = selectedDay.flatMapLatest { container.dao.watchApps(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val videos = selectedDay.flatMapLatest { container.dao.watchVideos(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val week = container.dao.watchWeek().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stepsState = container.steps.state
    val stepsWeek = container.dao.watchStepsWeek().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasAccess = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var dateAtRefresh = LocalDate.now().toString()
    fun refresh() {
        if (refreshing.value) return
        hasAccess.value = container.collector.hasAccess()
        val today = LocalDate.now().toString()
        if (selectedDay.value == dateAtRefresh) selectedDay.value = today
        dateAtRefresh = today
        viewModelScope.launch {
            refreshing.value = true
            try { withContext(Dispatchers.IO) {
                container.collector.collect()
                withTimeout(20_000) { container.steps.refresh() }
                withTimeout(20_000) { container.family.refresh() }
            } }
            catch (_: TimeoutCancellationException) { error.value = "Cloud update is waiting for a connection. Local tracking continues." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Could not refresh. Saved statistics remain available; check connection and permissions." }
            finally { refreshing.value = false }
        }
    }
    fun label(pkg: String): String = when (pkg) {
        "com.instagram.android" -> "Instagram"
        "com.google.android.youtube" -> "YouTube"
        else -> runCatching { getApplication<Application>().packageManager.let { it.getApplicationLabel(it.getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }
    fun runAction(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }
}
