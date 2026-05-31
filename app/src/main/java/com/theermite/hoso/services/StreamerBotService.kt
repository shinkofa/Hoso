package com.theermite.hoso.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.theermite.hoso.R
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.streamerbot.StreamerBotAction
import com.theermite.hoso.streamerbot.StreamerBotClient
import com.theermite.hoso.streamerbot.StreamerBotEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Foreground service that owns the [StreamerBotClient] lifecycle and
 * fan-outs its state / events / actions to the rest of the app via
 * package-internal broadcasts.
 *
 * Why a separate service from [ChatBubbleService] / [OverlayService]:
 *   - The bridge to the PC (over Tailscale) must survive ColorOS /
 *     Realme aggressive background killers — only a foreground service
 *     with its own notification gets that survival guarantee.
 *   - The chat bubble already owns its own FGS for the same reason; we
 *     follow the "one concern per service" pattern instead of bolting
 *     another socket into ChatBubbleService.
 *   - Lifecycle is independent from streaming: Jay may want to test
 *     triggers from the phone without being live, or keep the bridge
 *     up while the actual stream is paused.
 *
 * Commands (incoming Intents):
 *   - [ACTION_START]      — start the client (idempotent)
 *   - [ACTION_STOP]       — stop the service entirely
 *   - [ACTION_DO_ACTION]  — fire a DoAction on the connected client.
 *                           Extras: [EXTRA_ACTION_ID] (required).
 *
 * Broadcasts (outgoing, RECEIVER_NOT_EXPORTED):
 *   - [BROADCAST_STATE_CHANGED] with [EXTRA_STATE]
 *   - [BROADCAST_ACTIONS_UPDATED] with [EXTRA_ACTIONS_JSON]
 *   - [BROADCAST_EVENT] with [EXTRA_EVENT_SOURCE], [EXTRA_EVENT_TYPE],
 *     [EXTRA_EVENT_USER], optional [EXTRA_EVENT_BITS] / [EXTRA_EVENT_VIEWERS]
 *     / [EXTRA_EVENT_MONTHS]. The OverlayService renders these as toasts.
 */
class StreamerBotService : Service() {

    private lateinit var streamConfig: StreamConfig
    private var client: StreamerBotClient? = null
    private var lastState: StreamerBotClient.State = StreamerBotClient.State.IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        streamConfig = StreamConfig(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(lastState))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DO_ACTION -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
                if (!actionId.isNullOrBlank()) {
                    client?.sendAction(actionId)
                } else {
                    Log.w(TAG, "DoAction ignored — missing EXTRA_ACTION_ID")
                }
            }
            // ACTION_START or null → ensure the client is running
            else -> ensureClientStarted()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Persist enabled=false so MainActivity reflects the off state
        // even if the user killed the service via the notification.
        streamConfig.streamerBotEnabled = false
        client?.stop()
        client = null
        super.onDestroy()
    }

    // ── Client lifecycle ─────────────────────────────────────────────

    private fun ensureClientStarted() {
        if (client != null) return
        val host = streamConfig.streamerBotHost
        val port = streamConfig.streamerBotPort
        if (host.isBlank()) {
            Log.w(TAG, "ensureClientStarted — empty host, stopping")
            stopSelf()
            return
        }
        client = StreamerBotClient(
            host = host,
            port = port,
            password = streamConfig.streamerBotPassword,
            onState = ::onClientState,
            onEvent = ::onClientEvent,
            onActions = ::onClientActions,
        ).also { it.start() }
    }

    // ── Client callbacks → broadcasts ────────────────────────────────

    private fun onClientState(state: StreamerBotClient.State) {
        lastState = state
        latestState = state
        updateNotification(state)
        sendBroadcast(
            Intent(BROADCAST_STATE_CHANGED).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATE, state.name)
            }
        )
    }

    private fun onClientEvent(event: StreamerBotEvent) {
        sendBroadcast(
            Intent(BROADCAST_EVENT).apply {
                setPackage(packageName)
                putExtra(EXTRA_EVENT_SOURCE, event.source)
                putExtra(EXTRA_EVENT_TYPE, event.type)
                event.userName?.let { putExtra(EXTRA_EVENT_USER, it) }
                event.bits?.let { putExtra(EXTRA_EVENT_BITS, it) }
                event.viewers?.let { putExtra(EXTRA_EVENT_VIEWERS, it) }
                event.months?.let { putExtra(EXTRA_EVENT_MONTHS, it) }
            }
        )
    }

    private fun onClientActions(actions: List<StreamerBotAction>) {
        val json = JSONArray()
        for (a in actions) {
            json.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("enabled", a.enabled)
                    .apply { a.group?.let { put("group", it) } }
            )
        }
        val payload = json.toString()
        latestActionsJson = payload
        sendBroadcast(
            Intent(BROADCAST_ACTIONS_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_ACTIONS_JSON, payload)
            }
        )
    }

    // ── Notification ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sb_notif_channel),
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun updateNotification(state: StreamerBotClient.State) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: StreamerBotClient.State): Notification {
        val textRes = when (state) {
            StreamerBotClient.State.CONNECTED -> R.string.sb_notif_text_connected
            StreamerBotClient.State.CONNECTING,
            StreamerBotClient.State.AUTHENTICATING -> R.string.sb_notif_text_connecting
            StreamerBotClient.State.RECONNECTING -> R.string.sb_notif_text_reconnecting
            StreamerBotClient.State.STOPPED,
            StreamerBotClient.State.IDLE -> R.string.sb_notif_text_idle
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stream)
            .setContentTitle(getString(R.string.sb_notif_title))
            .setContentText(getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "StreamerBotService"

        const val ACTION_START = "com.theermite.hoso.streamerbot.START"
        const val ACTION_STOP = "com.theermite.hoso.streamerbot.STOP"
        const val ACTION_DO_ACTION = "com.theermite.hoso.streamerbot.DO_ACTION"
        const val EXTRA_ACTION_ID = "action_id"

        const val BROADCAST_STATE_CHANGED = "com.theermite.hoso.streamerbot.STATE"
        const val BROADCAST_ACTIONS_UPDATED = "com.theermite.hoso.streamerbot.ACTIONS"
        const val BROADCAST_EVENT = "com.theermite.hoso.streamerbot.EVENT"

        const val EXTRA_STATE = "state"
        const val EXTRA_ACTIONS_JSON = "actions_json"
        const val EXTRA_EVENT_SOURCE = "event_source"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_EVENT_USER = "event_user"
        const val EXTRA_EVENT_BITS = "event_bits"
        const val EXTRA_EVENT_VIEWERS = "event_viewers"
        const val EXTRA_EVENT_MONTHS = "event_months"

        private const val CHANNEL_ID = "hoso_streamerbot_bridge"
        private const val NOTIFICATION_ID = 4203

        // Snapshot of the latest known client state and action list,
        // exposed so siblings (OverlayService) can bootstrap their UI
        // when they spawn AFTER the bridge has already connected — the
        // BROADCAST_STATE_CHANGED stream only carries deltas, so a late
        // subscriber would otherwise display IDLE forever.
        @Volatile
        var latestState: StreamerBotClient.State =
            StreamerBotClient.State.IDLE
            private set

        @Volatile
        var latestActionsJson: String = "[]"
            private set

        /** Start the service, ensuring the client is running. */
        fun start(context: Context) {
            val intent = Intent(context, StreamerBotService::class.java)
                .setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        /** Stop the service. Idempotent (no-op if already stopped). */
        fun stop(context: Context) {
            val intent = Intent(context, StreamerBotService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** Fire-and-forget a DoAction request through the running service. */
        fun sendAction(context: Context, actionId: String) {
            val intent = Intent(context, StreamerBotService::class.java)
                .setAction(ACTION_DO_ACTION)
                .putExtra(EXTRA_ACTION_ID, actionId)
            context.startService(intent)
        }
    }
}
