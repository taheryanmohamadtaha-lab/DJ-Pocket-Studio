package com.djpocket.studio.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes any audio file the device codecs support (MP3, WAV, FLAC, AAC, OGG...) to 44.1 kHz stereo PCM. */
object Decoder {
    fun decode(ctx: Context, uri: Uri, name: String): Track {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var idx = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { idx = i; fmt = f; break }
        }
        if (idx < 0 || fmt == null) { ex.release(); throw IllegalStateException("No audio track") }
        ex.selectTrack(idx)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var out = ShortArray(1 shl 20)
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        val maxSamples = 44100 * 2 * 60 * 15
        try {
            while (!outDone) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(5000)
                    if (i >= 0) {
                        val ib = codec.getInputBuffer(i)!!
                        val sz = ex.readSampleData(ib, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, sz, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5000)
                if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val cnt = sb.remaining()
                    if (n + cnt > out.size) out = out.copyOf(maxOf(out.size * 2, n + cnt))
                    sb.get(out, n, cnt)
                    n += cnt
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    if (n > maxSamples) outDone = true
                } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
        val srcFrames = n / ch
        if (srcFrames < SR) throw IllegalStateException("Track too short or unreadable")
        val pcm: ShortArray
        if (srcRate == SR && ch == 2) {
            pcm = out.copyOf(srcFrames * 2)
        } else {
            val ratio = srcRate.toDouble() / SR
            val dst = (srcFrames / ratio).toInt()
            pcm = ShortArray(dst * 2)
            for (i in 0 until dst) {
                val sp = i * ratio
                val i0 = sp.toInt()
                val f = (sp - i0).toFloat()
                val i1 = minOf(i0 + 1, srcFrames - 1)
                for (c in 0..1) {
                    val cc = if (ch > 1) c else 0
                    val a = out[i0 * ch + cc].toFloat()
                    val b = out[i1 * ch + cc].toFloat()
                    pcm[i * 2 + c] = (a + (b - a) * f).toInt().toShort()
                }
            }
        }
        return Analysis.build(name, uri.toString(), pcm)
    }
}
