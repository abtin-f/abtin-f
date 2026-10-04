package com.abtinf.glassmusic.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingRescan: Job? = null
    private var observing = false

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val granted = hasAudioPermission()
        var real = if (granted) MediaStoreSource.load(context) else emptyList()
        val current = _library.value
        if (granted && real.isEmpty() && current.loaded && !current.isDemo) {
            // A real library does not vanish: MediaStore can answer "nothing" for a moment while it re-indexes. Ask once more
            // before swapping the user's songs for the sample library.
            delay(1_500)
            real = MediaStoreSource.load(context)
        }
        // Nothing changed on the device: keep the same Library so no screen has to recompose.
        if (real.isNotEmpty() && current.loaded && !current.isDemo && current.hasPermission && current.tracks == real) return@withContext
        _library.value = if (real.isNotEmpty()) Library.build(real, isDemo = false, hasPermission = true)
        else Library.build(DemoCatalog.tracks(), isDemo = true, hasPermission = granted)
    }

    /** Re-scans (debounced) whenever songs are added, removed or edited on the device. */
    fun startObserving() {
        if (observing) return
        observing = true
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    pendingRescan?.cancel()
                    pendingRescan = scope.launch {
                        delay(2_500)
                        refresh()
                    }
                }
            },
        )
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
