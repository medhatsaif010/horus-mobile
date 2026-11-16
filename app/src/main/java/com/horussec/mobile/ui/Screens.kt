/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloatAsState
import com.horussec.mobile.History
import com.horussec.mobile.CheckState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horussec.mobile.Actions
import com.horussec.mobile.App
import com.horussec.mobile.BuildConfig
import com.horussec.mobile.Check
import com.horussec.mobile.Finding
import com.horussec.mobile.Fix
import com.horussec.mobile.Intel
import com.horussec.mobile.L
import com.horussec.mobile.Screen
import com.horussec.mobile.Sev
import com.horussec.mobile.Updater
import com.horussec.mobile.AutoScan
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stamp = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US)
private val hm = SimpleDateFormat("HH:mm", Locale.US)

// ------------------------------------------------------------------ seal wording
fun verdictWord(v: String?): String = when (v) {
    "compromised" -> L.t("مخترق", "Hacked"); "suspicious" -> L.t("راجع", "Review"); else -> L.t("سليم", "Clean")
}

fun verdictSub(v: String?, iocs: Int = 0): String = when (v) {
    "compromised" -> if (iocs > 0) L.t("غالبًا · علامات اختراق: $iocs", "Likely · $iocs signs of compromise") else L.t("غالبًا: فيه علامات اختراق", "Likely: signs of compromise")
    "suspicious" -> L.t("فيه حاجات محتاجة تتأكد منها", "Some things need checking")
    else -> L.t("مفيش علامات اختراق", "No signs of compromise")
}

fun scoreLine(score: Int): String = L.t("رقم الأمان $score / 100", "Score $score / 100")

fun ringText(deep: Boolean, at: Long, apps: Int): String {
    val d = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(if (at > 0) at else System.currentTimeMillis())).uppercase(Locale.US)
    return "HORUS SEC · " + (if (deep) "DEEP SCAN" else "QUICK SCAN") + " · $d · " + (if (apps > 0) "$apps APPS · " else "")
}

/** "Today 13:40", "Yesterday 07:40", or the date. */
fun dayLabel(at: Long): String {
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = at
    val that = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = System.currentTimeMillis(); cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yest = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    return when (that) {
        today -> L.t("النهارده ", "Today ") + hm.format(Date(at))
        yest -> L.t("امبارح ", "Yesterday ") + hm.format(Date(at))
        else -> SimpleDateFormat("dd/MM  HH:mm", Locale.US).format(Date(at))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { content() }
}

// ------------------------------------------------------------------ HOME
@Composable
fun HomeScreen() {
    val history = remember(App.lastScanAt) { History.list() }
    Column(Modifier.fillMaxSize()) {
        TopBar(null, null)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val last = App.lastScore
            Centered {
                if (last != null) {
                    val v = App.lastVerdict
                    val h = history.firstOrNull()
                    Seal(
                        verdictWord(v), verdictColor(v ?: "clean"),
                        ringText(h?.deep ?: App.result?.deep ?: true, App.lastScanAt, h?.apps ?: App.result?.appCount ?: 0),
                        sub = verdictSub(v, App.result?.iocCount ?: 0), line = scoreLine(last),
                    )
                } else {
                    Seal(
                        L.t("حورس", "Horus"), C.Gold, "HORUS SEC · MOBILE · V" + BuildConfig.VERSION_NAME + " · ",
                        sub = L.t("موبايلك آمن؟ اعمل أول فحص", "Is your phone safe? Run a first scan"),
                    )
                }
            }
            UpdateBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Primary(L.t("فحص عميق", "Deep scan"), { App.startScan(deep = true) }, Modifier.weight(1.2f))
                Secondary(L.t("فحص سريع", "Quick scan"), { App.startScan(deep = false) }, Modifier.weight(1f).height(52.dp))
            }
            if (App.result != null) ActionText(L.t("اعرض نتيجة آخر فحص", "Show the last result")) { App.screen = Screen.RESULT }

            Column {
                SectionTitle(L.t("آخر الفحوصات", "Recent scans"))
                if (history.isEmpty()) {
                    Text(L.t("لسه مفيش فحوصات. الفحص العميق بياخد أقل من دقيقة.", "No scans yet. A deep scan takes under a minute."), color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Hairline()
                }
                history.take(5).forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        val kind = when { e.auto -> L.t("تلقائي", "Automatic"); e.deep -> L.t("عميق", "Deep"); else -> L.t("سريع", "Quick") }
                        Text(dayLabel(e.at) + "  ·  " + kind, color = C.Text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(e.score.toString(), color = if (e.verdict == "compromised") C.Danger else scoreColor(e.score), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Hairline()
                }
            }

            Column {
                SectionTitle(L.t("جالك لينك؟", "Got a link?"))
                Text(L.t("قبل ما تفتح لينك من واتساب أو رسالة، افحصه هنا. أو من أي تطبيق: مشاركة ثم Horus Sec.",
                    "Before opening a link from WhatsApp or a message, check it here. Or from any app: Share, then Horus Sec."), color = C.Text2, fontSize = 14.sp)
                ActionText(L.t("افحص لينك", "Check a link")) { App.linkResult = null; App.screen = Screen.LINK }
                Hairline()
            }

            AutoScanCard()
            RootCard()
            Text(L.t("Horus Sec مش بيمسح ولا بيغيّر حاجة لوحده، ومش بيقرا رسايلك ولا صورك، ومش بيبعت بيانات الموبايل لأي سيرفر. كل تصليح إنت اللي بتعمله بزرار بيفتحلك الإعداد الصح.",
                "Horus Sec never removes or changes anything on its own, never reads your messages or photos, and never sends phone data to any server. Every fix is yours, through a button that opens the right setting."),
                color = C.Muted, fontSize = 13.sp)
            val intel = App.intel
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Horus Sec  ·  " + L.t("مدحت سيف", "Medhat Seif") + "\nv" + BuildConfig.VERSION_NAME + "  ·  " + L.t("قاعدة التهديدات: ", "Threat intel: ") + (intel?.date?.ifEmpty { "—" } ?: "…"),
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                SmallLink(L.t("عن البرنامج", "About")) { App.screen = Screen.ABOUT }
            }
        }
    }
}

fun gradeOf(v: Int): String = when { v >= 85 -> L.t("ممتاز", "Excellent"); v >= 70 -> L.t("كويس", "Good"); v >= 50 -> L.t("محتاج شغل", "Needs work"); else -> L.t("ضعيف", "Weak") }

// ------------------------------------------------------------------ SCANNING
@Composable
fun ScanningScreen() {
    val steps = listOf(
        L.t("بيقرا قايمة التطبيقات", "Reading the app list"),
        L.t("بيفحص التطبيقات والتوقيعات", "Checking apps and signatures"),
        L.t("بيفحص الصلاحيات القوية", "Checking powerful access"),
        L.t("بيفحص الشبكة والشهادات", "Checking network and certificates"),
        L.t("بيحسب النتيجة", "Working out the result"),
    ) + (if (App.scanDeep) listOf(
        L.t("فحص عميق: بيحسب بصمات التطبيقات", "Deep: fingerprinting apps"),
        L.t("فحص عميق: بيختبر الـ DNS والتحكم في الموبايل", "Deep: testing DNS and device control"),
        L.t("فحص عميق: بيراجع البيانات اللي اتبعتت", "Deep: reviewing data sent out"),
    ) else emptyList())
    val step = App.scanStep.coerceIn(0, steps.size)
    val prog by animateFloatAsState((step + 0.5f) / steps.size, tween(600), label = "p")
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxSize()) {
        TopBar(null, null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Centered {
                Seal(
                    L.t("بيفحص", "Scanning"), C.Gold, ringText(App.scanDeep, System.currentTimeMillis(), 0),
                    modifier = Modifier.alpha(a), sub = steps.getOrNull(step) ?: steps.last(), line = "${minOf(step + 1, steps.size)} / ${steps.size}", progress = prog,
                )
            }
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { i, s ->
                val done = i < step; val now = i == step
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s, color = if (done || now) C.Text else C.Dim, fontSize = 15.sp, fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text(when { done -> L.t("تم", "Done"); now -> L.t("دلوقتي", "Now"); else -> "" }, color = if (done) C.Safe else C.Gold, fontSize = 13.sp)
                }
                Hairline()
            }
        }
    }
}

// ------------------------------------------------------------------ RESULT
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen() {
    val r = App.result ?: run { App.screen = Screen.HOME; return }
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Finding?>(null) }
    var rootAsk by remember { mutableStateOf<Pair<Finding, Fix>?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("نتيجة الفحص", "Scan result"), { App.screen = Screen.HOME }) {
            SmallLink(L.t("شارك", "Share")) { Actions.shareText(ctx, Actions.summary(r, App.device)) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                Centered {
                    Seal(verdictWord(r.verdict), verdictColor(r.verdict), ringText(r.deep, r.atMillis, r.appCount),
                        sub = verdictSub(r.verdict, r.findings.count { it.ioc }), line = scoreLine(r.score))
                }
                Text(r.headline, color = C.Text2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text((if (r.deep) L.t("فحص عميق", "Deep scan") else L.t("فحص سريع: الفحص العميق بيدوّر أكتر", "Quick scan: a deep scan looks further")) + "  ·  " + stamp.format(Date(r.atMillis)),
                    color = C.Dim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp))
                Secondary(if (r.deep) L.t("افحص تاني", "Scan again") else L.t("اعمل فحص عميق", "Run a deep scan"), { App.startScan(deep = true) }, Modifier.fillMaxWidth())
            }
            if (r.changes.isNotEmpty()) {
                item { SectionTitle(L.t("اتغيّر من آخر فحص", "Changed since last scan")) }
                item { Panel { r.changes.forEach { Text(it, color = C.Text, fontSize = 14.sp) } } }
            }
            item { SectionTitle(if (r.findings.isEmpty()) L.t("مفيش حاجة محتاجة تتصرف فيها", "Nothing needs action") else L.t("ابدأ بالترتيب ده", "Start in this order")) }
            itemsIndexed(r.findings, key = { _, f -> f.key }) { i, f -> FindingRow(i + 1, f) { fx ->
                when {
                    fx == Fix.UNINSTALL && f.stalker -> confirm = f
                    fx == Fix.ROOT_STOP || fx == Fix.ROOT_DISABLE || fx == Fix.ROOT_BLOCKNET -> rootAsk = f to fx
                    else -> Actions.open(ctx, fx, f.pkg)
                }
            } }
            item { SectionTitle(L.t("إعدادات الأمان", "Security settings")) }
            items(r.checks) { CheckRow(it) }
            item { SectionTitle(L.t("مين يقدر يسمعك ويشوفك", "Who can hear and see you")) }
            item {
                Text(L.t("التطبيقات اللي إنت مسطّبها ومعاها الصلاحيات دي دلوقتي:", "Apps you installed that hold these permissions now:"), color = C.Muted, fontSize = 13.sp)
                r.exposure.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(e.label, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (e.apps.isEmpty()) L.t("ولا تطبيق", "None") else e.apps.joinToString(if (L.ar) "، " else ", "), color = C.Muted, fontSize = 13.sp)
                        }
                        Text(e.apps.size.toString(), color = if (e.apps.isEmpty()) C.Safe else C.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Hairline()
                }
                ActionText(L.t("افتح مدير الصلاحيات", "Open permission manager")) { openPermissionManager(ctx) }
            }
            if (r.verified.isNotEmpty()) {
                item { SectionTitle(L.t("اتأكدنا من", "Verified")) }
                items(r.verified) { v ->
                    Text(v, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)); Hairline()
                }
            }
            item {
                Text(
                    L.t("التطبيق بيشوف اللي أندرويد بيسمح لأي تطبيق يشوفه، من غير Root. برامج التجسس المتقدمة جدًا ممكن متبانش هنا. لو شاكك جامد، افحص الموبايل كمان من برنامج Horus Sec على الكمبيوتر بالكابل.",
                        "The app sees what Android lets any app see, without root. Very advanced spyware may not show here. If you strongly suspect something, also scan the phone with Horus Sec on a computer over USB."),
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp),
                )
            }
        }
    }
    rootAsk?.let { (f, fx) ->
        val what = when (fx) {
            Fix.ROOT_STOP -> L.t("هيقفل التطبيق دلوقتي. لو بيشتغل لوحده هيرجع تاني بعد شوية.", "This closes the app now. If it starts itself, it will come back later.")
            Fix.ROOT_DISABLE -> L.t("هيعطّل التطبيق بالكامل لحد ما ترجّعه. بياناته مش هتتمسح، وتقدر ترجّعه من الشاشة الرئيسية.", "This disables the app until you restore it. Its data is kept, and you can restore it from the home screen.")
            else -> L.t("هيمنع التطبيق يوصل للنت لحد ما ترجّعه أو تعمل Restart للموبايل.", "This blocks the app from the internet until you restore it or restart the phone.")
        }
        AlertDialog(
            onDismissRequest = { rootAsk = null }, shape = RoundedCornerShape(6.dp),
            containerColor = C.Surface, titleContentColor = C.Text, textContentColor = C.Text2,
            title = { Text(Actions.label(fx)) },
            text = { Text(what + "\n\n" + (f.pkg ?: "") + if (f.stalker) L.t("\n\nاللي سطّب برنامج التجسس ممكن يلاحظ.", "\n\nWhoever installed the stalkerware may notice.") else "") },
            confirmButton = { TextButton(onClick = { rootAsk = null; f.pkg?.let { App.rootAction(fx, it) } }) { Text(L.t("نفّذ", "Do it"), color = C.Danger) } },
            dismissButton = { TextButton(onClick = { rootAsk = null }) { Text(L.t("إلغاء", "Cancel"), color = C.Gold) } },
        )
    }
    if (App.rootMsg.isNotEmpty() && App.screen == Screen.RESULT) {
        AlertDialog(
            onDismissRequest = { App.rootMsg = "" }, containerColor = C.Surface, textContentColor = C.Text, shape = RoundedCornerShape(6.dp),
            text = { Text(App.rootMsg) }, confirmButton = { TextButton(onClick = { App.rootMsg = "" }) { Text("OK", color = C.Gold) } },
        )
    }
    confirm?.let { f ->
        AlertDialog(
            onDismissRequest = { confirm = null }, shape = RoundedCornerShape(6.dp),
            containerColor = C.Surface, titleContentColor = C.Text, textContentColor = C.Text2,
            title = { Text(L.t("قبل ما تمسحه", "Before you remove it")) },
            text = { Text(L.t("اللي سطّب برنامج التجسس ده هيعرف إنه اتمسح. لو حاسس إن فيه خطر عليك منه، احفظ الملخص الأول واطلب مساعدة من حد تثق فيه، وبعدين امسحه.",
                "Whoever installed this stalkerware will notice it is gone. If you feel at risk from them, save the summary first and get help from someone you trust, then remove it.")) },
            confirmButton = { TextButton(onClick = { confirm = null; Actions.open(ctx, Fix.UNINSTALL, f.pkg) }) { Text(L.t("فاهم، امسحه", "I understand, remove it"), color = C.Danger) } },
            dismissButton = { TextButton(onClick = { confirm = null; Actions.shareText(ctx, Actions.summary(r, App.device)) }) { Text(L.t("احفظ الملخص الأول", "Save the summary first"), color = C.Gold) } },
        )
    }
}

private fun openPermissionManager(ctx: android.content.Context) {
    val tries = listOf(
        android.content.Intent("android.intent.action.MANAGE_PERMISSIONS"),
        android.content.Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS),
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    for (i in tries) { try { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { } }
}

/** One numbered thing to do: big number in the severity colour, then text actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindingRow(n: Int, f: Finding, onFix: (Fix) -> Unit) {
    var open by remember(f.key) { mutableStateOf(false) }
    val col = sevColor(f.sev)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(n.toString(), color = col, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(f.title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(f.why, color = C.Muted, fontSize = 14.sp)
            Text(L.t("تعمل إيه: ", "What to do: ") + f.todo, color = C.Text2, fontSize = 14.sp)
            if (f.evidence.isNotEmpty()) {
                Text(if (open) L.t("اخفي التفاصيل", "Hide details") else L.t("التفاصيل (${f.evidence.size})", "Details (${f.evidence.size})"),
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp))
                if (open) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Surface).padding(10.dp)) {
                    f.evidence.forEach { Text(it, color = C.Muted, fontSize = 12.sp) }
                }
            }
            if (f.fixes.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                f.fixes.forEach { fx -> ActionText(Actions.label(fx), col) { onFix(fx) } }
            }
        }
    }
    Hairline()
}

@Composable
fun CheckRow(c: Check) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.name, color = C.Text, fontSize = 15.sp)
            Text(c.detail + if (c.hint.isNotEmpty() && c.state != CheckState.PASS) "  ·  ${c.hint}" else "", color = C.Muted, fontSize = 12.sp)
            val fx = c.fix
            if (fx != null && c.state != CheckState.PASS) ActionText(L.t("افتح الإعداد", "Open the setting"), checkColor(c.state)) { Actions.open(ctx, fx) }
        }
        Spacer(Modifier.width(10.dp))
        Text(when (c.state) { CheckState.PASS -> L.t("تمام", "OK"); CheckState.WARN -> L.t("راجع", "Check"); CheckState.FAIL -> L.t("خطر", "Risk") },
            color = checkColor(c.state), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Hairline()
}

// ------------------------------------------------------------------ LINK
@Composable
fun LinkScreen() {
    var text by remember(App.linkText) { mutableStateOf(App.linkText) }
    val r = App.linkResult
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("افحص لينك", "Check a link"), { App.screen = Screen.HOME })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("الزق اللينك اللي جالك. Horus Sec مش بيفتح الصفحة: بيشوف رايحة فين، ومين صاحبها، وعمرها قد إيه.",
                "Paste the link you received. Horus Sec does not open the page: it checks where it leads, who it pretends to be, and how old it is."), color = C.Text2, fontSize = 14.sp)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                placeholder = { Text("https://…", color = C.Dim, style = Ltr) }, textStyle = Ltr.copy(color = C.Text, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { App.checkLink(text) }), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line, cursorColor = C.Gold, focusedContainerColor = C.Bar, unfocusedContainerColor = C.Bar),
            )
            Primary(if (App.linkBusy) L.t("بيفحص...", "Checking...") else L.t("افحص اللينك", "Check link"), { App.checkLink(text) }, enabled = !App.linkBusy && text.isNotBlank())
            if (App.linkBusy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = C.Gold, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(L.t("بيتبع التحويلات ويسأل عن عمر الدومين...", "Following redirects and looking up the domain age..."), color = C.Muted, fontSize = 13.sp)
            }
            if (r != null && !App.linkBusy) {
                if (!r.ok) Text(L.t("ده مش لينك صحيح. الزق اللينك كامل زي ما جالك.", "That is not a valid link. Paste the whole link as you received it."), color = C.Danger, fontSize = 14.sp)
                else {
                    val vc = when (r.verdict) { "danger" -> C.Danger; "suspicious" -> C.Warn; else -> C.Safe }
                    val word = when (r.verdict) { "danger" -> L.t("خطر", "Danger"); "suspicious" -> L.t("مشبوه", "Suspect"); else -> L.t("عادي", "Clear") }
                    val sub = when (r.verdict) {
                        "danger" -> L.t("متفتحش اللينك ده", "Do not open this link")
                        "suspicious" -> L.t("متكتبش فيه أي بيانات", "Do not type anything into it")
                        else -> L.t("مفيش علامات خطر معروفة", "No known danger signs")
                    }
                    Centered {
                        Seal(word, vc, r.domain.uppercase(Locale.US).take(28) + " · LINK CHECK · " + SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()).uppercase(Locale.US) + " · ",
                            sub = sub, line = r.ageDays?.let { L.t("عمر الدومين $it يوم", "Domain $it days old") })
                    }
                    Text(r.finalUrl, color = C.Text, fontSize = 13.sp, style = Ltr)
                    var meta = L.t("الدومين الفعلي: ", "Actual domain: ") + r.domain
                    if (r.chain.size > 1) meta += "  ·  " + L.t("${r.chain.size - 1} تحويل", "${r.chain.size - 1} redirects")
                    Text(meta, color = C.Muted, fontSize = 12.sp)
                    if (r.verdict == "nosigns") Text(L.t("ده مش ضمان إنه آمن: صفحات النصب الجديدة بتاخد وقت لحد ما تتسجل. لو اللينك طالب باسورد أو بيانات كارت، ادخل الموقع من عنوانه اللي تعرفه مش من اللينك.",
                        "This is not a guarantee: new scam pages take time to be listed. If the link asks for a password or card details, go to the site by the address you know, not through the link."), color = C.Text2, fontSize = 13.sp)
                    if (r.signals.isNotEmpty()) Column {
                        SectionTitle(L.t("ليه", "Why"))
                        r.signals.forEachIndexed { i, s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                Text((i + 1).toString(), color = levelColor(s.level), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                                Text(s.text, color = if (s.level == "note") C.Text2 else C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            }
                            Hairline()
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ ABOUT
@Composable
fun AboutScreen() {
    val intel = App.intel
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("عن البرنامج", "About"), { App.screen = Screen.HOME })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorusMark(Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Horus Sec Mobile", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = C.Muted, fontSize = 13.sp)
                }
            }
            Panel {
                Text(L.t("تطوير وملكية", "Developed and owned by"), color = C.Muted, fontSize = 13.sp)
                Text(Owner.name(), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(L.t("صُنع في مصر", "Made in Egypt"), color = C.Gold, fontSize = 14.sp)
                Text(Owner.copyright(), color = C.Text2, fontSize = 13.sp)
                Text(L.t("ممنوع نسخ التطبيق أو تعديله أو إعادة نشره من غير إذن مكتوب.", "Copying, modifying or republishing the app without written permission is prohibited."), color = C.Dim, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary(L.t("شروط الاستخدام", "Terms of use"), { LegalPage.page = 0; App.screen = Screen.LEGAL }, Modifier.weight(1f))
                    Secondary(L.t("الخصوصية", "Privacy"), { LegalPage.page = 1; App.screen = Screen.LEGAL }, Modifier.weight(1f))
                }
                Secondary(L.t("التراخيص ومصادر البيانات", "Licences and data sources"), { LegalPage.page = 2; App.screen = Screen.LEGAL }, Modifier.fillMaxWidth())
                SmallLink(L.t("تواصل مع المطوّر", "Contact the developer")) {
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Owner.contact)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
            }
            Panel {
                Text(L.t("قاعدة التهديدات", "Threat intel"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val src = when (intel?.source) { "downloaded" -> L.t("متحدّثة وموقّعة", "updated and signed"); "bundled" -> L.t("اللي جاية مع التطبيق", "bundled with the app"); else -> "—" }
                Text(L.t("التاريخ: ", "Date: ") + (intel?.date ?: "—") + "  ·  " + src + (intel?.let { Intel.ageDays(it) }?.let { L.t("  ·  من $it يوم", "  ·  $it days old") } ?: ""), color = C.Text2, fontSize = 13.sp)
                if (intel != null) Text(L.t("${intel.stalkerware.size} برنامج تجسس، ${intel.c2Domains.size} دومين تحكم، ${intel.malwareUrls.size} لينك خبيث، ${intel.kev.size} ثغرة أندرويد مستغلة",
                    "${intel.stalkerware.size} stalkerware apps, ${intel.c2Domains.size} command domains, ${intel.malwareUrls.size} malware links, ${intel.kev.size} exploited Android flaws"), color = C.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Secondary(if (App.intelBusy) L.t("بيحدّث...", "Updating...") else L.t("حدّث دلوقتي", "Update now"), { App.updateIntel() }, Modifier.fillMaxWidth(), enabled = !App.intelBusy)
                if (App.intelMsg.isNotEmpty()) Text(App.intelMsg, color = C.Gold, fontSize = 13.sp)
                Text(L.t("الملف بيتقبل بس لو توقيعه الرقمي مطابق لمفتاح Horus Sec، فمحدش يقدر يبعت قاعدة مزيفة.", "A pack is accepted only if its digital signature matches the Horus Sec key, so nobody can push a fake one."), color = C.Dim, fontSize = 12.sp)
            }
            Panel {
                Text(L.t("تحديث التطبيق", "App updates"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(L.t("النسخة اللي عندك: ", "Installed version: ") + BuildConfig.VERSION_NAME, color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Secondary(if (App.updateBusy && App.updateProgress < 0) L.t("بيدوّر...", "Checking...") else L.t("دوّر على تحديث", "Check for updates"), { App.checkUpdate() }, Modifier.fillMaxWidth(), enabled = !App.updateBusy)
                if (App.updateMsg.isNotEmpty() && App.update == null) Text(App.updateMsg, color = C.Gold, fontSize = 13.sp)
            }
            UpdateBanner()
            Panel {
                Text(L.t("خصوصيتك", "Your privacy"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(L.t("الفحص بيتم على الموبايل بس. اللي بيخرج من الموبايل: تنزيل قاعدة التهديدات، ولما تفحص لينك بنطلب عنوان الصفحة بس (من غير ما نفتحها) ونسأل rdap.org عن عمر الدومين.",
                    "Scans run on the phone only. What leaves the phone: the threat-intel download, and when you check a link, a header-only request to that link and a domain-age lookup at rdap.org."), color = C.Text2, fontSize = 13.sp)
            }

        }
    }
}

@Composable
fun UpdateBanner() {
    val u = App.update ?: return
    val ctx = LocalContext.current
    AccentPanel(C.Gold) {
        Text(L.t("فيه نسخة جديدة: ", "New version available: ") + u.name, color = C.Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (u.notes.isNotBlank()) Text(u.notes, color = C.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        if (App.needInstallPermission) {
            Text(L.t("أندرويد محتاج تسمح لـ Horus Sec يسطّب التحديثات بتاعته. فعّل «Allow from this source» وارجع دوس تحديث تاني.",
                "Android needs you to let Horus Sec install its own updates. Turn on «Allow from this source», come back and tap Update again."), color = C.Text, fontSize = 13.sp)
            Secondary(L.t("افتح الإذن", "Open the permission"), { Updater.openInstallPermission(ctx) }, Modifier.fillMaxWidth())
        }
        val p = App.updateProgress
        Primary(
            when { p >= 0 -> L.t("بينزّل $p%", "Downloading $p%"); else -> L.t("نزّل وحدّث", "Download and update") },
            { App.installUpdate() }, enabled = !App.updateBusy,
        )
        Text(L.t("بنتأكد إن الملف مطابق للنسخة الرسمية قبل التسطيب، وأندرويد هيسألك توافق. إعداداتك ونتايجك مش هتتمسح.",
            "The file is checked against the official build before installing, and Android will ask you to confirm. Your settings and results are kept."), color = C.Dim, fontSize = 12.sp)
        if (App.updateMsg.isNotEmpty()) Text(App.updateMsg, color = C.Danger, fontSize = 13.sp)
    }
}

fun answerOf(v: String): String = when (v) {
    "compromised" -> L.t("غالبًا أيوه: فيه علامات اختراق", "Probably yes: signs of compromise")
    "suspicious" -> L.t("مش متأكدين: فيه حاجات محتاجة تتأكد منها", "Not sure: some things need checking")
    else -> L.t("لأ، مفيش علامات اختراق", "No signs of compromise")
}

fun verdictColor(v: String): Color = when (v) { "compromised" -> C.Danger; "suspicious" -> C.Warn; else -> C.Safe }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoScanCard() {
    val ctx = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> App.notifyAllowed = ok }
    Panel {
        Text(L.t("الفحص التلقائي", "Automatic scan"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(L.t("Horus Sec يعمل فحص عميق لوحده في الخلفية، ويبعتلك تنبيه بس لو ظهرت علامة اختراق أو تطبيق جديد أخد صلاحيات خطيرة.",
            "Horus Sec runs a deep scan on its own in the background, and only alerts you if a sign of compromise appears or a new app gets risky access."), color = C.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoScan.choices.forEach { h ->
                val sel = App.autoHours == h
                val label = if (h == 0) L.t("مقفول", "Off") else L.t("كل $h ساعات", "Every $h h").replace("كل 12 ساعات", "كل 12 ساعة").replace("كل 24 ساعات", "كل يوم")
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) C.Gold else C.Bg).border(1.dp, if (sel) C.Gold else C.Line, RoundedCornerShape(6.dp))
                        .clickable {
                            App.setAuto(h)
                            if (h > 0 && android.os.Build.VERSION.SDK_INT >= 33 && !AutoScan.notificationsAllowed(ctx)) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label, color = if (sel) C.OnGold else C.Text, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        if (App.autoHours > 0) {
            val last = com.horussec.mobile.Prefs.long("lastAutoScan")
            Text(if (last > 0) L.t("آخر فحص تلقائي: ", "Last automatic scan: ") + stamp.format(Date(last)) else L.t("أول فحص تلقائي بعد ${App.autoHours} ساعات. أندرويد ممكن يأخّره شوية علشان البطارية.", "First automatic scan in ${App.autoHours} hours. Android may delay it a little to save battery."), color = C.Muted, fontSize = 12.sp)
            if (!App.notifyAllowed) {
                Text(L.t("التنبيهات مقفولة لـ Horus Sec، فمش هتعرف لو الفحص لقى حاجة.", "Notifications are off for Horus Sec, so you will not hear about findings."), color = C.Gold, fontSize = 13.sp)
                SmallLink(L.t("شغّل التنبيهات", "Turn on notifications")) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(L.t("استهلاك البيانات", "Data usage"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (App.usageAccess) Text(L.t("مسموح: الفحص العميق بيشوف التطبيقات المخفية اللي بتبعت بيانات كتير.", "Allowed: the deep scan spots hidden apps sending lots of data."), color = C.Safe, fontSize = 13.sp)
        else {
            Text(L.t("اسمح بـ Usage access علشان الفحص العميق يكشف التطبيقات اللي بتبعت صورك وتسجيلاتك لبرّه. Horus Sec بيشوف الأرقام بس، مش المحتوى.",
                "Allow Usage access so the deep scan can spot apps sending your photos and recordings out. Horus Sec sees only the numbers, never the content."), color = C.Text2, fontSize = 13.sp)
            SmallLink(L.t("اسمح بـ Usage access", "Allow Usage access")) { com.horussec.mobile.DeepScan.openUsageAccess(ctx) }
        }
    }
}

@Composable
fun RootCard() {
    val ctx = LocalContext.current
    AccentPanel(if (App.rootOn) C.Gold else C.Border) {
        Text(L.t("وضع Root (للخبراء)", "Root mode (experts)"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (!App.rootDetected && !App.rootOn) {
            Text(L.t("الموبايل ده مش عليه Root، فالوضع ده مقفول. وده الأفضل لأمانك.", "This phone is not rooted, so this mode is off. That is safer for you."), color = C.Safe, fontSize = 13.sp)
            Text(L.t("على الموبايلات اللي عليها Root (Magisk أو KernelSU)، الوضع ده بيكشف التطبيقات المتصلة دلوقتي بسيرفرات تحكم، والبرامج المخفية، وتحويل الترافيك، وبيقدر يوقّف أو يعطّل تطبيق خطر بعد موافقتك. متعملش Root علشان تجرّبه: ده بيضعّف حماية الموبايل.",
                "On rooted phones (Magisk or KernelSU), this mode spots apps connected right now to command servers, hidden programs and traffic redirection, and can stop or disable a dangerous app after you confirm. Do not root your phone to try it: rooting weakens its protection."), color = C.Text2, fontSize = 13.sp)
            Secondary(L.t("الحالة: مقفول", "Status: off"), { }, Modifier.fillMaxWidth(), enabled = false)
        } else if (!App.rootOn) {
            Text(L.t("الموبايل ده عليه Root. لو سمحت لـ Horus Sec بالصلاحية، الفحص العميق هيشوف الاتصالات الشغالة لكل تطبيق ويقارنها بسيرفرات التحكم المعروفة، والبرامج الشغالة، وموديولات Magisk، وسكربتات الإقلاع، وملف hosts، وتحويل الترافيك. وهيقدر يوقّف أو يعطّل أو يقطع النت عن تطبيق خطر بعد موافقتك.",
                "This phone is rooted. If you grant Horus Sec root, the deep scan checks every app's live connections against known command servers, running programs, Magisk modules, boot scripts, the hosts file and traffic redirection. It can also stop, disable or cut the internet of a dangerous app after you confirm."), color = C.Text2, fontSize = 13.sp)
            Text(L.t("الـ Root نفسه بيضعّف حماية أندرويد. Horus Sec بيستخدمه للقراءة، ومش بيغيّر حاجة غير بموافقتك على كل خطوة.", "Root itself weakens Android's protection. Horus Sec uses it to read, and changes nothing without your consent for each step."), color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Secondary(if (App.rootBusy) L.t("مستني موافقة Magisk...", "Waiting for Magisk...") else L.t("فعّل وضع Root", "Turn on root mode"), { App.enableRoot() }, Modifier.fillMaxWidth(), enabled = !App.rootBusy)
        } else {
            Text(L.t("شغال: الفحص العميق والفحص التلقائي بيستخدموه.", "On: deep and automatic scans use it."), color = C.Safe, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Secondary(if (App.rootBusy) L.t("بيجمع...", "Collecting...") else L.t("صدّر سجلات النظام كدليل", "Export system logs as evidence"), { App.exportEvidence(ctx) }, Modifier.fillMaxWidth(), enabled = !App.rootBusy)
            if (App.rootUndo.isNotEmpty()) {
                Text(L.t("اللي Horus Sec عطّله أو قطع عنه النت:", "Apps Horus Sec disabled or cut off:"), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                App.rootUndo.forEach { u ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text((if (u.kind == "block") L.t("نت مقطوع: ", "No internet: ") else L.t("متعطّل: ", "Disabled: ")) + u.pkg, color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        SmallLink(L.t("رجّع", "Restore")) { App.rootUndoAction(u) }
                    }
                }
            }
            SmallLink(L.t("اقفل وضع Root", "Turn off root mode")) { App.disableRoot() }
        }
        if (App.rootMsg.isNotEmpty() && App.screen == Screen.HOME) Text(App.rootMsg, color = C.Gold, fontSize = 13.sp)
    }
}
