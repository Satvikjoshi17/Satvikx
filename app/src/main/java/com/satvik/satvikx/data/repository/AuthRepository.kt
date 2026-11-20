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
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

sealed class SignUpResult {
    data class Success(val profile: UserProfile) : SignUpResult()
    data class ConfirmationEmailSent(val email: String, val message: String) : SignUpResult()
    data class Failure(val message: String) : SignUpResult()
}

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
    private val supabaseClient: SupabaseClient,
    private val homeFeedCacheManager: HomeFeedCacheManager,
    private val supabaseAnalyticsRepositoryProvider: javax.inject.Provider<SupabaseAnalyticsRepository>
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
            val pendingName = prefs.getString("pending_signup_name", null)
            val isGuest = profile?.isGuest ?: (user.email.isNullOrBlank())

            val localSavedName = prefs.getString("display_name", null)?.ifBlank { null }
            val currentMemName = _currentUser.value?.displayName?.ifBlank { null }

            val resolvedName = profile?.displayName?.ifBlank { null }
                ?: metaName?.ifBlank { null }
                ?: currentMemName
                ?: localSavedName
                ?: pendingName?.ifBlank { null }
                ?: user.email?.substringBefore("@")?.replace(".", " ")?.replace("_", " ")
                    ?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                ?: "Stark Operative"

            val resolvedAvatar = profile?.avatarUrl?.ifBlank { null }
                ?: metaAvatar?.ifBlank { null }
                ?: _currentUser.value?.avatarUrl?.ifBlank { null }
                ?: "⚡"

            val resolvedListeningSeconds = maxOf(
                profile?.listeningSeconds ?: 0L,
                _currentUser.value?.listeningSeconds ?: 0L,
                prefs.getLong("listening_seconds", 0L)
            )

            // Clear any pending signup info now that profile is loaded
            prefs.edit().remove("pending_signup_name").remove("pending_signup_email").apply()

            // Self-Healing Database Entry:
            // If the row was missing in Supabase 'profiles' or had missing/outdated data, write it now with core columns!
            if (profile == null || profile.displayName.isBlank() || profile.displayName != resolvedName || profile.listeningSeconds < resolvedListeningSeconds) {
                try {
                    val profileToSave = buildJsonObject {
                        put("id", user.id)
                        put("display_name", resolvedName)
                        put("avatar_url", resolvedAvatar)
                        put("is_guest", isGuest)
                        put("listening_seconds", resolvedListeningSeconds)
                    }
                    supabaseClient.from("profiles").upsert(profileToSave)
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "Profile self-healing upsert non-fatal: ${e.message}")
                }
            }

            val resolvedProfile = UserProfile(
                id = user.id,
                email = user.email,
                displayName = resolvedName,
                avatarUrl = resolvedAvatar,
                isGuest = isGuest,
                listeningSeconds = resolvedListeningSeconds
            )

            _currentUser.value = resolvedProfile
            persistLocalProfile(resolvedProfile)

            scope.launch {
                try {
                    supabaseAnalyticsRepositoryProvider.get().syncUserFullAnalytics()
                } catch (_: Exception) {}
            }

            resolvedProfile
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signInAnonymously(displayName: String, avatarUrl: String = ""): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            val cleanName = displayName.trim().ifBlank { "Operative" }
            val cleanAvatar = avatarUrl.trim().ifBlank { "⚡" }

            var supaUser = try {
                withTimeout(12_000L) {
                    supabaseClient.auth.signInAnonymously(
                        data = buildJsonObject {
                            put("display_name", cleanName)
                            put("avatar_url", cleanAvatar)
                            put("is_guest", true)
                        }
                    )
                    supabaseClient.auth.currentUserOrNull()
                }
            } catch (e: Exception) {
                null
            }

            val userId = supaUser?.id ?: ("guest_" + System.currentTimeMillis())
            val existingSeconds = prefs.getLong("listening_seconds", 0L)
            val profileDto = ProfileDto(
                id = userId,
                displayName = cleanName,
                avatarUrl = cleanAvatar,
                isGuest = true,
                listeningSeconds = existingSeconds
            )

            if (supaUser != null) {
                try {
                    supabaseClient.from("profiles").upsert(profileDto)
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "Anonymous profile upsert non-fatal: ${e.message}")
                }
            }

            val profile = UserProfile(
                id = userId,
                email = null,
                displayName = cleanName,
                avatarUrl = cleanAvatar,
                isGuest = true,
                listeningSeconds = existingSeconds
            )
            _currentUser.value = profile
            persistLocalProfile(profile)
            scope.launch {
                try {
                    supabaseAnalyticsRepositoryProvider.get().syncUserFullAnalytics()
                } catch (_: Exception) {}
            }
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): SignUpResult = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            val cleanName = displayName.trim().ifBlank {
                email.substringBefore("@").replace(".", " ").replace("_", " ")
                    .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    .ifBlank { "Stark Operative" }
            }

            withTimeout(15_000L) {
                supabaseClient.auth.signUpWith(Email) {
                    this.email = email
                    this.password = pass
                    data = buildJsonObject {
                        put("display_name", cleanName)
                        put("avatar_url", "⚡")
                        put("is_guest", false)
                    }
                }
            }

            val user = supabaseClient.auth.currentUserOrNull()
            val existingSeconds = prefs.getLong("listening_seconds", 0L)

            if (user != null) {
                val profileDto = ProfileDto(
                    id = user.id,
                    displayName = cleanName,
                    avatarUrl = "⚡",
                    isGuest = false,
                    listeningSeconds = existingSeconds
                )

                try {
                    supabaseClient.from("profiles").upsert(profileDto)
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "SignUp profile upsert non-fatal: ${e.message}")
                }

                val profile = UserProfile(
                    id = user.id,
                    email = user.email ?: email,
                    displayName = cleanName,
                    avatarUrl = "⚡",
                    isGuest = false,
                    listeningSeconds = existingSeconds
                )
                _currentUser.value = profile
                persistLocalProfile(profile)
                scope.launch {
                    try {
                        supabaseAnalyticsRepositoryProvider.get().syncUserFullAnalytics()
                    } catch (_: Exception) {}
                }
                SignUpResult.Success(profile)
            } else {
                // Email confirmation is required by Supabase Auth!
                // Cache user details locally so they are immediately written to profiles on first login
                prefs.edit()
                    .putString("pending_signup_name", cleanName)
                    .putString("pending_signup_email", email)
                    .apply()

                SignUpResult.ConfirmationEmailSent(
                    email = email,
                    message = "Verification link sent to $email! Please verify or log in to complete your profile."
                )
            }
        } catch (e: Exception) {
            SignUpResult.Failure(e.localizedMessage ?: "Registration failed")
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            withTimeout(15_000L) {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }
            }

            val profile = loadUserProfile()
                ?: return@withContext Result.failure(IllegalStateException("Failed to load profile. Please verify your credentials."))

            persistLocalProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            val msg = e.localizedMessage.orEmpty()
            val friendlyMsg = if (msg.contains("Email not confirmed", ignoreCase = true)) {
                "Email not confirmed yet. Please check your inbox or turn off 'Confirm email' in Supabase."
            } else if (msg.contains("Invalid login credentials", ignoreCase = true)) {
                "Invalid email or password."
            } else {
                msg.ifBlank { "Authentication failed" }
            }
            Result.failure(Exception(friendlyMsg, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Records listening engagement time in seconds, persisting to local cache and synchronizing
     * to the user's Supabase database entry in real time.
     */
    suspend fun recordListeningSeconds(seconds: Long) = withContext(Dispatchers.IO) {
        if (seconds <= 0L) return@withContext
        val current = _currentUser.value
        val oldSeconds = current?.listeningSeconds ?: prefs.getLong("listening_seconds", 0L)
        val newSeconds = oldSeconds + seconds

        prefs.edit().putLong("listening_seconds", newSeconds).apply()

        if (current != null) {
            val updated = current.copy(listeningSeconds = newSeconds)
            _currentUser.value = updated
            persistLocalProfile(updated)

            // Sync updated telemetry to Supabase profiles table
            try {
                val user = supabaseClient.auth.currentUserOrNull()
                if (user != null) {
                    val coreDto = buildJsonObject {
                        put("id", user.id)
                        put("display_name", updated.displayName)
                        put("avatar_url", updated.avatarUrl)
                        put("is_guest", updated.isGuest)
                        put("listening_seconds", newSeconds)
                    }
                    supabaseClient.from("profiles").upsert(coreDto)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun updateProfile(displayName: String, avatarUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanName = displayName.trim().ifBlank { "Stark Operative" }
            val cleanAvatar = avatarUrl.trim().ifBlank { "⚡" }
            val currentListening = _currentUser.value?.listeningSeconds ?: prefs.getLong("listening_seconds", 0L)

            val current = _currentUser.value
            val isGuest = current?.isGuest ?: false
            val authUser = try { supabaseClient.auth.currentUserOrNull() } catch (_: Exception) { null }
            val targetId = authUser?.id ?: current?.id ?: prefs.getString("user_id", null) ?: "user_${System.currentTimeMillis()}"

            // 1. Clear any pending signup fields so they never overwrite the updated callsign later
            prefs.edit()
                .remove("pending_signup_name")
                .remove("pending_signup_email")
                .apply()

            // 2. OPTIMISTIC UPDATE: Update in-memory state and local disk storage IMMEDIATELY
            val newProfile = UserProfile(
                id = targetId,
                email = current?.email ?: authUser?.email,
                displayName = cleanName,
                avatarUrl = cleanAvatar,
                isGuest = isGuest,
                listeningSeconds = currentListening
            )
            _currentUser.value = newProfile
            persistLocalProfile(newProfile)

            // 3. UPDATE SUPABASE AUTH USER METADATA (for cloud authenticated users)
            if (authUser != null) {
                try {
                    supabaseClient.auth.updateUser {
                        data {
                            put("display_name", cleanName)
                            put("avatar_url", cleanAvatar)
                        }
                    }
                    android.util.Log.d("AuthRepository", "Supabase Auth user metadata updated to: $cleanName")
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "Non-fatal: Failed to update Supabase Auth user metadata: ${e.message}")
                }
            }

            // 4. UPDATE SUPABASE 'profiles' TABLE WITH CORE COLUMNS
            try {
                val coreDto = buildJsonObject {
                    put("id", targetId)
                    put("display_name", cleanName)
                    put("avatar_url", cleanAvatar)
                    put("is_guest", isGuest)
                    put("listening_seconds", currentListening)
                }
                supabaseClient.from("profiles").upsert(coreDto)
                android.util.Log.d("AuthRepository", "Supabase profiles table updated to: $cleanName")
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Non-fatal: Failed to upsert to profiles table: ${e.message}")
            }

            // 5. UPDATE SUPABASE 'user_analytics' TABLE IN REAL TIME
            scope.launch {
                try {
                    supabaseAnalyticsRepositoryProvider.get().syncUserFullAnalytics()
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepository", "Non-fatal: Failed to sync analytics: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to update profile", e)
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
            homeFeedCacheManager.clearCache()
            Result.success(Unit)
        } catch (e: Exception) {
            clearLocalProfile()
            _currentUser.value = null
            homeFeedCacheManager.clearCache()
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
}
