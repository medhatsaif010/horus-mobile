/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horusict.shield.CheckState
import com.horusict.shield.L
import com.horusict.shield.R
import com.horusict.shield.Sev

/**
 * Horus Shield palette. Dark (default): night navy, Egyptian gold, Nile teal. Light: papyrus.
 * Every colour is read through a getter so switching [light] recolours the whole app.
 */
object C {
    var light by mutableStateOf(false)
    private fun c(dark: Long, day: Long) = Color(if (light) day else dark)

    val Bg: Color get() = c(0xFF0B1020, 0xFFF4ECDA)
    val Surface: Color get() = c(0xFF141E33, 0xFFFFF9EC)
    val Line: Color get() = c(0xFF22304D, 0xFFD9CBA6)
    val Gold: Color get() = c(0xFFD4AF37, 0xFF87590A)
    val GoldLight: Color get() = c(0xFFE7C76A, 0xFF9A6A10)
    val OnGold: Color get() = Color(0xFF1A1406)
    val Teal: Color get() = c(0xFF0096A6, 0xFF0B6F78)
    val TealLight: Color get() = c(0xFF7FE3EC, 0xFF0B6F78)
    val Text: Color get() = c(0xFFEDE6D6, 0xFF1F1A12)
    val Text2: Color get() = c(0xFFD6CFBF, 0xFF3A3428)
    val Muted: Color get() = c(0xFFA9A493, 0xFF625B4C)
    val Dim: Color get() = c(0xFF8F8B7E, 0xFF716A5A)
    val Safe: Color get() = c(0xFF3CC8B6, 0xFF0B6F63)
    val Warn: Color get() = c(0xFFF2994A, 0xFFA84A07)
    val Danger: Color get() = c(0xFFE5534B, 0xFFB3261E)
    val Bar: Color get() = c(0xFF0E1528, 0xFFEBDFC4)

    // names used across the older screens
    val Lapis: Color get() = Teal
    val Carnelian: Color get() = Danger
    val Navy: Color get() = Bg
    val NavyDark: Color get() = Bar
    val Card: Color get() = Surface
    val Border: Color get() = Line
    val Green: Color get() = Safe
    val Red: Color get() = Danger
    val Orange: Color get() = Warn

    val goldBrush: Brush get() = Brush.verticalGradient(listOf(Color(0xFFF3D98E), Color(0xFFD4AF37), Color(0xFFB8901F)))
    val bgBrush: Brush get() = if (light) Brush.verticalGradient(listOf(Color(0xFFF7F0E0), Color(0xFFF4ECDA)))
        else Brush.radialGradient(listOf(Color(0xFF16233A), Color(0xFF0B1020)), radius = 1400f)
}

val Cairo = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_semibold, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
    Font(R.font.cairo_extrabold, FontWeight.ExtraBold),
)
val Montserrat = FontFamily(Font(R.font.montserrat_medium, FontWeight.Medium), Font(R.font.montserrat_semibold, FontWeight.SemiBold))
/** kept for code that still refers to the previous font */
val Readex = Cairo

fun sevColor(s: Sev): Color = when (s) { Sev.CRIT -> C.Danger; Sev.HIGH -> C.Warn; Sev.MED -> C.GoldLight; Sev.LOW -> C.Muted }
fun scoreColor(v: Int): Color = when { v >= 70 -> C.Safe; v >= 50 -> C.Warn; else -> C.Danger }
fun checkColor(s: CheckState): Color = when (s) { CheckState.PASS -> C.Safe; CheckState.WARN -> C.Warn; CheckState.FAIL -> C.Danger }
fun levelColor(l: String): Color = when (l) { "bad" -> C.Danger; "warn" -> C.Warn; else -> C.Muted }
fun fwd(): String = if (L.ar) "←" else "→"
val Ltr = TextStyle(textDirection = TextDirection.Ltr)

// ------------------------------------------------------------------ art
/** The Horus Shield mark: the gold Eye of Horus. */
@Composable
fun HorusEye(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.art_eye), contentDescription = "Horus Shield", modifier = modifier, contentScale = ContentScale.Fit)
}

/** Kept name for old call sites. */
@Composable
fun HorusMark(modifier: Modifier = Modifier, color: Color = C.Gold, pupil: Color = C.Danger, small: Boolean = false) = HorusEye(modifier)

@Composable
fun Decor(res: Int, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Image(painterResource(res), contentDescription = null, modifier = modifier.alpha(alpha), contentScale = ContentScale.Fit)
}

@Composable
fun Ico(res: Int, tint: Color = C.Gold, size: Dp = 24.dp, mirror: Boolean = false) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun Wordmark(size: Float = 16f, color: Color = C.Gold) {
    Text("HORUS SHIELD", color = color, fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = size.sp, letterSpacing = (size * 0.25f).sp, style = Ltr, maxLines = 1)
}

// ------------------------------------------------------------------ building blocks
private val R8 = RoundedCornerShape(8.dp)
private val R12 = RoundedCornerShape(12.dp)

/** The app background: navy with a soft light from the top. */
@Composable
fun Backdrop(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg).background(C.bgBrush), content = content)
}

/** A rounded card with a thin border. [accent] uses a gold border. */
@Composable
fun HCard(modifier: Modifier = Modifier, accent: Boolean = false, border: Color? = null, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(14.dp), content: @Composable ColumnScope.() -> Unit) {
    val b = border ?: if (accent) C.Gold.copy(alpha = 0.4f) else C.Line
    var m = modifier.clip(R12).background(C.Surface).border(1.dp, b, R12)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** Kept for older screens: a card section. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = HCard(modifier.fillMaxWidth(), content = content)

@Composable
fun AccentPanel(accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = HCard(modifier.fillMaxWidth(), border = accent.copy(alpha = 0.6f), content = content)

@Composable
fun Hairline(color: Color = C.Line) { Box(Modifier.fillMaxWidth().height(1.dp).background(color)) }

/** Gold gradient button, the main action on a screen. */
@Composable
fun Primary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.fillMaxWidth().height(50.dp).shadow(if (enabled) 6.dp else 0.dp, R8, ambientColor = C.Gold, spotColor = C.Gold).clip(R8)
            .background(if (enabled) C.goldBrush else Brush.verticalGradient(listOf(C.Line, C.Line)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) C.OnGold else C.Dim, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Gold outline button. */
@Composable
fun Secondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = 46.dp).clip(R8).border(BorderStroke(1.5.dp, if (enabled) C.Gold else C.Line), R8)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) C.GoldLight else C.Dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2) }
}

@Composable
fun SmallLink(text: String, color: Color = C.GoldLight, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ActionText(text: String, color: Color = C.GoldLight, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clip(R8).clickable(onClick = onClick).padding(horizontal = 2.dp), contentAlignment = Alignment.CenterStart) {
        Text(text + "  " + fwd(), color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = C.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) { Box(Modifier.size(size).clip(CircleShape).background(color)) }

/** A row inside a grouped settings card: title, optional subtitle, trailing content, chevron when clickable. */
@Composable
fun ListRow(title: String, sub: String? = null, subColor: Color = C.Muted, icon: Int? = null, onClick: (() -> Unit)? = null, last: Boolean = false, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { Ico(icon, C.Gold, 22.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (sub != null) Text(sub, color = subColor, fontSize = 12.sp)
            }
            trailing?.invoke(this)
            if (onClick != null) { Spacer(Modifier.width(6.dp)); Ico(R.drawable.ic_chev_left, C.Gold, 18.dp) }
        }
        if (!last) Hairline(C.Line.copy(alpha = 0.6f))
    }
}

/** Gold toggle drawn to match the design (on = gold). */
@Composable
fun GoldSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) C.goldBrush else Brush.verticalGradient(listOf(Color(0xFF3A4256), Color(0xFF3A4256))))
            .clickable { onChange(!on) }.padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) Color(0xFFFFF8E6) else Color(0xFFEDE6D6))) }
}

@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(R8).background(if (selected) C.goldBrush else Brush.verticalGradient(listOf(C.Surface, C.Surface)))
            .border(1.dp, if (selected) C.Gold else C.Line, R8).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) C.OnGold else C.Text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

/** One short line, with the long explanation behind "ليه؟". */
@Composable
fun Why(short: String, long: String, shortColor: Color = C.Text2) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Text(short, color = shortColor, fontSize = 14.sp)
    Text(if (open) L.t("إخفاء", "اخفي", "Hide") else L.t("لماذا؟", "ليه؟", "Why?"), color = C.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.heightIn(min = 36.dp).clip(R8).clickable { open = !open }.padding(vertical = 8.dp))
    if (open) Text(long, color = C.Muted, fontSize = 13.sp)
}

/** Screen header: back chevron at the start, centred title, optional trailing slot. */
@Composable
fun TopBar(title: String?, onBack: (() -> Unit)?, extra: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Ico(if (L.ar) R.drawable.ic_chev_right else R.drawable.ic_chev_left, C.Gold, 22.dp)
            }
        }
        if (title == null) Box(Modifier.align(Alignment.Center)) { Wordmark(15f) }
        else Text(title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center), maxLines = 1)
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { extra?.invoke() }
    }
}

enum class Tab { SECURITY, TOOLS, REPORTS, SETTINGS }

@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Bar)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(66.dp)) {
            Tab.entries.forEach { t ->
                val sel = t == current
                val col = if (sel) C.Gold else C.Dim
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Ico(when (t) {
                        Tab.SECURITY -> if (sel) R.drawable.ic_home_filled else R.drawable.ic_home
                        Tab.TOOLS -> R.drawable.ic_grid
                        Tab.REPORTS -> R.drawable.ic_report
                        Tab.SETTINGS -> R.drawable.ic_gear
                    }, col, 24.dp)
                    Text(
                        when (t) { Tab.SECURITY -> L.t("الرئيسية", "الرئيسية", "Home"); Tab.TOOLS -> L.t("الخدمات", "الخدمات", "Services"); Tab.REPORTS -> L.t("التقارير", "التقارير", "Reports"); Tab.SETTINGS -> L.t("الإعدادات", "الإعدادات", "Settings") },
                        color = col, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
