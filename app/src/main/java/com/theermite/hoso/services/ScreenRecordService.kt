package com.theermite.hoso.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.lifecycleScope
import com.theermite.hoso.MainActivity
import com.theermite.hoso.R
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.UriMediaDescriptor
import io.github.thibaultbee.streampack.core.elements.sources.audio.IAudioSourceInternal
import android.media.MediaRecorder
import com.theermite.hoso.audio.MixedAudioSourceFactory
import com.theermite.hoso.config.AudioSource
import com.theermite.hoso.config.StreamConfig
import io.github.thibaultbee.streampack.core.elements.sources.audio.audiorecord.MicrophoneSourceFactory
import io.github.thibaultbee.streampack.core.interfaces.IWithAudioSource
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.ISingleStreamer
import io.github.thibaultbee.streampack.services.MediaProjectionService
import io.github.thibaultbee.streampack.services.utils.SingleStreamerFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
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
        // G7.1 Phase B.2 — bitmask doit matcher la source réelle :
        //  - MIC : MEDIA_PROJECTION | MICROPHONE (Android 14+ requiert MICROPHONE
        //          sinon AppOps silence le mic, observé ColorOS)
        //  - MIX : MEDIA_PROJECTION | MICROPHONE (mic ET playback capture
        //          actifs simultanément, donc on déclare les deux types)
        //  Source officielle : developer.android.com/develop/background-work/
        //  services/fgs/service-types — "mediaProjection is sufficient for
        //  capturing audio via the AudioPlaybackCapture API", auquel on ajoute
        //  MICROPHONE dès qu'un AudioRecord MIC est actif.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val source = StreamConfig(this).audioSource
            val fgsType = when (source) {
                AudioSource.MIC,
                AudioSource.MIX ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                onOpenNotification(),
                fgsType
            )
        }
        // Mark alive only after startForeground succeeded. OverlayService
        // reads this before sending ACTION_QUERY_STATE to avoid spawning
        // this service without a MediaProjection consent token.
        isRunning = true
    }

    override fun createDefaultAudioSource(
        mediaProjection: MediaProjection,
        extras: Bundle
    ): IAudioSourceInternal.Factory {
        // G7.1 Phase B.2 — MIC = upstream microphone source only.
        // MIX = our composite MixedAudioSource (mic + AudioPlaybackCapture,
        // PCM-summed with independent gains read live from AudioGains).
        val source = StreamConfig(this).audioSource
        return when (source) {
            AudioSource.MIC -> MicrophoneSourceFactory(
                audioSource = MediaRecorder.AudioSource.MIC,
                effects = emptySet()
            )
            AudioSource.MIX -> MixedAudioSourceFactory(mediaProjection)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                streamStartedAt = 0L
                reconnectAttemptJob?.cancel()
                reconnectWatcherJob?.cancel()
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
            ACTION_REMEMBER_URL -> {
                val url = intent.getStringExtra(EXTRA_RTMP_URL)
                if (!url.isNullOrBlank()) {
                    currentRtmpUrl = url
                    // G5.1 — first time we get a URL = stream just
                    // started (MainActivity sends this immediately
                    // after streamer.startStream returns). We don't
                    // reset on subsequent reconnects so the HUD timer
                    // keeps the original session duration.
                    if (streamStartedAt == 0L) {
                        streamStartedAt = System.currentTimeMillis()
                    }
                    startReconnectWatcher()
                    broadcastState()
                }
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMute()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                toggleMask(MASK_PAUSE)
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PRIVACY -> {
                toggleMask(MASK_PRIVACY)
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

    /**
     * Toggle a mask mode (pause / privacy). Activating a mask:
     * - Saves the pre-mask mute state, then force-mutes
     * - Asks the overlay to draw a fullscreen opaque mask
     * Deactivating restores the pre-mask mute state.
     * Switching directly between two mask modes preserves the saved
     * "pre-first-mask" mute state so a PAUSE -> PRIVACY -> off cycle
     * still ends on the user's original mic state.
     */
    private fun toggleMask(newMode: String) {
        val audio = (streamer as? IWithAudioSource)?.audioInput
        if (currentMask == newMode) {
            // toggle off
            audio?.isMuted = muteBeforeMask
            currentMask = MASK_NONE
        } else {
            if (currentMask == MASK_NONE) {
                muteBeforeMask = audio?.isMuted ?: false
            }
            audio?.isMuted = true
            currentMask = newMode
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
                .putExtra(EXTRA_MASK_MODE, currentMask)
                .putExtra(EXTRA_STREAM_STARTED_AT, streamStartedAt)
        )
    }

    private var currentMask: String = MASK_NONE
    private var muteBeforeMask: Boolean = false

    // G5.1 — wall-clock at first successful stream start. 0L = no
    // active stream. Used by OverlayService HUD to compute duration.
    // Survives reconnects.
    private var streamStartedAt: Long = 0L

    // G4.1 — Auto-reconnect state. URL is captured at first successful
    // startStream (MainActivity sends ACTION_REMEMBER_URL right after
    // streamer.startStream returns) so the service can re-call
    // startStream itself on disconnect without re-binding to the
    // activity. stopRequested guards against reconnect after a
    // user-initiated STOP.
    private var currentRtmpUrl: String? = null
    private var stopRequested: Boolean = false
    private var reconnectWatcherJob: Job? = null
    private var reconnectAttemptJob: Job? = null

    /**
     * Watch the endpoint's isOpenFlow. drop(1) skips the initial
     * StateFlow emission (already-known state). Transition to false
     * while we still have a remembered URL and the user has not
     * pressed STOP triggers the backoff loop. Transition back to true
     * cancels any in-flight loop and clears the reconnect UI.
     */
    private fun startReconnectWatcher() {
        if (reconnectWatcherJob?.isActive == true) return
        val s = streamer ?: return
        reconnectWatcherJob = lifecycleScope.launch {
            s.isOpenFlow
                .drop(1)
                .distinctUntilChanged()
                .collect { isOpen ->
                    if (stopRequested) return@collect
                    if (isOpen) {
                        // Successful (re)connection. If we were in a
                        // reconnect loop, kill it and tell the overlay.
                        if (reconnectAttemptJob?.isActive == true) {
                            reconnectAttemptJob?.cancel()
                            broadcastReconnect(
                                attempt = 0,
                                giveUp = false,
                                recovered = true
                            )
                            updateNotification(reconnecting = false)
                        }
                    } else if (currentRtmpUrl != null) {
                        // Unwanted disconnect — start backoff loop
                        reconnectAttemptJob?.cancel()
                        reconnectAttemptJob = launch { runReconnectLoop() }
                    }
                }
        }
    }

    /**
     * Backoff sequence 1 → 2 → 5 → 10 → 15 → 30 s, then capped at 30 s
     * up to MAX_RECONNECT_ATTEMPTS (20). Each iteration broadcasts the
     * current attempt number for the overlay Toast and updates the
     * FGS notification text. The parent watcher cancels this job as
     * soon as isOpenFlow flips back to true.
     */
    private suspend fun runReconnectLoop() = coroutineScope {
        val url = currentRtmpUrl ?: return@coroutineScope
        val descriptor = try {
            UriMediaDescriptor(Uri.parse(url))
        } catch (_: Exception) {
            return@coroutineScope
        }
        for (i in 0 until MAX_RECONNECT_ATTEMPTS) {
            if (!isActive || stopRequested) return@coroutineScope
            val attempt = i + 1
            val delaySec = RECONNECT_BACKOFF.getOrElse(i) { 30 }
            broadcastReconnect(
                attempt = attempt,
                giveUp = false,
                recovered = false
            )
            updateNotification(reconnecting = true, attempt = attempt)
            delay(delaySec * 1000L)
            if (!isActive || stopRequested) return@coroutineScope
            try {
                streamer?.startStream(descriptor)
                // Let the connection settle. If it succeeds the parent
                // watcher will cancel this coroutine; if it stays down
                // we loop to the next backoff slot.
                delay(SETTLE_MS)
            } catch (_: Exception) {
                // Endpoint refused or threw — keep trying.
            }
        }
        broadcastReconnect(
            attempt = MAX_RECONNECT_ATTEMPTS,
            giveUp = true,
            recovered = false
        )
        updateNotification(reconnecting = false)
    }

    private fun broadcastReconnect(
        attempt: Int,
        giveUp: Boolean,
        recovered: Boolean
    ) {
        sendBroadcast(
            Intent(BROADCAST_RECONNECT_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_RECONNECT_ATTEMPT, attempt)
                .putExtra(EXTRA_RECONNECT_GIVE_UP, giveUp)
                .putExtra(EXTRA_RECONNECT_RECOVERED, recovered)
        )
    }

    private fun updateNotification(
        reconnecting: Boolean,
        attempt: Int = 0
    ) {
        val nm = getSystemService(NotificationManager::class.java)
            ?: return
        val title = if (reconnecting) {
            getString(R.string.notification_reconnecting, attempt,
                MAX_RECONNECT_ATTEMPTS)
        } else {
            getString(R.string.notification_title)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stream)
            .setContentTitle(title)
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.btn_stop),
                buildStopPending(this)
            )
            .build()
        nm.notify(NOTIFICATION_ID, notif)
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

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        /**
         * Set to true once onCreate() has successfully promoted the service
         * to foreground with the mediaProjection FGS type — meaning a
         * MediaProjection consent token is present and the service is fully
         * alive. Cleared in onDestroy().
         *
         * OverlayService reads this before sending ACTION_QUERY_STATE so it
         * never instantiates this service when no projection token exists
         * (which would crash with SecurityException on Android 14+).
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val NOTIFICATION_ID = 0x484F534F
        const val CHANNEL_ID = "com.theermite.hoso.stream"
        const val ACTION_STOP =
            "com.theermite.hoso.STOP_STREAM"
        const val ACTION_TOGGLE_MUTE =
            "com.theermite.hoso.TOGGLE_MUTE"
        const val ACTION_TOGGLE_PAUSE =
            "com.theermite.hoso.TOGGLE_PAUSE"
        const val ACTION_TOGGLE_PRIVACY =
            "com.theermite.hoso.TOGGLE_PRIVACY"
        const val ACTION_QUERY_STATE =
            "com.theermite.hoso.QUERY_STATE"
        const val ACTION_REMEMBER_URL =
            "com.theermite.hoso.REMEMBER_URL"
        const val BROADCAST_STOPPED =
            "com.theermite.hoso.STREAM_STOPPED"
        const val BROADCAST_STATE_CHANGED =
            "com.theermite.hoso.STATE_CHANGED"
        const val BROADCAST_RECONNECT_STATE =
            "com.theermite.hoso.RECONNECT_STATE"
        const val EXTRA_IS_MUTED = "is_muted"
        const val EXTRA_MASK_MODE = "mask_mode"
        const val EXTRA_RTMP_URL = "rtmp_url"
        const val EXTRA_RECONNECT_ATTEMPT = "reconnect_attempt"
        const val EXTRA_RECONNECT_GIVE_UP = "reconnect_give_up"
        const val EXTRA_RECONNECT_RECOVERED = "reconnect_recovered"
        const val EXTRA_STREAM_STARTED_AT = "stream_started_at"

        // G4.1 — Auto-reconnect timings. 5 short attempts then capped
        // 30 s slots, 20 total = ~8 minutes before giving up. Covers
        // mobile network hiccups, wifi-to-4G handoffs, and short
        // Twitch ingest server flaps. Past that, manual restart is
        // the right call (likely longer outage).
        const val MAX_RECONNECT_ATTEMPTS = 20
        val RECONNECT_BACKOFF = listOf(1, 2, 5, 10, 15, 30)
        const val SETTLE_MS = 3_000L

        // Mask modes for the fullscreen pause / privacy overlay.
        // String constants keep the intent extras serializable
        // without bringing in @IntDef ceremony for 3 values.
        const val MASK_NONE = "none"
        const val MASK_PAUSE = "pause"
        const val MASK_PRIVACY = "privacy"

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
