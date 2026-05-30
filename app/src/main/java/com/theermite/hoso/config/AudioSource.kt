package com.theermite.hoso.config

/**
 * Audio capture source chosen by the user at stream setup.
 *
 * MIC — microphone only. Full coverage on all Android versions. The
 *       microphone physically captures whatever it can hear, including
 *       game audio leaking through the device speaker, so this mode is
 *       acoustically a "mic + ambient" mix.
 *
 * MIX — true software mix of microphone + internal playback capture
 *       (AudioPlaybackCapture, requires Android 10+). Two AudioRecord
 *       instances are read in parallel and their 16-bit PCM samples
 *       are added with independent gains (see [com.theermite.hoso.audio.AudioGains]).
 *
 * Pure "game only" mode was dropped in G7.1 Phase B.2: users who want
 * mic muted just slide the mic gain to 0 in MIX mode.
 *
 * `storageKey` is persisted in SharedPreferences and must remain stable
 * across versions. Legacy `"game"` keys are migrated to MIX.
 */
enum class AudioSource(val storageKey: String) {
    MIC("mic"),
    MIX("mix");

    companion object {
        fun fromStorageKey(key: String?): AudioSource = when (key) {
            "mix" -> MIX
            // Legacy migration: pre-B.2 builds stored "game" for the
            // internal-playback-only mode. We fold it into MIX so the
            // user keeps capturing game audio after upgrade.
            "game" -> MIX
            else -> MIC
        }
    }
}
