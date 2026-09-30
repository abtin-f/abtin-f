package com.abtinf.glassmusic.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val granted = hasAudioPermission()
        val real = if (granted) MediaStoreSource.load(context) else emptyList()
        _library.value = if (real.isNotEmpty()) Library.build(real, isDemo = false, hasPermission = true)
        else Library.build(DemoCatalog.tracks(), isDemo = true, hasPermission = granted)
    }

    companion object {
        fun audioPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun permissionsToRequest(): Array<String> =
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
