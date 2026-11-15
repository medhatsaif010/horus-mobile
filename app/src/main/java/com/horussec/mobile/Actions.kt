package com.horussec.mobile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Opens the right Android settings page. Horus never changes settings itself: the user does. */
object Actions {
    fun label(f: Fix): String = when (f) {
        Fix.APP_SETTINGS -> L.t("إعدادات التطبيق", "App settings")
        Fix.UNINSTALL -> L.t("امسح التطبيق", "Uninstall")
        Fix.ACCESSIBILITY -> L.t("افتح Accessibility", "Open Accessibility")
        Fix.SECURITY -> L.t("افتح إعدادات الأمان", "Open security settings")
        Fix.NOTIFICATIONS -> L.t("افتح قراية الإشعارات", "Open notification access")
        Fix.UPDATE -> L.t("افتح تحديث النظام", "Open system update")
        Fix.LOCK -> L.t("حط قفل شاشة", "Set a screen lock")
        Fix.DEVELOPER -> L.t("افتح خيارات المطورين", "Open developer options")
        Fix.NETWORK -> L.t("افتح إعدادات الشبكة", "Open network settings")
        Fix.VPN -> L.t("افتح إعدادات VPN", "Open VPN settings")
        Fix.UNKNOWN_SOURCES -> L.t("افتح تسطيب التطبيقات المجهولة", "Open install unknown apps")
    }

    private fun intentFor(f: Fix, pkg: String?): List<Intent> = when (f) {
        Fix.APP_SETTINGS -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg ?: "", null)))
        Fix.UNINSTALL -> listOf(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg ?: "", null)), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg ?: "", null)))
        Fix.ACCESSIBILITY -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Fix.SECURITY -> listOf(Intent(Settings.ACTION_SECURITY_SETTINGS))
        Fix.NOTIFICATIONS -> listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        Fix.UPDATE -> listOf(Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        Fix.LOCK -> listOf(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), Intent(Settings.ACTION_SECURITY_SETTINGS))
        Fix.DEVELOPER -> listOf(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        Fix.NETWORK -> listOf(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        Fix.VPN -> listOf(Intent(Settings.ACTION_VPN_SETTINGS))
        Fix.UNKNOWN_SOURCES -> listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
    }

    fun open(ctx: Context, f: Fix, pkg: String? = null) {
        for (i in intentFor(f, pkg) + Intent(Settings.ACTION_SETTINGS)) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
        }
    }

    fun shareText(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try { ctx.startActivity(Intent.createChooser(i, "Horus Sec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }

    fun summary(r: ScanResult, d: DeviceData?): String = buildString {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        appendLine("Horus Sec Mobile — ${L.t("تقرير فحص الموبايل", "Phone scan report")}")
        appendLine("${L.t("الوقت", "Time")}: ${fmt.format(Date(r.atMillis))}")
        if (d != null) appendLine("${L.t("الموبايل", "Phone")}: ${d.maker} ${d.model}, Android ${d.release}, ${L.t("تحديث أمني", "patch")} ${d.patch}")
        appendLine("${L.t("رقم الأمان", "Security score")}: ${r.score}/100 (${r.grade})")
        appendLine(r.headline)
        appendLine()
        for (f in r.findings) {
            appendLine("[${f.sev.name}] ${f.title}")
            f.evidence.take(6).forEach { appendLine("   - $it") }
        }
        if (r.changes.isNotEmpty()) {
            appendLine(); appendLine(L.t("اتغيّر من آخر فحص:", "Changed since last scan:"))
            r.changes.forEach { appendLine(" - $it") }
        }
        appendLine(); appendLine("${L.t("قاعدة التهديدات", "Threat intel")}: ${r.intelDate}")
    }
}
