package com.satvik.satvikx.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.repository.PlaylistShareManager
import com.satvik.satvikx.data.repository.SharedPlaylistPayload
import com.satvik.satvikx.data.repository.SharedTrackDto
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.SurfaceVariantDark
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.theme.TextTertiary

/**
 * High-tech Dialog for importing shared playlists via deep link or pasted link/code.
 * Allows user to review tracks, edit the playlist title, and save to their library.
 */
@Composable
fun ImportPlaylistDialog(
    initialPayload: SharedPlaylistPayload? = null,
    initialRawInput: String = "",
    shareManager: PlaylistShareManager,
    onImport: (playlistName: String, tracks: List<SharedTrackDto>) -> Unit,
    onPlayPreview: (tracks: List<TrackEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var rawInputText by remember { mutableStateOf(initialRawInput) }
    var payload by remember { mutableStateOf(initialPayload) }
    var playlistTitle by remember { mutableStateOf(initialPayload?.title ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun processInput(text: String) {
        if (text.isBlank()) return
        val decoded = shareManager.decodePayload(text)
        if (decoded != null && decoded.tracks.isNotEmpty()) {
            payload = decoded
            playlistTitle = decoded.title
            errorMessage = null
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            errorMessage = "Invalid or expired playlist link. Please verify and try again."
        }
    }

    LaunchedEffect(initialRawInput) {
        if (initialPayload == null && initialRawInput.isNotBlank()) {
            processInput(initialRawInput)
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString().orEmpty()
            if (text.isNotBlank()) {
                rawInputText = text
                processInput(text)
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
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
                    color = ArcCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, ArcCyanBright),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = ArcCyanBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (payload == null) "Import Playlist" else "Shared Playlist",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = if (payload == null) "PASTE LINK OR CODE" else "PREVIEW & SAVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = ArcCyanBright,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (payload == null) {
                    Text(
                        text = "Paste a shared playlist link (satvikx://playlist...) or enter an STKPL code to import tracks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = {
                            rawInputText = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text("satvikx://playlist?data=... or STKPL:...", color = TextTertiary, fontSize = 12.sp)
                        },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyanBright,
                            unfocusedBorderColor = StarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = SurfaceElevated.copy(alpha = 0.4f),
                            unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pasteFromClipboard() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SurfaceVariantDark)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = ArcCyanBright
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PASTE", fontSize = 12.sp, color = ArcCyanBright)
                        }

                        Button(
                            onClick = { processInput(rawInputText) },
                            enabled = rawInputText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("DECODE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFFF5252),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    val currentPayload = payload!!

                    // Editable Playlist Name
                    Text(
                        text = "PLAYLIST TITLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = ArcCyanBright
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = StarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = SurfaceElevated.copy(alpha = 0.4f),
                            unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Curated by ${currentPayload.creatorName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SpotifyGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${currentPayload.tracks.size} tracks",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = SpotifyGreen
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Track List Preview Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(0.6.dp, SurfaceVariantDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(currentPayload.tracks) { index, trackDto ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceDark.copy(alpha = 0.6f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = TextTertiary,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.width(20.dp)
                                    )

                                    if (trackDto.thumbnailUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = trackDto.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SurfaceVariantDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = TextTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = trackDto.title,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = trackDto.artist,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextTertiary,
                                                fontSize = 10.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (payload != null) {
                Button(
                    onClick = {
                        val finalName = playlistTitle.trim().ifBlank { payload!!.title }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onImport(finalName, payload!!.tracks)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE TO LIBRARY", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (payload != null && payload!!.tracks.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val trackEntities = payload!!.tracks.map { it.toTrackEntity() }
                            onPlayPreview(trackEntities)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SurfaceVariantDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = SpotifyGreen
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PLAY", color = TextSecondary)
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = TextSecondary)
                }
            }
        }
    )
}
