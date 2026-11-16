/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horussec.mobile.CheckState
import com.horussec.mobile.L
import com.horussec.mobile.R
import com.horussec.mobile.Sev

/** Horus Sec palette: "Tutankhamun gold" on night lapis. */
object C {
    val Bg = Color(0xFF0D1226)
    val Surface = Color(0xFF161D3A)
    val Line = Color(0xFF2A3566)
    val Gold = Color(0xFFE8B23A)
    val OnGold = Color(0xFF1A1205)
    val Carnelian = Color(0xFFC23B2E)
    val Lapis = Color(0xFF24357F)
    val Text = Color(0xFFF4EFE3)
    val Text2 = Color(0xFFD9D4C7)
    val Muted = Color(0xFFA9A99F)
    val Dim = Color(0xFF858795)
    val Safe = Color(0xFF3BC1B0)
    val Warn = Color(0xFFF08A24)
    val Danger = Color(0xFFE5484D)
    val Bar = Color(0xFF090D1C)

    // names used across the older screens
    val Navy = Bg
    val NavyDark = Bar
    val Card = Surface
    val Border = Line
    val Green = Safe
    val LightGreen = Safe
    val Red = Danger
    val Orange = Warn
}

val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_semibold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
)

fun sevColor(s: Sev): Color = when (s) { Sev.CRIT -> C.Danger; Sev.HIGH -> C.Warn; Sev.MED -> C.Gold; Sev.LOW -> C.Muted }
fun scoreColor(v: Int): Color = when { v >= 70 -> C.Safe; v >= 50 -> C.Warn; else -> C.Danger }
fun checkColor(s: CheckState): Color = when (s) { CheckState.PASS -> C.Safe; CheckState.WARN -> C.Warn; CheckState.FAIL -> C.Danger }
fun levelColor(l: String): Color = when (l) { "bad" -> C.Danger; "warn" -> C.Warn; else -> C.Muted }

/** Forward arrow for text actions: points left in Arabic, right in English. */
fun fwd(): String = if (L.ar) "←" else "→"

val Ltr = TextStyle(textDirection = TextDirection.Ltr)

// ------------------------------------------------------------------ the mark: shield + wedjat eye (viewBox 100 x 100)
private object MarkPaths {
    val shield = PathParser().parsePathString("M50 6 L87 19 V47 C87 71 71 87 50 95 C29 87 13 71 13 47 V19 Z").toPath()
    val brow = PathParser().parsePathString("M29 36 Q50 26.5 71 34").toPath()
    val eye = PathParser().parsePathString("M26.5 50 Q50 33.5 73.5 50 Q50 62.5 26.5 50 Z").toPath()
    val tear = PathParser().parsePathString("M45 58.5 L40 76").toPath()
    val curl = PathParser().parsePathString("M57 58.5 C60 70 70.5 72 70.5 63.5").toPath()
    // heavier version for small sizes
    val eyeS = PathParser().parsePathString("M25 48 Q50 30 75 48 Q50 64 25 48 Z").toPath()
    val tearS = PathParser().parsePathString("M45 60 L40 78").toPath()
}

@Composable
fun HorusMark(modifier: Modifier = Modifier, color: Color = C.Gold, pupil: Color = C.Carnelian, small: Boolean = false) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height) / 100f
        translate((size.width - 100f * s) / 2f, (size.height - 100f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                val round = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
                if (small) {
                    drawPath(MarkPaths.shield, color, style = round(9f))
                    drawPath(MarkPaths.eyeS, color, style = round(7f))
                    drawCircle(pupil, 8.5f, Offset(50f, 48f))
                    drawPath(MarkPaths.tearS, color, style = round(7f))
                } else {
                    drawPath(MarkPaths.shield, color, style = round(6.5f))
                    drawPath(MarkPaths.brow, color, style = round(5f))
                    drawPath(MarkPaths.eye, color, style = round(5f))
                    drawCircle(pupil, 7f, Offset(50f, 49f))
                    drawPath(MarkPaths.tear, color, style = round(5f))
                    drawPath(MarkPaths.curl, color, style = round(5f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ the seal
/**
 * The Horus Sec seal: a round stamp with Latin text around the ring and one big word in the middle.
 * [progress] (0..1) draws a gold arc on the outer ring while a scan is running.
 */
@Composable
fun Seal(
    word: String,
    color: Color,
    ring: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    line: String? = null,
    progress: Float? = null,
    size: Dp = 224.dp,
) {
    val ctx = LocalContext.current
    val tf = remember { try { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.readex_medium) } catch (e: Exception) { null } }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val k = this.size.minDimension / 300f // design viewBox is 300
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = 118f * k
            drawCircle(color, r + 16f * k, c, style = Stroke(2f * k))
            drawCircle(color, r - 28f * k, c, style = Stroke(1f * k))
            if (progress != null) {
                val rr = r + 16f * k
                drawArc(C.Gold, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(5f * k, cap = StrokeCap.Round))
            }
            drawIntoCanvas { cv ->
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    textSize = 13f * k
                    if (tf != null) typeface = tf
                }
                val rt = r - 14f * k
                val path = android.graphics.Path().apply {
                    addArc(android.graphics.RectF(c.x - rt, c.y - rt, c.x + rt, c.y + rt), 180f, 359.9f)
                }
                val circ = (2 * Math.PI * rt).toFloat() * 0.985f
                val base = p.measureText(ring)
                if (ring.isNotEmpty() && base < circ) p.letterSpacing = ((circ - base) / ring.length) / p.textSize
                cv.nativeCanvas.drawTextOnPath(ring, path, 0f, p.textSize * 0.35f, p)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = size * 0.2f)) {
            val big = when { word.length <= 5 -> 46; word.length <= 8 -> 36; else -> 28 }
            Text(word, color = color, fontSize = (big * size.value / 250f).sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, color = C.Text2, fontSize = (13 * size.value / 250f).sp, textAlign = TextAlign.Center, maxLines = 2)
            if (line != null) Text(line, color = color, fontSize = (12 * size.value / 250f).sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ flat building blocks
/** A plain section: no card, a hairline under it. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    Hairline()
}

/** Same as [Panel], with a short coloured rule on the leading edge for emphasis. */
@Composable
fun AccentPanel(accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(Modifier.width(3.dp).height(28.dp).background(accent))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
    Hairline()
}

@Composable
fun Hairline(color: Color = C.Line) { Box(Modifier.fillMaxWidth().height(1.dp).background(color)) }

private val Square = RoundedCornerShape(6.dp)

@Composable
fun Primary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp), shape = Square,
        colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.OnGold, disabledContainerColor = C.Surface, disabledContentColor = C.Dim),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
fun Secondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(46.dp), shape = Square,
        border = BorderStroke(1.dp, if (enabled) C.Gold else C.Line), contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Gold, disabledContentColor = C.Dim),
    ) { Text(text, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2) }
}

@Composable
fun SmallLink(text: String, color: Color = C.Gold, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 14.sp)
    }
}

/** A text action such as "امسحه ←" in the colour of what it fixes. */
@Composable
fun ActionText(text: String, color: Color = C.Gold, onClick: () -> Unit) {
    Text(text + "  " + fwd(), color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(Square).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp))
}

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}

@Composable
fun TopBar(title: String?, onBack: (() -> Unit)?, extra: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().background(C.Bar).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) SmallLink(L.t("‹ رجوع", "‹ Back"), C.Text, onBack)
            else {
                Spacer(Modifier.width(6.dp))
                HorusMark(Modifier.size(30.dp), small = true)
                Spacer(Modifier.width(10.dp))
            }
            Text(title ?: "Horus Sec", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            extra?.invoke()
            SmallLink(if (L.ar) "English" else "عربي") { com.horussec.mobile.App.setLang(!L.ar) }
        }
        // the thin gold / lapis band under the bar
        Row(Modifier.fillMaxWidth().height(2.dp)) {
            repeat(24) { i -> Box(Modifier.weight(1f).fillMaxSize().background(if (i % 2 == 0) C.Gold else C.Lapis)) }
        }
    }
}
