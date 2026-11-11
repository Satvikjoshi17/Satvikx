package com.satvik.satvikx.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.satvik.satvikx.BuildConfig
import com.satvik.satvikx.data.local.dao.BlendDao
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.remote.supabase.dto.PlaybackSessionAnalyticsDto
import com.satvik.satvikx.data.remote.supabase.dto.ProfileDto
import com.satvik.satvikx.data.remote.supabase.dto.UserAnalyticsDto
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Enterprise Supabase Analytics Engine for SatvikX.
 *
 * Captures clean, user-wise analytics in Supabase:
 * 1. User Summary Analytics ('user_analytics'): User-wise aggregated listening time in seconds,
 *    human-readable listening duration, songs played, skips, completions, top artists/genres,
 *    device diagnostics, and exact Indian Standard Time (IST, UTC+05:30) timestamps.
 * 2. Granular Playback Session Log ('user_playback_sessions'): Track-by-track listening durations,
 *    completion percentages, and IST timestamps.
 * 3. Keeps Supabase 'profiles' table updated with latest listening time and IST active timestamp.
 */
@Singleton
class SupabaseAnalyticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val authRepositoryProvider: Provider<AuthRepository>,
    private val playlistDao: PlaylistDao,
    private val blendDao: BlendDao,
    private val recommendationRepositoryProvider: Provider<RecommendationRepository>
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("satvikx_user_analytics", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SupabaseAnalytics"
        private const val PREF_TOTAL_LISTENING_SECONDS = "total_listening_seconds"
        private const val PREF_TOTAL_SONGS_PLAYED = "total_songs_played"
        private const val PREF_TOTAL_COMPLETED_SONGS = "total_completed_songs"
        private const val PREF_TOTAL_SKIPS = "total_skips"
        private const val PREF_LAST_PLAYED_SONG = "last_played_song"
        private const val PREF_LAST_PLAYED_ARTIST = "last_played_artist"
    }

    /**
     * Returns the current wall-clock timestamp formatted in India Standard Time (IST, UTC+05:30).
     * e.g. "11 Nov 2026, 01:22:01 PM IST"
     */
    fun getIndiaTimestamp(timeMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a 'IST'", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return sdf.format(Date(timeMs))
    }

    /**
     * Converts seconds into a clear, human-readable format.
     * e.g. 9452s -> "2h 37m 32s"
     */
    fun formatListeningSecondsHuman(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Detailed device model and OS identifier.
     */
    fun getDeviceModelInfo(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        return "$manufacturer $model (Android $release, API $sdk)"
    }

    /**
     * Records a completed or skipped track playback session in real time.
     * Increments cumulative listening counters and syncs to Supabase tables:
     * 1. 'user_analytics': User-wise aggregated analytics card
     * 2. 'user_playback_sessions': Granular per-song listening log
     * 3. 'profiles': Latest profile update
     */
    fun recordTrackPlayback(
        track: TrackEntity,
        listenedSeconds: Long,
        isCompleted: Boolean,
        isSkipped: Boolean
    ) {
        if (listenedSeconds <= 0L) return

        scope.launch {
            try {
                // 1. Update local cumulative counters in SharedPreferences
                val authRepo = authRepositoryProvider.get()
                val currentUser = authRepo.currentUser.value
                val authSeconds = currentUser?.listeningSeconds ?: 0L
                val currentTotalSec = maxOf(
                    prefs.getLong(PREF_TOTAL_LISTENING_SECONDS, 0L) + listenedSeconds,
                    authSeconds + listenedSeconds
                )
                val currentPlayed = prefs.getInt(PREF_TOTAL_SONGS_PLAYED, 0) + 1
                val currentCompleted = prefs.getInt(PREF_TOTAL_COMPLETED_SONGS, 0) + if (isCompleted) 1 else 0
                val currentSkips = prefs.getInt(PREF_TOTAL_SKIPS, 0) + if (isSkipped) 1 else 0

                prefs.edit()
                    .putLong(PREF_TOTAL_LISTENING_SECONDS, currentTotalSec)
                    .putInt(PREF_TOTAL_SONGS_PLAYED, currentPlayed)
                    .putInt(PREF_TOTAL_COMPLETED_SONGS, currentCompleted)
                    .putInt(PREF_TOTAL_SKIPS, currentSkips)
                    .putString(PREF_LAST_PLAYED_SONG, track.title)
                    .putString(PREF_LAST_PLAYED_ARTIST, track.artist)
                    .apply()

                val nowIst = getIndiaTimestamp()
                val humanDuration = formatListeningSecondsHuman(currentTotalSec)

                val supabaseUser = supabaseClient.auth.currentUserOrNull()

                val effectiveUserId = supabaseUser?.id ?: currentUser?.id ?: "local_user_${prefs.getString("fallback_uid", null) ?: run {
                    val newId = UUID.randomUUID().toString()
                    prefs.edit().putString("fallback_uid", newId).apply()
                    newId
                }}"

                val effectiveDisplayName = currentUser?.displayName?.ifBlank { null }
                    ?: supabaseUser?.userMetadata?.get("name")?.toString()?.replace("\"", "")
                    ?: "Stark Operative"

                val effectiveEmail = currentUser?.email ?: supabaseUser?.email ?: if (currentUser?.isGuest == true) "Guest" else null
                val effectiveAvatar = currentUser?.avatarUrl?.ifBlank { "⚡" } ?: "⚡"

                // 2. Insert into 'user_playback_sessions' (granular log)
                try {
                    val durationSec = track.durationSeconds.coerceAtLeast(1L)
                    val completionPercent = ((listenedSeconds.toFloat() / durationSec.toFloat()) * 100f).coerceIn(0f, 100f)

                    val sessionDto = PlaybackSessionAnalyticsDto(
                        userId = effectiveUserId,
                        userName = effectiveDisplayName,
                        trackId = track.id,
                        trackTitle = track.title,
                        trackArtist = track.artist,
                        listenedSeconds = listenedSeconds,
                        trackDurationSeconds = durationSec,
                        completionPercent = completionPercent,
                        isCompleted = isCompleted,
                        isSkipped = isSkipped,
                        timestampIst = nowIst
                    )
                    supabaseClient.from("user_playback_sessions").insert(sessionDto)
                    Log.d(TAG, "Recorded session to Supabase user_playback_sessions: ${track.title} ($listenedSeconds s)")
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal: user_playback_sessions table sync skipped: ${e.message}")
                }

                // 3. Upsert user summary to 'user_analytics'
                try {
                    val userAnalyticsDto = UserAnalyticsDto(
                        userId = effectiveUserId,
                        displayName = effectiveDisplayName,
                        email = effectiveEmail,
                        isGuest = currentUser?.isGuest ?: (effectiveEmail == "Guest"),
                        avatar = effectiveAvatar,
                        totalListeningSeconds = currentTotalSec,
                        listeningTimeHuman = humanDuration,
                        totalSongsPlayed = currentPlayed,
                        totalCompletedSongs = currentCompleted,
                        totalSkips = currentSkips,
                        lastPlayedSong = track.title,
                        lastPlayedArtist = track.artist,
                        topArtists = getTopArtistsSummary(),
                        topGenres = getTopGenresSummary(),
                        likedSongsCount = getLikedSongsCount(),
                        playlistsCount = getPlaylistsCount(),
                        blendsCount = getBlendsCount(),
                        deviceInfo = getDeviceModelInfo(),
                        appVersion = BuildConfig.VERSION_NAME,
                        timezone = "Asia/Kolkata (IST)",
                        lastActiveIst = nowIst,
                        updatedAtIst = nowIst
                    )
                    supabaseClient.from("user_analytics").upsert(userAnalyticsDto)
                    Log.d(TAG, "Upserted user analytics for $effectiveDisplayName: $humanDuration ($currentTotalSec s)")
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal: user_analytics table sync skipped: ${e.message}")
                }

                // 4. Update 'profiles' table with latest listening time & Indian timestamp
                try {
                    if (supabaseUser != null) {
                        val profileDto = ProfileDto(
                            id = supabaseUser.id,
                            displayName = effectiveDisplayName,
                            avatarUrl = effectiveAvatar,
                            isGuest = currentUser?.isGuest ?: false,
                            listeningSeconds = currentTotalSec,
                            listeningTimeHuman = humanDuration,
                            lastActiveIst = nowIst,
                            deviceModel = getDeviceModelInfo()
                        )
                        supabaseClient.from("profiles").upsert(profileDto)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal: profiles table sync skipped: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to record playback analytics", e)
            }
        }
    }

    /**
     * Synchronizes full user analytics (profile, device info, top genres, artists, playlists, and IST timestamp).
     */
    suspend fun syncUserFullAnalytics() = withContext(Dispatchers.IO) {
        try {
            val nowIst = getIndiaTimestamp()
            val authRepo = authRepositoryProvider.get()
            val currentUser = authRepo.currentUser.value
            val authSeconds = currentUser?.listeningSeconds ?: 0L
            val totalSeconds = maxOf(prefs.getLong(PREF_TOTAL_LISTENING_SECONDS, 0L), authSeconds)
            val humanDuration = formatListeningSecondsHuman(totalSeconds)

            val supabaseUser = supabaseClient.auth.currentUserOrNull()

            val effectiveUserId = supabaseUser?.id ?: currentUser?.id ?: prefs.getString("fallback_uid", null) ?: return@withContext
            val effectiveDisplayName = currentUser?.displayName?.ifBlank { null }
                ?: supabaseUser?.userMetadata?.get("name")?.toString()?.replace("\"", "")
                ?: "Stark Operative"
            val effectiveEmail = currentUser?.email ?: supabaseUser?.email ?: if (currentUser?.isGuest == true) "Guest" else null
            val effectiveAvatar = currentUser?.avatarUrl?.ifBlank { "⚡" } ?: "⚡"

            val analyticsDto = UserAnalyticsDto(
                userId = effectiveUserId,
                displayName = effectiveDisplayName,
                email = effectiveEmail,
                isGuest = currentUser?.isGuest ?: (effectiveEmail == "Guest"),
                avatar = effectiveAvatar,
                totalListeningSeconds = totalSeconds,
                listeningTimeHuman = humanDuration,
                totalSongsPlayed = prefs.getInt(PREF_TOTAL_SONGS_PLAYED, 0),
                totalCompletedSongs = prefs.getInt(PREF_TOTAL_COMPLETED_SONGS, 0),
                totalSkips = prefs.getInt(PREF_TOTAL_SKIPS, 0),
                lastPlayedSong = prefs.getString(PREF_LAST_PLAYED_SONG, "").orEmpty(),
                lastPlayedArtist = prefs.getString(PREF_LAST_PLAYED_ARTIST, "").orEmpty(),
                topArtists = getTopArtistsSummary(),
                topGenres = getTopGenresSummary(),
                likedSongsCount = getLikedSongsCount(),
                playlistsCount = getPlaylistsCount(),
                blendsCount = getBlendsCount(),
                deviceInfo = getDeviceModelInfo(),
                appVersion = BuildConfig.VERSION_NAME,
                timezone = "Asia/Kolkata (IST)",
                lastActiveIst = nowIst,
                updatedAtIst = nowIst
            )

            try {
                supabaseClient.from("user_analytics").upsert(analyticsDto)
                Log.d(TAG, "Full analytics synced to user_analytics for $effectiveDisplayName")
            } catch (e: Exception) {
                Log.w(TAG, "user_analytics upsert warning: ${e.message}")
            }

            try {
                if (supabaseUser != null) {
                    val profileDto = ProfileDto(
                        id = supabaseUser.id,
                        displayName = effectiveDisplayName,
                        avatarUrl = effectiveAvatar,
                        isGuest = currentUser?.isGuest ?: false,
                        listeningSeconds = totalSeconds,
                        listeningTimeHuman = humanDuration,
                        lastActiveIst = nowIst,
                        deviceModel = getDeviceModelInfo()
                    )
                    supabaseClient.from("profiles").upsert(profileDto)
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing full user analytics", e)
        }
    }

    private suspend fun getLikedSongsCount(): Int {
        return try {
            val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
            if (likedPlaylist != null) {
                playlistDao.getPlaylistWithTracksSync(likedPlaylist.playlistId)?.tracks?.size ?: 0
            } else 0
        } catch (_: Exception) { 0 }
    }

    private suspend fun getPlaylistsCount(): Int {
        return try {
            playlistDao.getAllPlaylistsWithTracksSync().count { it.playlist.isCustom }
        } catch (_: Exception) { 0 }
    }

    private suspend fun getBlendsCount(): Int {
        return try {
            blendDao.getAllBlendsSync().size
        } catch (_: Exception) { 0 }
    }

    private suspend fun getTopArtistsSummary(): String {
        return try {
            val affinities = recommendationRepositoryProvider.get().analyzeUserAffinities()
            affinities.overallTopArtists.take(5).joinToString(", ")
        } catch (_: Exception) { "" }
    }

    private suspend fun getTopGenresSummary(): String {
        return try {
            val affinities = recommendationRepositoryProvider.get().analyzeUserAffinities()
            affinities.topGenres.take(4).joinToString(", ")
        } catch (_: Exception) { "" }
    }
}
