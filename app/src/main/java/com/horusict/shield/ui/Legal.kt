/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horusict.shield.App
import com.horusict.shield.L
import com.horusict.shield.Screen

object Owner {
    const val company = "Horus ICT"
    const val year = "2026"
    const val website = "https://horusict.com"
    const val contact = "horusict.com"
    const val developerLinkedIn = "https://www.linkedin.com/in/medhat-saif/"
    fun name() = company
    fun copyright() = "© $year " + name() + ". " + L.t("جميع الحقوق محفوظة.", "كل الحقوق محفوظة.", "All rights reserved.")
}

/** 0 = terms, 1 = privacy, 2 = open-source notices */
object LegalPage { var page by androidx.compose.runtime.mutableIntStateOf(0) }

@Composable
fun LegalScreen() {
    var tab by remember { mutableIntStateOf(LegalPage.page) }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("الشروط والخصوصية", "الشروط والخصوصية", "Terms and privacy"), { App.screen = Screen.ABOUT })
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(L.t("شروط الاستخدام", "شروط الاستخدام", "Terms of use"), L.t("الخصوصية", "الخصوصية", "Privacy"), L.t("التراخيص", "التراخيص", "Licences")).forEachIndexed { i, t ->
                val sel = tab == i
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (sel) C.Gold else C.Bar).clickable { tab = i }.padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t, color = if (sel) C.OnGold else C.Text, fontSize = 13.sp, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val sections = when (tab) { 0 -> terms(); 1 -> privacy(); else -> licences() }
            sections.forEach { (h, body) ->
                Panel {
                    Text(h, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(body, color = C.Text2, fontSize = 13.sp)
                }
            }
            Text(Owner.copyright(), color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 14.dp))
        }
    }
}

private fun terms(): List<Pair<String, String>> = listOf(
    L.t("الملكية", "الملكية", "Ownership") to L.t(
        "Horus Shield، بالكود والتصميم والاسم والشعار، ملك شركة ${Owner.company}. جميع الحقوق محفوظة. يُمنع نسخ التطبيق أو تعديله أو فك تجميعه أو إعادة توزيعه أو بيعه أو نشر نسخة منه باسم آخر دون إذن كتابي.", "Horus Shield، بالكود والتصميم والاسم واللوجو، ملك شركة ${Owner.company}. كل الحقوق محفوظة. ممنوع نسخ التطبيق أو تعديله أو فك تجميعه أو إعادة توزيعه أو بيعه أو نشر نسخة منه باسم تاني من غير إذن مكتوب.",
        "Horus Shield, including the code, design, name and logo, are owned by ${Owner.company}. All rights reserved. You may not copy, modify, decompile, redistribute, sell or publish the app under another name without written permission."),
    L.t("ترخيص الاستخدام", "ترخيص الاستخدام", "Licence to use") to L.t(
        "يُسمح لك بتثبيت التطبيق واستخدامه على أجهزتك الشخصية والعائلية. أما الاستخدام التجاري (الشركات ومراكز الصيانة والخدمات المدفوعة) فيحتاج إلى اتفاق منفصل.", "مسموحلك تسطّب التطبيق وتستخدمه على أجهزتك الشخصية والعائلية. الاستخدام التجاري (شركات، مراكز صيانة، خدمات مدفوعة) محتاج اتفاق منفصل.",
        "You may install and use the app on your own and your family's devices. Commercial use (companies, repair shops, paid services) needs a separate agreement."),
    L.t("حدود الفحص", "حدود الفحص", "What a scan can and cannot do") to L.t(
        "يساعدك التطبيق على اكتشاف علامات الخطر، لكنه ليس ضمانًا كاملًا. قد تفوته تهديدات جديدة أو متقدمة، وقد يحذّر من تطبيق سليم. والقرار النهائي بحذف أي شيء أو تعطيله قرارك أنت.", "التطبيق بيساعدك تكتشف علامات الخطر، لكنه مش ضمان كامل. ممكن يفوّت تهديدات جديدة أو متقدمة، وممكن يحذّر من تطبيق سليم. القرار النهائي في مسح أو تعطيل أي حاجة قرارك إنت.",
        "The app helps you spot warning signs, but it is not a complete guarantee. It can miss new or advanced threats and can flag a harmless app. The final decision to remove or disable anything is yours."),
    L.t("وضع Root", "وضع Root", "Root mode") to L.t(
        "تغيّر أدوات وضع Root حالة التطبيقات على الهاتف بعد موافقتك على كل خطوة، واستخدامها على مسؤوليتك.", "أدوات وضع Root بتغيّر حالة تطبيقات على الموبايل بعد موافقتك على كل خطوة. استخدامها على مسؤوليتك.",
        "Root-mode tools change the state of apps on the phone after you confirm each step. You use them at your own risk."),
    L.t("إخلاء المسؤولية", "إخلاء المسؤولية", "Disclaimer") to L.t(
        "يُقدَّم التطبيق \"كما هو\". ولا يتحمل المطوّر مسؤولية أي خسارة ناتجة عن استخدامه أو عن الاعتماد على نتائجه، في الحدود التي يسمح بها القانون المصري.", "التطبيق بيتقدّم \"كما هو\". المطوّر مش مسؤول عن أي خسارة ناتجة عن استخدامه أو عن الاعتماد على نتايجه، في حدود ما يسمح به القانون المصري.",
        "The app is provided \"as is\". To the extent Egyptian law allows, the developer is not liable for any loss arising from its use or from relying on its results."),
    L.t("الاستخدام المسموح", "الاستخدام المسموح", "Acceptable use") to L.t(
        "استخدم التطبيق على أجهزتك أو على أجهزة أنت مسؤول عنها فقط. ويُمنع استخدامه للتجسس على أي شخص أو لفحص جهاز دون علم صاحبه.", "استخدم التطبيق على أجهزتك أو أجهزة إنت مسؤول عنها بس. ممنوع استخدامه للتجسس على حد أو لفحص جهاز من غير علم صاحبه.",
        "Use the app only on devices you own or are responsible for. Do not use it to spy on anyone or to inspect a device without its owner's knowledge."),
)

private fun privacy(): List<Pair<String, String>> = listOf(
    L.t("يتم الفحص على الهاتف", "الفحص بيتم على الموبايل", "Scans stay on the phone") to L.t(
        "تُقرأ قائمة التطبيقات والصلاحيات والإعدادات ونتائج الفحص وتُحسب على هاتفك، ولا تُرسل إلى أي خادم. لا يوجد حساب ولا إعلانات ولا أدوات تتبع.", "قايمة التطبيقات والصلاحيات والإعدادات ونتايج الفحص بتتقرا وتتحسب على موبايلك، ومش بتتبعت لأي سيرفر. مفيش حساب، ومفيش إعلانات، ومفيش أدوات تتبع.",
        "The app list, permissions, settings and scan results are read and processed on your phone and are not sent to any server. There is no account, no ads and no tracking."),
    L.t("ما يخرج من الهاتف", "اللي بيخرج من الموبايل", "What leaves the phone") to L.t(
        "• تنزيل قاعدة التهديدات وملف التحديث من GitHub.\n• عند فحص رابط: طلب لعنوان الرابط نفسه دون فتح الصفحة، واسم النطاق فقط إلى rdap.org لمعرفة عمره.\n• عند الإبلاغ عن احتيال، وفقط حين تضغط إرسال: الرابط أو الرقم الذي أبلغت عنه، ومكان وصوله، ونسخة التطبيق ولغته، إلى horusict.com. لا يُرسل اسمك ولا رقمك ولا أي معرّف لهاتفك.\nلا يُرسل أي شيء آخر.", "• تنزيل قاعدة التهديدات وملف التحديث من GitHub.\n• لما تفحص لينك: طلب لعنوان اللينك نفسه من غير فتح الصفحة، واسم الدومين بس لـ rdap.org علشان يعرف عمره.\n• لما تبلّغ عن نصب، ولما تدوس إرسال بس: اللينك أو الرقم اللي بلّغت عنه، وجالك منين، ونسخة البرنامج ولغته، لـ horusict.com. مش بيتبعت اسمك ولا رقمك ولا أي حاجة تعرّف موبايلك.\nمفيش حاجة تانية بتتبعت.",
        "• Downloading the threat intel and app updates from GitHub.\n• When you check a link: a header-only request to that link without opening the page, and only the domain name to rdap.org for its age.\n• When you report a scam, and only when you tap Send: the link or number you reported, where it reached you, and the app's version and language, to horusict.com. Not your name, your number or anything that identifies your phone.\nNothing else is sent."),
    L.t("الصلاحيات", "الصلاحيات", "Permissions") to L.t(
        "قائمة التطبيقات (للبحث عن برامج التجسس)، وUsage access إن سمحت به (أرقام استهلاك البيانات فقط، لا المحتوى)، والإشعارات، وتثبيت التحديثات، والأجهزة القريبة والموقع للبحث عن أجهزة التتبع بالبلوتوث فقط (يشترطهما أندرويد، ولا يُحفظ موقعك ولا يُرسل). لا يقرأ التطبيق رسائلك ولا صورك ولا مكالماتك.", "قايمة التطبيقات (علشان يدوّر على برامج التجسس)، Usage access لو سمحت بيه (أرقام استهلاك البيانات بس، مش المحتوى)، التنبيهات، وتسطيب التحديثات، والأجهزة القريبة والموقع علشان يدوّر على أجهزة التتبع بالبلوتوث بس (أندرويد بيطلبهم، وموقعك مش بيتحفظ ولا بيتبعت). التطبيق مش بيقرا رسايلك ولا صورك ولا مكالماتك.",
        "The app list (to look for stalkerware), Usage access if you allow it (data-usage numbers only, never content), notifications, installing its own updates, and nearby devices and location only to search for Bluetooth trackers (Android requires them; your location is never saved or sent). The app never reads your messages, photos or calls."),
    L.t("المشاركة", "المشاركة", "Sharing") to L.t(
        "يُنشأ الملخص وملف الأدلة على الهاتف، ولا يُرسلان إلى أحد إلا إذا اخترت أنت مشاركتهما.", "الملخص وملف الأدلة بيتعملوا على الموبايل، ومش بيتبعتوا لحد غير لما إنت تختار تشاركهم.",
        "The summary and the evidence file are created on the phone and go nowhere unless you choose to share them."),
    L.t("التواصل", "التواصل", "Contact") to L.t("أسئلة الخصوصية: ${Owner.contact}", "أسئلة الخصوصية: ${Owner.contact}", "Privacy questions: ${Owner.contact}"),
)

private fun licences(): List<Pair<String, String>> = listOf(
    "Horus Shield" to Owner.copyright() + L.t(" برنامج مملوك وغير مفتوح المصدر.", " برنامج مملوك وغير مفتوح المصدر.", " Proprietary, closed-source software."),
    "Stalkerware indicators — Echap" to "Echap stalkerware-indicators, licensed under Creative Commons Attribution 4.0 (CC BY 4.0). https://github.com/AssoEchap/stalkerware-indicators",
    "abuse.ch — ThreatFox, URLhaus, MalwareBazaar" to "Data from abuse.ch, released under CC0 1.0 (public domain). https://abuse.ch",
    "CISA Known Exploited Vulnerabilities" to "U.S. Cybersecurity and Infrastructure Security Agency, public domain. https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
    "RDAP" to "Domain registration data via rdap.org.",
    "Android Jetpack, Jetpack Compose, AndroidX WorkManager" to "Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0. https://www.apache.org/licenses/LICENSE-2.0",
    "Cairo" to "Copyright 2009 The Cairo Project Authors. Licensed under the SIL Open Font License, Version 1.1. https://openfontlicense.org",
    "Montserrat" to "Copyright 2024 The Montserrat Project Authors. Licensed under the SIL Open Font License, Version 1.1. https://openfontlicense.org",
    "Kotlin, kotlinx.coroutines" to "Copyright JetBrains s.r.o. and contributors. Licensed under the Apache License, Version 2.0.",
)
