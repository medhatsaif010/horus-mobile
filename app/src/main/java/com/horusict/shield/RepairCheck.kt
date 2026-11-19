/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Before you hand the phone in for repair": saves a detailed picture of the phone, and after
 * it comes back, lists exactly what changed. Stays on the phone; nothing is sent anywhere.
 */
object RepairCheck {
    private const val FILE = "repair-baseline.json"
    private val sensitive = setOf(
        "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
        "android.permission.READ_CALL_LOG", "android.permission.RECORD_AUDIO", "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS", "android.permission.READ_PHONE_STATE",
    )

    data class Change(val sev: Sev, val text: String, val pkg: String? = null)

    fun savedAt(ctx: Context): Long = try { JSONObject(File(ctx.filesDir, FILE).readText()).optLong("at") } catch (e: Exception) { 0L }

    fun clear(ctx: Context) { File(ctx.filesDir, FILE).delete() }

    fun save(ctx: Context, d: DeviceData) {
        val o = JSONObject()
        o.put("at", d.atMillis)
        o.put("fingerprint", Build.FINGERPRINT ?: "")
        o.put("patch", d.patch); o.put("release", d.release); o.put("tags", d.buildTags)
        o.put("secure", d.secure); o.put("adb", d.adb); o.put("adbWifi", d.adbWifi); o.put("dev", d.devOptions)
        o.put("proxy", d.proxy ?: ""); o.put("vpn", d.vpn); o.put("certs", JSONArray(d.userCerts))
        o.put("su", JSONArray(d.suPaths))
        o.put("acc", JSONArray(d.accessibility.toList())); o.put("nl", JSONArray(d.notifListeners.toList())); o.put("admins", JSONArray(d.admins.toList()))
        val apps = JSONObject()
        for (a in d.apps) {
            if (a.system || a.pkg == d.selfPkg) continue
            apps.put(a.pkg, JSONObject().apply {
                put("label", a.label); put("ver", a.version); put("inst", a.installer ?: ""); put("init", a.initiator ?: "")
                put("certs", JSONArray(a.certSha1)); put("perms", JSONArray(a.granted.filter { it in sensitive }))
                put("overlay", a.overlay); put("canInstall", a.canInstall == true); put("launcher", a.launcher)
            })
        }
        o.put("apps", apps)
        o.put("systemCount", d.apps.count { it.system })
        File(ctx.filesDir, FILE).writeText(o.toString())
    }

    private fun set(o: JSONObject, k: String): Set<String> { val a = o.optJSONArray(k) ?: JSONArray(); return (0 until a.length()).map { a.optString(it) }.toSet() }

    /** null when nothing was saved yet. */
    fun compare(ctx: Context, d: DeviceData): List<Change>? {
        val o = try { JSONObject(File(ctx.filesDir, FILE).readText()) } catch (e: Exception) { return null }
        val out = ArrayList<Change>()
        val byPkg = d.apps.associateBy { it.pkg }
        fun label(p: String) = byPkg[p]?.label?.takeIf { it != p }?.let { "$it ($p)" } ?: p
        fun sh(p: String) = p.removePrefix("android.permission.")
        val before = o.optJSONObject("apps") ?: JSONObject()
        val now = d.apps.filter { !it.system && it.pkg != d.selfPkg }

        // firmware
        if ((Build.FINGERPRINT ?: "") != o.optString("fingerprint")) {
            val sameVer = d.release == o.optString("release") && d.patch == o.optString("patch")
            out.add(Change(if (sameVer) Sev.HIGH else Sev.MED,
                if (sameVer) L.t("أُعيد تثبيت نظام الهاتف أو عُدّل دون تحديث رسمي", "سوفت وير الموبايل اتعاد تسطيبه أو اتعدّل من غير تحديث رسمي", "The phone's system was reinstalled or modified without an official update")
                else L.t("تغيّر نظام الهاتف: Android ${o.optString("release")} (${o.optString("patch")}) ← ${d.release} (${d.patch})", "سوفت وير الموبايل اتغيّر: Android ${o.optString("release")} (${o.optString("patch")}) ← ${d.release} (${d.patch})", "The phone's system changed: Android ${o.optString("release")} (${o.optString("patch")}) → ${d.release} (${d.patch})")))
        }
        if (d.suPaths.isNotEmpty() && set(o, "su").isEmpty()) out.add(Change(Sev.CRIT, L.t("ظهرت صلاحية Root على الهاتف", "الموبايل بقى عليه Root", "The phone has been rooted")))
        if (d.buildTags.contains("test-keys") && !o.optString("tags").contains("test-keys")) out.add(Change(Sev.HIGH, L.t("النظام أصبح نسخة غير رسمية (test-keys)", "السوفت وير بقى نسخة مش رسمية (test-keys)", "The system is now an unofficial build (test-keys)")))
        val sysBefore = o.optInt("systemCount", -1)
        val sysNow = d.apps.count { it.system }
        if (sysBefore >= 0 && sysNow > sysBefore) out.add(Change(Sev.MED, L.t("زاد عدد تطبيقات النظام ${sysNow - sysBefore}", "تطبيقات النظام زادت ${sysNow - sysBefore}", "${sysNow - sysBefore} more system apps than before")))

        // settings
        if (o.optBoolean("secure") && !d.secure) out.add(Change(Sev.HIGH, L.t("أُزيل قفل الشاشة", "قفل الشاشة اتشال", "The screen lock was removed")))
        if (!o.optBoolean("adb") && d.adb) out.add(Change(Sev.MED, L.t("فُعّل USB debugging", "USB debugging اتفتح", "USB debugging was turned on")))
        if (!o.optBoolean("adbWifi") && d.adbWifi) out.add(Change(Sev.HIGH, L.t("فُعّل Wireless debugging", "Wireless debugging اتفتح", "Wireless debugging was turned on")))
        if (!o.optBoolean("dev") && d.devOptions) out.add(Change(Sev.LOW, L.t("فُعّلت خيارات المطورين", "خيارات المطورين اتفتحت", "Developer options were turned on")))
        if (o.optString("proxy").isEmpty() && d.proxy != null) out.add(Change(Sev.HIGH, L.t("أُضيف Proxy: ${d.proxy}", "اتضاف Proxy: ${d.proxy}", "A proxy was added: ${d.proxy}")))
        set(o, "certs").let { b -> d.userCerts.filter { it !in b }.forEach { out.add(Change(Sev.HIGH, L.t("أُضيفت شهادة أمان: ", "اتضافت شهادة أمان: ", "Security certificate added: ") + it)) } }
        set(o, "acc").let { b -> d.accessibility.filter { it !in b && byPkg[it]?.system != true }.forEach { out.add(Change(Sev.HIGH, L.t("صلاحية Accessibility جديدة: ", "صلاحية Accessibility جديدة: ", "New Accessibility access: ") + label(it), it)) } }
        set(o, "admins").let { b -> d.admins.filter { it !in b && byPkg[it]?.system != true }.forEach { out.add(Change(Sev.HIGH, L.t("Device admin جديد: ", "Device admin جديد: ", "New device admin: ") + label(it), it)) } }
        set(o, "nl").let { b -> d.notifListeners.filter { it !in b && byPkg[it]?.system != true }.forEach { out.add(Change(Sev.HIGH, L.t("تطبيق جديد يقرأ الإشعارات: ", "تطبيق جديد بيقرا الإشعارات: ", "New app reading notifications: ") + label(it), it)) } }

        // apps
        for (a in now) {
            val b = before.optJSONObject(a.pkg)
            if (b == null) {
                val side = Analyzer.isSideloaded(a, d.selfPkg)
                val sens = a.granted.filter { it in sensitive }
                val sev = when { !a.launcher && sens.isNotEmpty() -> Sev.CRIT; side || !a.launcher -> Sev.HIGH; sens.isNotEmpty() -> Sev.MED; else -> Sev.LOW }
                var t = L.t("تطبيق جديد: ", "تطبيق جديد: ", "New app: ") + label(a.pkg)
                if (side) t += L.t(" · من خارج المتجر", " · من برّه المتجر", " · from outside a store")
                if (!a.launcher) t += L.t(" · مخفي", " · مخفي", " · hidden")
                if (sens.isNotEmpty()) t += " · " + sens.joinToString(", ") { sh(it) }
                out.add(Change(sev, t, a.pkg))
                continue
            }
            val oldCerts = set(b, "certs")
            if (oldCerts.isNotEmpty() && a.certSha1.isNotEmpty() && a.certSha1.none { it in oldCerts })
                out.add(Change(Sev.CRIT, L.t("تطبيق استُبدل بنسخة توقيعها مختلف: ", "تطبيق اتبدّل بنسخة توقيعها مختلف: ", "App replaced by a copy with a different signature: ") + label(a.pkg), a.pkg))
            val newPerms = a.granted.filter { it in sensitive && it !in set(b, "perms") }
            if (newPerms.isNotEmpty()) out.add(Change(Sev.MED, L.t("صلاحيات جديدة لـ ", "صلاحيات جديدة لـ ", "New permissions for ") + label(a.pkg) + ": " + newPerms.joinToString(", ") { sh(it) }, a.pkg))
            if (!b.optBoolean("overlay") && a.overlay) out.add(Change(Sev.MED, L.t("أصبح يظهر فوق التطبيقات: ", "بقى يظهر فوق التطبيقات: ", "Can now draw over other apps: ") + label(a.pkg), a.pkg))
            if (!b.optBoolean("canInstall") && a.canInstall == true) out.add(Change(Sev.MED, L.t("أصبح مسموحًا له بتثبيت تطبيقات: ", "بقى مسموحله يسطّب تطبيقات: ", "Now allowed to install apps: ") + label(a.pkg), a.pkg))
            if (b.optBoolean("launcher") && !a.launcher) out.add(Change(Sev.HIGH, L.t("اختفت أيقونته من الشاشة: ", "أيقونته اختفت من الشاشة: ", "Its icon disappeared from the home screen: ") + label(a.pkg), a.pkg))
            if (b.optString("ver") != a.version) out.add(Change(Sev.LOW, L.t("تحدّث: ", "اتحدّث: ", "Updated: ") + label(a.pkg) + " ${b.optString("ver")} ← ${a.version}", a.pkg))
        }
        val gone = before.keys().asSequence().filter { it !in byPkg }.toList()
        if (gone.isNotEmpty()) out.add(Change(Sev.MED, L.t("حُذفت ${gone.size} تطبيقات: ", "${gone.size} تطبيق اتمسحوا: ", "${gone.size} apps removed: ") + gone.take(8).joinToString(", ") { before.optJSONObject(it)?.optString("label")?.ifEmpty { it } ?: it }))
        return out.sortedBy { it.sev.rank }
    }
}
