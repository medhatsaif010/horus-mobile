/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horussec.mobile.App
import com.horussec.mobile.AutoScan
import com.horussec.mobile.BuildConfig
import com.horussec.mobile.DeepScan
import com.horussec.mobile.L
import com.horussec.mobile.Prefs
import com.horussec.mobile.Screen
import com.horussec.mobile.SealWidget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stampS = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US)

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() = TabScreen(Tab.SETTINGS) {
    val ctx = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> App.notifyAllowed = ok }
    var pinMsg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("الإعدادات", "Settings"), null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp)) {

            // ---- automatic scan
            Heading(L.t("الفحص التلقائي", "Automatic scan"))
            Why(
                L.t("فحص عميق في الخلفية، وتنبيه بس لو ظهر حاجة.", "A deep scan in the background, with an alert only if something shows up."),
                L.t("Horus Sec يعمل فحص عميق لوحده، ويبعتلك تنبيه بس لو ظهرت علامة اختراق أو تطبيق جديد أخد صلاحيات خطيرة. أندرويد ممكن يأخّر الفحص شوية علشان البطارية.",
                    "Horus Sec runs a deep scan on its own and only alerts you if a sign of compromise appears or a new app gets risky access. Android may delay it a little to save battery."),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoScan.choices.forEach { h ->
                    Choice(autoLabel(h), App.autoHours == h) {
                        App.setAuto(h)
                        if (h > 0 && android.os.Build.VERSION.SDK_INT >= 33 && !AutoScan.notificationsAllowed(ctx)) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            if (App.autoHours > 0) {
                val last = Prefs.long("lastAutoScan")
                Text(if (last > 0) L.t("آخر فحص تلقائي: ", "Last automatic scan: ") + stampS.format(Date(last)) else L.t("أول فحص تلقائي بعد ${App.autoHours} ساعات.", "First automatic scan in ${App.autoHours} hours."),
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (!App.notifyAllowed) {
                    Text(L.t("التنبيهات مقفولة، فمش هتعرف لو الفحص لقى حاجة.", "Notifications are off, so you will not hear about findings."), color = C.Warn, fontSize = 13.sp)
                    ActionText(L.t("شغّل التنبيهات", "Turn on notifications")) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                    }
                }
            }
            Spacer(Modifier.height(10.dp)); Hairline()

            // ---- data usage
            Heading(L.t("استهلاك البيانات", "Data usage"))
            if (App.usageAccess) Text(L.t("مسموح: الفحص العميق بيشوف التطبيقات المخفية اللي بتبعت بيانات كتير.", "Allowed: the deep scan spots hidden apps sending lots of data."), color = C.Safe, fontSize = 14.sp)
            else {
                Why(
                    L.t("اسمح بيه علشان الفحص العميق يكشف اللي بيبعت صورك لبرّه.", "Allow it so the deep scan can spot apps sending your photos out."),
                    L.t("Usage access بيدّي Horus Sec أرقام استهلاك النت لكل تطبيق بس، مش المحتوى. لو تطبيق مخفي بيرفع كمية كبيرة، ده ممكن يكون برنامج تجسس بيبعت صور أو تسجيلات.",
                        "Usage access gives Horus Sec each app's data numbers only, never the content. A hidden app uploading a lot may be stalkerware sending photos or recordings."),
                )
                ActionText(L.t("اسمح بـ Usage access", "Allow Usage access")) { DeepScan.openUsageAccess(ctx) }
            }
            Spacer(Modifier.height(10.dp)); Hairline()

            // ---- appearance
            Heading(L.t("الشكل", "Appearance"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(L.t("زي الموبايل", "Match phone"), App.theme == "system") { App.chooseTheme("system") }
                Choice(L.t("غامق", "Dark"), App.theme == "dark") { App.chooseTheme("dark") }
                Choice(L.t("فاتح", "Light"), App.theme == "light") { App.chooseTheme("light") }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("عربي", L.ar) { App.setLang(true) }
                Choice("English", !L.ar) { App.setLang(false) }
            }
            Spacer(Modifier.height(4.dp))
            ActionText(L.t("حط الختم على الشاشة الرئيسية للموبايل", "Put the seal on your home screen")) {
                if (!SealWidget.requestPin(ctx)) pinMsg = L.t("اضغط مطوّل على الشاشة الرئيسية، اختار Widgets، ودوّر على Horus Sec.", "Long-press your home screen, choose Widgets, and find Horus Sec.")
            }
            if (pinMsg.isNotEmpty()) Text(pinMsg, color = C.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp)); Hairline()

            // ---- expert tools
            Heading(L.t("أدوات الخبراء", "Expert tools"))
            RootSection()
            Spacer(Modifier.height(10.dp)); Hairline()

            // ---- privacy and about
            Heading(L.t("خصوصيتك", "Your privacy"))
            Why(
                L.t("الفحص بيتم على الموبايل، ومش بيبعت بياناتك لأي سيرفر.", "Scans run on the phone and send none of your data to any server."),
                L.t("Horus Sec مش بيمسح ولا بيغيّر حاجة لوحده، ومش بيقرا رسايلك ولا صورك. اللي بيخرج من الموبايل: تنزيل قاعدة التهديدات والتحديثات، ولما تفحص لينك بنطلب عنوان الصفحة بس ونسأل rdap.org عن عمر الدومين.",
                    "Horus Sec never removes or changes anything on its own and never reads your messages or photos. What leaves the phone: the threat intel and app updates, and when you check a link, a header-only request and a domain-age lookup at rdap.org."),
            )
            Spacer(Modifier.height(10.dp)); Hairline()
            ActionText(L.t("عن البرنامج والتحديثات", "About and updates")) { App.screen = Screen.ABOUT }
            Hairline()
            Text("Horus Sec  ·  v" + BuildConfig.VERSION_NAME + "  ·  " + L.t("قاعدة التهديدات: ", "Threat intel: ") + (App.intel?.date?.ifEmpty { "—" } ?: "…"),
                color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun RootSection() {
    val ctx = LocalContext.current
    if (App.rootMsg.isNotEmpty() && App.screen == Screen.SETTINGS) Text(App.rootMsg, color = C.Gold, fontSize = 13.sp)
    if (!App.rootDetected && !App.rootOn) {
        Why(
            L.t("وضع Root مقفول لأن الموبايل مش عليه Root، وده الأفضل لأمانك.", "Root mode is off because the phone is not rooted. That is safer for you."),
            L.t("على الموبايلات اللي عليها Root (Magisk أو KernelSU)، الوضع ده بيكشف التطبيقات المتصلة دلوقتي بسيرفرات تحكم، والبرامج المخفية، وتحويل الترافيك، وبيقدر يوقّف أو يعطّل تطبيق خطر بعد موافقتك. متعملش Root علشان تجرّبه: ده بيضعّف حماية الموبايل.",
                "On rooted phones (Magisk or KernelSU), this mode spots apps connected right now to command servers, hidden programs and traffic redirection, and can stop or disable a dangerous app after you confirm. Do not root your phone to try it: rooting weakens its protection."),
            C.Muted,
        )
        return
    }
    if (!App.rootOn) {
        Why(
            L.t("الموبايل عليه Root. الوضع ده بيخلّي الفحص العميق يشوف أعمق.", "The phone is rooted. This mode lets the deep scan look deeper."),
            L.t("لو سمحت لـ Horus Sec بالصلاحية، الفحص العميق هيشوف الاتصالات الشغالة لكل تطبيق ويقارنها بسيرفرات التحكم المعروفة، والبرامج الشغالة، وموديولات Magisk، وسكربتات الإقلاع، وملف hosts، وتحويل الترافيك. Horus Sec بيستخدمه للقراءة، ومش بيغيّر حاجة غير بموافقتك على كل خطوة.",
                "With root, the deep scan checks every app's live connections against known command servers, running programs, Magisk modules, boot scripts, the hosts file and traffic redirection. Horus Sec uses it to read, and changes nothing without your consent for each step."),
        )
        Secondary(if (App.rootBusy) L.t("مستني موافقة Magisk...", "Waiting for Magisk...") else L.t("فعّل وضع Root", "Turn on root mode"), { App.enableRoot() }, Modifier.fillMaxWidth(), enabled = !App.rootBusy)
    } else {
        Text(L.t("شغال: الفحص العميق والفحص التلقائي بيستخدموه.", "On: deep and automatic scans use it."), color = C.Safe, fontSize = 14.sp)
        ActionText(if (App.rootBusy) L.t("بيجمع...", "Collecting...") else L.t("صدّر سجلات النظام كدليل", "Export system logs as evidence")) { if (!App.rootBusy) App.exportEvidence(ctx) }
        if (App.rootUndo.isNotEmpty()) {
            Text(L.t("اللي Horus Sec عطّله أو قطع عنه النت:", "Apps Horus Sec disabled or cut off:"), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            App.rootUndo.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text((if (u.kind == "block") L.t("نت مقطوع: ", "No internet: ") else L.t("متعطّل: ", "Disabled: ")) + u.pkg, color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    SmallLink(L.t("رجّع", "Restore")) { App.rootUndoAction(u) }
                }
            }
        }
        ActionText(L.t("اقفل وضع Root", "Turn off root mode"), C.Muted) { App.disableRoot() }
    }
}

