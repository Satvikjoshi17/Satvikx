package com.satvik.satvikx.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satvik.satvikx.data.repository.BlendInvitePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
fun CreateBlendDialog(
    viewModel: BlendViewModel,
    userName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var tastePayload by remember { mutableStateOf<BlendInvitePayload?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCopied by remember { mutableStateOf(false) }

    var progress by remember { mutableFloatStateOf(0f) }
    var etaText by remember { mutableStateOf("EST. 1.2s") }
    var stageText by remember { mutableStateOf("1/3 • MINING ACOUSTIC AFFINITIES...") }

    LaunchedEffect(Unit) {
        val computationDeferred = async(Dispatchers.IO) {
            viewModel.generateInviteInfo()
        }

        // Stage 1: Mine local rotation & history (0% -> 38%)
        while (progress < 0.38f) {
            delay(35)
            progress += 0.05f
            etaText = "EST. 1.0s"
            stageText = "1/3 • MINING ACOUSTIC ROTATION..."
        }

        // Stage 2: Synthesize multi-genre vectors (38% -> 76%)
        while (progress < 0.76f) {
            delay(35)
            progress += 0.05f
            etaText = "EST. 0.5s"
            stageText = "2/3 • SYNTHESIZING TASTE VECTORS..."
        }

        // Await computation (instant local resolution with safety timeout)
        val info = try {
            withTimeout(3000L) {
                computationDeferred.await()
            }
        } catch (_: Exception) {
            viewModel.generateInviteInfo()
        }

        // Stage 3: Encrypt decentralized Stark token (76% -> 100%)
        while (progress < 1.0f) {
            delay(30)
            progress = (progress + 0.06f).coerceAtMost(1f)
            etaText = if (progress >= 1f) "READY" else "EST. 0.2s"
            stageText = "3/3 • ENCRYPTING STARK TOKEN..."
        }

        delay(120) // Smooth visual closure
        inviteCode = info.code
        tastePayload = info.payload
        isLoading = false
    }

    fun copyOnlyCode(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Strictly copy only the code (no extraneous sentences, links, or emoji)
        val clip = ClipData.newPlainText("Stark Blend Code", code)
        clipboard.setPrimaryClip(clip)
        isCopied = true
        Toast.makeText(context, "Blend code copied to clipboard", Toast.LENGTH_SHORT).show()
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
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = ArcCyanBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Blend with $userName",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "STARK BLEND MATRIX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = ArcCyan
                        )
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Generate your decentralized Taste Token. When your friend enters this code, SatvikX calculates your compatibility score and fuses a 35-track shared playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    TasteProfileComputationEtaLoader(
                        progress = progress,
                        etaText = etaText,
                        stageText = stageText
                    )
                } else {
                    val code = inviteCode.orEmpty()

                    // Holographic Arc Reactor Code Box (Tap anywhere to copy only the code)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCopied) PrimaryNeon else ArcCyanBright.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (code.isNotBlank()) copyOnlyCode(code)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = if (isCopied) PrimaryNeon else ArcCyanBright,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "STARK BLEND CODE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.8.sp,
                                            color = if (isCopied) PrimaryNeon else ArcCyanBright
                                        )
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCopied) PrimaryNeon.copy(alpha = 0.15f) else ArcCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, if (isCopied) PrimaryNeon else ArcCyanBright)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = if (isCopied) PrimaryNeon else ArcCyanBright,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (isCopied) "COPIED" else "TAP TO COPY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = if (isCopied) PrimaryNeon else ArcCyanBright
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Display Code Preview in High-Tech Monospace Box
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceDark,
                                border = BorderStroke(0.5.dp, SurfaceVariantDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Taste Profile Embedded Summary Chip
                    val payload = tastePayload
                    if (payload != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceDark,
                            border = BorderStroke(0.5.dp, SurfaceVariantDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = StarkGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "EMBEDDED PROFILE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = StarkGold
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Artists: ${payload.topArtists.take(3).joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Genres: ${payload.topGenres.take(2).joinToString(" • ")} • ${payload.anchorTitles.size} Anchor Tracks",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Your friend enters only this code in Playlists > Join Code to fuse your tastes.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            // Main Button: Strictly copies only the code to clipboard
            Button(
                onClick = {
                    inviteCode?.let { code ->
                        copyOnlyCode(code)
                    }
                },
                enabled = !isLoading && inviteCode != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCopied) PrimaryNeon else ArcCyanBright,
                    contentColor = StarkCarbon
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCopied) "COPIED CODE" else "COPY CODE",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Share Code directly
                OutlinedButton(
                    onClick = {
                        inviteCode?.let { code ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                // Share strictly the code
                                putExtra(Intent.EXTRA_TEXT, code)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Stark Blend Code")
                            context.startActivity(shareIntent)
                        }
                    },
                    enabled = !isLoading && inviteCode != null,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceVariantDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SHARE", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }

                TextButton(onClick = onDismiss) {
                    Text("CLOSE", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    )
}

/**
 * Stark HUD Dynamic ETA Progress Loader.
 * Alleviates user cognitive load by presenting clear stage feedback,
 * animated progress, and estimated time remaining.
 */
@Composable
private fun TasteProfileComputationEtaLoader(
    progress: Float,
    etaText: String,
    stageText: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceElevated.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, ArcCyanBright.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = ArcCyanBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STARK TASTE ANALYSIS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            color = ArcCyanBright
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ArcCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, ArcCyanBright.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = etaText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = PrimaryNeon
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // High-Tech Animated Linear Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ArcCyanBright,
                trackColor = SurfaceDark
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stageText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ArcCyan
                    )
                )
            }
        }
    }
}
