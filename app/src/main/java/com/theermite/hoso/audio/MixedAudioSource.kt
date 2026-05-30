package com.theermite.hoso.audio

import io.github.thibaultbee.streampack.core.elements.data.RawFrame
import io.github.thibaultbee.streampack.core.elements.sources.audio.AudioSourceConfig
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import io.github.thibaultbee.streampack.core.elements.utils.pool.IReadOnlyRawFrameFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A composite [IAudioSourceInternal] that captures two streams in parallel
 * (microphone + internal playback) and mixes them as 16-bit signed PCM
 * before handing a single frame to the encoder pipeline.
 *
 * Both children share the same [AudioSourceConfig] (sample rate, channel
 * config, byte format), so a sample-for-sample mix is sufficient — no
 * resampling, no rate adaptation.
 *
 * Timestamps come from the microphone child (it owns the AudioRecord
 * clock authority via MONOTONIC). The game timestamp is read and
 * discarded — both AudioRecords share `TIMEBASE_MONOTONIC`, so drift is
 * bounded and the user-perceived A/V sync is preserved.
 *
 * Gains are read from [AudioGains] at every frame — a slider drag in the
 * UI or the floating overlay is reflected in the next mixed frame with
 * no allocation and no lock.
 */
class MixedAudioSource(
    private val micSource: IAudioSourceInternal,
    private val gameSource: IAudioSourceInternal
) : IAudioSourceInternal {

    private val _isStreamingFlow = MutableStateFlow(false)
    override val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow.asStateFlow()

    /**
     * Scratch buffer holding game-source PCM for the current frame.
     * Allocated lazily and reused — direct buffer to match AudioRecord
     * native reads with zero copy.
     */
    private var scratchGameBuffer: ByteBuffer? = null

    override suspend fun configure(config: AudioSourceConfig) {
        micSource.configure(config)
        gameSource.configure(config)
    }

    override suspend fun startStream() {
        micSource.startStream()
        gameSource.startStream()
        _isStreamingFlow.tryEmit(true)
    }

    override suspend fun stopStream() {
        try { micSource.stopStream() } catch (_: Throwable) { /* keep going */ }
        try { gameSource.stopStream() } catch (_: Throwable) { /* keep going */ }
        _isStreamingFlow.tryEmit(false)
    }

    override fun release() {
        try { micSource.release() } catch (_: Throwable) { /* keep going */ }
        try { gameSource.release() } catch (_: Throwable) { /* keep going */ }
        scratchGameBuffer = null
        _isStreamingFlow.tryEmit(false)
    }

    override fun fillAudioFrame(frame: RawFrame): RawFrame {
        // 1. Mic fills the frame buffer AND sets the canonical timestamp.
        micSource.fillAudioFrame(frame)

        // 2. Read game into a scratch buffer of the same capacity.
        val capacity = frame.rawBuffer.capacity()
        val scratch = ensureScratch(capacity)
        scratch.clear()
        val gameFrame = RawFrame(scratch, 0L)
        val gameOk = try {
            gameSource.fillAudioFrame(gameFrame); true
        } catch (_: Throwable) {
            false
        }

        // 3. Mix in place: mic[i] = clip(mic[i]*gMic + game[i]*gGame).
        // Mic gain is applied even when the game read fails, so a 0%
        // mic gain still silences the channel during a game outage.
        mixInPlace(frame.rawBuffer, scratch, gameOk)
        return frame
    }

    override fun getAudioFrame(frameFactory: IReadOnlyRawFrameFactory): RawFrame {
        // Delegate frame allocation + timestamp authority to the mic child,
        // then mix the game source on top.
        val frame = micSource.getAudioFrame(frameFactory)
        val capacity = frame.rawBuffer.capacity()
        val scratch = ensureScratch(capacity)
        scratch.clear()
        val gameFrame = RawFrame(scratch, 0L)
        val gameOk = try {
            gameSource.fillAudioFrame(gameFrame); true
        } catch (_: Throwable) {
            false
        }
        mixInPlace(frame.rawBuffer, scratch, gameOk)
        return frame
    }

    private fun ensureScratch(capacity: Int): ByteBuffer {
        val cur = scratchGameBuffer
        if (cur != null && cur.capacity() >= capacity) {
            return cur
        }
        val fresh = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        scratchGameBuffer = fresh
        return fresh
    }

    /**
     * Mixes 16-bit signed PCM from [game] into [mic], in place, using
     * absolute byte indexing so no buffer position is mutated.
     *
     * IMPORTANT — `AudioRecord.read(ByteBuffer, sizeInBytes)` writes
     * PCM bytes into the direct buffer but does NOT advance the buffer
     * position (Android SDK behavior). The valid byte range is therefore
     * the buffer capacity, not the position. Both children share the
     * same [AudioSourceConfig], so mic.capacity() == game.capacity().
     *
     * [gameOk] is false when the game child threw on read — in that case
     * the game gain is forced to 0 so the mic gain still applies cleanly
     * (a slider at 0% mic must silence the mic even during a game outage).
     */
    private fun mixInPlace(mic: ByteBuffer, game: ByteBuffer, gameOk: Boolean) {
        val gMic = AudioGains.micGain
        val gGame = if (gameOk) AudioGains.gameGain else 0f
        mic.order(ByteOrder.nativeOrder())
        game.order(ByteOrder.nativeOrder())
        val bytes = minOf(mic.capacity(), game.capacity())
        var i = 0
        while (i < bytes - 1) {
            val m = mic.getShort(i).toInt()
            val g = game.getShort(i).toInt()
            var mixed = (m * gMic + g * gGame).toInt()
            if (mixed > 32767) mixed = 32767
            else if (mixed < -32768) mixed = -32768
            mic.putShort(i, mixed.toShort())
            i += 2
        }
    }
}
