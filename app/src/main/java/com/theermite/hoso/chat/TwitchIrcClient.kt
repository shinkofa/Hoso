package com.theermite.hoso.chat

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * Read-only Twitch IRC client over a hand-written TLS socket.
 *
 * Logs in anonymously via the well-known `justinfan<digits>` convention.
 * No OAuth scope is required — Twitch IRC is publicly readable by anyone
 * who knows the channel name.
 *
 * Threading model:
 *   - One [CoroutineScope] backed by [SupervisorJob] + [Dispatchers.IO].
 *   - One coroutine per lifecycle: connectLoop holds reconnect logic,
 *     readLoop drains the socket, heartbeatLoop sends client-side PINGs.
 *   - All callbacks are dispatched from the IO scope — the consumer is
 *     responsible for forwarding to the main thread if needed.
 *
 * Reconnect policy:
 *   - Exponential backoff 1s → 30s with jitter, retries forever.
 *   - Attempt counter resets after 60s of stable connection.
 *   - On reconnect, CAP REQ + JOIN are replayed (Twitch does not auto-rejoin).
 *
 * Heartbeat policy:
 *   - Twitch server PING fires every ~5 min; client PONG is replied automatically.
 *   - We also send a client PING every 4 min and consider the link dead if no
 *     PONG arrives within 10s — this detects silent socket death on mobile
 *     networks (cellular handover, NAT timeout) that TCP keepalive misses.
 *
 * The class is deliberately not a [android.app.Service] — the lifecycle is
 * owned by whatever component is hosting the chat overlay. Call [start] to
 * connect, [stop] to disconnect; both are idempotent.
 */
class TwitchIrcClient(
    private val onMessage: (IrcMessage) -> Unit,
    private val onState: (State) -> Unit = {},
) {

    enum class State {
        IDLE,
        CONNECTING,
        AUTHENTICATING,
        CONNECTED,
        RECONNECTING,
        STOPPED,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var driverJob: Job? = null

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var reader: BufferedReader? = null

    @Volatile private var channel: String? = null
    @Volatile private var lastPongAt: Long = 0L
    @Volatile private var connectedSince: Long = 0L
    private var reconnectAttempt: Int = 0

    @Volatile private var currentState: State = State.IDLE

    /**
     * Start the client and join [channelName]. Lowercased, leading `#` stripped.
     * Calling start twice while already running is a no-op (channel switch is
     * not supported in V1 — call [stop] first).
     */
    fun start(channelName: String) {
        if (driverJob?.isActive == true) return
        val ch = channelName.lowercase().trim().removePrefix("#")
        if (ch.isEmpty()) return
        channel = ch
        driverJob = scope.launch { connectLoop(ch) }
    }

    /**
     * Cancel all coroutines and close the underlying socket. Idempotent.
     * The instance is single-use after stop — create a new [TwitchIrcClient]
     * to reconnect (this matches the lifecycle of the host Service).
     */
    fun stop() {
        emitState(State.STOPPED)
        runCatching { socket?.close() }
        scope.cancel()
    }

    private suspend fun connectLoop(ch: String) {
        while (scope.isActive) {
            try {
                emitState(State.CONNECTING)
                connect(ch)
                connectedSince = System.currentTimeMillis()
                readLoop()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.w(TAG, "connectLoop error", t)
            } finally {
                cleanupConnection()
            }
            if (!scope.isActive) break

            if (System.currentTimeMillis() - connectedSince > STABLE_CONNECTION_MS) {
                reconnectAttempt = 0
            }
            emitState(State.RECONNECTING)
            val wait = backoffDelay(reconnectAttempt)
            reconnectAttempt++
            delay(wait)
        }
        emitState(State.STOPPED)
    }

    private suspend fun connect(ch: String) = withContext(Dispatchers.IO) {
        val sf = SSLSocketFactory.getDefault() as SSLSocketFactory
        val s = sf.createSocket(IRC_HOST, IRC_PORT) as SSLSocket
        s.soTimeout = 0
        s.keepAlive = true
        socket = s
        writer = BufferedWriter(OutputStreamWriter(s.outputStream, Charsets.UTF_8))
        reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))

        emitState(State.AUTHENTICATING)
        val nick = "justinfan${Random.nextInt(10_000, 99_999)}"
        // PASS first — historical workaround for Twitch server quirks
        // even on anonymous login. Value is arbitrary by convention.
        sendRaw("PASS SCHMOOPIIE")
        sendRaw("NICK $nick")
        sendRaw("CAP REQ :twitch.tv/tags twitch.tv/commands")
        sendRaw("JOIN #$ch")
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val r = reader ?: return@withContext
        val heartbeat = scope.launch { heartbeatLoop() }
        try {
            while (scope.isActive) {
                val line = r.readLine() ?: break
                handleLine(line)
            }
        } finally {
            heartbeat.cancel()
        }
    }

    private suspend fun heartbeatLoop() {
        while (scope.isActive) {
            delay(CLIENT_PING_INTERVAL_MS)
            val pingSentAt = System.currentTimeMillis()
            sendRaw("PING :hoso-keepalive")
            delay(PING_TIMEOUT_MS)
            if (lastPongAt < pingSentAt) {
                Log.w(TAG, "heartbeat PONG timeout — forcing reconnect")
                runCatching { socket?.close() }
                break
            }
        }
    }

    private fun handleLine(line: String) {
        val msg = IrcMessage.parse(line) ?: return
        when (msg.command) {
            "PING" -> sendRaw("PONG :${msg.trailing ?: "tmi.twitch.tv"}")
            "PONG" -> lastPongAt = System.currentTimeMillis()
            "001" -> { /* welcome — auth accepted, JOIN still pending */ }
            "JOIN" -> emitState(State.CONNECTED)
            "PRIVMSG",
            "CLEARCHAT",
            "CLEARMSG",
            "USERNOTICE",
            "NOTICE" -> dispatchSafely(msg)
            else -> { /* ignore CAP ACK, ROOMSTATE, USERSTATE, 353/366… in V1 */ }
        }
    }

    private fun dispatchSafely(msg: IrcMessage) {
        try {
            onMessage(msg)
        } catch (t: Throwable) {
            Log.w(TAG, "onMessage threw", t)
        }
    }

    private fun emitState(s: State) {
        if (currentState == s) return
        currentState = s
        try {
            onState(s)
        } catch (t: Throwable) {
            Log.w(TAG, "onState threw", t)
        }
    }

    private fun sendRaw(cmd: String) {
        val w = writer ?: return
        runCatching {
            w.write(cmd)
            w.write("\r\n")
            w.flush()
        }.onFailure { Log.w(TAG, "sendRaw failed for: $cmd", it) }
    }

    private fun cleanupConnection() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }

    companion object {
        private const val TAG = "TwitchIrc"
        private const val IRC_HOST = "irc.chat.twitch.tv"
        private const val IRC_PORT = 6697

        private const val CLIENT_PING_INTERVAL_MS = 4L * 60L * 1000L
        private const val PING_TIMEOUT_MS = 10_000L
        private const val STABLE_CONNECTION_MS = 60_000L

        internal fun backoffDelay(attempt: Int): Long {
            val capped = attempt.coerceIn(0, 5)
            val base = (1L shl capped) * 1000L          // 1, 2, 4, 8, 16, 32 s
            val ceil = base.coerceAtMost(30_000L)
            val jitter = Random.nextLong(0, 500)
            return ceil + jitter
        }
    }
}
