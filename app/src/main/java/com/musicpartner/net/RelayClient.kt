package com.musicpartner.net

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * WebSocket connection to the relay server that reconnects automatically
 * (1s, 2s, 4s, 8s, then every 10s) until [close] is called.
 * Callbacks arrive on OkHttp's background thread.
 */
class RelayClient(private val url: String, private val listener: Listener) {

    interface Listener {
        fun onOpen()
        fun onText(json: JSONObject)
        fun onBinary(bytes: ByteString)
        fun onDisconnected(willRetry: Boolean, reason: String?)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false
    private var attempt = 0

    fun connect() {
        closed = false
        open()
    }

    private fun open() {
        if (closed) return
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            closed = true
            listener.onDisconnected(false, "Invalid server address: $url")
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== socket) return
                attempt = 0
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== socket) return
                val json = try { JSONObject(text) } catch (e: Exception) { return }
                listener.onText(json)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket !== socket) return
                listener.onBinary(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                dropped(webSocket, reason.ifEmpty { null })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failure: ${t.message}")
                dropped(webSocket, t.message)
            }
        })
    }

    private fun dropped(webSocket: WebSocket, reason: String?) {
        if (webSocket !== socket) return
        socket = null
        if (closed) {
            listener.onDisconnected(false, reason)
            return
        }
        listener.onDisconnected(true, reason)
        val delayMs = min(10_000L, 1000L shl min(attempt, 4))
        attempt++
        try {
            scheduler.schedule({ open() }, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            // scheduler shut down: we're closing
        }
    }

    fun sendText(text: String): Boolean = socket?.send(text) ?: false

    /** Drops the frame instead of queueing if the network can't keep up (keeps latency low). */
    fun sendBinary(bytes: ByteString): Boolean {
        val s = socket ?: return false
        if (s.queueSize() > MAX_QUEUE_BYTES) return false
        return s.send(bytes)
    }

    fun close(finalText: String? = null) {
        closed = true
        val s = socket
        socket = null
        if (s != null) {
            if (finalText != null) s.send(finalText)
            s.close(1000, "bye")
        }
        scheduler.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val TAG = "RelayClient"
        const val MAX_QUEUE_BYTES = 128 * 1024L
    }
}
