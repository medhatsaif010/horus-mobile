/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import com.horussec.mobile.ui.dayLabel
import com.horussec.mobile.ui.verdictTitle

/** The seal on the phone's home screen: the last verdict, and a tap opens the app. */
class SealWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    companion object {
        private const val SAFE = 0xFF3CC8B6.toInt()
        private const val WARN = 0xFFF2994A.toInt()
        private const val DANGER = 0xFFE5534B.toInt()
        private const val GOLD = 0xFFE7C76A.toInt()

        fun update(ctx: Context) {
            try {
                Prefs.init(ctx)
                L.ar = (Prefs.str("lang") ?: "ar") == "ar"
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, SealWidget::class.java))
                if (ids.isEmpty()) return
                val v = RemoteViews(ctx.packageName, R.layout.widget_seal)
                v.setImageViewBitmap(R.id.seal, bitmap(ctx, 400))
                val at = Prefs.long("lastScanAt")
                val verdict = Prefs.str("lastVerdict")
                val score = Prefs.str("lastScore")
                v.setTextViewText(R.id.verdict, if (at == 0L) "HORUS SHIELD" else verdictTitle(verdict) + (score?.let { "  $it%" } ?: ""))
                v.setTextColor(R.id.verdict, when { at == 0L -> GOLD; verdict == "compromised" -> DANGER; verdict == "suspicious" -> WARN; else -> SAFE })
                v.setTextViewText(R.id.when_, if (at > 0) L.t("آخر فحص: ", "Last scan: ") + dayLabel(at) else L.t("اضغط لأول فحص", "Tap to run a first scan"))
                val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                v.setOnClickPendingIntent(R.id.root, open)
                mgr.updateAppWidget(ids, v)
            } catch (e: Exception) { }
        }

        /** The gold eye as a bitmap (RemoteViews cannot scale vectors on every launcher). */
        fun bitmap(ctx: Context, px: Int): Bitmap {
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.art_eye)!!
            val h = px * d.intrinsicHeight / d.intrinsicWidth
            val b = Bitmap.createBitmap(px, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, px, h); d.draw(Canvas(b))
            return b
        }

        /** Asks the launcher to place the widget (Android 8+). Returns false if the launcher cannot. */
        fun requestPin(ctx: Context): Boolean = try {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.isRequestPinAppWidgetSupported && mgr.requestPinAppWidget(ComponentName(ctx, SealWidget::class.java), null, null)
        } catch (e: Exception) { false }
    }
}
