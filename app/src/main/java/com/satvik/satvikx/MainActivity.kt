package com.satvik.satvikx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.satvik.satvikx.ui.navigation.RootNavigationHost
import com.satvik.satvikx.ui.theme.SatvikXTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission granted or denied handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestNotificationPermission()
        handleBlendIntent(intent)

        setContent {
            SatvikXTheme {
                RootNavigationHost()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBlendIntent(intent)
    }

    private fun handleBlendIntent(intent: android.content.Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "satvikx" && uri.host == "blend") {
            val token = uri.getQueryParameter("data") ?: uri.getQueryParameter("token") ?: uri.getQueryParameter("code")
            if (!token.isNullOrBlank()) {
                val cleanCode = if (token.startsWith("STKBLD:")) token else "STKBLD:$token"
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Stark Blend Code", cleanCode)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Stark Blend code detected! Open Playlists > Join Code to fuse.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
