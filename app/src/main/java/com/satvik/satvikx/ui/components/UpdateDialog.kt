package com.satvik.satvikx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.satvik.satvikx.updater.model.UpdateState

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onConfirmUpdate: (String) -> Unit,
    onInstallDownloaded: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    when (updateState) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SatvikXLogo(sizeDp = 42.dp, showGlow = false)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Update Available",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ArcCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, ArcCyanBright)
                                ) {
                                    Text(
                                        text = "v${updateState.newVersion}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = ArcCyanBright,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "STARK SYSTEM UPGRADE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    color = ArcCyan
                                )
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CHANGELOG & RELEASE NOTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = ArcCyanBright
                                )
                            )

                            if (updateState.apkSize > 0) {
                                val sizeMb = String.format("%.1f MB", updateState.apkSize / (1024f * 1024f))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceVariantDark
                                ) {
                                    Text(
                                        text = sizeMb,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = StarkGold
                                        ),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dedicated Scrollable Release Notes Card with Markdown Rendering
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, SurfaceVariantDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 340.dp)
                        ) {
                            val scrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(12.dp)
                            ) {
                                MarkdownContent(
                                    content = updateState.releaseNotes.ifBlank {
                                        "Bug fixes and performance improvements."
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmUpdate(updateState.downloadUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryNeon,
                            contentColor = StarkCarbon
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPDATE NOW",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "LATER",
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryNeon,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "DOWNLOADING UPDATE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Downloading native update package...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PrimaryNeon,
                            trackColor = SurfaceVariantDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(updateState.progress * 100).toInt()}% COMPLETED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNeon
                                )
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PrimaryNeon,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "UPDATE READY",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                text = {
                    Text(
                        text = "The latest SatvikX update has been verified and downloaded. Tap below to launch installation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onInstallDownloaded(updateState.apkFilePath) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryNeon,
                            contentColor = StarkCarbon
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "INSTALL NOW",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "DISMISS",
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "UPDATE FAILED",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                        )
                    }
                },
                text = {
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryNeon,
                            contentColor = StarkCarbon
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "OK",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        }
        else -> Unit
    }
}
