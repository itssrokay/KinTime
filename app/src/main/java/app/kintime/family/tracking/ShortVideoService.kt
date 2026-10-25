package app.kintime.family.tracking

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.kintime.family.KinTimeApp
import app.kintime.family.core.*
import app.kintime.family.data.VideoSample
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.*
import java.util.UUID

class ShortVideoService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val counter = VideoCounter()
    private val container get() = (application as KinTimeApp).container
    private var status = ""
    private val pulse = object : Runnable {
        override fun run() { sample(); handler.postDelayed(this, 1_000) }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { sample() }
    }

    override fun onServiceConnected() {
        registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, RECEIVER_NOT_EXPORTED)
        handler.post(pulse)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observe every foreground transition, but only inspect supported applications.
        handler.removeCallbacks(eventSample)
        handler.postDelayed(eventSample, 180)
    }
    private val eventSample = Runnable { sample() }
    private fun sample() {
        val prefs = container.preferences.state.value
        val enabled = prefs.videoConsent && getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val root = if (enabled) rootInActiveWindow else null
        val pkg = root?.packageName?.toString().orEmpty()
        val supported = pkg == "com.instagram.android" || pkg == "com.google.android.youtube"
        val observation = if (supported) detect(root!!, pkg) else null
        val message = when {
            !prefs.videoConsent -> "Optional tracking is off"
            !enabled -> "Waiting · screen is locked or off"
            observation != null -> "Recognising ${if (pkg == "com.instagram.android") "Reels" else "Shorts"} · estimates"
            supported -> "No recognised short-video viewer · interface may be unsupported"
            else -> "Ready · waiting for Reels or Shorts"
        }
        if (message != status || observation != null && System.currentTimeMillis() - prefs.detectorLastSeen > 30_000) {
            status = message
            container.preferences.detector(message, if (observation != null) System.currentTimeMillis() else null)
        }
        counter.observe(observation, SystemClock.elapsedRealtime())?.let { delta ->
            if (delta.millis > 0 || delta.views > 0) save(delta)
        }
    }

    private fun detect(root: AccessibilityNodeInfo, pkg: String): VideoObservation? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var viewer = false
        val identity = mutableListOf<String>()
        val viewerHints = if (pkg == "com.instagram.android") listOf("clips_viewer", "clips_video", "clips_recycler", "reel_viewer")
            else listOf("reel_watch", "reel_player", "shorts_player", "shorts_container", "reel_recycler")
        val identityHints = if (pkg == "com.instagram.android") listOf("caption", "clips_author", "username", "user_name")
            else listOf("reel_title", "channel_name", "reel_channel", "shorts_title")
        while (queue.isNotEmpty() && visited++ < 350) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            val id = node.viewIdResourceName.orEmpty().substringAfter(":id/")
            if (viewerHints.any { id.contains(it) }) viewer = true
            if (identityHints.any { id.contains(it) }) {
                val value = node.text?.toString()?.trim().orEmpty().ifBlank { node.contentDescription?.toString()?.trim().orEmpty() }
                if (value.length >= 3 && !value.all { it.isDigit() }) identity.add(value.take(300))
            }
            for (i in 0 until node.childCount.coerceAtMost(40)) node.getChild(i)?.let { queue.add(it) }
        }
        // A viewer without a usable identity cannot yield defensible per-video counts.
        if (!viewer || identity.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.distinct().sorted().joinToString("|").toByteArray())
        return VideoObservation(pkg, digest.joinToString("") { "%02x".format(it) })
    }

    private fun save(delta: VideoDelta) {
        val end = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val start = end - delta.millis
        container.scope.launch {
            var cursor = start
            while (cursor < end) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val stop = minOf(end, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                container.dao.addVideo(VideoSample(UUID.randomUUID().toString(), day.toString(), delta.packageName,
                    stop - cursor, if (stop == end) delta.views else 0, stop))
                cursor = stop
            }
            if (delta.millis == 0L && delta.views > 0) container.dao.addVideo(VideoSample(UUID.randomUUID().toString(),
                Instant.ofEpochMilli(end).atZone(zone).toLocalDate().toString(), delta.packageName, 0, delta.views, end))
        }
    }
    override fun onInterrupt() { counter.observe(null, SystemClock.elapsedRealtime())?.let { save(it) } }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        counter.observe(null, SystemClock.elapsedRealtime())?.let { save(it) }
        container.preferences.detector("Service disconnected · open tracking settings")
        super.onDestroy()
    }
}
