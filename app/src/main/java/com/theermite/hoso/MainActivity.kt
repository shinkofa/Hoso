package com.theermite.hoso

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import com.theermite.hoso.audio.AudioGains
import com.theermite.hoso.config.AudioSource
import com.theermite.hoso.config.DestinationPreset
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.databinding.DialogPresetEditBinding
import com.theermite.hoso.databinding.ActivityMainBinding
import com.theermite.hoso.services.OverlayService
import com.theermite.hoso.services.ScreenRecordService
import com.theermite.hoso.services.StreamerBotService
import io.github.thibaultbee.streampack.core.logger.ILogger
import io.github.thibaultbee.streampack.core.logger.Logger

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: StreamConfig
    private var isStreaming = false
    /**
     * Polish pass — single START/STOP button. true once the overlay has
     * been launched from this activity, flipped back on
     * BROADCAST_STOPPED or an explicit stop tap. The activity itself
     * never runs the actual stream — it's the floating overlay that
     * does — but this flag is enough to drive the button's two states.
     */
    private var overlayLaunched = false

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            runOnUiThread {
                // The service self-stopped (notification Stop tapped, or
                // streamer error). G6.2 — overlay no longer dies with the
                // stream; user can re-start from the floating bubble. We
                // only restore portrait orientation lock and refresh the
                // settings UI (button reset, status).
                requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                overlayLaunched = false
                showStatus(getString(R.string.status_idle))
                updateUI(streaming = false)
            }
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless */ }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* checked when starting stream */ }

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
        setupStreamerBotUI()

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

        binding.btnAction.setOnClickListener {
            if (overlayLaunched) stopStreaming() else startStreaming()
        }

        setupCollapsibleCards()
    }

    /**
     * Polish pass — make each of the 4 Material cards collapsible. Each
     * header row drives a body LinearLayout: tap toggles visibility,
     * chevron flips 180°, state persists in StreamConfig so the next
     * launch restores the same folded/unfolded layout. Cards stay
     * pliable while streaming so the user can free up screen real estate
     * mid-broadcast without affecting the running stream.
     */
    private fun setupCollapsibleCards() {
        bindCollapsible(
            binding.headerDestination,
            binding.bodyDestination,
            binding.chevronDestination,
            getInitial = { config.cardDestinationCollapsed },
            setState = { config.cardDestinationCollapsed = it }
        )
        bindCollapsible(
            binding.headerStream,
            binding.bodyStream,
            binding.chevronStream,
            getInitial = { config.cardStreamCollapsed },
            setState = { config.cardStreamCollapsed = it }
        )
        bindCollapsible(
            binding.headerAudio,
            binding.bodyAudio,
            binding.chevronAudio,
            getInitial = { config.cardAudioCollapsed },
            setState = { config.cardAudioCollapsed = it }
        )
        bindCollapsible(
            binding.headerSb,
            binding.bodySb,
            binding.chevronSb,
            getInitial = { config.cardSbCollapsed },
            setState = { config.cardSbCollapsed = it }
        )
    }

    private fun bindCollapsible(
        header: View,
        body: View,
        chevron: ImageView,
        getInitial: () -> Boolean,
        setState: (Boolean) -> Unit,
    ) {
        // Apply persisted state without animation on first bind so the
        // screen opens straight to the user's last layout (no flash of
        // expanded content on a card that was left folded).
        val collapsed = getInitial()
        body.visibility = if (collapsed) View.GONE else View.VISIBLE
        chevron.rotation = if (collapsed) 180f else 0f

        header.setOnClickListener {
            val nowCollapsing = body.visibility == View.VISIBLE
            if (nowCollapsing) {
                body.visibility = View.GONE
                chevron.animate().rotation(180f).setDuration(200L).start()
            } else {
                body.visibility = View.VISIBLE
                chevron.animate().rotation(0f).setDuration(200L).start()
            }
            setState(nowCollapsing)
        }
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

    // G6.1 — Streamer.bot bridge settings. App-level config (host/port/
    // password) only saved on focus loss / start to avoid prefs churn on
    // every keystroke. The enabled switch toggles the input group's
    // visibility and persists immediately — it's the actuator the
    // future StreamerBotService observes to know whether to connect.
    private fun setupStreamerBotUI() {
        applyStreamerBotGroupVisibility(config.streamerBotEnabled)
        binding.switchSbEnabled.setOnCheckedChangeListener { _, checked ->
            // Persist whatever the user typed BEFORE flipping the
            // service on, so the connection picks up the latest values.
            if (checked) persistStreamerBotFields()
            config.streamerBotEnabled = checked
            applyStreamerBotGroupVisibility(checked)
            if (checked) {
                StreamerBotService.start(this)
            } else {
                StreamerBotService.stop(this)
            }
        }
        // If the user had the bridge enabled in a previous session, auto-
        // start the service on app open so the connection is up before
        // they go live.
        if (config.streamerBotEnabled && config.streamerBotHost.isNotBlank()) {
            StreamerBotService.start(this)
        }
    }

    private fun applyStreamerBotGroupVisibility(enabled: Boolean) {
        binding.groupSbSettings.visibility =
            if (enabled) View.VISIBLE else View.GONE
    }

    private fun persistStreamerBotFields() {
        config.streamerBotHost =
            binding.editSbHost.text?.toString().orEmpty()
        config.streamerBotPort =
            binding.editSbPort.text?.toString()?.toIntOrNull()
                ?: StreamConfig.DEFAULT_SB_PORT
        config.streamerBotPassword =
            binding.editSbPassword.text?.toString().orEmpty()
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
        dlgBinding.editPresetTwitchUser.setText(existing?.twitchUsername ?: "")
        dlgBinding.layoutPresetTwitchUser.visibility =
            if (initialType == DestinationPreset.Type.TWITCH) View.VISIBLE
            else View.GONE

        // When the user changes type on a brand-new preset, swap the URL
        // to the type's default so they don't have to type it manually.
        // For existing presets, never overwrite their custom URL silently.
        // Twitch username field follows the type selection in both cases.
        dlgBinding.spinnerPresetType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?,
                    position: Int, id: Long
                ) {
                    val t = types.getOrNull(position) ?: return
                    dlgBinding.layoutPresetTwitchUser.visibility =
                        if (t == DestinationPreset.Type.TWITCH) View.VISIBLE
                        else View.GONE
                    if (existing != null) return
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
                val twitchUser = dlgBinding.editPresetTwitchUser.text
                    .toString().trim().lowercase().removePrefix("#")
                    .takeIf { type == DestinationPreset.Type.TWITCH } ?: ""

                if (existing != null) {
                    existing.name = name
                    existing.type = type
                    existing.rtmpUrl = url
                    existing.streamKey = key
                    existing.twitchUsername = twitchUser
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
                        twitchUsername = twitchUser,
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

        // Streamer.bot bridge fields are app-level, not preset-scoped,
        // but we still re-bind them on every loadConfigToUI() so the
        // initial hydration after onCreate populates them once.
        binding.editSbHost.setText(config.streamerBotHost)
        binding.editSbPort.setText(config.streamerBotPort.toString())
        binding.editSbPassword.setText(config.streamerBotPassword)
        binding.switchSbEnabled.isChecked = config.streamerBotEnabled
        applyStreamerBotGroupVisibility(config.streamerBotEnabled)
    }

    private fun saveConfigFromUI() {
        config.streamKey =
            binding.editStreamKey.text.toString().trim()
        config.resolutionIndex =
            binding.spinnerResolution.selectedItemPosition
        config.videoBitrate = StreamConfig.BITRATE_MIN +
            binding.seekbarBitrate.progress * StreamConfig.BITRATE_STEP
        // Streamer.bot fields are app-level — persist alongside the
        // preset-scoped ones whenever the UI snapshot is committed.
        persistStreamerBotFields()
    }

    /**
     * G6.2 — MainActivity no longer starts the stream itself. It saves the
     * current settings snapshot and brings up the floating overlay; the
     * user then starts the stream from the overlay (which routes through
     * [StreamPermissionActivity] to acquire MediaProjection). This matches
     * the "open Hoso → tweak settings → launch overlay → switch to game →
     * start streaming when ready" flow that the activity's screen is too
     * heavy for.
     */
    private fun startStreaming() {
        val key = binding.editStreamKey.text.toString().trim()
        if (key.isEmpty()) {
            showStatus(getString(R.string.error_no_key))
            return
        }
        saveConfigFromUI()
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        startOverlay()
        overlayLaunched = true
        showStatus(getString(R.string.status_overlay_active))
        updateUI(streaming = true)
    }

    /**
     * Emergency shutdown from the settings screen. The overlay already
     * exposes a Start/Stop toggle, so this path is mostly useful when
     * the overlay is somehow stuck or has been killed by the system.
     * Idempotent — sending ACTION_STOP to a non-running service is a
     * no-op, and stopOverlay is safe on an already-stopped service.
     */
    private fun stopStreaming() {
        stopOverlay()

        val stopIntent = Intent(
            this, ScreenRecordService::class.java
        ).apply { action = ScreenRecordService.ACTION_STOP }
        startService(stopIntent)

        overlayLaunched = false
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

        // Polish pass — single unified action button. Idle = green
        // "PASSER EN DIRECT", live/overlay-up = red "ARRÊTER LE STREAM".
        // Settings inputs lock while the overlay is up to prevent edits
        // mid-broadcast (the running stream still uses the snapshot taken
        // at startStreaming()).
        val actionTextRes =
            if (streaming) R.string.action_stop_live
            else R.string.action_go_live
        val actionColorRes =
            if (streaming) R.color.stream_stop else R.color.stream_start
        binding.btnAction.setText(actionTextRes)
        binding.btnAction.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, actionColorRes)
        )

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
