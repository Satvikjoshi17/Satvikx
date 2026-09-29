package com.satvik.satvikx.data.download

import android.content.Context
import com.satvik.satvikx.data.download.model.DownloadQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQualityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("satvikx_download_preferences", Context.MODE_PRIVATE)
    private val _currentQuality = MutableStateFlow(loadInitialQuality())
    val currentQuality: StateFlow<DownloadQuality> = _currentQuality.asStateFlow()

    private fun loadInitialQuality(): DownloadQuality {
        val key = prefs.getString("default_download_quality", DownloadQuality.SAVER.key)
        return DownloadQuality.fromKey(key)
    }

    fun setQuality(quality: DownloadQuality) {
        prefs.edit().putString("default_download_quality", quality.key).apply()
        _currentQuality.value = quality
    }

    fun getQuality(): DownloadQuality = _currentQuality.value
}
