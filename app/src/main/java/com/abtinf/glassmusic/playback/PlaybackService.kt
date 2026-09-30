package com.abtinf.glassmusic.playback

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.abtinf.glassmusic.App

/** Hosts the MediaSession (notification + lock-screen controls) for the app-wide player. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as App).container.player.sessionPlayer
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
