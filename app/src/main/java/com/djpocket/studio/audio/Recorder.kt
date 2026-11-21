package com.djpocket.studio.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Records the final master mix (post EQ / crossfader / FX / limiter) to a 16-bit WAV file. */
class Recorder {
    private var raf: RandomAccessFile? = null
    private val q = ArrayBlockingQueue<ShortArray>(1024)
    @Volatile var recording = false
    @Volatile var paused = false
    @Volatile var frames = 0L
    @Volatile var dropped = 0
    var file: File? = null
    private var thread: Thread? = null

    val bytes: Long get() = frames * 4

    fun start(f: File) {
        file = f
        val r = RandomAccessFile(f, "rw")
        r.setLength(0)
        r.write(ByteArray(44))
        raf = r
        frames = 0; dropped = 0; q.clear(); paused = false
        recording = true
        thread = Thread { writerLoop() }.also { it.start() }
    }

    /** Called from the audio thread with interleaved stereo floats. */
    fun feed(buf: FloatArray, n: Int) {
        if (!recording || paused) return
        val s = ShortArray(n * 2)
        for (i in 0 until n * 2) {
            val v = (buf[i] * 32767f).coerceIn(-32768f, 32767f)
            s[i] = v.toInt().toShort()
        }
        if (q.offer(s)) frames += n else dropped++
    }

    private fun writerLoop() {
        val r = raf ?: return
        while (recording || q.isNotEmpty()) {
            val s = q.poll(50, TimeUnit.MILLISECONDS) ?: continue
            val bb = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(s)
            r.write(bb.array())
        }
    }

    fun stop() {
        if (!recording) return
        recording = false
        thread?.join()
        val r = raf ?: return
        val dataLen = frames * 4
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt((36 + dataLen).toInt()); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(2)
        h.putInt(SR); h.putInt(SR * 4); h.putShort(4); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataLen.toInt())
        r.seek(0); r.write(h.array()); r.close()
        raf = null
    }
}
