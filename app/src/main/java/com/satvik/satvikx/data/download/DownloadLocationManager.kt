package com.satvik.satvikx.data.download

import android.content.Context
import com.satvik.satvikx.data.download.model.DownloadLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("satvikx_download_preferences", Context.MODE_PRIVATE)
    private val _currentLocation = MutableStateFlow(loadInitialLocation())
    val currentLocation: StateFlow<DownloadLocation> = _currentLocation.asStateFlow()

    private fun loadInitialLocation(): DownloadLocation {
        val key = prefs.getString("default_download_location", DownloadLocation.PUBLIC_MUSIC.key)
        return DownloadLocation.fromKey(key)
    }

    fun setLocation(location: DownloadLocation) {
        prefs.edit().putString("default_download_location", location.key).apply()
        _currentLocation.value = location
    }

    fun getLocation(): DownloadLocation = _currentLocation.value
}
