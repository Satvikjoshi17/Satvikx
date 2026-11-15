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
import androidx.media3.session.DefaultMediaNotificationProvider
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

    @Inject
    lateinit var trackDao: com.satvik.satvikx.data.local.dao.TrackDao

    @Inject
    lateinit var playbackSessionManager: PlaybackSessionManager

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "satvikx_playback_channel"
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 0. Ensure persistent playback notification channel exists on API 26+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(com.satvik.satvikx.R.string.playback_notification_channel_name),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SatvikX Continuous Music Playback"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        // Configure Media3 Notification Provider with dedicated notification channel (auto-picks @drawable/media3_notification_small_icon)
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(com.satvik.satvikx.R.string.playback_notification_channel_name)
            .build()
        setMediaNotificationProvider(notificationProvider)

        // Initialize high-performance WifiLock and CPU WakeLock to guarantee uninterrupted background playback across Doze mode
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SatvikX:PlaybackWakeLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {}

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

        // 1. Configure optimized buffering strategy via DefaultLoadControl for rapid initial start
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15000,
                /* maxBufferMs = */ 50000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1500
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

        // 7. Auto-restore last session if player is empty (e.g. system service restart under memory pressure)
        restoreSessionIfEmpty()
    }

    private fun restoreSessionIfEmpty() {
        val p = player ?: return
        if (p.mediaItemCount == 0) {
            val session = playbackSessionManager.getLastPlaybackSession()
            if (session != null && session.queue.isNotEmpty()) {
                val targetIdx = session.queueIndex.coerceIn(0, session.queue.size - 1)
                val mediaItems = session.queue.map { track ->
                    val uri = when {
                        !track.localPath.isNullOrBlank() -> {
                            if (track.localPath.startsWith("file://")) android.net.Uri.parse(track.localPath)
                            else android.net.Uri.fromFile(java.io.File(track.localPath))
                        }
                        !track.streamUrl.isNullOrBlank() -> android.net.Uri.parse(track.streamUrl)
                        else -> android.net.Uri.EMPTY
                    }
                    MediaItem.Builder()
                        .setMediaId(track.id)
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setAlbumTitle("SatvikX")
                                .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) android.net.Uri.parse(track.thumbnailUrl) else null)
                                .setExtras(Bundle().apply {
                                    putLong("duration_seconds", track.durationSeconds)
                                })
                                .build()
                        )
                        .build()
                }
                p.setMediaItems(mediaItems, targetIdx, session.positionMs)
                p.prepare()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val activePlayer = session.player
        // Keep the service firmly in foreground while playing or transitioning between tracks.
        // Prevents Android 14+ from dropping the notification or restricting background playback.
        val shouldRemainInForeground = startInForegroundRequired ||
            activePlayer.playWhenReady ||
            activePlayer.playbackState == Player.STATE_BUFFERING ||
            activePlayer.playbackState == Player.STATE_READY

        super.onUpdateNotification(session, shouldRemainInForeground)
    }

    private fun updateLocks() {
        try {
            val activePlayer = player
            val shouldHold = activePlayer != null && activePlayer.playWhenReady && activePlayer.playbackState != Player.STATE_IDLE
            if (shouldHold) {
                if (wakeLock?.isHeld == false) wakeLock?.acquire(45 * 60 * 1000L) // 45-min safety timeout
                if (wifiLock?.isHeld == false) wifiLock?.acquire()
            } else {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                if (wifiLock?.isHeld == true) wifiLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer != null) {
            playbackSessionManager.savePosition(activePlayer.currentPosition.coerceAtLeast(0L))
        }
        // Keep service alive if media is actively playing or buffering
        if (activePlayer == null || (!activePlayer.playWhenReady && activePlayer.playbackState != Player.STATE_BUFFERING)) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        val activePlayer = player
        if (activePlayer != null) {
            playbackSessionManager.savePosition(activePlayer.currentPosition.coerceAtLeast(0L))
        }
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
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
            updateLocks()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateLocks()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateLocks()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.e("PlaybackService", "ExoPlayer service-level error: ${error.errorCodeName} (${error.errorCode}) - ${error.message}", error)
            // Note: Auto-recovery and fresh stream URL re-resolution is handled gracefully by
            // PlaybackConnectionManager. Do NOT call p.prepare() here on the stale item to avoid infinite buffering loops.
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId ?: return
            serviceScope.launch {
                try {
                    val metadata = mediaItem.mediaMetadata
                    val existing = trackDao.getTrackByIdSync(trackId)
                    if (existing == null) {
                        val duration = (metadata.extras?.getLong("duration_seconds") ?: 0L).coerceAtLeast(0L)
                        val newTrack = com.satvik.satvikx.data.local.entity.TrackEntity(
                            id = trackId,
                            title = metadata.title?.toString() ?: "Unknown Title",
                            artist = metadata.artist?.toString() ?: "Unknown Artist",
                            durationSeconds = duration,
                            thumbnailUrl = metadata.artworkUri?.toString() ?: "",
                            addedAtTimestamp = System.currentTimeMillis()
                        )
                        trackDao.insertTrack(newTrack)
                    }
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

            // Expose player commands so Android 13/14 SystemUI renders full scrubber, previous/next, play/pause
            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()

            return MediaSession.ConnectionResult.accept(
                sessionCommands.build(),
                availablePlayerCommands
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
