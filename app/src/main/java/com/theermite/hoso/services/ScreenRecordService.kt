package com.theermite.hoso.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.MainActivity
import com.theermite.hoso.R
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import android.media.MediaRecorder
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
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
        if (intent?.action == ACTION_STOP) {
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
        return super.onStartCommand(intent, flags, startId)
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
        const val BROADCAST_STOPPED =
            "com.theermite.hoso.STREAM_STOPPED"

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
