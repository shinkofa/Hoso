package com.theermite.hoso.streamerbot

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64 as JvmBase64

/**
 * Pure JVM unit tests for the Streamer.bot WebSocket protocol primitives.
 *
 * The auth test vectors were computed independently with Python's stdlib
 * (`hashlib.sha256` + `base64.b64encode`) — see session report 2026-06-01
 * for the exact computation. Hardcoding them here protects against silent
 * regressions in our hash composition order or base64 variant.
 */
class StreamerBotProtocolTest {

    @Before
    fun installJvmBase64Adapter() {
        // Replace android.util.Base64 with a JVM-native equivalent so the
        // pure unit test runs without an Android SDK stub on classpath.
        StreamerBotProtocol.base64Override = { bytes ->
            JvmBase64.getEncoder().encodeToString(bytes)
        }
    }

    @After
    fun resetAdapter() {
        StreamerBotProtocol.base64Override = null
    }

    // ---------------------------------------------------------------
    // Auth hash — independent test vector (computed offline via Python)
    // ---------------------------------------------------------------

    @Test
    fun `computeAuthentication matches the independent Python reference vector`() {
        val auth = StreamerBotProtocol.computeAuthentication(
            password = "testpassword",
            salt = "somesalt123",
            challenge = "somechallenge456",
        )
        assertEquals("0T2fZe+2gbDDZy+0sQWIytzrD2DFJH+Wposl0gsegoA=", auth)
    }

    @Test
    fun `computeAuthentication is deterministic for identical inputs`() {
        val a = StreamerBotProtocol.computeAuthentication("p", "s", "c")
        val b = StreamerBotProtocol.computeAuthentication("p", "s", "c")
        assertEquals(a, b)
    }

    @Test
    fun `computeAuthentication differs when any input changes`() {
        val base = StreamerBotProtocol.computeAuthentication("pw", "salt", "ch")
        val diffPw = StreamerBotProtocol.computeAuthentication("pw2", "salt", "ch")
        val diffSalt = StreamerBotProtocol.computeAuthentication("pw", "salt2", "ch")
        val diffCh = StreamerBotProtocol.computeAuthentication("pw", "salt", "ch2")
        assertFalse(base == diffPw)
        assertFalse(base == diffSalt)
        assertFalse(base == diffCh)
    }

    @Test
    fun `computeAuthentication handles UTF-8 password correctly`() {
        // Accents and CJK must round-trip via UTF-8 bytes, not the platform default.
        val auth = StreamerBotProtocol.computeAuthentication(
            password = "pässwörd日本語",
            salt = "s",
            challenge = "c",
        )
        // The exact value matters less than: it must produce a 44-char base64
        // string (32 bytes SHA256 → 44 chars base64 with padding) and be
        // reproducible across calls.
        assertEquals(44, auth.length)
        assertEquals(
            auth,
            StreamerBotProtocol.computeAuthentication("pässwörd日本語", "s", "c"),
        )
    }

    // ---------------------------------------------------------------
    // Request builders
    // ---------------------------------------------------------------

    @Test
    fun `buildAuthenticateRequest serializes all fields`() {
        val raw = StreamerBotProtocol.buildAuthenticateRequest(
            id = "auth-1",
            authentication = "ABCDEF==",
        )
        val obj = JSONObject(raw)
        assertEquals("Authenticate", obj.getString("request"))
        assertEquals("auth-1", obj.getString("id"))
        assertEquals("ABCDEF==", obj.getString("authentication"))
    }

    @Test
    fun `buildSubscribeRequest nests events by category`() {
        val raw = StreamerBotProtocol.buildSubscribeRequest(
            id = "sub-1",
            events = mapOf("Twitch" to listOf("Follow", "Sub", "Raid")),
        )
        val obj = JSONObject(raw)
        assertEquals("Subscribe", obj.getString("request"))
        assertEquals("sub-1", obj.getString("id"))
        val ev = obj.getJSONObject("events")
        val twitch = ev.getJSONArray("Twitch")
        assertEquals(3, twitch.length())
        assertEquals("Follow", twitch.getString(0))
        assertEquals("Sub", twitch.getString(1))
        assertEquals("Raid", twitch.getString(2))
    }

    @Test
    fun `buildGetActionsRequest carries id only`() {
        val obj = JSONObject(StreamerBotProtocol.buildGetActionsRequest("ga-1"))
        assertEquals("GetActions", obj.getString("request"))
        assertEquals("ga-1", obj.getString("id"))
        assertFalse(obj.has("action"))
    }

    @Test
    fun `buildDoActionRequest omits args when null`() {
        val obj = JSONObject(StreamerBotProtocol.buildDoActionRequest("do-1", "guid-123"))
        assertEquals("DoAction", obj.getString("request"))
        assertEquals("do-1", obj.getString("id"))
        assertEquals("guid-123", obj.getJSONObject("action").getString("id"))
        assertFalse("args must be absent when not provided", obj.has("args"))
    }

    @Test
    fun `buildDoActionRequest omits args when empty map`() {
        val obj = JSONObject(
            StreamerBotProtocol.buildDoActionRequest("do-1", "guid-123", emptyMap()),
        )
        assertFalse("empty args treated as absent", obj.has("args"))
    }

    @Test
    fun `buildDoActionRequest serializes args when present`() {
        val obj = JSONObject(
            StreamerBotProtocol.buildDoActionRequest(
                id = "do-2",
                actionId = "guid-456",
                args = mapOf("user" to "jay", "amount" to "42"),
            ),
        )
        val args = obj.getJSONObject("args")
        assertEquals("jay", args.getString("user"))
        assertEquals("42", args.getString("amount"))
    }

    @Test
    fun `default Twitch events list covers expected categories`() {
        val ev = StreamerBotProtocol.DEFAULT_TWITCH_EVENTS
        assertTrue("Follow", "Follow" in ev)
        assertTrue("Sub", "Sub" in ev)
        assertTrue("ReSub", "ReSub" in ev)
        assertTrue("GiftSub", "GiftSub" in ev)
        assertTrue("GiftBomb", "GiftBomb" in ev)
        assertTrue("Cheer", "Cheer" in ev)
        assertTrue("Raid", "Raid" in ev)
    }
}

/**
 * Pure JVM unit tests for [StreamerBotEvent.parse] and
 * [StreamerBotAction.parseList].
 */
class StreamerBotEventParseTest {

    @Test
    fun `parses a Twitch Follow event`() {
        val json = JSONObject(
            """
            {
              "timestamp": "2026-05-31T12:00:00Z",
              "event": { "source": "Twitch", "type": "Follow" },
              "data": { "user_name": "ronni", "userId": "1337" }
            }
            """.trimIndent(),
        )
        val ev = StreamerBotEvent.parse(json)
        assertNotNull(ev)
        ev!!
        assertEquals("Twitch", ev.source)
        assertEquals("Follow", ev.type)
        assertEquals("ronni", ev.userName)
    }

    @Test
    fun `parses a Twitch Cheer event with bits`() {
        val json = JSONObject(
            """
            {
              "event": { "source": "Twitch", "type": "Cheer" },
              "data": { "userName": "alice", "bits": 500 }
            }
            """.trimIndent(),
        )
        val ev = StreamerBotEvent.parse(json)!!
        assertEquals("alice", ev.userName)
        assertEquals(500, ev.bits)
        assertNull(ev.months)
    }

    @Test
    fun `parses a Twitch Raid event with viewers`() {
        val json = JSONObject(
            """
            {
              "event": { "source": "Twitch", "type": "Raid" },
              "data": { "user_name": "bob", "viewers": 42 }
            }
            """.trimIndent(),
        )
        val ev = StreamerBotEvent.parse(json)!!
        assertEquals("bob", ev.userName)
        assertEquals(42, ev.viewers)
    }

    @Test
    fun `returns null when event envelope is missing`() {
        val json = JSONObject("""{ "status": "ok", "id": "x" }""")
        assertNull(StreamerBotEvent.parse(json))
    }

    @Test
    fun `returns null when event source or type is missing`() {
        val noSource = JSONObject("""{ "event": { "type": "Follow" } }""")
        val noType = JSONObject("""{ "event": { "source": "Twitch" } }""")
        assertNull(StreamerBotEvent.parse(noSource))
        assertNull(StreamerBotEvent.parse(noType))
    }

    @Test
    fun `defaults data to empty object when missing`() {
        val json = JSONObject(
            """{ "event": { "source": "Twitch", "type": "Follow" } }""",
        )
        val ev = StreamerBotEvent.parse(json)!!
        assertEquals(0, ev.data.length())
        assertNull(ev.userName)
    }
}

class StreamerBotActionParseTest {

    @Test
    fun `parses a GetActions response with multiple actions`() {
        val resp = JSONObject(
            """
            {
              "status": "ok",
              "id": "ga-1",
              "count": 2,
              "actions": [
                { "id": "guid-1", "name": "Switch Scene", "enabled": true, "group": "Scenes" },
                { "id": "guid-2", "name": "Play Sound",   "enabled": false }
              ]
            }
            """.trimIndent(),
        )
        val list = StreamerBotAction.parseList(resp)
        assertEquals(2, list.size)
        val first = list[0]
        assertEquals("guid-1", first.id)
        assertEquals("Switch Scene", first.name)
        assertTrue(first.enabled)
        assertEquals("Scenes", first.group)
        val second = list[1]
        assertFalse(second.enabled)
        assertNull(second.group)
    }

    @Test
    fun `skips malformed action entries silently`() {
        val resp = JSONObject(
            """
            {
              "actions": [
                { "id": "ok", "name": "Good" },
                { "name": "Missing id" },
                { "id": "no-name" },
                { "id": "g3", "name": "Also Good" }
              ]
            }
            """.trimIndent(),
        )
        val list = StreamerBotAction.parseList(resp)
        assertEquals(2, list.size)
        assertEquals("ok", list[0].id)
        assertEquals("g3", list[1].id)
    }

    @Test
    fun `returns empty list when actions key is missing`() {
        val resp = JSONObject("""{ "status": "ok", "id": "ga-1" }""")
        assertTrue(StreamerBotAction.parseList(resp).isEmpty())
    }

    @Test
    fun `defaults enabled to true when key is missing`() {
        val resp = JSONObject(
            """{ "actions": [ { "id": "g1", "name": "X" } ] }""",
        )
        val list = StreamerBotAction.parseList(resp)
        assertTrue(list.single().enabled)
    }
}
