/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(val code: Int, val name: String, val url: String, val sha256: String, val notes: String)

/**
 * App updates for builds installed outside Google Play. Reads version.json from the
 * GitHub release, downloads the new APK over HTTPS, checks its SHA-256 against
 * version.json, then hands it to Android's installer. Android always asks the user
 * to confirm; the app cannot update itself silently.
 */
object Updater {
    private val META = listOf(
        "https://github.com/medhatsaif010/horus-mobile-releases/releases/download/apk/version.json",
        "https://github.com/medhatsaif010/horus-mobile/releases/download/apk/version.json",
    )
    private val allowedHosts = Regex("(^|\\.)(github\\.com|githubusercontent\\.com)$")

    /** Blocking. Returns the newer version, or null if this is the latest. Throws on network errors. */
    fun check(): UpdateInfo? {
        var last: Exception? = null
        var text: String? = null
        for (u in META) { try { text = String(Intel.download(u, 20_000), Charsets.UTF_8); break } catch (e: Exception) { last = e } }
        val o = JSONObject(text ?: throw (last ?: Exception("net")))
        val info = UpdateInfo(
            o.optInt("versionCode"), o.optString("versionName"), o.optString("url"),
            o.optString("sha256").lowercase(), o.optString("notes"),
        )
        Prefs.putLong("updateChecked", System.currentTimeMillis())
        return if (info.code > BuildConfig.VERSION_CODE && info.url.startsWith("https://") && info.sha256.length == 64) info else null
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
        )
        for (i in tries) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { } }
    }

    /** Blocking. Downloads and verifies; returns the file or throws with "sha" / "net". */
    fun download(ctx: Context, info: UpdateInfo, progress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "HorusSec-Mobile-${info.code}.apk")
        var cur = URL(info.url)
        for (hop in 0 until 6) {
            if (cur.protocol != "https" || !allowedHosts.containsMatchIn(cur.host)) throw Exception("net")
            val c = cur.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("User-Agent", "HorusSec-Mobile")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw Exception("net")
                c.disconnect(); cur = URL(cur, loc); continue
            }
            if (code != 200) { c.disconnect(); throw Exception("net") }
            val total = c.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(32768); var done = 0L; var last = -1
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (done > 200_000_000) throw Exception("net")
                        if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; progress(p) } }
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (hex != info.sha256) { out.delete(); throw Exception("sha") }
            return out
        }
        throw Exception("net")
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    @Suppress("unused")
    private val sdk = Build.VERSION.SDK_INT
}
