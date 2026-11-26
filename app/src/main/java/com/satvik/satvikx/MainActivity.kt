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
import androidx.lifecycle.lifecycleScope
import com.satvik.satvikx.data.repository.BlendTasteCalibrator
import com.satvik.satvikx.data.repository.PlaylistShareManager
import com.satvik.satvikx.data.repository.SupabaseAnalyticsRepository
import com.satvik.satvikx.ui.navigation.RootNavigationHost
import com.satvik.satvikx.ui.theme.SatvikXTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseAnalyticsRepositoryProvider: Provider<SupabaseAnalyticsRepository>

    @Inject
    lateinit var playlistShareManagerProvider: Provider<PlaylistShareManager>

    @Inject
    lateinit var blendTasteCalibratorProvider: Provider<BlendTasteCalibrator>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission granted or denied handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestNotificationPermission()
        checkAndRequestBatteryOptimization()
        handlePlaylistIntent(intent)
        handleBlendIntent(intent)
        handleAuthIntent(intent)

        // Real-time Supabase analytics synchronization on app startup
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                supabaseAnalyticsRepositoryProvider.get().syncUserFullAnalytics()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Initial Supabase sync non-fatal: ${e.message}")
            }
        }

        setContent {
            SatvikXTheme {
                RootNavigationHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep Supabase last_active_ist updated in real time when user resumes app
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                supabaseAnalyticsRepositoryProvider.get().syncUserFullAnalytics()
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlaylistIntent(intent)
        handleBlendIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handlePlaylistIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val isSatvikxPlaylist = uri.scheme == "satvikx" && uri.host == "playlist"
        val isWebPlaylist = uri.scheme == "https" && (
            (uri.host == "satvikx.app" && uri.path?.startsWith("/playlist") == true) ||
            (uri.host.equals("satvikjoshi17.github.io", ignoreCase = true) && uri.path?.contains("/Satvikx", ignoreCase = true) == true)
        )
        if (isSatvikxPlaylist || isWebPlaylist) {
            val token = uri.getQueryParameter("data") ?: uri.getQueryParameter("playlist") ?: uri.getQueryParameter("token") ?: uri.getQueryParameter("code")
            if (!token.isNullOrBlank()) {
                val handled = playlistShareManagerProvider.get().handleDeepLink(token)
                if (handled) {
                    android.widget.Toast.makeText(this, "Shared playlist link received!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleBlendIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val isSatvikxBlend = uri.scheme == "satvikx" && uri.host == "blend"
        val isWebBlend = uri.scheme == "https" && (
            (uri.host == "satvikx.app" && uri.path?.startsWith("/blend") == true) ||
            (uri.host.equals("satvikjoshi17.github.io", ignoreCase = true) && uri.path?.contains("/Satvikx", ignoreCase = true) == true)
        )
        if (isSatvikxBlend || isWebBlend) {
            val token = uri.getQueryParameter("token") ?: uri.getQueryParameter("blend") ?: uri.getQueryParameter("data") ?: uri.getQueryParameter("code")
            if (!token.isNullOrBlank()) {
                val calibrator = blendTasteCalibratorProvider.get()
                calibrator.handleDeepLink(token)
                val cleanCode = calibrator.extractCleanCode(token)
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Stark Blend Code", cleanCode)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Stark Blend invite link received! Opening Blend...", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleAuthIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "satvikx" && (uri.host == "auth-callback" || uri.host == "login-callback" || uri.host == "auth")) {
            val errorDesc = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            if (!errorDesc.isNullOrBlank()) {
                android.widget.Toast.makeText(this, "Auth: $errorDesc", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "Supabase authentication verified! Welcome to SatvikX.", android.widget.Toast.LENGTH_LONG).show()
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

    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
