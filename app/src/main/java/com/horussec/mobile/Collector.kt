/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile

import android.Manifest
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Reads the phone's state. Read-only: nothing here changes a setting or an app.
 * Everything is available to a normal app without root.
 */
object Collector {
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su", "/vendor/bin/su",
        "/su/bin/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su", "/system/app/Superuser.apk",
    )

    private fun sha1(b: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02X".format(it) }

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    private fun certsOf(pm: PackageManager, pkg: String): List<Pair<String, String>> = try {
        val sigs: Array<android.content.pm.Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val si = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            when {
                si == null -> null
                si.hasMultipleSigners() -> si.apkContentsSigners
                else -> si.signingCertificateHistory
            }
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        sigs?.map { s ->
            val b = s.toByteArray()
            val subj = try { (cf.generateCertificate(java.io.ByteArrayInputStream(b)) as X509Certificate).subjectX500Principal.name } catch (e: Exception) { "" }
            sha1(b) to subj
        } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    /** null = Android did not let us read it. */
    @Suppress("DEPRECATION")
    private fun opAllowed(ops: AppOpsManager, op: String?, uid: Int, pkg: String): Boolean? {
        if (op == null) return null
        return try {
            val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(op, uid, pkg) else ops.checkOpNoThrow(op, uid, pkg)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { null }
    }

    private fun components(s: String?): Set<String> =
        (s ?: "").split(':').mapNotNull { it.substringBefore('/').trim().ifEmpty { null } }.toSet()

    @Suppress("DEPRECATION")
    fun collect(ctx: Context, deep: Boolean = false, progress: (Int) -> Unit): DeviceData {
        val pm = ctx.packageManager
        val cr = ctx.contentResolver
        progress(0)
        val pkgs: List<PackageInfo> = try { pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) } catch (e: Exception) { emptyList() }
        val launcher = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val overlayOp = try { AppOpsManager.permissionToOp(Manifest.permission.SYSTEM_ALERT_WINDOW) } catch (e: Exception) { null }
        val installOp = try { AppOpsManager.permissionToOp(Manifest.permission.REQUEST_INSTALL_PACKAGES) } catch (e: Exception) { null }

        progress(1)
        val apps = ArrayList<AppInfo>()
        for (pi in pkgs) {
            val ai: ApplicationInfo = pi.applicationInfo ?: continue
            val system = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val req = pi.requestedPermissions ?: emptyArray()
            val flags = pi.requestedPermissionsFlags
            val granted = HashSet<String>()
            for (i in req.indices) {
                if (flags != null && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted.add(req[i])
            }
            val requested = req.toSet()
            val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { pi.packageName }
            val overlay = if (system) false else (opAllowed(ops, overlayOp, ai.uid, pi.packageName) ?: requested.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
            val certs = if (system) emptyList() else certsOf(pm, pi.packageName)
            val canInstall = if (system || !requested.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES)) false else opAllowed(ops, installOp, ai.uid, pi.packageName)
            apps.add(
                AppInfo(
                    pkg = pi.packageName, label = label, version = pi.versionName ?: "",
                    installer = if (system) null else installerOf(pm, pi.packageName),
                    firstInstall = pi.firstInstallTime, system = system, launcher = launcher.contains(pi.packageName),
                    granted = granted, requested = requested,
                    certSha1 = certs.map { it.first }, certSubjects = certs.map { it.second },
                    overlay = overlay, canInstall = canInstall,
                )
            )
        }

        progress(2)
        val acc = components(try { Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) } catch (e: Exception) { null })
        val nls = try { NotificationManagerCompat.getEnabledListenerPackages(ctx) } catch (e: Exception) { emptySet() }
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins = try { dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet() } catch (e: Exception) { emptySet() }
        val secure = try { (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure } catch (e: Exception) { false }
        val enc = try { dpm.storageEncryptionStatus } catch (e: Exception) { 0 }
        fun g(name: String): Int = try { Settings.Global.getInt(cr, name, 0) } catch (e: Exception) { 0 }

        progress(3)
        var pdActive: Boolean? = null; var pdHost: String? = null; var vpn = false; var proxy: String? = null
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            if (net != null) {
                val lp = cm.getLinkProperties(net)
                if (Build.VERSION.SDK_INT >= 28 && lp != null) { pdActive = lp.isPrivateDnsActive; pdHost = lp.privateDnsServerName }
                vpn = cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                proxy = lp?.httpProxy?.host?.ifEmpty { null }
            }
            if (proxy == null) proxy = cm.defaultProxy?.host?.ifEmpty { null }
        } catch (e: Exception) { }
        val certs = try {
            val ks = KeyStore.getInstance("AndroidCAStore"); ks.load(null)
            ks.aliases().toList().filter { it.startsWith("user:") }.map { a ->
                (ks.getCertificate(a) as? X509Certificate)?.subjectX500Principal?.name ?: a
            }
        } catch (e: Exception) { emptyList() }

        val soc = buildString {
            if (Build.VERSION.SDK_INT >= 31) append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append(' ')
            append(Build.HARDWARE).append(' ').append(Build.BOARD)
        }.lowercase()

        progress(4)
        return DeviceData(
            atMillis = System.currentTimeMillis(), selfPkg = ctx.packageName, apps = apps,
            accessibility = acc, notifListeners = nls, admins = admins, secure = secure, encryption = enc,
            devOptions = g(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1, adb = g(Settings.Global.ADB_ENABLED) == 1,
            adbWifi = g("adb_wifi_enabled") == 1, sdk = Build.VERSION.SDK_INT, release = Build.VERSION.RELEASE ?: "",
            patch = Build.VERSION.SECURITY_PATCH ?: "", buildTags = Build.TAGS ?: "",
            suPaths = suPaths.filter { try { File(it).exists() } catch (e: Exception) { false } },
            soc = soc, maker = Build.MANUFACTURER ?: "", model = Build.MODEL ?: "",
            privateDnsActive = pdActive, privateDnsHost = pdHost, vpn = vpn, proxy = proxy, userCerts = certs,
            prev = loadSnapshot(),
            deep = if (deep) DeepScan.collect(ctx, apps, { a -> Analyzer.isSideloaded(a, ctx.packageName) }, progress) else null,
            root = if (deep && RootScan.enabled()) { progress(8); RootScan.collect(ctx) } else null,
        )
    }

    fun loadSnapshot(): Snapshot? = try {
        Prefs.str("snapshot")?.let { s ->
            val o = JSONObject(s)
            fun set(k: String): Set<String> { val a = o.optJSONArray(k) ?: JSONArray(); return (0 until a.length()).map { a.optString(it) }.toSet() }
            Snapshot(set("apps"), set("acc"), set("admins"), if (o.has("score")) o.optInt("score") else null, set("iocs"))
        }
    } catch (e: Exception) { null }

    fun saveSnapshot(d: DeviceData, r: ScanResult) {
        val score = r.score
        val o = JSONObject()
        o.put("apps", JSONArray(d.apps.filter { !it.system }.map { it.pkg }))
        o.put("acc", JSONArray(d.accessibility.toList()))
        o.put("admins", JSONArray(d.admins.toList()))
        o.put("score", score)
        o.put("iocs", JSONArray(r.findings.filter { it.ioc }.map { it.key }))
        o.put("at", d.atMillis)
        Prefs.put("snapshot", o.toString())
    }
}
