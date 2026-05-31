package com.theermite.hoso.streaming

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Size
import androidx.core.net.toUri
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.services.ScreenRecordService
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.UriMediaDescriptor
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.IAudioSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.IVideoSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig

/**
 * Shared stream-launch logic.
 *
 * Originally lived inside [com.theermite.hoso.MainActivity.configureAndStart].
 * Hoisted here because G6.2 introduces a second call-site
 * ([com.theermite.hoso.StreamPermissionActivity]) so the overlay can start
 * the stream without going through MainActivity. Both paths must produce
 * the exact same stream configuration (video bitrate, resolution, audio
 * mode) — keeping the recipe in one place avoids drift.
 */
object StreamLauncher {

    /**
     * Configure the bound StreamPack streamer with the current [config]
     * preset, kick off the RTMP stream, and tell [ScreenRecordService] to
     * remember the URL so its auto-reconnect loop can re-call startStream
     * on its own without re-binding to an activity.
     *
     * Throws on streamer error — caller is expected to catch and surface.
     */
    suspend fun configureAndStart(
        context: Context,
        streamer: ISingleStreamer,
        config: StreamConfig,
    ) {
        // StreamPack expects resolution as (width, height) where width is
        // the larger axis — the projected display is rotated by the
        // service if the device is in portrait. We normalize here so the
        // configured value is always landscape-shaped, matching the
        // forced ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE upstream.
        val raw = config.resolution
        val res = Size(
            maxOf(raw.width, raw.height),
            minOf(raw.width, raw.height)
        )
        val bitrate = config.videoBitrate * 1000

        val videoConfig = VideoConfig(
            mimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
            startBitrate = bitrate,
            resolution = res,
            fps = config.fps,
            gopDurationInS = 2f,
            customize = {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
            }
        )

        val audioConfig = AudioConfig(
            mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
            startBitrate = config.audioBitrate,
            sampleRate = 44100,
            channelConfig = AudioConfig.getChannelConfig(1),
            byteFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
        )

        (streamer as IVideoSingleStreamer).setVideoConfig(videoConfig)
        (streamer as IAudioSingleStreamer).setAudioConfig(audioConfig)

        val descriptor = UriMediaDescriptor(config.fullRtmpUrl.toUri())
        streamer.startStream(descriptor)

        // G4.1 — hand the resolved RTMP URL to the foreground service
        // so it can drive its own auto-reconnect loop without having
        // to call back into the activity. The activity that launched
        // us may finish immediately (StreamPermissionActivity) and
        // unbind, but the service stays alive and self-manages.
        context.startService(
            Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_REMEMBER_URL
                putExtra(
                    ScreenRecordService.EXTRA_RTMP_URL,
                    config.fullRtmpUrl
                )
            }
        )
    }
}
