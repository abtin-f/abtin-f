package com.abtinf.glassmusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.abtinf.glassmusic.data.AudioArtFetcher
import com.abtinf.glassmusic.data.AudioArtKeyer
import com.abtinf.glassmusic.data.MusicRepository
import com.abtinf.glassmusic.data.UserStore
import com.abtinf.glassmusic.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(AudioArtKeyer())
            add(AudioArtFetcher.Factory())
        }
        .crossfade(true)
        .build()
}

class AppContainer(app: Application) {
    val userStore = UserStore(app)
    val repository = MusicRepository(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val player = PlayerController(
        context = app,
        scope = scope,
        onTrackStarted = { userStore.recordPlay(it.id) },
        initialAutoMix = userStore.autoMix.value,
    )
}
