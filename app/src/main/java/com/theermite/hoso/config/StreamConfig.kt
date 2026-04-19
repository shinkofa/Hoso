package com.theermite.hoso.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Size

class StreamConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var streamKey: String
        get() = prefs.getString(KEY_STREAM_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STREAM_KEY, value).apply()

    var rtmpBaseUrl: String
        get() = prefs.getString(KEY_RTMP_URL, DEFAULT_TWITCH_URL) ?: DEFAULT_TWITCH_URL
        set(value) = prefs.edit().putString(KEY_RTMP_URL, value).apply()

    var videoBitrate: Int
        get() = prefs.getInt(KEY_VIDEO_BITRATE, DEFAULT_VIDEO_BITRATE)
        set(value) = prefs.edit().putInt(KEY_VIDEO_BITRATE, value).apply()

    var resolution: Size
        get() {
            val w = prefs.getInt(KEY_RES_W, DEFAULT_WIDTH)
            val h = prefs.getInt(KEY_RES_H, DEFAULT_HEIGHT)
            return Size(w, h)
        }
        set(value) = prefs.edit()
            .putInt(KEY_RES_W, value.width)
            .putInt(KEY_RES_H, value.height)
            .apply()

    var fps: Int
        get() = prefs.getInt(KEY_FPS, DEFAULT_FPS)
        set(value) = prefs.edit().putInt(KEY_FPS, value).apply()

    var audioBitrate: Int
        get() = prefs.getInt(KEY_AUDIO_BITRATE, DEFAULT_AUDIO_BITRATE)
        set(value) = prefs.edit().putInt(KEY_AUDIO_BITRATE, value).apply()

    val fullRtmpUrl: String
        get() = "$rtmpBaseUrl$streamKey"

    companion object {
        private const val PREFS_NAME = "hoso_stream_config"
        private const val KEY_STREAM_KEY = "stream_key"
        private const val KEY_RTMP_URL = "rtmp_base_url"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_RES_W = "resolution_w"
        private const val KEY_RES_H = "resolution_h"
        private const val KEY_FPS = "fps"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"

        const val DEFAULT_TWITCH_URL = "rtmp://live.twitch.tv/app/"
        const val DEFAULT_VIDEO_BITRATE = 2_500_000
        const val DEFAULT_AUDIO_BITRATE = 128_000
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 30
    }
}
