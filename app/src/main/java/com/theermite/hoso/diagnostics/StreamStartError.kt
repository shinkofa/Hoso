package com.theermite.hoso.diagnostics

/**
 * Stable classification of a stream / foreground-service start failure.
 *
 * Brick B — Android 14/15 tightened foreground-service rules; on strict OEMs
 * (observed: Samsung One UI 7 on the S25 Ultra) [android.app.Service.startForeground]
 * can throw where it succeeds elsewhere, and an unguarded throw kills the app
 * with no message. Classifying the failure lets us show the user a real reason
 * and attach a searchable tag to the GlitchTip report.
 *
 * Matching is done by exception class simpleName + message so this stays a pure
 * JVM unit (no Android classpath, no Robolectric): the Android-only types
 * (ForegroundServiceStartNotAllowedException, MissingForegroundServiceTypeException)
 * are referenced by name, not by import.
 */
enum class StreamStartError {
    /** Microphone busy or blocked by AppOps — FGS "microphone" type refused. */
    MIC_UNAVAILABLE,

    /** Foreground service start refused (not-allowed / missing type). */
    FGS_NOT_ALLOWED,

    /** MediaProjection consent token missing or expired. */
    PROJECTION_INVALID,

    /** Anything we couldn't attribute to a known cause. */
    UNKNOWN;

    companion object {
        /** The GlitchTip tag key under which the category is reported. */
        const val TAG_KEY = "stream_start_error"

        fun classify(t: Throwable): StreamStartError {
            val name = t::class.java.simpleName
            val msg = (t.message ?: "").lowercase()
            return when {
                name == "ForegroundServiceStartNotAllowedException" -> FGS_NOT_ALLOWED
                name == "MissingForegroundServiceTypeException" -> FGS_NOT_ALLOWED
                t is SecurityException && "microphone" in msg -> MIC_UNAVAILABLE
                t is SecurityException && "projection" in msg -> PROJECTION_INVALID
                t is SecurityException -> FGS_NOT_ALLOWED
                "microphone" in msg || "audiorecord" in msg -> MIC_UNAVAILABLE
                "projection" in msg -> PROJECTION_INVALID
                else -> UNKNOWN
            }
        }
    }
}
