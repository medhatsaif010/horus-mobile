/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.em
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

/**
 * Horus Sec palette. Dark: "Tutankhamun gold" on night lapis. Light: papyrus.
 * Every colour is read through a getter so switching [light] recolours the whole app.
 */
object C {
    var light by mutableStateOf(false)
    private fun c(dark: Long, day: Long) = Color(if (light) day else dark)

    val Bg: Color get() = c(0xFF0D1226, 0xFFF4ECDA)
    val Surface: Color get() = c(0xFF161D3A, 0xFFEAE0C8)
    val Line: Color get() = c(0xFF2A3566, 0xFFCFC2A3)
    val Gold: Color get() = c(0xFFE8B23A, 0xFF87590A)
    val OnGold: Color get() = c(0xFF1A1205, 0xFFFFF8E8)
    val Carnelian: Color get() = c(0xFFC23B2E, 0xFFA8321F)
    val Lapis: Color get() = c(0xFF24357F, 0xFF24357F)
    val Text: Color get() = c(0xFFF4EFE3, 0xFF1F1A12)
    val Text2: Color get() = c(0xFFD9D4C7, 0xFF3A3428)
    val Muted: Color get() = c(0xFFA9A99F, 0xFF625B4C)
    val Dim: Color get() = c(0xFF858795, 0xFF716A5A)
    val Safe: Color get() = c(0xFF3BC1B0, 0xFF0B6F63)
    val Warn: Color get() = c(0xFFF08A24, 0xFFA84A07)
    val Danger: Color get() = c(0xFFE5484D, 0xFFB3261E)
    val Bar: Color get() = c(0xFF090D1C, 0xFFE9DFC5)

    // names used across the older screens
    val Navy: Color get() = Bg
    val NavyDark: Color get() = Bar
    val Card: Color get() = Surface
    val Border: Color get() = Line
    val Green: Color get() = Safe
    val LightGreen: Color get() = Safe
    val Red: Color get() = Danger
    val Orange: Color get() = Warn
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
    size: Dp = 0.dp,
) {
    @Suppress("NAME_SHADOWING")
    val size = if (size > 0.dp) size else (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.62f).coerceIn(220f, 320f).dp
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
        // text inside the seal scales with the seal, not with the phone's font-size setting, and shrinks to fit the inner ring
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fs = density.fontScale
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val k = size.value / 250f
        val maxW = with(density) { (size * 0.52f).toPx() }
        var wordSp = (when { word.length <= 5 -> 46; word.length <= 8 -> 36; else -> 28 }) * k
        while (wordSp > 14f && measurer.measure(word, TextStyle(fontSize = (wordSp / fs).sp, fontWeight = FontWeight.Bold, fontFamily = Readex)).size.width > maxW) wordSp -= 2f
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size * 0.54f)) {
            Text(word, color = color, fontSize = (wordSp / fs).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, lineHeight = 1.2.em)
            if (sub != null) Text(sub, color = C.Text2, fontSize = (12.5f * k / fs).sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 1.35.em,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.width(size * 0.48f))
            if (line != null) Text(line, color = color, fontSize = (12 * k / fs).sp, textAlign = TextAlign.Center, maxLines = 1)
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
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 14.sp)
    }
}

/** A text action such as "امسحه ←" in the colour of what it fixes. */
@Composable
fun ActionText(text: String, color: Color = C.Gold, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clip(Square).clickable(onClick = onClick).padding(horizontal = 2.dp), contentAlignment = Alignment.CenterStart) {
        Text(text + "  " + fwd(), color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
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

// ------------------------------------------------------------------ choices, "why?" and the bottom bar
@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(Square).background(if (selected) C.Gold else C.Bg)
            .border(1.dp, if (selected) C.Gold else C.Line, Square).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) C.OnGold else C.Text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

/** One short line, with the long explanation behind "ليه؟". */
@Composable
fun Why(short: String, long: String, shortColor: Color = C.Text2) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Text(short, color = shortColor, fontSize = 14.sp)
    Text(if (open) L.t("اخفي", "Hide") else L.t("ليه؟", "Why?"), color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.heightIn(min = 40.dp).clip(Square).clickable { open = !open }.padding(vertical = 10.dp))
    if (open) Text(long, color = C.Muted, fontSize = 13.sp)
}

enum class Tab { SECURITY, LINKS, SETTINGS }

@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Bar)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            Tab.entries.forEach { t ->
                val sel = t == current
                val col = if (sel) C.Gold else C.Muted
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.width(28.dp).height(2.dp).background(if (sel) C.Gold else Color.Transparent))
                    Spacer(Modifier.height(6.dp))
                    when (t) {
                        Tab.SECURITY -> HorusMark(Modifier.size(24.dp), color = col, pupil = if (sel) C.Carnelian else col, small = true)
                        Tab.LINKS -> Icon(Icons.Filled.Search, null, tint = col, modifier = Modifier.size(24.dp))
                        Tab.SETTINGS -> Icon(Icons.Filled.Settings, null, tint = col, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        when (t) { Tab.SECURITY -> L.t("الأمان", "Security"); Tab.LINKS -> L.t("اللينكات", "Links"); Tab.SETTINGS -> L.t("الإعدادات", "Settings") },
                        color = col, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
