package com.satvik.satvikx.data.repository

import android.content.Context
import android.util.Log
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.PlaylistEntity
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.extractPolishedArtist
import com.satvik.satvikx.data.local.entity.extractPolishedTitle
import com.satvik.satvikx.data.remote.supabase.dto.UserFavoriteDto
import com.satvik.satvikx.data.remote.supabase.dto.UserPlaylistDto
import com.satvik.satvikx.data.remote.supabase.dto.UserPlaylistTrackDto
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val message: String) : SyncState
    data class Error(val message: String) : SyncState
}

@Singleton
class CloudSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val syncPrefs = context.getSharedPreferences("satvikx_cloud_sync_cache", Context.MODE_PRIVATE)

    init {
        // Auto-sync whenever user logs in or profile emits
        scope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    syncAll()
                }
            }
        }
    }

    // =========================================================================
    // PERSISTENT OFFLINE PENDING ACTIONS (SURVIVES APP RESTARTS)
    // =========================================================================

    @Synchronized
    private fun getPendingFavoriteDeletions(): Set<String> {
        return syncPrefs.getStringSet("pending_fav_deletions", emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    private fun addPendingFavoriteDeletion(trackId: String) {
        val set = getPendingFavoriteDeletions().toMutableSet()
        set.add(trackId)
        syncPrefs.edit().putStringSet("pending_fav_deletions", set).apply()
    }

    @Synchronized
    private fun removePendingFavoriteDeletion(trackId: String) {
        val set = getPendingFavoriteDeletions().toMutableSet()
        if (set.remove(trackId)) {
            syncPrefs.edit().putStringSet("pending_fav_deletions", set).apply()
        }
    }

    @Synchronized
    private fun getPendingPlaylistTrackDeletions(): Set<String> {
        return syncPrefs.getStringSet("pending_playlist_track_deletions", emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    private fun addPendingPlaylistTrackDeletion(playlistName: String, trackId: String) {
        val set = getPendingPlaylistTrackDeletions().toMutableSet()
        set.add("$playlistName\t$trackId")
        syncPrefs.edit().putStringSet("pending_playlist_track_deletions", set).apply()
    }

    @Synchronized
    private fun removePendingPlaylistTrackDeletion(key: String) {
        val set = getPendingPlaylistTrackDeletions().toMutableSet()
        if (set.remove(key)) {
            syncPrefs.edit().putStringSet("pending_playlist_track_deletions", set).apply()
        }
    }

    @Synchronized
    private fun getPendingPlaylistDeletions(): Set<String> {
        return syncPrefs.getStringSet("pending_playlist_deletions", emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    private fun addPendingPlaylistDeletion(playlistName: String) {
        val set = getPendingPlaylistDeletions().toMutableSet()
        set.add(playlistName)
        syncPrefs.edit().putStringSet("pending_playlist_deletions", set).apply()
    }

    @Synchronized
    private fun removePendingPlaylistDeletion(playlistName: String) {
        val set = getPendingPlaylistDeletions().toMutableSet()
        if (set.remove(playlistName)) {
            syncPrefs.edit().putStringSet("pending_playlist_deletions", set).apply()
        }
    }

    private fun isInitialSyncCompleted(userId: String): Boolean {
        return syncPrefs.getBoolean("initial_sync_done_$userId", false)
    }

    private fun setInitialSyncCompleted(userId: String, done: Boolean) {
        syncPrefs.edit().putBoolean("initial_sync_done_$userId", done).apply()
    }

    // =========================================================================
    // CORE FULL RECONCILIATION SYNC
    // =========================================================================

    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser.value ?: return@withContext Result.failure(
            IllegalStateException("No active Stark account to sync")
        )

        try {
            _syncState.value = SyncState.Syncing

            // 1. Flush any pending deletions to Supabase first so remote is cleaned
            flushPendingDeletions(user.id)

            // 2. Ensure local 'Liked Songs' playlist exists
            var likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
            if (likedPlaylist == null) {
                val newId = playlistDao.insertPlaylist(PlaylistEntity(name = "Liked Songs"))
                likedPlaylist = PlaylistEntity(playlistId = newId, name = "Liked Songs")
            }

            // 3. Fetch remote favorites from Supabase
            val remoteFavorites = try {
                supabaseClient.from("user_favorites")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserFavoriteDto>()
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to fetch remote favorites: ${e.message}")
                emptyList()
            }

            val currentPendingFavDeletes = getPendingFavoriteDeletions()
            val validRemoteFavorites = remoteFavorites.filterNot { it.trackId in currentPendingFavDeletes }
            val remoteTrackIds = validRemoteFavorites.map { it.trackId }.toSet()

            // Insert valid remote favorites into local Room DB
            validRemoteFavorites.forEach { fav ->
                trackDao.insertTrack(
                    TrackEntity(
                        id = fav.trackId,
                        title = fav.title,
                        artist = fav.artist,
                        durationSeconds = fav.durationSeconds.toLong(),
                        thumbnailUrl = fav.thumbnailUrl
                    )
                )
                playlistDao.insertPlaylistTrackCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = likedPlaylist.playlistId,
                        trackId = fav.trackId
                    )
                )
            }

            val localLikedTrackIds = playlistDao.getLikedTrackIds().firstOrNull() ?: emptyList()
            val hasInitialSyncCompleted = isInitialSyncCompleted(user.id)

            if (!hasInitialSyncCompleted) {
                // First-time sync for this user: upload local liked songs not in remote to Supabase
                val toUpload = localLikedTrackIds.filter { it !in remoteTrackIds && it !in currentPendingFavDeletes }
                for (trackId in toUpload) {
                    val track = trackDao.getTrackByIdSync(trackId)
                    if (track != null) {
                        val cleanT = extractPolishedTitle(track.title, track.artist)
                        val cleanA = extractPolishedArtist(track.title, track.artist)
                        val cleanThumb = if (track.thumbnailUrl.contains("hqdefault.jpg")) {
                            track.thumbnailUrl.replace("hqdefault.jpg", "mqdefault.jpg")
                        } else track.thumbnailUrl

                        try {
                            supabaseClient.from("user_favorites").upsert(
                                UserFavoriteDto(
                                    userId = user.id,
                                    trackId = track.id,
                                    title = cleanT,
                                    artist = cleanA,
                                    thumbnailUrl = cleanThumb,
                                    durationSeconds = track.durationSeconds.toInt()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            } else {
                // Subsequent sync: remote is authoritative. If a track is in local DB but missing from remote
                // and NOT in pending additions, it was deleted in the cloud: delete locally from Room DB!
                val toDeleteLocally = localLikedTrackIds.filter { it !in remoteTrackIds }
                for (trackId in toDeleteLocally) {
                    playlistDao.deletePlaylistTrackCrossRef(likedPlaylist.playlistId, trackId)
                }
            }

            // 4. Bidirectional Custom Playlist & Track Synchronization
            val remotePlaylists = try {
                supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to fetch remote playlists: ${e.message}")
                emptyList()
            }

            val currentPendingPlaylistDeletes = getPendingPlaylistDeletions()
            val validRemotePlaylists = remotePlaylists.filterNot { it.name in currentPendingPlaylistDeletes }
            val currentPendingTrackDeletes = getPendingPlaylistTrackDeletions()

            // A. Reconcile remote playlists & tracks to local Room DB
            for (remoteP in validRemotePlaylists) {
                var localP = playlistDao.getPlaylistByNameSync(remoteP.name)
                if (localP == null) {
                    val id = playlistDao.insertPlaylist(PlaylistEntity(name = remoteP.name))
                    localP = PlaylistEntity(playlistId = id, name = remoteP.name)
                }

                if (remoteP.id != null) {
                    val remoteTracks = try {
                        supabaseClient.from("user_playlist_tracks")
                            .select {
                                filter {
                                    eq("playlist_id", remoteP.id)
                                }
                            }
                            .decodeList<UserPlaylistTrackDto>()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val validRemoteTracks = remoteTracks.filterNot {
                        "${remoteP.name}\t${it.trackId}" in currentPendingTrackDeletes
                    }
                    val remoteTrackIdsForPlaylist = validRemoteTracks.map { it.trackId }.toSet()

                    validRemoteTracks.forEach { rTrack ->
                        trackDao.insertTrack(
                            TrackEntity(
                                id = rTrack.trackId,
                                title = rTrack.title,
                                artist = rTrack.artist,
                                durationSeconds = rTrack.durationSeconds.toLong(),
                                thumbnailUrl = rTrack.thumbnailUrl
                            )
                        )
                        playlistDao.insertPlaylistTrackCrossRef(
                            PlaylistTrackCrossRef(
                                playlistId = localP.playlistId,
                                trackId = rTrack.trackId
                            )
                        )
                    }

                    // If subsequent sync, reconcile deletions: local tracks not in remote are deleted from Room
                    if (hasInitialSyncCompleted) {
                        val localTracksForP = playlistDao.getPlaylistWithTracksSync(localP.playlistId)?.tracks.orEmpty()
                        val toDeleteFromLocal = localTracksForP.filter { it.id !in remoteTrackIdsForPlaylist }
                        for (track in toDeleteFromLocal) {
                            playlistDao.deletePlaylistTrackCrossRef(localP.playlistId, track.id)
                        }
                    }
                }
            }

            // B. Upload local custom playlists & their tracks to Supabase
            val localCustomPlaylists = playlistDao.getAllPlaylistsWithTracksSync()
                .filter { it.playlist.name != "Liked Songs" }

            for (localCP in localCustomPlaylists) {
                if (localCP.playlist.name in currentPendingPlaylistDeletes) continue

                val matchingRemote = validRemotePlaylists.firstOrNull { it.name.equals(localCP.playlist.name, ignoreCase = true) }
                val targetPlaylistId = matchingRemote?.id ?: run {
                    try {
                        val newDto = UserPlaylistDto(
                            userId = user.id,
                            name = localCP.playlist.name
                        )
                        val inserted = supabaseClient.from("user_playlists")
                            .insert(newDto) {
                                select()
                            }
                            .decodeSingleOrNull<UserPlaylistDto>()
                        inserted?.id
                    } catch (_: Exception) {
                        null
                    }
                }

                if (targetPlaylistId != null) {
                    val remoteTracksForPlaylist = try {
                        supabaseClient.from("user_playlist_tracks")
                            .select {
                                filter {
                                    eq("playlist_id", targetPlaylistId)
                                }
                            }
                            .decodeList<UserPlaylistTrackDto>()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val existingRemoteTrackIds = remoteTracksForPlaylist.map { it.trackId }.toSet()

                    for (track in localCP.tracks) {
                        val trackKey = "${localCP.playlist.name}\t${track.id}"
                        if (trackKey in currentPendingTrackDeletes) continue

                        if (!existingRemoteTrackIds.contains(track.id)) {
                            val cleanT = extractPolishedTitle(track.title, track.artist)
                            val cleanA = extractPolishedArtist(track.title, track.artist)
                            val cleanThumb = if (track.thumbnailUrl.contains("hqdefault.jpg")) {
                                track.thumbnailUrl.replace("hqdefault.jpg", "mqdefault.jpg")
                            } else track.thumbnailUrl

                            try {
                                supabaseClient.from("user_playlist_tracks").upsert(
                                    UserPlaylistTrackDto(
                                        playlistId = targetPlaylistId,
                                        userId = user.id,
                                        trackId = track.id,
                                        title = cleanT,
                                        artist = cleanA,
                                        thumbnailUrl = cleanThumb,
                                        durationSeconds = track.durationSeconds.toInt()
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            setInitialSyncCompleted(user.id, true)
            _syncState.value = SyncState.Success("Cloud sequence synchronized")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync error")
            Result.failure(e)
        }
    }

    private suspend fun flushPendingDeletions(userId: String) {
        // 1. Flush pending favorite deletions
        val favDeletes = getPendingFavoriteDeletions()
        for (trackId in favDeletes) {
            try {
                supabaseClient.from("user_favorites").delete {
                    filter {
                        eq("user_id", userId)
                        eq("track_id", trackId)
                    }
                }
                removePendingFavoriteDeletion(trackId)
            } catch (_: Exception) {}
        }

        // 2. Flush pending playlist deletions
        val playlistDeletes = getPendingPlaylistDeletions()
        for (playlistName in playlistDeletes) {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val matching = remotePlaylists.filter { it.name.equals(playlistName, ignoreCase = true) }
                for (p in matching) {
                    if (p.id != null) {
                        supabaseClient.from("user_playlist_tracks").delete {
                            filter {
                                eq("playlist_id", p.id)
                            }
                        }
                        supabaseClient.from("user_playlists").delete {
                            filter {
                                eq("id", p.id)
                                eq("user_id", userId)
                            }
                        }
                    }
                }
                removePendingPlaylistDeletion(playlistName)
            } catch (_: Exception) {}
        }

        // 3. Flush pending playlist track deletions
        val trackDeletes = getPendingPlaylistTrackDeletions()
        for (key in trackDeletes) {
            val parts = key.split("\t")
            if (parts.size == 2) {
                val pName = parts[0]
                val tId = parts[1]
                try {
                    val remotePlaylists = supabaseClient.from("user_playlists")
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeList<UserPlaylistDto>()
                    val matching = remotePlaylists.filter { it.name.equals(pName, ignoreCase = true) }
                    for (p in matching) {
                        if (p.id != null) {
                            supabaseClient.from("user_playlist_tracks").delete {
                                filter {
                                    eq("playlist_id", p.id)
                                    eq("track_id", tId)
                                }
                            }
                        }
                    }
                    removePendingPlaylistTrackDeletion(key)
                } catch (_: Exception) {}
            }
        }
    }

    // =========================================================================
    // REAL-TIME EVENT HANDLERS (INVOKED IMMEDIATELY FROM UI / VIEWMODELS)
    // =========================================================================

    fun onFavoriteChanged(track: TrackEntity, isLiked: Boolean) {
        if (!isLiked) {
            addPendingFavoriteDeletion(track.id)
        } else {
            removePendingFavoriteDeletion(track.id)
        }

        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                if (isLiked) {
                    val cleanT = extractPolishedTitle(track.title, track.artist)
                    val cleanA = extractPolishedArtist(track.title, track.artist)
                    val cleanThumb = if (track.thumbnailUrl.contains("hqdefault.jpg")) {
                        track.thumbnailUrl.replace("hqdefault.jpg", "mqdefault.jpg")
                    } else track.thumbnailUrl

                    supabaseClient.from("user_favorites").upsert(
                        UserFavoriteDto(
                            userId = user.id,
                            trackId = track.id,
                            title = cleanT,
                            artist = cleanA,
                            thumbnailUrl = cleanThumb,
                            durationSeconds = track.durationSeconds.toInt()
                        )
                    )
                } else {
                    supabaseClient.from("user_favorites").delete {
                        filter {
                            eq("user_id", user.id)
                            eq("track_id", track.id)
                        }
                    }
                    removePendingFavoriteDeletion(track.id)
                }
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to sync favorite change: ${e.message}")
            }
        }
    }

    fun onTrackRemovedFromPlaylist(playlistName: String, trackId: String) {
        addPendingPlaylistTrackDeletion(playlistName, trackId)
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val matching = remotePlaylists.filter { it.name.equals(playlistName, ignoreCase = true) }
                for (p in matching) {
                    if (p.id != null) {
                        supabaseClient.from("user_playlist_tracks").delete {
                            filter {
                                eq("playlist_id", p.id)
                                eq("track_id", trackId)
                            }
                        }
                    }
                }
                removePendingPlaylistTrackDeletion("$playlistName\t$trackId")
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to remove track from remote playlist: ${e.message}")
            }
        }
    }

    fun onPlaylistDeleted(playlistName: String) {
        addPendingPlaylistDeletion(playlistName)
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val matching = remotePlaylists.filter { it.name.equals(playlistName, ignoreCase = true) }
                for (p in matching) {
                    if (p.id != null) {
                        supabaseClient.from("user_playlist_tracks").delete {
                            filter {
                                eq("playlist_id", p.id)
                            }
                        }
                        supabaseClient.from("user_playlists").delete {
                            filter {
                                eq("id", p.id)
                                eq("user_id", user.id)
                            }
                        }
                    }
                }
                removePendingPlaylistDeletion(playlistName)
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to delete remote playlist: ${e.message}")
            }
        }
    }

    fun onPlaylistCreated(playlistName: String) {
        removePendingPlaylistDeletion(playlistName)
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val existing = remotePlaylists.firstOrNull { it.name.equals(playlistName, ignoreCase = true) }
                if (existing == null) {
                    supabaseClient.from("user_playlists").insert(
                        UserPlaylistDto(userId = user.id, name = playlistName)
                    )
                }
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to create remote playlist: ${e.message}")
            }
        }
    }

    fun onPlaylistRenamed(oldName: String, newName: String) {
        removePendingPlaylistDeletion(oldName)
        removePendingPlaylistDeletion(newName)
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val matching = remotePlaylists.filter { it.name.equals(oldName, ignoreCase = true) }
                for (p in matching) {
                    if (p.id != null) {
                        supabaseClient.from("user_playlists").update({
                            set("name", newName)
                        }) {
                            filter {
                                eq("id", p.id)
                                eq("user_id", user.id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to rename remote playlist: ${e.message}")
            }
        }
    }

    fun onPlaylistCleared(playlistName: String) {
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val matching = remotePlaylists.filter { it.name.equals(playlistName, ignoreCase = true) }
                for (p in matching) {
                    if (p.id != null) {
                        supabaseClient.from("user_playlist_tracks").delete {
                            filter {
                                eq("playlist_id", p.id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to clear remote playlist: ${e.message}")
            }
        }
    }

    fun onTrackAddedToPlaylist(playlistName: String, track: TrackEntity) {
        removePendingPlaylistTrackDeletion("$playlistName\t${track.id}")
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            try {
                val remotePlaylists = supabaseClient.from("user_playlists")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserPlaylistDto>()
                val matching = remotePlaylists.firstOrNull { it.name.equals(playlistName, ignoreCase = true) }
                val targetPlaylistId = matching?.id ?: run {
                    val newDto = UserPlaylistDto(
                        userId = user.id,
                        name = playlistName
                    )
                    val inserted = supabaseClient.from("user_playlists")
                        .insert(newDto) {
                            select()
                        }
                        .decodeSingleOrNull<UserPlaylistDto>()
                    inserted?.id
                }

                if (targetPlaylistId != null) {
                    val cleanT = extractPolishedTitle(track.title, track.artist)
                    val cleanA = extractPolishedArtist(track.title, track.artist)
                    val cleanThumb = if (track.thumbnailUrl.contains("hqdefault.jpg")) {
                        track.thumbnailUrl.replace("hqdefault.jpg", "mqdefault.jpg")
                    } else track.thumbnailUrl

                    supabaseClient.from("user_playlist_tracks").upsert(
                        UserPlaylistTrackDto(
                            playlistId = targetPlaylistId,
                            userId = user.id,
                            trackId = track.id,
                            title = cleanT,
                            artist = cleanA,
                            thumbnailUrl = cleanThumb,
                            durationSeconds = track.durationSeconds.toInt()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed to add track to remote playlist: ${e.message}")
            }
        }
    }
}
