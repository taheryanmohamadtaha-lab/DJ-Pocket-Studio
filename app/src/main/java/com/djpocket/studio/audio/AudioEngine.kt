package com.djpocket.studio.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Environment
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * Real-time engine: Deck A + Deck B -> per-deck FX -> crossfader -> sampler -> master FX
 * -> master gain -> limiter -> AudioTrack and (optionally) the WAV recorder.
 */
class AudioEngine(private val ctx: Context) {
    val a = Deck(0)
    val b = Deck(1)
    val fx = arrayOf(Fx(), Fx(), Fx()) // 0 = deck A, 1 = deck B, 2 = master
    val sampler = Sampler()
    val recorder = Recorder()

    @Volatile var xfader = 0.5f
    @Volatile var xfCut = false
    @Volatile var xfReverse = false
    @Volatile var master = 0.8f
    @Volatile var limiterOn = true
    @Volatile var pitchRange = 0.10f
    @Volatile var masterPeak = 0f
    @Volatile var masterRms = 0f
    @Volatile var clipUntilMs = 0L
    @Volatile var underruns = 0
    @Volatile var rebuilds = 0

    private val block = 256
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun deck(i: Int) = if (i == 0) a else b
    fun pauseAll() { a.pause(); b.pause() }

    private fun buildTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val bytes = max(min, block * 2 * 4 * 4)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread { loop() }.also { it.name = "DJ-Audio"; it.start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        recorder.stop()
    }

    fun startRec(): File {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
        dir.mkdirs()
        val f = File(dir, "DJ_Pocket_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav")
        recorder.start(f)
        return f
    }

    fun stopRec() = recorder.stop()

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val n = block
        val al = FloatArray(n); val ar = FloatArray(n)
        val bl = FloatArray(n); val br = FloatArray(n)
        val ml = FloatArray(n); val mr = FloatArray(n)
        val out = FloatArray(n * 2)
        var gAp = 1f; var gBp = 1f
        var lim = 1f
        var at = buildTrack()
        track = at
        at.play()
        while (running) {
            a.render(al, ar, n)
            b.render(bl, br, n)
            fx[0].process(al, ar, n)
            fx[1].process(bl, br, n)

            val x = if (xfReverse) 1f - xfader else xfader
            val tA: Float
            val tB: Float
            if (xfCut) {
                tA = min(1f, (1f - x) * 8f); tB = min(1f, x * 8f)
            } else {
                tA = cos(x * PI / 2).toFloat(); tB = sin(x * PI / 2).toFloat()
            }
            for (i in 0 until n) {
                val t = (i + 1) / n.toFloat()
                val gA = gAp + (tA - gAp) * t
                val gB = gBp + (tB - gBp) * t
                ml[i] = al[i] * gA + bl[i] * gB
                mr[i] = ar[i] * gA + br[i] * gB
            }
            gAp = tA; gBp = tB

            sampler.render(ml, mr, n)
            fx[2].process(ml, mr, n)

            val mv = master
            val lo = limiterOn
            var pk = 0f; var sq = 0f; var clipped = false
            for (i in 0 until n) {
                var l = ml[i] * mv
                var r = mr[i] * mv
                val m = max(abs(l), abs(r))
                if (m > 1f) clipped = true
                if (lo) {
                    val d = if (m * lim > 0.95f) 0.95f / m else 1f
                    lim = if (d < lim) d else lim + (d - lim) * 0.0004f
                    l *= lim; r *= lim
                }
                l = l.coerceIn(-1f, 1f); r = r.coerceIn(-1f, 1f)
                out[2 * i] = l; out[2 * i + 1] = r
                val a2 = max(abs(l), abs(r)); if (a2 > pk) pk = a2
                sq += l * l + r * r
            }
            masterPeak = max(pk, masterPeak * 0.92f)
            masterRms = sqrt(sq / (2 * n))
            if (clipped) clipUntilMs = System.currentTimeMillis() + 800
            recorder.feed(out, n)

            val w = at.write(out, 0, n * 2, AudioTrack.WRITE_BLOCKING)
            if (w < 0) {
                // output device changed or died (headphones/USB/Bluetooth): rebuild the stream
                try { at.release() } catch (_: Exception) {}
                try { Thread.sleep(150) } catch (_: InterruptedException) {}
                try {
                    at = buildTrack(); track = at; at.play(); rebuilds++
                } catch (_: Exception) { Thread.sleep(500) }
            } else {
                underruns = at.underrunCount
            }
        }
        try { at.stop() } catch (_: Exception) {}
        at.release()
    }
}
