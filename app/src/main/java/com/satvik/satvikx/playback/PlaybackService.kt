package com.satvik.satvikx.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.satvik.satvikx.MainActivity
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.entity.RecentPlaybackEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Enterprise Media3 MediaSessionService orchestrating background music playback,
 * system notifications, hardware lockscreen scrubber, and audio focus management.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var recentPlaybackDao: RecentPlaybackDao

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Initialize high-performance WifiLock to prevent Android OS from putting network radio to sleep during background playback
        try {
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val lockMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(lockMode, "SatvikX:PlaybackWifiLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {}

        // 1. Configure optimized buffering strategy via DefaultLoadControl
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20000,
                /* maxBufferMs = */ 60000,
                /* bufferForPlaybackMs = */ 1000,
                /* bufferForPlaybackAfterRebufferMs = */ 2000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 2. Configure AudioAttributes with media usage & music content type
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 3. Configure hybrid datasource: DefaultDataSource supports file://, content://, and delegates http(s):// to OkHttp
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, okHttpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        // 4. Instantiate ExoPlayer with background wake-lock and noisy headset listener
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                addListener(PlayerEventListener())
            }

        // 5. Activity launch pending intent for notifications
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 6. Build the MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
        wifiLock = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    /**
     * Intercepts media item transitions and playback errors to guarantee rock-solid playback.
     */
    private inner class PlayerEventListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            try {
                if (isPlaying) {
                    if (wifiLock?.isHeld == false) wifiLock?.acquire()
                } else {
                    if (player?.playbackState != Player.STATE_BUFFERING && wifiLock?.isHeld == true) {
                        wifiLock?.release()
                    }
                }
            } catch (_: Exception) {}
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                try {
                    if (wifiLock?.isHeld == false) wifiLock?.acquire()
                } catch (_: Exception) {}
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e("PlaybackService", "ExoPlayer service-level error: ${error.errorCodeName} - ${error.message}", error)
            // Auto-recovery: If network dropped or buffer timed out, auto-retry without stopping
            val p = player ?: return
            if (p.currentMediaItem != null) {
                p.prepare()
                p.play()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId ?: return
            serviceScope.launch {
                try {
                    recentPlaybackDao.recordRecentPlayback(
                        RecentPlaybackEntity(
                            trackId = trackId,
                            playedAtTimestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    // Suppress and continue playback
                }
            }
        }
    }

    /**
     * Callback for controlling player behavior and custom commands.
     */
    private inner class MediaSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
            return MediaSession.ConnectionResult.accept(
                sessionCommands.build(),
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
