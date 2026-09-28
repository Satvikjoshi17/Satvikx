package com.satvik.satvikx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satvik.satvikx.updater.model.UpdateState
import com.satvik.satvikx.ui.theme.PrimaryNeon

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
                title = {
                    Text(
                        text = "SatvikX v${updateState.newVersion} Available",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "A new native update is ready with performance improvements.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Release Notes:\n${updateState.releaseNotes}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmUpdate(updateState.downloadUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                    ) {
                        Text("Update Now", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later", color = Color.Gray)
                    }
                }
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading Update...") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = PrimaryNeon
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(updateState.progress * 100).toInt()}% completed",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Update Ready to Install",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text("The latest SatvikX update has been downloaded. Tap below to launch installation.")
                },
                confirmButton = {
                    Button(
                        onClick = { onInstallDownloaded(updateState.apkFilePath) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                    ) {
                        Text("Install Now", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", color = Color.Gray)
                    }
                }
            )
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Update Failed") },
                text = { Text(updateState.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = PrimaryNeon)
                    }
                }
            )
        }
        else -> Unit
    }
}
