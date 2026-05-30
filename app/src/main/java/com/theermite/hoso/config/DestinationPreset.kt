package com.theermite.hoso.config

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Streaming destination preset: a named bundle of (RTMP URL + stream key +
 * resolution + bitrate). Stored as a JSON list in SharedPreferences via
 * [StreamConfig]. The active preset id is stored separately so it survives
 * preset edits / renames.
 *
 * The [type] is used only for UI (icon, label hints, default URL on create).
 * The actual RTMP target is always [rtmpUrl] + [streamKey].
 */
data class DestinationPreset(
    val id: String,
    var name: String,
    var type: Type,
    var rtmpUrl: String,
    var streamKey: String,
    var resolutionIndex: Int,
    var videoBitrate: Int,
    /**
     * Twitch channel/login used by the chat IRC overlay (read-only). Saved
     * once at preset creation because Twitch stream keys are opaque and
     * cannot be reversed to a channel name. Empty for non-Twitch presets.
     */
    var twitchUsername: String = "",
) {

    enum class Type(val defaultUrl: String, val displayLabel: String) {
        TWITCH("rtmp://live.twitch.tv/app/", "Twitch"),
        YOUTUBE("rtmp://a.rtmp.youtube.com/live2/", "YouTube Live"),
        KICK("rtmps://fa723fc1b171.global-contribute.live-video.net/app/", "Kick"),
        CUSTOM("rtmp://", "Custom RTMP");

        companion object {
            fun fromName(name: String?): Type =
                values().firstOrNull { it.name == name } ?: CUSTOM
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put(K_ID, id)
        put(K_NAME, name)
        put(K_TYPE, type.name)
        put(K_URL, rtmpUrl)
        put(K_KEY, streamKey)
        put(K_RES_IDX, resolutionIndex)
        put(K_BITRATE, videoBitrate)
        put(K_TWITCH_USER, twitchUsername)
    }

    companion object {
        private const val K_ID = "id"
        private const val K_NAME = "name"
        private const val K_TYPE = "type"
        private const val K_URL = "url"
        private const val K_KEY = "key"
        private const val K_RES_IDX = "res_idx"
        private const val K_BITRATE = "bitrate"
        private const val K_TWITCH_USER = "twitch_user"

        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(obj: JSONObject): DestinationPreset = DestinationPreset(
            id = obj.optString(K_ID, newId()),
            name = obj.optString(K_NAME, "Preset"),
            type = Type.fromName(obj.optString(K_TYPE)),
            rtmpUrl = obj.optString(K_URL, ""),
            streamKey = obj.optString(K_KEY, ""),
            resolutionIndex = obj.optInt(K_RES_IDX, 0),
            videoBitrate = obj.optInt(K_BITRATE, 3_000),
            twitchUsername = obj.optString(K_TWITCH_USER, ""),
        )

        fun listToJson(list: List<DestinationPreset>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(raw: String?): List<DestinationPreset> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map {
                    fromJson(arr.getJSONObject(it))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
