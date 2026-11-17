/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.horussec.mobile.R
import com.horussec.mobile.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stampS = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() = TabScreen(Tab.SETTINGS) {
    val ctx = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> App.notifyAllowed = ok }
    var langOpen by rememberSaveable { mutableStateOf(false) }
    val langs = listOf("en" to "English", "ar" to "العربية (الفصحى)", "eg" to "العربية (المصرية)")
    var rootOpen by rememberSaveable { mutableStateOf(App.rootOn || App.rootDetected) }
    fun openNotifSettings() {
        try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("الإعدادات", "الإعدادات", "Settings"), null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ListRow(L.t("الفحص التلقائي", "الفحص التلقائي", "Automatic scan"), if (App.autoHours > 0) autoLabel(App.autoHours) else L.t("معطّل", "مقفول", "Off")) {
                    GoldSwitch(App.autoHours > 0) { on ->
                        App.setAuto(if (on) 6 else 0)
                        if (on && android.os.Build.VERSION.SDK_INT >= 33 && !AutoScan.notificationsAllowed(ctx)) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (App.autoHours > 0) {
                    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutoScan.choices.filter { it > 0 }.forEach { h -> Choice(autoLabel(h), App.autoHours == h) { App.setAuto(h) } }
                    }
                    val last = Prefs.long("lastAutoScan")
                    Text(if (last > 0) L.t("آخر فحص تلقائي: ", "آخر فحص تلقائي: ", "Last automatic scan: ") + stampS.format(Date(last)) else L.t("أول فحص تلقائي بعد ${App.autoHours} ساعات.", "أول فحص تلقائي بعد ${App.autoHours} ساعات.", "First automatic scan in ${App.autoHours} hours."),
                        color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Hairline(C.Line.copy(alpha = 0.6f))
                }
                ListRow(L.t("تنبيهات الأمان", "تنبيهات الأمان", "Security alerts"), if (App.notifyAllowed) L.t("سنرسل إليك تنبيهًا إن ظهر شيء", "هنبعتلك تنبيه لو ظهرت حاجة", "You will be alerted if something shows up") else L.t("معطّلة: لن تعرف إن وجد الفحص شيئًا", "مقفولة: مش هتعرف لو الفحص لقى حاجة", "Off: you will not hear about findings"),
                    subColor = if (App.notifyAllowed) C.Muted else C.Warn) {
                    GoldSwitch(App.notifyAllowed) { on ->
                        if (on && android.os.Build.VERSION.SDK_INT >= 33) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) else openNotifSettings()
                    }
                }
                ListRow(L.t("مراقبة استهلاك البيانات", "مراقبة استهلاك البيانات", "Data-usage monitoring"), if (App.usageAccess) L.t("مسموح", "مسموح", "Allowed") else L.t("يلزم إذن Usage access", "محتاج إذن Usage access", "Needs Usage access"),
                    subColor = if (App.usageAccess) C.Safe else C.GoldLight, onClick = { DeepScan.openUsageAccess(ctx) })
                ListRow(L.t("أدوات الخبراء (Root)", "أدوات الخبراء (Root)", "Expert tools (root)"), if (App.rootOn) L.t("مفعّل", "شغال", "On") else if (App.rootDetected) L.t("متاح", "متاح", "Available") else L.t("معطّل · الهاتف ليس عليه Root", "مقفول · الموبايل مش عليه Root", "Off · the phone is not rooted"),
                    onClick = { rootOpen = !rootOpen }, last = !rootOpen)
                if (rootOpen) Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { RootSection() }
            }

            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ListRow(L.t("اللغة", "اللغة", "Language"), langs.first { it.first == L.lang }.second, onClick = { langOpen = !langOpen })
                if (langOpen) FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    langs.forEach { (code, name) -> Choice(name, L.lang == code) { App.setLang(code) } }
                }
                ListRow(L.t("المظهر", "المظهر", "Appearance"), when (App.theme) { "light" -> L.t("فاتح", "فاتح", "Light"); "dark" -> L.t("داكن", "داكن", "Dark"); else -> L.t("مثل الهاتف", "زي الموبايل", "Match phone") },
                    onClick = { App.chooseTheme(when (App.theme) { "system" -> "dark"; "dark" -> "light"; else -> "system" }) }, last = true)
            }

            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Text(L.t("خصوصيتك", "خصوصيتك", "Your privacy"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Why(
                    L.t("يتم الفحص على الهاتف، ولا تُرسل بياناتك إلى أي خادم.", "الفحص بيتم على الموبايل، ومش بيبعت بياناتك لأي سيرفر.", "Scans run on the phone and send none of your data to any server."),
                    L.t("لا يحذف Horus Shield أي شيء ولا يغيّره من تلقاء نفسه، ولا يقرأ رسائلك ولا صورك. ما يخرج من الهاتف: تنزيل قاعدة التهديدات والتحديثات، وعند فحص رابط نطلب عنوان الصفحة فقط ونستعلم من rdap.org عن عمر النطاق.", "Horus Shield مش بيمسح ولا بيغيّر حاجة لوحده، ومش بيقرا رسايلك ولا صورك. اللي بيخرج من الموبايل: تنزيل قاعدة التهديدات والتحديثات، ولما تفحص لينك بنطلب عنوان الصفحة بس ونسأل rdap.org عن عمر الدومين.",
                        "Horus Shield never removes or changes anything on its own and never reads your messages or photos. What leaves the phone: the threat intel and app updates, and when you check a link, a header-only request and a domain-age lookup at rdap.org."),
                )
            }

            HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ListRow(L.t("حول التطبيق والتحديثات", "حول التطبيق والتحديثات", "About and updates"), onClick = { App.screen = Screen.ABOUT })
                ListRow(L.t("الدعم والتواصل مع المطوّر", "الدعم والتواصل مع المطوّر", "Support and contact"), "LinkedIn", onClick = {
                    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Owner.linkedin)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }, last = true)
            }

            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Decor(R.drawable.art_lotus, Modifier.width(44.dp), alpha = 0.6f)
                Text("HORUS SHIELD · V" + BuildConfig.VERSION_NAME + " · MEDHAT SAIF", color = C.Dim, fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 2.sp, style = Ltr)
            }
        }
    }
}

@Composable
private fun RootSection() {
    val ctx = LocalContext.current
    if (App.rootMsg.isNotEmpty() && App.screen == Screen.SETTINGS) Text(App.rootMsg, color = C.GoldLight, fontSize = 13.sp)
    if (!App.rootDetected && !App.rootOn) {
        Why(
            L.t("وضع Root معطّل لأن الهاتف ليس عليه Root، وهذا أفضل لأمانك.", "وضع Root مقفول لأن الموبايل مش عليه Root، وده الأفضل لأمانك.", "Root mode is off because the phone is not rooted. That is safer for you."),
            L.t("على الهواتف التي عليها Root (Magisk أو KernelSU)، يكشف هذا الوضع التطبيقات المتصلة الآن بخوادم تحكم، والبرامج المخفية، وتحويل حركة البيانات، ويستطيع إيقاف تطبيق خطر أو تعطيله بعد موافقتك. لا تفعّل Root لتجربته، فهو يُضعف حماية الهاتف.", "على الموبايلات اللي عليها Root (Magisk أو KernelSU)، الوضع ده بيكشف التطبيقات المتصلة دلوقتي بسيرفرات تحكم، والبرامج المخفية، وتحويل الترافيك، وبيقدر يوقّف أو يعطّل تطبيق خطر بعد موافقتك. متعملش Root علشان تجرّبه: ده بيضعّف حماية الموبايل.",
                "On rooted phones (Magisk or KernelSU), this mode spots apps connected right now to command servers, hidden programs and traffic redirection, and can stop or disable a dangerous app after you confirm. Do not root your phone to try it: rooting weakens its protection."),
            C.Muted,
        )
        return
    }
    if (!App.rootOn) {
        Why(
            L.t("الهاتف عليه Root، وهذا الوضع يتيح للفحص العميق رؤية أعمق.", "الموبايل عليه Root. الوضع ده بيخلّي الفحص العميق يشوف أعمق.", "The phone is rooted. This mode lets the deep scan look deeper."),
            L.t("إن سمحت لـ Horus Shield بالصلاحية، فسيفحص الفحص العميق الاتصالات القائمة لكل تطبيق ويقارنها بخوادم التحكم المعروفة، إضافة إلى البرامج العاملة ووحدات Magisk ونصوص الإقلاع وملف hosts وتحويل حركة البيانات. يستخدمها Horus Shield للقراءة فقط، ولا يغيّر شيئًا إلا بموافقتك على كل خطوة.", "لو سمحت لـ Horus Shield بالصلاحية، الفحص العميق هيشوف الاتصالات الشغالة لكل تطبيق ويقارنها بسيرفرات التحكم المعروفة، والبرامج الشغالة، وموديولات Magisk، وسكربتات الإقلاع، وملف hosts، وتحويل الترافيك. Horus Shield بيستخدمه للقراءة، ومش بيغيّر حاجة غير بموافقتك على كل خطوة.",
                "With root, the deep scan checks every app's live connections against known command servers, running programs, Magisk modules, boot scripts, the hosts file and traffic redirection. Horus Shield uses it to read, and changes nothing without your consent for each step."),
        )
        Secondary(if (App.rootBusy) L.t("بانتظار موافقة Magisk...", "مستني موافقة Magisk...", "Waiting for Magisk...") else L.t("فعّل وضع Root", "فعّل وضع Root", "Turn on root mode"), { App.enableRoot() }, Modifier.fillMaxWidth(), enabled = !App.rootBusy)
    } else {
        Text(L.t("مفعّل: يستخدمه الفحص العميق والفحص التلقائي.", "شغال: الفحص العميق والفحص التلقائي بيستخدموه.", "On: deep and automatic scans use it."), color = C.Safe, fontSize = 14.sp)
        ActionText(if (App.rootBusy) L.t("جارٍ الجمع...", "بيجمع...", "Collecting...") else L.t("صدّر سجلات النظام كدليل", "صدّر سجلات النظام كدليل", "Export system logs as evidence")) { if (!App.rootBusy) App.exportEvidence(ctx) }
        if (App.rootUndo.isNotEmpty()) {
            Text(L.t("ما عطّله Horus Shield أو قطع عنه الإنترنت:", "اللي Horus Shield عطّله أو قطع عنه النت:", "Apps Horus Shield disabled or cut off:"), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            App.rootUndo.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text((if (u.kind == "block") L.t("الإنترنت مقطوع: ", "نت مقطوع: ", "No internet: ") else L.t("معطّل: ", "متعطّل: ", "Disabled: ")) + u.pkg, color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    SmallLink(L.t("استعادة", "رجّع", "Restore")) { App.rootUndoAction(u) }
                }
            }
        }
        ActionText(L.t("أوقف وضع Root", "اقفل وضع Root", "Turn off root mode"), C.Muted) { App.disableRoot() }
    }
}
