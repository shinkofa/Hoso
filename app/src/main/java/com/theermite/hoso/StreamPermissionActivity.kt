package com.theermite.hoso

import android.Manifest
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.diagnostics.StreamStartError
import com.theermite.hoso.services.ScreenRecordService
import com.theermite.hoso.streaming.StreamLauncher
import io.sentry.Sentry
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.core.streamers.utils.MediaProjectionUtils
import io.github.thibaultbee.streampack.services.MediaProjectionService
import kotlinx.coroutines.launch

/**
 * Transparent, no-history activity that exists solely to acquire the two
 * permissions a stream needs (microphone + screen capture) and kick off
 * [ScreenRecordService]. Used as the start-stream entry point from the
 * floating overlay, since MediaProjection consent and audio permission
 * MUST be requested from an Activity context — services cannot do it.
 *
 * Why not just bring [MainActivity] forward:
 *   - MainActivity carries the whole settings UI (preset spinner, audio
 *     source pickers, RTMP URL editor). Showing it in front of a running
 *     game just to tap "start" is jarring and steals input focus.
 *   - This activity has zero UI: theme = transparent, no animation. The
 *     user sees the Android consent dialogs, taps allow, and we're gone.
 *
 * Lifecycle:
 *   1. onCreate validates that a stream key exists.
 *   2. Request RECORD_AUDIO. On grant → request MediaProjection.
 *   3. On MediaProjection grant → bind [ScreenRecordService] via
 *      StreamPack's helper, which hands us a bound streamer.
 *   4. Configure + start the stream via [StreamLauncher].
 *   5. Unbind and finish. The foreground service keeps the stream alive
 *      on its own, so dropping the bind here doesn't kill anything.
 */
class StreamPermissionActivity : ComponentActivity() {

    private lateinit var config: StreamConfig
    private var connection: ServiceConnection? = null

    private val requestAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestScreenCapture.launch(
                MediaProjectionUtils.createScreenCaptureIntent(this)
            )
        } else {
            toast(R.string.error_mic_denied)
            finish()
        }
    }

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            toast(R.string.error_capture_denied)
            finish()
            return@registerForActivityResult
        }

        // Ensure the service is started via startForegroundService(),
        // not just created via BIND_AUTO_CREATE. Without this, some
        // OEMs (ColorOS/OPPO) don't grant full FGS priority and kill
        // the AudioRecord after ~60 s once this Activity finishes.
        startForegroundService(
            Intent(this, ScreenRecordService::class.java)
        )

        connection = MediaProjectionService.bindService(
            context = this,
            serviceClass = ScreenRecordService::class.java,
            resultCode = result.resultCode,
            resultData = result.data!!,
            onServiceCreated = { boundStreamer ->
                lifecycleScope.launch {
                    try {
                        StreamLauncher.configureAndStart(
                            context = applicationContext,
                            streamer = boundStreamer as ISingleStreamer,
                            config = config,
                        )
                    } catch (e: Exception) {
                        // Brick B — classify + report to GlitchTip with a
                        // searchable tag, and show a reason the user can act on
                        // instead of a raw exception message.
                        val category = StreamStartError.classify(e)
                        Sentry.withScope { scope ->
                            scope.setTag(StreamStartError.TAG_KEY, category.name)
                            Sentry.captureException(e)
                        }
                        val msgRes = when (category) {
                            StreamStartError.MIC_UNAVAILABLE ->
                                R.string.error_start_mic_unavailable
                            StreamStartError.FGS_NOT_ALLOWED ->
                                R.string.error_start_fgs_not_allowed
                            StreamStartError.PROJECTION_INVALID ->
                                R.string.error_start_projection_invalid
                            StreamStartError.UNKNOWN ->
                                R.string.error_start_unknown
                        }
                        runOnUiThread {
                            Toast.makeText(
                                this@StreamPermissionActivity,
                                getString(msgRes),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        runOnUiThread { cleanupAndFinish() }
                    }
                }
            },
            // If the service dies while we're configuring, just bail —
            // there's nothing useful for this transient activity to do.
            onServiceDisconnected = {
                runOnUiThread { cleanupAndFinish() }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = StreamConfig(this)

        // No layout — theme is transparent, the user only sees the
        // system permission dialogs flicker on top of their game.

        if (config.streamKey.isBlank()) {
            toast(R.string.error_no_key)
            finish()
            return
        }

        // Lock the device to landscape for the duration of the consent
        // flow so the projected display orientation matches what the
        // encoder is configured for. ScreenRecordService takes over
        // orientation handling once the stream is up.
        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        requestAudio.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun cleanupAndFinish() {
        try { connection?.let { unbindService(it) } } catch (_: Exception) {}
        connection = null
        finish()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }
}
