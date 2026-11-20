/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horusict.shield.Actions
import com.horusict.shield.App
import com.horusict.shield.BuildConfig
import com.horusict.shield.Check
import com.horusict.shield.CheckState
import com.horusict.shield.Finding
import com.horusict.shield.Fix
import com.horusict.shield.History
import com.horusict.shield.Intel
import com.horusict.shield.L
import com.horusict.shield.R
import com.horusict.shield.ScanResult
import com.horusict.shield.Screen
import com.horusict.shield.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hm = SimpleDateFormat("HH:mm", Locale.US)

// ------------------------------------------------------------------ shared wording
fun verdictWord(v: String?): String = when (v) {
    "compromised" -> L.t("مخترق", "مخترق", "Hacked"); "suspicious" -> L.t("راجِع", "راجع", "Review"); else -> L.t("سليم", "سليم", "Clean")
}

fun verdictTitle(v: String?): String = when (v) {
    "compromised" -> L.t("توجد علامات اختراق", "فيه علامات اختراق", "Signs of compromise"); "suspicious" -> L.t("يحتاج إلى مراجعة", "محتاج مراجعة", "Needs a review"); else -> L.t("جهازك محمي", "جهازك محمي", "Your phone is protected")
}

fun verdictSub(v: String?, iocs: Int = 0): String = when (v) {
    "compromised" -> if (iocs > 0) L.t("علامات اختراق: $iocs", "علامات اختراق: $iocs", "$iocs signs of compromise") else L.t("توجد علامات اختراق", "فيه علامات اختراق", "Signs of compromise")
    "suspicious" -> L.t("توجد أمور تحتاج إلى التحقق منها", "فيه حاجات محتاجة تتأكد منها", "Some things need checking")
    else -> L.t("لا توجد علامات اختراق", "مفيش علامات اختراق", "No signs of compromise")
}

fun scoreLine(score: Int): String = L.t("الحماية $score%", "الحماية $score%", "Protection $score%")

fun ringText(deep: Boolean, at: Long, apps: Int): String {
    val d = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(if (at > 0) at else System.currentTimeMillis())).uppercase(Locale.US)
    return "HORUS SHIELD · " + (if (deep) "DEEP SCAN" else "QUICK SCAN") + " · $d · " + (if (apps > 0) "$apps APPS · " else "")
}

fun dayLabel(at: Long): String {
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = at
    val that = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = System.currentTimeMillis(); cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yest = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    return when (that) {
        today -> L.t("اليوم ", "النهارده ", "Today ") + hm.format(Date(at))
        yest -> L.t("أمس ", "امبارح ", "Yesterday ") + hm.format(Date(at))
        else -> SimpleDateFormat("dd/MM  HH:mm", Locale.US).format(Date(at))
    }
}

fun agoLabel(at: Long): String {
    val m = ((System.currentTimeMillis() - at) / 60000).coerceAtLeast(0)
    return when {
        m < 1 -> L.t("آخر فحص الآن", "آخر فحص دلوقتي", "Last scan just now")
        m < 60 -> L.t("آخر فحص منذ $m دقيقة", "آخر فحص منذ $m دقيقة", "Last scan $m min ago")
        m < 60 * 24 -> L.t("آخر فحص منذ ${m / 60} ساعة", "آخر فحص منذ ${m / 60} ساعة", "Last scan ${m / 60} h ago")
        else -> L.t("آخر فحص منذ ${m / 1440} يوم", "آخر فحص منذ ${m / 1440} يوم", "Last scan ${m / 1440} days ago")
    }
}

fun potential(r: ScanResult): Pair<Int, Int>? {
    if (r.verdict == "compromised") return null
    val gains = (r.findings.filter { it.fixes.isNotEmpty() }.map { it.sev.weight } +
        r.checks.filter { it.fix != null }.map { when (it.state) { CheckState.FAIL -> 3; CheckState.WARN -> 1; else -> 0 } })
        .filter { it > 0 }.sortedDescending()
    if (gains.isEmpty()) return null
    val n = minOf(3, gains.size)
    val to = minOf(100, r.score + gains.take(n).sum())
    return if (to > r.score) n to to else null
}

fun ageText(days: Int): String = when {
    days < 1 -> L.t("أُنشئ اليوم", "اتعمل النهارده", "created today")
    days < 31 -> L.t("أُنشئ منذ $days يوم", "اتعمل من $days يوم", "created $days days ago")
    days < 365 -> L.t("عمره ${days / 30} شهر", "عمره ${days / 30} شهر", "${days / 30} months old")
    else -> L.t("عمره ${days / 365} سنة", "عمره ${days / 365} سنة", "${days / 365} years old")
}

fun autoLabel(h: Int): String = when (h) {
    0 -> L.t("معطّل", "مقفول", "Off"); 12 -> L.t("كل 12 ساعة", "كل 12 ساعة", "Every 12 h"); 24 -> L.t("كل يوم", "كل يوم", "Daily"); else -> L.t("كل $h ساعات", "كل $h ساعات", "Every $h h")
}

fun gradeOf(v: Int): String = when { v >= 85 -> L.t("ممتاز", "ممتاز", "Excellent"); v >= 70 -> L.t("جيد", "كويس", "Good"); v >= 50 -> L.t("يحتاج إلى تحسين", "محتاج شغل", "Needs work"); else -> L.t("ضعيف", "ضعيف", "Weak") }

fun answerOf(v: String): String = when (v) {
    "compromised" -> L.t("غالبًا نعم: توجد علامات اختراق", "غالبًا أيوه: فيه علامات اختراق", "Probably yes: signs of compromise")
    "suspicious" -> L.t("غير متأكدين: توجد أمور تحتاج إلى التحقق منها", "مش متأكدين: فيه حاجات محتاجة تتأكد منها", "Not sure: some things need checking")
    else -> L.t("لا، لا توجد علامات اختراق", "لأ، مفيش علامات اختراق", "No signs of compromise")
}

fun verdictColor(v: String): Color = when (v) { "compromised" -> C.Danger; "suspicious" -> C.Warn; else -> C.Safe }

internal fun openPermissionManager(ctx: android.content.Context) {
    val tries = listOf(
        android.content.Intent("android.intent.action.MANAGE_PERMISSIONS"),
        android.content.Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS),
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    for (i in tries) { try { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { } }
}

/** A tab screen: content plus the bottom bar. */
@Composable
fun TabScreen(tab: Tab, content: @Composable () -> Unit) {
    Backdrop {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        BottomBar(tab) { t -> App.screen = when (t) { Tab.SECURITY -> Screen.HOME; Tab.TOOLS -> Screen.TOOLS; Tab.REPORTS -> Screen.REPORTS; Tab.SETTINGS -> Screen.SETTINGS } }
    }
}

// ------------------------------------------------------------------ shield drawing
private object ShieldPaths {
    val shield = PathParser().parsePathString("M50 4 L92 18 V52 C92 80 74 100 50 108 C26 100 8 80 8 52 V18 Z").toPath()
    val hole = PathParser().parsePathString("M45 54 L42 76 H58 L55 54 Z").toPath()
}

/** The gold shield from the design, optionally with a keyhole. Drawn in a 100 x 112 box. */
@Composable
fun GoldShield(modifier: Modifier, keyhole: Boolean = false) {
    Canvas(modifier) {
        val s = minOf(size.width / 100f, size.height / 112f)
        translate((size.width - 100f * s) / 2f, (size.height - 112f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(ShieldPaths.shield, Brush.radialGradient(listOf(Color(0xFF0E3A48), Color(0xFF0D1529)), center = Offset(50f, 45f), radius = 60f))
                drawPath(ShieldPaths.shield, Brush.verticalGradient(listOf(Color(0xFFF3D98E), Color(0xFFA9831F)), 0f, 112f), style = Stroke(4.5f, join = StrokeJoin.Round))
                if (keyhole) {
                    val g = Brush.verticalGradient(listOf(Color(0xFFF3D98E), Color(0xFFA9831F)), 30f, 80f)
                    drawCircle(g, 11f, Offset(50f, 46f)); drawPath(ShieldPaths.hole, g)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ SPLASH
@Composable
fun SplashScreen() {
    Backdrop {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height * 0.4f)
                for (r in listOf(150, 200, 250)) drawCircle(Color(0x14D4AF37), r.dp.toPx(), c, style = Stroke(1f))
            }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(0.9f))
                HorusEye(Modifier.width(210.dp))
                Spacer(Modifier.height(20.dp))
                Text("HORUS", color = C.Gold, fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = 9.sp, style = Ltr)
                Text("SHIELD", color = C.Gold, fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 13.sp, style = Ltr)
                Spacer(Modifier.weight(1f))
                Decor(R.drawable.art_wings, Modifier.width(300.dp), alpha = 0.85f)
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ HOME
@Composable
fun HomeScreen() = TabScreen(Tab.SECURITY) {
    val ctx = LocalContext.current
    val last = App.lastScore
    val v = App.lastVerdict
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp)) {
            Box(Modifier.align(Alignment.Center)) { Wordmark(15f) }
            Box(Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape).clickable { App.screen = Screen.SETTINGS }, contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_menu, C.Gold, 24.dp)
                if (App.update != null || !App.notifyAllowed) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 9.dp).size(7.dp).clip(CircleShape).background(C.GoldLight))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val frac by animateFloatAsState((last ?: 0) / 100f, tween(900, easing = FastOutSlowInEasing), label = "ring")
            val tint = if (last == null) C.Teal else if (v == "compromised") C.Danger else if (v == "suspicious") C.Warn else C.Teal
            val dotC = C.TealLight; val faint = C.Line
            // status: a compact ring so the services fit on the first screen even on small phones
            Box(Modifier.size(width = 230.dp, height = 196.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val big = 98.dp.toPx(); val r = 78.dp.toPx()
                    drawArc(faint, 125f, 290f, false, Offset(c.x - big, c.y - big), Size(big * 2, big * 2), style = Stroke(1.2.dp.toPx()))
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent), c, r * 1.05f), r * 1.05f, c)
                    drawArc(tint.copy(alpha = 0.18f), 125f, 290f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    val sweep = 290f * frac
                    drawArc(tint, 125f, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    if (frac > 0f) {
                        val a = Math.toRadians((125f + sweep).toDouble())
                        drawCircle(dotC, 3.dp.toPx(), Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat()))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        GoldShield(Modifier.size(width = 56.dp, height = 63.dp))
                        HorusEye(Modifier.width(32.dp).padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if (last == null) L.t("لم يُجرَ أي فحص بعد", "لسه مفيش فحص", "No scan yet") else verdictTitle(v), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.widthIn(max = 140.dp))
                    Text(if (last == null) L.t("أجرِ أول فحص", "اعمل أول فحص", "Run a first scan") else L.t("الحماية $last%", "الحماية $last%", "Protection $last%"), color = C.Muted, fontSize = 13.sp)
                }
            }
            if (last != null) Text(agoLabel(App.lastScanAt), color = C.Dim, fontSize = 12.sp)
            UpdateBanner()
            Primary(L.t("افحص موبايلي", "افحص موبايلي", "Scan my phone"), { App.startScan(deep = true) }, Modifier.width(260.dp))
            if (App.result != null) Text(L.t("اعرض آخر تقرير", "اعرض آخر تقرير", "View the last report"), color = C.GoldLight, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { App.screen = Screen.RESULT }.padding(horizontal = 16.dp, vertical = 8.dp))
            // what do you want to do? the services, by what the person is worried about
            Text(L.t("ماذا تريد أن تفعل؟", "عايز تعمل إيه؟", "What do you want to do?"), color = C.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(R.drawable.ic_link, L.t("افحص رابطًا", "افحص لينك", "Check a link"), L.t("قبل أن تفتحه", "قبل ما تفتحه", "Before you open it"), Modifier.weight(1f)) { App.linkResult = null; App.go(Screen.LINK) }
                HomeTile(R.drawable.ic_bank, L.t("تطبيقات البنوك", "تطبيقات البنوك", "Bank apps"), L.t("رسمية أم مزيفة؟", "رسمية ولا مزيفة؟", "Official or fake?"), Modifier.weight(1f)) { App.bankApps = null; App.go(Screen.BANKAPPS) }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(R.drawable.ic_phone, L.t("واتساب والمكالمات", "واتساب والمكالمات", "WhatsApp and calls"), L.t("امنع سرقة حسابك", "امنع سرقة حسابك", "Stop account theft"), Modifier.weight(1f)) { App.go(Screen.WHATSAPP) }
                HomeTile(R.drawable.ic_flag, L.t("أبلغ عن احتيال", "بلّغ عن نصب", "Report a scam"), L.t("رابط أو رقم", "لينك أو رقم", "A link or number"), Modifier.weight(1f)) { App.reportMsg = ""; App.go(Screen.SCAMREPORT) }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(R.drawable.ic_wrench, L.t("قبل الصيانة وبعدها", "قبل التصليح وبعده", "Repair check"), if (App.repairSavedAt > 0) L.t("قارن الآن", "قارن دلوقتي", "Compare now") else L.t("احفظ حالة الهاتف", "احفظ حالة الموبايل", "Save the phone's state"), Modifier.weight(1f)) { App.go(Screen.REPAIR) }
                HomeTile(R.drawable.ic_radar, L.t("أجهزة التتبع", "أجهزة التتبع", "Trackers"), L.t("مثل AirTag", "زي AirTag", "AirTag and others"), Modifier.weight(1f)) { App.go(Screen.TRACKERS) }
            }
            HCard(Modifier.fillMaxWidth(), border = C.Danger.copy(alpha = 0.55f), onClick = { App.go(Screen.HELP) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ic_lifebuoy, C.Danger, 26.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L.t("أحدهم يراقبني", "حد بيراقبني", "Someone is watching me"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(L.t("خطة آمنة وأرقام المساعدة", "خطة آمنة وأرقام المساعدة", "A safe plan and helplines"), color = C.Muted, fontSize = 12.sp)
                    }
                    Ico(R.drawable.ic_chev_left, C.Gold, 18.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HomeTile(icon: Int, label: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    HCard(modifier.fillMaxHeight().heightIn(min = 104.dp), onClick = onClick, padding = PaddingValues(vertical = 12.dp, horizontal = 10.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Ico(icon, C.Gold, 28.dp)
            Text(label, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(hint, color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

// ------------------------------------------------------------------ SCANNING
@Composable
fun ScanningScreen() {
    val steps = listOf(
        L.t("قراءة قائمة التطبيقات", "قراءة قايمة التطبيقات", "Reading the app list"),
        L.t("فحص التطبيقات والتوقيعات", "فحص التطبيقات والتوقيعات", "Checking apps and signatures"),
        L.t("فحص الصلاحيات", "فحص الصلاحيات", "Checking permissions"),
        L.t("فحص الشبكة والشهادات", "فحص الشبكة والشهادات", "Checking network and certificates"),
        L.t("حساب النتيجة", "حساب النتيجة", "Working out the result"),
    ) + (if (App.scanDeep) listOf(
        L.t("بصمات التطبيقات", "بصمات التطبيقات", "App fingerprints"),
        L.t("الـ DNS والتحكم في الهاتف", "الـ DNS والتحكم في الموبايل", "DNS and device control"),
        L.t("البيانات المُرسَلة", "البيانات اللي اتبعتت", "Data sent out"),
    ) else emptyList())
    val step = App.scanStep.coerceIn(0, steps.size)
    val prog by animateFloatAsState((step + 0.5f) / steps.size, tween(600), label = "p")
    val t = rememberInfiniteTransition(label = "radar")
    val ang by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val teal = C.Teal
    Backdrop {
        TopBar(L.t("فحص الجهاز", "فحص الجهاز", "Scanning"), null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center; val r0 = size.minDimension / 2f - 4.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(teal.copy(alpha = 0.3f), Color.Transparent), c, r0), r0, c)
                    drawCircle(teal.copy(alpha = 0.3f), r0, c, style = Stroke(1.dp.toPx()))
                    drawCircle(teal.copy(alpha = 0.22f), r0 * 0.8f, c, style = Stroke(1.dp.toPx()))
                    drawCircle(Color(0xFF0D1529), r0 * 0.6f, c)
                    drawCircle(teal.copy(alpha = 0.5f), r0 * 0.6f, c, style = Stroke(1.5.dp.toPx()))
                    rotate(ang, c) {
                        drawArc(Brush.sweepGradient(listOf(Color.Transparent, teal.copy(alpha = 0.45f)), c), -90f, 60f, true, Offset(c.x - r0, c.y - r0), Size(r0 * 2, r0 * 2))
                    }
                    drawArc(teal, -90f, 360f * prog, false, Offset(c.x - r0, c.y - r0), Size(r0 * 2, r0 * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                HorusEye(Modifier.width(100.dp))
            }
            Text(L.t("جارٍ فحص الجهاز…", "جاري فحص الجهاز…", "Scanning the device…"), color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(steps.getOrNull(step) ?: steps.last(), color = C.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            steps.forEachIndexed { i, s ->
                val done = i < step; val now = i == step
                HCard(Modifier.fillMaxWidth(), border = if (now) C.Teal else null, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s, color = when { now -> C.TealLight; done -> C.Text; else -> C.Dim }, fontSize = 14.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        when {
                            done -> Ico(R.drawable.ic_check_circle, C.Gold, 20.dp)
                            now -> CircularProgressIndicator(Modifier.size(18.dp), color = C.Teal, strokeWidth = 2.dp)
                            else -> Ico(R.drawable.ic_circle, C.Line, 20.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------ RESULT (report)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen() {
    val r = App.result ?: run { App.screen = Screen.HOME; return }
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Finding?>(null) }
    var rootAsk by remember { mutableStateOf<Pair<Finding, Fix>?>(null) }
    Backdrop {
        TopBar(L.t("تقرير الحماية", "تقرير الحماية", "Protection report"), { App.screen = Screen.HOME }) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { Actions.shareText(ctx, Actions.summary(r, App.device)) }, contentAlignment = Alignment.Center) { Ico(R.drawable.ic_share, C.Gold, 22.dp) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val stamp0 = remember { Animatable(if (App.justScanned) 0f else 1f) }
                val haptic = LocalHapticFeedback.current
                LaunchedEffect(r.atMillis) {
                    if (App.justScanned) {
                        App.justScanned = false
                        stamp0.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                val k = stamp0.value
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(width = 300.dp, height = 138.dp).graphicsLayer { val sc = 1.3f - 0.3f * k; scaleX = sc; scaleY = sc; alpha = k }) {
                        Decor(R.drawable.art_wings, Modifier.width(300.dp))
                        GoldShield(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(width = 76.dp, height = 86.dp), keyhole = true)
                    }
                    val tc = when (r.verdict) { "compromised" -> C.Danger; "suspicious" -> C.Warn; else -> C.GoldLight }
                    Text(when (r.verdict) { "compromised" -> L.t("توجد علامات اختراق", "فيه علامات اختراق", "Signs of compromise"); "suspicious" -> L.t("يحتاج إلى مراجعة", "محتاج مراجعة", "Needs a review"); else -> L.t("تمت حمايتك", "تم حمايتك", "You are protected") },
                        color = tc, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(verdictSub(r.verdict, r.findings.count { it.ioc }) + "  ·  " + (if (r.deep) L.t("فحص عميق", "فحص عميق", "Deep scan") else L.t("فحص سريع", "فحص سريع", "Quick scan")) + "  ·  " + hm.format(Date(r.atMillis)),
                        color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    val before = App.scoreBefore
                    if (before != null && before != r.score) Text(
                        if (r.score > before) L.t("ارتفعت الحماية من $before% إلى ${r.score}%", "الحماية زادت من $before% لـ ${r.score}%", "Protection went up from $before% to ${r.score}%")
                        else L.t("انخفضت الحماية من $before% إلى ${r.score}%", "الحماية نزلت من $before% لـ ${r.score}%", "Protection went down from $before% to ${r.score}%"),
                        color = if (r.score > before) C.GoldLight else C.Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Decor(R.drawable.art_frieze2, Modifier.fillMaxWidth().padding(vertical = 8.dp), alpha = 0.45f)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("${r.score}%", L.t("مستوى الحماية", "مستوى الحماية", "Protection"), if (r.score < 70) scoreColor(r.score) else C.GoldLight, Modifier.weight(1f))
                        Stat("${r.appCount}", L.t("تطبيق فُحص", "تطبيق اتفحص", "Apps scanned"), C.GoldLight, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("${r.findings.size}", L.t("خطوات مقترحة", "حاجات تعملها", "Things to do"), C.GoldLight, Modifier.weight(1f))
                        Stat("${r.iocCount}", L.t("علامات اختراق", "علامات اختراق", "Compromise signs"), if (r.iocCount > 0) C.Danger else C.TealLight, Modifier.weight(1f))
                    }
                    potential(r)?.let { p ->
                        Text(L.t("أول ${p.first} خطوات في الأسفل توصلك إلى ${p.second}%", "أول ${p.first} حاجات تحت توصلك لـ ${p.second}%", "The first ${p.first} things below take you to ${p.second}%"), color = C.GoldLight, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    if (r.deep) Secondary(L.t("افحص مرة أخرى", "افحص تاني", "Scan again"), { App.startScan(deep = true) }, Modifier.fillMaxWidth())
                    else Primary(L.t("أجرِ فحصًا عميقًا", "اعمل فحص عميق", "Run a deep scan"), { App.startScan(deep = true) })
                }
            }
            if (r.changes.isNotEmpty()) item {
                SectionTitle(L.t("ما تغيّر منذ آخر فحص", "اتغيّر من آخر فحص", "Changed since last scan"))
                HCard(Modifier.fillMaxWidth()) { r.changes.forEach { Text(it, color = C.Text2, fontSize = 14.sp) } }
            }
            item { SectionTitle(if (r.findings.isEmpty()) L.t("لا يوجد ما يستدعي التصرف", "مفيش حاجة محتاجة تتصرف فيها", "Nothing needs action") else L.t("ابدأ بهذا الترتيب", "ابدأ بالترتيب ده", "Start in this order")) }
            itemsIndexed(r.findings, key = { _, f -> f.key }) { i, f -> FindingRow(i + 1, f) { fx ->
                when {
                    fx == Fix.UNINSTALL && f.stalker -> confirm = f
                    fx == Fix.ROOT_STOP || fx == Fix.ROOT_DISABLE || fx == Fix.ROOT_BLOCKNET -> rootAsk = f to fx
                    else -> Actions.open(ctx, fx, f.pkg)
                }
            } }
            item {
                SectionTitle(L.t("إعدادات الأمان", "إعدادات الأمان", "Security settings"))
                var showOk by remember { mutableStateOf(false) }
                val bad = r.checks.filter { it.state != CheckState.PASS }
                val ok = r.checks.filter { it.state == CheckState.PASS }
                HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    bad.forEach { CheckRow(it) }
                    if (ok.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showOk = !showOk }, verticalAlignment = Alignment.CenterVertically) {
                            Ico(R.drawable.ic_check_circle, C.Safe, 20.dp); Spacer(Modifier.width(10.dp))
                            Text(L.t("${ok.size} إعدادات سليمة", "${ok.size} إعدادات تمام", "${ok.size} settings are fine"), color = C.Safe, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (showOk) L.t("إخفاء", "اخفي", "Hide") else L.t("عرضها", "اعرضها", "Show"), color = C.GoldLight, fontSize = 13.sp)
                        }
                        if (showOk) ok.forEach { CheckRow(it) }
                    }
                }
            }
            item {
                SectionTitle(L.t("من يستطيع سماعك ورؤيتك", "مين يقدر يسمعك ويشوفك", "Who can hear and see you"))
                HCard(Modifier.fillMaxWidth()) {
                    Text(L.t("التطبيقات التي ثبّتها والتي تملك هذه الصلاحيات الآن:", "التطبيقات اللي إنت مسطّبها ومعاها الصلاحيات دي دلوقتي:", "Apps you installed that hold these permissions now:"), color = C.Muted, fontSize = 13.sp)
                    r.exposure.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(e.label, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (e.apps.isEmpty()) L.t("لا يوجد أي تطبيق", "ولا تطبيق", "None") else e.apps.joinToString(if (L.ar) "، " else ", "), color = C.Muted, fontSize = 13.sp)
                            }
                            Text(e.apps.size.toString(), color = if (e.apps.isEmpty()) C.Safe else C.GoldLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    ActionText(L.t("افتح مدير الصلاحيات", "افتح مدير الصلاحيات", "Open permission manager")) { openPermissionManager(ctx) }
                }
            }
            if (r.verified.isNotEmpty()) item {
                SectionTitle(L.t("تحققنا من", "اتأكدنا من", "Verified"))
                HCard(Modifier.fillMaxWidth()) { r.verified.forEach { Text(it, color = C.Text2, fontSize = 14.sp) } }
            }
            item {
                Text(L.t("يرى التطبيق ما يسمح أندرويد لأي تطبيق برؤيته، دون Root. وقد لا تظهر هنا برامج التجسس المتقدمة جدًا. إن كان شكّك قويًا، فافحص الهاتف أيضًا ببرنامج Horus Shield على الكمبيوتر عبر الكابل.", "التطبيق بيشوف اللي أندرويد بيسمح لأي تطبيق يشوفه، من غير Root. برامج التجسس المتقدمة جدًا ممكن متبانش هنا. لو شاكك جامد، افحص الموبايل كمان من برنامج Horus Shield على الكمبيوتر بالكابل.",
                    "The app sees what Android lets any app see, without root. Very advanced spyware may not show here. If you strongly suspect something, also scan the phone with Horus Shield on a computer over USB."),
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }
    rootAsk?.let { (f, fx) ->
        val what = when (fx) {
            Fix.ROOT_STOP -> L.t("سيُغلق التطبيق الآن. وإن كان يعمل تلقائيًا فسيعود بعد قليل.", "هيقفل التطبيق دلوقتي. لو بيشتغل لوحده هيرجع تاني بعد شوية.", "This closes the app now. If it starts itself, it will come back later.")
            Fix.ROOT_DISABLE -> L.t("سيُعطَّل التطبيق بالكامل حتى تعيده. لن تُحذف بياناته، ويمكنك إعادته من الإعدادات.", "هيعطّل التطبيق بالكامل لحد ما ترجّعه. بياناته مش هتتمسح، وتقدر ترجّعه من الإعدادات.", "This disables the app until you restore it. Its data is kept, and you can restore it from Settings.")
            else -> L.t("سيُمنع التطبيق من الوصول إلى الإنترنت حتى تعيده أو تعيد تشغيل الهاتف.", "هيمنع التطبيق يوصل للنت لحد ما ترجّعه أو تعمل Restart للموبايل.", "This blocks the app from the internet until you restore it or restart the phone.")
        }
        AlertDialog(
            onDismissRequest = { rootAsk = null }, shape = RoundedCornerShape(12.dp),
            containerColor = C.Surface, titleContentColor = C.Text, textContentColor = C.Text2,
            title = { Text(Actions.label(fx)) },
            text = { Text(what + "\n\n" + (f.pkg ?: "") + if (f.stalker) L.t("\n\nقد يلاحظ من ثبّت برنامج التجسس ذلك.", "\n\nاللي سطّب برنامج التجسس ممكن يلاحظ.", "\n\nWhoever installed the stalkerware may notice.") else "") },
            confirmButton = { TextButton(onClick = { rootAsk = null; f.pkg?.let { App.rootAction(fx, it) } }) { Text(L.t("نفّذ", "نفّذ", "Do it"), color = C.Danger) } },
            dismissButton = { TextButton(onClick = { rootAsk = null }) { Text(L.t("إلغاء", "إلغاء", "Cancel"), color = C.GoldLight) } },
        )
    }
    if (App.rootMsg.isNotEmpty() && App.screen == Screen.RESULT) {
        AlertDialog(
            onDismissRequest = { App.rootMsg = "" }, containerColor = C.Surface, textContentColor = C.Text, shape = RoundedCornerShape(12.dp),
            text = { Text(App.rootMsg) }, confirmButton = { TextButton(onClick = { App.rootMsg = "" }) { Text("OK", color = C.GoldLight) } },
        )
    }
    confirm?.let { f ->
        AlertDialog(
            onDismissRequest = { confirm = null }, shape = RoundedCornerShape(12.dp),
            containerColor = C.Surface, titleContentColor = C.Text, textContentColor = C.Text2,
            title = { Text(L.t("قبل حذفه", "قبل ما تمسحه", "Before you remove it")) },
            text = { Text(L.t("سيعلم من ثبّت برنامج التجسس هذا أنه حُذف. إن شعرت بأن هناك خطرًا عليك منه، فاحفظ الملخص أولًا واطلب المساعدة من شخص تثق به، ثم احذفه.", "اللي سطّب برنامج التجسس ده هيعرف إنه اتمسح. لو حاسس إن فيه خطر عليك منه، احفظ الملخص الأول واطلب مساعدة من حد تثق فيه، وبعدين امسحه.",
                "Whoever installed this stalkerware will notice it is gone. If you feel at risk from them, save the summary first and get help from someone you trust, then remove it.")) },
            confirmButton = { TextButton(onClick = { confirm = null; Actions.open(ctx, Fix.UNINSTALL, f.pkg) }) { Text(L.t("فهمت، احذفه", "فاهم، امسحه", "I understand, remove it"), color = C.Danger) } },
            dismissButton = { TextButton(onClick = { confirm = null; Actions.shareText(ctx, Actions.summary(r, App.device)) }) { Text(L.t("احفظ الملخص أولًا", "احفظ الملخص الأول", "Save the summary first"), color = C.GoldLight) } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    HCard(modifier, accent = true, padding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, style = Ltr)
            Text(label, color = C.Muted, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindingRow(n: Int, f: Finding, onFix: (Fix) -> Unit) {
    var open by remember(f.key) { mutableStateOf(false) }
    val col = sevColor(f.sev)
    HCard(Modifier.fillMaxWidth(), border = if (f.ioc) col.copy(alpha = 0.6f) else null) {
        Row(verticalAlignment = Alignment.Top) {
            val first = n == 1
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (first) C.goldBrush else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .border(1.5.dp, C.Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(n.toString(), color = if (first) C.OnGold else C.GoldLight, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(f.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(f.why, color = C.Muted, fontSize = 13.sp)
                Text(L.t("ما الذي تفعله: ", "تعمل إيه: ", "What to do: ") + f.todo, color = C.Text2, fontSize = 13.sp)
                if (f.evidence.isNotEmpty()) {
                    Text(if (open) L.t("إخفاء التفاصيل", "اخفي التفاصيل", "Hide details") else L.t("التفاصيل (${f.evidence.size})", "التفاصيل (${f.evidence.size})", "Details (${f.evidence.size})"),
                        color = C.Muted, fontSize = 12.sp, modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp))
                    if (open) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Bg).padding(10.dp)) {
                        f.evidence.forEach { Text(it, color = C.Muted, fontSize = 12.sp) }
                    }
                }
                val topic = AskKB.topicFor(f.key)
                if (f.fixes.isNotEmpty() || topic != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    f.fixes.forEach { fx -> ActionText(Actions.label(fx)) { onFix(fx) } }
                    if (topic != null) ActionText(L.t("اشرح لي", "اشرحلي", "Explain")) { Ask.open(Screen.RESULT, topic) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.padding(top = 6.dp)) { Dot(col, 8.dp) }
        }
    }
}

@Composable
fun CheckRow(c: Check) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(c.detail + if (c.hint.isNotEmpty() && c.state != CheckState.PASS) "  ·  ${c.hint}" else "", color = C.Muted, fontSize = 12.sp)
            val fx = c.fix
            if (fx != null && c.state != CheckState.PASS) ActionText(L.t("افتح الإعداد", "افتح الإعداد", "Open the setting")) { Actions.open(ctx, fx) }
        }
        Spacer(Modifier.width(10.dp))
        Text(when (c.state) { CheckState.PASS -> L.t("سليم", "تمام", "OK"); CheckState.WARN -> L.t("راجِع", "راجع", "Check"); CheckState.FAIL -> L.t("خطر", "خطر", "Risk") },
            color = checkColor(c.state), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ LINK
@Composable
fun LinkScreen() {
    var text by remember(App.linkText) { mutableStateOf(App.linkText) }
    val r = App.linkResult
    Backdrop {
        TopBar(L.t("فحص الروابط", "فحص اللينكات", "Link check"), { App.screen = App.back })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("الصق الرابط الذي وصلك. لا يفتح Horus Shield الصفحة، بل يتحقق من وجهتها ومالكها وعمرها.", "الزق اللينك اللي جالك. Horus Shield مش بيفتح الصفحة: بيشوف رايحة فين، ومين صاحبها، وعمرها قد إيه.",
                "Paste the link you received. Horus Shield does not open the page: it checks where it leads, who it pretends to be, and how old it is."), color = C.Text2, fontSize = 14.sp)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                placeholder = { Text("https://…", color = C.Dim, style = Ltr) }, textStyle = Ltr.copy(color = C.Text, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { App.checkLink(text) }), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line, cursorColor = C.Gold, focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface),
            )
            Primary(if (App.linkBusy) L.t("جارٍ الفحص...", "بيفحص...", "Checking...") else L.t("افحص الرابط", "افحص اللينك", "Check link"), { App.checkLink(text) }, enabled = !App.linkBusy && text.isNotBlank())
            if (App.linkBusy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = C.Teal, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(L.t("يتتبع التحويلات ويستعلم عن عمر النطاق...", "بيتبع التحويلات ويسأل عن عمر الدومين...", "Following redirects and looking up the domain age..."), color = C.Muted, fontSize = 13.sp)
            }
            if (r != null && !App.linkBusy) {
                if (!r.ok) Text(L.t("هذا ليس رابطًا صحيحًا. الصق الرابط كاملًا كما وصلك.", "ده مش لينك صحيح. الزق اللينك كامل زي ما جالك.", "That is not a valid link. Paste the whole link as you received it."), color = C.Danger, fontSize = 14.sp)
                else {
                    val vc = when (r.verdict) { "danger" -> C.Danger; "suspicious" -> C.Warn; else -> C.Safe }
                    HCard(Modifier.fillMaxWidth(), border = vc.copy(alpha = 0.7f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ico(if (r.verdict == "nosigns") R.drawable.ic_shield_check else R.drawable.ic_warning, vc, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(when (r.verdict) { "danger" -> L.t("خطر: لا تفتح هذا الرابط", "خطر: متفتحش اللينك ده", "Danger: do not open this link"); "suspicious" -> L.t("مشبوه: لا تُدخل فيه أي بيانات", "مشبوه: متكتبش فيه أي بيانات", "Suspicious: type nothing into it"); else -> L.t("لا توجد علامات خطر معروفة", "مفيش علامات خطر معروفة", "No known danger signs") },
                                    color = vc, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                r.ageDays?.let { Text(L.t("النطاق ", "الدومين ", "Domain ") + ageText(it), color = C.Muted, fontSize = 13.sp) }
                            }
                        }
                        Text(r.finalUrl, color = C.Text, fontSize = 13.sp, style = Ltr)
                        var meta = L.t("النطاق الفعلي: ", "الدومين الفعلي: ", "Actual domain: ") + r.domain
                        if (r.chain.size > 1) meta += "  ·  " + L.t("${r.chain.size - 1} تحويل", "${r.chain.size - 1} تحويل", "${r.chain.size - 1} redirects")
                        Text(meta, color = C.Muted, fontSize = 12.sp)
                        if (r.verdict == "nosigns") Text(L.t("هذا ليس ضمانًا لأمانه، فصفحات الاحتيال الجديدة تستغرق وقتًا حتى تُسجَّل. إن طلب الرابط كلمة مرور أو بيانات بطاقة، فادخل إلى الموقع من عنوانه الذي تعرفه لا من الرابط.", "ده مش ضمان إنه آمن: صفحات النصب الجديدة بتاخد وقت لحد ما تتسجل. لو اللينك طالب باسورد أو بيانات كارت، ادخل الموقع من عنوانه اللي تعرفه مش من اللينك.",
                            "This is not a guarantee: new scam pages take time to be listed. If the link asks for a password or card details, go to the site by the address you know, not through the link."), color = C.Text2, fontSize = 13.sp)
                    }
                    if (r.signals.isNotEmpty()) {
                        SectionTitle(L.t("السبب", "ليه", "Why"))
                        r.signals.forEachIndexed { i, s ->
                            HCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text((i + 1).toString(), color = levelColor(s.level), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(28.dp))
                                    Text(s.text, color = if (s.level == "note") C.Text2 else C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------ SERVICES (the Tools tab)
@Composable
fun ToolsScreen() = TabScreen(Tab.TOOLS) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("الخدمات", "الخدمات", "Services"), null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(L.t("فلوسك وحساباتك", "فلوسك وحساباتك", "Your money and accounts"))
            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ListRow(L.t("افحص رابطًا", "افحص لينك", "Check a link"), L.t("قبل أن تفتح أي رابط من واتساب أو رسالة", "قبل ما تفتح أي لينك من واتساب أو رسالة", "Before you open a link from WhatsApp or a message"), icon = R.drawable.ic_link, onClick = { App.linkResult = null; App.go(Screen.LINK) })
                ListRow(L.t("تطبيقات البنوك والمحافظ", "تطبيقات البنوك والمحافظ", "Bank and wallet apps"), L.t("هل إنستاباي والبنك والمحفظة هي النسخ الرسمية؟", "إنستاباي والبنك والمحفظة رسميين ولا لأ؟", "Are InstaPay, your bank and wallet the official apps?"), icon = R.drawable.ic_bank, onClick = { App.bankApps = null; App.go(Screen.BANKAPPS) })
                ListRow(L.t("واتساب والمكالمات", "واتساب والمكالمات", "WhatsApp and calls"), L.t("اكشف تحويل مكالماتك وأمّن حساب واتساب", "اكشف تحويل مكالماتك وأمّن حساب واتساب", "Find call forwarding and secure your WhatsApp"), icon = R.drawable.ic_phone, onClick = { App.go(Screen.WHATSAPP) })
                ListRow(L.t("أبلغ عن احتيال", "بلّغ عن نصب", "Report a scam"), L.t("رابط أو رقم احتيال وصلك", "لينك أو رقم نصب جالك", "A scam link or number you received"), icon = R.drawable.ic_flag, onClick = { App.reportMsg = ""; App.go(Screen.SCAMREPORT) }, last = true)
            }
            SectionTitle(L.t("خصوصيتك", "خصوصيتك", "Your privacy"))
            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ListRow(L.t("أحدهم يراقبني", "حد بيراقبني", "Someone is watching me"), L.t("خطة آمنة خطوة بخطوة، وأرقام المساعدة في مصر", "خطة آمنة خطوة بخطوة، وأرقام المساعدة في مصر", "A safe plan step by step, and helplines in Egypt"), subColor = C.Danger, icon = R.drawable.ic_lifebuoy, onClick = { App.go(Screen.HELP) })
                ListRow(L.t("أجهزة التتبع بالبلوتوث", "أجهزة التتبع بالبلوتوث", "Bluetooth trackers"), L.t("أجهزة مثل AirTag قد تكون قريبة منك", "أجهزة زي AirTag ممكن تكون جنبك", "AirTag, SmartTag and Tile near you"), icon = R.drawable.ic_radar, onClick = { App.go(Screen.TRACKERS) })
                val exp = App.result?.exposure?.firstOrNull()
                ListRow(L.t("من يستطيع سماعك ورؤيتك", "مين يقدر يسمعك ويشوفك", "Who can hear and see you"), exp?.let { L.t("${it.apps.size} تطبيقات لديها ${it.label}", "${it.apps.size} تطبيقات معاها ${it.label}", "${it.apps.size} apps with ${it.label}") } ?: L.t("الميكروفون والكاميرا والموقع", "المايك والكاميرا والموقع", "Microphone, camera and location"),
                    icon = R.drawable.ic_mic, onClick = { openPermissionManager(ctx) }, last = true)
            }
            SectionTitle(L.t("موبايلك", "موبايلك", "Your phone"))
            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ListRow(L.t("افحص موبايلي", "افحص موبايلي", "Scan my phone"), L.t("برامج التجسس والتطبيقات المزيفة والإعدادات الخطرة", "برامج التجسس والتطبيقات المزيفة والإعدادات الخطر", "Spyware, fake apps and risky settings"), icon = R.drawable.ic_shield_pulse, onClick = { App.startScan(deep = true) })
                ListRow(L.t("قبل الصيانة وبعدها", "قبل التصليح وبعده", "Before and after repair"), if (App.repairSavedAt > 0) L.t("الحالة محفوظة · قارن الآن", "الحالة متحفظة · قارن دلوقتي", "State saved · compare now") else L.t("احفظ حالة الهاتف قبل تسليمه لمحل صيانة", "احفظ حالة الموبايل قبل ما تسلّمه لمحل تصليح", "Save the phone's state before handing it in"),
                    icon = R.drawable.ic_wrench, onClick = { App.go(Screen.REPAIR) })
                ListRow(L.t("اسأل حورس", "اسأل حورس", "Ask Horus"), L.t("أسئلة ونصائح أمنية، دون إنترنت", "أسئلة ونصايح أمنية، من غير نت", "Questions and security tips, offline"), icon = R.drawable.ic_chat, onClick = { Ask.open(Screen.TOOLS) }, last = true)
            }
            Text(L.t("قاعدة التهديدات واستهلاك البيانات وأدوات الخبراء في الإعدادات.", "قاعدة التهديدات واستهلاك البيانات وأدوات الخبراء في الإعدادات.", "Threat intel, data usage and expert tools are in Settings."), color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------ REPORTS
@Composable
fun ReportsScreen() = TabScreen(Tab.REPORTS) {
    val history = remember(App.lastScanAt) { History.list() }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("التقارير", "التقارير", "Reports"), null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val r = App.result
            if (r != null) HCard(Modifier.fillMaxWidth(), accent = true, onClick = { App.screen = Screen.RESULT }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoldShield(Modifier.size(width = 44.dp, height = 50.dp), keyhole = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L.t("آخر تقرير", "آخر تقرير", "Latest report"), color = C.Muted, fontSize = 12.sp)
                        Text(verdictTitle(r.verdict), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(scoreLine(r.score) + "  ·  " + dayLabel(r.atMillis), color = C.GoldLight, fontSize = 13.sp)
                    }
                    Ico(R.drawable.ic_chev_left, C.Gold, 20.dp)
                }
            }
            SectionTitle(L.t("آخر الفحوصات", "آخر الفحوصات", "Recent scans"))
            if (history.isEmpty()) {
                Text(L.t("لا توجد فحوصات بعد.", "لسه مفيش فحوصات.", "No scans yet."), color = C.Muted, fontSize = 14.sp)
                Primary(L.t("فحص الجهاز", "فحص الجهاز", "Scan device"), { App.startScan(deep = true) })
            } else HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                history.forEachIndexed { i, e ->
                    val kind = when { e.auto -> L.t("تلقائي", "تلقائي", "Automatic"); e.deep -> L.t("عميق", "عميق", "Deep"); else -> L.t("سريع", "سريع", "Quick") }
                    ListRow(dayLabel(e.at), kind + "  ·  " + verdictSub(e.verdict), last = i == history.lastIndex) {
                        Text("${e.score}%", color = if (e.verdict == "compromised") C.Danger else scoreColor(e.score), fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, style = Ltr)
                    }
                }
            }
            Decor(R.drawable.art_frieze2, Modifier.fillMaxWidth().padding(vertical = 8.dp), alpha = 0.3f)
        }
    }
}

// ------------------------------------------------------------------ ABOUT
@Composable
fun AboutScreen() {
    val intel = App.intel
    val ctx = LocalContext.current
    Backdrop {
        TopBar(L.t("حول التطبيق", "حول التطبيق", "About"), { App.screen = Screen.SETTINGS })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HorusEye(Modifier.width(130.dp))
                Spacer(Modifier.height(10.dp))
                Wordmark(18f)
                Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = C.Muted, fontSize = 13.sp, style = Ltr)
            }
            HCard(Modifier.fillMaxWidth()) {
                Text(L.t("التطوير والملكية", "تطوير وملكية", "Developed and owned by"), color = C.Muted, fontSize = 13.sp)
                Text(Owner.name(), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(L.t("صُنع في مصر", "صُنع في مصر", "Made in Egypt"), color = C.GoldLight, fontSize = 14.sp)
                Text(Owner.copyright(), color = C.Text2, fontSize = 13.sp)
                Text(L.t("يُمنع نسخ التطبيق أو تعديله أو إعادة نشره دون إذن كتابي.", "ممنوع نسخ التطبيق أو تعديله أو إعادة نشره من غير إذن مكتوب.", "Copying, modifying or republishing the app without written permission is prohibited."), color = C.Dim, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary(L.t("شروط الاستخدام", "شروط الاستخدام", "Terms of use"), { LegalPage.page = 0; App.screen = Screen.LEGAL }, Modifier.weight(1f))
                    Secondary(L.t("الخصوصية", "الخصوصية", "Privacy"), { LegalPage.page = 1; App.screen = Screen.LEGAL }, Modifier.weight(1f))
                }
                Secondary(L.t("التراخيص ومصادر البيانات", "التراخيص ومصادر البيانات", "Licences and data sources"), { LegalPage.page = 2; App.screen = Screen.LEGAL }, Modifier.fillMaxWidth())
                fun open(u: String) { try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } }
                ActionText(L.t("الموقع الرسمي: horusict.com", "الموقع الرسمي: horusict.com", "Official website: horusict.com")) { open(Owner.website) }
            }
            HCard(Modifier.fillMaxWidth()) {
                Text(L.t("قاعدة التهديدات", "قاعدة التهديدات", "Threat intel"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val src = when (intel?.source) { "downloaded" -> L.t("محدّثة وموقّعة", "متحدّثة وموقّعة", "updated and signed"); "bundled" -> L.t("المرفقة مع التطبيق", "اللي جاية مع التطبيق", "bundled with the app"); else -> "—" }
                Text(L.t("التاريخ: ", "التاريخ: ", "Date: ") + (intel?.date ?: "—") + "  ·  " + src + (intel?.let { Intel.ageDays(it) }?.let { L.t("  ·  منذ $it يوم", "  ·  من $it يوم", "  ·  $it days old") } ?: ""), color = C.Text2, fontSize = 13.sp)
                if (intel != null) Text(L.t("${intel.stalkerware.size} برنامج تجسس، ${intel.c2Domains.size} نطاق تحكم، ${intel.malwareUrls.size} رابط خبيث، ${intel.kev.size} ثغرة أندرويد مستغلة", "${intel.stalkerware.size} برنامج تجسس، ${intel.c2Domains.size} دومين تحكم، ${intel.malwareUrls.size} لينك خبيث، ${intel.kev.size} ثغرة أندرويد مستغلة",
                    "${intel.stalkerware.size} stalkerware apps, ${intel.c2Domains.size} command domains, ${intel.malwareUrls.size} malware links, ${intel.kev.size} exploited Android flaws"), color = C.Muted, fontSize = 12.sp)
                Secondary(if (App.intelBusy) L.t("جارٍ التحديث...", "بيحدّث...", "Updating...") else L.t("حدّث الآن", "حدّث دلوقتي", "Update now"), { App.updateIntel() }, Modifier.fillMaxWidth(), enabled = !App.intelBusy)
                if (App.intelMsg.isNotEmpty()) Text(App.intelMsg, color = C.GoldLight, fontSize = 13.sp)
                Text(L.t("لا يُقبل الملف إلا إذا طابق توقيعه الرقمي مفتاح Horus Shield، فلا يستطيع أحد إرسال قاعدة مزيفة.", "الملف بيتقبل بس لو توقيعه الرقمي مطابق لمفتاح Horus Shield، فمحدش يقدر يبعت قاعدة مزيفة.", "A pack is accepted only if its digital signature matches the Horus Shield key, so nobody can push a fake one."), color = C.Dim, fontSize = 12.sp)
            }
            HCard(Modifier.fillMaxWidth()) {
                Text(L.t("تحديث التطبيق", "تحديث التطبيق", "App updates"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(L.t("نسختك الحالية: ", "النسخة اللي عندك: ", "Installed version: ") + BuildConfig.VERSION_NAME, color = C.Text2, fontSize = 13.sp)
                Secondary(if (App.updateBusy && App.updateProgress < 0) L.t("جارٍ البحث...", "بيدوّر...", "Checking...") else L.t("ابحث عن تحديث", "دوّر على تحديث", "Check for updates"), { App.checkUpdate() }, Modifier.fillMaxWidth(), enabled = !App.updateBusy)
                if (App.updateMsg.isNotEmpty() && App.update == null) Text(App.updateMsg, color = C.GoldLight, fontSize = 13.sp)
            }
            UpdateBanner()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun UpdateBanner() {
    val u = App.update ?: return
    val ctx = LocalContext.current
    HCard(Modifier.fillMaxWidth(), accent = true) {
        Text(L.t("توجد نسخة جديدة: ", "فيه نسخة جديدة: ", "New version available: ") + u.name, color = C.GoldLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (u.notes.isNotBlank()) Text(u.notes, color = C.Text2, fontSize = 13.sp)
        if (App.needInstallPermission) {
            Text(L.t("يحتاج أندرويد إلى أن تسمح لـ Horus Shield بتثبيت تحديثاته. فعّل «Allow from this source» ثم عُد واضغط تحديث مرة أخرى.", "أندرويد محتاج تسمح لـ Horus Shield يسطّب التحديثات بتاعته. فعّل «Allow from this source» وارجع دوس تحديث تاني.",
                "Android needs you to let Horus Shield install its own updates. Turn on «Allow from this source», come back and tap Update again."), color = C.Text, fontSize = 13.sp)
            Secondary(L.t("افتح الإذن", "افتح الإذن", "Open the permission"), { Updater.openInstallPermission(ctx) }, Modifier.fillMaxWidth())
        }
        val p = App.updateProgress
        Primary(if (p >= 0) L.t("جارٍ التنزيل $p%", "بينزّل $p%", "Downloading $p%") else L.t("نزّل وحدّث", "نزّل وحدّث", "Download and update"), { App.installUpdate() }, enabled = !App.updateBusy)
        Text(L.t("نتحقق من مطابقة الملف للنسخة الرسمية قبل التثبيت، ولن تُحذف إعداداتك ونتائجك.", "بنتأكد إن الملف مطابق للنسخة الرسمية قبل التسطيب، وإعداداتك ونتايجك مش هتتمسح.",
            "The file is checked against the official build before installing; your settings and results are kept."), color = C.Dim, fontSize = 12.sp)
        if (App.updateMsg.isNotEmpty()) Text(App.updateMsg, color = C.Danger, fontSize = 13.sp)
    }
}
