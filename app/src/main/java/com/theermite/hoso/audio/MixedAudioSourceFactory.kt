package com.theermite.hoso.audio

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MediaProjectionAudioSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory

/**
 * Builds a [MixedAudioSource] wrapping a fresh microphone source and a
 * fresh media-projection source, both backed by the same [MediaProjection]
 * the screen capture is already using.
 *
 * Requires API 29 (Q) because internal playback capture is gated by
 * AudioPlaybackCapture from Android 10 onward.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MixedAudioSourceFactory(
    private val mediaProjection: MediaProjection
) : IAudioSourceInternal.Factory {

    override suspend fun create(context: Context): IAudioSourceInternal {
        val mic = MicrophoneSourceFactory().create(context)
        val game = MediaProjectionAudioSourceFactory(mediaProjection).create(context)
        return MixedAudioSource(mic, game)
    }

    override fun isSourceEquals(source: IAudioSourceInternal?): Boolean {
        return source is MixedAudioSource
    }
}
