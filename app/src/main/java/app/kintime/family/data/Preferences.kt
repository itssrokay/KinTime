package app.kintime.family.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PreferencesState(
    val displayName: String = "You", val sharing: Boolean = false, val shareApps: Boolean = false,
    val videoConsent: Boolean = false, val familyId: String = "", val lastSync: Long = 0,
    val collectorMessage: String = "Enable Usage Access to start", val detectorMessage: String = "Optional tracking is off",
    val detectorLastSeen: Long = 0, val goalMinutes: Int = 180,
)

class Preferences(context: Context, namespace: String = "kintime") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    private fun read() = PreferencesState(
        prefs.getString("name", "You") ?: "You", prefs.getBoolean("sharing", false), prefs.getBoolean("shareApps", false),
        prefs.getBoolean("videoConsent", false), prefs.getString("familyId", "") ?: "", prefs.getLong("lastSync", 0),
        prefs.getString("collector", "Enable Usage Access to start") ?: "", prefs.getString("detector", "Optional tracking is off") ?: "",
        prefs.getLong("detectorLastSeen", 0), prefs.getInt("goal", 180),
    )
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun name(value: String) { prefs.edit().putString("name", value.trim().take(40).ifBlank { "You" }).apply(); mutable.value = read() }
    fun sharing(value: Boolean) { prefs.edit().putBoolean("sharing", value).apply(); mutable.value = read() }
    fun shareApps(value: Boolean) { prefs.edit().putBoolean("shareApps", value).apply(); mutable.value = read() }
    fun videoConsent(value: Boolean) { prefs.edit().putBoolean("videoConsent", value).apply(); mutable.value = read() }
    fun family(id: String) { prefs.edit().putString("familyId", id).putLong("lastSync", 0).putBoolean("sharing", false).apply(); mutable.value = read() }
    fun synced(at: Long) { prefs.edit().putLong("lastSync", at).apply(); mutable.value = read() }
    fun collector(message: String) { prefs.edit().putString("collector", message).apply(); mutable.value = read() }
    fun detector(message: String, seen: Long? = null) {
        prefs.edit().putString("detector", message).apply { if (seen != null) putLong("detectorLastSeen", seen) }.apply()
        mutable.value = read()
    }
    fun goal(minutes: Int) { prefs.edit().putInt("goal", minutes.coerceIn(15, 1440)).apply() }
}
