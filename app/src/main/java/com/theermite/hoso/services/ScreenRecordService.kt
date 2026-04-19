package com.theermite.hoso.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.theermite.hoso.MainActivity
import com.theermite.hoso.R
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.services.MediaProjectionService
import io.github.thibaultbee.streampack.services.utils.SingleStreamerFactory

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
        return MicrophoneSourceFactory()
    }

    override fun onOpenNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stream)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 0x484F534F
        const val CHANNEL_ID = "com.theermite.hoso.stream"
    }
}
