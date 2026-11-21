package com.djpocket.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.djpocket.studio.audio.AudioEngine
import com.djpocket.studio.audio.SR
import kotlinx.coroutines.delay

@Composable
fun DjApp(e: AudioEngine, onLoad: (Int) -> Unit, onCues: (com.djpocket.studio.audio.Deck) -> Unit) {
    LaunchedEffect(Unit) { while (true) { delay(33); Tick.n++ } }
    LaunchedEffect(Status.msg) { if (Status.msg.isNotEmpty()) { delay(4000); Status.msg = "" } }
    var showSet by remember { mutableStateOf(false) }
    var showPads by remember { mutableStateOf(false) }
    val p = Th.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(e, onSettings = { showSet = true }, onPads = { showPads = true })
            Row(Modifier.fillMaxWidth().then(if (Th.live) Modifier.weight(1f) else Modifier.height(54.dp)).padding(horizontal = 2.dp, vertical = 2.dp)) {
                WaveView(e.a, p.a, Modifier.weight(1f).fillMaxHeight().padding(end = 1.dp))
                WaveView(e.b, p.b, Modifier.weight(1f).fillMaxHeight().padding(start = 1.dp))
            }
            if (Th.live) LiveBody(e, onCues) else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    DeckPanel(e, e.a, onLoad, Modifier.weight(1f))
                    Mixer(e, Modifier.width(210.dp))
                    DeckPanel(e, e.b, onLoad, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().height(70.dp)) {
                    BottomPanel(e, e.a, onCues, Modifier.weight(1f))
                    BottomPanel(e, e.b, onCues, Modifier.weight(1f))
                }
            }
        }
        if (Status.msg.isNotEmpty()) {
            Text(Status.msg, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
                    .background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        if (showSet) SettingsDialog(e) { showSet = false }
        if (showPads) PadsDialog(e) { showPads = false }
    }
}

@Composable
fun TopBar(e: AudioEngine, onSettings: () -> Unit, onPads: () -> Unit) {
    val p = Th.p
    Row(Modifier.fillMaxWidth().height(30.dp).background(p.panel).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveText(p.a, 11.sp, Modifier.weight(1f)) {
            val t = e.a.track
            if (t == null) "A  --" else "A  ${t.name}  -${fmtTime((t.frames - e.a.pos) / SR)}"
        }
        Btn("REC", Color(0xFFFF3B30), Modifier.width(40.dp).height(22.dp), activeFn = { e.recorder.recording && !e.recorder.paused }, onTap = {
            if (e.recorder.recording) { e.stopRec(); Status.msg = L.t("Saved: ", "ذخیره شد: ") + (e.recorder.file?.absolutePath ?: "") }
            else { val f = e.startRec(); Status.msg = L.t("Recording…", "در حال ضبط…") }
        })
        Btn("II", p.dim, Modifier.width(24.dp).height(22.dp).padding(start = 2.dp), onTap = { if (e.recorder.recording) e.recorder.paused = !e.recorder.paused })
        LiveText(p.text, 10.sp, Modifier.width(92.dp).padding(start = 4.dp)) {
            if (e.recorder.recording) "%s %.1fMB".format(fmtTime(e.recorder.frames.toDouble() / SR), e.recorder.bytes / 1048576.0) else ""
        }
        Canvas(Modifier.width(60.dp).height(10.dp)) {
            val tk = Tick.n
            val lv = e.masterPeak.coerceIn(0f, 1f) + (if (tk < 0) 1f else 0f)
            drawRect(Color(0xFF26262A), Offset.Zero, size)
            val c = if (System.currentTimeMillis() < e.clipUntilMs) Color(0xFFFF3B30) else if (lv > 0.8f) Color(0xFFFFD60A) else Color(0xFF34C759)
            drawRect(c, Offset.Zero, Size(size.width * lv.coerceAtMost(1f), size.height))
        }
        var mv by remember { mutableFloatStateOf(e.master) }
        HSlider(mv, { mv = it; e.master = it }, p.text, Modifier.width(60.dp).height(22.dp).padding(horizontal = 4.dp), def = 0.8f)
        Btn("PADS", p.text, Modifier.width(42.dp).height(22.dp), onTap = onPads)
        Btn("LIVE", p.text, Modifier.width(38.dp).height(22.dp).padding(start = 2.dp), activeFn = { Th.live }, onTap = { Th.live = !Th.live })
        Btn("SET", p.text, Modifier.width(36.dp).height(22.dp).padding(start = 2.dp), onTap = onSettings)
        LiveText(p.b, 11.sp, Modifier.weight(1f).padding(start = 6.dp)) {
            val t = e.b.track
            if (t == null) "B  --" else "B  ${t.name}  -${fmtTime((t.frames - e.b.pos) / SR)}"
        }
    }
}

@Composable
fun LiveBody(e: AudioEngine, onCues: (com.djpocket.studio.audio.Deck) -> Unit) {
    val p = Th.p
    var xf by remember { mutableFloatStateOf(e.xfader) }
    Column(Modifier.fillMaxWidth().height(106.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            for (d in listOf(e.a, e.b)) {
                val c = if (d.id == 0) p.a else p.b
                Row(Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
                    val m = Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                    Btn("PLAY", c, m, protect = true, activeFn = { d.playing }, onTap = { d.togglePlay() })
                    Btn("CUE", c, m, protect = true, onPress = { d.cueDown() }, onRelease = { d.cueUp() })
                    Btn("SYNC", c, m, protect = true, onTap = { d.syncTo(if (d.id == 0) e.b else e.a) })
                }
                if (d.id == 0) {
                    Column(Modifier.weight(1.4f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        HSlider(xf, { xf = it; e.xfader = it }, Color.White, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp), def = 0.5f)
                        Row(Modifier.fillMaxWidth().height(22.dp)) {
                            listOf("ECHO" to 1, "VERB" to 2, "FLNG" to 3, "CRSH" to 5, "ROLL" to 7).forEach { (s, t) ->
                                Btn(s, p.text, Modifier.weight(1f).fillMaxHeight().padding(1.dp), activeFn = { e.fx[2].type == t },
                                    onTap = { e.fx[2].type = if (e.fx[2].type == t) 0 else t })
                            }
                            Btn(if (Th.lock) "UNLOCK" else "LOCK", Color(0xFFFFD60A), Modifier.weight(1.3f).fillMaxHeight().padding(1.dp),
                                activeFn = { Th.lock }, onTap = { Th.lock = !Th.lock })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp)) {
            for (d in listOf(e.a, e.b)) {
                Row(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp)) {
                    for (i in 0 until 8) {
                        Btn("${i + 1}", CueColors[i], Modifier.weight(1f).fillMaxHeight().padding(1.dp),
                            activeFn = { d.hotCues[i] >= 0 },
                            onTap = { d.jumpCue(i); onCues(d) }, onLong = { d.hotCues[i] = -1.0; onCues(d) })
                    }
                }
            }
        }
    }
}

@Composable
fun PadsDialog(e: AudioEngine, onClose: () -> Unit) {
    val p = Th.p
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.background(p.panel).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAMPLER", color = p.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Btn("LOOP", p.a, Modifier.width(56.dp).height(26.dp), activeFn = { e.sampler.loop }, onTap = {
                    e.sampler.loop = !e.sampler.loop
                    if (!e.sampler.loop) for (i in 0 until 16) e.sampler.stop(i)
                })
                Btn("STOP", p.b, Modifier.width(56.dp).height(26.dp).padding(start = 4.dp), onTap = { for (i in 0 until 16) e.sampler.stop(i) })
                var v by remember { mutableFloatStateOf(e.sampler.volume) }
                HSlider(v, { v = it; e.sampler.volume = it }, p.text, Modifier.width(80.dp).height(26.dp).padding(horizontal = 4.dp))
                Btn("X", p.dim, Modifier.width(30.dp).height(26.dp), onTap = onClose)
            }
            for (row in 0 until 4) {
                Row(Modifier.fillMaxWidth().height(52.dp)) {
                    for (col in 0 until 4) {
                        val i = row * 4 + col
                        val c = CueColors[(i / 2) % 8]
                        Btn(e.sampler.names[i], c, Modifier.weight(1f).fillMaxHeight().padding(2.dp),
                            activeFn = { e.sampler.isActive(i) }, onPress = { e.sampler.trigger(i) })
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(e: AudioEngine, onClose: () -> Unit) {
    val p = Th.p
    var range by remember { mutableFloatStateOf(e.pitchRange) }
    var cut by remember { mutableStateOf(e.xfCut) }
    var rev by remember { mutableStateOf(e.xfReverse) }
    var lim by remember { mutableStateOf(e.limiterOn) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.background(p.panel).padding(10.dp).verticalScroll(rememberScrollState())) {
            Text(L.t("Settings", "تنظیمات"), color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(L.t("Theme", "تم"), color = p.dim, fontSize = 11.sp)
            Row {
                Pill("Professional Club", !Th.neon, p.a) { Th.neon = false }
                Pill("RGB Neon", Th.neon, p.a) { Th.neon = true }
            }
            Text(L.t("Language", "زبان"), color = p.dim, fontSize = 11.sp)
            Row {
                Pill("فارسی", L.fa, p.a) { L.fa = true }
                Pill("English", !L.fa, p.a) { L.fa = false }
            }
            Text(L.t("Jog wheel mode", "حالت جاگ"), color = p.dim, fontSize = 11.sp)
            Row {
                Pill("Vinyl (scratch)", !Th.jogBend, p.a) { Th.jogBend = false }
                Pill("Pitch bend", Th.jogBend, p.a) { Th.jogBend = true }
            }
            Text(L.t("Crossfader", "کراس‌فیدر"), color = p.dim, fontSize = 11.sp)
            Row {
                Pill("Smooth", !cut, p.a) { cut = false; e.xfCut = false }
                Pill("Cut", cut, p.a) { cut = true; e.xfCut = true }
                Pill("Reverse", rev, p.b) { rev = !rev; e.xfReverse = rev }
            }
            Text(L.t("Pitch range", "محدوده پیچ"), color = p.dim, fontSize = 11.sp)
            Row {
                for (r in listOf(0.06f, 0.10f, 0.16f, 1.0f)) {
                    Pill("±${(r * 100).toInt()}%", range == r, p.a) { range = r; e.pitchRange = r }
                }
            }
            Text(L.t("Master limiter", "لیمیتر مستر"), color = p.dim, fontSize = 11.sp)
            Row {
                Pill("ON", lim, p.a) { lim = true; e.limiterOn = true }
                Pill("OFF", !lim, p.a) { lim = false; e.limiterOn = false }
            }
            LiveText(p.text, 11.sp) {
                "${L.t("Audio dropouts", "افت صدا")}: ${e.underruns}   ${L.t("Stream rebuilds", "بازسازی خروجی")}: ${e.rebuilds}   44.1kHz"
            }
            Text(L.t("Recordings folder: Android/data/com.djpocket.studio/files/Music", "پوشه ضبط‌ها: Android/data/com.djpocket.studio/files/Music"),
                color = p.dim, fontSize = 10.sp)
            Row { Pill(L.t("Close", "بستن"), true, p.b) { onClose() } }
        }
    }
}
