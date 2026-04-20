package com.theermite.hoso.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.theermite.hoso.R

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
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
            BUTTON_SIZE,
            BUTTON_SIZE,
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

        val button = ImageView(this).apply {
            setImageResource(R.drawable.ic_stop_overlay)
            contentDescription = getString(R.string.btn_stop)

            var startX = 0f
            var startY = 0f
            var startParamX = 0
            var startParamY = 0
            var dragged = false

            setOnTouchListener { v, event ->
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
                        if (dx * dx + dy * dy > DRAG_THRESHOLD_SQ) {
                            dragged = true
                        }
                        params.x = startParamX + dx.toInt()
                        params.y = startParamY + dy.toInt()
                        windowManager?.updateViewLayout(v, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) {
                            stopStream()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        overlayView = button
        windowManager?.addView(button, params)
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
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) { }
        }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 0x484F5350
        const val CHANNEL_ID = "com.theermite.hoso.overlay"
        private const val BUTTON_SIZE = 160
        private const val DRAG_THRESHOLD = 15f
        private const val DRAG_THRESHOLD_SQ =
            DRAG_THRESHOLD * DRAG_THRESHOLD
    }
}
