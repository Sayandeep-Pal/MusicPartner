package com.musicpartner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Listener-side player: AAC frames from the network -> jitter buffer -> decoder -> speaker.
 *
 * Latency policy: start after [PREBUFFER_FRAMES] (~130 ms) are queued. If the network delivers a
 * burst and more than [MAX_FRAMES] (~640 ms) pile up, the oldest are dropped back to
 * [TARGET_FRAMES] so the friend never drifts far behind the host.
 */
class StreamPlayer(private val onBufferMs: (Int) -> Unit) {

    class Format(val sampleRate: Int, val channels: Int, val csd: ByteArray) {
        fun sameAs(o: Format) = sampleRate == o.sampleRate && channels == o.channels && csd.contentEquals(o.csd)
    }

    private class Frame(val ptsUs: Long, val data: ByteArray)

    private val queue = LinkedBlockingDeque<Frame>()
    @Volatile private var format: Format? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "StreamPlayer").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread?.join(1000)
        thread = null
        queue.clear()
    }

    fun setFormat(newFormat: Format) {
        val current = format
        if (current != null && current.sameAs(newFormat)) return
        queue.clear()
        format = newFormat
    }

    fun push(ptsUs: Long, data: ByteArray) {
        if (format == null) return
        queue.offerLast(Frame(ptsUs, data))
        if (queue.size > MAX_FRAMES) {
            while (queue.size > TARGET_FRAMES) queue.pollFirst()
        }
    }

    private fun loop() {
        var codec: MediaCodec? = null
        var track: AudioTrack? = null
        var active: Format? = null
        val info = MediaCodec.BufferInfo()
        var pcm = ByteArray(8192)
        var prebuffering = true
        var lastReport = 0L

        fun releaseAll() {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { track?.stop() }
            runCatching { track?.release() }
            codec = null
            track = null
            active = null
        }

        while (running) {
            try {
                val f = format
                if (f == null) {
                    Thread.sleep(50)
                    continue
                }
                if (f !== active) {
                    releaseAll()
                    codec = createDecoder(f)
                    track = createTrack(f)
                    active = f
                    prebuffering = true
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastReport > 250) {
                    lastReport = now
                    onBufferMs(queue.size * 1024 * 1000 / f.sampleRate)
                }

                if (prebuffering) {
                    if (queue.size < PREBUFFER_FRAMES) {
                        Thread.sleep(10)
                        continue
                    }
                    prebuffering = false
                }

                val frame = queue.pollFirst(250, TimeUnit.MILLISECONDS)
                if (frame == null) {
                    prebuffering = true // underrun: refill before playing again
                    continue
                }

                val c = codec ?: continue
                val t = track ?: continue
                val inIndex = c.dequeueInputBuffer(50_000)
                if (inIndex >= 0) {
                    val ib = c.getInputBuffer(inIndex)
                    if (ib != null && frame.data.size <= ib.capacity()) {
                        ib.clear()
                        ib.put(frame.data)
                        c.queueInputBuffer(inIndex, 0, frame.data.size, frame.ptsUs, 0)
                    } else {
                        c.queueInputBuffer(inIndex, 0, 0, frame.ptsUs, 0)
                    }
                }

                while (true) {
                    val outIndex = c.dequeueOutputBuffer(info, 0)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
                    if (outIndex < 0) break
                    val ob = c.getOutputBuffer(outIndex)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        if (pcm.size < info.size) pcm = ByteArray(info.size)
                        ob.get(pcm, 0, info.size)
                        t.write(pcm, 0, info.size) // blocking: paces the loop at real time
                    }
                    c.releaseOutputBuffer(outIndex, false)
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (!running) break
                Log.e(TAG, "playback error, restarting decoder", e)
                releaseAll()
                try { Thread.sleep(300) } catch (_: InterruptedException) { break }
            }
        }
        releaseAll()
        onBufferMs(0)
    }

    private fun createDecoder(f: Format): MediaCodec {
        val mf = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, f.sampleRate, f.channels).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(f.csd))
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
        }
        return MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(mf, null, null, 0)
            start()
        }
    }

    private fun createTrack(f: Format): AudioTrack {
        val mask = if (f.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(f.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(f.sampleRate)
                    .setChannelMask(mask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .apply { play() }
    }

    private companion object {
        const val TAG = "StreamPlayer"
        const val PREBUFFER_FRAMES = 6   // ~128 ms at 48 kHz (1024 samples/frame)
        const val TARGET_FRAMES = 8      // ~170 ms
        const val MAX_FRAMES = 30        // ~640 ms
    }
}
