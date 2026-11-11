package com.satvik.satvikx.data.repository

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
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        // Auto-sync whenever user logs in
        scope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    syncAll()
                }
            }
        }
    }

    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = authRepository.currentUser.value ?: return@withContext Result.failure(
            IllegalStateException("No active Stark account to sync")
        )

        try {
            _syncState.value = SyncState.Syncing

            // 1. Ensure local 'Liked Songs' playlist exists
            var likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
            if (likedPlaylist == null) {
                val newId = playlistDao.insertPlaylist(PlaylistEntity(name = "Liked Songs"))
                likedPlaylist = PlaylistEntity(playlistId = newId, name = "Liked Songs")
            }

            // 2. Fetch remote favorites from Supabase
            val remoteFavorites = try {
                supabaseClient.from("user_favorites")
                    .select {
                        filter {
                            eq("user_id", user.id)
                        }
                    }
                    .decodeList<UserFavoriteDto>()
            } catch (e: Exception) {
                emptyList()
            }

            // Insert remote favorites into local Room DB
            val remoteTrackIds = remoteFavorites.map { it.trackId }.toSet()
            remoteFavorites.forEach { fav ->
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

            // 3. Upload local favorites that are not yet on Supabase (with polished clean metadata)
            val localLikedTrackIds = playlistDao.getLikedTrackIds().firstOrNull() ?: emptyList()
            val toUpload = localLikedTrackIds.filter { it !in remoteTrackIds }

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
                emptyList()
            }

            // A. Download missing remote playlists to local Room DB
            for (remoteP in remotePlaylists) {
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

                    remoteTracks.forEach { rTrack ->
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
                }
            }

            // B. Upload local custom playlists & their tracks to Supabase
            val localCustomPlaylists = playlistDao.getAllPlaylistsWithTracksSync()
                .filter { it.playlist.name != "Liked Songs" }

            for (localCP in localCustomPlaylists) {
                val matchingRemote = remotePlaylists.firstOrNull { it.name.equals(localCP.playlist.name, ignoreCase = true) }
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

            _syncState.value = SyncState.Success("Cloud sequence synchronized")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync error")
            Result.failure(e)
        }
    }

    fun onFavoriteChanged(track: TrackEntity, isLiked: Boolean) {
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
                }
            } catch (_: Exception) {}
        }
    }
}
