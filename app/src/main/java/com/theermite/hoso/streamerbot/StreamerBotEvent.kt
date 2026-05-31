package com.theermite.hoso.streamerbot

import org.json.JSONObject

/**
 * Streamer.bot event payload — the union of the event envelope (source/type)
 * and the per-event `data` blob that Streamer.bot ships verbatim from the
 * upstream platform (Twitch, YouTube, …).
 *
 * Reference: https://docs.streamer.bot/api/websocket/events
 *
 * The data blob shape varies wildly by event type, so it is kept as a raw
 * [JSONObject] and inspected lazily via the typed accessors below. New event
 * types can be added without code changes.
 */
data class StreamerBotEvent(
    /** Event source/category, e.g. `"Twitch"`, `"YouTube"`, `"Kick"`. */
    val source: String,
    /** Event name, e.g. `"Follow"`, `"Sub"`, `"Cheer"`, `"Raid"`. */
    val type: String,
    /** Raw event payload as delivered by the server. Never null (may be empty). */
    val data: JSONObject,
) {

    /** Display name of the user that triggered the event, when available. */
    val userName: String?
        get() = data.optStringOrNull("user_name")
            ?: data.optStringOrNull("userName")
            ?: data.optStringOrNull("displayName")
            ?: data.optStringOrNull("display_name")

    /** Bits cheered (Twitch Cheer event), null when not applicable. */
    val bits: Int?
        get() = data.optIntOrNull("bits") ?: data.optIntOrNull("cheered")

    /** Sub tier / months (Twitch Sub/ReSub), null when not applicable. */
    val months: Int?
        get() = data.optIntOrNull("cumulative") ?: data.optIntOrNull("months")

    /** Raid viewer count (Twitch Raid), null when not applicable. */
    val viewers: Int?
        get() = data.optIntOrNull("viewers") ?: data.optIntOrNull("viewerCount")

    companion object {

        /**
         * Parse a Streamer.bot event frame. Returns null if the JSON does not
         * have the expected event envelope (e.g. it is a response, not an
         * event).
         *
         * Expected shape:
         *
         *   {
         *     "timestamp": "...",
         *     "event": { "source": "Twitch", "type": "Follow" },
         *     "data":  { ...platform-specific fields... }
         *   }
         */
        fun parse(json: JSONObject): StreamerBotEvent? {
            val ev = json.optJSONObject("event") ?: return null
            val source = ev.optStringOrNull("source") ?: return null
            val type = ev.optStringOrNull("type") ?: return null
            val data = json.optJSONObject("data") ?: JSONObject()
            return StreamerBotEvent(source = source, type = type, data = data)
        }
    }
}

/**
 * Streamer.bot action descriptor returned by a `GetActions` response.
 *
 * Reference: https://docs.streamer.bot/api/websocket/requests/get-actions
 */
data class StreamerBotAction(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val group: String? = null,
) {
    companion object {

        /**
         * Parse the actions array out of a GetActions response payload.
         *
         * Response shape:
         *
         *   {
         *     "status": "ok",
         *     "id": "...",
         *     "count": 12,
         *     "actions": [
         *        { "id": "guid", "name": "Foo", "enabled": true, "group": "..." },
         *        ...
         *     ]
         *   }
         *
         * Items missing `id` or `name` are silently skipped — the server
         * should never emit them, but a single malformed entry must not
         * break the whole list.
         */
        fun parseList(response: JSONObject): List<StreamerBotAction> {
            val arr = response.optJSONArray("actions") ?: return emptyList()
            val out = ArrayList<StreamerBotAction>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optStringOrNull("id") ?: continue
                val name = obj.optStringOrNull("name") ?: continue
                val enabled = obj.optBoolean("enabled", true)
                val group = obj.optStringOrNull("group")
                out.add(StreamerBotAction(id = id, name = name, enabled = enabled, group = group))
            }
            return out
        }
    }
}

/** Returns null instead of the literal string `"null"` or an empty string. */
internal fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key, "")
    return v.takeIf { it.isNotEmpty() }
}

/** Returns null when the key is missing or not coercible to an Int. */
internal fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getInt(key) }.getOrNull()
}
