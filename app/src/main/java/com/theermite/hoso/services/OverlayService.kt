package com.theermite.hoso.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Handler
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.theermite.hoso.R
import com.theermite.hoso.audio.AudioGains
import com.theermite.hoso.config.AudioSource
import com.theermite.hoso.config.StreamConfig

/**
 * Floating overlay shown over the captured screen while Hoso is live.
 *
 * G4.5 ergonomics — two visual states:
 *   COLLAPSED : single 48 dp draggable trigger. Defaults to top-left,
 *               position remembered between expand/collapse cycles.
 *               Tap → expand. Drag → move. This is the "out of the way"
 *               state that should be the default during a stream.
 *   EXPANDED  : full action row (mic / privacy / pause / stop) plus a
 *               HUD strip reserved for G5.1. Always centered on screen
 *               when entering this state — the streamer should always
 *               find it in the same place under pressure. Auto-collapse
 *               after 5 s of inactivity, or tap btn_collapse.
 *
 * Mask mode (pause / privacy) forces an expand at activation so the
 * Resume affordance is one tap away. The mask itself is a separate
 * window; we re-add the controls view on top of it (the only stable
 * way to enforce z-order between two TYPE_APPLICATION_OVERLAY windows
 * of the same app under stock WindowManager).
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null

    // Current visible view (collapsed FrameLayout OR expanded LinearLayout).
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // EXPANDED-state references — null while collapsed.
    private var btnMic: ImageView? = null
    private var btnPrivacy: ImageView? = null
    private var btnPause: ImageView? = null
    private var btnStop: ImageView? = null
    private var btnCollapse: ImageView? = null
    private var btnChat: ImageView? = null
    private var controlsRow: LinearLayout? = null
    private var hudText: TextView? = null

    // G7.1 Phase B.2 — mix gain sliders. Only inflated/visible when the
    // active audio source is MIX. AudioGains receives the value on every
    // drag step for live audio response; StreamConfig persists on release.
    private var mixGainsGroup: LinearLayout? = null
    private var seekbarMicGain: SeekBar? = null
    private var seekbarGameGain: SeekBar? = null
    private var textMicGainValue: TextView? = null
    private var textGameGainValue: TextView? = null
    private var streamConfig: StreamConfig? = null

    // COLLAPSED state remembers the last drag-applied position so a
    // collapse after expand returns to where the user parked it.
    private var collapsedX: Int = 50
    private var collapsedY: Int = 300

    private var expanded: Boolean = false

    private var maskView: FrameLayout? = null
    private var currentMaskMode: String = ScreenRecordService.MASK_NONE

    // Cached mute state — used when MASK_NONE comes back so we can
    // restore the right mic icon without an extra broadcast.
    private var lastMuted: Boolean = false

    // G5.1 — HUD state. streamStartedAt arrives via BROADCAST_STATE_CHANGED;
    // 0 = no active stream, hide HUD. Reconnect state mirrors the
    // service-side backoff loop, updated by handleReconnect().
    private var streamStartedAt: Long = 0L
    private var isReconnecting: Boolean = false
    private var reconnectAttempt: Int = 0
    private var hasGivenUp: Boolean = false

    // TrafficStats sampling baseline. We measure UID tx delta on each
    // tick; the RTMP stream dominates traffic by orders of magnitude
    // during a live, so this is an honest proxy for outgoing bitrate.
    // UNSUPPORTED on some OEMs (returns -1) — handled by formatBitrate.
    private var lastTxBytes: Long = -1L
    private var lastSampleAt: Long = 0L
    private var currentKbps: Int = -1

    private val idleHandler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { collapse() }

    // G5.1 — HUD tick. Re-posts itself every HUD_TICK_MS only while the
    // overlay is expanded, so collapsed state has zero recurring cost.
    private val hudTickRunnable = object : Runnable {
        override fun run() {
            if (!expanded) return
            tickHud()
            idleHandler.postDelayed(this, HUD_TICK_MS)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenRecordService.BROADCAST_STATE_CHANGED -> {
                    val muted = intent.getBooleanExtra(
                        ScreenRecordService.EXTRA_IS_MUTED, false
                    )
                    val maskMode = intent.getStringExtra(
                        ScreenRecordService.EXTRA_MASK_MODE
                    ) ?: ScreenRecordService.MASK_NONE
                    val startedAt = intent.getLongExtra(
                        ScreenRecordService.EXTRA_STREAM_STARTED_AT, 0L
                    )
                    lastMuted = muted
                    // First reception of streamStartedAt (or a refresh
                    // after stop/start) → reset the HUD baselines so
                    // bitrate doesn't show a fake spike on the first tick.
                    if (startedAt != streamStartedAt) {
                        streamStartedAt = startedAt
                        if (startedAt == 0L) {
                            // Stream stopped — clear reconnect state too.
                            isReconnecting = false
                            reconnectAttempt = 0
                            hasGivenUp = false
                        }
                        lastTxBytes = -1L
                        lastSampleAt = 0L
                        currentKbps = -1
                    }
                    // Activating a mask is a high-urgency moment for the
                    // streamer — make sure controls are right there.
                    if (maskMode != ScreenRecordService.MASK_NONE
                        && !expanded
                    ) {
                        expand()
                    }
                    refreshMicIcon(muted)
                    applyMaskMode(maskMode)
                    refreshPrivacyIcon(
                        maskMode == ScreenRecordService.MASK_PRIVACY
                    )
                    if (expanded) tickHud()
                }
                ScreenRecordService.BROADCAST_RECONNECT_STATE -> {
                    handleReconnect(intent)
                }
            }
        }
    }

    // Track which reconnect events we've already toasted so we don't
    // spam the user with a Toast for every backoff iteration. One Toast
    // at attempt 1 (connection lost), one on recovery, one on give-up.
    // Backoff progress lives in the FGS notification text.
    private var lastReconnectAttempt: Int = 0

    private fun handleReconnect(intent: Intent) {
        val attempt = intent.getIntExtra(
            ScreenRecordService.EXTRA_RECONNECT_ATTEMPT, 0
        )
        val giveUp = intent.getBooleanExtra(
            ScreenRecordService.EXTRA_RECONNECT_GIVE_UP, false
        )
        val recovered = intent.getBooleanExtra(
            ScreenRecordService.EXTRA_RECONNECT_RECOVERED, false
        )
        when {
            recovered -> {
                lastReconnectAttempt = 0
                isReconnecting = false
                reconnectAttempt = 0
                hasGivenUp = false
                toast(getString(R.string.reconnect_recovered))
            }
            giveUp -> {
                lastReconnectAttempt = 0
                isReconnecting = false
                hasGivenUp = true
                toast(
                    getString(
                        R.string.reconnect_give_up,
                        ScreenRecordService.MAX_RECONNECT_ATTEMPTS
                    )
                )
            }
            attempt == 1 && lastReconnectAttempt == 0 -> {
                lastReconnectAttempt = attempt
                isReconnecting = true
                reconnectAttempt = attempt
                hasGivenUp = false
                toast(getString(R.string.reconnect_lost))
            }
            else -> {
                lastReconnectAttempt = attempt
                isReconnecting = true
                reconnectAttempt = attempt
            }
        }
        if (expanded) tickHud()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        streamConfig = StreamConfig(this)

        registerReceiver(
            stateReceiver,
            IntentFilter().apply {
                addAction(ScreenRecordService.BROADCAST_STATE_CHANGED)
                addAction(ScreenRecordService.BROADCAST_RECONNECT_STATE)
            },
            RECEIVER_NOT_EXPORTED
        )

        // Default: COLLAPSED. The streamer sees one small disc on top-left
        // and has to tap to access controls.
        showCollapsed()

        // Ask the streamer service for the current state so we paint the
        // icons correctly on initial show (covers any race where the
        // overlay starts after a prior toggle, e.g. restart).
        startService(
            Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_QUERY_STATE
            }
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_notification_title),
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stream)
            .setContentTitle(
                getString(R.string.overlay_notification_title)
            )
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    // ---- State transitions: COLLAPSED <-> EXPANDED -----------------

    /**
     * Inflate and show the collapsed trigger at the remembered position
     * (top-left corner if first run). Any in-flight collapse timer is
     * cancelled since the timer is only meaningful while expanded.
     */
    private fun showCollapsed() {
        idleHandler.removeCallbacks(collapseRunnable)
        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = collapsedX
            y = collapsedY
        }
        layoutParams = params

        val root = LayoutInflater.from(this)
            .inflate(R.layout.overlay_collapsed, null) as FrameLayout
        rootView = root
        attachCollapsedTouch(root, params)
        refreshCollapsedChatPastille(root)

        wm.addView(root, params)
        clearExpandedRefs()
        expanded = false
    }

    /**
     * Show/hide the small sky-blue dot on the COLLAPSED trigger.
     * Visible when the chat bubble service is active so the streamer
     * can confirm chat is wired without expanding the controls. Called
     * every time we re-inflate the collapsed layout.
     */
    private fun refreshCollapsedChatPastille(collapsedRoot: View) {
        val pastille = collapsedRoot.findViewById<View>(R.id.chat_pastille)
            ?: return
        pastille.visibility =
            if (streamConfig?.chatEnabled == true) View.VISIBLE
            else View.GONE
    }

    /**
     * Replace collapsed with expanded. The expanded row is pinned to the
     * right edge, vertically centered (G7.1 Phase D polish — under
     * pressure the streamer always knows where to reach with the thumb
     * holding the phone). Auto-collapse timer starts immediately.
     */
    private fun expand() {
        if (expanded) {
            scheduleCollapse()
            return
        }
        val wm = windowManager ?: return

        rootView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }
        layoutParams = params

        val root = LayoutInflater.from(this)
            .inflate(R.layout.overlay_controls, null) as LinearLayout
        rootView = root
        bindExpandedViews(root)

        // Re-paint icons from cached state so the row reflects reality
        // the instant it appears (no flicker from defaults).
        refreshMicIcon(lastMuted)
        refreshPauseIcon(
            currentMaskMode == ScreenRecordService.MASK_PAUSE
        )
        refreshPrivacyIcon(
            currentMaskMode == ScreenRecordService.MASK_PRIVACY
        )
        applyMixSlidersVisibility()

        attachExpandedTouch(root, params)

        wm.addView(root, params)
        expanded = true
        scheduleCollapse()
        startHudTicker()
    }

    /**
     * Locate every EXPANDED-state child view in [root] and store the
     * references. Called from expand() AND bringControlsToFront() —
     * the latter re-inflates the controls view to enforce z-order over
     * the mask, so the binding must happen in both code paths.
     */
    private fun bindExpandedViews(root: LinearLayout) {
        controlsRow = root.findViewById(R.id.controls_row)
        btnCollapse = root.findViewById(R.id.btn_collapse)
        btnMic = root.findViewById(R.id.btn_mic)
        btnPrivacy = root.findViewById(R.id.btn_privacy)
        btnPause = root.findViewById(R.id.btn_pause)
        btnChat = root.findViewById(R.id.btn_chat)
        btnStop = root.findViewById(R.id.btn_stop)
        hudText = root.findViewById(R.id.hud_text)
        refreshChatButtonState()
        mixGainsGroup = root.findViewById(R.id.overlay_mix_gains)
        seekbarMicGain = root.findViewById(R.id.overlay_seekbar_mic_gain)
        seekbarGameGain = root.findViewById(R.id.overlay_seekbar_game_gain)
        textMicGainValue = root.findViewById(R.id.overlay_mic_gain_value)
        textGameGainValue = root.findViewById(R.id.overlay_game_gain_value)
        wireMixGainSliders()
    }

    /**
     * Toggle the gain sliders depending on the configured audio source.
     * Source is read once per expand — it can only change while not
     * streaming (spinner disabled in MainActivity.updateUI(true)), so
     * caching across the EXPANDED lifetime is safe.
     */
    private fun applyMixSlidersVisibility() {
        val source = streamConfig?.audioSource ?: AudioSource.MIC
        mixGainsGroup?.visibility = when (source) {
            AudioSource.MIX -> View.VISIBLE
            AudioSource.MIC -> View.GONE
        }
    }

    /**
     * Bind both sliders to AudioGains (live, every drag step) AND
     * StreamConfig (persisted on release only — avoids one I/O write
     * per drag pixel). Every interaction also bumps the auto-collapse
     * timer so the row stays visible while the user is fine-tuning.
     */
    private fun wireMixGainSliders() {
        seekbarMicGain?.progress = AudioGains.micGainPermil
        seekbarGameGain?.progress = AudioGains.gameGainPermil
        renderMicGainLabel(AudioGains.micGainPermil)
        renderGameGainLabel(AudioGains.gameGainPermil)

        seekbarMicGain?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    renderMicGainLabel(progress)
                    if (fromUser) {
                        AudioGains.micGainPermil = progress
                        scheduleCollapse()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    scheduleCollapse()
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sb?.progress?.let { streamConfig?.micGainPermil = it }
                    scheduleCollapse()
                }
            }
        )

        seekbarGameGain?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    renderGameGainLabel(progress)
                    if (fromUser) {
                        AudioGains.gameGainPermil = progress
                        scheduleCollapse()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    scheduleCollapse()
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    sb?.progress?.let { streamConfig?.gameGainPermil = it }
                    scheduleCollapse()
                }
            }
        )
    }

    private fun renderMicGainLabel(permil: Int) {
        textMicGainValue?.text = getString(
            R.string.gain_value_format, permil / 10
        )
    }

    private fun renderGameGainLabel(permil: Int) {
        textGameGainValue?.text = getString(
            R.string.gain_value_format, permil / 10
        )
    }

    /**
     * Replace expanded with collapsed. Used by the auto-collapse timer,
     * btn_collapse tap, and explicit calls (e.g. when leaving mask
     * mode if we want to free the screen — currently we leave that to
     * the auto timer).
     */
    private fun collapse() {
        if (!expanded) return
        val wm = windowManager ?: return
        idleHandler.removeCallbacks(collapseRunnable)
        stopHudTicker()

        rootView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        clearExpandedRefs()

        // Recreate the collapsed view fresh — keeps the same layout
        // params lifecycle as the initial onCreate path.
        showCollapsed()
    }

    private fun clearExpandedRefs() {
        controlsRow = null
        btnCollapse = null
        btnMic = null
        btnPrivacy = null
        btnPause = null
        btnChat = null
        btnStop = null
        hudText = null
        mixGainsGroup = null
        seekbarMicGain = null
        seekbarGameGain = null
        textMicGainValue = null
        textGameGainValue = null
    }

    private fun scheduleCollapse() {
        idleHandler.removeCallbacks(collapseRunnable)
        idleHandler.postDelayed(collapseRunnable, IDLE_TIMEOUT_MS)
    }

    // ---- Mask (pause / privacy) plumbing — preserved from G3 -------

    /**
     * Add or remove the fullscreen pause/privacy mask. When a mask is
     * already shown and a different mode is requested, we just refresh
     * the label (no remove/add cycle). When the mask is added or
     * removed we re-add the controls view so it stacks on top.
     */
    private fun applyMaskMode(mode: String) {
        if (mode == currentMaskMode) return
        val wm = windowManager ?: return

        when {
            mode == ScreenRecordService.MASK_NONE -> {
                maskView?.let {
                    try { wm.removeView(it) } catch (_: Exception) {}
                }
                maskView = null
            }
            currentMaskMode == ScreenRecordService.MASK_NONE -> {
                val mask = LayoutInflater.from(this)
                    .inflate(R.layout.stream_mask, null) as FrameLayout
                mask.findViewById<TextView>(R.id.mask_title).text =
                    getString(maskTitleRes(mode))
                val mp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.OPAQUE
                )
                wm.addView(mask, mp)
                maskView = mask
                bringControlsToFront()
            }
            else -> {
                maskView?.findViewById<TextView>(R.id.mask_title)?.text =
                    getString(maskTitleRes(mode))
            }
        }
        currentMaskMode = mode
        refreshPauseIcon(mode == ScreenRecordService.MASK_PAUSE)
    }

    private fun maskTitleRes(mode: String): Int = when (mode) {
        ScreenRecordService.MASK_PRIVACY -> R.string.btn_privacy
        else -> R.string.btn_pause
    }

    /**
     * Re-add the controls view (whatever its current state) so it
     * stacks above the mask. Touch state is reattached with fresh
     * closures; persistent state lives in the service.
     */
    private fun bringControlsToFront() {
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        val view = rootView ?: return
        try { wm.removeView(view) } catch (_: Exception) {}
        // Re-inflate to avoid "view already has a parent" races.
        if (expanded) {
            val root = LayoutInflater.from(this)
                .inflate(R.layout.overlay_controls, null) as LinearLayout
            rootView = root
            bindExpandedViews(root)
            refreshMicIcon(lastMuted)
            refreshPauseIcon(
                currentMaskMode == ScreenRecordService.MASK_PAUSE
            )
            refreshPrivacyIcon(
                currentMaskMode == ScreenRecordService.MASK_PRIVACY
            )
            applyMixSlidersVisibility()
            attachExpandedTouch(root, params)
            wm.addView(root, params)
            scheduleCollapse()
            startHudTicker()
        } else {
            val root = LayoutInflater.from(this)
                .inflate(R.layout.overlay_collapsed, null) as FrameLayout
            rootView = root
            attachCollapsedTouch(root, params)
            refreshCollapsedChatPastille(root)
            wm.addView(root, params)
        }
    }

    // ---- Touch handlers --------------------------------------------

    /**
     * Touch handler for COLLAPSED state. Single child (the trigger)
     * acts as drag handle AND tap target. Tap → expand. Drag past
     * threshold → move the floating window. Position is persisted
     * to collapsedX/Y so a subsequent expand→collapse cycle remembers
     * it.
     */
    private fun attachCollapsedTouch(
        root: View,
        params: WindowManager.LayoutParams
    ) {
        var startX = 0f
        var startY = 0f
        var startParamX = 0
        var startParamY = 0
        var dragged = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragged
                        && dx * dx + dy * dy > DRAG_THRESHOLD_SQ
                    ) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x = startParamX + dx.toInt()
                        params.y = startParamY + dy.toInt()
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        // Persist the new resting position.
                        collapsedX = params.x
                        collapsedY = params.y
                    } else {
                        expand()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    /**
     * Touch handler for EXPANDED state. The expanded row is always
     * centered, so we do NOT support dragging it (matches the design
     * decision: predictable position under pressure). Tap on any
     * action child fires the action; tap on btn_collapse closes the
     * row; any touch resets the auto-collapse timer.
     */
    private fun attachExpandedTouch(
        root: LinearLayout,
        @Suppress("UNUSED_PARAMETER") params: WindowManager.LayoutParams
    ) {
        var startX = 0f
        var startY = 0f
        var dragged = false
        var candidate: View? = null

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    dragged = false
                    candidate = hitTestExpanded(event.x, event.y)
                    scheduleCollapse()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragged
                        && dx * dx + dy * dy > DRAG_THRESHOLD_SQ
                    ) {
                        dragged = true
                        // Cancel the candidate as soon as we know it's
                        // a drag — but we don't actually move the
                        // window (expanded is always centered by design).
                        candidate = null
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        when (candidate?.id) {
                            R.id.btn_collapse -> collapse()
                            R.id.btn_mic -> sendStreamAction(
                                ScreenRecordService.ACTION_TOGGLE_MUTE
                            )
                            R.id.btn_privacy -> sendStreamAction(
                                ScreenRecordService.ACTION_TOGGLE_PRIVACY
                            )
                            R.id.btn_pause -> sendStreamAction(
                                ScreenRecordService.ACTION_TOGGLE_PAUSE
                            )
                            R.id.btn_chat -> toggleChatBubble()
                            R.id.btn_stop -> stopStream()
                        }
                    }
                    candidate = null
                    scheduleCollapse()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    candidate = null
                    scheduleCollapse()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Walk the controls row children and return whichever one contains
     * (x, y) in root-local coordinates. The expanded root is a vertical
     * LinearLayout whose action row is a nested horizontal LinearLayout,
     * so we offset by the row's position before testing children.
     */
    private fun hitTestExpanded(x: Float, y: Float): View? {
        val row = controlsRow ?: return null
        val rowX = x - row.left
        val rowY = y - row.top
        if (rowY < 0 || rowY > row.height) return null
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (rowX >= c.left && rowX <= c.right) {
                return c
            }
        }
        return null
    }

    // ---- Service actions -------------------------------------------

    private fun sendStreamAction(action: String) {
        startService(
            Intent(this, ScreenRecordService::class.java).apply {
                this.action = action
            }
        )
    }

    private fun refreshMicIcon(muted: Boolean) {
        btnMic?.setImageResource(
            if (muted) R.drawable.ic_mic_off
            else R.drawable.ic_mic_on
        )
        btnMic?.setBackgroundResource(
            if (muted) R.drawable.overlay_btn_active_bg
            else R.drawable.overlay_btn_bg
        )
        btnMic?.contentDescription = getString(
            if (muted) R.string.btn_unmute
            else R.string.btn_mute
        )
    }

    private fun refreshPrivacyIcon(privacy: Boolean) {
        btnPrivacy?.setImageResource(
            if (privacy) R.drawable.ic_privacy_on
            else R.drawable.ic_privacy_off
        )
        btnPrivacy?.setBackgroundResource(
            if (privacy) R.drawable.overlay_btn_active_bg
            else R.drawable.overlay_btn_bg
        )
        btnPrivacy?.contentDescription = getString(
            if (privacy) R.string.btn_privacy_off
            else R.string.btn_privacy
        )
    }

    private fun refreshPauseIcon(paused: Boolean) {
        btnPause?.setImageResource(
            if (paused) R.drawable.ic_play
            else R.drawable.ic_pause
        )
        btnPause?.setBackgroundResource(
            if (paused) R.drawable.overlay_btn_active_bg
            else R.drawable.overlay_btn_bg
        )
        btnPause?.contentDescription = getString(
            if (paused) R.string.btn_resume
            else R.string.btn_pause
        )
    }

    // ---- G6.2 chat bubble toggle -----------------------------------

    /**
     * Start or stop the chat bubble service depending on its current
     * [StreamConfig.chatEnabled] flag. Idempotent — calling twice while
     * enabling is a no-op (Android coalesces startForegroundService).
     * The flag is the single source of truth: ChatBubbleService.onDestroy
     * resets it, so kill-from-recents leaves us in a coherent state.
     */
    private fun toggleChatBubble() {
        val cfg = streamConfig ?: return
        val intent = Intent(this, ChatBubbleService::class.java)
        if (cfg.chatEnabled) {
            // Disable: stop the service (its onDestroy resets the flag).
            stopService(intent)
        } else {
            cfg.chatEnabled = true
            startForegroundService(intent)
        }
        // Optimistic UI — the icon updates instantly, the service catches up.
        refreshChatButtonState()
    }

    private fun refreshChatButtonState() {
        val enabled = streamConfig?.chatEnabled == true
        btnChat?.setBackgroundResource(
            if (enabled) R.drawable.overlay_btn_chat_on_bg
            else R.drawable.overlay_btn_bg
        )
        btnChat?.alpha = 1.0f
        btnChat?.contentDescription = getString(R.string.btn_chat)
    }

    // ---- G5.1 HUD live stats ---------------------------------------

    /**
     * Start the per-second HUD tick. Renders one frame immediately so
     * the row doesn't appear empty for a second on expand. The ticker
     * self-cancels in onRun if expanded has flipped to false in the
     * meantime (defensive against race with collapse()).
     */
    private fun startHudTicker() {
        idleHandler.removeCallbacks(hudTickRunnable)
        tickHud()
        idleHandler.postDelayed(hudTickRunnable, HUD_TICK_MS)
    }

    private fun stopHudTicker() {
        idleHandler.removeCallbacks(hudTickRunnable)
    }

    /**
     * One HUD frame. G7.1 Phase D — compact single-line HUD designed to
     * sit horizontally next to the control row at the right edge:
     *   "⏱ 00:42:13 · ↑ 4.2M · ●"
     * The trailing dot is colored from the connection state (green =
     * LIVE, orange = RECONNECT, red = LOST). Sample TrafficStats delta
     * to derive outgoing bitrate (UID-level; RTMP dominates traffic
     * during a live so this is honest within a few kbps). When no
     * stream is active (streamStartedAt == 0), keep the HUD hidden.
     */
    private fun tickHud() {
        val hud = hudText ?: return
        if (streamStartedAt == 0L) {
            hud.visibility = View.GONE
            return
        }
        sampleBitrate()
        val durationStr = formatDuration(
            System.currentTimeMillis() - streamStartedAt
        )
        val bitrateStr = formatBitrateCompact(currentKbps)

        val builder = SpannableStringBuilder()
        builder.append("⏱ ").append(durationStr)
        builder.append("  ·  ↑ ").append(bitrateStr)
        builder.append("  ·  ")
        val dotStart = builder.length
        builder.append("●")
        builder.setSpan(
            ForegroundColorSpan(stateDotColor()),
            dotStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        hud.text = builder
        hud.visibility = View.VISIBLE
    }

    /**
     * Color of the trailing status dot in the compact HUD.
     * Green = healthy live, orange = reconnect attempt in progress,
     * red = reconnection definitively given up.
     */
    private fun stateDotColor(): Int = when {
        hasGivenUp -> Color.parseColor("#FFF87171")       // stream_stop
        isReconnecting -> Color.parseColor("#FFFBBF24")   // amber
        else -> Color.parseColor("#FF4ADE80")             // green
    }

    /**
     * Compact bitrate for the single-line HUD: "4.2M" above 1000 kbps,
     * "850k" below. Returns "--" when the OEM doesn't report UID stats.
     */
    private fun formatBitrateCompact(kbps: Int): String {
        if (kbps < 0) return getString(R.string.hud_bitrate_unavailable)
        if (kbps >= 1000) {
            val mbps = kbps / 1000.0
            return String.format("%.1fM", mbps)
        }
        return "${kbps}k"
    }

    /**
     * Update lastTxBytes / lastSampleAt baselines and recompute
     * currentKbps as a 1-sample delta. First sample (lastSampleAt == 0)
     * just seeds the baseline; subsequent samples produce real numbers.
     * Returns -1 (UNAVAILABLE) when the OEM doesn't report UID stats.
     */
    private fun sampleBitrate() {
        val tx = TrafficStats.getUidTxBytes(Process.myUid())
        if (tx < 0L) {
            currentKbps = -1
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastSampleAt > 0L && lastTxBytes >= 0L) {
            val dtMs = now - lastSampleAt
            if (dtMs > 0) {
                val deltaBytes = (tx - lastTxBytes).coerceAtLeast(0L)
                // bits/s = bytes * 8 / sec ; kbps = /1000
                currentKbps =
                    ((deltaBytes * 8.0) / dtMs).toInt() // bytes*8/ms = kbits/s
            }
        }
        lastTxBytes = tx
        lastSampleAt = now
    }

    private fun formatDuration(elapsedMs: Long): String {
        val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun formatBitrate(kbps: Int): String {
        if (kbps < 0) {
            return getString(R.string.hud_bitrate_unavailable) + " kbps"
        }
        return "$kbps kbps"
    }

    private fun formatState(): String = when {
        hasGivenUp -> getString(R.string.hud_state_lost)
        isReconnecting -> getString(
            R.string.hud_state_reconnect,
            reconnectAttempt,
            ScreenRecordService.MAX_RECONNECT_ATTEMPTS
        )
        else -> getString(R.string.hud_state_live)
    }

    private fun stopStream() {
        val intent = Intent(
            this, ScreenRecordService::class.java
        ).apply { action = ScreenRecordService.ACTION_STOP }
        startService(intent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(collapseRunnable)
        idleHandler.removeCallbacks(hudTickRunnable)
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        maskView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        rootView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        maskView = null
        rootView = null
        clearExpandedRefs()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 0x484F5350
        const val CHANNEL_ID = "com.theermite.hoso.overlay"
        private const val DRAG_THRESHOLD = 15f
        private const val DRAG_THRESHOLD_SQ =
            DRAG_THRESHOLD * DRAG_THRESHOLD

        // G4.5 — auto-collapse after 5 s of inactivity in EXPANDED.
        // Short enough to stay out of the way during normal play, long
        // enough to let the streamer line up a 2-button sequence
        // (e.g. mute → pause) without the row vanishing mid-action.
        private const val IDLE_TIMEOUT_MS = 5_000L

        // G5.1 — HUD refresh cadence. 1 s aligns with the human eye's
        // ability to read changing numbers without strobing, and it
        // gives TrafficStats a comfortable window for delta math.
        // Ticker is gated on `expanded` so collapsed costs zero.
        private const val HUD_TICK_MS = 1_000L
    }
}
