package com.theermite.hoso.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Size
import android.view.WindowManager

class StreamConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val nativeScreen: Size = detectNativeScreen(context)

    var streamKey: String
        get() = prefs.getString(KEY_STREAM_KEY, "") ?: ""
        set(value) = prefs.edit()
            .putString(KEY_STREAM_KEY, value).apply()

    var rtmpBaseUrl: String
        get() = prefs.getString(
            KEY_RTMP_URL, DEFAULT_TWITCH_URL
        ) ?: DEFAULT_TWITCH_URL
        set(value) = prefs.edit()
            .putString(KEY_RTMP_URL, value).apply()

    var videoBitrate: Int
        get() = prefs.getInt(KEY_VIDEO_BITRATE, DEFAULT_VIDEO_BITRATE)
        set(value) = prefs.edit()
            .putInt(KEY_VIDEO_BITRATE, value).apply()

    var resolutionIndex: Int
        get() = prefs.getInt(KEY_RES_INDEX, 0)
        set(value) = prefs.edit()
            .putInt(KEY_RES_INDEX, value).apply()

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

    val fullRtmpUrl: String
        get() = "$rtmpBaseUrl$streamKey"

    val presets: List<Pair<String, Size>> by lazy {
        buildPresets(nativeScreen)
    }

    val presetLabels: Array<String> by lazy {
        presets.map { it.first }.toTypedArray()
    }

    companion object {
        private const val PREFS_NAME = "hoso_stream_config"
        private const val KEY_STREAM_KEY = "stream_key"
        private const val KEY_RTMP_URL = "rtmp_base_url"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_RES_INDEX = "res_idx_v3"
        private const val KEY_FPS = "fps"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"

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
