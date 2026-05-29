package com.theermite.hoso

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.databinding.ActivityMainBinding
import com.theermite.hoso.services.OverlayService
import com.theermite.hoso.services.ScreenRecordService
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.UriMediaDescriptor
import io.github.thibaultbee.streampack.core.logger.ILogger
import io.github.thibaultbee.streampack.core.logger.Logger
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.IAudioSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.IVideoSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.utils.MediaProjectionUtils
import io.github.thibaultbee.streampack.services.MediaProjectionService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: StreamConfig
    private var connection: ServiceConnection? = null
    private var streamer: ISingleStreamer? = null
    private var isStreaming = false

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            runOnUiThread {
                // The service self-stopped (notification Stop tapped, or
                // streamer error). Release our bind BEFORE clearing the
                // connection reference, otherwise the binding leaks and a
                // subsequent start fails (stale ServiceConnection inside the
                // ActivityManager) — observable as "service not started" on
                // the next start attempt.
                stopOverlay()
                connection?.let {
                    try { unbindService(it) } catch (_: Exception) {}
                }
                streamer = null
                connection = null
                requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                updateUI(streaming = false)
            }
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            screenCaptureLauncher.launch(
                MediaProjectionUtils.createScreenCaptureIntent(this)
            )
        } else {
            showStatus(getString(R.string.error_mic_denied))
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless */ }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* checked when starting stream */ }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            showStatus(getString(R.string.error_capture_denied))
            return@registerForActivityResult
        }

        saveConfigFromUI()

        connection = MediaProjectionService.bindService(
            context = this,
            serviceClass = ScreenRecordService::class.java,
            resultCode = result.resultCode,
            resultData = result.data!!,
            onServiceCreated = { boundStreamer ->
                streamer = boundStreamer as ISingleStreamer
                lifecycleScope.launch {
                    configureAndStart(boundStreamer as ISingleStreamer)
                }
            },
            onServiceDisconnected = {
                streamer = null
                runOnUiThread { updateUI(streaming = false) }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remap all StreamPack logs (v/d/i/w/e) to Log.i with SP/ prefix
        // so verbose/debug levels survive logcat default filtering.
        Logger.logger = object : ILogger {
            override fun e(tag: String, message: String, tr: Throwable?) {
                Log.i("SP/$tag", "E $message", tr)
            }
            override fun w(tag: String, message: String, tr: Throwable?) {
                Log.i("SP/$tag", "W $message", tr)
            }
            override fun i(tag: String, message: String, tr: Throwable?) {
                Log.i("SP/$tag", "I $message", tr)
            }
            override fun v(tag: String, message: String, tr: Throwable?) {
                Log.i("SP/$tag", "V $message", tr)
            }
            override fun d(tag: String, message: String, tr: Throwable?) {
                Log.i("SP/$tag", "D $message", tr)
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = StreamConfig(this)
        setupUI()
        loadConfigToUI()

        registerReceiver(
            stoppedReceiver,
            IntentFilter(ScreenRecordService.BROADCAST_STOPPED),
            RECEIVER_NOT_EXPORTED
        )

        requestNotificationPermission.launch(
            Manifest.permission.POST_NOTIFICATIONS
        )
        requestOverlayPermission()
    }

    override fun onDestroy() {
        unregisterReceiver(stoppedReceiver)
        super.onDestroy()
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun setupUI() {
        binding.spinnerResolution.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            config.presetLabels
        )

        val steps = (StreamConfig.BITRATE_MAX - StreamConfig.BITRATE_MIN) /
            StreamConfig.BITRATE_STEP
        binding.seekbarBitrate.max = steps
        binding.seekbarBitrate.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    val kbps = StreamConfig.BITRATE_MIN +
                        progress * StreamConfig.BITRATE_STEP
                    binding.textBitrateValue.text = "$kbps kbps"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            }
        )

        binding.btnStart.setOnClickListener { startStreaming() }
        binding.btnStop.setOnClickListener { stopStreaming() }
    }

    private fun loadConfigToUI() {
        binding.editStreamKey.setText(config.streamKey)
        binding.spinnerResolution.setSelection(config.resolutionIndex)

        val progress = (config.videoBitrate - StreamConfig.BITRATE_MIN) /
            StreamConfig.BITRATE_STEP
        binding.seekbarBitrate.progress = progress.coerceIn(
            0, binding.seekbarBitrate.max
        )
        binding.textBitrateValue.text = "${config.videoBitrate} kbps"
    }

    private fun saveConfigFromUI() {
        config.streamKey =
            binding.editStreamKey.text.toString().trim()
        config.resolutionIndex =
            binding.spinnerResolution.selectedItemPosition
        config.videoBitrate = StreamConfig.BITRATE_MIN +
            binding.seekbarBitrate.progress * StreamConfig.BITRATE_STEP
    }

    private fun startStreaming() {
        val key = binding.editStreamKey.text.toString().trim()
        if (key.isEmpty()) {
            showStatus(getString(R.string.error_no_key))
            return
        }
        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        showStatus(getString(R.string.status_connecting))
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private suspend fun configureAndStart(streamer: ISingleStreamer) {
        try {
            val raw = config.resolution
            val res = Size(
                maxOf(raw.width, raw.height),
                minOf(raw.width, raw.height)
            )
            val bitrate = config.videoBitrate * 1000

            val videoConfig = VideoConfig(
                mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
                startBitrate = bitrate,
                resolution = res,
                fps = config.fps,
                gopDurationInS = 2f,
                customize = {
                    setInteger(
                        MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    )
                }
            )

            val audioConfig = AudioConfig(
                mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
                startBitrate = config.audioBitrate,
                sampleRate = 44100,
                channelConfig = AudioConfig.getChannelConfig(1),
                byteFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            )

            (streamer as IVideoSingleStreamer).setVideoConfig(videoConfig)
            (streamer as IAudioSingleStreamer).setAudioConfig(audioConfig)

            val descriptor = UriMediaDescriptor(
                config.fullRtmpUrl.toUri()
            )
            streamer.startStream(descriptor)

            runOnUiThread {
                updateUI(streaming = true)
                startOverlay()
            }
        } catch (e: Exception) {
            runOnUiThread {
                showStatus(
                    "${getString(R.string.error_stream_failed)}: " +
                        "${e.message}"
                )
                updateUI(streaming = false)
            }
        }
    }

    private fun stopStreaming() {
        stopOverlay()

        val stopIntent = Intent(
            this, ScreenRecordService::class.java
        ).apply { action = ScreenRecordService.ACTION_STOP }
        startService(stopIntent)

        try {
            connection?.let { unbindService(it) }
        } catch (_: Exception) { }
        connection = null
        streamer = null

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        updateUI(streaming = false)
    }

    private fun startOverlay() {
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(
                Intent(this, OverlayService::class.java)
            )
        }
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun updateUI(streaming: Boolean) {
        isStreaming = streaming
        binding.btnStart.isEnabled = !streaming
        binding.btnStop.isEnabled = streaming
        binding.editStreamKey.isEnabled = !streaming
        binding.spinnerResolution.isEnabled = !streaming
        binding.seekbarBitrate.isEnabled = !streaming

        showStatus(
            getString(
                if (streaming) R.string.status_live
                else R.string.status_idle
            )
        )
    }

    private fun showStatus(message: String) {
        binding.textStatus.text = message
    }
}
