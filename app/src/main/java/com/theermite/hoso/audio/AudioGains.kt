package com.theermite.hoso.audio

import java.util.concurrent.atomic.AtomicInteger

/**
 * Volatile mix gains shared between the UI (sliders in MainActivity and
 * the floating overlay) and the audio pipeline (MixedAudioSource reads
 * them at every frame). Lock-free, allocation-free updates.
 *
 * Gains are stored as integer permil (0..2000) so dragging a slider is
 * a single int write — 1000 = 100% (unity), 600 = 60%, 2000 = 200%.
 *
 * Persistence is owned by StreamConfig; this object is the in-memory
 * source of truth during a live session and is restored from prefs at
 * StreamConfig construction.
 */
object AudioGains {
    private val mic = AtomicInteger(GAIN_DEFAULT_MIC)
    private val game = AtomicInteger(GAIN_DEFAULT_GAME)

    var micGainPermil: Int
        get() = mic.get()
        set(value) { mic.set(value.coerceIn(GAIN_MIN, GAIN_MAX)) }

    var gameGainPermil: Int
        get() = game.get()
        set(value) { game.set(value.coerceIn(GAIN_MIN, GAIN_MAX)) }

    /** Multiplier in [0.0, 2.0] for the mic channel. */
    val micGain: Float get() = mic.get() / 1000f

    /** Multiplier in [0.0, 2.0] for the game audio channel. */
    val gameGain: Float get() = game.get() / 1000f

    const val GAIN_MIN = 0
    const val GAIN_MAX = 2000
    const val GAIN_DEFAULT_MIC = 1000
    const val GAIN_DEFAULT_GAME = 600
}
