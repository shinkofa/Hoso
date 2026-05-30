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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.audio.AudioGains
import com.theermite.hoso.config.AudioSource
import com.theermite.hoso.config.DestinationPreset
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.databinding.DialogPresetEditBinding
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

    private var suppressPresetSpinnerEvent = false

    private fun setupUI() {
        binding.spinnerResolution.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            config.presetLabels
        )

        setupAudioSourceUI()
        setupMixGainSliders()
        setupPresetUI()

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

    // G7.1 Phase B.2 — order matches AudioSource enum declaration so
    // spinner index == enum ordinal. Keep these aligned if a new entry
    // is added.
    private val audioSourceOptions = listOf(
        AudioSource.MIC,
        AudioSource.MIX
    )

    private fun setupAudioSourceUI() {
        val labels = audioSourceOptions.map { src ->
            getString(
                when (src) {
                    AudioSource.MIC -> R.string.audio_source_mic
                    AudioSource.MIX -> R.string.audio_source_mix
                }
            )
        }
        binding.spinnerAudioSource.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        binding.spinnerAudioSource.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?,
                    position: Int, id: Long
                ) {
                    val chosen = audioSourceOptions.getOrNull(position)
                        ?: return
                    if (chosen != config.audioSource) {
                        config.audioSource = chosen
                    }
                    applyMixSlidersVisibility(chosen)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun applyMixSlidersVisibility(source: AudioSource) {
        binding.groupMixGains.visibility = when (source) {
            AudioSource.MIX -> View.VISIBLE
            AudioSource.MIC -> View.GONE
        }
    }

    private fun setupMixGainSliders() {
        // Both sliders feed AudioGains (lock-free atomics read by the
        // mixer every frame) AND config.{mic,game}GainPermil (which
        // persists to prefs). Slider range is permil 0..2000 so the
        // text shows full % and dragging is a single int write.
        binding.seekbarMicGain.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    binding.textMicGainValue.text = getString(
                        R.string.gain_value_format, progress / 10
                    )
                    if (fromUser) config.micGainPermil = progress
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            }
        )
        binding.seekbarGameGain.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    binding.textGameGainValue.text = getString(
                        R.string.gain_value_format, progress / 10
                    )
                    if (fromUser) config.gameGainPermil = progress
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            }
        )
    }

    private fun setupPresetUI() {
        refreshPresetSpinner()

        binding.spinnerPreset.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?,
                    position: Int, id: Long
                ) {
                    if (suppressPresetSpinnerEvent) return
                    val presets = config.presetList
                    val selected = presets.getOrNull(position) ?: return
                    if (selected.id == config.activePresetId) return
                    // Persist any in-progress UI edits into the previously
                    // active preset before switching, so we don't lose them.
                    saveConfigFromUI()
                    config.activePresetId = selected.id
                    loadConfigToUI()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.btnPresetAdd.setOnClickListener { showPresetDialog(null) }
        binding.btnPresetEdit.setOnClickListener {
            config.activePreset?.let { showPresetDialog(it) }
        }
        binding.btnPresetDelete.setOnClickListener { confirmDeleteActivePreset() }
    }

    private fun refreshPresetSpinner() {
        val presets = config.presetList
        val labels = presets.map { p ->
            "${p.name} (${p.type.displayLabel})"
        }
        suppressPresetSpinnerEvent = true
        binding.spinnerPreset.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        val activeId = config.activePresetId
        val activeIdx = presets.indexOfFirst { it.id == activeId }
            .takeIf { it >= 0 } ?: 0
        binding.spinnerPreset.setSelection(activeIdx)
        binding.spinnerPreset.post { suppressPresetSpinnerEvent = false }

        binding.btnPresetDelete.isEnabled = presets.size > 1
    }

    private fun showPresetDialog(existing: DestinationPreset?) {
        val dlgBinding = DialogPresetEditBinding.inflate(layoutInflater)

        val types = DestinationPreset.Type.values()
        dlgBinding.spinnerPresetType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            types.map { it.displayLabel }
        )

        val initialType = existing?.type ?: DestinationPreset.Type.TWITCH
        dlgBinding.spinnerPresetType.setSelection(
            types.indexOf(initialType).coerceAtLeast(0)
        )
        dlgBinding.editPresetName.setText(
            existing?.name ?: initialType.displayLabel
        )
        dlgBinding.editPresetUrl.setText(
            existing?.rtmpUrl ?: initialType.defaultUrl
        )
        dlgBinding.editPresetKey.setText(existing?.streamKey ?: "")

        // When the user changes type on a brand-new preset, swap the URL
        // to the type's default so they don't have to type it manually.
        // For existing presets, never overwrite their custom URL silently.
        dlgBinding.spinnerPresetType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?,
                    position: Int, id: Long
                ) {
                    if (existing != null) return
                    val t = types.getOrNull(position) ?: return
                    dlgBinding.editPresetUrl.setText(t.defaultUrl)
                    val currentName = dlgBinding.editPresetName.text.toString()
                    if (currentName.isBlank() ||
                        types.any { it.displayLabel == currentName }
                    ) {
                        dlgBinding.editPresetName.setText(t.displayLabel)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        val titleRes = if (existing == null)
            R.string.preset_dialog_title_new
        else R.string.preset_dialog_title_edit

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(dlgBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = dlgBinding.editPresetName.text.toString()
                    .trim().ifBlank { initialType.displayLabel }
                val type = types[
                    dlgBinding.spinnerPresetType.selectedItemPosition
                ]
                val url = dlgBinding.editPresetUrl.text.toString().trim()
                val key = dlgBinding.editPresetKey.text.toString().trim()

                if (existing != null) {
                    existing.name = name
                    existing.type = type
                    existing.rtmpUrl = url
                    existing.streamKey = key
                    config.upsertPreset(existing)
                } else {
                    val created = DestinationPreset(
                        id = DestinationPreset.newId(),
                        name = name,
                        type = type,
                        rtmpUrl = url,
                        streamKey = key,
                        resolutionIndex = config.resolutionIndex,
                        videoBitrate = config.videoBitrate,
                    )
                    config.upsertPreset(created)
                    config.activePresetId = created.id
                }
                refreshPresetSpinner()
                loadConfigToUI()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteActivePreset() {
        val active = config.activePreset ?: return
        if (config.presetList.size <= 1) return
        AlertDialog.Builder(this)
            .setTitle(R.string.preset_delete_confirm_title)
            .setMessage(getString(R.string.preset_delete_confirm_msg, active.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                config.deletePreset(active.id)
                refreshPresetSpinner()
                loadConfigToUI()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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

        val audioIdx = audioSourceOptions.indexOf(config.audioSource)
            .takeIf { it >= 0 } ?: 0
        binding.spinnerAudioSource.setSelection(audioIdx)
        applyMixSlidersVisibility(config.audioSource)

        // Mix gains — read from persisted config (already mirrored into
        // AudioGains via StreamConfig.init). Programmatic progress change
        // fires onProgressChanged with fromUser=false → text updates,
        // but no write-back to prefs (avoids the load→save→load loop).
        binding.seekbarMicGain.progress = config.micGainPermil
        binding.seekbarGameGain.progress = config.gameGainPermil
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

            // Hand the resolved RTMP URL to the foreground service so
            // it can drive its own auto-reconnect loop (G4.1) without
            // having to call back into this activity.
            startService(
                Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_REMEMBER_URL
                    putExtra(
                        ScreenRecordService.EXTRA_RTMP_URL,
                        config.fullRtmpUrl
                    )
                }
            )

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
        binding.spinnerAudioSource.isEnabled = !streaming

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
