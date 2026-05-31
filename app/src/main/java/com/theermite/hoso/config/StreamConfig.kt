package com.theermite.hoso.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Size
import android.view.WindowManager
import com.theermite.hoso.audio.AudioGains

class StreamConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val nativeScreen: Size = detectNativeScreen(context)

    init {
        // Hydrate the in-memory AudioGains singleton from persisted prefs
        // so the audio pipeline reads the user's last-known mix gains
        // from frame #1 — and so the UI sliders bind to the same values.
        AudioGains.micGainPermil = prefs.getInt(
            KEY_MIC_GAIN_PERMIL, AudioGains.GAIN_DEFAULT_MIC
        )
        AudioGains.gameGainPermil = prefs.getInt(
            KEY_GAME_GAIN_PERMIL, AudioGains.GAIN_DEFAULT_GAME
        )

        // Silent migration: if no presets stored yet (fresh install OR
        // user upgrading from per-key storage), seed a default Twitch
        // preset from whatever values were previously in prefs.
        if (prefs.getString(KEY_PRESETS_JSON, null).isNullOrBlank()) {
            val legacyKey = prefs.getString(KEY_STREAM_KEY, "") ?: ""
            val legacyUrl = prefs.getString(
                KEY_RTMP_URL, DEFAULT_TWITCH_URL
            ) ?: DEFAULT_TWITCH_URL
            val legacyBitrate = prefs.getInt(
                KEY_VIDEO_BITRATE, DEFAULT_VIDEO_BITRATE
            )
            val legacyResIdx = prefs.getInt(KEY_RES_INDEX, 0)

            val seed = DestinationPreset(
                id = DestinationPreset.newId(),
                name = DestinationPreset.Type.TWITCH.displayLabel,
                type = DestinationPreset.Type.TWITCH,
                rtmpUrl = legacyUrl,
                streamKey = legacyKey,
                resolutionIndex = legacyResIdx,
                videoBitrate = legacyBitrate,
            )
            prefs.edit()
                .putString(
                    KEY_PRESETS_JSON,
                    DestinationPreset.listToJson(listOf(seed))
                )
                .putString(KEY_ACTIVE_PRESET_ID, seed.id)
                .apply()
        }
    }

    // ── Preset list management ────────────────────────────────────────

    var presetList: List<DestinationPreset>
        get() = DestinationPreset.listFromJson(
            prefs.getString(KEY_PRESETS_JSON, null)
        )
        set(value) {
            prefs.edit()
                .putString(
                    KEY_PRESETS_JSON,
                    DestinationPreset.listToJson(value)
                )
                .apply()
        }

    var activePresetId: String
        get() = prefs.getString(KEY_ACTIVE_PRESET_ID, "") ?: ""
        set(value) = prefs.edit()
            .putString(KEY_ACTIVE_PRESET_ID, value).apply()

    val activePreset: DestinationPreset?
        get() {
            val id = activePresetId
            val list = presetList
            return list.firstOrNull { it.id == id }
                ?: list.firstOrNull()
        }

    fun upsertPreset(preset: DestinationPreset) {
        val list = presetList.toMutableList()
        val idx = list.indexOfFirst { it.id == preset.id }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        presetList = list
    }

    fun deletePreset(id: String) {
        val list = presetList.toMutableList()
        list.removeAll { it.id == id }
        if (list.isEmpty()) {
            // Never leave the user with zero presets — re-seed a fresh
            // default Twitch one so the UI always has something to bind to.
            val seed = DestinationPreset(
                id = DestinationPreset.newId(),
                name = DestinationPreset.Type.TWITCH.displayLabel,
                type = DestinationPreset.Type.TWITCH,
                rtmpUrl = DEFAULT_TWITCH_URL,
                streamKey = "",
                resolutionIndex = 0,
                videoBitrate = DEFAULT_VIDEO_BITRATE,
            )
            list.add(seed)
            activePresetId = seed.id
        } else if (activePresetId == id) {
            activePresetId = list.first().id
        }
        presetList = list
    }

    // ── Active-preset accessors (UI binds to these) ──────────────────

    var streamKey: String
        get() = activePreset?.streamKey ?: ""
        set(value) {
            activePreset?.let { p ->
                p.streamKey = value
                upsertPreset(p)
            }
        }

    var rtmpBaseUrl: String
        get() = activePreset?.rtmpUrl ?: DEFAULT_TWITCH_URL
        set(value) {
            activePreset?.let { p ->
                p.rtmpUrl = value
                upsertPreset(p)
            }
        }

    var videoBitrate: Int
        get() = activePreset?.videoBitrate ?: DEFAULT_VIDEO_BITRATE
        set(value) {
            activePreset?.let { p ->
                p.videoBitrate = value
                upsertPreset(p)
            }
        }

    var resolutionIndex: Int
        get() = activePreset?.resolutionIndex ?: 0
        set(value) {
            activePreset?.let { p ->
                p.resolutionIndex = value
                upsertPreset(p)
            }
        }

    val resolution: Size
        get() {
            val presets = buildPresets(nativeScreen)
            val idx = resolutionIndex.coerceIn(
                0, presets.lastIndex
            )
            return presets[idx].second
        }

    var fps: Int
        get() = prefs.getInt(KEY_FPS, DEFAULT_FPS)
        set(value) = prefs.edit()
            .putInt(KEY_FPS, value).apply()

    var audioBitrate: Int
        get() = prefs.getInt(KEY_AUDIO_BITRATE, DEFAULT_AUDIO_BITRATE)
        set(value) = prefs.edit()
            .putInt(KEY_AUDIO_BITRATE, value).apply()

    // G7.1 Phase B.1 — chosen audio capture source. Default = MIC keeps
    // legacy behavior so existing users notice no change after upgrade.
    var audioSource: AudioSource
        get() = AudioSource.fromStorageKey(
            prefs.getString(KEY_AUDIO_SOURCE, null)
        )
        set(value) = prefs.edit()
            .putString(KEY_AUDIO_SOURCE, value.storageKey).apply()

    // G7.1 Phase B.2 — mix gains, global across destinations. The UI
    // slider writes here; the audio pipeline reads them via AudioGains
    // every frame. Persistence is global on purpose: a Bluetooth-mic /
    // wired-headset trade-off is a hardware preference of the user, not
    // a property of "the Twitch destination" vs "another".
    var micGainPermil: Int
        get() = prefs.getInt(KEY_MIC_GAIN_PERMIL, AudioGains.GAIN_DEFAULT_MIC)
        set(value) {
            val clamped = value.coerceIn(AudioGains.GAIN_MIN, AudioGains.GAIN_MAX)
            prefs.edit().putInt(KEY_MIC_GAIN_PERMIL, clamped).apply()
            AudioGains.micGainPermil = clamped
        }

    var gameGainPermil: Int
        get() = prefs.getInt(KEY_GAME_GAIN_PERMIL, AudioGains.GAIN_DEFAULT_GAME)
        set(value) {
            val clamped = value.coerceIn(AudioGains.GAIN_MIN, AudioGains.GAIN_MAX)
            prefs.edit().putInt(KEY_GAME_GAIN_PERMIL, clamped).apply()
            AudioGains.gameGainPermil = clamped
        }

    val fullRtmpUrl: String
        get() = "$rtmpBaseUrl$streamKey"

    // ── Chat bubble overlay (G6.2) ───────────────────────────────────
    // Position is stored as an edge-anchor (LEFT/RIGHT) + Y offset so
    // the bubble re-snaps cleanly to the same edge after rotation
    // instead of drifting toward the center.

    var chatEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHAT_ENABLED, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_CHAT_ENABLED, value).apply()

    var chatSizeIndex: Int
        get() = prefs.getInt(KEY_CHAT_SIZE_IDX, CHAT_SIZE_DEFAULT)
        set(value) = prefs.edit()
            .putInt(KEY_CHAT_SIZE_IDX, value.coerceIn(0, 2)).apply()

    /** 0..100 — slider unit. The window alpha = opacity/100. */
    var chatOpacity: Int
        get() = prefs.getInt(KEY_CHAT_OPACITY, CHAT_OPACITY_DEFAULT)
        set(value) = prefs.edit()
            .putInt(KEY_CHAT_OPACITY, value.coerceIn(CHAT_OPACITY_MIN, CHAT_OPACITY_MAX))
            .apply()

    /**
     * X offset in px from the left of the screen for the bubble origin.
     * -1 means "not yet positioned" → ChatBubbleService defaults to the
     * right edge for first launch. Free positioning since the post-launch
     * UX rework (no more edge snap).
     */
    var chatX: Int
        get() = prefs.getInt(KEY_CHAT_X, -1)
        set(value) = prefs.edit().putInt(KEY_CHAT_X, value).apply()

    /** Y offset in px from the top of the screen for the bubble origin. */
    var chatY: Int
        get() = prefs.getInt(KEY_CHAT_Y, -1)
        set(value) = prefs.edit().putInt(KEY_CHAT_Y, value).apply()

    // ── Streamer.bot bridge (G6.1) ───────────────────────────────────
    // App-level settings, intentionally NOT per-destination: the bridge
    // talks to Jay's PC over Tailscale and is the same regardless of
    // whether the stream goes to Twitch, YouTube, or Kick.

    var streamerBotEnabled: Boolean
        get() = prefs.getBoolean(KEY_SB_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SB_ENABLED, value).apply()

    var streamerBotHost: String
        get() = prefs.getString(KEY_SB_HOST, "") ?: ""
        // Strip ALL whitespace, not just trim() — Hibiki voice
        // dictation inserts spaces after dots ("100. 109.16.38"),
        // which okhttp rejects with "Invalid URL host".
        set(value) = prefs.edit()
            .putString(KEY_SB_HOST, value.replace(Regex("\\s+"), ""))
            .apply()

    var streamerBotPort: Int
        get() = prefs.getInt(KEY_SB_PORT, DEFAULT_SB_PORT)
        set(value) = prefs.edit()
            .putInt(KEY_SB_PORT, value.coerceIn(1, 65_535))
            .apply()

    /**
     * Optional WebSocket password. Empty string = no auth (server has auth
     * disabled). Stored in plain SharedPreferences — same trust level as
     * the Twitch stream key already living in this file.
     */
    var streamerBotPassword: String
        get() = prefs.getString(KEY_SB_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SB_PASSWORD, value).apply()

    // ── Main screen collapsible cards (polish pass) ──────────────────
    // Per-card collapsed state. Persisted so the screen reopens with the
    // same folded/unfolded layout the user left it in. Default = false
    // (all expanded on first launch) so the user discovers the inputs.

    var cardDestinationCollapsed: Boolean
        get() = prefs.getBoolean(KEY_CARD_DEST_COLLAPSED, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_CARD_DEST_COLLAPSED, value).apply()

    var cardStreamCollapsed: Boolean
        get() = prefs.getBoolean(KEY_CARD_STREAM_COLLAPSED, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_CARD_STREAM_COLLAPSED, value).apply()

    var cardAudioCollapsed: Boolean
        get() = prefs.getBoolean(KEY_CARD_AUDIO_COLLAPSED, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_CARD_AUDIO_COLLAPSED, value).apply()

    var cardSbCollapsed: Boolean
        get() = prefs.getBoolean(KEY_CARD_SB_COLLAPSED, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_CARD_SB_COLLAPSED, value).apply()

    val presets: List<Pair<String, Size>> by lazy {
        buildPresets(nativeScreen)
    }

    val presetLabels: Array<String> by lazy {
        presets.map { it.first }.toTypedArray()
    }

    companion object {
        private const val PREFS_NAME = "hoso_stream_config"
        // Legacy keys (kept readable for migration only)
        private const val KEY_STREAM_KEY = "stream_key"
        private const val KEY_RTMP_URL = "rtmp_base_url"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_RES_INDEX = "res_idx_v3"
        // Non-preset keys (apply to all destinations)
        private const val KEY_FPS = "fps"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_MIC_GAIN_PERMIL = "mix_mic_gain_permil"
        private const val KEY_GAME_GAIN_PERMIL = "mix_game_gain_permil"
        // Preset storage
        private const val KEY_PRESETS_JSON = "presets_json_v1"
        private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"
        // Chat overlay (G6.2)
        private const val KEY_CHAT_ENABLED = "chat_enabled"
        private const val KEY_CHAT_SIZE_IDX = "chat_size_idx"
        private const val KEY_CHAT_OPACITY = "chat_opacity"
        private const val KEY_CHAT_X = "chat_x"
        private const val KEY_CHAT_Y = "chat_y"
        const val CHAT_SIZE_DEFAULT = 1   // M
        const val CHAT_OPACITY_DEFAULT = 70
        const val CHAT_OPACITY_MIN = 20
        const val CHAT_OPACITY_MAX = 80
        // Streamer.bot bridge (G6.1)
        private const val KEY_SB_ENABLED = "sb_enabled"
        private const val KEY_SB_HOST = "sb_host"
        private const val KEY_SB_PORT = "sb_port"
        private const val KEY_SB_PASSWORD = "sb_password"
        const val DEFAULT_SB_PORT = 8080
        // Main screen collapsible cards (polish pass)
        private const val KEY_CARD_DEST_COLLAPSED = "card_dest_collapsed"
        private const val KEY_CARD_STREAM_COLLAPSED = "card_stream_collapsed"
        private const val KEY_CARD_AUDIO_COLLAPSED = "card_audio_collapsed"
        private const val KEY_CARD_SB_COLLAPSED = "card_sb_collapsed"

        const val DEFAULT_TWITCH_URL = "rtmp://live.twitch.tv/app/"
        const val DEFAULT_VIDEO_BITRATE = 3_000
        const val DEFAULT_AUDIO_BITRATE = 128_000
        const val DEFAULT_FPS = 30

        const val BITRATE_MIN = 1_000
        const val BITRATE_MAX = 8_000
        const val BITRATE_STEP = 500

        private fun detectNativeScreen(ctx: Context): Size {
            val wm = ctx.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager
            val b = wm.maximumWindowMetrics.bounds
            return Size(b.width(), b.height())
        }

        private fun buildPresets(native: Size): List<Pair<String, Size>> {
            val longSide = maxOf(native.width, native.height)
            val shortSide = minOf(native.width, native.height)
            val ratio = shortSide.toFloat() / longSide

            val fitH1080 = roundEven((1920 * ratio).toInt())
            val fitH720 = roundEven((1280 * ratio).toInt())

            return listOf(
                "1080p Fit (1920x$fitH1080)" to
                    Size(1920, fitH1080),
                "1080p Crop (1920x1080)" to
                    Size(1920, 1080),
                "720p Fit (1280x$fitH720)" to
                    Size(1280, fitH720),
                "720p Crop (1280x720)" to
                    Size(1280, 720),
                "480p (854x480)" to Size(854, 480)
            )
        }

        private fun roundEven(value: Int): Int =
            (value / 2) * 2
    }
}
