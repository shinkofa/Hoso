package com.theermite.hoso.streamerbot

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the reconnect backoff schedule used by
 * [StreamerBotClient]. Mirrors the exponential-with-jitter shape of
 * [com.theermite.hoso.chat.TwitchIrcClient.backoffDelay] but lives on
 * its own so the two clients can diverge without coupling.
 */
class StreamerBotBackoffTest {

    @Test
    fun `backoff is monotonic in the doubling range`() {
        // attempt 0..5 → base 1, 2, 4, 8, 16, 32 (s). Compare floors
        // (drop jitter) to assert the doubling shape.
        val floors = (0..5).map { StreamerBotClient.backoffDelay(it) - 500 }
        for (i in 1 until floors.size) {
            assertTrue(
                "floor[${i - 1}]=${floors[i - 1]} should be <= floor[$i]=${floors[i]}",
                floors[i - 1] <= floors[i],
            )
        }
    }

    @Test
    fun `backoff caps at 30 seconds plus jitter`() {
        // attempt 5 → base 32s, clamped to 30s + up to 500ms jitter.
        // attempt 100 → still capped.
        for (attempt in 5..100) {
            val d = StreamerBotClient.backoffDelay(attempt)
            assertTrue("attempt=$attempt delay=$d > 30_500", d <= 30_500L)
            assertTrue("attempt=$attempt delay=$d < 30_000", d >= 30_000L)
        }
    }

    @Test
    fun `backoff never returns a negative value`() {
        for (attempt in 0..100) {
            assertTrue(StreamerBotClient.backoffDelay(attempt) >= 0L)
        }
    }
}
