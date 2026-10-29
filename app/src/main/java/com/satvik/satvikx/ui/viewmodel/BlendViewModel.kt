package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.local.entity.BlendEntity
import com.satvik.satvikx.data.local.entity.BlendWithTracks
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.repository.BlendInvitePayload
import com.satvik.satvikx.data.repository.BlendRepository
import com.satvik.satvikx.data.repository.CuratedBlendArtist
import com.satvik.satvikx.playback.PlaybackConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BlendInviteInfo(
    val code: String,
    val payload: BlendInvitePayload,
    val shareText: String
)

data class BlendUiState(
    val activeBlend: BlendWithTracks? = null,
    val isGenerating: Boolean = false,
    val selectedArtistIds: Set<String> = emptySet(),
    val error: String? = null,
    val currentUserName: String = "Satvik"
)

@HiltViewModel
class BlendViewModel @Inject constructor(
    private val blendRepository: BlendRepository,
    private val playbackConnectionManager: PlaybackConnectionManager
) : ViewModel() {

    val blends: StateFlow<List<BlendEntity>> = blendRepository.getAllBlends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(BlendUiState(currentUserName = blendRepository.getCurrentUserName()))
    val uiState: StateFlow<BlendUiState> = _uiState.asStateFlow()

    val curatedArtists: List<CuratedBlendArtist> = blendRepository.curatedArtists

    init {
        _uiState.update { it.copy(currentUserName = blendRepository.getCurrentUserName()) }
    }

    fun loadBlend(blendId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val blendWithTracks = blendRepository.getBlendWithTracks(blendId)
            _uiState.update {
                it.copy(
                    activeBlend = blendWithTracks,
                    isGenerating = false,
                    error = if (blendWithTracks == null) "Blend archive not found" else null
                )
            }
        }
    }

    fun toggleArtistSelection(artistId: String) {
        _uiState.update { state ->
            val current = state.selectedArtistIds.toMutableSet()
            if (current.contains(artistId)) {
                current.remove(artistId)
            } else {
                if (current.size < 3) {
                    current.add(artistId)
                }
            }
            state.copy(selectedArtistIds = current)
        }
    }

    fun clearArtistSelection() {
        _uiState.update { it.copy(selectedArtistIds = emptySet()) }
    }

    fun generateArtistBlend(onComplete: (String) -> Unit) {
        val selected = curatedArtists.filter { _uiState.value.selectedArtistIds.contains(it.id) }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            try {
                val created = blendRepository.synthesizeArtistBlend(selected)
                _uiState.update { it.copy(activeBlend = created, isGenerating = false, selectedArtistIds = emptySet()) }
                onComplete(created.blend.blendId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = "Synthesis failed: ${e.message}") }
            }
        }
    }

    suspend fun generateInviteInfo(): BlendInviteInfo = withContext(Dispatchers.IO) {
        val payload = blendRepository.generateInvitePayload()
        val token = blendRepository.encodeInviteToken(payload)
        BlendInviteInfo(
            code = token,
            payload = payload,
            shareText = token
        )
    }

    suspend fun getInviteCode(): String = withContext(Dispatchers.IO) {
        val payload = blendRepository.generateInvitePayload()
        blendRepository.encodeInviteToken(payload)
    }

    suspend fun getInviteShareText(): String = withContext(Dispatchers.IO) {
        getInviteCode()
    }

    fun extractCleanCode(rawToken: String): String {
        return blendRepository.extractCleanCode(rawToken)
    }

    fun peekInvitePayload(rawToken: String): BlendInvitePayload? {
        return blendRepository.decodeInviteToken(rawToken)
    }

    fun saveBlendAsPlaylist(blendWithTracks: BlendWithTracks, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val playlistId = blendRepository.saveBlendAsPlaylist(blendWithTracks)
                onComplete(playlistId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not save playlist: ${e.message}") }
            }
        }
    }

    fun joinBlendWithToken(rawToken: String, onComplete: (String) -> Unit) {
        val payload = blendRepository.decodeInviteToken(rawToken)
        if (payload == null) {
            _uiState.update { it.copy(error = "Invalid or corrupted Stark Blend invite code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            try {
                val created = blendRepository.synthesizeFriendBlend(payload)
                _uiState.update { it.copy(activeBlend = created, isGenerating = false) }
                onComplete(created.blend.blendId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isGenerating = false, error = "Failed to blend: ${e.message}") }
            }
        }
    }

    fun playBlend(blend: BlendWithTracks, shuffle: Boolean = false) {
        val tracks = blend.attributedTracks.map { it.track }
        if (tracks.isEmpty()) return

        val queue = if (shuffle) tracks.shuffled() else tracks
        playbackConnectionManager.playTrack(queue.first(), queue)
    }

    fun playTrackInBlend(track: TrackEntity, allTracks: List<TrackEntity>) {
        val idx = allTracks.indexOfFirst { it.id == track.id }
        val queue = if (idx != -1) {
            allTracks.subList(idx, allTracks.size) + allTracks.subList(0, idx)
        } else {
            listOf(track) + allTracks
        }
        playbackConnectionManager.playTrack(track, queue)
    }

    fun deleteBlend(blendId: String) {
        viewModelScope.launch {
            blendRepository.deleteBlend(blendId)
            if (_uiState.value.activeBlend?.blend?.blendId == blendId) {
                _uiState.update { it.copy(activeBlend = null) }
            }
        }
    }
}
