package com.horussec.mobile

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest

data class DeepData(
    val apkHashes: Map<String, String>,
    val hashSkipped: Int,
    val bootReceivers: Set<String>,
    val dnsChecked: Boolean,
    val dnsBad: List<String>,
    val deviceOwner: String?,
    val profileOwners: List<String>,
    val usageAccess: Boolean,
    val uploads: Map<String, Long>,
    val durationMs: Long,
)

/**
 * The deep part of a scan. Still read-only and without root:
 *  - SHA-256 of the APK of every app installed from outside a store (and hidden apps),
 *    compared with known-malware hashes in the signed intel
 *  - which apps start themselves when the phone boots
 *  - whether the current network answers DNS honestly (DNS hijacking)
 *  - device-owner / profile-owner apps (full remote control of the phone)
 *  - with Usage access: how much each app uploaded in the last 7 days
 */
object DeepScan {
    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    fun openUsageAccess(ctx: Context) {
        for (i in listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
        }
    }

    private fun sha256(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins -> val buf = ByteArray(65536); while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { null }

    private fun dnsIs(name: String, ok: Set<String>): String? = try {
        val got = InetAddress.getAllByName(name).mapNotNull { it.hostAddress?.lowercase() }
        val bad = got.filter { it !in ok }
        if (got.isNotEmpty() && bad.isNotEmpty()) "$name -> ${bad.joinToString(", ")}" else null
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    fun collect(ctx: Context, apps: List<AppInfo>, isSide: (AppInfo) -> Boolean, progress: (Int) -> Unit): DeepData {
        val started = System.currentTimeMillis()
        val pm = ctx.packageManager

        // 1. APK hashes: sideloaded, hidden and system apps (preloaded malware on cheap phones).
        //    Hashes are cached by path + size + date, so only the first deep scan is slow.
        progress(5)
        val cacheFile = File(ctx.filesDir, "hash-cache.json")
        val cache = try { org.json.JSONObject(cacheFile.readText()) } catch (e: Exception) { org.json.JSONObject() }
        val fresh = org.json.JSONObject()
        val hashes = HashMap<String, String>(); var skipped = 0
        for (a in apps) {
            if (a.pkg == ctx.packageName || (!a.system && !isSide(a) && a.launcher)) continue
            val path = try { pm.getApplicationInfo(a.pkg, 0).sourceDir } catch (e: Exception) { null } ?: continue
            val f = File(path)
            if (!f.canRead() || f.length() > 150L * 1024 * 1024) { skipped++; continue }
            val ck = path + "|" + f.length() + "|" + f.lastModified()
            val h = cache.optString(ck, "").ifEmpty { null } ?: sha256(f)
            if (h == null) { skipped++; continue }
            hashes[a.pkg] = h; fresh.put(ck, h)
        }
        try { cacheFile.writeText(fresh.toString()) } catch (e: Exception) { }

        // 2. apps that start on boot
        val boot = try {
            pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED), 0).mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) { emptySet() }

        // 3. DNS honesty on the current network
        progress(6)
        val dnsBad = ArrayList<String>(); var dnsChecked = false
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (cm.activeNetwork != null) {
                dnsChecked = true
                dnsIs("dns.google", setOf("8.8.8.8", "8.8.4.4", "2001:4860:4860:0:0:0:0:8888", "2001:4860:4860:0:0:0:0:8844", "2001:4860:4860::8888", "2001:4860:4860::8844"))?.let { dnsBad.add(it) }
                dnsIs("one.one.one.one", setOf("1.1.1.1", "1.0.0.1", "2606:4700:4700:0:0:0:0:1111", "2606:4700:4700:0:0:0:0:1001", "2606:4700:4700::1111", "2606:4700:4700::1001"))?.let { dnsBad.add(it) }
            }
        } catch (e: Exception) { }

        // 4. device owner / profile owner
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins = try { dpm.activeAdmins?.map { it.packageName }?.distinct() ?: emptyList() } catch (e: Exception) { emptyList() }
        val owner = admins.firstOrNull { p -> try { dpm.isDeviceOwnerApp(p) } catch (e: Exception) { false } }
        val profile = admins.filter { p -> try { dpm.isProfileOwnerApp(p) } catch (e: Exception) { false } }

        // 5. uploads per app, last 7 days (needs Usage access)
        progress(7)
        val usage = hasUsageAccess(ctx)
        val up = HashMap<String, Long>()
        if (usage) {
            try {
                val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
                val end = System.currentTimeMillis(); val start = end - 7L * 24 * 3600_000L
                val byUid = HashMap<Int, Long>()
                for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                    try {
                        val st = nsm.querySummary(type, null, start, end)
                        val b = NetworkStats.Bucket()
                        while (st.hasNextBucket()) { st.getNextBucket(b); byUid[b.uid] = (byUid[b.uid] ?: 0L) + b.txBytes }
                        st.close()
                    } catch (e: Exception) { }
                }
                for ((uid, tx) in byUid) {
                    val pk = try { pm.getPackagesForUid(uid) } catch (e: Exception) { null } ?: continue
                    if (pk.size == 1) up[pk[0]] = tx
                }
            } catch (e: Exception) { }
        }
        return DeepData(hashes, skipped, boot, dnsChecked, dnsBad, owner, profile, usage, up, System.currentTimeMillis() - started)
    }

}
