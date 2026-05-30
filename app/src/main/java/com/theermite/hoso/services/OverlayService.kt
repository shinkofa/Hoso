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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.theermite.hoso.R

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
    private var controlsRow: LinearLayout? = null

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

    private val idleHandler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { collapse() }

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
                    lastMuted = muted
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
                toast(getString(R.string.reconnect_recovered))
            }
            giveUp -> {
                lastReconnectAttempt = 0
                toast(
                    getString(
                        R.string.reconnect_give_up,
                        ScreenRecordService.MAX_RECONNECT_ATTEMPTS
                    )
                )
            }
            attempt == 1 && lastReconnectAttempt == 0 -> {
                lastReconnectAttempt = attempt
                toast(getString(R.string.reconnect_lost))
            }
            else -> {
                lastReconnectAttempt = attempt
            }
        }
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

        wm.addView(root, params)
        clearExpandedRefs()
        expanded = false
    }

    /**
     * Replace collapsed with expanded. The expanded row is always
     * centered horizontally on screen (per Jay's request — under
     * pressure, predictable position beats freedom). Vertical position
     * defaults to centered too; the streamer can drag it after if
     * needed. Auto-collapse timer starts immediately.
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
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        layoutParams = params

        val root = LayoutInflater.from(this)
            .inflate(R.layout.overlay_controls, null) as LinearLayout
        rootView = root
        controlsRow = root.findViewById(R.id.controls_row)
        btnCollapse = root.findViewById(R.id.btn_collapse)
        btnMic = root.findViewById(R.id.btn_mic)
        btnPrivacy = root.findViewById(R.id.btn_privacy)
        btnPause = root.findViewById(R.id.btn_pause)
        btnStop = root.findViewById(R.id.btn_stop)

        // Re-paint icons from cached state so the row reflects reality
        // the instant it appears (no flicker from defaults).
        refreshMicIcon(lastMuted)
        refreshPauseIcon(
            currentMaskMode == ScreenRecordService.MASK_PAUSE
        )
        refreshPrivacyIcon(
            currentMaskMode == ScreenRecordService.MASK_PRIVACY
        )

        attachExpandedTouch(root, params)

        wm.addView(root, params)
        expanded = true
        scheduleCollapse()
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
        btnStop = null
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
            controlsRow = root.findViewById(R.id.controls_row)
            btnCollapse = root.findViewById(R.id.btn_collapse)
            btnMic = root.findViewById(R.id.btn_mic)
            btnPrivacy = root.findViewById(R.id.btn_privacy)
            btnPause = root.findViewById(R.id.btn_pause)
            btnStop = root.findViewById(R.id.btn_stop)
            refreshMicIcon(lastMuted)
            refreshPauseIcon(
                currentMaskMode == ScreenRecordService.MASK_PAUSE
            )
            refreshPrivacyIcon(
                currentMaskMode == ScreenRecordService.MASK_PRIVACY
            )
            attachExpandedTouch(root, params)
            wm.addView(root, params)
            scheduleCollapse()
        } else {
            val root = LayoutInflater.from(this)
                .inflate(R.layout.overlay_collapsed, null) as FrameLayout
            rootView = root
            attachCollapsedTouch(root, params)
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
        btnPause?.contentDescription = getString(
            if (paused) R.string.btn_resume
            else R.string.btn_pause
        )
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
    }
}
