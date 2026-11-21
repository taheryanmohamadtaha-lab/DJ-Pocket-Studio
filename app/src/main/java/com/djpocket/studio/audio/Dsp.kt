package com.djpocket.studio.audio

import kotlin.math.*

const val SR = 44100

/** RBJ biquad (transposed direct form II). */
class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    private fun norm(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0
    }

    fun lowShelf(f: Double, db: Double) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / SR; val c = cos(w)
        val al = sin(w) / (2 * 0.7071); val sa = 2 * sqrt(a) * al
        norm(a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa)
    }

    fun highShelf(f: Double, db: Double) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / SR; val c = cos(w)
        val al = sin(w) / (2 * 0.7071); val sa = 2 * sqrt(a) * al
        norm(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa)
    }

    fun peak(f: Double, db: Double, q: Double) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / SR; val c = cos(w); val al = sin(w) / (2 * q)
        norm(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }

    fun lowPass(f: Double, q: Double) {
        val w = 2 * PI * min(f, SR * 0.45) / SR; val c = cos(w); val al = sin(w) / (2 * q)
        norm((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }

    fun highPass(f: Double, q: Double) {
        val w = 2 * PI * min(f, SR * 0.45) / SR; val c = cos(w); val al = sin(w) / (2 * q)
        norm((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
    }
}

/**
 * One effect slot. type: 0 off, 1 echo, 2 reverb, 3 flanger, 4 phaser, 5 bit crusher, 6 gate, 7 roll.
 * wet and p (the effect-specific amount) are 0..1.
 */
class Fx {
    @Volatile var type = 0
    @Volatile var wet = 0.6f
    @Volatile var p = 0.5f

    private class Comb(val n: Int) {
        val buf = FloatArray(n); var i = 0; var store = 0f
        fun run(x: Float, fb: Float, damp: Float): Float {
            val o = buf[i]
            store = o * (1 - damp) + store * damp
            buf[i] = x + store * fb
            i++; if (i >= n) i = 0
            return o
        }
    }

    private class Ap(val n: Int) {
        val buf = FloatArray(n); var i = 0
        fun run(x: Float): Float {
            val b = buf[i]
            val o = -x + b
            buf[i] = x + b * 0.5f
            i++; if (i >= n) i = 0
            return o
        }
    }

    private class Ap1 {
        var x1 = 0f; var y1 = 0f
        fun run(x: Float, a: Float): Float {
            val y = a * x + x1 - a * y1
            x1 = x; y1 = y
            return y
        }
    }

    private val en = SR * 2
    private val eL = FloatArray(en); private val eR = FloatArray(en); private var ew = 0
    private val cL = arrayOf(Comb(1116), Comb(1188), Comb(1277), Comb(1356))
    private val cR = arrayOf(Comb(1139), Comb(1211), Comb(1300), Comb(1379))
    private val aL = arrayOf(Ap(556), Ap(441), Ap(341), Ap(225))
    private val aR = arrayOf(Ap(579), Ap(464), Ap(364), Ap(248))
    private val fn = 2048
    private val fl = FloatArray(fn); private val fr = FloatArray(fn); private var fw = 0
    private var lfo = 0.0
    private val ph1 = Array(4) { Ap1() }; private val ph2 = Array(4) { Ap1() }
    private var gatePh = 0.0; private var gateG = 1f
    private var crushL = 0f; private var crushR = 0f; private var crushCnt = 0
    private val rn = SR
    private val rl = FloatArray(rn); private val rr = FloatArray(rn); private var rw = 0
    private var rolling = false; private var rstart = 0; private var rlen = 1; private var rpos = 0

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        val t = type; val w = wet; val pp = p
        if (t != 7) {
            rolling = false
            for (i in 0 until n) { rl[rw] = l[i]; rr[rw] = r[i]; rw++; if (rw >= rn) rw = 0 }
        }
        when (t) {
            1 -> {
                val d = ((0.12f + 0.63f * pp) * SR).toInt()
                for (i in 0 until n) {
                    var rd = ew - d; if (rd < 0) rd += en
                    val yl = eL[rd]; val yr = eR[rd]
                    eL[ew] = l[i] + yl * 0.5f; eR[ew] = r[i] + yr * 0.5f
                    l[i] += yl * w; r[i] += yr * w
                    ew++; if (ew >= en) ew = 0
                }
            }
            2 -> {
                val fb = 0.72f + 0.24f * pp
                for (i in 0 until n) {
                    val inL = l[i] * 0.03f; val inR = r[i] * 0.03f
                    var oL = 0f; var oR = 0f
                    for (c in cL) oL += c.run(inL, fb, 0.3f)
                    for (c in cR) oR += c.run(inR, fb, 0.3f)
                    for (a in aL) oL = a.run(oL)
                    for (a in aR) oR = a.run(oR)
                    l[i] += oL * w * 1.5f; r[i] += oR * w * 1.5f
                }
            }
            3 -> {
                val rate = 0.1 + pp * 1.5
                for (i in 0 until n) {
                    lfo += 2 * PI * rate / SR; if (lfo > 2 * PI) lfo -= 2 * PI
                    val d = (0.001 + 0.0035 * (1 + sin(lfo))) * SR
                    var rp = fw - d; while (rp < 0) rp += fn
                    val i0 = rp.toInt(); val f = (rp - i0).toFloat()
                    val i1 = if (i0 + 1 >= fn) 0 else i0 + 1
                    val yl = fl[i0] * (1 - f) + fl[i1] * f
                    val yr = fr[i0] * (1 - f) + fr[i1] * f
                    fl[fw] = l[i] + yl * 0.4f; fr[fw] = r[i] + yr * 0.4f
                    l[i] += yl * w; r[i] += yr * w
                    fw++; if (fw >= fn) fw = 0
                }
            }
            4 -> {
                val rate = 0.2 + pp * 2.0
                for (i in 0 until n) {
                    lfo += 2 * PI * rate / SR; if (lfo > 2 * PI) lfo -= 2 * PI
                    val f = 300 + 1700 * (0.5 + 0.5 * sin(lfo))
                    val tn = tan(PI * f / SR)
                    val a = ((tn - 1) / (tn + 1)).toFloat()
                    var yl = l[i]; var yr = r[i]
                    for (s in 0 until 4) { yl = ph1[s].run(yl, a); yr = ph2[s].run(yr, a) }
                    l[i] += yl * w; r[i] += yr * w
                }
            }
            5 -> {
                val levels = 2.0.pow((3 + (1 - pp) * 12).toDouble()).toFloat()
                val hold = 1 + (pp * 10).toInt()
                for (i in 0 until n) {
                    if (crushCnt <= 0) {
                        crushL = round(l[i] * levels / 2f) * 2f / levels
                        crushR = round(r[i] * levels / 2f) * 2f / levels
                        crushCnt = hold
                    }
                    crushCnt--
                    l[i] = l[i] * (1 - w) + crushL * w
                    r[i] = r[i] * (1 - w) + crushR * w
                }
            }
            6 -> {
                val rate = 2 + pp * 14
                for (i in 0 until n) {
                    gatePh += rate / SR; if (gatePh >= 1.0) gatePh -= 1.0
                    val target = if (gatePh < 0.5) 1f else 1f - w
                    gateG += (target - gateG) * 0.01f
                    l[i] *= gateG; r[i] *= gateG
                }
            }
            7 -> {
                if (!rolling) {
                    rolling = true
                    rlen = ((0.06f + 0.44f * pp) * SR).toInt().coerceAtLeast(64)
                    var s = rw - rlen; if (s < 0) s += rn
                    rstart = s; rpos = 0
                }
                for (i in 0 until n) {
                    var idx = rstart + rpos; if (idx >= rn) idx -= rn
                    l[i] = l[i] * (1 - w) + rl[idx] * w
                    r[i] = r[i] * (1 - w) + rr[idx] * w
                    rpos++; if (rpos >= rlen) rpos = 0
                }
            }
            else -> {}
        }
    }
}
