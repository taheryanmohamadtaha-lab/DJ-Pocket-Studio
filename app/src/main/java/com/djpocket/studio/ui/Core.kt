package com.djpocket.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object L {
    var fa by mutableStateOf(true)
    fun t(en: String, f: String): String = if (fa) f else en
}

/** Global ~30 fps clock. Only draw blocks and tiny live widgets read it, so the whole tree is not recomposed. */
object Tick { var n by mutableIntStateOf(0) }

object Status { var msg by mutableStateOf("") }

class Pal(
    val bg: Color, val panel: Color, val panel2: Color,
    val a: Color, val b: Color, val text: Color, val dim: Color
)

val CLUB = Pal(Color(0xFF0D0E10), Color(0xFF1B1C1F), Color(0xFF2A2C30), Color(0xFFFF7A18), Color(0xFF19D3E0), Color(0xFFEDEDED), Color(0xFF8A8D93))
val NEON = Pal(Color(0xFF05020F), Color(0xFF120A26), Color(0xFF1F1443), Color(0xFFFF2BD6), Color(0xFF2BE6FF), Color(0xFFF4EEFF), Color(0xFF8C7FB5))

object Th {
    var neon by mutableStateOf(true)
    var live by mutableStateOf(false)
    var lock by mutableStateOf(false)
    var jogBend by mutableStateOf(false)
    val p: Pal get() = if (neon) NEON else CLUB
}

val CueColors = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFD60A), Color(0xFF34C759),
    Color(0xFF00C7BE), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF2D92)
)

fun fmtTime(sec: Double): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

@Composable
fun LiveText(color: Color, size: TextUnit, modifier: Modifier = Modifier, f: () -> String) {
    val tk = Tick.n
    Text(f(), color = color, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = modifier.padding(horizontal = (tk * 0).dp))
}

@Composable
fun Btn(
    text: String, color: Color, modifier: Modifier = Modifier,
    protect: Boolean = false, activeFn: (() -> Boolean)? = null,
    onPress: (() -> Unit)? = null, onRelease: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null, onLong: (() -> Unit)? = null
) {
    val p = Th.p
    val active = if (activeFn != null) { val tk = Tick.n; activeFn() && tk >= 0 } else false
    val pr by rememberUpdatedState(onPress)
    val rl by rememberUpdatedState(onRelease)
    val tp by rememberUpdatedState(onTap)
    val lg by rememberUpdatedState(onLong)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (active) color else p.panel2)
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .pointerInput(protect) {
                detectTapGestures(
                    onPress = {
                        if (protect && Th.lock) return@detectTapGestures
                        pr?.invoke(); tryAwaitRelease(); rl?.invoke()
                    },
                    onTap = { if (!(protect && Th.lock)) tp?.invoke() },
                    onLongPress = { if (!(protect && Th.lock)) lg?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color.Black else color, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.padding(2.dp).clip(RoundedCornerShape(6.dp))
            .background(if (on) color else Th.p.panel2)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (on) Color.Black else Th.p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Knob(
    label: String, value: Float, onChange: (Float) -> Unit, color: Color,
    dia: Dp = 26.dp, min: Float = -1f, max: Float = 1f, def: Float = 0f,
    onLong: (() -> Unit)? = null
) {
    val cur by rememberUpdatedState(value)
    val oc by rememberUpdatedState(onChange)
    val ol by rememberUpdatedState(onLong)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(dia)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { oc(def) }, onLongPress = { ol?.invoke() })
                }
                .pointerInput(Unit) {
                    detectDragGestures { ch, d ->
                        ch.consume()
                        val nv = (cur - d.y / (size.height * 2.2f) * (max - min)).coerceIn(min, max)
                        oc(nv)
                    }
                }
        ) {
            val r = this.size.minDimension / 2f
            val pad = r * 0.25f
            val tl = Offset(pad, pad)
            val sz = Size(2 * r - 2 * pad, 2 * r - 2 * pad)
            val f = ((cur - min) / (max - min)).coerceIn(0f, 1f)
            drawArc(Color(0xFF3A3A40), 135f, 270f, false, tl, sz, style = Stroke(r * 0.22f, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * f, false, tl, sz, style = Stroke(r * 0.22f, cap = StrokeCap.Round))
            val ang = Math.toRadians((135f + 270f * f).toDouble())
            val c = Offset(r, r)
            drawLine(Color.White, Offset(c.x + cos(ang).toFloat() * r * 0.15f, c.y + sin(ang).toFloat() * r * 0.15f),
                Offset(c.x + cos(ang).toFloat() * r * 0.55f, c.y + sin(ang).toFloat() * r * 0.55f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
        }
        Text(label, fontSize = 8.sp, color = Th.p.dim, maxLines = 1)
    }
}

@Composable
fun VSlider(
    value: Float, onChange: (Float) -> Unit, color: Color, modifier: Modifier,
    min: Float = 0f, max: Float = 1f, def: Float? = null
) {
    val oc by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (def != null) oc(def) }) }
            .pointerInput(Unit) {
                fun conv(y: Float): Float = (max - (y / size.height.coerceAtLeast(1)) * (max - min)).coerceIn(min, max)
                detectVerticalDragGestures(onDragStart = { o -> oc(conv(o.y)) }) { ch, _ ->
                    ch.consume(); oc(conv(ch.position.y))
                }
            }
    ) {
        val w = size.width; val h = size.height
        drawLine(Color(0xFF3A3A40), Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val y = (1f - f) * h
        val hh = w * 0.7f
        drawRect(color, Offset(w * 0.05f, (y - hh / 2).coerceIn(0f, h - hh)), Size(w * 0.9f, hh))
        drawRect(Color.Black, Offset(w * 0.05f, (y - 1f).coerceIn(0f, h)), Size(w * 0.9f, 2f))
    }
}

@Composable
fun HSlider(
    value: Float, onChange: (Float) -> Unit, color: Color, modifier: Modifier,
    min: Float = 0f, max: Float = 1f, def: Float? = null
) {
    val oc by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (def != null) oc(def) }) }
            .pointerInput(Unit) {
                fun conv(x: Float): Float = (min + (x / size.width.coerceAtLeast(1)) * (max - min)).coerceIn(min, max)
                detectHorizontalDragGestures(onDragStart = { o -> oc(conv(o.x)) }) { ch, _ ->
                    ch.consume(); oc(conv(ch.position.x))
                }
            }
    ) {
        val w = size.width; val h = size.height
        drawLine(Color(0xFF3A3A40), Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = h * 0.14f, cap = StrokeCap.Round)
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val x = f * w
        val ww = h * 0.9f
        drawRect(color, Offset((x - ww / 2).coerceIn(0f, w - ww), h * 0.05f), Size(ww, h * 0.9f))
        drawRect(Color.Black, Offset((x - 1f).coerceIn(0f, w), h * 0.05f), Size(2f, h * 0.9f))
    }
}

@Composable
fun Meter(modifier: Modifier, level: () -> Float) {
    Canvas(modifier) {
        val tk = Tick.n
        val lv = level().coerceIn(0f, 1f)
        val segs = 12
        val gap = 2f
        val sh = (size.height - gap * (segs - 1)) / segs
        for (s in 0 until segs) {
            val on = s < (lv * segs).toInt() + (if (tk < 0) 1 else 0)
            val c = when { s >= 10 -> Color(0xFFFF3B30); s >= 8 -> Color(0xFFFFD60A); else -> Color(0xFF34C759) }
            val y = size.height - (s + 1) * sh - s * gap
            drawRect(if (on) c else Color(0xFF26262A), Offset(0f, y), Size(size.width, sh))
        }
    }
}
