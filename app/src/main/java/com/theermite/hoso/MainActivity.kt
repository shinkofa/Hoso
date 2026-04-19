package com.theermite.hoso

import android.Manifest
import android.content.ServiceConnection
import android.media.MediaFormat
import android.os.Bundle
import android.util.Size
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.databinding.ActivityMainBinding
import com.theermite.hoso.services.ScreenRecordService
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.UriMediaDescriptor
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
                updateUI(streaming = false)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = StreamConfig(this)
        setupUI()
        loadConfigToUI()

        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun setupUI() {
        val resolutions = arrayOf("1280x720", "1920x1080", "854x480")
        binding.spinnerResolution.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, resolutions
        )

        val bitrates = arrayOf("2500 kbps", "3500 kbps", "4500 kbps", "1500 kbps")
        binding.spinnerBitrate.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, bitrates
        )

        binding.btnStream.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
            } else {
                startStreaming()
            }
        }
    }

    private fun loadConfigToUI() {
        binding.editStreamKey.setText(config.streamKey)

        val resIdx = when (config.resolution) {
            Size(1920, 1080) -> 1
            Size(854, 480) -> 2
            else -> 0
        }
        binding.spinnerResolution.setSelection(resIdx)

        val bitrateIdx = when (config.videoBitrate) {
            3_500_000 -> 1
            4_500_000 -> 2
            1_500_000 -> 3
            else -> 0
        }
        binding.spinnerBitrate.setSelection(bitrateIdx)
    }

    private fun saveConfigFromUI() {
        config.streamKey = binding.editStreamKey.text.toString().trim()

        config.resolution = when (binding.spinnerResolution.selectedItemPosition) {
            1 -> Size(1920, 1080)
            2 -> Size(854, 480)
            else -> Size(1280, 720)
        }

        config.videoBitrate = when (binding.spinnerBitrate.selectedItemPosition) {
            1 -> 3_500_000
            2 -> 4_500_000
            3 -> 1_500_000
            else -> 2_500_000
        }
    }

    private fun startStreaming() {
        val key = binding.editStreamKey.text.toString().trim()
        if (key.isEmpty()) {
            showStatus(getString(R.string.error_no_key))
            return
        }
        showStatus(getString(R.string.status_connecting))
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private suspend fun configureAndStart(streamer: ISingleStreamer) {
        try {
            val videoConfig = VideoConfig(
                mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
                startBitrate = config.videoBitrate,
                resolution = config.resolution,
                fps = config.fps
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

            val descriptor = UriMediaDescriptor(config.fullRtmpUrl.toUri())
            streamer.startStream(descriptor)

            runOnUiThread { updateUI(streaming = true) }
        } catch (e: Exception) {
            runOnUiThread {
                showStatus("${getString(R.string.error_stream_failed)}: ${e.message}")
                updateUI(streaming = false)
            }
        }
    }

    private fun stopStreaming() {
        lifecycleScope.launch {
            try {
                streamer?.stopStream()
            } catch (_: Exception) { }

            connection?.let { unbindService(it) }
            connection = null
            streamer = null

            runOnUiThread { updateUI(streaming = false) }
        }
    }

    private fun updateUI(streaming: Boolean) {
        isStreaming = streaming
        binding.btnStream.text = getString(
            if (streaming) R.string.btn_stop else R.string.btn_start
        )
        binding.btnStream.setBackgroundColor(
            getColor(if (streaming) R.color.stream_stop else R.color.stream_start)
        )
        binding.editStreamKey.isEnabled = !streaming
        binding.spinnerResolution.isEnabled = !streaming
        binding.spinnerBitrate.isEnabled = !streaming

        showStatus(
            getString(if (streaming) R.string.status_live else R.string.status_idle)
        )
    }

    private fun showStatus(message: String) {
        binding.textStatus.text = message
    }

    override fun onDestroy() {
        if (isStreaming) {
            stopStreaming()
        }
        super.onDestroy()
    }
}
