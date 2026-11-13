package com.musicpartner.capture

import com.musicpartner.BuildConfig
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.musicpartner.Conn
import com.musicpartner.NowPlaying
import com.musicpartner.Role
import com.musicpartner.Session
import com.musicpartner.audio.AacEncoder
import com.musicpartner.media.MediaHub
import com.musicpartner.net.Protocol
import com.musicpartner.net.RelayClient
import com.musicpartner.util.Locks
import com.musicpartner.util.Notifications
import okio.ByteString
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Runs on the phone that plays the music.
 *
 * MODE_TEST: captures system audio and reports whether anything is actually captured
 *            (the feasibility test: does YouTube Music / Spotify / YouTube allow capture?).
 * MODE_HOST: same, plus encodes to AAC, streams to the relay, and executes play/pause/skip
 *            commands from friends on whatever music app is playing.
 */
class CaptureService : Service() {

    companion object {
        const val MODE_TEST = "test"
        const val MODE_HOST = "host"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_URL = "url"
        private const val EXTRA_ACTION = "action"
        private const val ACTION_CMD = "com.musicpartner.action.CAPTURE_CMD"
        private const val TAG = "CaptureService"

        private const val SAMPLE_RATE = 48_000
        private const val CHANNELS = 2
        private const val BITRATE = 128_000
        private const val CHUNK_BYTES = 1024 * CHANNELS * 2 // ~21 ms

        @Volatile var running = false
            private set

        fun start(context: Context, resultCode: Int, data: Intent, mode: String, url: String) {
            val intent = Intent(context, CaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_URL, url)
            ContextCompat.startForegroundService(context, intent)
        }

        fun command(context: Context, action: String) {
            if (!running) return
            context.startService(
                Intent(context, CaptureService::class.java).setAction(ACTION_CMD).putExtra(EXTRA_ACTION, action)
            )
        }

        fun stop(context: Context) {
            if (!running) return
            context.startService(Intent(context, CaptureService::class.java).setAction(Notifications.ACTION_STOP))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var mode = MODE_TEST
    private var projection: MediaProjection? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false
    private var hub: MediaHub? = null
    @Volatile private var relay: RelayClient? = null
    @Volatile private var csd: ByteArray? = null
    @Volatile private var hostKey: String? = null
    private var locks: Locks? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // User revoked capture from the system UI.
            main.post { stopSelf() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Notifications.ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CMD -> {
                intent.getStringExtra(EXTRA_ACTION)?.let { hub?.command(it) }
                return START_NOT_STICKY
            }
        }
        if (running) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_TEST
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 14+: must be in the foreground (with type mediaProjection) before getMediaProjection().
        startForeground(
            Notifications.ID_CAPTURE,
            Notifications.build(this, title(), "Starting…", CaptureService::class.java),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        running = true

        val proj = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            null
        }
        if (proj == null) {
            Session.message.value = "Couldn't start audio capture (permission expired). Try again."
            stopSelf()
            return START_NOT_STICKY
        }
        proj.registerCallback(projectionCallback, main)
        projection = proj

        Session.reset()
        Session.role.value = if (mode == MODE_HOST) Role.HOST else Role.TEST
        locks = Locks(this).also { it.acquire() }

        hub = MediaHub(this) { np -> onNowPlaying(np) }.also { it.start() }

        if (mode == MODE_HOST) {
            Session.conn.value = Conn.CONNECTING
            relay = RelayClient(url, relayListener).also { it.connect() }
        }

        capturing = true
        captureThread = Thread({ captureLoop(proj) }, "AudioCapture").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        updateNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        capturing = false
        captureThread?.join(1000)
        captureThread = null
        relay?.close(finalText = Protocol.end())
        relay = null
        hub?.stop()
        hub = null
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        locks?.release()
        locks = null
        if (running) Session.reset()
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun title() = when {
        mode != MODE_HOST -> "Capture test"
        BuildConfig.FULL_EDITION -> "Sharing your music"
        else -> "In Sync With You · sharing 💞"
    }

    private fun updateNotification() {
        if (!running) return
        val text = when {
            mode == MODE_TEST -> "Checking whether music audio can be captured"
            Session.roomCode.value != null ->
                "Room ${Session.roomCode.value} · ${Session.listeners.value} listening"
            else -> "Connecting…"
        }
        Notifications.update(this, Notifications.ID_CAPTURE,
            Notifications.build(this, title(), text, CaptureService::class.java))
    }

    // ---- media control ----------------------------------------------------------------------

    private fun onNowPlaying(np: NowPlaying?) {
        Session.nowPlaying.value = np
        relay?.sendText(Protocol.nowPlaying(np))
    }

    // ---- relay ------------------------------------------------------------------------------

    private val relayListener = object : RelayClient.Listener {
        override fun onOpen() {
            Session.conn.value = Conn.CONNECTED
            // Reclaim our room after a network drop, or create a new one.
            relay?.sendText(Protocol.host(Session.roomCode.value, hostKey))
        }

        override fun onText(json: JSONObject) {
            when (json.optString("t")) {
                "room" -> {
                    Session.roomCode.value = json.optString("code")
                    hostKey = json.optString("key")
                    csd?.let { relay?.sendText(Protocol.format(SAMPLE_RATE, CHANNELS, it)) }
                    relay?.sendText(Protocol.nowPlaying(Session.nowPlaying.value))
                    main.post { updateNotification() }
                }
                "peers" -> {
                    Session.listeners.value = json.optInt("count")
                    main.post { updateNotification() }
                }
                "cmd" -> {
                    val action = json.optString("action")
                    if (action in Protocol.COMMANDS) main.post { hub?.command(action) }
                }
                "error" -> {
                    if (json.optString("code") == "room_gone") {
                        Session.roomCode.value = null
                        hostKey = null
                        Session.message.value = "Connection was lost for too long, so you have a new room code. Send it to your friend again."
                        relay?.sendText(Protocol.host())
                    } else {
                        Session.message.value = json.optString("msg", "Server error")
                    }
                }
            }
        }

        override fun onBinary(bytes: ByteString) = Unit

        override fun onDisconnected(willRetry: Boolean, reason: String?) {
            Session.conn.value = if (willRetry) Conn.RECONNECTING else Conn.IDLE
            if (!willRetry && reason != null && running) Session.message.value = reason
        }
    }

    // ---- capture ----------------------------------------------------------------------------

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by MainActivity before starting.
    private fun captureLoop(proj: MediaProjection) {
        val record = try {
            val config = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(minBuf * 2, CHUNK_BYTES * 8))
                .setAudioPlaybackCaptureConfig(config)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord setup failed", e)
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            Session.message.value = "This phone refused to start audio capture."
            main.post { stopSelf() }
            return
        }

        var bytesSent = 0L
        val encoder = if (mode == MODE_HOST) {
            AacEncoder(
                SAMPLE_RATE, CHANNELS, BITRATE,
                onConfig = { config ->
                    csd = config
                    relay?.sendText(Protocol.format(SAMPLE_RATE, CHANNELS, config))
                },
                onFrame = { data, length, ptsUs ->
                    if (relay?.sendBinary(Protocol.audioFrame(ptsUs, data, length)) == true) bytesSent += length
                },
            )
        } else null

        val verdict = CaptureVerdict()
        val buf = ByteArray(CHUNK_BYTES)
        var peakSinceUi = 0f
        var lastUi = 0L
        var lastStats = SystemClock.elapsedRealtime()

        try {
            record.startRecording()
            while (capturing) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) {
                    Log.e(TAG, "read error $n")
                    break
                }
                if (n == 0) continue
                val peak = peakOf(buf, n)
                peakSinceUi = max(peakSinceUi, peak)
                encoder?.encode(buf, n)

                val now = SystemClock.elapsedRealtime()
                if (now - lastUi >= 66) {
                    Session.level.value = peakSinceUi
                    Session.captureVerdict.value = verdict.update(
                        peakSinceUi, now, hub?.isPlaying, Session.nowPlaying.value?.app,
                    )
                    peakSinceUi = 0f
                    lastUi = now
                }
                if (now - lastStats >= 1000) {
                    Session.kbps.value = (bytesSent * 8 / (now - lastStats)).toInt()
                    bytesSent = 0
                    lastStats = now
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "capture loop failed", e)
            Session.message.value = "Audio capture stopped: ${e.message}"
        } finally {
            runCatching { record.stop() }
            record.release()
            encoder?.release()
        }
        if (capturing) main.post { stopSelf() }
    }

    private fun peakOf(b: ByteArray, n: Int): Float {
        var m = 0
        var i = 0
        while (i + 1 < n) {
            val s = ((b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)).toShort().toInt()
            val a = abs(s)
            if (a > m) m = a
            i += 2
        }
        return m / 32768f
    }
}

/**
 * Decides, from the captured level and what the music app reports, whether capture works.
 * The key case: the app says it is PLAYING but we capture pure silence for several seconds
 * -> that app blocks capture (it opted out of AudioPlaybackCapture).
 */
internal class CaptureVerdict {
    private var lastSoundAt = 0L
    private var playingSince = 0L

    fun update(peak: Float, now: Long, playing: Boolean?, app: String?): String {
        val name = app ?: "The music app"
        if (playing == true) {
            if (playingSince == 0L) playingSince = now
        } else {
            playingSince = 0L
        }
        if (peak > SOUND_THRESHOLD) {
            lastSoundAt = now
            return if (playing == true) "✓ Capturing audio from $name" else "✓ Capturing audio"
        }
        val silentFor = now - lastSoundAt
        return when {
            playing == true && silentFor > 4000 && now - playingSince > 4000 ->
                "⚠ $name is playing but no sound is reaching this app. This app most likely blocks audio capture (or its volume is 0)."
            playing == null && silentFor > 4000 ->
                "No sound captured. Play something. (Turn on notification access so I can tell which app is playing.)"
            silentFor > 1000 -> "Waiting for sound… play something in any app."
            else -> "✓ Capturing audio"
        }
    }

    private companion object {
        const val SOUND_THRESHOLD = 0.002f
    }
}
