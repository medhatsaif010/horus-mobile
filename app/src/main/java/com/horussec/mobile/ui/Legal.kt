/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile.ui

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
import com.horussec.mobile.App
import com.horussec.mobile.L
import com.horussec.mobile.Screen

object Owner {
    val nameAr = "مدحت سيف"
    val nameEn = "Medhat Saif"
    const val year = "2026"
    const val linkedin = "https://www.linkedin.com/in/medhat-saif/"
    const val credly = "https://www.credly.com/users/medhat-saif/badges/credly"
    const val contact = linkedin
    fun name() = L.t(nameAr, nameEn)
    fun copyright() = "© $year " + name() + ". " + L.t("كل الحقوق محفوظة.", "All rights reserved.")
}

/** 0 = terms, 1 = privacy, 2 = open-source notices */
object LegalPage { var page by androidx.compose.runtime.mutableIntStateOf(0) }

@Composable
fun LegalScreen() {
    var tab by remember { mutableIntStateOf(LegalPage.page) }
    Column(Modifier.fillMaxSize()) {
        TopBar(L.t("الشروط والخصوصية", "Terms and privacy"), { App.screen = Screen.ABOUT })
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(L.t("شروط الاستخدام", "Terms of use"), L.t("الخصوصية", "Privacy"), L.t("التراخيص", "Licences")).forEachIndexed { i, t ->
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
    L.t("الملكية", "Ownership") to L.t(
        "Horus Sec و Horus Sec Mobile، بالكود والتصميم والاسم واللوجو، ملك ${Owner.nameAr}. كل الحقوق محفوظة. ممنوع نسخ التطبيق أو تعديله أو فك تجميعه أو إعادة توزيعه أو بيعه أو نشر نسخة منه باسم تاني من غير إذن مكتوب.",
        "Horus Sec and Horus Sec Mobile, including the code, design, name and logo, are owned by ${Owner.nameEn}. All rights reserved. You may not copy, modify, decompile, redistribute, sell or publish the app under another name without written permission."),
    L.t("ترخيص الاستخدام", "Licence to use") to L.t(
        "مسموحلك تسطّب التطبيق وتستخدمه على أجهزتك الشخصية والعائلية. الاستخدام التجاري (شركات، مراكز صيانة، خدمات مدفوعة) محتاج اتفاق منفصل.",
        "You may install and use the app on your own and your family's devices. Commercial use (companies, repair shops, paid services) needs a separate agreement."),
    L.t("حدود الفحص", "What a scan can and cannot do") to L.t(
        "التطبيق بيساعدك تكتشف علامات الخطر، لكنه مش ضمان كامل. ممكن يفوّت تهديدات جديدة أو متقدمة، وممكن يحذّر من تطبيق سليم. القرار النهائي في مسح أو تعطيل أي حاجة قرارك إنت.",
        "The app helps you spot warning signs, but it is not a complete guarantee. It can miss new or advanced threats and can flag a harmless app. The final decision to remove or disable anything is yours."),
    L.t("وضع Root", "Root mode") to L.t(
        "أدوات وضع Root بتغيّر حالة تطبيقات على الموبايل بعد موافقتك على كل خطوة. استخدامها على مسؤوليتك.",
        "Root-mode tools change the state of apps on the phone after you confirm each step. You use them at your own risk."),
    L.t("إخلاء المسؤولية", "Disclaimer") to L.t(
        "التطبيق بيتقدّم \"كما هو\". المطوّر مش مسؤول عن أي خسارة ناتجة عن استخدامه أو عن الاعتماد على نتايجه، في حدود ما يسمح به القانون المصري.",
        "The app is provided \"as is\". To the extent Egyptian law allows, the developer is not liable for any loss arising from its use or from relying on its results."),
    L.t("الاستخدام المسموح", "Acceptable use") to L.t(
        "استخدم التطبيق على أجهزتك أو أجهزة إنت مسؤول عنها بس. ممنوع استخدامه للتجسس على حد أو لفحص جهاز من غير علم صاحبه.",
        "Use the app only on devices you own or are responsible for. Do not use it to spy on anyone or to inspect a device without its owner's knowledge."),
)

private fun privacy(): List<Pair<String, String>> = listOf(
    L.t("الفحص بيتم على الموبايل", "Scans stay on the phone") to L.t(
        "قايمة التطبيقات والصلاحيات والإعدادات ونتايج الفحص بتتقرا وتتحسب على موبايلك، ومش بتتبعت لأي سيرفر. مفيش حساب، ومفيش إعلانات، ومفيش أدوات تتبع.",
        "The app list, permissions, settings and scan results are read and processed on your phone and are not sent to any server. There is no account, no ads and no tracking."),
    L.t("اللي بيخرج من الموبايل", "What leaves the phone") to L.t(
        "• تنزيل قاعدة التهديدات وملف التحديث من GitHub.\n• لما تفحص لينك: طلب لعنوان اللينك نفسه من غير فتح الصفحة، واسم الدومين بس لـ rdap.org علشان يعرف عمره.\nمفيش حاجة تانية بتتبعت.",
        "• Downloading the threat intel and app updates from GitHub.\n• When you check a link: a header-only request to that link without opening the page, and only the domain name to rdap.org for its age.\nNothing else is sent."),
    L.t("الصلاحيات", "Permissions") to L.t(
        "قايمة التطبيقات (علشان يدوّر على برامج التجسس)، Usage access لو سمحت بيه (أرقام استهلاك البيانات بس، مش المحتوى)، التنبيهات، وتسطيب التحديثات. التطبيق مش بيقرا رسايلك ولا صورك ولا مكالماتك.",
        "The app list (to look for stalkerware), Usage access if you allow it (data-usage numbers only, never content), notifications, and installing its own updates. The app never reads your messages, photos or calls."),
    L.t("المشاركة", "Sharing") to L.t(
        "الملخص وملف الأدلة بيتعملوا على الموبايل، ومش بيتبعتوا لحد غير لما إنت تختار تشاركهم.",
        "The summary and the evidence file are created on the phone and go nowhere unless you choose to share them."),
    L.t("التواصل", "Contact") to L.t("أسئلة الخصوصية: ${Owner.contact}", "Privacy questions: ${Owner.contact}"),
)

private fun licences(): List<Pair<String, String>> = listOf(
    "Horus Sec Mobile" to Owner.copyright() + L.t(" برنامج مملوك وغير مفتوح المصدر.", " Proprietary, closed-source software."),
    "Stalkerware indicators — Echap" to "Echap stalkerware-indicators, licensed under Creative Commons Attribution 4.0 (CC BY 4.0). https://github.com/AssoEchap/stalkerware-indicators",
    "abuse.ch — ThreatFox, URLhaus, MalwareBazaar" to "Data from abuse.ch, released under CC0 1.0 (public domain). https://abuse.ch",
    "CISA Known Exploited Vulnerabilities" to "U.S. Cybersecurity and Infrastructure Security Agency, public domain. https://www.cisa.gov/known-exploited-vulnerabilities-catalog",
    "RDAP" to "Domain registration data via rdap.org.",
    "Android Jetpack, Jetpack Compose, AndroidX WorkManager" to "Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0. https://www.apache.org/licenses/LICENSE-2.0",
    "Readex Pro" to "Copyright 2018 The Readex Pro Project Authors. Licensed under the SIL Open Font License, Version 1.1. https://openfontlicense.org",
    "Kotlin, kotlinx.coroutines" to "Copyright JetBrains s.r.o. and contributors. Licensed under the Apache License, Version 2.0.",
)
