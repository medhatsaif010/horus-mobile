/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps the threat intel fresh every day, whether or not the user opens the app or turned on automatic scans.
 * Downloads only the signed pack (no data about the user is sent) and only when a network is available.
 */
object IntelWork {
    private const val WORK = "horus-intel-daily"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<IntelWorker>(24, TimeUnit.HOURS, 4, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class IntelWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Prefs.init(c)
        val age = Intel.ageDays(Intel.get(c)) ?: 99
        if (age < 1) return Result.success()
        return if (Intel.update(c) == null) Result.success() else Result.retry()
    }
}
