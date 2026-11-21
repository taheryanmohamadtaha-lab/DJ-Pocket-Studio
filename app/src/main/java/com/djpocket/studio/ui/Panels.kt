package com.djpocket.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djpocket.studio.audio.AudioEngine
import com.djpocket.studio.audio.Deck
import com.djpocket.studio.audio.SR
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaveView(d: Deck, color: Color, modifier: Modifier) {
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { d.zoom = if (d.zoom >= 12.0) 3.0 else d.zoom * 2.0 },
                    onTap = { o ->
                        val off = (o.x - size.width / 2f) / size.width * d.zoom * SR
                        d.seekFrames(d.pos + off)
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, dx ->
                    ch.consume()
                    d.seekFrames(d.pos - dx / size.width * d.zoom * SR)
                }
            }
    ) {
        val tk = Tick.n
        val w = size.width; val h = size.height; val mid = h / 2f
        drawRect(Th.p.panel2.copy(alpha = 0.5f))
        val t = d.track ?: return@Canvas
        val win = d.zoom
        val posSec = d.pos / SR + (if (tk < 0) 1 else 0)
        var x = 0f
        val step = 2f
        while (x < w) {
            val ts = posSec + ((x - w / 2f) / w) * win
            val bin = (ts * 100).toInt()
            if (ts >= 0 && bin < t.peaks.size) {
                val hh = t.peaks[bin] * mid * 0.95f
                val c = lerp(Color.White, color, (0.35f + t.lows[bin] * 0.65f).coerceIn(0f, 1f))
                drawLine(c, Offset(x, mid - hh), Offset(x, mid + hh), strokeWidth = step)
            }
            x += step
        }
        // beat grid
        val bl = 60.0 / d.bpm
        val fb = d.firstBeat / SR
        var k = ceil((posSec - win / 2 - fb) / bl).toInt()
        var guard = 0
        while (guard < 80) {
            val bt = fb + k * bl
            if (bt > posSec + win / 2) break
            if (bt >= 0) {
                val bx = ((bt - posSec) / win * w + w / 2).toFloat()
                val strong = k % 4 == 0
                drawLine(Color.White.copy(alpha = if (strong) 0.7f else 0.25f), Offset(bx, 0f),
                    Offset(bx, if (strong) h else h * 0.3f), strokeWidth = if (strong) 2f else 1f)
            }
            k++; guard++
        }
        // loop region
        if (d.loopOn && d.loopEnd > d.loopStart) {
            val x1 = ((d.loopStart / SR - posSec) / win * w + w / 2).toFloat()
            val x2 = ((d.loopEnd / SR - posSec) / win * w + w / 2).toFloat()
            drawRect(Color(0x5534C759), Offset(x1, 0f), Size((x2 - x1).coerceAtLeast(1f), h))
        }
        // hot cues
        for (i in 0 until 8) {
            val c = d.hotCues[i]
            if (c >= 0) {
                val cx = ((c / SR - posSec) / win * w + w / 2).toFloat()
                drawRect(CueColors[i], Offset(cx - 1f, 0f), Size(3f, h))
                drawCircle(CueColors[i], 4f, Offset(cx, 5f))
            }
        }
        drawLine(Color(0xFFFF3B30), Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
    }
}

@Composable
fun Jog(d: Deck, color: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cx = size.width / 2f; val cy = size.height / 2f
                    fun ang(o: Offset): Double = Math.toDegrees(atan2((o.y - cy).toDouble(), (o.x - cx).toDouble()))
                    var prevA = ang(down.position)
                    var prevT = down.uptimeMillis
                    d.touchStart(Th.jogBend)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val a = ang(ch.position)
                        var da = a - prevA
                        if (da > 180) da -= 360
                        if (da < -180) da += 360
                        val dt = ch.uptimeMillis - prevT
                        if (dt > 0) { d.touchMove(da, dt); prevA = a; prevT = ch.uptimeMillis }
                        ch.consume()
                    }
                    d.touchEnd()
                }
            }
        ) {
            val tk = Tick.n
            val r = size.minDimension / 2f
            val c = center
            val t = d.track
            val angle = (((d.pos / SR * 200.0) % 360.0) + (if (tk < 0) 1 else 0)).toFloat()
            drawCircle(Color(0xFF08080A), r * 0.98f, c)
            drawCircle(color.copy(alpha = 0.25f), r * 0.95f, c, style = Stroke(r * 0.07f))
            if (t != null) {
                val f = (d.pos / t.frames).toFloat().coerceIn(0f, 1f)
                drawArc(color, -90f, 360f * f, false, Offset(c.x - r * 0.95f, c.y - r * 0.95f),
                    Size(r * 1.9f, r * 1.9f), style = Stroke(r * 0.07f))
            }
            rotate(angle, c) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3A44), Color(0xFF14141A)), center = c, radius = r * 0.8f), r * 0.8f, c)
                for (i in 0 until 24) {
                    val a = Math.toRadians(i * 15.0)
                    drawLine(Color(0x33FFFFFF),
                        Offset(c.x + cos(a).toFloat() * r * 0.55f, c.y + sin(a).toFloat() * r * 0.55f),
                        Offset(c.x + cos(a).toFloat() * r * 0.78f, c.y + sin(a).toFloat() * r * 0.78f), strokeWidth = 2f)
                }
                drawLine(color, Offset(c.x, c.y - r * 0.8f), Offset(c.x, c.y - r * 0.52f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
            }
            drawCircle(Color(0xFF0B0B10), r * 0.42f, c)
            drawCircle(color.copy(alpha = 0.6f), r * 0.42f, c, style = Stroke(2f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LiveText(Th.p.text, 16.sp) { if (d.track == null) "--" else "%.1f".format(d.bpm * d.tempo) }
            Text("BPM", color = Th.p.dim, fontSize = 8.sp)
        }
    }
}

@Composable
fun TempoSlider(e: AudioEngine, d: Deck, color: Color, modifier: Modifier) {
    val tk = Tick.n
    val v = ((d.tempo - 1.0) / e.pitchRange).toFloat().coerceIn(-1f, 1f)
    Box(modifier) {
        VSlider(v, { nv -> d.tempo = 1.0 + nv * e.pitchRange }, color, Modifier.fillMaxSize().padding(top = 12.dp), -1f, 1f, 0f)
        Text("%+.1f".format((d.tempo - 1.0) * 100), color = Th.p.dim, fontSize = 8.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = (tk * 0).dp))
    }
}

@Composable
fun DeckPanel(e: AudioEngine, d: Deck, onLoad: (Int) -> Unit, modifier: Modifier) {
    val color = if (d.id == 0) Th.p.a else Th.p.b
    val other = if (d.id == 0) e.b else e.a
    Row(modifier.fillMaxHeight().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
        TempoSlider(e, d, color, Modifier.width(30.dp).fillMaxHeight().padding(vertical = 2.dp))
        Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp), contentAlignment = Alignment.Center) {
            Jog(d, color, Modifier.aspectRatio(1f))
        }
        Column(Modifier.width(54.dp).fillMaxHeight().padding(2.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            val bm = Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp)
            Btn("LOAD", color, bm, protect = true, onTap = { onLoad(d.id) })
            Btn("CUE", color, bm, protect = true, onPress = { d.cueDown() }, onRelease = { d.cueUp() })
            Btn("PLAY", color, bm, protect = true, activeFn = { d.playing }, onTap = { d.togglePlay() })
            Btn("SYNC", color, bm, protect = true, onTap = { d.syncTo(other) })
        }
    }
}

@Composable
fun Channel(d: Deck, color: Color, modifier: Modifier = Modifier) {
    var gain by remember { mutableFloatStateOf(d.gainK) }
    var hi by remember { mutableFloatStateOf(d.hi) }
    var mid by remember { mutableFloatStateOf(d.mid) }
    var lo by remember { mutableFloatStateOf(d.lo) }
    var filt by remember { mutableFloatStateOf(d.filt) }
    var vol by remember { mutableFloatStateOf(d.volume) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Knob("GAIN", gain, { gain = it; d.gainK = it }, color, 24.dp)
            Knob("FILT", filt, { filt = it; d.filt = it }, color, 24.dp)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Knob("HI", hi, { hi = it; d.hi = it }, color, 24.dp,
                    onLong = { val nv = if (hi <= -0.97f) 0f else -1f; hi = nv; d.hi = nv })
                Knob("MID", mid, { mid = it; d.mid = it }, color, 24.dp,
                    onLong = { val nv = if (mid <= -0.97f) 0f else -1f; mid = nv; d.mid = nv })
                Knob("LOW", lo, { lo = it; d.lo = it }, color, 24.dp,
                    onLong = { val nv = if (lo <= -0.97f) 0f else -1f; lo = nv; d.lo = nv })
            }
            VSlider(vol, { vol = it; d.volume = it }, color, Modifier.width(24.dp).fillMaxHeight().padding(vertical = 4.dp), def = 0.85f)
            Meter(Modifier.width(7.dp).fillMaxHeight().padding(vertical = 4.dp)) { d.level }
        }
    }
}

@Composable
fun Mixer(e: AudioEngine, modifier: Modifier) {
    var xf by remember { mutableFloatStateOf(e.xfader) }
    Column(modifier.fillMaxHeight().background(Th.p.panel).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Channel(e.a, Th.p.a, Modifier.weight(1f).fillMaxHeight())
            Channel(e.b, Th.p.b, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", color = Th.p.a, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            HSlider(xf, { xf = it; e.xfader = it }, Color.White, Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp), def = 0.5f)
            Text("B", color = Th.p.b, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
fun BottomPanel(e: AudioEngine, d: Deck, onCues: (Deck) -> Unit, modifier: Modifier) {
    val color = if (d.id == 0) Th.p.a else Th.p.b
    var tab by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(d.id) }
    Column(modifier.padding(2.dp)) {
        Row(Modifier.fillMaxWidth().height(22.dp)) {
            listOf("HOT CUE", "LOOP", "FX", "GRID").forEachIndexed { i, s ->
                Btn(s, color, Modifier.weight(1f).fillMaxHeight().padding(1.dp), activeFn = { tab == i }, onTap = { tab = i })
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            when (tab) {
                0 -> Row(Modifier.fillMaxSize()) {
                    for (i in 0 until 8) {
                        Btn("${i + 1}", CueColors[i], Modifier.weight(1f).fillMaxHeight().padding(1.dp),
                            activeFn = { d.hotCues[i] >= 0 },
                            onTap = { d.jumpCue(i); onCues(d) },
                            onLong = { d.hotCues[i] = -1.0; onCues(d) })
                    }
                }
                1 -> Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    listOf("1/2" to 0.5, "1" to 1.0, "2" to 2.0, "4" to 4.0, "8" to 8.0, "16" to 16.0, "32" to 32.0).forEach { (s, bts) ->
                        Btn(s, color, Modifier.width(34.dp).fillMaxHeight().padding(1.dp), onTap = { d.autoLoop(bts) })
                    }
                    Btn("IN", color, Modifier.width(34.dp).fillMaxHeight().padding(1.dp), onTap = { d.loopIn() })
                    Btn("OUT", color, Modifier.width(34.dp).fillMaxHeight().padding(1.dp), onTap = { d.loopOut() })
                    Btn("EXIT", color, Modifier.width(38.dp).fillMaxHeight().padding(1.dp), activeFn = { d.loopOn }, onTap = { d.loopExit() })
                    Btn("RELOOP", color, Modifier.width(50.dp).fillMaxHeight().padding(1.dp), onTap = { d.reloop() })
                }
                2 -> Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    listOf("A", "B", "M").forEachIndexed { i, s ->
                        Btn(s, color, Modifier.width(26.dp).fillMaxHeight().padding(1.dp), activeFn = { target == i }, onTap = { target = i })
                    }
                    listOf("OFF", "ECHO", "VERB", "FLNG", "PHSR", "CRSH", "GATE", "ROLL").forEachIndexed { i, s ->
                        Btn(s, color, Modifier.width(38.dp).fillMaxHeight().padding(1.dp),
                            activeFn = { e.fx[target].type == i }, onTap = { e.fx[target].type = i })
                    }
                    var wet by remember(target) { mutableFloatStateOf(e.fx[target].wet) }
                    var amt by remember(target) { mutableFloatStateOf(e.fx[target].p) }
                    Knob("WET", wet, { wet = it; e.fx[target].wet = it }, color, 26.dp, 0f, 1f, 0.6f)
                    Knob("AMT", amt, { amt = it; e.fx[target].p = it }, color, 26.dp, 0f, 1f, 0.5f)
                    Btn("BRAKE", color, Modifier.width(44.dp).fillMaxHeight().padding(1.dp), onTap = { d.brake() })
                    Btn("SPIN", color, Modifier.width(38.dp).fillMaxHeight().padding(1.dp), onTap = { d.backspin() })
                }
                else -> Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    LiveText(color, 13.sp, Modifier.width(54.dp)) { "%.2f".format(d.bpm) }
                    Btn("-0.1", color, Modifier.width(36.dp).fillMaxHeight().padding(1.dp), onTap = { d.bpm = (d.bpm - 0.1).coerceAtLeast(40.0) })
                    Btn("+0.1", color, Modifier.width(36.dp).fillMaxHeight().padding(1.dp), onTap = { d.bpm = (d.bpm + 0.1).coerceAtMost(240.0) })
                    Btn("/2", color, Modifier.width(30.dp).fillMaxHeight().padding(1.dp), onTap = { d.bpm = (d.bpm / 2).coerceAtLeast(40.0) })
                    Btn("x2", color, Modifier.width(30.dp).fillMaxHeight().padding(1.dp), onTap = { d.bpm = (d.bpm * 2).coerceAtMost(240.0) })
                    Btn("<", color, Modifier.width(30.dp).fillMaxHeight().padding(1.dp), onTap = { d.firstBeat -= 441.0 })
                    Btn(">", color, Modifier.width(30.dp).fillMaxHeight().padding(1.dp), onTap = { d.firstBeat += 441.0 })
                    Btn("BEAT 1 = NOW", color, Modifier.width(86.dp).fillMaxHeight().padding(1.dp), onTap = { d.firstBeat = d.pos })
                }
            }
        }
    }
}
