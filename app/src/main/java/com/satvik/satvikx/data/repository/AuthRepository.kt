package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.remote.supabase.dto.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val id: String,
    val email: String? = null,
    val displayName: String = "",
    val avatarUrl: String = "",
    val isGuest: Boolean = false,
    val listeningSeconds: Long = 0L
)

@Singleton
class AuthRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val supabaseClient: SupabaseClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("satvikx_auth_profile", android.content.Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // 1. Immediately restore local profile from SharedPreferences on app launch
        restoreLocalProfile()

        // 2. Observe Supabase session status in background
        scope.launch {
            supabaseClient.auth.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        loadUserProfile()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        // If no local guest user exists, set null
                        val localUser = _currentUser.value
                        if (localUser == null || !localUser.isGuest) {
                            _currentUser.value = null
                            clearLocalProfile()
                        }
                        _isLoading.value = false
                    }
                    else -> {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    private fun restoreLocalProfile() {
        val savedId = prefs.getString("user_id", null)
        val savedName = prefs.getString("display_name", null)
        if (!savedId.isNullOrBlank() && !savedName.isNullOrBlank()) {
            _currentUser.value = UserProfile(
                id = savedId,
                email = prefs.getString("email", null),
                displayName = savedName,
                avatarUrl = prefs.getString("avatar_url", "").orEmpty(),
                isGuest = prefs.getBoolean("is_guest", true),
                listeningSeconds = prefs.getLong("listening_seconds", 0L)
            )
            _isLoading.value = false
        } else {
            _isLoading.value = false
        }
    }

    private fun persistLocalProfile(profile: UserProfile) {
        prefs.edit()
            .putString("user_id", profile.id)
            .putString("display_name", profile.displayName)
            .putString("avatar_url", profile.avatarUrl)
            .putString("email", profile.email)
            .putBoolean("is_guest", profile.isGuest)
            .putLong("listening_seconds", profile.listeningSeconds)
            .apply()
    }

    private fun clearLocalProfile() {
        prefs.edit().clear().apply()
    }

    suspend fun loadUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val user = supabaseClient.auth.currentUserOrNull() ?: return@withContext null
            val profile = try {
                supabaseClient.from("profiles")
                    .select {
                        filter {
                            eq("id", user.id)
                        }
                    }
                    .decodeSingleOrNull<ProfileDto>()
            } catch (e: Exception) {
                null
            }

            val metaName = user.userMetadata?.get("display_name")?.toString()?.replace("\"", "")
            val metaAvatar = user.userMetadata?.get("avatar_url")?.toString()?.replace("\"", "")
            val isGuest = profile?.isGuest ?: (user.email.isNullOrBlank())

            val resolvedProfile = UserProfile(
                id = user.id,
                email = user.email,
                displayName = profile?.displayName ?: metaName ?: _currentUser.value?.displayName ?: "Operative",
                avatarUrl = profile?.avatarUrl ?: metaAvatar ?: _currentUser.value?.avatarUrl ?: "",
                isGuest = profile?.isGuest ?: isGuest,
                listeningSeconds = profile?.listeningSeconds ?: 0L
            )

            _currentUser.value = resolvedProfile
            persistLocalProfile(resolvedProfile)
            _isLoading.value = false
            resolvedProfile
        } catch (e: Exception) {
            _isLoading.value = false
            null
        }
    }

    suspend fun signInAnonymously(displayName: String, avatarUrl: String = ""): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            val cleanName = displayName.trim().ifBlank { "Operative" }

            var supaUser = try {
                supabaseClient.auth.signInAnonymously(
                    data = buildJsonObject {
                        put("display_name", cleanName)
                        put("avatar_url", avatarUrl)
                        put("is_guest", true)
                    }
                )
                supabaseClient.auth.currentUserOrNull()
            } catch (e: Exception) {
                null
            }

            val userId = supaUser?.id ?: ("guest_" + System.currentTimeMillis())
            val profileDto = ProfileDto(
                id = userId,
                displayName = cleanName,
                avatarUrl = avatarUrl,
                isGuest = true
            )

            if (supaUser != null) {
                try {
                    supabaseClient.from("profiles").upsert(profileDto)
                } catch (_: Exception) {}
            }

            val profile = UserProfile(
                id = userId,
                email = null,
                displayName = cleanName,
                avatarUrl = avatarUrl,
                isGuest = true
            )
            _currentUser.value = profile
            persistLocalProfile(profile)
            _isLoading.value = false
            Result.success(profile)
        } catch (e: Exception) {
            _isLoading.value = false
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
                data = buildJsonObject {
                    put("display_name", displayName)
                    put("is_guest", false)
                }
            }

            val user = supabaseClient.auth.currentUserOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Sign up succeeded, please verify email if required"))

            val profileDto = ProfileDto(
                id = user.id,
                displayName = displayName.ifBlank { "Stark Operative" },
                avatarUrl = "",
                isGuest = false
            )

            try {
                supabaseClient.from("profiles").upsert(profileDto)
            } catch (_: Exception) {}

            val profile = UserProfile(
                id = user.id,
                email = user.email,
                displayName = profileDto.displayName,
                avatarUrl = "",
                isGuest = false
            )
            _currentUser.value = profile
            persistLocalProfile(profile)
            _isLoading.value = false
            Result.success(profile)
        } catch (e: Exception) {
            _isLoading.value = false
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }

            val profile = loadUserProfile()
                ?: return@withContext Result.failure(IllegalStateException("Failed to load profile after login"))

            persistLocalProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            _isLoading.value = false
            Result.failure(e)
        }
    }

    suspend fun updateProfile(displayName: String, avatarUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = supabaseClient.auth.currentUserOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No active user session"))

            val updated = ProfileDto(
                id = user.id,
                displayName = displayName,
                avatarUrl = avatarUrl,
                isGuest = _currentUser.value?.isGuest ?: false
            )

            supabaseClient.from("profiles").upsert(updated)
            val newProfile = _currentUser.value?.copy(
                displayName = displayName,
                avatarUrl = avatarUrl
            )
            _currentUser.value = newProfile
            newProfile?.let { persistLocalProfile(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            try {
                supabaseClient.auth.signOut()
            } catch (_: Exception) {}
            clearLocalProfile()
            _currentUser.value = null
            _isLoading.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            clearLocalProfile()
            _currentUser.value = null
            _isLoading.value = false
            Result.failure(e)
        }
    }
}
