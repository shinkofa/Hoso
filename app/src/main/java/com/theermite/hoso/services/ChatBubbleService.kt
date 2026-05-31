package com.theermite.hoso.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theermite.hoso.R
import com.theermite.hoso.chat.ChatMessageAdapter
import com.theermite.hoso.chat.IrcMessage
import com.theermite.hoso.chat.TwitchIrcClient
import com.theermite.hoso.config.StreamConfig

/**
 * Floating Twitch chat bubble. Runs as its own foreground service so it
 * survives ColorOS/Realme background killers and keeps the IRC socket
 * alive for the whole stream — independent from [OverlayService] which
 * owns the control disc and the mask windows.
 *
 * Behaviors:
 *   - Header drag, free positioning anywhere on screen (clamped to
 *     screen bounds). X/Y persisted to prefs at drag release.
 *   - Tap the bottom strip to cycle size S → M → L → S.
 *   - Long-press the header to cycle opacity 80 → 60 → 40 → 20 → 80.
 *   - Auto-fade content to 30% after 5 s of no interaction. Any touch
 *     restores full content alpha (window alpha is unaffected — that
 *     stays at the user's chosen opacity).
 *   - Auto-scrolls to bottom on every new message UNLESS the user has
 *     scrolled away, in which case a "N nouveaux ↓" pill appears and
 *     tapping it jumps to bottom.
 *   - Hidden (alpha 0) while the streamer is in PRIVACY or PAUSE so
 *     the chat does not bleed into a screenshot the user explicitly
 *     wanted private.
 */
class ChatBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null

    private lateinit var streamConfig: StreamConfig
    private val ircClient: TwitchIrcClient by lazy {
        TwitchIrcClient(
            onMessage = ::onIrcMessage,
            onState = ::onIrcState,
        )
    }

    private val adapter = ChatMessageAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var list: RecyclerView? = null
    private var header: View? = null
    private var resizeBar: View? = null
    private var newBadge: TextView? = null
    private var statusLabel: TextView? = null
    private var closeBtn: ImageView? = null

    /** Number of unread messages while the user scrolled away from bottom. */
    private var unread: Int = 0
    private var stuckToBottom: Boolean = true

    /** Last user interaction wall-clock — drives the auto-fade. */
    private var lastInteractAt: Long = 0L
    private val fadeRunnable = Runnable { applyFade(true) }

    // Live screen size used to clamp drag/snap. Refreshed on rotation
    // via onConfigurationChanged.
    private var screenW: Int = 0
    private var screenH: Int = 0

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != ScreenRecordService.BROADCAST_STATE_CHANGED) return
            val mask = intent.getStringExtra(
                ScreenRecordService.EXTRA_MASK_MODE
            ) ?: ScreenRecordService.MASK_NONE
            val visible = mask == ScreenRecordService.MASK_NONE
            root?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        streamConfig = StreamConfig(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        registerReceiver(
            stateReceiver,
            IntentFilter(ScreenRecordService.BROADCAST_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED,
        )

        readScreenBounds()
        showBubble()
        startIrc()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        streamConfig.chatEnabled = false
        ircClient.stop()
        mainHandler.removeCallbacks(fadeRunnable)
        runCatching { unregisterReceiver(stateReceiver) }
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        super.onDestroy()
    }

    // ── Bubble window ────────────────────────────────────────────────

    private fun showBubble() {
        val v = LayoutInflater.from(this).inflate(R.layout.chat_bubble, null)
        root = v
        list = v.findViewById(R.id.chat_list)
        header = v.findViewById(R.id.chat_header)
        resizeBar = v.findViewById(R.id.chat_resize)
        newBadge = v.findViewById(R.id.chat_new_badge)
        statusLabel = v.findViewById(R.id.chat_status)
        closeBtn = v.findViewById(R.id.chat_close)

        list?.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        list?.adapter = adapter
        list?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val last = lm.findLastVisibleItemPosition()
                val total = adapter.itemCount
                stuckToBottom = last >= total - 1
                if (stuckToBottom) hideNewBadge()
                noteInteraction()
            }
        })

        newBadge?.setOnClickListener {
            list?.smoothScrollToPosition(maxOf(0, adapter.itemCount - 1))
            hideNewBadge()
            noteInteraction()
        }
        closeBtn?.setOnClickListener {
            stopSelf()
        }

        installHeaderDrag()
        installResizeTap()
        installLongPressOpacity()

        params = buildLayoutParams()
        windowManager?.addView(v, params)
        applyFade(false)
        noteInteraction()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val (wDp, hDp) = SIZE_PRESETS[streamConfig.chatSizeIndex.coerceIn(0, 2)]
        val w = dp(wDp)
        val h = dp(hDp)
        val storedX = streamConfig.chatX
        val storedY = streamConfig.chatY
        // Free positioning. First launch (storedX < 0) anchors on the
        // right edge, vertically centered. Subsequent launches reuse
        // whatever the user dragged to, clamped inside the screen.
        val x = if (storedX < 0) screenW - w
            else storedX.coerceIn(0, (screenW - w).coerceAtLeast(0))
        val y = if (storedY < 0) (screenH - h) / 2
            else storedY.coerceIn(0, (screenH - h).coerceAtLeast(0))

        return WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            alpha = streamConfig.chatOpacity / 100f
        }
    }

    private fun installHeaderDrag() {
        val h = header ?: return
        var downX = 0f
        var downY = 0f
        var startWinX = 0
        var startWinY = 0
        var dragging = false
        h.setOnTouchListener { _, e ->
            val p = params ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startWinX = p.x
                    startWinY = p.y
                    dragging = false
                    noteInteraction()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (kotlin.math.abs(dx) > TOUCH_SLOP_PX ||
                            kotlin.math.abs(dy) > TOUCH_SLOP_PX)) {
                        dragging = true
                    }
                    if (dragging) {
                        p.x = (startWinX + dx).toInt()
                            .coerceIn(0, screenW - p.width)
                        p.y = (startWinY + dy).toInt()
                            .coerceIn(0, screenH - p.height)
                        runCatching { windowManager?.updateViewLayout(root, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) persistFreePosition()
                    dragging
                }
                else -> false
            }
        }
    }

    /**
     * Save the current free position to prefs. Called on drag release —
     * no edge snap, the bubble stays wherever the user lifted the finger
     * (still clamped to the screen by the move handler above).
     */
    private fun persistFreePosition() {
        val p = params ?: return
        streamConfig.chatX = p.x
        streamConfig.chatY = p.y
    }

    private fun installResizeTap() {
        resizeBar?.setOnClickListener {
            val next = (streamConfig.chatSizeIndex + 1) % SIZE_PRESETS.size
            streamConfig.chatSizeIndex = next
            applyNewSize()
            noteInteraction()
        }
    }

    private fun installLongPressOpacity() {
        header?.setOnLongClickListener {
            val current = streamConfig.chatOpacity
            // Cycle 80 → 60 → 40 → 20 → 80, clamped to allowed range.
            val next = when {
                current > 70 -> 60
                current > 50 -> 40
                current > 30 -> 20
                else -> 80
            }.coerceIn(StreamConfig.CHAT_OPACITY_MIN, StreamConfig.CHAT_OPACITY_MAX)
            streamConfig.chatOpacity = next
            params?.let {
                it.alpha = next / 100f
                runCatching { windowManager?.updateViewLayout(root, it) }
            }
            noteInteraction()
            true
        }
    }

    private fun applyNewSize() {
        val p = params ?: return
        val (wDp, hDp) = SIZE_PRESETS[streamConfig.chatSizeIndex.coerceIn(0, 2)]
        p.width = dp(wDp)
        p.height = dp(hDp)
        // Keep the current position after resize, just clamp so the
        // bubble doesn't bleed off-screen if the new size is larger.
        p.x = p.x.coerceIn(0, (screenW - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH - p.height).coerceAtLeast(0))
        // Persist the (possibly clamped) position so a relaunch keeps it.
        streamConfig.chatX = p.x
        streamConfig.chatY = p.y
        runCatching { windowManager?.updateViewLayout(root, p) }
    }

    // ── Auto-fade after idle ─────────────────────────────────────────

    private fun noteInteraction() {
        lastInteractAt = System.currentTimeMillis()
        applyFade(false)
        mainHandler.removeCallbacks(fadeRunnable)
        mainHandler.postDelayed(fadeRunnable, IDLE_FADE_MS)
    }

    private fun applyFade(faded: Boolean) {
        // We fade only the inner list — the header and bubble frame
        // stay visible so the user always sees where the chat is.
        list?.alpha = if (faded) 0.3f else 1.0f
    }

    // ── IRC ──────────────────────────────────────────────────────────

    private fun startIrc() {
        val channel = streamConfig.activePreset?.twitchUsername.orEmpty()
        if (channel.isBlank()) {
            statusLabel?.setText(R.string.chat_status_no_channel)
            return
        }
        ircClient.start(channel)
    }

    private fun onIrcMessage(msg: IrcMessage) {
        if (msg.command != "PRIVMSG") return
        mainHandler.post {
            adapter.append(msg)
            if (stuckToBottom) {
                list?.scrollToPosition(adapter.itemCount - 1)
            } else {
                unread += 1
                showNewBadge(unread)
            }
        }
    }

    private fun onIrcState(state: TwitchIrcClient.State) {
        mainHandler.post {
            val res = when (state) {
                TwitchIrcClient.State.CONNECTING,
                TwitchIrcClient.State.AUTHENTICATING -> R.string.chat_status_connecting
                TwitchIrcClient.State.CONNECTED -> R.string.chat_status_connected
                TwitchIrcClient.State.RECONNECTING -> R.string.chat_status_reconnecting
                TwitchIrcClient.State.STOPPED,
                TwitchIrcClient.State.IDLE -> R.string.chat_status_stopped
            }
            statusLabel?.setText(res)
        }
    }

    // ── Badge ────────────────────────────────────────────────────────

    private fun showNewBadge(count: Int) {
        val b = newBadge ?: return
        b.text = getString(R.string.chat_new_badge, count)
        b.visibility = View.VISIBLE
    }

    private fun hideNewBadge() {
        unread = 0
        newBadge?.visibility = View.GONE
    }

    // ── Notification ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.chat_notif_channel),
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stream)
            .setContentTitle(getString(R.string.chat_notif_title))
            .setContentText(getString(R.string.chat_notif_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    // ── Helpers ──────────────────────────────────────────────────────

    private fun readScreenBounds() {
        val b = windowManager?.maximumWindowMetrics?.bounds ?: return
        screenW = b.width()
        screenH = b.height()
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        const val ACTION_STOP = "com.theermite.hoso.chat.STOP"

        private const val CHANNEL_ID = "hoso_chat_overlay"
        private const val NOTIFICATION_ID = 4202
        private const val TOUCH_SLOP_PX = 8f
        private const val IDLE_FADE_MS = 5_000L

        // (widthDp, heightDp) for S / M / L. Tuned for a typical phone
        // in landscape — fits comfortably in a corner without covering
        // game UI.
        private val SIZE_PRESETS = listOf(
            160 to 120,
            260 to 180,
            360 to 300,
        )
    }
}
