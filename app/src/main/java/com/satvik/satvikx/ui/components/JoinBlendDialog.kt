package com.satvik.satvikx.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satvik.satvikx.data.repository.BlendInvitePayload
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.SurfaceVariantDark
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.viewmodel.BlendViewModel

@Composable
fun JoinBlendDialog(
    viewModel: BlendViewModel,
    onBlendCreated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }
    var detectedClipboardCode by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Auto-detect valid blend code in user's clipboard upon opening
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
        if (clip.isNotBlank()) {
            val clean = viewModel.extractCleanCode(clip)
            if (clean.isNotBlank() && viewModel.peekInvitePayload(clean) != null) {
                detectedClipboardCode = clean
            }
        }
    }

    val cleanCode = remember(tokenInput) {
        if (tokenInput.isNotBlank()) viewModel.extractCleanCode(tokenInput) else ""
    }

    val verifiedPayload: BlendInvitePayload? = remember(cleanCode) {
        if (cleanCode.isNotBlank()) viewModel.peekInvitePayload(cleanCode) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryNeon.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, PrimaryNeon),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = PrimaryNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Join Stark Blend",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "TASTE FUSION PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryNeon
                        )
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter your friend's Blend code below to compare your music tastes, calculate your Taste Match %, and synthesize your shared playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                // Quick Tap-to-Paste Clipboard Detection Banner
                AnimatedVisibility(
                    visible = detectedClipboardCode != null && tokenInput.isBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ArcCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ArcCyanBright.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    detectedClipboardCode?.let {
                                        tokenInput = it
                                        detectedClipboardCode = null
                                        viewModel.clearError()
                                        Toast.makeText(context, "Code inserted from clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = ArcCyanBright,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Blend code detected in clipboard • Tap to paste",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = ArcCyanBright
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field with Smart Auto-Sanitizer
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { input ->
                        // Automatically clean any messy pasted chat text
                        tokenInput = viewModel.extractCleanCode(input).ifBlank { input }
                        viewModel.clearError()
                    },
                    placeholder = {
                        Text(
                            text = "Paste STKBLD: code",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (verifiedPayload != null) PrimaryNeon else ArcCyanBright,
                        unfocusedBorderColor = if (verifiedPayload != null) PrimaryNeon.copy(alpha = 0.5f) else SurfaceVariantDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 2,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tokenInput.isNotBlank()) {
                                IconButton(
                                    onClick = { tokenInput = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val text = item?.text?.toString().orEmpty().trim()
                                    if (text.isNotBlank()) {
                                        val clean = viewModel.extractCleanCode(text)
                                        tokenInput = clean.ifBlank { text }
                                        Toast.makeText(context, "Code pasted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PASTE",
                                    color = PrimaryNeon,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                )

                // Real-Time Verified Fusion Target Hologram
                if (verifiedPayload != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PrimaryNeon,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "FUSION TARGET IDENTIFIED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.8.sp,
                                            color = PrimaryNeon
                                        )
                                    )
                                }

                                Text(
                                    text = "VERIFIED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNeon
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = StarkGold.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, StarkGold),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = StarkGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = verifiedPayload.userName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            if (verifiedPayload.topGenres.isNotEmpty()) {
                                Text(
                                    text = "Vibe: ${verifiedPayload.topGenres.take(3).joinToString(" • ")}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (verifiedPayload.topArtists.isNotEmpty()) {
                                Text(
                                    text = "Artists: ${verifiedPayload.topArtists.take(4).joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = TextSecondary.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (tokenInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ensure you enter a valid Stark Blend code (starts with STKBLD:).",
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "",
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cleanCode.isNotBlank()) {
                        viewModel.joinBlendWithToken(cleanCode) { blendId ->
                            onBlendCreated(blendId)
                            onDismiss()
                        }
                    }
                },
                enabled = verifiedPayload != null && !uiState.isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon, contentColor = StarkCarbon),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        color = StarkCarbon,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FUSING TASTES...", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                } else {
                    Text("SYNTHESIZE BLEND", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        }
    )
}
