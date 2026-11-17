/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Language switch: "en" English (default), "ar" Modern Standard Arabic, "eg" Egyptian Arabic.
 *  Every user-facing string goes through L.t(msa, eg, en). */
object L {
    var lang by mutableStateOf("en")
    val ar: Boolean get() = lang != "en"
    fun t(a: String, e: String): String = if (ar) a else e
    fun t(msa: String, eg: String, en: String): String = when (lang) { "ar" -> msa; "eg" -> eg; else -> en }
    fun load() { lang = when (Prefs.str("lang")) { "ar" -> "ar"; "eg" -> "eg"; else -> "en" } }
}

object Prefs {
    private var sp: SharedPreferences? = null
    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("horus", Context.MODE_PRIVATE)
    }
    fun str(key: String): String? = sp?.getString(key, null)
    fun put(key: String, value: String?) {
        sp?.edit()?.apply { if (value == null) remove(key) else putString(key, value) }?.apply()
    }
    fun long(key: String): Long = sp?.getLong(key, 0L) ?: 0L
    fun putLong(key: String, value: Long) { sp?.edit()?.putLong(key, value)?.apply() }
}

enum class Sev(val weight: Int, val rank: Int) { CRIT(25, 0), HIGH(8, 1), MED(3, 2), LOW(1, 3) }

enum class Fix {
    APP_SETTINGS, UNINSTALL, ACCESSIBILITY, SECURITY, NOTIFICATIONS, UPDATE, LOCK,
    DEVELOPER, NETWORK, VPN, UNKNOWN_SOURCES,
    ROOT_STOP, ROOT_DISABLE, ROOT_BLOCKNET,
}

data class Finding(
    val key: String,
    val sev: Sev,
    val title: String,
    val why: String,
    val todo: String,
    val evidence: List<String> = emptyList(),
    val pkg: String? = null,
    val fixes: List<Fix> = emptyList(),
    val ioc: Boolean = false,
    val stalker: Boolean = false,
)

enum class CheckState { PASS, WARN, FAIL }

data class Check(
    val name: String,
    val state: CheckState,
    val detail: String,
    val hint: String = "",
    val fix: Fix? = null,
)

data class Exposure(val label: String, val apps: List<String>)

data class ScanResult(
    val atMillis: Long,
    val score: Int,
    val grade: String,
    val verdict: String,
    val headline: String,
    val findings: List<Finding>,
    val checks: List<Check>,
    val changes: List<String>,
    val verified: List<String>,
    val exposure: List<Exposure>,
    val appCount: Int,
    val userAppCount: Int,
    val intelDate: String,
    val deep: Boolean,
    val iocCount: Int,
)

data class AppInfo(
    val pkg: String,
    val label: String,
    val version: String,
    val installer: String?,
    val firstInstall: Long,
    val system: Boolean,
    val launcher: Boolean,
    val granted: Set<String>,
    val requested: Set<String>,
    val certSha1: List<String>,
    val certSubjects: List<String>,
    val overlay: Boolean,
    val canInstall: Boolean?,
)

data class Snapshot(
    val apps: Set<String>,
    val acc: Set<String>,
    val admins: Set<String>,
    val score: Int?,
    val iocs: Set<String>,
)

data class DeviceData(
    val atMillis: Long,
    val selfPkg: String,
    val apps: List<AppInfo>,
    val accessibility: Set<String>,
    val notifListeners: Set<String>,
    val admins: Set<String>,
    val secure: Boolean,
    val encryption: Int,
    val devOptions: Boolean,
    val adb: Boolean,
    val adbWifi: Boolean,
    val sdk: Int,
    val release: String,
    val patch: String,
    val buildTags: String,
    val suPaths: List<String>,
    val soc: String,
    val maker: String,
    val model: String,
    val privateDnsActive: Boolean?,
    val privateDnsHost: String?,
    val vpn: Boolean,
    val proxy: String?,
    val userCerts: List<String>,
    val prev: Snapshot?,
    val deep: DeepData?,
    val root: RootData? = null,
)

data class Signal(val level: String, val text: String)

data class UrlResult(
    val ok: Boolean,
    val error: String = "",
    val url: String = "",
    val finalUrl: String = "",
    val domain: String = "",
    val chain: List<String> = emptyList(),
    val ageDays: Int? = null,
    val reach: String = "",
    val verdict: String = "",
    val signals: List<Signal> = emptyList(),
)
