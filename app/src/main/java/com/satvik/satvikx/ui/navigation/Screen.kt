package com.satvik.satvikx.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Playlists : Screen("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic)
    object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    object Recents : Screen("recents", "History", Icons.Default.History)

    companion object {
        val bottomNavItems = listOf(Home, Search, Playlists, Downloads, Recents)
    }
}

