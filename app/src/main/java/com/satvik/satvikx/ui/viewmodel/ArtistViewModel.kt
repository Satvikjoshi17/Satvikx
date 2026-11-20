package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.repository.DeezerArtistRepository
import com.satvik.satvikx.data.repository.RecommendationRepository
import com.satvik.satvikx.playback.PlaybackConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val artistName: String = "",
    val imageUrl: String? = null,
    val fansCount: Long = 0L,
    val deezerId: Long? = null,
    val popularTracks: List<TrackEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val deezerArtistRepository: DeezerArtistRepository,
    private val recommendationRepository: RecommendationRepository,
    private val playbackConnectionManager: PlaybackConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    fun loadArtist(
        artistName: String,
        initialImageUrl: String? = null,
        initialFans: Long = 0L,
        initialDeezerId: Long? = null
    ) {
        _uiState.update {
            it.copy(
                artistName = artistName,
                imageUrl = initialImageUrl,
                fansCount = initialFans,
                deezerId = initialDeezerId,
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                // 1. Fetch or refresh verified portrait & metadata from Deezer
                val portrait = deezerArtistRepository.getArtistPortrait(artistName)
                val canonicalName = portrait?.name ?: artistName
                val img = portrait?.imageUrl ?: initialImageUrl
                val fans = portrait?.fansCount ?: initialFans
                val id = portrait?.id ?: initialDeezerId

                _uiState.update {
                    it.copy(
                        artistName = canonicalName,
                        imageUrl = img,
                        fansCount = fans,
                        deezerId = id
                    )
                }

                // 2. Fetch artist's genuine top tracks (Deezer top tracks or fallback)
                val topTracks = recommendationRepository.getArtistTracks(canonicalName)

                _uiState.update {
                    it.copy(
                        popularTracks = topTracks,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    fun playAll(shuffled: Boolean = false) {
        val tracks = _uiState.value.popularTracks
        if (tracks.isNotEmpty()) {
            val list = if (shuffled) tracks.shuffled() else tracks
            playbackConnectionManager.playTrack(list.first(), list, isUserPlaylist = false)
        }
    }

    fun playTrack(track: TrackEntity) {
        val tracks = _uiState.value.popularTracks
        val list = if (tracks.isNotEmpty()) tracks else listOf(track)
        playbackConnectionManager.playTrack(track, list, isUserPlaylist = false)
    }

    fun addTrackToQueue(track: TrackEntity) {
        playbackConnectionManager.addToQueue(track)
    }

    fun playNext(track: TrackEntity) {
        playbackConnectionManager.playNext(track)
    }
}

