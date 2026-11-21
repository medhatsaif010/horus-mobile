/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub for a new app version about twice a day, in the background, and posts
 * one notification per new version. Tapping it opens the About screen, where the user
 * downloads and confirms the install. Nothing is downloaded or installed in the background.
 */
object UpdateCheck {
    private const val WORK = "horus-update-check"
    private const val CHANNEL = "horus_updates"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Posts the notification once per version code. */
    fun notifyOnce(ctx: Context, info: UpdateInfo) {
        if (Prefs.str("notifiedUpdate") == info.code.toString()) return
        if (post(ctx, info)) Prefs.put("notifiedUpdate", info.code.toString())
    }

    /** Posts an urgent alert from Horus Studio once per alert id. Plain text, opens the app. */
    fun alertOnce(ctx: Context, a: Studio.Alert) {
        if (Prefs.str("alertNotified") == a.id || Studio.dismissed(a)) return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, L.t("تحديثات التطبيق", "تحديثات التطبيق", "App updates"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(ctx, 3, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val text = Studio.text(a)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(L.t("تحذير من Horus Shield", "تحذير من Horus Shield", "Warning from Horus Shield"))
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pi).setColor(0xFFD4AF37.toInt())
                .build()
            NotificationManagerCompat.from(ctx).notify(44, n)
            Prefs.put("alertNotified", a.id)
        } catch (e: Exception) { }
    }

    fun post(ctx: Context, info: UpdateInfo): Boolean = try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, L.t("تحديثات التطبيق", "تحديثات التطبيق", "App updates"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = L.t("عند صدور نسخة جديدة من Horus Shield", "لما تنزل نسخة جديدة من Horus Shield", "When a new version of Horus Shield is out")
            })
        }
        val open = Intent(ctx, MainActivity::class.java).putExtra("horus_open_about", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = L.t("يتوفر تحديث جديد: V${info.name}", "فيه تحديث جديد: V${info.name}", "Update available: V${info.name}")
        val body = L.t("اضغط لتنزيل النسخة الجديدة من Horus Shield.", "دوس علشان تنزّل النسخة الجديدة من Horus Shield.", "Tap to download the new version of Horus Shield.")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title).setContentText(body)
            .setAutoCancel(true).setContentIntent(pi)
            .setColor(0xFFD4AF37.toInt())
            .build()
        NotificationManagerCompat.from(ctx).notify(43, n)
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    } catch (e: SecurityException) { false } catch (e: Exception) { false }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Prefs.init(c)
        L.load()
        Studio.load(c)
        return try {
            // an urgent warning from Horus Studio: shown once as a notification
            Studio.fetchAlert(force = true)?.let { UpdateCheck.alertOnce(c, it) }
            Updater.check()?.let { UpdateCheck.notifyOnce(c, it) }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
