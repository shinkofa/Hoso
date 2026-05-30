package com.theermite.hoso.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IRC line parser unit tests — pure JVM, no Android stubs needed.
 *
 * Lines marked "verbatim doc" come from the official Twitch IRC docs
 * (https://dev.twitch.tv/docs/irc/tags/, dev.twitch.tv/docs/irc/example-parser/)
 * and are kept byte-for-byte to detect spec drift.
 */
class IrcMessageParseTest {

    @Test
    fun `parses a PRIVMSG with full tags (verbatim doc)`() {
        val line = "@badge-info=;badges=turbo/1;color=#0D4200;display-name=ronni;" +
            "emotes=25:0-4,12-16/1902:6-10;id=b34ccfc7-4977-403a-8a94-33c6bac34fb8;" +
            "mod=0;room-id=1337;subscriber=0;tmi-sent-ts=1507246572675;turbo=1;" +
            "user-id=1337;user-type=global_mod " +
            ":ronni!ronni@ronni.tmi.twitch.tv PRIVMSG #ronni :Kappa Keepo Kappa"

        val msg = IrcMessage.parse(line)!!

        assertEquals("PRIVMSG", msg.command)
        assertEquals(listOf("#ronni"), msg.params)
        assertEquals("ronni", msg.channel)
        assertEquals("Kappa Keepo Kappa", msg.text)
        assertEquals("ronni!ronni@ronni.tmi.twitch.tv", msg.prefix)
        assertEquals("ronni", msg.displayName)
        assertEquals("#0D4200", msg.color)
        assertEquals("1337", msg.userId)
        assertEquals("b34ccfc7-4977-403a-8a94-33c6bac34fb8", msg.messageId)
        assertEquals(1507246572675L, msg.sentAt)
        assertEquals("turbo/1", msg.tags["badges"])
        assertEquals("", msg.tags["badge-info"])
    }

    @Test
    fun `parses a PING with trailing payload`() {
        val msg = IrcMessage.parse("PING :tmi.twitch.tv")!!
        assertEquals("PING", msg.command)
        assertEquals("tmi.twitch.tv", msg.trailing)
        assertTrue(msg.tags.isEmpty())
        assertNull(msg.prefix)
    }

    @Test
    fun `parses a JOIN line (no tags, no trailing)`() {
        val msg = IrcMessage.parse(":justinfan42!justinfan42@justinfan42.tmi.twitch.tv JOIN #foo")!!
        assertEquals("JOIN", msg.command)
        assertEquals(listOf("#foo"), msg.params)
        assertEquals("foo", msg.channel)
        assertEquals("justinfan42", msg.displayName) // fallback to nick from prefix
    }

    @Test
    fun `parses a numeric 001 welcome`() {
        val msg = IrcMessage.parse(":tmi.twitch.tv 001 justinfan42 :Welcome, GLHF!")!!
        assertEquals("001", msg.command)
        assertEquals(listOf("justinfan42"), msg.params)
        assertEquals("Welcome, GLHF!", msg.trailing)
    }

    @Test
    fun `parses CLEARCHAT (timeout) with target user`() {
        val line = "@ban-duration=60;target-user-id=123 :tmi.twitch.tv CLEARCHAT #ronni :baduser"
        val msg = IrcMessage.parse(line)!!
        assertEquals("CLEARCHAT", msg.command)
        assertEquals("baduser", msg.trailing)
        assertEquals("60", msg.tags["ban-duration"])
        assertEquals("123", msg.tags["target-user-id"])
        assertEquals("ronni", msg.channel)
    }

    @Test
    fun `parses CLEARMSG with target-msg-id`() {
        val line = "@login=ronni;target-msg-id=abc-uuid :tmi.twitch.tv CLEARMSG #ronni :HeyGuys"
        val msg = IrcMessage.parse(line)!!
        assertEquals("CLEARMSG", msg.command)
        assertEquals("abc-uuid", msg.tags["target-msg-id"])
        assertEquals("HeyGuys", msg.trailing)
    }

    @Test
    fun `display-name falls back to nick when tag is empty`() {
        val line = "@display-name=;color= :alice!alice@alice.tmi.twitch.tv PRIVMSG #foo :hi"
        val msg = IrcMessage.parse(line)!!
        assertEquals("alice", msg.displayName)
        assertNull(msg.color)
    }

    @Test
    fun `unescapes IRCv3 tag values`() {
        // \s → space, \: → ;, \\ → \, \r \n
        val line = "@key=hello\\sworld\\:test\\\\path\\r\\n PRIVMSG #x :y"
        val msg = IrcMessage.parse(line)!!
        assertEquals("hello world;test\\path\r\n", msg.tags["key"])
    }

    @Test
    fun `handles message text containing a colon (only first leading colon is trailing marker)`() {
        val line = ":bob!bob@bob.tmi.twitch.tv PRIVMSG #foo :see https://twitch.tv/foo:42 yo"
        val msg = IrcMessage.parse(line)!!
        assertEquals("see https://twitch.tv/foo:42 yo", msg.text)
    }

    @Test
    fun `parses message without tags`() {
        val msg = IrcMessage.parse(":bob!bob@bob.tmi.twitch.tv PRIVMSG #foo :plain text")!!
        assertEquals("plain text", msg.text)
        assertTrue(msg.tags.isEmpty())
    }

    @Test
    fun `returns null on blank line`() {
        assertNull(IrcMessage.parse(""))
        assertNull(IrcMessage.parse("   "))
        assertNull(IrcMessage.parse("\r\n"))
    }

    @Test
    fun `trims trailing CRLF`() {
        val msg = IrcMessage.parse("PING :foo\r\n")!!
        assertEquals("foo", msg.trailing)
    }

    @Test
    fun `command is uppercased even if server lowercased it`() {
        val msg = IrcMessage.parse(":x!x@x privmsg #c :body")!!
        assertEquals("PRIVMSG", msg.command)
    }

    @Test
    fun `first-msg=1 sets isFirstMessage true`() {
        val line = "@first-msg=1 :n!n@n PRIVMSG #c :hi"
        val msg = IrcMessage.parse(line)!!
        assertTrue(msg.isFirstMessage)
    }

    @Test
    fun `first-msg=0 sets isFirstMessage false`() {
        val line = "@first-msg=0 :n!n@n PRIVMSG #c :hi"
        val msg = IrcMessage.parse(line)!!
        assertEquals(false, msg.isFirstMessage)
    }

    @Test
    fun `multiple middle params preserved in order`() {
        // CAP ACK : :tmi.twitch.tv CAP * ACK :twitch.tv/tags twitch.tv/commands
        val msg = IrcMessage.parse(":tmi.twitch.tv CAP * ACK :twitch.tv/tags twitch.tv/commands")!!
        assertEquals("CAP", msg.command)
        assertEquals(listOf("*", "ACK"), msg.params)
        assertEquals("twitch.tv/tags twitch.tv/commands", msg.trailing)
    }
}

class TwitchIrcClientBackoffTest {

    @Test
    fun `backoff stays within bounded window for early attempts`() {
        // 1s base ≤ result ≤ 1s + 500ms jitter
        val d0 = TwitchIrcClient.backoffDelay(0)
        assertTrue("got $d0", d0 in 1_000L..1_500L)
    }

    @Test
    fun `backoff doubles up to attempt 5`() {
        val d3 = TwitchIrcClient.backoffDelay(3)
        // 2^3 = 8s base + jitter < 500ms
        assertTrue("got $d3", d3 in 8_000L..8_500L)
    }

    @Test
    fun `backoff caps at 30s for high attempts`() {
        val d100 = TwitchIrcClient.backoffDelay(100)
        // Even huge attempt counts clamp to 30s + jitter
        assertTrue("got $d100", d100 in 30_000L..30_500L)
    }
}
