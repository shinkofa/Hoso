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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.theermite.hoso.R

/**
 * Floating, draggable control row shown over the captured screen
 * while Hoso is live. Hosts one button per stream-level action
 * (mute, privacy, pause, stop). G3.1 wires mic + stop; G3.2/G3.3
 * unhide the privacy and pause buttons.
 *
 * Touch model:
 * - Touch on any child first locks onto that child as the "candidate"
 * - Movement past the drag threshold cancels the click and starts a
 *   container drag (the whole row moves)
 * - Release without crossing the threshold fires the candidate's tap
 * The whole row shares the auto-dim / wake animation introduced in G2.1.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null
    private var btnMic: ImageView? = null
    private var btnPrivacy: ImageView? = null
    private var btnPause: ImageView? = null
    private var btnStop: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var maskView: FrameLayout? = null
    private var currentMaskMode: String = ScreenRecordService.MASK_NONE

    private val idleHandler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable { dimOverlay() }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action ==
                ScreenRecordService.BROADCAST_STATE_CHANGED
            ) {
                val muted = intent.getBooleanExtra(
                    ScreenRecordService.EXTRA_IS_MUTED, false
                )
                val maskMode = intent.getStringExtra(
                    ScreenRecordService.EXTRA_MASK_MODE
                ) ?: ScreenRecordService.MASK_NONE
                refreshMicIcon(muted)
                applyMaskMode(maskMode)
                refreshPrivacyIcon(
                    maskMode == ScreenRecordService.MASK_PRIVACY
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        registerReceiver(
            stateReceiver,
            IntentFilter(ScreenRecordService.BROADCAST_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED
        )

        showOverlay()

        // Ask the streamer service for the current state so we paint
        // the icons correctly on initial show (covers any race where
        // the overlay starts after a prior toggle, e.g. restart).
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

    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }
        layoutParams = params

        val root = LayoutInflater.from(this)
            .inflate(R.layout.overlay_controls, null) as LinearLayout
        rootView = root
        btnMic = root.findViewById(R.id.btn_mic)
        btnPrivacy = root.findViewById(R.id.btn_privacy)
        btnPause = root.findViewById(R.id.btn_pause)
        btnStop = root.findViewById(R.id.btn_stop)

        attachTouchHandling(root, params)

        windowManager?.addView(root, params)
        scheduleDim()
    }

    /**
     * Add or remove the fullscreen pause/privacy mask. When a mask is
     * already shown and a different mode is requested, we just refresh
     * the label (no remove/add cycle). When the mask is removed, the
     * control row is brought back to top by re-adding it.
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
                // Coming from no mask: inflate and add fullscreen mask
                // BELOW the controls. To force z-order with two
                // TYPE_APPLICATION_OVERLAY windows of the same app we
                // remove and re-add the controls so they end up on top.
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
                // Switching pause <-> privacy without leaving mask state
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
     * Re-add the control row so it stacks above the mask. Re-inflation
     * is heavier than we need but it's the only stable way (in stock
     * WindowManager) to guarantee z-order between two overlay windows
     * of the same app. Touch state is preserved by reattaching the
     * handler with fresh closures; preset state lives in the service.
     */
    private fun bringControlsToFront() {
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        rootView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        val root = LayoutInflater.from(this)
            .inflate(R.layout.overlay_controls, null) as LinearLayout
        rootView = root
        btnMic = root.findViewById(R.id.btn_mic)
        btnPrivacy = root.findViewById(R.id.btn_privacy)
        btnPause = root.findViewById(R.id.btn_pause)
        btnStop = root.findViewById(R.id.btn_stop)
        attachTouchHandling(root, params)
        wm.addView(root, params)
        scheduleDim()
    }

    /**
     * Single touch handler for the whole row: tracks one candidate
     * child per gesture, promotes to drag past the threshold, and
     * fires the candidate's tap on release if no drag happened.
     */
    private fun attachTouchHandling(
        root: LinearLayout,
        params: WindowManager.LayoutParams
    ) {
        var startX = 0f
        var startY = 0f
        var startParamX = 0
        var startParamY = 0
        var dragged = false
        var candidate: View? = null

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    dragged = false
                    candidate = childAt(root, event.x, event.y)
                    wakeOverlay()
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
                    if (!dragged) {
                        when (candidate?.id) {
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
                    scheduleDim()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    candidate = null
                    scheduleDim()
                    true
                }
                else -> false
            }
        }
    }

    /** Returns the visible child whose bounds contain the parent-local (x,y). */
    private fun childAt(
        parent: LinearLayout,
        x: Float,
        y: Float
    ): View? {
        for (i in 0 until parent.childCount) {
            val c = parent.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (x >= c.left && x <= c.right
                && y >= c.top && y <= c.bottom
            ) {
                return c
            }
        }
        return null
    }

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

    private fun scheduleDim() {
        idleHandler.removeCallbacks(dimRunnable)
        idleHandler.postDelayed(dimRunnable, IDLE_TIMEOUT_MS)
    }

    private fun dimOverlay() {
        rootView?.animate()
            ?.alpha(DIM_ALPHA)
            ?.setDuration(FADE_DURATION_MS)
            ?.start()
    }

    private fun wakeOverlay() {
        idleHandler.removeCallbacks(dimRunnable)
        rootView?.let { v ->
            if (v.alpha < FULL_ALPHA) {
                v.animate()
                    .alpha(FULL_ALPHA)
                    .setDuration(WAKE_DURATION_MS)
                    .start()
            }
        }
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
        idleHandler.removeCallbacks(dimRunnable)
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        maskView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        rootView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        maskView = null
        rootView = null
        btnMic = null
        btnPrivacy = null
        btnPause = null
        btnStop = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 0x484F5350
        const val CHANNEL_ID = "com.theermite.hoso.overlay"
        private const val DRAG_THRESHOLD = 15f
        private const val DRAG_THRESHOLD_SQ =
            DRAG_THRESHOLD * DRAG_THRESHOLD

        // Idle fade: 7 s after last touch, the overlay fades to 30%
        // alpha so it stops covering the game. Any touch wakes it
        // back instantly to 100%.
        private const val IDLE_TIMEOUT_MS = 7_000L
        private const val DIM_ALPHA = 0.3f
        private const val FULL_ALPHA = 1.0f
        private const val FADE_DURATION_MS = 400L
        private const val WAKE_DURATION_MS = 120L
    }
}
