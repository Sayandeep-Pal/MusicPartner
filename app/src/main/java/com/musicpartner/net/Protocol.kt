package com.musicpartner.net

import android.util.Base64
import com.musicpartner.NowPlaying
import com.musicpartner.audio.StreamPlayer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer

/**
 * Wire protocol shared with server/server.js.
 *
 * Text frames are JSON with a "t" (type) field.
 * Binary frames (host -> listeners) are audio: [1 byte type=1][8 bytes pts µs, big endian][AAC frame].
 */
object Protocol {
    private const val FRAME_AUDIO: Byte = 1
    private const val HEADER = 9

    val COMMANDS = setOf("play", "pause", "toggle", "next", "prev")

    fun host(code: String? = null, key: String? = null): String = JSONObject().apply {
        put("t", "host")
        if (code != null && key != null) {
            put("code", code)
            put("key", key)
        }
    }.toString()

    fun join(code: String): String = JSONObject().put("t", "join").put("code", code).toString()

    fun end(): String = JSONObject().put("t", "end").toString()

    fun cmd(action: String): String = JSONObject().put("t", "cmd").put("action", action).toString()

    fun format(sampleRate: Int, channels: Int, csd: ByteArray): String = JSONObject()
        .put("t", "format")
        .put("codec", "aac")
        .put("sampleRate", sampleRate)
        .put("channels", channels)
        .put("csd", Base64.encodeToString(csd, Base64.NO_WRAP))
        .toString()

    fun parseFormat(j: JSONObject): StreamPlayer.Format? {
        if (j.optString("codec") != "aac") return null
        val csd = runCatching { Base64.decode(j.getString("csd"), Base64.NO_WRAP) }.getOrNull() ?: return null
        return StreamPlayer.Format(j.optInt("sampleRate", 48000), j.optInt("channels", 2), csd)
    }

    fun nowPlaying(np: NowPlaying?): String = JSONObject().apply {
        put("t", "nowPlaying")
        if (np != null) {
            put("title", np.title ?: "")
            put("artist", np.artist ?: "")
            put("app", np.app ?: "")
            put("playing", np.playing)
        }
    }.toString()

    fun parseNowPlaying(j: JSONObject): NowPlaying? {
        if (!j.has("app")) return null
        return NowPlaying(
            title = j.optString("title").ifEmpty { null },
            artist = j.optString("artist").ifEmpty { null },
            app = j.optString("app").ifEmpty { null },
            playing = j.optBoolean("playing"),
        )
    }

    fun audioFrame(ptsUs: Long, data: ByteArray, length: Int): ByteString {
        val bb = ByteBuffer.allocate(HEADER + length)
        bb.put(FRAME_AUDIO)
        bb.putLong(ptsUs)
        bb.put(data, 0, length)
        return bb.array().toByteString()
    }

    /** Returns (ptsUs, aacFrame) or null if this isn't an audio frame. */
    fun parseAudio(bytes: ByteString): Pair<Long, ByteArray>? {
        if (bytes.size <= HEADER || bytes[0] != FRAME_AUDIO) return null
        val arr = bytes.toByteArray()
        val pts = ByteBuffer.wrap(arr, 1, 8).long
        return pts to arr.copyOfRange(HEADER, arr.size)
    }
}
