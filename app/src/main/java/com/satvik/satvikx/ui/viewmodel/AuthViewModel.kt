package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.repository.AuthRepository
import com.satvik.satvikx.data.repository.CloudSyncRepository
import com.satvik.satvikx.data.repository.SignUpResult
import com.satvik.satvikx.data.repository.SyncState
import com.satvik.satvikx.data.repository.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isLoading: StateFlow<Boolean> = authRepository.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncState: StateFlow<SyncState> = cloudSyncRepository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Idle)

    fun signInAsGuest(displayName: String, avatarUrl: String = "", onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signInAnonymously(displayName, avatarUrl)
            result.onSuccess {
                cloudSyncRepository.syncAll()
                onResult(true, null)
            }.onFailure {
                onResult(false, it.localizedMessage ?: "Failed to initialize guest session")
            }
        }
    }

    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(email, pass)
            result.onSuccess {
                cloudSyncRepository.syncAll()
                onResult(true, null)
            }.onFailure {
                onResult(false, it.localizedMessage ?: "Authentication failed")
            }
        }
    }

    fun signUpWithEmail(
        email: String,
        pass: String,
        displayName: String,
        onResult: (SignUpResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.signUpWithEmail(email, pass, displayName)
            if (result is SignUpResult.Success) {
                cloudSyncRepository.syncAll()
            }
            onResult(result)
        }
    }

    fun updateProfile(displayName: String, avatarUrl: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.updateProfile(displayName, avatarUrl)
            onResult(result.isSuccess, result.exceptionOrNull()?.localizedMessage)
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            cloudSyncRepository.syncAll()
        }
    }
}
