package com.theermite.hoso.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Brick B — hardening the foreground-service start path (S25 Ultra / One UI 7
 * black-box failure). [StreamStartError.classify] turns a raw start failure
 * into a stable category so the UI can show a real message and GlitchTip gets
 * a searchable tag instead of a silent crash.
 *
 * Android-only exception types (ForegroundServiceStartNotAllowedException,
 * MissingForegroundServiceTypeException) are simulated by local classes with
 * the same simpleName, since the classifier matches by class name to stay
 * runnable on the plain JVM (no Robolectric).
 */
class StreamStartErrorTest {

    // Local doubles reproducing the Android class names the classifier keys on.
    private class ForegroundServiceStartNotAllowedException(msg: String) :
        Exception(msg)
    private class MissingForegroundServiceTypeException(msg: String) :
        Exception(msg)

    @Test
    fun should_classify_fgs_not_allowed_when_start_not_allowed_thrown() {
        val t = ForegroundServiceStartNotAllowedException("startForeground denied")
        assertEquals(
            StreamStartError.FGS_NOT_ALLOWED,
            StreamStartError.classify(t)
        )
    }

    @Test
    fun should_classify_fgs_not_allowed_when_missing_service_type() {
        val t = MissingForegroundServiceTypeException("no fgs type declared")
        assertEquals(
            StreamStartError.FGS_NOT_ALLOWED,
            StreamStartError.classify(t)
        )
    }

    @Test
    fun should_classify_mic_unavailable_when_security_mentions_microphone() {
        val t = SecurityException(
            "Starting FGS with type microphone not allowed, mic in use"
        )
        assertEquals(
            StreamStartError.MIC_UNAVAILABLE,
            StreamStartError.classify(t)
        )
    }

    @Test
    fun should_classify_projection_invalid_when_security_mentions_projection() {
        val t = SecurityException(
            "Media projections require a foreground service"
        )
        assertEquals(
            StreamStartError.PROJECTION_INVALID,
            StreamStartError.classify(t)
        )
    }

    @Test
    fun should_classify_fgs_not_allowed_for_generic_security_exception() {
        val t = SecurityException("permission denied")
        assertEquals(
            StreamStartError.FGS_NOT_ALLOWED,
            StreamStartError.classify(t)
        )
    }

    @Test
    fun should_classify_mic_unavailable_when_message_mentions_audiorecord() {
        val t = IllegalStateException("AudioRecord start failed")
        assertEquals(
            StreamStartError.MIC_UNAVAILABLE,
            StreamStartError.classify(t)
        )
    }

    @Test
    fun should_classify_unknown_when_nothing_matches() {
        val t = RuntimeException("something else entirely")
        assertEquals(
            StreamStartError.UNKNOWN,
            StreamStartError.classify(t)
        )
    }
}
