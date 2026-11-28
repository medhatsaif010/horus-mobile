/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

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

    /**
     * Whether something new and important showed up since the previous scan. The notification itself never says
     * what was found: it can show on the lock screen, and spyware with notification access reads it too, so naming
     * a finding could warn whoever installed the spyware. The details are only shown inside the app.
     */
    fun alertFor(d: DeviceData, r: ScanResult): Boolean {
        val prev = d.prev ?: return false
        if (r.findings.any { it.ioc && it.key !in prev.iocs }) return true
        val byPkg = d.apps.associateBy { it.pkg }
        if (d.accessibility.any { it !in prev.acc && byPkg[it]?.system == false }) return true
        if (d.admins.any { it !in prev.admins && byPkg[it]?.system == false }) return true
        return d.apps.any { it.pkg !in prev.apps && Analyzer.isSideloaded(it, d.selfPkg) }
    }

    fun neutralTitle() = "Horus Shield"
    fun neutralBody() = L.t("لديك نتيجة فحص جديدة. افتح التطبيق لمراجعتها.", "عندك نتيجة فحص جديدة. افتح البرنامج علشان تراجعها.", "You have a new scan result. Open the app to review it.")

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, title: String, body: String) {
        try {
            ensureChannel(ctx)
            // a one-time token, so only this notification (not any other app) can make Horus Shield start a scan
            val token = java.util.UUID.randomUUID().toString()
            Prefs.put("openToken", token)
            val open = Intent(ctx, MainActivity::class.java).putExtra("horus_open_result", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title).setContentText(body.lines().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi)
                .setColor(0xFFD4AF37.toInt())
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notify).setContentTitle("Horus Shield").build())
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
        Studio.load(c)
        try {
            val age = Intel.ageDays(Intel.get(c)) ?: 99
            if (age >= 1) Intel.update(c)
        } catch (e: Exception) { }
        return try {
            val intel = Intel.get(c)
            val d = Collector.collect(c, deep = true) { }
            val r = Analyzer.analyze(d, intel)
            Collector.saveSnapshot(d, r)
            Prefs.put("lastScore", r.score.toString()); Prefs.putLong("lastScanAt", r.atMillis)
            Prefs.put("lastVerdict", r.verdict); Prefs.putLong("lastAutoScan", r.atMillis)
            History.add(r, auto = true)
            if (AutoScan.alertFor(d, r)) AutoScan.notify(c, AutoScan.neutralTitle(), AutoScan.neutralBody())
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
