package com.abtinf.glassmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.abtinf.glassmusic.App
import com.abtinf.glassmusic.MainActivity
import com.abtinf.glassmusic.R

/** Hosts the MediaSession (notification + lock-screen + Bluetooth controls) for the app-wide player. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val controller = (application as App).container.player
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Our own status-bar icon instead of Media3's generic one.
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_music) })
        session = MediaSession.Builder(this, controller.sessionPlayer)
            .setCallback(SessionCallback(controller))
            .setSessionActivity(open)
            .build()
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

    /**
     * Routes Next / Previous straight to the app's queue logic. The player behind the session only ever holds the
     * current song, so the default handling (which asks that player to skip) cannot be relied on.
     */
    private class SessionCallback(private val pc: PlayerController) : MediaSession.Callback {
        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
            @Suppress("DEPRECATION")
            val key: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            Log.i(TAG, "media button key=${key?.keyCode} action=${key?.action} from=${controllerInfo.packageName}")
            if (key == null || key.action != KeyEvent.ACTION_DOWN) return false
            return when (key.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> { pc.next(); true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> { pc.previous(); true }
                else -> false
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            Log.i(TAG, "player command=$playerCommand from=${controller.packageName}")
            return when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> { pc.next(); SessionResult.RESULT_INFO_SKIPPED }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> { pc.previous(); SessionResult.RESULT_INFO_SKIPPED }
                else -> SessionResult.RESULT_SUCCESS
            }
        }
    }

    private companion object { const val TAG = "GlassSession" }
}
