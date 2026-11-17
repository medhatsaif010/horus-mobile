/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Automatic deep scan every N hours (WorkManager). Android may delay a run a little
 * to save battery, but it always runs. A notification is shown only when something
 * new and important shows up, never for "all good".
 */
object AutoScan {
    private const val WORK = "horus-auto-scan"
    const val CHANNEL = "horus_alerts"
    val choices = listOf(0, 3, 6, 12, 24)

    fun hours(): Int = Prefs.str("autoHours")?.toIntOrNull() ?: 0

    fun apply(ctx: Context, h: Int) {
        Prefs.put("autoHours", h.toString())
        val wm = WorkManager.getInstance(ctx)
        if (h <= 0) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<ScanWorker>(h.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setInitialDelay(h.toLong(), TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, L.t("تنبيهات الأمان", "تنبيهات الأمان", "Security alerts"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = L.t("عندما يجد الفحص التلقائي شيئًا جديدًا ومهمًا", "لما الفحص التلقائي يلاقي حاجة جديدة ومهمة", "When the automatic scan finds something new and important")
            })
        }
    }

    fun notificationsAllowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** What is new since the previous scan and worth waking the user for. */
    fun alertFor(d: DeviceData, r: ScanResult): Pair<String, String>? {
        val prev = d.prev ?: return null
        val newIocs = r.findings.filter { it.ioc && it.key !in prev.iocs }
        val byPkg = d.apps.associateBy { it.pkg }
        val newAcc = d.accessibility.filter { it !in prev.acc && byPkg[it]?.system == false }
        val newAdm = d.admins.filter { it !in prev.admins && byPkg[it]?.system == false }
        val newSide = d.apps.filter { it.pkg !in prev.apps && Analyzer.isSideloaded(it, d.selfPkg) }
        if (newIocs.isNotEmpty()) {
            val title = if (newIocs.any { it.sev == Sev.CRIT }) L.t("Horus Shield: علامات اختراق على الهاتف", "Horus Shield: علامات اختراق على الموبايل", "Horus Shield: signs of compromise on this phone")
            else L.t("Horus Shield: أمر جديد يحتاج إلى التحقق منه", "Horus Shield: حاجة جديدة محتاجة تتأكد منها", "Horus Shield: something new needs checking")
            return title to newIocs.first().title
        }
        val lines = newAcc.map { L.t("صلاحية Accessibility جديدة: ", "صلاحية Accessibility جديدة: ", "New Accessibility access: ") + (byPkg[it]?.label ?: it) } +
            newAdm.map { L.t("Device admin جديد: ", "Device admin جديد: ", "New device admin: ") + (byPkg[it]?.label ?: it) } +
            newSide.map { L.t("تطبيق جديد من خارج المتجر: ", "تطبيق جديد من برّه المتجر: ", "New app from outside a store: ") + it.label }
        if (lines.isEmpty()) return null
        return L.t("Horus Shield: تغيّر شيء على الهاتف", "Horus Shield: اتغيّر حاجة على الموبايل", "Horus Shield: something changed on the phone") to lines.take(3).joinToString("\n")
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, title: String, body: String) {
        try {
            ensureChannel(ctx)
            val open = Intent(ctx, MainActivity::class.java).putExtra("horus_open_result", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title).setContentText(body.lines().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi)
                .setColor(0xFFF0B429.toInt())
                .build()
            NotificationManagerCompat.from(ctx).notify(42, n)
        } catch (e: Exception) { }
    }
}

class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Prefs.init(c)
        L.load()
        try {
            val age = Intel.ageDays(Intel.get(c)) ?: 99
            if (age >= 2) Intel.update(c)
        } catch (e: Exception) { }
        return try {
            val intel = Intel.get(c)
            val d = Collector.collect(c, deep = true) { }
            val r = Analyzer.analyze(d, intel)
            Collector.saveSnapshot(d, r)
            Prefs.put("lastScore", r.score.toString()); Prefs.putLong("lastScanAt", r.atMillis)
            Prefs.put("lastVerdict", r.verdict); Prefs.putLong("lastAutoScan", r.atMillis)
            History.add(r, auto = true)
            AutoScan.alertFor(d, r)?.let { (t, b) -> AutoScan.notify(c, t, b) }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
