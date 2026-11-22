/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horusict.shield.Actions
import com.horusict.shield.App
import com.horusict.shield.EgApps
import com.horusict.shield.Fix
import com.horusict.shield.L
import com.horusict.shield.Prefs
import com.horusict.shield.R
import com.horusict.shield.ScamReports
import com.horusict.shield.Screen
import com.horusict.shield.Sev
import com.horusict.shield.Studio
import com.horusict.shield.Trackers
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dial(ctx: Context, number: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

private fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

private fun openApp(ctx: Context, pkg: String): Boolean {
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
}

private fun Context.activity(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

/** Scrolling page body under a top bar, used by all the screens below. */
@Composable
private fun Page(title: String, back: Screen = App.back, extra: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Backdrop {
        TopBar(title, { App.screen = back }, extra)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Intro(text: String) = Text(text, color = C.Text2, fontSize = 14.sp)

@Composable
private fun Busy(text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(Modifier.size(18.dp), color = C.Teal, strokeWidth = 2.dp)
    Spacer(Modifier.width(10.dp))
    Text(text, color = C.Muted, fontSize = 13.sp)
}

/** A numbered step card. */
@Composable
private fun Step(n: Int, title: String, body: String, action: (@Composable ColumnScope.() -> Unit)? = null) {
    HCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(n.toString(), color = C.Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(body, color = C.Text2, fontSize = 13.sp)
                action?.invoke(this)
            }
        }
    }
}

// ------------------------------------------------------------------ 1. BANK APPS
@Composable
fun BankAppsScreen() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { App.loadBankApps() }
    val list = App.bankApps
    Page(L.t("تطبيقات البنوك والمحافظ", "تطبيقات البنوك والمحافظ", "Bank and wallet apps")) {
        Intro(L.t("يتحقق Horus Shield من أن تطبيقات البنوك والمحافظ المصرية وواتساب على هاتفك هي النسخ الرسمية: من أين ثُبّتت، وهل تغيّر توقيعها منذ آخر مرة.",
            "Horus Shield بيتأكد إن تطبيقات البنوك والمحافظ المصرية وواتساب اللي على موبايلك هي النسخ الرسمية: اتسطبت منين، وتوقيعها اتغيّر من آخر مرة ولا لأ.",
            "Horus Shield checks that the Egyptian bank and wallet apps and WhatsApp on your phone are the official versions: where they were installed from, and whether their signature changed since last time."))
        when {
            list == null -> Busy(L.t("جارٍ القراءة...", "بيقرا...", "Reading..."))
            list.isEmpty() -> Panel { Text(L.t("لا يوجد على الهاتف أي تطبيق بنك أو محفظة من القائمة.", "مفيش على الموبايل أي تطبيق بنك أو محفظة من القايمة.", "None of the listed bank or wallet apps is on this phone."), color = C.Text2, fontSize = 14.sp) }
            else -> HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                list.forEachIndexed { i, f ->
                    val (txt, col) = when (f.status) {
                        EgApps.Status.STORE -> L.t("رسمي · من المتجر", "رسمي · من المتجر", "Official · from the store") to C.Safe
                        EgApps.Status.OUTSIDE -> L.t("من خارج المتجر · لا يمكن التأكد منه", "من برّه المتجر · مش هنقدر نتأكد منه", "From outside a store · cannot be confirmed") to C.Warn
                        EgApps.Status.SIG_CHANGED -> L.t("التوقيع تغيّر · ليس النسخة الرسمية", "التوقيع اتغيّر · مش النسخة الرسمية", "Signature changed · not the official copy") to C.Danger
                    }
                    ListRow(f.brand, "${f.app.label} · $txt", subColor = col, last = i == list.lastIndex,
                        onClick = if (f.status == EgApps.Status.STORE) null else ({ Actions.open(ctx, Fix.APP_SETTINGS, f.app.pkg) })) {
                        Dot(col)
                    }
                }
            }
        }
        Text(L.t("يتعرّف أيضًا على التطبيقات المزيفة التي تحمل أسماء هذه البنوك بالعربي أو بالإنجليزي. وتظهر نتيجتها في الفحص.", "وكمان بيكشف التطبيقات المزيفة اللي شايلة أسماء البنوك دي بالعربي أو بالإنجليزي، ونتيجتها بتظهر في الفحص.",
            "It also spots fake apps that carry these banks' names in Arabic or English. Those show up in the scan."), color = C.Muted, fontSize = 13.sp)
        Primary(L.t("فحص الجهاز", "افحص الجهاز", "Scan the phone"), { App.startScan(deep = true) })
        SectionTitle(L.t("التطبيقات التي يعرفها", "التطبيقات اللي يعرفها", "Apps it knows"))
        Text(EgApps.brands.joinToString(" · ") { L.t(it.nameAr, it.nameAr, it.name) } + " · WhatsApp", color = C.Muted, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------ 2. REPAIR CHECK
@Composable
fun RepairScreen() {
    val changes = App.repairChanges
    Page(L.t("قبل الصيانة وبعدها", "قبل التصليح وبعده", "Before and after repair")) {
        Intro(L.t("قبل أن تسلّم هاتفك لمحل صيانة، احفظ حالته. وعندما تستلمه، قارن لتعرف بالضبط ما الذي تغيّر: تطبيقات جديدة أو مخفية، أو صلاحيات، أو إعدادات، أو نظام أُعيد تثبيته.",
            "قبل ما تسلّم موبايلك لمحل تصليح، احفظ حالته. ولما تستلمه، قارن وهتعرف بالظبط إيه اللي اتغيّر: تطبيقات جديدة أو مخفية، صلاحيات، إعدادات، أو سوفت وير اتعاد تسطيبه.",
            "Before you hand your phone in for repair, save its state. When you get it back, compare to see exactly what changed: new or hidden apps, permissions, settings, or a reinstalled system."))
        if (App.repairBusy) Busy(L.t("جارٍ قراءة الهاتف...", "بيقرا الموبايل...", "Reading the phone..."))
        if (App.repairSavedAt == 0L) {
            Step(1, L.t("قبل تسليم الهاتف", "قبل ما تسلّم الموبايل", "Before you hand it in"), L.t("اضغط حفظ. تبقى الحالة على هاتفك فقط.", "دوس حفظ. الحالة بتتحفظ على موبايلك بس.", "Tap Save. The state stays on your phone only."))
            Step(2, L.t("بعد الاستلام", "بعد ما تستلمه", "When you get it back"), L.t("افتح هذه الشاشة واضغط قارن قبل أن تُدخل أي كلمة مرور.", "افتح الشاشة دي ودوس قارن قبل ما تكتب أي باسورد.", "Open this screen and tap Compare before you type any password."))
            Primary(L.t("احفظ حالة الهاتف الآن", "احفظ حالة الموبايل دلوقتي", "Save the phone's state now"), { App.repairSave() }, enabled = !App.repairBusy)
        } else {
            Panel {
                Text(L.t("حُفظت الحالة", "الحالة متحفظة", "State saved"), color = C.Safe, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stamp.format(Date(App.repairSavedAt)), color = C.Muted, fontSize = 13.sp)
            }
            Primary(L.t("قارن الآن", "قارن دلوقتي", "Compare now"), { App.repairCompare() }, enabled = !App.repairBusy)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Secondary(L.t("احفظ حالة جديدة", "احفظ حالة جديدة", "Save a new state"), { App.repairSave() }, Modifier.weight(1f), enabled = !App.repairBusy)
                Secondary(L.t("احذف الحالة", "امسح الحالة", "Delete the state"), { App.repairClear() }, Modifier.weight(1f), enabled = !App.repairBusy)
            }
        }
        if (changes != null && !App.repairBusy) {
            val serious = changes.count { it.sev == Sev.CRIT || it.sev == Sev.HIGH }
            AccentPanel(if (serious > 0) C.Danger else if (changes.isEmpty()) C.Safe else C.Warn) {
                Text(when {
                    changes.isEmpty() -> L.t("لم يتغير شيء", "مفيش حاجة اتغيّرت", "Nothing changed")
                    serious > 0 -> L.t("$serious تغييرات تحتاج انتباهك", "$serious تغييرات محتاجة انتباهك", "$serious changes need your attention")
                    else -> L.t("تغييرات بسيطة فقط", "تغييرات بسيطة بس", "Only minor changes")
                }, color = if (serious > 0) C.Danger else if (changes.isEmpty()) C.Safe else C.Warn, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                if (serious > 0) Text(L.t("لا تُدخل كلمات المرور أو بيانات البنك قبل حذف ما لا تعرفه. وإن وجدت تطبيقًا مخفيًا جديدًا، فشغّل فحصًا كاملًا.", "متكتبش باسوردات أو بيانات بنك قبل ما تمسح اللي مش عارفه. ولو لقيت تطبيق مخفي جديد، اعمل فحص كامل.",
                    "Do not type passwords or bank details until you remove what you do not recognise. If there is a new hidden app, run a full scan."), color = C.Text2, fontSize = 13.sp)
            }
            val ctx = LocalContext.current
            changes.forEach { c ->
                HCard(Modifier.fillMaxWidth(), onClick = c.pkg?.let { p -> { Actions.open(ctx, Fix.APP_SETTINGS, p) } }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Dot(sevColor(c.sev)); Spacer(Modifier.width(10.dp))
                        Text(c.text, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (serious > 0) Primary(L.t("فحص كامل", "فحص كامل", "Full scan"), { App.startScan(deep = true) })
        }
    }
}

@Composable
private fun Tick(on: Boolean, onClick: () -> Unit) = ActionText(if (on) L.t("✓ تم", "✓ تمام", "✓ Done") else L.t("علّم أنه تم", "علّم إنه اتعمل", "Mark as done"), if (on) C.Safe else C.GoldLight, onClick)

// ------------------------------------------------------------------ 3. WHATSAPP AND CALLS
@Composable
fun WhatsAppScreen() {
    val ctx = LocalContext.current
    var done by remember { mutableStateOf((Prefs.str("waDone") ?: "").split(',').filter { it.isNotEmpty() }.toSet()) }
    fun tick(k: String) { done = if (k in done) done - k else done + k; Prefs.put("waDone", done.joinToString(",")) }
    Page(L.t("سرقة واتساب وتحويل المكالمات", "سرقة واتساب وتحويل المكالمات", "WhatsApp theft and call forwarding")) {
        Intro(L.t("أشهر طريقة لسرقة واتساب في مصر: يحوّل المحتال مكالماتك إلى رقمه، ثم يطلب رمز واتساب بمكالمة صوتية فيصله هو. هذه الخطوات تكشف ذلك وتمنعه.",
            "أشهر طريقة لسرقة واتساب في مصر: النصاب بيحوّل مكالماتك على رقمه، وبعدين يطلب كود واتساب بمكالمة صوتية فيوصله هو. الخطوات دي بتكشف ده وبتمنعه.",
            "The most common way WhatsApp accounts are stolen: the scammer forwards your calls to their number, then asks WhatsApp to send your code by voice call, which reaches them. These steps find and stop that."))
        SectionTitle(L.t("تحويل المكالمات", "تحويل المكالمات", "Call forwarding"))
        Step(1, L.t("افحص التحويل", "اكشف التحويل", "Check forwarding"),
            L.t("يفتح الاتصال بالرمز *#21#، فاضغط اتصال. يجب أن يكون الرد أن التحويل غير مفعّل. ثم جرّب *#67# و*#61# و*#62# (عند الانشغال، عدم الرد، عدم الوصول).", "هيفتح الاتصال بالكود *#21#، دوس اتصال. لازم الرد يقول إن التحويل مش مفعّل. وبعدين جرّب *#67# و*#61# و*#62# (لما تكون مشغول، أو مبتردّش، أو مقفول).",
                "Opens the dialer with *#21#: press call. The answer should say forwarding is not active. Then try *#67#, *#61# and *#62# (when busy, unanswered, unreachable).")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("*#21#", "*#67#", "*#61#", "*#62#").forEach { c -> Choice(c, false) { dial(ctx, c) } }
            }
        }
        Step(2, L.t("ألغِ أي تحويل", "الغِ أي تحويل", "Cancel all forwarding"), L.t("إن وجدت تحويلًا لم تضبطه أنت، فاتصل بالرمز ##002# لإلغاء كل أنواع التحويل.", "لو لقيت تحويل إنت مش عامله، اتصل بالكود ##002# يلغي كل أنواع التحويل.", "If you find forwarding you did not set, dial ##002# to cancel every kind of forwarding.")) {
            Choice("##002#", false) { dial(ctx, "##002#") }
        }
        SectionTitle("WhatsApp")
        Step(3, L.t("الأجهزة المرتبطة", "الأجهزة المربوطة", "Linked devices"),
            L.t("واتساب › الإعدادات › الأجهزة المرتبطة. إن رأيت جهازًا أو متصفحًا لا تعرفه، فاضغط عليه ثم سجّل الخروج. هذه أسهل طريقة لقراءة رسائلك من كمبيوتر آخر.", "واتساب › الإعدادات › الأجهزة المربوطة. لو شفت جهاز أو متصفح مش عارفه، دوس عليه واعمل تسجيل خروج. دي أسهل طريقة حد يقرا رسايلك من كمبيوتر تاني.",
                "WhatsApp › Settings › Linked devices. If you see a device or browser you do not know, tap it and log out. It is the easiest way for someone to read your messages from another computer.")) {
            Row { ActionText(L.t("افتح واتساب", "افتح واتساب", "Open WhatsApp")) { if (!openApp(ctx, "com.whatsapp")) openApp(ctx, "com.whatsapp.w4b") }; Spacer(Modifier.width(12.dp)); Tick("linked" in done) { tick("linked") } }
        }
        Step(4, L.t("التحقق بخطوتين", "التحقق بخطوتين", "Two-step verification"),
            L.t("واتساب › الإعدادات › الحساب › التحقق بخطوتين. ضع رقمًا سريًا من 6 أرقام وبريدًا إلكترونيًا. فحتى لو حصل أحد على الرمز، لن يستطيع الدخول دون هذا الرقم.", "واتساب › الإعدادات › الحساب › التحقق بخطوتين. حط رقم سري من 6 أرقام وإيميل. حتى لو حد خد الكود، مش هيعرف يدخل من غير الرقم ده.",
                "WhatsApp › Settings › Account › Two-step verification. Set a 6-digit PIN and an email. Even if someone gets your code, they cannot get in without it.")) { Tick("2fa" in done) { tick("2fa") } }
        Step(5, L.t("لا تعطِ الرمز لأحد", "متدّيش الكود لحد", "Never share the code"),
            L.t("لا يطلب واتساب ولا البنك ولا شركة الاتصالات رمز التحقق منك أبدًا. ومن يطلبه، حتى لو كان صديقًا، فهو غالبًا حساب مسروق.", "لا واتساب ولا البنك ولا شركة المحمول بيطلبوا منك كود التحقق أبدًا. اللي يطلبه، حتى لو صاحبك، غالبًا حسابه متسرق.",
                "WhatsApp, banks and phone companies never ask you for the code. Anyone who does, even a friend, is usually a stolen account.")) { Tick("code" in done) { tick("code") } }
        Step(6, L.t("نسخة واتساب الرسمية", "نسخة واتساب الرسمية", "The official WhatsApp"),
            L.t("النسخ المعدّلة مثل GB وواتساب بلس خطر على حسابك. يكشفها الفحص، وتظهر حالتها في شاشة تطبيقات البنوك والمحافظ.", "النسخ المعدّلة زي GB وواتساب بلس خطر على حسابك. الفحص بيكشفها، وحالتها بتظهر في شاشة تطبيقات البنوك والمحافظ.",
                "Modified builds such as GB and WhatsApp Plus put your account at risk. The scan finds them, and their status shows on the Bank and wallet apps screen.")) {
            ActionText(L.t("تطبيقات البنوك والمحافظ", "تطبيقات البنوك والمحافظ", "Bank and wallet apps")) { App.go(Screen.BANKAPPS) }
        }
        Text(L.t("رموز التحويل معيارية وتعمل على فودافون وأورنج واتصالات (e&) ووي. وإن لم يرد الرمز، فاتصل بخدمة عملاء شركتك.", "أكواد التحويل معيارية وبتشتغل على فودافون وأورنج واتصالات (e&) ووي. لو الكود مردّش، كلّم خدمة عملاء شركتك.",
            "The forwarding codes are standard and work on Vodafone, Orange, e& and WE. If a code does not answer, call your operator."), color = C.Muted, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------------ 4. TRACKERS
@Composable
fun TrackersScreen() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.all { it }) Trackers.start(ctx) else Trackers.problem = "perm" }
    DisposableEffect(Unit) { onDispose { Trackers.stop(ctx) } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Trackers.scanning) { while (Trackers.scanning) { now = System.currentTimeMillis(); delay(1000) } }
    Page(L.t("أجهزة التتبع بالبلوتوث", "أجهزة التتبع بالبلوتوث", "Bluetooth trackers")) {
        Intro(L.t("يبحث عن أجهزة التتبع الصغيرة القريبة منك، مثل AirTag أو SmartTag أو Tile، والتي قد تُخفى في حقيبة أو سيارة لمعرفة مكانك. يستمع فقط، ولا يتصل بأي جهاز.",
            "بيدوّر على أجهزة التتبع الصغيرة اللي جنبك، زي AirTag أو SmartTag أو Tile، اللي ممكن تتخبّى في شنطة أو عربية علشان حد يعرف مكانك. بيسمع بس، ومش بيتصل بأي جهاز.",
            "Looks for small trackers near you, such as AirTag, Samsung SmartTag and Tile, that can be hidden in a bag or car to follow where you go. It only listens and never connects to anything."))
        if (Trackers.scanning) {
            Busy(L.t("يبحث الآن... تحرّك ببطء حول حقيبتك أو سيارتك", "بيدوّر دلوقتي... اتحرك براحة حوالين شنطتك أو عربيتك", "Searching... move slowly around your bag or car") + "  ·  ${((now - Trackers.startedAt) / 1000).coerceAtLeast(0)}s")
            Secondary(L.t("إيقاف", "وقّف", "Stop"), { Trackers.stop(ctx) }, Modifier.fillMaxWidth())
        } else Primary(L.t("ابدأ البحث", "ابدأ الدوّير", "Start searching"), {
            if (Trackers.hasPermissions(ctx)) Trackers.start(ctx) else launcher.launch(Trackers.permissions())
        })
        Trackers.problem?.let { p ->
            AccentPanel(C.Warn) {
                Text(when (p) {
                    "nobt" -> L.t("هذا الهاتف لا يدعم Bluetooth Low Energy.", "الموبايل ده مش بيدعم Bluetooth Low Energy.", "This phone does not support Bluetooth Low Energy.")
                    "off" -> L.t("شغّل البلوتوث ثم اضغط ابدأ البحث.", "شغّل البلوتوث ودوس ابدأ الدوّير.", "Turn on Bluetooth, then tap Start searching.")
                    "location" -> L.t("شغّل خدمة الموقع. يشترطها أندرويد للبحث عن أجهزة البلوتوث، ولا يستخدم Horus Shield موقعك.", "شغّل الموقع (Location). أندرويد بيطلبه للدوّير على أجهزة البلوتوث، وHorus Shield مش بيستخدم موقعك.", "Turn on Location. Android requires it to search for Bluetooth devices; Horus Shield does not use your location.")
                    "perm" -> L.t("يلزم إذن الأجهزة القريبة والموقع. يشترطهما أندرويد للبحث، ولا يُحفظ موقعك ولا يُرسل.", "محتاج إذن الأجهزة القريبة والموقع. أندرويد بيطلبهم للدوّير، وموقعك مش بيتحفظ ولا بيتبعت.", "Nearby devices and location permission are needed. Android requires them to search; your location is never saved or sent.")
                    else -> L.t("تعذّر بدء البحث. أوقف البلوتوث وشغّله ثم حاول مرة أخرى.", "مقدرناش نبدأ الدوّير. اقفل البلوتوث وافتحه وجرّب تاني.", "Could not start searching. Turn Bluetooth off and on, then try again.")
                }, color = C.Text, fontSize = 14.sp)
            }
        }
        val list = Trackers.seen.sortedByDescending { it.rssi }
        if (list.isNotEmpty()) {
            SectionTitle(L.t("أجهزة قريبة (${list.size})", "أجهزة قريبة (${list.size})", "Nearby trackers (${list.size})"))
            list.forEach { s ->
                val mins = ((s.last - s.first) / 60_000L)
                HCard(Modifier.fillMaxWidth(), border = if (s.rssi >= -70) C.Warn.copy(alpha = 0.7f) else null) {
                    Text(Trackers.kindName(s.kind), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(Trackers.distance(s.rssi) + "  ·  ${s.rssi} dBm", color = if (s.rssi >= -70) C.Warn else C.Muted, fontSize = 13.sp)
                    Text(L.t("ظاهر منذ $mins دقيقة", "ظاهر من $mins دقيقة", "Seen for $mins min") + "  ·  " + s.addr, color = C.Dim, fontSize = 12.sp, style = Ltr)
                }
            }
        } else if (!Trackers.scanning && Trackers.startedAt > 0 && Trackers.problem == null) {
            AccentPanel(C.Safe) { Text(L.t("لم يُعثر على أي جهاز تتبع قريب.", "ملقيناش أي جهاز تتبع قريب.", "No trackers found nearby."), color = C.Safe, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        SectionTitle(L.t("كيف تقرأ النتيجة", "تقرا النتيجة إزاي", "How to read the result"))
        Text(L.t("ظهور جهاز من شبكة Apple Find My طبيعي إن كان بجانبك شخص لديه آيفون أو سماعات AirPods. لكن إن ظهر الجهاز نفسه وبقي قريبًا جدًا منك في أماكن مختلفة، أو بقي معك بعد ابتعادك عن الناس، فابحث في حقيبتك وملابسك وسيارتك: اقترب حتى تصبح الإشارة «قريب جدًا منك».",
            "ظهور جهاز من شبكة Apple Find My طبيعي لو جنبك حد معاه آيفون أو سماعات AirPods. لكن لو نفس الجهاز ظهر وفضل قريب منك جدًا في أماكن مختلفة، أو فضل معاك بعد ما بعدت عن الناس، دوّر في شنطتك وهدومك وعربيتك: قرّب لحد ما الإشارة تبقى «قريب منك جدًا».",
            "An Apple Find My device is normal if someone near you has an iPhone or AirPods. But if the same device stays very close to you in different places, or stays with you after you move away from people, search your bag, clothes and car: move until the signal says \"Very close to you\"."), color = C.Text2, fontSize = 13.sp)
        Text(L.t("إن وجدت جهازًا لا تعرفه، فلا ترمه فورًا إن شعرت بخطر: صوّره، واذهب إلى مكان آمن، واطلب المساعدة.", "لو لقيت جهاز مش بتاعك، متترموش على طول لو حاسس بخطر: صوّره، وروح مكان آمن، واطلب مساعدة.", "If you find one that is not yours and you feel at risk, do not throw it away straight away: photograph it, go somewhere safe, and get help."), color = C.Text2, fontSize = 13.sp)
        ActionText(L.t("مساعدة: أحدهم يراقبني", "مساعدة: حد بيراقبني", "Help: someone is watching me")) { App.go(Screen.HELP) }
    }
}

// ------------------------------------------------------------------ 5. HELP
@Composable
fun HelpScreen() {
    val ctx = LocalContext.current
    Page(L.t("أحدهم يراقبني", "حد بيراقبني", "Someone is watching me"), extra = {
        SmallLink(L.t("خروج سريع", "خروج سريع", "Quick exit"), C.Danger) {
            App.screen = Screen.HOME
            ctx.activity()?.finishAndRemoveTask()
            try { ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        }
    }) {
        AccentPanel(C.Danger) {
            Text(L.t("إن كنت في خطر الآن، فاتصل بالشرطة على 122.", "لو إنت في خطر دلوقتي، كلّم الشرطة على 122.", "If you are in danger right now, call the police on 122."), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            ActionText(L.t("اتصل 122", "اتصل 122", "Call 122"), C.Danger) { dial(ctx, "122") }
        }
        Intro(L.t("هذه الخطوات مكتوبة لمن يشك في أن شخصًا قريبًا منه، كزوج أو قريب أو زميل، يراقب هاتفه. خذ وقتك، ولا تفعل شيئًا يعرّضك للخطر.", "الخطوات دي معمولة لحد شاكك إن شخص قريب منه، زي جوز أو قريب أو زميل، بيراقب موبايله. خد وقتك، ومتعملش حاجة تعرّضك للخطر.",
            "These steps are for anyone who suspects that someone close to them, such as a partner, relative or colleague, is watching their phone. Take your time, and do nothing that puts you at risk."))
        Step(1, L.t("لا تواجه ولا تحذف فورًا", "متواجهش ومتمسحش على طول", "Do not confront or delete straight away"),
            L.t("حذف برنامج التجسس يُعلم من ثبّته، وقد يزيد الخطر عليك. خطّط أولًا، واستخدم جهازًا آخر تثق به للتواصل.", "مسح برنامج التجسس بيعرّف اللي سطّبه، وممكن يزوّد الخطر عليك. خطّط الأول، واستخدم جهاز تاني بتثق فيه للتواصل.",
                "Removing spyware alerts whoever installed it and may raise the risk to you. Plan first, and use another device you trust to reach out."))
        Step(2, L.t("احفظ الدليل", "احفظ الدليل", "Save the evidence"),
            L.t("شغّل فحصًا كاملًا، ثم شارك الملخص مع نفسك على بريد لا يعرفه أحد، أو صوّر الشاشة. يحتوي على اسم البرنامج وتاريخ تثبيته ومصدره.", "اعمل فحص كامل، وبعدين ابعت الملخص لنفسك على إيميل محدش يعرفه، أو صوّر الشاشة. فيه اسم البرنامج وتاريخ تسطيبه ومصدره.",
                "Run a full scan, then share the summary to an email nobody else knows, or take screenshots. It holds the program's name, install date and source.")) {
            val r = App.result
            ActionText(if (r != null) L.t("شارك ملخص آخر فحص", "شارك ملخص آخر فحص", "Share the last scan summary") else L.t("ابدأ فحصًا كاملًا", "ابدأ فحص كامل", "Start a full scan")) {
                if (r != null) Actions.shareText(ctx, Actions.summary(r, App.device)) else App.startScan(deep = true)
            }
        }
        Step(3, L.t("أمّن حساباتك من جهاز آخر", "أمّن حساباتك من جهاز تاني", "Secure your accounts from another device"),
            L.t("غيّر كلمة مرور Google وفيسبوك، وراجع الأجهزة المسجّلة في حسابك، وأوقف مشاركة الموقع في خرائط Google، وفعّل التحقق بخطوتين في واتساب.", "غيّر باسورد Google وفيسبوك، وراجع الأجهزة المتسجلة على حسابك، واقفل مشاركة الموقع في خرائط Google، وفعّل التحقق بخطوتين في واتساب.",
                "Change your Google and Facebook passwords, review the devices signed in to your account, turn off location sharing in Google Maps, and turn on WhatsApp two-step verification.")) {
            ActionText(L.t("أجهزة حساب Google", "أجهزة حساب Google", "Google account devices")) { openUrl(ctx, "https://myaccount.google.com/device-activity") }
            ActionText(L.t("واتساب وتحويل المكالمات", "واتساب وتحويل المكالمات", "WhatsApp and call forwarding")) { App.go(Screen.WHATSAPP) }
        }
        Step(4, L.t("ابحث عن أجهزة تتبع", "دوّر على أجهزة تتبع", "Look for trackers"), L.t("قد يُخفى AirTag أو جهاز تتبع في الحقيبة أو السيارة.", "ممكن يتخبّى AirTag أو جهاز تتبع في الشنطة أو العربية.", "An AirTag or tracker may be hidden in a bag or car.")) {
            ActionText(L.t("ابدأ البحث", "ابدأ الدوّير", "Start searching")) { App.go(Screen.TRACKERS) }
        }
        Step(5, L.t("اطلب المساعدة", "اطلب مساعدة", "Get help"), L.t("هذه الجهات تتلقى البلاغات بسرية:", "الجهات دي بتستقبل البلاغات بسرية:", "These services take reports confidentially:")) {
            val lines = Studio.helplines.ifEmpty {
                listOf(
                    Studio.Helpline("15115", "المجلس القومي للمرأة", "المجلس القومي للمرأة", "National Council for Women"),
                    Studio.Helpline("108", "الإبلاغ عن جرائم الإنترنت", "الإبلاغ عن جرائم الإنترنت", "Report internet crime"),
                    Studio.Helpline("16000", "خط نجدة الطفل (لمن هم دون 18)", "خط نجدة الطفل (لأقل من 18 سنة)", "Child helpline (under 18)"),
                )
            }
            lines.forEach { h -> ActionText(L.t(h.msa, h.eg, h.en) + ": " + h.number) { dial(ctx, h.number) } }
        }
        Step(6, L.t("بعد أن تصبح في أمان", "بعد ما تبقى في أمان", "Once you are safe"),
            L.t("احذف البرنامج، أو أعد ضبط المصنع إن لم تستطع حذفه، ثم غيّر كلمات المرور مرة أخرى.", "امسح البرنامج، أو اعمل Factory reset لو مش عارف تمسحه، وبعدين غيّر الباسوردات تاني.", "Remove the program, or factory reset if you cannot, then change your passwords again."))
        Text(L.t("لا يرسل Horus Shield أي شيء عنك إلى أي جهة. ولتقليل لفت الانتباه، يمكنك إيقاف إشعاراته.", "Horus Shield مش بيبعت أي حاجة عنك لأي حد. ولو عايز تقلّل لفت الانتباه، تقدر تقفل إشعاراته.", "Horus Shield sends nothing about you to anyone. To draw less attention, you can turn off its notifications."), color = C.Muted, fontSize = 12.sp)
        ActionText(L.t("إيقاف إشعارات Horus Shield", "اقفل إشعارات Horus Shield", "Turn off Horus Shield notifications")) {
            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        }
    }
}

// ------------------------------------------------------------------ 6. SCAM REPORTS
@Composable
fun ScamReportScreen() {
    var kind by rememberSaveable { mutableStateOf("link") }
    var value by rememberSaveable { mutableStateOf("") }
    var channel by rememberSaveable { mutableStateOf("whatsapp") }
    var err by remember { mutableStateOf("") }
    val mine = remember(App.reportsVersion) { ScamReports.list().reversed() }
    LaunchedEffect(Unit) { App.ensureIntel() }
    Page(L.t("أبلغ عن احتيال", "بلّغ عن نصب", "Report a scam")) {
        Intro(L.t("وصلك رابط أو رقم احتيال؟ أبلغ عنه. تراجع Horus ICT البلاغات، وتضيف المؤكد منها إلى قاعدة التهديدات فيُحمى بها كل المستخدمين.", "جالك لينك أو رقم نصب؟ بلّغ عنه. Horus ICT بتراجع البلاغات، واللي يتأكد بيتضاف لقاعدة التهديدات فيحمي كل المستخدمين.",
            "Got a scam link or number? Report it. Horus ICT reviews reports and adds confirmed ones to the threat intel, so everyone is protected."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(L.t("رابط", "لينك", "Link"), kind == "link") { kind = "link"; err = "" }
            Choice(L.t("رقم هاتف", "رقم تليفون", "Phone number"), kind == "phone") { kind = "phone"; err = "" }
        }
        OutlinedTextField(
            value = value, onValueChange = { value = it.take(2048); err = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 3,
            placeholder = { Text(if (kind == "link") "https://…" else "01xxxxxxxxx", color = C.Dim, style = Ltr) }, textStyle = Ltr.copy(color = C.Text, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (kind == "link") KeyboardType.Uri else KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line, cursorColor = C.Gold, focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface),
        )
        val known = kind == "phone" && ScamReports.normalize("phone", value)?.let { ScamReports.canonPhone(it) in (App.intel?.communityPhones ?: emptySet()) } == true
        if (known) Text(L.t("هذا الرقم أبلغ عنه مستخدمون آخرون، وتحقق منه فريق Horus: إنه احتيال. لا ترد ولا ترسل له أي أكواد.", "الرقم ده ناس تانية بلّغت عنه، وفريق Horus اتأكد إنه نصب. متردش عليه ومتبعتلوش أي أكواد.", "Other users reported this number and the Horus team confirmed it is a scam. Do not answer it or send it any codes."), color = C.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(L.t("أين وصلك؟", "جالك منين؟", "Where did it reach you?"), color = C.Muted, fontSize = 13.sp)
        val channels = listOf("whatsapp" to "WhatsApp", "sms" to L.t("رسالة SMS", "رسالة SMS", "SMS"), "call" to L.t("مكالمة", "مكالمة", "Call"), "facebook" to "Facebook", "telegram" to "Telegram", "other" to L.t("أخرى", "حاجة تانية", "Other"))
        channels.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (k, t) -> Choice(t, channel == k) { channel = k } } } }
        if (err.isNotEmpty()) Text(err, color = C.Danger, fontSize = 13.sp)
        Primary(L.t("أرسل البلاغ", "ابعت البلاغ", "Send report"), {
            val v = ScamReports.normalize(kind, value)
            if (v == null) err = if (kind == "link") L.t("هذا ليس رابطًا صحيحًا.", "ده مش لينك صحيح.", "That is not a valid link.") else L.t("هذا ليس رقم هاتف صحيحًا.", "ده مش رقم تليفون صحيح.", "That is not a valid phone number.")
            else { App.report(kind, v, channel); value = "" }
        }, enabled = value.isNotBlank())
        if (App.reportMsg.isNotEmpty()) Text(App.reportMsg, color = C.Safe, fontSize = 13.sp)
        Text(L.t("يُرسل: الرابط أو الرقم، ومكان وصوله، ونسخة التطبيق ولغته فقط. لا يُرسل اسمك ولا رقمك ولا أي معرّف لهاتفك. ويحذّرك فحص الروابط من أي موقع أبلغت عنه.",
            "اللي بيتبعت: اللينك أو الرقم، وجالك منين، ونسخة البرنامج ولغته بس. مش بيتبعت اسمك ولا رقمك ولا أي حاجة تعرّف موبايلك. وفحص اللينكات بيحذّرك من أي موقع إنت بلّغت عنه.",
            "Sent: the link or number, where it reached you, and the app's version and language only. Not your name, your number or anything that identifies your phone. The link check warns you about any site you reported."), color = C.Muted, fontSize = 12.sp)
        if (mine.isNotEmpty()) {
            SectionTitle(L.t("بلاغاتك (${mine.size})", "بلاغاتك (${mine.size})", "Your reports (${mine.size})"))
            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                mine.take(30).forEachIndexed { i, r ->
                    ListRow(r.value.take(60), stamp.format(Date(r.at)) + "  ·  " + if (r.sent) L.t("أُرسل", "اتبعت", "Sent") else L.t("في الانتظار", "مستني", "Waiting"),
                        subColor = if (r.sent) C.Safe else C.Warn, last = i == minOf(mine.size, 30) - 1) {
                        SmallLink(L.t("حذف", "امسح", "Delete"), C.Muted) { ScamReports.remove(r); App.reportsVersion++ }
                    }
                }
            }
        }
    }
}
