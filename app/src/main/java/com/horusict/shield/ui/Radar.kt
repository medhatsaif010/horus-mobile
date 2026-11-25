/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horusict.shield.L
import com.horusict.shield.Trackers

/** One tracker as the radar shows it. */
data class Blip(val s: Trackers.Seen, val score: Int, val level: Int, val zone: Int, val meters: Double, val places: Int, val moving: Boolean)

fun levelColor(l: Int): Color = when (l) { 2 -> C.Danger; 1 -> C.Warn; else -> C.Muted }

/** Where a distance sits on the radar: a log scale, so 1 m, 3 m and 10 m get room of their own. */
private fun frac(m: Double): Float {
    val x = Math.log(m.coerceIn(0.3, 15.0) / 0.3) / Math.log(15.0 / 0.3)
    return (0.12 + 0.8 * x).toFloat()
}

/** A phone cannot tell where a signal comes from, so each tracker keeps one fixed bearing; only the distance means something. */
private fun bearing(id: Int): Float = ((id * 137.508) % 360.0).toFloat()

/**
 * Sonar-style radar: you are the eye in the middle, the rings are 1, 3 and 10 metres, and a sweep line
 * lights each tracker as it passes. Colour is how worrying it is and shape is the kind, so neither
 * carries the meaning alone. Tapping a dot selects it.
 */
@Composable
fun TrackerRadar(blips: List<Blip>, scanning: Boolean, selected: Int?, onSelect: (Int?) -> Unit) {
    val ctx = LocalContext.current
    val still = remember { try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false } }
    val sweep = if (scanning && !still) {
        val t = rememberInfiniteTransition(label = "sweep")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else -1f
    val gold = C.Gold; val line = C.Line; val bg = C.Surface; val eye = C.Bg; val dim = C.Dim
    val desc = L.t("رادار أجهزة التتبع: ${blips.size} جهاز", "رادار أجهزة التتبع: ${blips.size} جهاز", "Tracker radar: ${blips.size} devices") +
        if (blips.any { it.level == 2 }) L.t("، منها جهاز قد يكون يتتبعك", "، منهم جهاز ممكن يكون بيتتبعك", ", one may be following you") else ""
    val now = System.currentTimeMillis()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(300.dp).semantics { contentDescription = desc }.pointerInput(blips) {
            detectTapGestures { p ->
                val r = size.width / 2f; val c = Offset(r, size.height / 2f)
                val hit = blips.minByOrNull { b -> (pos(c, r, b) - p).getDistance() }
                onSelect(hit?.takeIf { (pos(c, r, it) - p).getDistance() < 34.dp.toPx() }?.s?.id)
            }
        }) {
            val r = size.minDimension / 2f; val c = center
            drawCircle(bg, r, c)
            drawCircle(gold.copy(alpha = 0.45f), r, c, style = Stroke(1.dp.toPx()))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb(150, (gold.red * 255).toInt(), (gold.green * 255).toInt(), (gold.blue * 255).toInt()); textSize = 11.sp.toPx(); isAntiAlias = true }
            listOf(1.0 to "1m", 3.0 to "3m", 10.0 to "10m").forEach { (m, t) ->
                val rr = r * frac(m)
                drawCircle(gold.copy(alpha = 0.22f), rr, c, style = Stroke(1.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(t, c.x + 4.dp.toPx(), c.y - rr - 3.dp.toPx(), paint)
            }
            drawLine(line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
            drawLine(line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
            if (sweep >= 0f) {
                drawArc(gold.copy(alpha = 0.16f), sweep - 35f, 35f, true, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r))
                val a = Math.toRadians(sweep.toDouble())
                drawLine(gold, c, Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat()), 2.dp.toPx())
            }
            blips.forEach { b ->
                // brightest just after the sweep passes, then fades; trackers not heard for a while fade more
                val behind = if (sweep < 0f) 0f else ((sweep - bearing(b.s.id)) % 360f + 360f) % 360f
                var alpha = 1f - 0.6f * (behind / 360f)
                if (now - b.s.last > 10_000L) alpha *= 0.45f
                val col = levelColor(b.level).copy(alpha = alpha)
                val p = pos(c, r, b)
                if (b.level == 2) drawCircle(col.copy(alpha = 0.22f * alpha), 16.dp.toPx(), p)
                shape(b.s.kind, col, p, 7.dp.toPx())
                if (b.s.id == selected) drawCircle(gold, 13.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
            drawCircle(gold, 11.dp.toPx(), c)
            drawCircle(eye, 4.dp.toPx(), c)
            if (blips.isEmpty() && !scanning) drawCircle(dim.copy(alpha = 0.2f), r * 0.12f, c, style = Stroke(1.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        listOf(2, 1, 0).forEach { l ->
            Text("● " + Trackers.levelName(l), color = levelColor(l), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
    Text(L.t("● AirTag/Apple   ■ SmartTag   ▲ Tile   ◆ Google   ○ أخرى", "● AirTag/Apple   ■ SmartTag   ▲ Tile   ◆ Google   ○ غيره", "● AirTag/Apple   ■ SmartTag   ▲ Tile   ◆ Google   ○ other"),
        color = C.Dim, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

private fun pos(c: Offset, r: Float, b: Blip): Offset {
    val a = Math.toRadians(bearing(b.s.id).toDouble()); val d = r * frac(b.meters)
    return Offset(c.x + d * Math.cos(a).toFloat(), c.y + d * Math.sin(a).toFloat())
}

private fun DrawScope.shape(k: Trackers.Kind, col: Color, p: Offset, s: Float) {
    when (k) {
        Trackers.Kind.APPLE -> drawCircle(col, s, p)
        Trackers.Kind.SAMSUNG -> drawRect(col, Offset(p.x - s * 0.85f, p.y - s * 0.85f), Size(s * 1.7f, s * 1.7f))
        Trackers.Kind.TILE -> drawPath(Path().apply { moveTo(p.x, p.y - s); lineTo(p.x + s, p.y + s * 0.8f); lineTo(p.x - s, p.y + s * 0.8f); close() }, col)
        Trackers.Kind.GOOGLE -> drawPath(Path().apply { moveTo(p.x, p.y - s); lineTo(p.x + s, p.y); lineTo(p.x, p.y + s); lineTo(p.x - s, p.y); close() }, col)
        Trackers.Kind.DULT -> drawCircle(col, s * 0.8f, p, style = Stroke(s * 0.45f))
    }
}

/** The card under the radar for the tracker the user tapped. */
@Composable
fun BlipCard(b: Blip) {
    val mins = (b.s.last - b.s.first) / 60_000L
    HCard(Modifier.fillMaxWidth(), border = levelColor(b.level)) {
        Text(Trackers.label(b.s), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(Trackers.levelName(b.level) + "  ·  ${b.score}/100", color = levelColor(b.level), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(Trackers.zoneName(b.zone), color = C.Text2, fontSize = 13.sp)
        val why = buildList {
            if (b.moving) add(L.t("ظهر معك في أماكن مختلفة", "ظهر معاك في أماكن مختلفة", "Seen with you at different places"))
            else if (b.places >= 2) add(L.t("ظهر في ${b.places} أماكن اليوم", "ظهر في ${b.places} أماكن النهارده", "Seen at ${b.places} places today"))
            if (b.s.sep == true) add(L.t("بعيد عن صاحبه", "بعيد عن صاحبه", "Away from its owner"))
            if (b.s.sep == false) add(L.t("صاحبه قريب منه", "صاحبه قريب منه", "Its owner is near it"))
            add(L.t("ظاهر منذ $mins دقيقة", "ظاهر من $mins دقيقة", "Seen for $mins min"))
        }
        Text(why.joinToString("  ·  "), color = C.Muted, fontSize = 13.sp)
    }
}
