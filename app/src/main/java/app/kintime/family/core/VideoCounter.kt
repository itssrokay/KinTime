package app.kintime.family.core

data class VideoObservation(val packageName: String, val fingerprint: String)
data class VideoDelta(val packageName: String, val millis: Long, val views: Int)

/** Ephemeral identities only. A re-entry/revisit is a new appearance; a loop is not. */
class VideoCounter(private val minimumDwellMillis: Long = 2_000) {
    private var current: VideoObservation? = null
    private var started = 0L
    private var last = 0L
    private var counted = false

    fun observe(observation: VideoObservation?, elapsedMillis: Long): VideoDelta? {
        val previous = current
        val duration = if (previous != null) (elapsedMillis - last).coerceIn(0, 2_000) else 0L
        val qualifies = previous != null && !counted && elapsedMillis - started >= minimumDwellMillis
        if (qualifies) counted = true
        val result = previous?.let { VideoDelta(it.packageName, duration, if (qualifies) 1 else 0) }
        if (observation != previous) {
            current = observation
            started = elapsedMillis
            counted = false
        }
        last = elapsedMillis
        return result
    }
}
