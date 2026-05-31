package com.theermite.hoso.streamerbot

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * WebSocket client for Streamer.bot's WebSocket Server (v0.2.5+).
 *
 * Protocol summary (full ref: https://docs.streamer.bot/api/websocket):
 *
 *   1. Connect to `ws://host:port/`.
 *   2. Server sends a Hello frame. If auth is enabled, the frame includes
 *      `info.authentication.{salt,challenge}` and we MUST send an
 *      Authenticate request with `b64(SHA256(b64(SHA256(pw+salt)) + chal))`.
 *      If auth is disabled, the Hello has no `authentication` block and we
 *      skip straight to Subscribe.
 *   3. Send Subscribe with the Twitch event categories of interest, plus
 *      a GetActions request to hydrate the actions list.
 *   4. Stream incoming frames:
 *        - `event` envelope → [onEvent]
 *        - `actions` array (response to GetActions) → [onActions]
 *        - other status/id responses → logged at debug
 *
 * Threading:
 *   - One [CoroutineScope] backed by SupervisorJob + Dispatchers.IO.
 *   - The connectLoop coroutine owns the lifecycle; the OkHttp
 *     WebSocketListener fires on OkHttp's dispatcher and just toggles
 *     a [CompletableDeferred] when the socket closes.
 *   - All callbacks ([onState], [onEvent], [onActions]) are invoked from
 *     either the IO scope or OkHttp's dispatcher — the consumer is
 *     responsible for forwarding to the main thread if needed.
 *
 * Reconnect: exponential backoff 1s → 30s with jitter, retries forever.
 * Attempt counter resets after [STABLE_CONNECTION_MS] of stable connection.
 *
 * Heartbeat: OkHttp's built-in WebSocket ping handles keep-alive (30s).
 * Streamer.bot replies with pongs automatically; OkHttp closes the socket
 * on missed pong, which fires onFailure → reconnect.
 *
 * The class is single-use: call [start] to connect, [stop] to disconnect.
 * After [stop], create a new instance to reconnect.
 */
class StreamerBotClient(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val onState: (State) -> Unit = {},
    private val onEvent: (StreamerBotEvent) -> Unit = {},
    private val onActions: (List<StreamerBotAction>) -> Unit = {},
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

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(PING_INTERVAL_S, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket = no read timeout
            .build()
    }

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var currentState: State = State.IDLE
    @Volatile private var connectedSince: Long = 0L
    private var reconnectAttempt: Int = 0

    // Request id allocator. Each outbound request gets a monotonic id so
    // we can correlate responses (GetActions response → onActions).
    private val nextRequestId = AtomicLong(1L)
    @Volatile private var pendingGetActionsId: String? = null
    @Volatile private var pendingAuthenticateId: String? = null

    /** Start the client. Idempotent — no-op if already running. */
    fun start() {
        if (driverJob?.isActive == true) return
        if (host.isBlank() || port !in 1..65_535) {
            Log.w(TAG, "start() ignored — invalid host/port: '$host':$port")
            emitState(State.STOPPED)
            return
        }
        driverJob = scope.launch { connectLoop() }
    }

    /** Cancel all coroutines and close the socket. Idempotent. */
    fun stop() {
        emitState(State.STOPPED)
        runCatching { webSocket?.close(NORMAL_CLOSURE, "client stop") }
        webSocket = null
        scope.cancel()
    }

    /**
     * Fire a DoAction request for [actionId]. No-op if not connected.
     * The Streamer.bot side does not require a response, but the request
     * carries an id for traceability in case the server logs it.
     */
    fun sendAction(actionId: String, args: Map<String, String>? = null) {
        val ws = webSocket
        if (ws == null || currentState != State.CONNECTED) {
            Log.w(TAG, "sendAction($actionId) ignored — not connected")
            return
        }
        val id = "do-${nextRequestId.getAndIncrement()}"
        val frame = StreamerBotProtocol.buildDoActionRequest(id, actionId, args)
        runCatching { ws.send(frame) }
            .onFailure { Log.w(TAG, "sendAction send failed", it) }
    }

    // ── connect loop ─────────────────────────────────────────────────

    private suspend fun connectLoop() {
        while (scope.isActive) {
            try {
                emitState(State.CONNECTING)
                val terminated = CompletableDeferred<Unit>()
                openWebSocket(terminated)
                connectedSince = System.currentTimeMillis()
                terminated.await()
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

    private fun openWebSocket(terminated: CompletableDeferred<Unit>) {
        val url = "ws://$host:$port/"
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(
            request,
            ProtocolListener(terminated),
        )
    }

    private fun cleanupConnection() {
        runCatching { webSocket?.cancel() }
        webSocket = null
        pendingGetActionsId = null
        pendingAuthenticateId = null
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

    // ── OkHttp listener ──────────────────────────────────────────────

    /**
     * OkHttp WebSocketListener that drives the Streamer.bot protocol
     * handshake and dispatches incoming frames. Errors and closures
     * complete [terminated], which wakes [connectLoop] to reconnect.
     */
    private inner class ProtocolListener(
        private val terminated: CompletableDeferred<Unit>,
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WS open — waiting for Hello frame")
            // Streamer.bot pushes a Hello frame unsolicited; do nothing here.
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            routeFrame(webSocket, json)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WS closing code=$code reason=$reason")
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WS closed code=$code reason=$reason")
            terminated.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WS failure (will reconnect): ${t.message}")
            terminated.complete(Unit)
        }
    }

    /**
     * Top-level inbound frame router. Visible-for-internal-use only;
     * the OkHttp listener delegates here so the protocol logic is
     * reachable from one place.
     */
    private fun routeFrame(ws: WebSocket, json: JSONObject) {
        // 1) Hello frame — unsolicited on open, contains server info and
        //    (when auth is enabled) the auth challenge.
        val info = json.optJSONObject("info")
        if (info != null) {
            handleHello(ws, info)
            return
        }

        // 2) Event envelope — push from server (Twitch follow/sub/cheer…).
        val event = StreamerBotEvent.parse(json)
        if (event != null) {
            dispatchEvent(event)
            return
        }

        // 3) Response to one of our requests — correlate by `id`.
        val id = json.optString("id", "")
        if (id.isEmpty()) {
            return // unrouted; ignore silently
        }
        when (id) {
            pendingAuthenticateId -> handleAuthResponse(ws, json)
            pendingGetActionsId -> handleActionsResponse(json)
            else -> { /* DoAction / Subscribe ack — nothing to do */ }
        }
    }

    private fun handleHello(ws: WebSocket, info: JSONObject) {
        val auth = info.optJSONObject("authentication")
        if (auth == null) {
            // Auth disabled — go straight to subscribe + actions.
            Log.i(TAG, "Hello received — auth disabled, subscribing")
            subscribeAndFetchActions(ws)
            return
        }

        // Auth required. If we have no password configured, we can't
        // proceed — close the socket so connectLoop backs off and the
        // user has a chance to enter a password.
        if (password.isEmpty()) {
            Log.w(TAG, "Auth required but no password configured — closing")
            runCatching { ws.close(POLICY_VIOLATION, "no password") }
            return
        }

        val salt = auth.optString("salt", "")
        val challenge = auth.optString("challenge", "")
        if (salt.isEmpty() || challenge.isEmpty()) {
            Log.w(TAG, "Malformed Hello auth block — closing")
            runCatching { ws.close(POLICY_VIOLATION, "bad auth") }
            return
        }

        emitState(State.AUTHENTICATING)
        val token = StreamerBotProtocol.computeAuthentication(
            password = password,
            salt = salt,
            challenge = challenge,
        )
        val id = "auth-${nextRequestId.getAndIncrement()}"
        pendingAuthenticateId = id
        runCatching {
            ws.send(StreamerBotProtocol.buildAuthenticateRequest(id, token))
        }.onFailure { Log.w(TAG, "Authenticate send failed", it) }
    }

    private fun handleAuthResponse(ws: WebSocket, json: JSONObject) {
        pendingAuthenticateId = null
        val status = json.optString("status", "")
        if (status.equals("ok", ignoreCase = true)) {
            Log.i(TAG, "Auth ok — subscribing")
            subscribeAndFetchActions(ws)
        } else {
            Log.w(TAG, "Auth failed: $json — closing")
            runCatching { ws.close(POLICY_VIOLATION, "auth failed") }
        }
    }

    private fun subscribeAndFetchActions(ws: WebSocket) {
        emitState(State.CONNECTED)
        // Subscribe to default Twitch events.
        val subId = "sub-${nextRequestId.getAndIncrement()}"
        val subFrame = StreamerBotProtocol.buildSubscribeRequest(
            id = subId,
            events = mapOf("Twitch" to StreamerBotProtocol.DEFAULT_TWITCH_EVENTS),
        )
        runCatching { ws.send(subFrame) }
            .onFailure { Log.w(TAG, "Subscribe send failed", it) }

        // Fetch actions list for the overlay UI.
        val gaId = "ga-${nextRequestId.getAndIncrement()}"
        pendingGetActionsId = gaId
        val gaFrame = StreamerBotProtocol.buildGetActionsRequest(gaId)
        runCatching { ws.send(gaFrame) }
            .onFailure { Log.w(TAG, "GetActions send failed", it) }
    }

    private fun handleActionsResponse(json: JSONObject) {
        pendingGetActionsId = null
        val list = StreamerBotAction.parseList(json)
        try {
            onActions(list)
        } catch (t: Throwable) {
            Log.w(TAG, "onActions threw", t)
        }
    }

    private fun dispatchEvent(event: StreamerBotEvent) {
        try {
            onEvent(event)
        } catch (t: Throwable) {
            Log.w(TAG, "onEvent threw", t)
        }
    }

    companion object {
        private const val TAG = "StreamerBotClient"

        private const val PING_INTERVAL_S = 30L
        private const val STABLE_CONNECTION_MS = 60_000L

        // RFC 6455 WebSocket close codes.
        private const val NORMAL_CLOSURE = 1000
        private const val POLICY_VIOLATION = 1008

        internal fun backoffDelay(attempt: Int): Long {
            val capped = attempt.coerceIn(0, 5)
            val base = (1L shl capped) * 1000L          // 1, 2, 4, 8, 16, 32 s
            val ceil = base.coerceAtMost(30_000L)
            val jitter = Random.nextLong(0, 500)
            return ceil + jitter
        }
    }
}
