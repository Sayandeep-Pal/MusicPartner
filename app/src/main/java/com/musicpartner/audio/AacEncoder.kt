package com.musicpartner.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import kotlin.math.min

/**
 * PCM 16-bit -> AAC-LC using the phone's built-in MediaCodec encoder.
 * Not thread-safe: call [encode] and [release] from the capture thread only.
 *
 * @param onConfig receives the codec-specific data (AudioSpecificConfig) once, before any frame.
 * @param onFrame receives each raw AAC frame (buffer is reused; copy if you keep it).
 */
class AacEncoder(
    private val sampleRate: Int,
    channels: Int,
    bitrate: Int,
    private val onConfig: (ByteArray) -> Unit,
    private val onFrame: (data: ByteArray, length: Int, ptsUs: Long) -> Unit,
) {
    private val bytesPerSampleFrame = 2 * channels
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val info = MediaCodec.BufferInfo()
    private var out = ByteArray(8192)
    private var totalSamples = 0L

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun encode(pcm: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index < 0) {
                drain()
                continue
            }
            val buffer = codec.getInputBuffer(index) ?: continue
            buffer.clear()
            // Keep whole sample frames together.
            val n = min(buffer.remaining(), length - offset) / bytesPerSampleFrame * bytesPerSampleFrame
            buffer.put(pcm, offset, n)
            val ptsUs = totalSamples * 1_000_000L / sampleRate
            codec.queueInputBuffer(index, 0, n, ptsUs, 0)
            totalSamples += n / bytesPerSampleFrame
            offset += n
            drain()
        }
    }

    private fun drain() {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (index < 0) return
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0) {
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                if (out.size < info.size) out = ByteArray(info.size)
                buffer.get(out, 0, info.size)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    onConfig(out.copyOf(info.size))
                } else {
                    onFrame(out, info.size, info.presentationTimeUs)
                }
            }
            codec.releaseOutputBuffer(index, false)
        }
    }

    fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }
}
