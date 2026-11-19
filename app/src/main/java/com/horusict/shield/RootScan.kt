/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class Conn(val uid: Int, val pkg: String, val remote: String, val port: Int, val proto: String)
data class RootData(
    val ok: Boolean,
    val conns: List<Conn>,
    val exes: List<Pair<Int, String>>,
    val modules: List<String>,
    val bootScripts: List<String>,
    val hostsExtra: List<String>,
    val natRules: List<String>,
    val selinux: String,
    val tmpFiles: List<String>,
    val raw: String,
)

/** Runs commands through su (Magisk / KernelSU). Every call shows up in the root manager's log. */
object Su {
    fun run(script: String, timeoutMs: Long = 45_000): String? = try {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = Thread {
            try { p.inputStream.bufferedReader().forEachLine { if (out.length < 4_000_000) out.append(it).append('\n') } } catch (e: Exception) { }
        }
        reader.start()
        p.outputStream.bufferedWriter().use { it.write(script); it.write("\nexit\n") }
        reader.join(timeoutMs)
        if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroy()
        out.toString()
    } catch (e: Exception) { null }

    /** Triggers the root manager's prompt the first time. */
    fun granted(): Boolean = run("id", 20_000)?.contains("uid=0") == true
}

object RootScan {
    fun enabled(): Boolean = Prefs.str("rootMode") == "1"

    fun looksRooted(ctx: Context): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk", "/data/adb/ksu")
        if (paths.any { try { File(it).exists() } catch (e: Exception) { false } }) return true
        return listOf("com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch").any {
            try { ctx.packageManager.getPackageInfo(it, 0); true } catch (e: Exception) { false }
        }
    }

    private val SCRIPT = """
echo '##TCP'; cat /proc/net/tcp /proc/net/tcp6 2>/dev/null
echo '##UDP'; cat /proc/net/udp /proc/net/udp6 2>/dev/null
echo '##EXE'; for p in /proc/[0-9]*; do e=${'$'}(readlink ${'$'}p/exe 2>/dev/null); [ -n "${'$'}e" ] && echo "${'$'}(stat -c %u ${'$'}p 2>/dev/null) ${'$'}e"; done
echo '##MOD'; for m in /data/adb/modules/*; do [ -d "${'$'}m" ] && echo "${'$'}(basename ${'$'}m)|${'$'}(grep -m1 '^name=' ${'$'}m/module.prop 2>/dev/null | cut -d= -f2-)|${'$'}( [ -f ${'$'}m/disable ] && echo off || echo on)"; done
echo '##BOOT'; for d in /data/adb/service.d /data/adb/post-fs-data.d /data/adb/boot-completed.d /system/etc/init.d; do for f in ${'$'}d/*; do [ -f "${'$'}f" ] && echo "${'$'}f"; done; done
echo '##HOSTS'; cat /system/etc/hosts 2>/dev/null
echo '##NAT'; iptables -t nat -S 2>/dev/null; ip6tables -t nat -S 2>/dev/null
echo '##SE'; getenforce 2>/dev/null
echo '##TMP'; ls -la /data/local/tmp 2>/dev/null
echo '##END'
"""

    private fun hexIp(h: String): String? {
        return try {
            if (h.length == 8) {
                val v = h.chunked(2).map { it.toInt(16) }.reversed()
                v.joinToString(".")
            } else if (h.length == 32) {
                // four little-endian 32-bit words
                val bytes = h.chunked(8).flatMap { w -> w.chunked(2).reversed() }.map { it.toInt(16) }
                if (bytes.subList(0, 10).all { it == 0 } && bytes[10] == 0xff && bytes[11] == 0xff) bytes.subList(12, 16).joinToString(".")
                else bytes.chunked(2).joinToString(":") { "%x".format(it[0] * 256 + it[1]) }
            } else null
        } catch (e: Exception) { null }
    }

    private fun isLocal(ip: String) = ip == "0.0.0.0" || ip.startsWith("127.") || ip == "0:0:0:0:0:0:0:0" || ip == "0:0:0:0:0:0:0:1" ||
        ip.startsWith("10.") || ip.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(ip) || ip.startsWith("169.254.")

    fun collect(ctx: Context): RootData {
        val out = Su.run(SCRIPT) ?: return RootData(false, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "", emptyList(), "")
        if (!out.contains("##END")) return RootData(false, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "", emptyList(), out.take(2000))
        val sec = HashMap<String, MutableList<String>>()
        var cur = ""
        for (line in out.lines()) {
            if (line.startsWith("##")) { cur = line.removePrefix("##").trim(); sec.getOrPut(cur) { ArrayList() }; continue }
            if (cur.isNotEmpty() && line.isNotBlank()) sec.getOrPut(cur) { ArrayList() }.add(line)
        }
        val pm = ctx.packageManager
        val pkgOf = HashMap<Int, String>()
        fun pkg(uid: Int): String = pkgOf.getOrPut(uid) {
            when {
                uid == 0 -> "root"
                uid == 1000 -> "system"
                uid < 10000 -> "uid $uid"
                else -> try { pm.getPackagesForUid(uid)?.joinToString(",") ?: "uid $uid" } catch (e: Exception) { "uid $uid" }
            }
        }
        val conns = ArrayList<Conn>()
        for ((name, proto) in listOf("TCP" to "tcp", "UDP" to "udp")) {
            for (l in sec[name] ?: emptyList()) {
                val f = l.trim().split(Regex("\\s+"))
                if (f.size < 8 || !f[0].endsWith(":")) continue
                val rem = f[2].split(':'); if (rem.size != 2) continue
                val ip = hexIp(rem[0]) ?: continue
                val port = rem[1].toIntOrNull(16) ?: continue
                if (port == 0 || isLocal(ip)) continue
                if (proto == "tcp" && f[3] != "01") continue // ESTABLISHED only
                val uid = f[7].toIntOrNull() ?: continue
                conns.add(Conn(uid, pkg(uid), ip, port, proto))
            }
        }
        val exes = (sec["EXE"] ?: emptyList()).mapNotNull { l ->
            val i = l.indexOf(' '); if (i < 0) null else (l.substring(0, i).toIntOrNull() ?: -1) to l.substring(i + 1).trim()
        }.distinct()
        val hostsExtra = (sec["HOSTS"] ?: emptyList()).map { it.trim() }.filter {
            it.isNotEmpty() && !it.startsWith("#") && !Regex("^(127\\.0\\.0\\.1|::1)\\s+(localhost|ip6-localhost)(\\s.*)?$").matches(it)
        }
        val nat = (sec["NAT"] ?: emptyList()).filter { it.contains("-j DNAT") || it.contains("-j REDIRECT") }
        return RootData(
            ok = true, conns = conns.distinct(), exes = exes, modules = sec["MOD"] ?: emptyList(), bootScripts = sec["BOOT"] ?: emptyList(),
            hostsExtra = hostsExtra, natRules = nat, selinux = (sec["SE"] ?: emptyList()).firstOrNull()?.trim() ?: "",
            tmpFiles = (sec["TMP"] ?: emptyList()).filter { !it.startsWith("total") && !it.trim().endsWith(" .") && !it.trim().endsWith(" ..") },
            raw = out,
        )
    }

    // ----------------------------------------------------------------- response actions (always after user confirmation)
    /** Package names go into a root shell, so anything that is not a plain package name is refused. */
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private fun safe(pkg: String) = PKG.matches(pkg) && pkg.length <= 255

    fun stop(pkg: String) = safe(pkg) && Su.run("am force-stop $pkg", 15_000) != null
    fun disable(pkg: String): Boolean { if (!safe(pkg)) return false; val ok = Su.run("pm disable-user --user 0 $pkg", 20_000)?.contains("disabled", true) == true; if (ok) remember("disable", pkg, 0); return ok }
    fun blockNet(pkg: String, uid: Int): Boolean {
        if (!safe(pkg) || uid < 10000) return false
        val ok = Su.run("iptables -I OUTPUT -m owner --uid-owner $uid -j REJECT && ip6tables -I OUTPUT -m owner --uid-owner $uid -j REJECT && echo HORUS_OK", 15_000)?.contains("HORUS_OK") == true
        if (ok) remember("block", pkg, uid)
        return ok
    }
    fun undo(kind: String, pkg: String, uid: Int): Boolean {
        if (!safe(pkg)) return false
        val ok = when (kind) {
            "disable" -> Su.run("pm enable $pkg", 20_000)?.contains("enabled", true) == true
            "block" -> Su.run("iptables -D OUTPUT -m owner --uid-owner $uid -j REJECT; ip6tables -D OUTPUT -m owner --uid-owner $uid -j REJECT; echo HORUS_OK", 15_000)?.contains("HORUS_OK") == true
            else -> false
        }
        if (ok) forget(kind, pkg)
        return ok
    }

    data class Undo(val kind: String, val pkg: String, val uid: Int, val at: Long)
    fun undoList(): List<Undo> = try {
        val a = JSONArray(Prefs.str("rootUndo") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { Undo(it.optString("kind"), it.optString("pkg"), it.optInt("uid"), it.optLong("at")) }
    } catch (e: Exception) { emptyList() }
    private fun save(l: List<Undo>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("kind", it.kind).put("pkg", it.pkg).put("uid", it.uid).put("at", it.at)) }
        Prefs.put("rootUndo", a.toString())
    }
    private fun remember(kind: String, pkg: String, uid: Int) = save(undoList().filterNot { it.kind == kind && it.pkg == pkg } + Undo(kind, pkg, uid, System.currentTimeMillis()))
    private fun forget(kind: String, pkg: String) = save(undoList().filterNot { it.kind == kind && it.pkg == pkg })

    /** Evidence: root scan output + full logcat + dmesg, shared as a text file. */
    fun exportEvidence(ctx: Context, summary: String): Intent? {
        val logs = Su.run("echo '##LOGCAT'; logcat -d -b all -t 20000 2>/dev/null; echo '##DMESG'; dmesg 2>/dev/null | tail -n 3000", 60_000) ?: return null
        val scan = Su.run(SCRIPT) ?: ""
        val dir = File(ctx.cacheDir, "evidence").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val f = File(dir, "HorusSec-evidence-$stamp.txt")
        f.writeText("Horus Shield - root evidence $stamp\n\n$summary\n\n$scan\n\n$logs")
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        File(dir, f.name + ".sha256").writeText("$sha  ${f.name}\n")
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, "SHA-256: $sha").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Horus Shield evidence",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
