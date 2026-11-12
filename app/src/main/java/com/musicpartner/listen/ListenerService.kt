package com.musicpartner.listen

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.musicpartner.Conn
import com.musicpartner.Role
import com.musicpartner.Session
import com.musicpartner.audio.StreamPlayer
import com.musicpartner.net.Protocol
import com.musicpartner.net.RelayClient
import com.musicpartner.util.Locks
import com.musicpartner.util.Notifications
import okio.ByteString
import org.json.JSONObject

/** Runs on the friend's phone: joins a room, plays the host's stream, sends play/pause/skip. */
class ListenerService : Service() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_ACTION = "action"
        private const val ACTION_CMD = "com.musicpartner.action.LISTEN_CMD"

        @Volatile var running = false
            private set

        fun start(context: Context, url: String, code: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ListenerService::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_CODE, code),
            )
        }

        fun command(context: Context, action: String) {
            if (!running) return
            context.startService(
                Intent(context, ListenerService::class.java).setAction(ACTION_CMD).putExtra(EXTRA_ACTION, action)
            )
        }

        fun stop(context: Context) {
            if (!running) return
            context.startService(Intent(context, ListenerService::class.java).setAction(Notifications.ACTION_STOP))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var relay: RelayClient? = null
    private var player: StreamPlayer? = null
    private var locks: Locks? = null
    private var code = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Notifications.ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CMD -> {
                val action = intent.getStringExtra(EXTRA_ACTION)
                if (action != null && action in Protocol.COMMANDS) relay?.sendText(Protocol.cmd(action))
                return START_NOT_STICKY
            }
        }
        if (running) return START_NOT_STICKY

        val url = intent?.getStringExtra(EXTRA_URL)
        val joinCode = intent?.getStringExtra(EXTRA_CODE)
        if (url.isNullOrBlank() || joinCode.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        code = joinCode.trim().uppercase()

        startForeground(
            Notifications.ID_LISTEN,
            Notifications.build(this, "Listening with a friend", "Room $code", ListenerService::class.java),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        running = true

        Session.reset()
        Session.role.value = Role.LISTENER
        Session.roomCode.value = code
        Session.conn.value = Conn.CONNECTING

        locks = Locks(this).also { it.acquire() }
        player = StreamPlayer { ms -> Session.bufferMs.value = ms }.also { it.start() }
        relay = RelayClient(url, relayListener).also { it.connect() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        relay?.close()
        relay = null
        player?.stop()
        player = null
        locks?.release()
        locks = null
        if (running) Session.reset()
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun finish(message: String) {
        Session.message.value = message
        main.post { stopSelf() }
    }

    private val relayListener = object : RelayClient.Listener {
        override fun onOpen() {
            relay?.sendText(Protocol.join(code))
        }

        override fun onText(json: JSONObject) {
            when (json.optString("t")) {
                "joined" -> {
                    Session.conn.value = Conn.CONNECTED
                    Session.hostAway.value = !json.optBoolean("hostOnline", true)
                }
                "format" -> Protocol.parseFormat(json)?.let { player?.setFormat(it) }
                "nowPlaying" -> {
                    val np = Protocol.parseNowPlaying(json)
                    Session.nowPlaying.value = np
                    val text = np?.let { listOfNotNull(it.title, it.artist).joinToString(" · ") }
                        ?.ifEmpty { null } ?: "Room $code"
                    main.post {
                        if (running) Notifications.update(this@ListenerService, Notifications.ID_LISTEN,
                            Notifications.build(this@ListenerService, "Listening with a friend", text, ListenerService::class.java))
                    }
                }
                "hostAway" -> Session.hostAway.value = true
                "hostBack" -> Session.hostAway.value = false
                "ended" -> finish(
                    if (json.optString("reason") == "host_ended") "Your friend stopped sharing."
                    else "Your friend's connection was lost for too long. Session ended."
                )
                "error" -> finish(json.optString("msg", "Couldn't join the room."))
            }
        }

        override fun onBinary(bytes: ByteString) {
            val (pts, frame) = Protocol.parseAudio(bytes) ?: return
            player?.push(pts, frame)
        }

        override fun onDisconnected(willRetry: Boolean, reason: String?) {
            Session.conn.value = if (willRetry) Conn.RECONNECTING else Conn.IDLE
            if (!willRetry && reason != null && running) finish(reason)
        }
    }
}
