package com.theermite.hoso.manifest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B-001 — OverlayService MUST NOT declare foregroundServiceType="specialUse".
 *
 * Play Store rejects "specialUse" for a floating overlay. The overlay
 * window uses SYSTEM_ALERT_WINDOW and does not need its own FGS — the
 * process is kept alive by ScreenRecordService's mediaProjection FGS.
 *
 * These tests read the raw manifest XML so they run on plain JVM (no
 * Android instrumentation needed).
 */
class OverlayServiceManifestTest {

    private val manifestFile = File("src/main/AndroidManifest.xml")

    private fun manifestText(): String {
        assertTrue(
            "AndroidManifest.xml must exist at ${manifestFile.absolutePath}",
            manifestFile.exists()
        )
        return manifestFile.readText()
    }

    /**
     * Extract the <service> block for a given service class name.
     * Handles both self-closing (`/>`) and open+close (`>...</service>`) tags.
     * Returns null if the service is not declared.
     */
    private fun serviceBlock(manifest: String, serviceName: String): String? {
        // Try self-closing first: <service ... OverlayService ... />
        val selfClosing = Regex(
            """<service[^>]*\.${Regex.escape(serviceName)}[^>]*/>""",
            RegexOption.DOT_MATCHES_ALL
        )
        selfClosing.find(manifest)?.let { return it.value }

        // Then try open+close: <service ... OverlayService ...>...</service>
        val openClose = Regex(
            """<service[^>]*\.${Regex.escape(serviceName)}[^>]*>.*?</service>""",
            RegexOption.DOT_MATCHES_ALL
        )
        return openClose.find(manifest)?.value
    }

    @Test
    fun should_not_declare_specialUse_for_OverlayService() {
        val manifest = manifestText()
        val block = serviceBlock(manifest, "OverlayService")

        // OverlayService must still be declared (it's a valid service)
        assertTrue(
            "OverlayService must be declared in the manifest",
            block != null
        )

        // It must NOT have foregroundServiceType="specialUse"
        assertFalse(
            "OverlayService must not declare foregroundServiceType=\"specialUse\"",
            block!!.contains("specialUse")
        )
    }

    @Test
    fun should_not_have_special_use_property_for_OverlayService() {
        val manifest = manifestText()
        val block = serviceBlock(manifest, "OverlayService")!!

        assertFalse(
            "OverlayService must not have PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
            block.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE")
        )
    }

    @Test
    fun should_not_declare_specialUse_for_ChatBubbleService() {
        val manifest = manifestText()
        val block = serviceBlock(manifest, "ChatBubbleService")

        // P-A2 — chat binds to the stream FGS instead of being its own
        // specialUse FGS, so Google Play has no specialUse to review.
        assertTrue(
            "ChatBubbleService must be declared in the manifest",
            block != null
        )
        assertFalse(
            "ChatBubbleService must not declare foregroundServiceType=\"specialUse\"",
            block!!.contains("specialUse")
        )
    }

    @Test
    fun should_not_request_special_use_permission_anywhere() {
        val manifest = manifestText()

        // No service uses specialUse anymore → the permission must be gone.
        // Keeping it would draw needless Play Store review scrutiny.
        assertFalse(
            "Manifest must not request FOREGROUND_SERVICE_SPECIAL_USE",
            manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE")
        )
    }

    @Test
    fun should_still_declare_mediaProjection_for_ScreenRecordService() {
        val manifest = manifestText()
        val block = serviceBlock(manifest, "ScreenRecordService")

        assertTrue(
            "ScreenRecordService must declare mediaProjection",
            block != null && block.contains("mediaProjection")
        )
    }
}
