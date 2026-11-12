package com.musicpartner.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import com.musicpartner.NowPlaying

/**
 * Watches whichever app is playing music (YouTube Music, Spotify, YouTube, Chrome...) and
 * sends it play / pause / next / previous. Must be created and used on the main thread.
 */
class MediaHub(private val context: Context, private val onChange: (NowPlaying?) -> Unit) {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaNotificationListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var current: MediaController? = null
    private var last: NowPlaying? = null
    private var started = false

    /** Readable from any thread (the capture thread uses it). */
    @Volatile var isPlaying = false
        private set

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list ?: emptyList())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    // Some apps don't trigger a sessions-changed event when they start playing, so re-check.
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, 3000)
        }
    }

    // ---- Basic mode (no notification access, e.g. the Lite edition) -------------------------
    // Controls work by sending media-button presses (what Bluetooth headphones do) to whichever
    // app played last; "is music playing" comes from AudioManager. No song title is available.
    private val audio = context.getSystemService(AudioManager::class.java)
    private var basicMode = false
    private val basicPoll = object : Runnable {
        override fun run() {
            val playing = audio.isMusicActive
            isPlaying = playing
            val np = NowPlaying(title = null, artist = null, app = null, playing = playing)
            if (np != last) {
                last = np
                onChange(np)
            }
            main.postDelayed(this, 700)
        }
    }

    fun start() {
        if (started || basicMode) return
        if (hasAccess(context)) {
            try {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
                started = true
                refresh()
                main.postDelayed(poll, 3000)
                return
            } catch (e: SecurityException) {
                // fall through to basic mode
            }
        }
        basicMode = true
        main.post(basicPoll)
    }

    fun stop() {
        main.removeCallbacks(poll)
        main.removeCallbacks(basicPoll)
        if (started) runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        current?.unregisterCallback(callback)
        current = null
        started = false
        basicMode = false
        isPlaying = false
    }

    fun command(action: String) {
        if (!started && !basicMode) start()
        if (basicMode) {
            when (action) {
                "play" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                "pause" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                "toggle" -> mediaKey(if (audio.isMusicActive) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
                "next" -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                "prev" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            main.postDelayed({ if (basicMode) { main.removeCallbacks(basicPoll); basicPoll.run() } }, 400)
            return
        }
        refresh()
        val controls = current?.transportControls ?: return
        when (action) {
            "play" -> controls.play()
            "pause" -> controls.pause()
            "toggle" -> if (isPlaying) controls.pause() else controls.play()
            "next" -> controls.skipToNext()
            "prev" -> controls.skipToPrevious()
        }
    }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun refresh() {
        if (!started) return
        val sessions = try {
            sessionManager.getActiveSessions(component)
        } catch (e: SecurityException) {
            emptyList()
        }
        pick(sessions)
    }

    private fun pick(sessions: List<MediaController>) {
        val others = sessions.filter { it.packageName != context.packageName }
        val playing = others.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val keep = current?.let { cur -> others.firstOrNull { it.sessionToken == cur.sessionToken } }
        val chosen = playing ?: keep ?: others.firstOrNull()
        if (chosen?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = chosen
            chosen?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = current
        val np = if (c == null) null else {
            val md = c.metadata
            NowPlaying(
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                app = appLabel(c.packageName),
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }
        isPlaying = np?.playing == true
        if (np != last) {
            last = np
            onChange(np)
        }
    }

    private fun appLabel(pkg: String): String = KNOWN_APPS[pkg] ?: try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    companion object {
        private val KNOWN_APPS = mapOf(
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.google.android.youtube" to "YouTube",
            "com.spotify.music" to "Spotify",
            "com.android.chrome" to "Chrome",
            "com.jio.media.jiobeats" to "JioSaavn",
            "com.amazon.mp3" to "Amazon Music",
            "com.apple.android.music" to "Apple Music",
        )

        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
