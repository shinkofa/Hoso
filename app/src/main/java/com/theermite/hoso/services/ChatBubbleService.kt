package com.theermite.hoso.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
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
 * Architecture: 3 windows (G6.2 update — replaces the previous single
 * window + hidden-API click-through which ColorOS 14 / SDK 35 blocks).
 *
 *   1. BODY window — the full visual chat layout. Added with
 *      [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] so touches in the
 *      chat list area fall through to the game underneath. The visible
 *      "resize dot" at the bottom is decorative on this window.
 *   2. HEADER OVERLAY window — transparent strip aligned over the body's
 *      32 dp header. Captures drag (move the bubble), close-button taps
 *      (right ~40 dp), and long-press (cycle opacity).
 *   3. DOT OVERLAY window — transparent ~40 × 24 dp catcher centered at
 *      the body's bottom edge. Captures tap (cycle size S/M/L).
 *
 * Side-effect: the chat list itself is no longer scrollable by the user.
 * That is acceptable for an overlay during gameplay — auto-scroll keeps
 * the latest messages visible and the "N nouveaux ↓" badge stays as a
 * passive indicator only.
 *
 * Behaviors preserved:
 *   - X/Y persisted to prefs at drag release. No edge snap.
 *   - Size cycle S → M → L → S on dot tap.
 *   - Opacity cycle 80 → 60 → 40 → 20 → 80 on header long-press.
 *   - Auto-fade content alpha to 30% after 5 s of no interaction.
 *   - Hidden during PRIVACY / PAUSE mask (all 3 windows).
 */
class ChatBubbleService : Service() {

    private var windowManager: WindowManager? = null

    // 3-window state. Body holds the visual layout; the two overlays
    // are transparent touch catchers stacked on top of it.
    private var bodyRoot: View? = null
    private var headerOverlayRoot: View? = null
    private var dotOverlayRoot: View? = null
    private var bodyParams: WindowManager.LayoutParams? = null
    private var headerParams: WindowManager.LayoutParams? = null
    private var dotParams: WindowManager.LayoutParams? = null

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
    private var newBadge: TextView? = null
    private var statusLabel: TextView? = null
    private var statusDot: View? = null
    private var sizeLabel: TextView? = null
    private var closeBtn: ImageView? = null

    /** Number of unread messages while the user scrolled away from bottom. */
    private var unread: Int = 0
    private var stuckToBottom: Boolean = true

    /** Last user interaction wall-clock — drives the auto-fade. */
    private var lastInteractAt: Long = 0L
    private val fadeRunnable = Runnable { applyFade(true) }

    // Live screen size used to clamp drag. Refreshed on rotation
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
            val vis = if (visible) View.VISIBLE else View.GONE
            bodyRoot?.visibility = vis
            headerOverlayRoot?.visibility = vis
            dotOverlayRoot?.visibility = vis
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
        dotOverlayRoot?.let { runCatching { windowManager?.removeView(it) } }
        headerOverlayRoot?.let { runCatching { windowManager?.removeView(it) } }
        bodyRoot?.let { runCatching { windowManager?.removeView(it) } }
        dotOverlayRoot = null
        headerOverlayRoot = null
        bodyRoot = null
        super.onDestroy()
    }

    // ── Bubble windows ───────────────────────────────────────────────

    private fun showBubble() {
        val v = LayoutInflater.from(this).inflate(R.layout.chat_bubble, null)
        bodyRoot = v
        list = v.findViewById(R.id.chat_list)
        header = v.findViewById(R.id.chat_header)
        newBadge = v.findViewById(R.id.chat_new_badge)
        statusLabel = v.findViewById(R.id.chat_status)
        statusDot = v.findViewById(R.id.chat_status_dot)
        sizeLabel = v.findViewById(R.id.chat_size_label)
        closeBtn = v.findViewById(R.id.chat_close)
        refreshSizeLabel()

        list?.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        list?.adapter = adapter
        // No scroll listener: the body window is FLAG_NOT_TOUCHABLE so
        // the user cannot scroll. The chat stays auto-stuck to the
        // bottom and the badge becomes a passive indicator only.

        // Body window: visual only, no touches.
        bodyParams = buildBodyParams()
        runCatching { windowManager?.addView(v, bodyParams) }

        // Header overlay: transparent FrameLayout sized like the body's
        // header strip (full width × 32 dp). All header-area gestures
        // land here.
        val headerOverlay = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
        }
        headerOverlayRoot = headerOverlay
        headerParams = buildHeaderOverlayParams()
        installHeaderOverlayTouch(headerOverlay)
        runCatching { windowManager?.addView(headerOverlay, headerParams) }

        // Dot overlay: small transparent catcher centered at the body
        // bottom. Visible feedback is the dot drawable already drawn in
        // the body window underneath.
        val dotOverlay = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
        }
        dotOverlayRoot = dotOverlay
        dotParams = buildDotOverlayParams()
        installDotOverlayClick(dotOverlay)
        runCatching { windowManager?.addView(dotOverlay, dotParams) }

        applyFade(false)
        noteInteraction()
    }

    private fun buildBodyParams(): WindowManager.LayoutParams {
        val (wDp, hDp) = SIZE_PRESETS[streamConfig.chatSizeIndex.coerceIn(0, 2)]
        val w = dp(wDp)
        val h = dp(hDp)
        val storedX = streamConfig.chatX
        val storedY = streamConfig.chatY
        val x = if (storedX < 0) screenW - w
            else storedX.coerceIn(0, (screenW - w).coerceAtLeast(0))
        val y = if (storedY < 0) (screenH - h) / 2
            else storedY.coerceIn(0, (screenH - h).coerceAtLeast(0))

        // FLAG_NOT_TOUCHABLE: every touch in this window's bounds is
        // delivered to the next window down (the game).
        return WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            alpha = streamConfig.chatOpacity / 100f
        }
    }

    private fun buildHeaderOverlayParams(): WindowManager.LayoutParams {
        val bp = bodyParams!!
        return WindowManager.LayoutParams(
            bp.width,
            dp(HEADER_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = bp.x
            this.y = bp.y
        }
    }

    private fun buildDotOverlayParams(): WindowManager.LayoutParams {
        val bp = bodyParams!!
        val w = dp(DOT_HIT_WIDTH_DP)
        val h = dp(DOT_HIT_HEIGHT_DP)
        return WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = bp.x + (bp.width - w) / 2
            this.y = bp.y + bp.height - h
        }
    }

    /**
     * Update the two overlay windows to follow the body window after a
     * drag or a resize. Body params are the source of truth.
     */
    private fun syncOverlaysToBody() {
        val bp = bodyParams ?: return
        headerParams?.let {
            it.x = bp.x
            it.y = bp.y
            it.width = bp.width
            runCatching {
                windowManager?.updateViewLayout(headerOverlayRoot, it)
            }
        }
        dotParams?.let {
            it.x = bp.x + (bp.width - it.width) / 2
            it.y = bp.y + bp.height - it.height
            runCatching {
                windowManager?.updateViewLayout(dotOverlayRoot, it)
            }
        }
    }

    private fun installHeaderOverlayTouch(overlay: View) {
        var downX = 0f
        var downY = 0f
        var downXLocal = 0f
        var startWinX = 0
        var startWinY = 0
        var dragging = false
        var longPressed = false
        val longPressRunnable = Runnable {
            if (!dragging) {
                longPressed = true
                cycleOpacity()
                noteInteraction()
            }
        }

        overlay.setOnTouchListener { _, e ->
            val bp = bodyParams ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    downXLocal = e.x
                    startWinX = bp.x
                    startWinY = bp.y
                    dragging = false
                    longPressed = false
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    noteInteraction()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (kotlin.math.abs(dx) > TOUCH_SLOP_PX ||
                            kotlin.math.abs(dy) > TOUCH_SLOP_PX)) {
                        dragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        bp.x = (startWinX + dx).toInt()
                            .coerceIn(0, (screenW - bp.width).coerceAtLeast(0))
                        bp.y = (startWinY + dy).toInt()
                            .coerceIn(0, (screenH - bp.height).coerceAtLeast(0))
                        runCatching {
                            windowManager?.updateViewLayout(bodyRoot, bp)
                        }
                        syncOverlaysToBody()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (dragging) {
                        persistFreePosition()
                    } else if (!longPressed) {
                        // Tap. Hit-test the close-button zone on the
                        // right edge of the header.
                        if (downXLocal > overlay.width - dp(CLOSE_HIT_WIDTH_DP)) {
                            stopSelf()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun installDotOverlayClick(overlay: View) {
        overlay.setOnClickListener {
            val next = (streamConfig.chatSizeIndex + 1) % SIZE_PRESETS.size
            streamConfig.chatSizeIndex = next
            applyNewSize()
            refreshSizeLabel()
            noteInteraction()
        }
    }

    /** Update the visible S/M/L pill at the bottom of the body window. */
    private fun refreshSizeLabel() {
        val idx = streamConfig.chatSizeIndex.coerceIn(0, SIZE_PRESETS.size - 1)
        sizeLabel?.text = SIZE_LABELS[idx]
    }

    private fun cycleOpacity() {
        val current = streamConfig.chatOpacity
        // Cycle 80 → 60 → 40 → 20 → 80, clamped to allowed range.
        val next = when {
            current > 70 -> 60
            current > 50 -> 40
            current > 30 -> 20
            else -> 80
        }.coerceIn(StreamConfig.CHAT_OPACITY_MIN, StreamConfig.CHAT_OPACITY_MAX)
        streamConfig.chatOpacity = next
        bodyParams?.let {
            it.alpha = next / 100f
            runCatching { windowManager?.updateViewLayout(bodyRoot, it) }
        }
    }

    /**
     * Save the current free position to prefs. Called on drag release —
     * no edge snap, the bubble stays wherever the user lifted the finger
     * (still clamped to the screen by the move handler above).
     */
    private fun persistFreePosition() {
        val p = bodyParams ?: return
        streamConfig.chatX = p.x
        streamConfig.chatY = p.y
    }

    private fun applyNewSize() {
        val p = bodyParams ?: return
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
        runCatching { windowManager?.updateViewLayout(bodyRoot, p) }
        syncOverlaysToBody()
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

            // Colored dot mirrors the IRC state so the streamer can read
            // it at a glance without parsing the text token.
            val dotColor = when (state) {
                TwitchIrcClient.State.CONNECTED ->
                    resources.getColor(R.color.stream_start, theme)
                TwitchIrcClient.State.CONNECTING,
                TwitchIrcClient.State.AUTHENTICATING,
                TwitchIrcClient.State.RECONNECTING ->
                    resources.getColor(R.color.ermite_accent, theme)
                TwitchIrcClient.State.STOPPED,
                TwitchIrcClient.State.IDLE ->
                    resources.getColor(R.color.text_secondary, theme)
            }
            statusDot?.backgroundTintList = ColorStateList.valueOf(dotColor)
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
        private const val LONG_PRESS_MS = 500L

        // Must match chat_bubble.xml's chat_header height.
        private const val HEADER_HEIGHT_DP = 32

        // Generous finger-friendly hit zone for the close icon on the
        // right side of the header overlay.
        private const val CLOSE_HIT_WIDTH_DP = 40

        // Dot tap zone. Larger than the 12 dp visible dot so the user
        // doesn't need pixel-perfect aim.
        private const val DOT_HIT_WIDTH_DP = 56
        private const val DOT_HIT_HEIGHT_DP = 28

        // (widthDp, heightDp) for S / M / L. Tuned for a typical phone
        // in landscape — fits comfortably in a corner without covering
        // game UI.
        private val SIZE_PRESETS = listOf(
            160 to 120,
            260 to 180,
            360 to 300,
        )

        // Visible label rendered in the bottom pill — index aligned
        // with SIZE_PRESETS.
        private val SIZE_LABELS = listOf("S", "M", "L")
    }
}
