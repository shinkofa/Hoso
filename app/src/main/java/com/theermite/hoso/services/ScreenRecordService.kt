package com.theermite.hoso.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.MainActivity
import com.theermite.hoso.R
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import android.media.MediaRecorder
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.interfaces.IWithAudioSource
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.services.MediaProjectionService
import io.github.thibaultbee.streampack.services.utils.SingleStreamerFactory
import kotlinx.coroutines.launch

class ScreenRecordService : MediaProjectionService<ISingleStreamer>(
    streamerFactory = SingleStreamerFactory(
        withAudio = true,
        withVideo = true
    ),
    notificationId = NOTIFICATION_ID,
    channelId = CHANNEL_ID,
    channelNameResourceId = R.string.notification_channel_name
) {

    override fun onCreate() {
        super.onCreate()
        // StreamPack's StreamerService.onCreate() promotes the service
        // to foreground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION only.
        // Android 14+ requires MICROPHONE in the bitmask so the framework
        // calls AppOps.startOp(RECORD_AUDIO); without it the mic is silenced
        // (observed on ColorOS via VD.AudioRecordMonitor isSilenced=true).
        // Re-call startForeground with both types to upgrade.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                onOpenNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        }
    }

    override fun createDefaultAudioSource(
        mediaProjection: MediaProjection,
        extras: Bundle
    ): IAudioSourceInternal.Factory {
        return MicrophoneSourceFactory(
            audioSource = MediaRecorder.AudioSource.MIC,
            effects = emptySet()
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                lifecycleScope.launch {
                    try { streamer?.stopStream() } catch (_: Exception) {}
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    sendBroadcast(
                        Intent(BROADCAST_STOPPED).setPackage(packageName)
                    )
                }
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMute()
                return START_NOT_STICKY
            }
            ACTION_QUERY_STATE -> {
                broadcastState()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Flip the mic mute state on the StreamPack audio input and notify
     * the overlay so it can refresh its icon. Safe to call before the
     * stream is fully up: if the audio input isn't available yet, the
     * call is a no-op and the broadcast still carries the current
     * (unchanged) state.
     */
    private fun toggleMute() {
        val audio = (streamer as? IWithAudioSource)?.audioInput
        if (audio != null) {
            audio.isMuted = !audio.isMuted
        }
        broadcastState()
    }

    private fun broadcastState() {
        val isMuted =
            (streamer as? IWithAudioSource)?.audioInput?.isMuted ?: false
        sendBroadcast(
            Intent(BROADCAST_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_IS_MUTED, isMuted)
        )
    }

    override fun onOpenNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = buildStopPending(this)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stream)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openPending)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.btn_stop),
                stopPending
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 0x484F534F
        const val CHANNEL_ID = "com.theermite.hoso.stream"
        const val ACTION_STOP =
            "com.theermite.hoso.STOP_STREAM"
        const val ACTION_TOGGLE_MUTE =
            "com.theermite.hoso.TOGGLE_MUTE"
        const val ACTION_QUERY_STATE =
            "com.theermite.hoso.QUERY_STATE"
        const val BROADCAST_STOPPED =
            "com.theermite.hoso.STREAM_STOPPED"
        const val BROADCAST_STATE_CHANGED =
            "com.theermite.hoso.STATE_CHANGED"
        const val EXTRA_IS_MUTED = "is_muted"

        fun buildStopPending(context: Context): PendingIntent {
            val intent = Intent(
                context, ScreenRecordService::class.java
            ).apply { action = ACTION_STOP }
            return PendingIntent.getService(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                    or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
