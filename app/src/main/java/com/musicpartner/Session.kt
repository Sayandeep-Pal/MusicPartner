package com.musicpartner

import kotlinx.coroutines.flow.MutableStateFlow

enum class Role { NONE, TEST, HOST, LISTENER }

enum class Conn { IDLE, CONNECTING, CONNECTED, RECONNECTING }

data class NowPlaying(
    val title: String?,
    val artist: String?,
    val app: String?,
    val playing: Boolean,
)

/**
 * Process-wide state shared between the foreground services (which do the work) and the UI
 * (which only observes). StateFlows are thread-safe, so services update them from any thread.
 */
object Session {
    val role = MutableStateFlow(Role.NONE)
    val conn = MutableStateFlow(Conn.IDLE)
    val roomCode = MutableStateFlow<String?>(null)

    /** Host: how many friends are connected. */
    val listeners = MutableStateFlow(0)

    /** Host/test: peak level of captured audio, 0..1. */
    val level = MutableStateFlow(0f)

    /** Host/test: human readable result of "is the audio actually being captured?". */
    val captureVerdict = MutableStateFlow<String?>(null)

    /** Host: upload rate of audio in kbit/s. */
    val kbps = MutableStateFlow(0)

    /** What is playing on the host phone (host reads it locally, listener receives it). */
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Listener: audio waiting in the jitter buffer, in ms. */
    val bufferMs = MutableStateFlow(0)

    /** Listener: true while the host's connection is lost but the room is still alive. */
    val hostAway = MutableStateFlow(false)

    /** Last error / info message to show in the UI. Not cleared by [reset]. */
    val message = MutableStateFlow<String?>(null)

    fun reset() {
        role.value = Role.NONE
        conn.value = Conn.IDLE
        roomCode.value = null
        listeners.value = 0
        level.value = 0f
        captureVerdict.value = null
        kbps.value = 0
        nowPlaying.value = null
        bufferMs.value = 0
        hostAway.value = false
    }
}
