package com.djpocket.studio.audio

import kotlin.math.*

/** 16 pads. All sounds are synthesized in code, so there are no licensing issues. */
class Sampler {
    val names = listOf("KICK", "SNARE", "CLAP", "HI-HAT", "CYMBAL", "SIREN", "AIR HORN", "CROWD",
        "SCRATCH", "VOICE", "RISER", "TOM", "COWBELL", "ZAP", "BASS DROP", "RIM")
    val samples: Array<FloatArray> = Array(16) { gen(it) }
    private val pos = IntArray(16) { -1 }
    @Volatile var loop = false
    @Volatile var volume = 0.8f

    fun trigger(i: Int) { pos[i] = 0 }
    fun stop(i: Int) { pos[i] = -1 }
    fun isActive(i: Int) = pos[i] >= 0

    fun render(l: FloatArray, r: FloatArray, n: Int) {
        val vol = volume; val lp = loop
        for (v in 0 until 16) {
            var p = pos[v]
            if (p < 0) continue
            val s = samples[v]
            for (i in 0 until n) {
                if (p >= s.size) {
                    if (lp) p = 0 else { p = -1; break }
                }
                val x = s[p] * vol
                l[i] += x; r[i] += x
                p++
            }
            pos[v] = p
        }
    }

    private fun gen(i: Int): FloatArray {
        val rnd = java.util.Random(i + 7L)
        fun noise(): Float = rnd.nextFloat() * 2f - 1f
        val len = when (i) {
            0 -> 0.4; 1 -> 0.25; 2 -> 0.3; 3 -> 0.09; 4 -> 0.9; 5 -> 1.2; 6 -> 0.9; 7 -> 1.5
            8 -> 0.35; 9 -> 0.5; 10 -> 1.5; 11 -> 0.4; 12 -> 0.3; 13 -> 0.25; 14 -> 1.0; else -> 0.1
        }
        val n = (len * SR).toInt()
        val o = FloatArray(n)
        var ph = 0.0; var lp = 0f; var prev = 0f
        for (k in 0 until n) {
            val t = k.toDouble() / SR
            var x = 0.0
            when (i) {
                0 -> { ph += 2 * PI * (45 + 120 * exp(-t * 20)) / SR; x = sin(ph) * exp(-t * 7) + (if (t < 0.004) noise() * 0.3 else 0.0) }
                1 -> { x = noise() * exp(-t * 18) * 0.6 + sin(2 * PI * 190 * t) * exp(-t * 22) * 0.5 }
                2 -> {
                    var e = 0.0
                    for (b in 0..2) { val tt = t - b * 0.011; if (tt >= 0) e += exp(-tt * (if (b < 2) 90.0 else 18.0)) }
                    x = noise() * e * 0.6
                }
                3 -> { val nn = noise(); x = (nn - prev) * 0.5 * exp(-t * 55); prev = nn }
                4 -> { val nn = noise(); x = (nn - prev) * 0.5 * exp(-t * 4.5); prev = nn }
                5 -> { ph += 2 * PI * (700 + 350 * sin(2 * PI * 2.2 * t)) / SR; x = sin(ph) * 0.6 * min(1.0, t * 20) * (1 - t / 1.2) }
                6 -> {
                    for (h in intArrayOf(440, 554, 659)) { val q = (t * h) % 1.0; x += (2 * q - 1) * 0.22 }
                    x *= min(1.0, t * 30) * (if (t > 0.7) (0.9 - t) / 0.2 else 1.0)
                }
                7 -> { lp += 0.12f * (noise() - lp); x = lp * 3.0 * sin(PI * t / 1.5) }
                8 -> {
                    ph += 2 * PI * (500 + 400 * sin(2 * PI * 7 * t)) / SR
                    val q = (ph / (2 * PI)) % 1.0
                    x = ((2 * q - 1) * 0.5 + noise() * 0.2) * exp(-t * 6)
                }
                9 -> {
                    var s = 0.0
                    for (h in 1..30) {
                        val fr = h * 140.0
                        val d1 = (fr - 700) / 220; val d2 = (fr - 1150) / 260
                        s += (exp(-d1 * d1) + 0.6 * exp(-d2 * d2)) * sin(2 * PI * fr * t)
                    }
                    x = s * 0.25 * sin(PI * t / 0.5)
                }
                10 -> {
                    ph += 2 * PI * (200 + 1800 * t / 1.5) / SR
                    val nn = noise()
                    x = (sin(ph) * 0.4 + (nn - prev) * 0.3) * (t / 1.5); prev = nn
                }
                11 -> { ph += 2 * PI * (90 + 110 * exp(-t * 14)) / SR; x = sin(ph) * exp(-t * 8) }
                12 -> {
                    val a = if ((t * 540) % 1.0 < 0.5) 1.0 else -1.0
                    val b = if ((t * 800) % 1.0 < 0.5) 1.0 else -1.0
                    x = (a + b) * 0.3 * exp(-t * 9)
                }
                13 -> { ph += 2 * PI * (100 + 3000 * exp(-t * 14)) / SR; x = sin(ph) * (1 - t / 0.25) }
                14 -> { ph += 2 * PI * (120 - 90 * t) / SR; x = sin(ph) * 0.9 * exp(-t * 1.5) }
                else -> { x = sin(2 * PI * 1700 * t) * exp(-t * 60) * 0.6 + noise() * exp(-t * 80) * 0.3 }
            }
            o[k] = x.toFloat()
        }
        var pk = 0f
        for (v in o) pk = max(pk, abs(v))
        if (pk > 0f) for (k in o.indices) o[k] *= 0.85f / pk
        return o
    }
}
