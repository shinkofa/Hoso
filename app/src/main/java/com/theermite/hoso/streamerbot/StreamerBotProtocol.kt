package com.theermite.hoso.streamerbot

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Streamer.bot WebSocket Server protocol primitives — pure, side-effect free,
 * testable on the JVM with no Android stubs.
 *
 * Reference: https://docs.streamer.bot/api/websocket
 *
 * Auth flow (when the server has a password configured):
 *
 *   1. Server sends a `Hello` message including `authentication.salt`
 *      and `authentication.challenge` (both base64 strings).
 *   2. Client computes:
 *        secret = base64( SHA256( password || salt ) )
 *        auth   = base64( SHA256( secret   || challenge ) )
 *   3. Client sends:
 *        { "request": "Authenticate", "id": "...", "authentication": auth }
 *
 * Concatenation is done on the UTF-8 byte sequences of the input strings,
 * not on raw bytes — this matches the OBS WebSocket v5 derivation that
 * Streamer.bot's auth flow mirrors.
 */
object StreamerBotProtocol {

    /**
     * Compute the `authentication` string required by an `Authenticate`
     * request, given the password and the server-issued salt + challenge.
     *
     * Returns a base64-encoded SHA256 digest (no line breaks).
     */
    fun computeAuthentication(
        password: String,
        salt: String,
        challenge: String,
    ): String {
        val secretBytes = sha256((password + salt).toByteArray(Charsets.UTF_8))
        val secretB64 = base64NoWrap(secretBytes)
        val authBytes = sha256((secretB64 + challenge).toByteArray(Charsets.UTF_8))
        return base64NoWrap(authBytes)
    }

    /** Build an Authenticate request payload. */
    fun buildAuthenticateRequest(id: String, authentication: String): String =
        JSONObject().apply {
            put("request", "Authenticate")
            put("id", id)
            put("authentication", authentication)
        }.toString()

    /**
     * Build a Subscribe request payload.
     *
     * [events] maps an event category (e.g. `"Twitch"`) to a list of event
     * names (e.g. `["Follow", "Sub", "Cheer"]`).
     */
    fun buildSubscribeRequest(id: String, events: Map<String, List<String>>): String =
        JSONObject().apply {
            put("request", "Subscribe")
            put("id", id)
            val evObj = JSONObject()
            for ((category, names) in events) {
                evObj.put(category, names)
            }
            put("events", evObj)
        }.toString()

    /** Build a GetActions request payload. */
    fun buildGetActionsRequest(id: String): String =
        JSONObject().apply {
            put("request", "GetActions")
            put("id", id)
        }.toString()

    /**
     * Build a DoAction request payload by action id (a Streamer.bot GUID).
     *
     * [args] is optional — pass null or an empty map to omit it.
     */
    fun buildDoActionRequest(
        id: String,
        actionId: String,
        args: Map<String, String>? = null,
    ): String =
        JSONObject().apply {
            put("request", "DoAction")
            put("id", id)
            put("action", JSONObject().apply { put("id", actionId) })
            if (!args.isNullOrEmpty()) {
                val argsObj = JSONObject()
                for ((k, v) in args) argsObj.put(k, v)
                put("args", argsObj)
            }
        }.toString()

    /** Default Twitch event names worth surfacing on a mobile overlay. */
    val DEFAULT_TWITCH_EVENTS: List<String> = listOf(
        "Follow",
        "Cheer",
        "Sub",
        "ReSub",
        "GiftSub",
        "GiftBomb",
        "Raid",
    )

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    /**
     * Base64 encode without padding/line wrapping artifacts. Uses
     * [Base64.NO_WRAP] which is what Streamer.bot's reference client emits.
     *
     * On a non-Android JVM (unit tests), [Base64] is provided by
     * `android.util.Base64` from the bundled Android SDK jar at test time.
     * When the test runtime does NOT bundle Android (pure JVM), the call
     * site routes through [computeAuthentication] which is the only public
     * crypto entry point — the test class injects a JVM-native base64
     * adapter via [base64Override] to keep the unit test hermetic.
     */
    private fun base64NoWrap(bytes: ByteArray): String {
        val override = base64Override
        if (override != null) return override(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Test-only seam. Production code never sets this. Tests inject a pure
     * JVM `java.util.Base64` encoder so [computeAuthentication] can be
     * exercised without Android stubs.
     */
    @Volatile
    internal var base64Override: ((ByteArray) -> String)? = null
}
