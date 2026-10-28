package com.satvik.satvikx.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satvik.satvikx.data.repository.SyncState
import com.satvik.satvikx.data.repository.UserProfile
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.ArcCyanGlow
import com.satvik.satvikx.ui.theme.BackgroundDark
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkCrimson
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.StarkSurface
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.theme.TextTertiary
import com.satvik.satvikx.ui.viewmodel.AuthViewModel

private val AVATAR_OPTIONS = listOf("⚡", "🎧", "🚀", "🛡️", "💎", "🤖", "🔥", "🌌")

/**
 * World-Class Stark ID Authentication & Operative Onboarding Window.
 * Features an unskippable mandatory setup gate on first run, empty callsign input (no prefilled names),
 * responsive avatar picker, smooth Supabase cloud authentication, and active profile management.
 */
@Composable
fun StarkAuthDialog(
    authViewModel: AuthViewModel,
    isMandatory: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val syncState by authViewModel.syncState.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = {
            if (!isMandatory && user != null) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory && user != null,
            dismissOnClickOutside = !isMandatory && user != null,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(
                width = 1.2.dp,
                color = if (user == null) ArcCyanBright.copy(alpha = 0.5f) else StarkBorder
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Top Header with Reactor Badge and optional Dismiss Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(ArcCyan.copy(alpha = 0.15f))
                                .border(1.5.dp, ArcCyanBright, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ArcCyanBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (user != null) "OPERATIVE PROFILE" else "WELCOME TO SATVIKX",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = if (user != null) "STARK MUSIC TELEMETRY • ACTIVE" else "SET UP YOUR IDENTITY TO CONTINUE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (user != null) SpotifyGreen else ArcCyanBright
                            )
                        }
                    }

                    if (!isMandatory && user != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (user != null) {
                    ActiveProfileView(
                        user = user!!,
                        syncState = syncState,
                        onSyncNow = { authViewModel.syncNow() },
                        onUpdateProfile = { newName, newAvatar ->
                            authViewModel.updateProfile(newName, newAvatar) { success, _ ->
                                if (success) {
                                    Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSignOut = {
                            authViewModel.signOut {
                                Toast.makeText(context, "Session disconnected", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClose = onDismiss
                    )
                } else {
                    UnauthenticatedOnboardingView(
                        isLoading = isLoading,
                        onGuestSignIn = { name, avatar ->
                            authViewModel.signInAsGuest(name, avatar) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Welcome to SatvikX, $name!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, error ?: "Session initialization failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onEmailSignIn = { email, pass ->
                            authViewModel.signInWithEmail(email, pass) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Authenticated successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, error ?: "Login failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onEmailSignUp = { email, pass, name ->
                            authViewModel.signUpWithEmail(email, pass, name) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Stark ID registered successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, error ?: "Registration failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * High-engagement onboarding screen when the user has not yet entered details.
 * Starts with NO prefilled name and offers smooth 1-tap quick setup or Supabase cloud authentication.
 */
@Composable
private fun UnauthenticatedOnboardingView(
    isLoading: Boolean,
    onGuestSignIn: (name: String, avatar: String) -> Unit,
    onEmailSignIn: (email: String, pass: String) -> Unit,
    onEmailSignUp: (email: String, pass: String, name: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Quick Profile, 1: Cloud Stark ID

    // Guest Profile Fields (NO PREFILLED NAME - strictly empty)
    var guestName by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("⚡") }

    // Cloud Email Fields
    var isSigningUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // High-Tech Pill Tab Selector
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, StarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 1: Quick Setup
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == 0) ArcCyan.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (selectedTab == 0) BorderStroke(1.dp, ArcCyanBright) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedTab = 0 }
                ) {
                    Text(
                        text = "⚡ QUICK PROFILE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (selectedTab == 0) ArcCyanBright else TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                // Tab 2: Cloud Stark ID
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == 1) ArcCyan.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (selectedTab == 1) BorderStroke(1.dp, ArcCyanBright) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedTab = 1 }
                ) {
                    Text(
                        text = "☁️ CLOUD STARK ID",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (selectedTab == 1) ArcCyanBright else TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (selectedTab == 0) {
            // ================= 1. QUICK PROFILE (INSTANT ONBOARDING) =================
            Text(
                text = "YOUR CALLSIGN / NAME",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = ArcCyanBright
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = guestName,
                onValueChange = { guestName = it },
                placeholder = {
                    Text(
                        text = "Enter your name (e.g. Satvik, Tony)",
                        color = TextTertiary,
                        fontSize = 13.5.sp
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (guestName.isNotBlank()) ArcCyanBright else TextSecondary
                    )
                },
                trailingIcon = {
                    if (guestName.isNotBlank()) {
                        IconButton(onClick = { guestName = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArcCyanBright,
                    unfocusedBorderColor = StarkBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SurfaceElevated.copy(alpha = 0.4f),
                    unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CHOOSE YOUR EMBLEM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = ArcCyanBright
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 8 Curated Avatars in a clean 4x2 grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AVATAR_OPTIONS.chunked(4).forEach { rowAvatars ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowAvatars.forEach { avatar ->
                            val isSelected = selectedAvatar == avatar
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) ArcCyan.copy(alpha = 0.25f) else SurfaceElevated.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) ArcCyanBright else StarkBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedAvatar = avatar },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = avatar, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Launch CTA Button
            val isReady = guestName.trim().isNotBlank()
            Button(
                onClick = { onGuestSignIn(guestName.trim(), selectedAvatar) },
                enabled = !isLoading && isReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArcCyan,
                    disabledContainerColor = SurfaceElevated
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isReady) "LAUNCH SATVIKX MATRIX" else "ENTER YOUR NAME TO CONTINUE",
                            color = if (isReady) Color.Black else TextTertiary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp
                            )
                        )
                        if (isReady) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Offline-ready • Instant high-fidelity audio stream resolution",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // ================= 2. CLOUD STARK ID (SUPABASE AUTH) =================
            if (isSigningUp) {
                Text(
                    text = "OPERATIVE CALLSIGN",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = ArcCyanBright
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = { Text("Your Display Name", color = TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ArcCyan) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcCyanBright,
                        unfocusedBorderColor = StarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = SurfaceElevated.copy(alpha = 0.3f),
                        unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "EMAIL ADDRESS",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = ArcCyanBright
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("operative@stark.io", color = TextTertiary, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = ArcCyan) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArcCyanBright,
                    unfocusedBorderColor = StarkBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SurfaceElevated.copy(alpha = 0.3f),
                    unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SECURITY CREDENTIAL / PASSWORD",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = ArcCyanBright
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("••••••••", color = TextTertiary, fontSize = 13.sp) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ArcCyan) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = TextSecondary
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArcCyanBright,
                    unfocusedBorderColor = StarkBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SurfaceElevated.copy(alpha = 0.3f),
                    unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            val canSubmitCloud = email.isNotBlank() && password.length >= 6 && (!isSigningUp || displayName.isNotBlank())
            Button(
                onClick = {
                    if (isSigningUp) {
                        onEmailSignUp(email.trim(), password, displayName.trim())
                    } else {
                        onEmailSignIn(email.trim(), password)
                    }
                },
                enabled = !isLoading && canSubmitCloud,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArcCyan,
                    disabledContainerColor = SurfaceElevated
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isSigningUp) "REGISTER STARK ID" else "AUTHENTICATE CLOUD ID",
                        color = if (canSubmitCloud) Color.Black else TextTertiary,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = { isSigningUp = !isSigningUp },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (isSigningUp) "Already have a Stark ID? Sign in" else "Need an account? Register new operative",
                    color = ArcCyanBright,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Operative Profile Management Screen shown when authenticated.
 */
@Composable
private fun ActiveProfileView(
    user: UserProfile,
    syncState: SyncState,
    onSyncNow: () -> Unit,
    onUpdateProfile: (name: String, avatar: String) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(user.displayName) }
    var editAvatar by remember { mutableStateOf(user.avatarUrl.ifBlank { "⚡" }) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Operative Identity Card
        Surface(
            color = SurfaceElevated.copy(alpha = 0.7f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, StarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(ArcCyan.copy(alpha = 0.2f))
                            .border(2.dp, ArcCyanBright, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.avatarUrl.ifBlank { "⚡" },
                            fontSize = 26.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (user.isGuest) StarkGold else SpotifyGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (user.isGuest) "LOCAL OPERATIVE PROFILE" else (user.email ?: "VERIFIED STARK ID"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (user.isGuest) StarkGold else SpotifyGreen
                            )
                        }
                    }

                    IconButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = ArcCyanBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Inline Profile Editor
                AnimatedVisibility(visible = isEditing) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        HorizontalDivider(color = StarkBorder, modifier = Modifier.padding(bottom = 12.dp))
                        Text(
                            text = "UPDATE CALLSIGN",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = ArcCyanBright
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "UPDATE EMBLEM",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = ArcCyanBright
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AVATAR_OPTIONS.take(6).forEach { avatar ->
                                val isSel = editAvatar == avatar
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) ArcCyan.copy(alpha = 0.3f) else SurfaceDark)
                                        .border(
                                            width = if (isSel) 2.dp else 1.dp,
                                            color = if (isSel) ArcCyanBright else StarkBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { editAvatar = avatar },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(avatar, fontSize = 18.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (editName.trim().isNotBlank()) {
                                    onUpdateProfile(editName.trim(), editAvatar)
                                    isEditing = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE CHANGES", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Cloud Synchronization Card
        Surface(
            color = SurfaceElevated.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, StarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (syncState) {
                            is SyncState.Syncing -> Icons.Default.Sync
                            is SyncState.Success -> Icons.Default.CloudDone
                            else -> Icons.Default.CloudSync
                        },
                        contentDescription = null,
                        tint = when (syncState) {
                            is SyncState.Syncing -> StarkGold
                            is SyncState.Success -> SpotifyGreen
                            is SyncState.Error -> StarkCrimson
                            else -> ArcCyanBright
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (syncState) {
                                is SyncState.Syncing -> "SYNCING TELEMETRY..."
                                is SyncState.Success -> "CLOUD TELEMETRY SYNCHRONIZED"
                                is SyncState.Error -> "SYNC FAILED: TAP TO RETRY"
                                is SyncState.Idle -> "CLOUD BACKUP: NOMINAL"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Playlists, favorites & telemetry",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }

                TextButton(onClick = onSyncNow) {
                    Text("SYNC", color = ArcCyanBright, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Dismiss / Close
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("DONE", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            // Disconnect / Sign Out
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = StarkCarbon),
                border = BorderStroke(1.dp, StarkCrimson.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = StarkCrimson, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("LOGOUT", color = StarkCrimson, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
