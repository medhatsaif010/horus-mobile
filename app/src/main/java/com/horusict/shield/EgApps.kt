/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import org.json.JSONArray
import org.json.JSONObject

/**
 * Egyptian bank, wallet and telecom apps that people get faked most often.
 * Package names were checked one by one against their Google Play pages.
 *
 * What this adds on top of the threat intel:
 *  - Arabic names, so a fake called "انستاباي" is caught like one called "InstaPay"
 *  - the official app's status: from the store, from outside the store, or its signature changed
 *  - modified WhatsApp builds (GB, YO, FM...) and WhatsApp installed from outside the store
 *
 * Signatures are learnt on the phone itself: the first time an official package is seen
 * installed from a store, its signing certificate is remembered. If the same package later
 * shows a different certificate, it was replaced by a copy that is not the official one.
 */
object EgApps {
    data class Brand(val name: String, val nameAr: String, val packages: Set<String>, val rx: Regex)

    private fun rx(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    val brands = listOf(
        Brand("InstaPay", "إنستاباي", setOf("com.egyptianbanks.instapay"), rx("instapay|insta.?pay|[اإ]نستا ?باي")),
        Brand("NBE Mobile", "البنك الأهلي المصري", setOf("com.ofss.obdx.and.nbe.com.eg"), rx("\\bnbe\\b|al.?ahly|ahly.?bank|national bank of egypt|البنك ال[اأ]هلي|ال[اأ]هلي المصري")),
        Brand("CIB", "البنك التجاري الدولي", setOf("com.cibeg.ddc1.digitalbanking.live", "com.CIB.Digital.MB", "com.cibeg.dxp.businessbanking.prod", "com.CIBEgyptSecureToken"), rx("\\bcib\\b|cibeg|التجاري الدولي")),
        Brand("Banque Misr", "بنك مصر", setOf("com.BanqueMisr.MobileBanking", "com.BanqueMisr.CorpMobileBanking", "com.emeint.android.mwallet.bm", "com.BankMisrSoftToken"), rx("banque.?misr|banquemisr|\\bbm.?online\\b|\\bbm.?wallet\\b|بنك مصر")),
        Brand("Banque du Caire", "بنك القاهرة", setOf("com.bdc.eg.mobile"), rx("banque.?du.?caire|\\bbdc\\b|بنك القاهرة")),
        Brand("QNB Egypt", "بنك QNB", setOf("eg.com.qnb.eazymobile", "com.emeint.android.mwallet.qnb"), rx("\\bqnb\\b")),
        Brand("AAIB", "البنك العربي الأفريقي", setOf("com.aaib"), rx("\\baaib\\b|العربي ال[اأ]فريقي")),
        Brand("Vodafone Cash", "فودافون كاش", setOf("com.emeint.android.myservices"), rx("vodafone.?cash|vf.?cash|ana.?vodafone|فودافون ?كاش|[اأ]نا فودافون")),
        Brand("Orange Cash", "أورنج كاش", setOf("com.orange.eg.money", "com.orange.mobinilandme"), rx("orange.?(cash|money)|[اأ]ورن[جچ] ?كاش")),
        Brand("e& money", "اتصالات كاش", setOf("com.etisalat.ewallet", "com.etisalat"), rx("e&.?money|etisalat.?cash|\\becash\\b|اتصالات ?كاش")),
        Brand("WE Pay", "وي باي", setOf("com.TE.WEWallet", "com.ucare.we"), rx("\\bwe.?pay\\b|وي ?باي")),
        Brand("Fawry", "فوري", setOf("com.fawry.myfawry", "cash.fawry"), rx("fawry|^فوري|فوري ?(كاش|بلس|\\+)")),
        Brand("valU", "فاليو", setOf("com.efgh.customer"), rx("\\bvalu\\b|فاليو")),
        Brand("Telda", "تلدا", setOf("io.telda.app"), rx("telda|تلدا")),
    )

    private val officialPkgs: Map<String, Brand> = buildMap { brands.forEach { b -> b.packages.forEach { put(it, b) } } }
    fun brandOf(pkg: String): Brand? = officialPkgs[pkg]

    /** The brand an app's name or package imitates, if any (and it is not that brand's own package). */
    fun imitates(a: AppInfo): Brand? {
        if (officialPkgs.containsKey(a.pkg)) return null
        return brands.firstOrNull { it.rx.containsMatchIn(a.label) || it.rx.containsMatchIn(a.pkg) }
    }

    val whatsappOfficial = setOf("com.whatsapp", "com.whatsapp.w4b")
    /** Modified WhatsApp builds. They ask for your WhatsApp account and are a common way to plant spyware. */
    val whatsappMods = setOf("com.gbwhatsapp", "com.yowhatsapp", "com.fmwhatsapp", "com.obwhatsapp", "com.whatsapp.plus", "com.whatsapp.gold", "com.aerowhatsapp", "com.gbwhatsapp3", "com.nswhatsapp", "com.jtwhatsapp")
    private val whatsappName = rx("whats.?app|واتس ?[اآ]ب|واتساب")
    fun looksLikeWhatsapp(a: AppInfo) = a.pkg !in whatsappOfficial && (a.pkg in whatsappMods || whatsappName.containsMatchIn(a.label))

    // ------------------------------------------------------------------ learnt signatures
    /** pkg -> signing certificate SHA-1s seen when it came from a store */
    fun pins(): Map<String, Set<String>> = try {
        val o = JSONObject(Prefs.str("egPins") ?: "{}")
        buildMap { o.keys().forEach { k -> val a = o.optJSONArray(k) ?: JSONArray(); put(k, (0 until a.length()).map { a.optString(it) }.toSet()) } }
    } catch (e: Exception) { emptyMap() }

    /** Remembers the certificate of every official app installed from a store, and of WhatsApp. */
    fun learn(d: DeviceData) {
        val cur = pins().toMutableMap()
        var changed = false
        for (a in d.apps) {
            if (a.system || a.certSha1.isEmpty()) continue
            if (brandOf(a.pkg) == null && a.pkg !in whatsappOfficial) continue
            if (Analyzer.isSideloaded(a, d.selfPkg)) continue
            if (cur.containsKey(a.pkg)) continue
            cur[a.pkg] = a.certSha1.map { it.uppercase() }.toSet(); changed = true
        }
        if (changed) {
            val o = JSONObject(); cur.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) }
            Prefs.put("egPins", o.toString())
        }
    }

    enum class Status { STORE, OUTSIDE, SIG_CHANGED }
    data class Found(val app: AppInfo, val brand: String, val status: Status)

    /** Official bank, wallet and WhatsApp apps on the phone, with their status. Used by the Bank apps screen. */
    fun found(d: DeviceData): List<Found> {
        val p = pins()
        return d.apps.filter { !it.system && (brandOf(it.pkg) != null || it.pkg in whatsappOfficial) }.map { a ->
            val name = brandOf(a.pkg)?.let { L.t(it.nameAr, it.nameAr, it.name) } ?: "WhatsApp"
            val pin = p[a.pkg]
            val st = when {
                pin != null && a.certSha1.isNotEmpty() && a.certSha1.none { it.uppercase() in pin } -> Status.SIG_CHANGED
                Analyzer.isSideloaded(a, d.selfPkg) -> Status.OUTSIDE
                else -> Status.STORE
            }
            Found(a, name, st)
        }.sortedBy { it.status != Status.SIG_CHANGED && it.status != Status.OUTSIDE }
    }
}
