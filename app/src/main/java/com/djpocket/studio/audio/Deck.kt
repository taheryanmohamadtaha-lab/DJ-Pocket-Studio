package com.djpocket.studio.audio

import kotlin.math.*

class Deck(val id: Int) {
    @Volatile var track: Track? = null
    @Volatile var playing = false
    @Volatile var pos = 0.0
    @Volatile var tempo = 1.0
    @Volatile var bend = 0.0
    @Volatile var scratching = false
    @Volatile var scratchVel = 0.0
    @Volatile var lastScratchNs = 0L
    @Volatile var bendTouch = false
    @Volatile var bpm = 120.0
    @Volatile var firstBeat = 0.0
    @Volatile var cuePoint = 0.0
    @Volatile var cuePreview = false
    @Volatile var loopOn = false
    @Volatile var loopStart = 0.0
    @Volatile var loopEnd = -1.0
    @Volatile var brakeMode = 0
    @Volatile var zoom = 6.0
    @Volatile var level = 0f
    val hotCues = DoubleArray(8) { -1.0 }

    // mixer controls (-1..1 unless noted)
    @Volatile var gainK = 0f
    @Volatile var hi = 0f
    @Volatile var mid = 0f
    @Volatile var lo = 0f
    @Volatile var filt = 0f
    @Volatile var volume = 0.85f

    private var curVel = 0.0
    private var backFrames = 0
    private val eqL = arrayOf(Biquad(), Biquad(), Biquad())
    private val eqR = arrayOf(Biquad(), Biquad(), Biquad())
    private val fL = Biquad()
    private val fR = Biquad()
    private var filtActive = false
    private var lh = 999f; private var lm = 999f; private var ll = 999f; private var lf = 999f
    private var curVol = 0.85f
    private var curGain = 1f

    val title: String get() = track?.name ?: ""
    fun beatLen(): Double = 60.0 / bpm * SR

    fun loadTrack(t: Track) {
        playing = false
        pos = 0.0; curVel = 0.0; cuePoint = 0.0
        loopOn = false; loopEnd = -1.0; brakeMode = 0
        bpm = t.bpm.toDouble(); firstBeat = t.firstBeat
        tempo = 1.0; bend = 0.0
        for (i in 0 until 8) hotCues[i] = -1.0
        track = t
    }

    // ---- transport ----
    fun togglePlay() {
        if (track == null) return
        if (playing) playing = false else { brakeMode = 0; playing = true }
    }

    fun pause() { playing = false; brakeMode = 0 }

    fun cueDown() {
        if (track == null) return
        if (playing) { playing = false; pos = cuePoint }
        else if (abs(pos - cuePoint) > 2000) cuePoint = pos
        else { cuePreview = true; brakeMode = 0; playing = true }
    }

    fun cueUp() {
        if (cuePreview) { cuePreview = false; playing = false; pos = cuePoint }
    }

    fun seekFrames(f: Double) {
        val t = track ?: return
        pos = f.coerceIn(0.0, (t.frames - 2).toDouble())
    }

    /** Returns true if a cue was newly stored. */
    fun jumpCue(i: Int): Boolean {
        if (track == null) return false
        return if (hotCues[i] >= 0) { pos = hotCues[i]; brakeMode = 0; playing = true; false }
        else { hotCues[i] = pos; true }
    }

    // ---- loops ----
    fun autoLoop(beats: Double) {
        if (track == null) return
        val len = beatLen() * beats
        val k = floor((pos - firstBeat) / len)
        loopStart = firstBeat + k * len
        loopEnd = loopStart + len
        loopOn = true
    }

    fun loopIn() { loopStart = pos; loopEnd = -1.0; loopOn = false }
    fun loopOut() { if (pos > loopStart + 500) { loopEnd = pos; loopOn = true } }
    fun loopExit() { loopOn = false }
    fun reloop() { if (loopEnd > loopStart) loopOn = true }

    // ---- sync ----
    fun syncTo(o: Deck) {
        if (track == null || o.track == null) return
        tempo = (o.bpm * o.tempo / bpm).coerceIn(0.25, 2.0)
        val phO = (((o.pos - o.firstBeat) / o.beatLen()) % 1.0 + 1.0) % 1.0
        val k0 = (pos - firstBeat) / beatLen()
        val base = floor(k0)
        var bestK = base + phO; var bd = abs(bestK - k0)
        for (c in doubleArrayOf(base + phO + 1, base + phO - 1)) {
            if (abs(c - k0) < bd) { bd = abs(c - k0); bestK = c }
        }
        seekFrames(firstBeat + bestK * beatLen())
    }

    // ---- fx on transport ----
    fun brake() { if (playing) brakeMode = 1 }
    fun backspin() { if (playing) { brakeMode = 2; backFrames = SR / 2 } }

    // ---- jog ----
    fun touchStart(bendMode: Boolean) {
        bendTouch = bendMode
        if (!bendMode) { scratchVel = 0.0; lastScratchNs = System.nanoTime(); scratching = true }
        else lastScratchNs = System.nanoTime()
    }

    fun touchMove(deltaDeg: Double, dtMs: Long) {
        val vel = deltaDeg * 5.0 / dtMs
        lastScratchNs = System.nanoTime()
        if (bendTouch) bend = vel.coerceIn(-3.0, 3.0) * 0.08 else scratchVel = vel
    }

    fun touchEnd() { scratching = false; bendTouch = false; bend = 0.0 }

    private fun eqDb(v: Float): Double = if (v <= -0.97f) -70.0 else if (v < 0f) v * 26.0 else v * 6.0

    private fun updateFilters() {
        val h = hi; val m = mid; val l = lo; val f = filt
        if (h != lh) { lh = h; val db = eqDb(h); eqL[2].highShelf(4000.0, db); eqR[2].highShelf(4000.0, db) }
        if (m != lm) { lm = m; val db = eqDb(m); eqL[1].peak(1200.0, db, 0.8); eqR[1].peak(1200.0, db, 0.8) }
        if (l != ll) { ll = l; val db = eqDb(l); eqL[0].lowShelf(250.0, db); eqR[0].lowShelf(250.0, db) }
        if (f != lf) {
            lf = f
            filtActive = abs(f) > 0.03f
            if (f < 0) {
                val fc = 22000.0 * 10.0.pow(2.6 * f); fL.lowPass(fc, 0.9); fR.lowPass(fc, 0.9)
            } else {
                val fc = 20.0 * 10.0.pow(2.6 * f); fL.highPass(fc, 0.9); fR.highPass(fc, 0.9)
            }
        }
    }

    /** Audio thread. Writes n frames into l/r (post gain, EQ, filter, channel volume). */
    fun render(l: FloatArray, r: FloatArray, n: Int) {
        val t = track
        if (t == null) {
            java.util.Arrays.fill(l, 0, n, 0f); java.util.Arrays.fill(r, 0, n, 0f)
            level *= 0.9f
            return
        }
        updateFilters()
        val pcm = t.pcm
        val total = t.frames
        val volT = volume
        val gainT = 10.0.pow(gainK * 12.0 / 20.0).toFloat()
        val nowNs = System.nanoTime()
        val scr = scratching
        val stale = nowNs - lastScratchNs > 60_000_000L
        if (bendTouch && nowNs - lastScratchNs > 80_000_000L) bend = 0.0
        val fa = filtActive
        var peak = 0f
        var p = pos
        var v = curVel
        for (i in 0 until n) {
            val target: Double
            val k: Double
            when {
                scr -> { target = if (stale) 0.0 else scratchVel; k = 0.03 }
                brakeMode == 1 -> { target = 0.0; k = 0.00004 }
                brakeMode == 2 -> { target = -4.0; k = 0.0007 }
                playing -> { target = tempo * (1.0 + bend); k = 0.004 }
                else -> { target = 0.0; k = 0.004 }
            }
            v += (target - v) * k
            if (brakeMode == 1 && abs(v) < 0.02) { brakeMode = 0; playing = false; v = 0.0 }
            if (brakeMode == 2) { backFrames--; if (backFrames <= 0) { brakeMode = 0; playing = false; v = 0.0 } }
            p += v
            val le = loopEnd
            if (loopOn && le > loopStart && v > 0 && p >= le) p = loopStart + (p - le)
            if (p < 0) { p = 0.0; if (v < 0) v = 0.0 }
            if (p >= total - 2) { p = (total - 2).toDouble(); v = 0.0; if (!scr) playing = false }
            val idx = p.toInt()
            val fr = (p - idx).toFloat()
            val j = idx * 2
            val sl = pcm[j] * (1f - fr) + pcm[j + 2] * fr
            val sr = pcm[j + 1] * (1f - fr) + pcm[j + 3] * fr
            var yl = (sl / 32768f).toDouble()
            var yr = (sr / 32768f).toDouble()
            for (e in 0 until 3) { yl = eqL[e].process(yl); yr = eqR[e].process(yr) }
            if (fa) { yl = fL.process(yl); yr = fR.process(yr) }
            curVol += (volT - curVol) * 0.002f
            curGain += (gainT - curGain) * 0.002f
            val g = curVol * curGain * 0.8f
            val ol = (yl * g).toFloat(); val orr = (yr * g).toFloat()
            l[i] = ol; r[i] = orr
            val a = max(abs(ol), abs(orr)); if (a > peak) peak = a
        }
        pos = p
        curVel = v
        level = max(peak, level * 0.9f)
    }
}
