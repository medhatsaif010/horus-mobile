package com.horussec.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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

// ------------------------------------------------------------------ HOME
@Composable
fun HomeScreen() {
    Column(Modifier.fillMaxSize()) {
        TopBar(null, null)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val last = App.lastScore
                    if (last != null) ScoreRing(last, 112.dp) else HorusEye(Modifier.size(width = 112.dp, height = 80.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        if (last != null) {
                            App.lastVerdict?.let { v ->
                                Text(L.t("الموبايل مخترق؟", "Is the phone compromised?"), color = C.Muted, fontSize = 13.sp)
                                Text(answerOf(v), color = verdictColor(v), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(L.t("رقم الأمان", "Security score"), color = C.Muted, fontSize = 13.sp)
                            Text(App.result?.grade ?: gradeOf(last), color = scoreColor(last), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(L.t("آخر فحص: ", "Last scan: ") + stamp.format(Date(App.lastScanAt)), color = C.Muted, fontSize = 12.sp, style = Ltr)
                        } else {
                            Text(L.t("موبايلك آمن؟", "Is your phone safe?"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(L.t("فحص في أقل من دقيقة: برامج التجسس، تطبيقات البنوك المزيفة، الصلاحيات الخطيرة والإعدادات.",
                                "A scan in under a minute: stalkerware, fake banking apps, risky permissions and settings."), color = C.Text2, fontSize = 14.sp)
                        }
                    }
                }
            }
            UpdateBanner()
            Primary(L.t("فحص عميق", "Deep scan"), { App.startScan(deep = true) })
            Secondary(L.t("فحص سريع (ثواني)", "Quick scan (seconds)"), { App.startScan(deep = false) }, Modifier.fillMaxWidth())
            if (App.result != null) Secondary(L.t("اعرض نتيجة آخر فحص", "Show the last result"), { App.screen = Screen.RESULT }, Modifier.fillMaxWidth())

            Panel {
                Text(L.t("جالك لينك؟", "Got a link?"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(L.t("قبل ما تفتح لينك من واتساب أو رسالة، افحصه هنا. أو من أي تطبيق: مشاركة ثم Horus Sec.",
                    "Before opening a link from WhatsApp or a message, check it here. Or from any app: Share, then Horus Sec."), color = C.Text2, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Secondary(L.t("افحص لينك", "Check a link"), { App.linkResult = null; App.screen = Screen.LINK }, Modifier.fillMaxWidth())
            }

            AutoScanCard()
            RootCard()
            Panel {
                Text(L.t("للقراءة بس", "Read-only"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(L.t("Horus Sec مش بيمسح ولا بيغيّر حاجة لوحده، ومش بيقرا رسايلك ولا صورك، ومش بيبعت بيانات الموبايل لأي سيرفر. كل تصليح إنت اللي بتعمله بزرار بيفتحلك الإعداد الصح.",
                    "Horus Sec never removes or changes anything on its own, never reads your messages or photos, and never sends phone data to any server. Every fix is yours, through a button that opens the right setting."),
                    color = C.Text2, fontSize = 13.sp)
            }
            val intel = App.intel
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "v" + BuildConfig.VERSION_NAME + "  ·  " + L.t("قاعدة التهديدات: ", "Threat intel: ") + (intel?.date?.ifEmpty { "—" } ?: "…"),
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
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        HorusEye(Modifier.size(width = 150.dp, height = 102.dp).alpha(a))
        Spacer(Modifier.height(24.dp))
        Text(L.t("بيفحص الموبايل...", "Scanning the phone..."), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                val done = i < App.scanStep; val now = i == App.scanStep
                if (now) CircularProgressIndicator(Modifier.size(16.dp), color = C.Gold, strokeWidth = 2.dp)
                else Dot(if (done) C.Green else C.Border, 12.dp)
                Spacer(Modifier.width(12.dp))
                Text(s, color = if (done || now) Color.White else C.Dim, fontSize = 15.sp)
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
        TopBar(L.t("نتيجة الفحص", "Scan result")) { App.screen = Screen.HOME }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                val vc = when (r.verdict) { "compromised" -> C.Red; "suspicious" -> C.Gold; else -> C.Green }
                AccentPanel(vc) {
                    Text(L.t("الموبايل مخترق؟", "Is the phone compromised?"), color = C.Muted, fontSize = 14.sp)
                    Text(answerOf(r.verdict), color = vc, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    val iocs = r.findings.filter { it.ioc }
                    if (iocs.isNotEmpty()) {
                        Text(L.t("علامات الاختراق اللي لقيناها (${iocs.size}):", "Signs of compromise found (${iocs.size}):"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        iocs.take(5).forEach { Text("• ${it.title}", color = sevColor(it.sev), fontSize = 13.sp) }
                    }
                    Text(if (r.deep) L.t("فحص عميق", "Deep scan") else L.t("فحص سريع: الفحص العميق بيدوّر أكتر", "Quick scan: a deep scan looks further"), color = C.Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(r.score, 108.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.grade, color = scoreColor(r.score), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(r.headline, color = vc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(L.t("اتفحص ${r.appCount} تطبيق  ·  ", "${r.appCount} apps checked  ·  ") + stamp.format(Date(r.atMillis)), color = C.Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Secondary(if (r.deep) L.t("افحص تاني", "Scan again") else L.t("فحص عميق", "Deep scan"), { App.startScan(deep = true) }, Modifier.weight(1f))
                    Secondary(L.t("شارك الملخص", "Share summary"), { Actions.shareText(ctx, Actions.summary(r, App.device)) }, Modifier.weight(1f))
                }
            }
            if (r.changes.isNotEmpty()) {
                item { SectionTitle(L.t("اتغيّر من آخر فحص", "Changed since last scan")) }
                item { Panel { r.changes.forEach { Text("• $it", color = Color.White, fontSize = 14.sp) } } }
            }
            item { SectionTitle(if (r.findings.isEmpty()) L.t("مفيش حاجة محتاجة تتصرف فيها", "Nothing needs action") else L.t("محتاج تتصرف فيه (${r.findings.size})", "Needs action (${r.findings.size})")) }
            items(r.findings, key = { it.key }) { f -> FindingCard(f) { fx ->
                when {
                    fx == Fix.UNINSTALL && f.stalker -> confirm = f
                    fx == Fix.ROOT_STOP || fx == Fix.ROOT_DISABLE || fx == Fix.ROOT_BLOCKNET -> rootAsk = f to fx
                    else -> Actions.open(ctx, fx, f.pkg)
                }
            } }
            item { SectionTitle(L.t("إعدادات الأمان", "Security settings")) }
            item { Panel { r.checks.forEach { CheckRow(it) } } }
            item { SectionTitle(L.t("مين يقدر يسمعك ويشوفك", "Who can hear and see you")) }
            item {
                Panel {
                    Text(L.t("التطبيقات اللي إنت مسطّبها ومعاها الصلاحيات دي دلوقتي:", "Apps you installed that hold these permissions now:"), color = C.Text2, fontSize = 13.sp)
                    r.exposure.forEach { e ->
                        Spacer(Modifier.height(6.dp))
                        Text("${e.label} (${e.apps.size})", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (e.apps.isEmpty()) L.t("ولا تطبيق", "None") else e.apps.joinToString("، ".takeIf { L.ar } ?: ", "), color = C.Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Secondary(L.t("افتح مدير الصلاحيات", "Open permission manager"), { openPermissionManager(ctx) }, Modifier.fillMaxWidth())
                }
            }
            if (r.verified.isNotEmpty()) {
                item { SectionTitle(L.t("اتأكدنا من", "Verified")) }
                item { Panel { r.verified.forEach { Row { Text("✓  ", color = C.Green, fontSize = 14.sp); Text(it, color = C.Text2, fontSize = 14.sp) } } } }
            }
            item {
                Text(
                    L.t("التطبيق بيشوف اللي أندرويد بيسمح لأي تطبيق يشوفه، من غير Root. برامج التجسس المتقدمة جدًا ممكن متبانش هنا. لو شاكك جامد، افحص الموبايل كمان من برنامج Horus Sec على الكمبيوتر بالكابل.",
                        "The app sees what Android lets any app see, without root. Very advanced spyware may not show here. If you strongly suspect something, also scan the phone with Horus Sec on a computer over USB."),
                    color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 14.dp),
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
            onDismissRequest = { rootAsk = null },
            containerColor = C.Card, titleContentColor = Color.White, textContentColor = C.Text2,
            title = { Text(Actions.label(fx)) },
            text = { Text(what + "\n\n" + (f.pkg ?: "") + if (f.stalker) L.t("\n\nاللي سطّب برنامج التجسس ممكن يلاحظ.", "\n\nWhoever installed the stalkerware may notice.") else "") },
            confirmButton = { TextButton(onClick = { rootAsk = null; f.pkg?.let { App.rootAction(fx, it) } }) { Text(L.t("نفّذ", "Do it"), color = C.Red) } },
            dismissButton = { TextButton(onClick = { rootAsk = null }) { Text(L.t("إلغاء", "Cancel"), color = C.Gold) } },
        )
    }
    if (App.rootMsg.isNotEmpty() && App.screen == Screen.RESULT) {
        AlertDialog(
            onDismissRequest = { App.rootMsg = "" }, containerColor = C.Card, textContentColor = Color.White,
            text = { Text(App.rootMsg) }, confirmButton = { TextButton(onClick = { App.rootMsg = "" }) { Text("OK", color = C.Gold) } },
        )
    }
    confirm?.let { f ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = C.Card, titleContentColor = Color.White, textContentColor = C.Text2,
            title = { Text(L.t("قبل ما تمسحه", "Before you remove it")) },
            text = { Text(L.t("اللي سطّب برنامج التجسس ده هيعرف إنه اتمسح. لو حاسس إن فيه خطر عليك منه، احفظ الملخص الأول واطلب مساعدة من حد تثق فيه، وبعدين امسحه.",
                "Whoever installed this stalkerware will notice it is gone. If you feel at risk from them, save the summary first and get help from someone you trust, then remove it.")) },
            confirmButton = { TextButton(onClick = { confirm = null; Actions.open(ctx, Fix.UNINSTALL, f.pkg) }) { Text(L.t("فاهم، امسحه", "I understand, remove it"), color = C.Red) } },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindingCard(f: Finding, onFix: (Fix) -> Unit) {
    var open by remember(f.key) { mutableStateOf(f.sev == Sev.CRIT) }
    val col = sevColor(f.sev)
    AccentPanel(col) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.clickable { open = !open }) {
            Box(Modifier.padding(top = 6.dp)) { Dot(col) }
            Spacer(Modifier.width(10.dp))
            Text(f.title, color = col, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Text(f.why, color = C.Text2, fontSize = 14.sp)
        Text(L.t("تعمل إيه: ", "What to do: ") + f.todo, color = Color.White, fontSize = 14.sp)
        if (f.evidence.isNotEmpty()) {
            SmallLink(if (open) L.t("اخفي التفاصيل", "Hide details") else L.t("التفاصيل (${f.evidence.size})", "Details (${f.evidence.size})")) { open = !open }
            if (open) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.NavyDark).padding(10.dp)) {
                f.evidence.forEach { Text(it, color = C.Muted, fontSize = 12.sp) }
            }
        }
        if (f.fixes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                f.fixes.forEach { fx -> Secondary(Actions.label(fx), { onFix(fx) }) }
            }
        }
    }
}

@Composable
fun CheckRow(c: Check) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(checkColor(c.state), 11.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = Color.White, fontSize = 15.sp)
            Text(c.detail + if (c.hint.isNotEmpty() && c.state != com.horussec.mobile.CheckState.PASS) "  ·  ${c.hint}" else "", color = C.Muted, fontSize = 12.sp)
        }
        val fx = c.fix
        if (fx != null && c.state != com.horussec.mobile.CheckState.PASS) SmallLink(L.t("افتح", "Open")) { Actions.open(ctx, fx) }
    }
}

// ------------------------------------------------------------------ LINK
@Composable
fun LinkScreen() {
    var text by remember(App.linkText) { mutableStateOf(App.linkText) }
    val r = App.linkResult
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("افحص لينك", "Check a link")) { App.screen = Screen.HOME }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("الزق اللينك اللي جالك. Horus Sec مش بيفتح الصفحة: بيشوف رايحة فين، ومين صاحبها، وعمرها قد إيه.",
                "Paste the link you received. Horus Sec does not open the page: it checks where it leads, who it pretends to be, and how old it is."), color = C.Text2, fontSize = 14.sp)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://…", color = C.Dim, style = Ltr) }, textStyle = Ltr.copy(color = Color.White, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { App.checkLink(text) }), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Border, cursorColor = C.Gold, focusedContainerColor = C.NavyDark, unfocusedContainerColor = C.NavyDark),
            )
            Primary(if (App.linkBusy) L.t("بيفحص...", "Checking...") else L.t("افحص اللينك", "Check link"), { App.checkLink(text) }, enabled = !App.linkBusy && text.isNotBlank())
            if (App.linkBusy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = C.Gold, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(L.t("بيتبع التحويلات ويسأل عن عمر الدومين...", "Following redirects and looking up the domain age..."), color = C.Muted, fontSize = 13.sp)
            }
            if (r != null && !App.linkBusy) {
                if (!r.ok) Text(L.t("ده مش لينك صحيح. الزق اللينك كامل زي ما جالك.", "That is not a valid link. Paste the whole link as you received it."), color = C.Red, fontSize = 14.sp)
                else {
                    val vc = when (r.verdict) { "danger" -> C.Red; "suspicious" -> C.Gold; else -> C.Green }
                    AccentPanel(vc) {
                        Text(when (r.verdict) {
                            "danger" -> L.t("خطر: متفتحش اللينك ده", "Dangerous: do not open this link")
                            "suspicious" -> L.t("مشبوه: متكتبش فيه أي بيانات", "Suspicious: do not type anything into it")
                            else -> L.t("مفيش علامات خطر معروفة", "No known danger signs")
                        }, color = vc, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(r.finalUrl, color = Color.White, fontSize = 13.sp, style = Ltr)
                        var meta = L.t("الدومين الفعلي: ", "Actual domain: ") + r.domain
                        r.ageDays?.let { meta += "  ·  " + L.t("عمره $it يوم", "$it days old") }
                        if (r.chain.size > 1) meta += "  ·  " + L.t("${r.chain.size - 1} تحويل", "${r.chain.size - 1} redirects")
                        Text(meta, color = C.Muted, fontSize = 12.sp)
                        if (r.verdict == "nosigns") Text(L.t("ده مش ضمان إنه آمن: صفحات النصب الجديدة بتاخد وقت لحد ما تتسجل. لو اللينك طالب باسورد أو بيانات كارت، ادخل الموقع من عنوانه اللي تعرفه مش من اللينك.",
                            "This is not a guarantee: new scam pages take time to be listed. If the link asks for a password or card details, go to the site by the address you know, not through the link."), color = C.Text2, fontSize = 13.sp)
                    }
                    r.signals.forEach { s ->
                        AccentPanel(levelColor(s.level)) { Text(s.text, color = if (s.level == "note") C.Text2 else Color.White, fontSize = 14.sp) }
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
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("عن البرنامج", "About")) { App.screen = Screen.HOME }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorusEye(Modifier.size(width = 64.dp, height = 44.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Horus Sec Mobile", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  ·  " + L.t("نسخة تجريبية", "test build"), color = C.Muted, fontSize = 13.sp)
                }
            }
            Panel {
                Text(L.t("قاعدة التهديدات", "Threat intel"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                Text(L.t("تحديث التطبيق", "App updates"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(L.t("النسخة اللي عندك: ", "Installed version: ") + BuildConfig.VERSION_NAME, color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Secondary(if (App.updateBusy && App.updateProgress < 0) L.t("بيدوّر...", "Checking...") else L.t("دوّر على تحديث", "Check for updates"), { App.checkUpdate() }, Modifier.fillMaxWidth(), enabled = !App.updateBusy)
                if (App.updateMsg.isNotEmpty() && App.update == null) Text(App.updateMsg, color = C.Gold, fontSize = 13.sp)
            }
            UpdateBanner()
            Panel {
                Text(L.t("خصوصيتك", "Your privacy"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(L.t("الفحص بيتم على الموبايل بس. اللي بيخرج من الموبايل: تنزيل قاعدة التهديدات، ولما تفحص لينك بنطلب عنوان الصفحة بس (من غير ما نفتحها) ونسأل rdap.org عن عمر الدومين.",
                    "Scans run on the phone only. What leaves the phone: the threat-intel download, and when you check a link, a header-only request to that link and a domain-age lookup at rdap.org."), color = C.Text2, fontSize = 13.sp)
            }
            Panel {
                Text(L.t("المصادر", "Sources"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Stalkerware indicators: Echap (CC BY 4.0)\nC2 / URLhaus / MalwareBazaar / ThreatFox: abuse.ch (CC0)\nExploited flaws: CISA Known Exploited Vulnerabilities\nDomain age: RDAP via rdap.org", color = C.Muted, fontSize = 12.sp, style = Ltr)
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
                "Android needs you to let Horus Sec install its own updates. Turn on «Allow from this source», come back and tap Update again."), color = Color.White, fontSize = 13.sp)
            Secondary(L.t("افتح الإذن", "Open the permission"), { Updater.openInstallPermission(ctx) }, Modifier.fillMaxWidth())
        }
        val p = App.updateProgress
        Primary(
            when { p >= 0 -> L.t("بينزّل $p%", "Downloading $p%"); else -> L.t("نزّل وحدّث", "Download and update") },
            { App.installUpdate() }, enabled = !App.updateBusy,
        )
        Text(L.t("بنتأكد إن الملف مطابق للنسخة الرسمية قبل التسطيب، وأندرويد هيسألك توافق. إعداداتك ونتايجك مش هتتمسح.",
            "The file is checked against the official build before installing, and Android will ask you to confirm. Your settings and results are kept."), color = C.Dim, fontSize = 12.sp)
        if (App.updateMsg.isNotEmpty()) Text(App.updateMsg, color = C.Red, fontSize = 13.sp)
    }
}

fun answerOf(v: String): String = when (v) {
    "compromised" -> L.t("غالبًا أيوه: فيه علامات اختراق", "Probably yes: signs of compromise")
    "suspicious" -> L.t("مش متأكدين: فيه حاجات محتاجة تتأكد منها", "Not sure: some things need checking")
    else -> L.t("لأ، مفيش علامات اختراق", "No signs of compromise")
}

fun verdictColor(v: String): Color = when (v) { "compromised" -> C.Red; "suspicious" -> C.Gold; else -> C.Green }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoScanCard() {
    val ctx = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> App.notifyAllowed = ok }
    Panel {
        Text(L.t("الفحص التلقائي", "Automatic scan"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(L.t("Horus Sec يعمل فحص عميق لوحده في الخلفية، ويبعتلك تنبيه بس لو ظهرت علامة اختراق أو تطبيق جديد أخد صلاحيات خطيرة.",
            "Horus Sec runs a deep scan on its own in the background, and only alerts you if a sign of compromise appears or a new app gets risky access."), color = C.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoScan.choices.forEach { h ->
                val sel = App.autoHours == h
                val label = if (h == 0) L.t("مقفول", "Off") else L.t("كل $h ساعات", "Every $h h").replace("كل 12 ساعات", "كل 12 ساعة").replace("كل 24 ساعات", "كل يوم")
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(if (sel) C.Gold else C.NavyDark)
                        .clickable {
                            App.setAuto(h)
                            if (h > 0 && android.os.Build.VERSION.SDK_INT >= 33 && !AutoScan.notificationsAllowed(ctx)) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label, color = if (sel) C.Navy else Color.White, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
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
        Text(L.t("استهلاك البيانات", "Data usage"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (App.usageAccess) Text(L.t("✓ مسموح: الفحص العميق بيشوف التطبيقات المخفية اللي بتبعت بيانات كتير.", "✓ Allowed: the deep scan spots hidden apps sending lots of data."), color = C.Green, fontSize = 13.sp)
        else {
            Text(L.t("اسمح بـ Usage access علشان الفحص العميق يكشف التطبيقات اللي بتبعت صورك وتسجيلاتك لبرّه. Horus Sec بيشوف الأرقام بس، مش المحتوى.",
                "Allow Usage access so the deep scan can spot apps sending your photos and recordings out. Horus Sec sees only the numbers, never the content."), color = C.Text2, fontSize = 13.sp)
            SmallLink(L.t("اسمح بـ Usage access", "Allow Usage access")) { com.horussec.mobile.DeepScan.openUsageAccess(ctx) }
        }
    }
}

@Composable
fun RootCard() {
    if (!App.rootDetected && !App.rootOn) return
    val ctx = LocalContext.current
    AccentPanel(if (App.rootOn) C.Gold else C.Border) {
        Text(L.t("وضع Root (للخبراء)", "Root mode (experts)"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (!App.rootOn) {
            Text(L.t("الموبايل ده عليه Root. لو سمحت لـ Horus Sec بالصلاحية، الفحص العميق هيشوف الاتصالات الشغالة لكل تطبيق ويقارنها بسيرفرات التحكم المعروفة، والبرامج الشغالة، وموديولات Magisk، وسكربتات الإقلاع، وملف hosts، وتحويل الترافيك. وهيقدر يوقّف أو يعطّل أو يقطع النت عن تطبيق خطر بعد موافقتك.",
                "This phone is rooted. If you grant Horus Sec root, the deep scan checks every app's live connections against known command servers, running programs, Magisk modules, boot scripts, the hosts file and traffic redirection. It can also stop, disable or cut the internet of a dangerous app after you confirm."), color = C.Text2, fontSize = 13.sp)
            Text(L.t("الـ Root نفسه بيضعّف حماية أندرويد. Horus Sec بيستخدمه للقراءة، ومش بيغيّر حاجة غير بموافقتك على كل خطوة.", "Root itself weakens Android's protection. Horus Sec uses it to read, and changes nothing without your consent for each step."), color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Secondary(if (App.rootBusy) L.t("مستني موافقة Magisk...", "Waiting for Magisk...") else L.t("فعّل وضع Root", "Turn on root mode"), { App.enableRoot() }, Modifier.fillMaxWidth(), enabled = !App.rootBusy)
        } else {
            Text(L.t("✓ شغال: الفحص العميق والفحص التلقائي بيستخدموه.", "✓ On: deep and automatic scans use it."), color = C.Green, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Secondary(if (App.rootBusy) L.t("بيجمع...", "Collecting...") else L.t("صدّر سجلات النظام كدليل", "Export system logs as evidence"), { App.exportEvidence(ctx) }, Modifier.fillMaxWidth(), enabled = !App.rootBusy)
            if (App.rootUndo.isNotEmpty()) {
                Text(L.t("اللي Horus Sec عطّله أو قطع عنه النت:", "Apps Horus Sec disabled or cut off:"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
