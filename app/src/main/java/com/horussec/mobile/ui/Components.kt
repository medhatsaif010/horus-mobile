package com.horussec.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horussec.mobile.CheckState
import com.horussec.mobile.L
import com.horussec.mobile.Sev

object C {
    val Navy = Color(0xFF13233A)
    val NavyDark = Color(0xFF0E1A2C)
    val Card = Color(0xFF172A45)
    val Border = Color(0xFF3B5273)
    val Gold = Color(0xFFF0B429)
    val Text2 = Color(0xFFB9C6D3)
    val Muted = Color(0xFF9FB0C2)
    val Dim = Color(0xFF6F829A)
    val Green = Color(0xFF4CC38A)
    val LightGreen = Color(0xFF8FD3B0)
    val Red = Color(0xFFFF8A8C)
    val Orange = Color(0xFFF59E4B)
}

fun sevColor(s: Sev): Color = when (s) { Sev.CRIT -> C.Red; Sev.HIGH -> C.Orange; Sev.MED -> C.Gold; Sev.LOW -> C.Muted }
fun scoreColor(v: Int): Color = when { v >= 85 -> C.Green; v >= 70 -> C.LightGreen; v >= 50 -> C.Gold; else -> C.Red }
fun checkColor(s: CheckState): Color = when (s) { CheckState.PASS -> C.Green; CheckState.WARN -> C.Gold; CheckState.FAIL -> C.Red }
fun levelColor(l: String): Color = when (l) { "bad" -> C.Red; "warn" -> C.Gold; else -> C.Muted }

val Ltr = TextStyle(textDirection = TextDirection.Ltr)

/** The Horus Sec eye, same geometry as the Windows logo (viewBox 220 x 150). */
@Composable
fun HorusEye(modifier: Modifier = Modifier, color: Color = C.Gold) {
    Canvas(modifier) {
        val s = minOf(size.width / 220f, size.height / 150f)
        val ox = (size.width - 220f * s) / 2f
        val oy = (size.height - 150f * s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val eye = Path().apply {
            moveTo(p(18f, 74f).x, p(18f, 74f).y)
            cubicTo(p(62f, 30f).x, p(62f, 30f).y, p(150f, 26f).x, p(150f, 26f).y, p(202f, 66f).x, p(202f, 66f).y)
            cubicTo(p(150f, 104f).x, p(150f, 104f).y, p(64f, 110f).x, p(64f, 110f).y, p(18f, 74f).x, p(18f, 74f).y)
            close()
        }
        drawPath(eye, color, style = Stroke(width = 16f * s, join = StrokeJoin.Round))
        drawCircle(color, radius = 24f * s, center = p(110f, 70f))
        drawLine(color, p(96f, 100f), p(82f, 142f), strokeWidth = 16f * s, cap = StrokeCap.Round)
    }
}

@Composable
fun ScoreRing(score: Int, size: Dp = 132.dp) {
    val col = scoreColor(score)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 12.dp.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(C.NavyDark, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            drawArc(col, -90f, 360f * score / 100f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), color = col, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("/100", color = C.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content,
    )
}

@Composable
fun AccentPanel(accent: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.55f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content,
    )
}

@Composable
fun Primary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Navy, disabledContainerColor = Color(0xFF2A4060), disabledContentColor = C.Dim),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Secondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(44.dp), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, C.Border), contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDCE4EC)),
    ) { Text(text, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

@Composable
fun SmallLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = C.Gold, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = C.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}

@Composable
fun TopBar(title: String?, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(C.NavyDark).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) SmallLink(L.t("‹ رجوع", "‹ Back"), onBack)
        else {
            Spacer(Modifier.width(8.dp))
            HorusEye(Modifier.size(width = 30.dp, height = 21.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title ?: "Horus Sec", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        SmallLink(if (L.ar) "English" else "عربي") { com.horussec.mobile.App.setLang(!L.ar) }
    }
}
