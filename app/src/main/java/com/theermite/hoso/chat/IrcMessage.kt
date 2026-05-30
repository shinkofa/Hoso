package com.theermite.hoso.chat

/**
 * Parsed IRCv3 line. Twitch chat messages travel over a vanilla IRC link
 * with the `twitch.tv/tags` capability enabled — every PRIVMSG carries
 * a tag bag (display-name, color, badges, emotes, message id, etc.).
 *
 * The data class is intentionally protocol-neutral: it just captures
 * "what the line says". Twitch semantics (broadcaster role, emote URL,
 * fallback name color, etc.) are derived by the UI layer.
 *
 * IRCv3 spec reference: https://ircv3.net/specs/extensions/message-tags
 * Twitch tags reference: https://dev.twitch.tv/docs/irc/tags/
 */
data class IrcMessage(
    /** IRCv3 message tags — empty map if the line had no `@`-prefix bag. */
    val tags: Map<String, String> = emptyMap(),
    /** Raw IRC prefix without the leading `:` — typically `nick!user@host`. */
    val prefix: String? = null,
    /** Uppercased IRC command (`PRIVMSG`, `PING`, `JOIN`, `001`, …). */
    val command: String,
    /** Middle params (positional, before the trailing param if any). */
    val params: List<String> = emptyList(),
    /** The `:trailing` portion if present — for PRIVMSG this is the chat text. */
    val trailing: String? = null,
) {

    /**
     * Display name resolved with Twitch fallback: prefer the `display-name`
     * tag (capitalized version chosen by the viewer), then fall back to the
     * lowercase nick from the IRC prefix.
     */
    val displayName: String?
        get() = tags["display-name"]?.takeIf { it.isNotBlank() }
            ?: prefix?.substringBefore('!')?.takeIf { it.isNotBlank() }

    /** Hex color `#RRGGBB` chosen by the viewer, or null → UI picks fallback. */
    val color: String? get() = tags["color"]?.takeIf { it.isNotBlank() }

    /** Twitch numeric user id (stable across name changes). */
    val userId: String? get() = tags["user-id"]

    /** UUID of the message (used by CLEARMSG to target a single line). */
    val messageId: String? get() = tags["id"]

    /** Channel name without the leading `#`. */
    val channel: String? get() = params.firstOrNull()?.removePrefix("#")

    /** Convenience alias — PRIVMSG body lives in `trailing`. */
    val text: String? get() = trailing

    /** Twitch server-side send timestamp in epoch milliseconds, if tagged. */
    val sentAt: Long? get() = tags["tmi-sent-ts"]?.toLongOrNull()

    /** `1` for the very first message of a viewer in this channel. */
    val isFirstMessage: Boolean get() = tags["first-msg"] == "1"

    companion object {

        /**
         * Parse one CRLF-terminated IRC line into an [IrcMessage].
         *
         * Returns null for empty / blank lines so callers can ignore them
         * without try/catch. Malformed lines are best-effort parsed —
         * a missing prefix or missing trailing simply yields an [IrcMessage]
         * with those fields null.
         *
         * The parser follows the IRCv3 grammar:
         *
         *   [`@` tags SPACE] [`:` prefix SPACE] command [SPACE middle]* [SPACE `:` trailing]
         *
         * Tag values are unescaped per the IRCv3 message-tags spec
         * (`\:` → `;`, `\s` → space, `\\` → `\`, `\r` → CR, `\n` → LF).
         */
        fun parse(rawLine: String): IrcMessage? {
            var rest = rawLine.trimEnd('\r', '\n')
            if (rest.isBlank()) return null

            val tags: Map<String, String> = if (rest.startsWith('@')) {
                val space = rest.indexOf(' ')
                if (space < 0) return null
                val tagsPart = rest.substring(1, space)
                rest = rest.substring(space + 1).trimStart()
                parseTags(tagsPart)
            } else {
                emptyMap()
            }

            val prefix: String? = if (rest.startsWith(':')) {
                val space = rest.indexOf(' ')
                if (space < 0) return null
                val p = rest.substring(1, space)
                rest = rest.substring(space + 1).trimStart()
                p
            } else null

            if (rest.isEmpty()) return null

            val cmdEnd = rest.indexOf(' ')
            val command: String
            val paramsRaw: String
            if (cmdEnd < 0) {
                command = rest
                paramsRaw = ""
            } else {
                command = rest.substring(0, cmdEnd)
                paramsRaw = rest.substring(cmdEnd + 1)
            }

            val params = mutableListOf<String>()
            var trailing: String? = null
            var p = paramsRaw
            while (p.isNotEmpty()) {
                if (p.startsWith(':')) {
                    trailing = p.substring(1)
                    break
                }
                val sp = p.indexOf(' ')
                if (sp < 0) {
                    params.add(p)
                    break
                }
                params.add(p.substring(0, sp))
                p = p.substring(sp + 1).trimStart()
            }

            return IrcMessage(
                tags = tags,
                prefix = prefix,
                command = command.uppercase(),
                params = params.toList(),
                trailing = trailing,
            )
        }

        private fun parseTags(s: String): Map<String, String> {
            if (s.isEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (pair in s.split(';')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    out[pair] = ""
                } else {
                    out[pair.substring(0, eq)] = unescapeTagValue(pair.substring(eq + 1))
                }
            }
            return out
        }

        /**
         * IRCv3 message-tags escape decoding. Unknown escape sequences are
         * left as the literal following character (per spec — clients must
         * not error on unknown escapes).
         */
        private fun unescapeTagValue(v: String): String {
            if ('\\' !in v) return v
            val sb = StringBuilder(v.length)
            var i = 0
            while (i < v.length) {
                val c = v[i]
                if (c == '\\' && i + 1 < v.length) {
                    when (v[i + 1]) {
                        ':' -> sb.append(';')
                        's' -> sb.append(' ')
                        '\\' -> sb.append('\\')
                        'r' -> sb.append('\r')
                        'n' -> sb.append('\n')
                        else -> sb.append(v[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
    }
}
