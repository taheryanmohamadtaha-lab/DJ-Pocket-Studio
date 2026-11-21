package com.djpocket.studio.audio

import kotlin.math.*

/** Decoded track: stereo interleaved 16-bit PCM at SR. Waveform bins are 100 per second. */
class Track(
    val name: String,
    val pcm: ShortArray,
    val frames: Int,
    val peaks: FloatArray,
    val lows: FloatArray,
    val bpm: Float,
    val firstBeat: Double,
    val uri: String
)

object Analysis {
    const val BIN = 441

    fun build(name: String, uri: String, pcm: ShortArray): Track {
        val frames = pcm.size / 2
        val bins = frames / BIN
        val peaks = FloatArray(bins)
        val lows = FloatArray(bins)
        var lp = 0f
        for (b in 0 until bins) {
            var pk = 0f; var le = 0f; var te = 1e-9f
            val base = b * BIN
            for (k in 0 until BIN) {
                val m = (pcm[2 * (base + k)] + pcm[2 * (base + k) + 1]) / 65536f
                lp += 0.05f * (m - lp)
                val a = abs(m); if (a > pk) pk = a
                le += lp * lp; te += m * m
            }
            peaks[b] = min(1f, pk * 1.4f)
            lows[b] = sqrt(le / te).coerceIn(0f, 1f)
        }
        val (bpm, fb) = detectBpm(pcm, frames)
        return Track(name, pcm, frames, peaks, lows, bpm, fb, uri)
    }

    /** Energy-flux autocorrelation tempo estimate (80..160 BPM folded) plus first-beat phase. */
    fun detectBpm(pcm: ShortArray, frames: Int): Pair<Float, Double> {
        val hop = 256
        val m = min(frames, SR * 120) / hop
        if (m < 400) return Pair(120f, 0.0)
        val env = FloatArray(m)
        for (i in 0 until m) {
            var s = 0.0
            val base = i * hop
            for (k in 0 until hop) {
                val x = (pcm[2 * (base + k)] + pcm[2 * (base + k) + 1]) / 65536.0
                s += x * x
            }
            env[i] = sqrt(s / hop).toFloat()
        }
        val flux = FloatArray(m)
        for (i in 1 until m) {
            flux[i] = max(0f, ln(1f + 50f * env[i]) - ln(1f + 50f * env[i - 1]))
        }
        val minLag = 55; val maxLag = 165
        val ac = FloatArray(maxLag * 2 + 2)
        for (lag in minLag / 2..min(maxLag * 2, m / 2)) {
            var s = 0.0
            for (i in 0 until m - lag) s += flux[i] * flux[i + lag]
            ac[lag] = (s / (m - lag)).toFloat()
        }
        var best = minLag; var bs = -1f
        val score = FloatArray(maxLag + 2)
        for (lag in minLag..maxLag) {
            val sc = ac[lag] + 0.5f * ac[lag * 2]
            score[lag] = sc
            if (sc > bs) { bs = sc; best = lag }
        }
        var lagF = best.toDouble()
        if (best > minLag && best < maxLag) {
            val y0 = score[best - 1]; val y1 = score[best]; val y2 = score[best + 1]
            val den = y0 - 2 * y1 + y2
            if (den != 0f) lagF += (0.5 * (y0 - y2) / den).coerceIn(-0.5, 0.5)
        }
        var bpm = 60.0 * SR / hop / lagF
        while (bpm < 80) bpm *= 2
        while (bpm > 160) bpm /= 2
        if (abs(bpm - Math.round(bpm)) < 0.15) bpm = Math.round(bpm).toDouble()
        val pe = 60.0 * SR / hop / bpm
        var bestPh = 0.0; var bsum = -1.0
        var ph = 0.0
        while (ph < pe) {
            var s = 0.0
            var k = 0
            while (true) {
                val idx = Math.round(ph + k * pe).toInt()
                if (idx >= m) break
                s += flux[idx]; k++
            }
            if (s > bsum) { bsum = s; bestPh = ph }
            ph += 1.0
        }
        return Pair(bpm.toFloat(), bestPh * hop)
    }
}
