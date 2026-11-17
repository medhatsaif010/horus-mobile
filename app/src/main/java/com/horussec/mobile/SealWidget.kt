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
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.horussec.mobile.ui.dayLabel
import com.horussec.mobile.ui.ringText
import com.horussec.mobile.ui.verdictSub
import com.horussec.mobile.ui.verdictWord

/** The seal on the phone's home screen: the last verdict, and a tap opens the app. */
class SealWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    companion object {
        private const val SAFE = 0xFF3BC1B0.toInt()
        private const val WARN = 0xFFF08A24.toInt()
        private const val DANGER = 0xFFE5484D.toInt()
        private const val GOLD = 0xFFE8B23A.toInt()
        private const val TEXT2 = 0xFFD9D4C7.toInt()

        fun update(ctx: Context) {
            try {
                Prefs.init(ctx)
                L.ar = (Prefs.str("lang") ?: "ar") == "ar"
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, SealWidget::class.java))
                if (ids.isEmpty()) return
                val v = RemoteViews(ctx.packageName, R.layout.widget_seal)
                v.setImageViewBitmap(R.id.seal, bitmap(ctx, 420))
                val at = Prefs.long("lastScanAt")
                v.setTextViewText(R.id.when_, if (at > 0) L.t("آخر فحص: ", "Last scan: ") + dayLabel(at) else L.t("اضغط لأول فحص", "Tap to run a first scan"))
                val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                v.setOnClickPendingIntent(R.id.root, open)
                mgr.updateAppWidget(ids, v)
            } catch (e: Exception) { }
        }

        /** Same geometry as the in-app seal (viewBox 300), drawn on a plain Android canvas. */
        fun bitmap(ctx: Context, px: Int): Bitmap {
            val verdict = Prefs.str("lastVerdict")
            val at = Prefs.long("lastScanAt")
            val hist = History.list().firstOrNull()
            val color = when { at == 0L -> GOLD; verdict == "compromised" -> DANGER; verdict == "suspicious" -> WARN; else -> SAFE }
            val word = if (at == 0L) L.t("حورس", "Horus") else verdictWord(verdict)
            val sub = if (at == 0L) L.t("اعمل أول فحص", "Run a first scan") else verdictSub(verdict, 0)
            val ring = if (at == 0L) "HORUS SEC · MOBILE · " else ringText(hist?.deep ?: true, at, hist?.apps ?: 0)

            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val k = px / 300f
            val cx = px / 2f; val cy = px / 2f; val r = 118f * k
            val tf = try { ResourcesCompat.getFont(ctx, R.font.readex_medium) } catch (e: Exception) { null }
            val tfb = try { ResourcesCompat.getFont(ctx, R.font.readex_bold) } catch (e: Exception) { null }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color }
            stroke.strokeWidth = 2.5f * k; c.drawCircle(cx, cy, r + 16f * k, stroke)
            stroke.strokeWidth = 1.2f * k; c.drawCircle(cx, cy, r - 28f * k, stroke)

            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 13f * k; if (tf != null) typeface = tf }
            val rt = r - 14f * k
            val path = Path().apply { addArc(RectF(cx - rt, cy - rt, cx + rt, cy + rt), 180f, 359.9f) }
            val circ = (2 * Math.PI * rt).toFloat() * 0.985f
            val base = p.measureText(ring)
            if (base < circ) p.letterSpacing = ((circ - base) / ring.length) / p.textSize
            c.drawTextOnPath(ring, path, 0f, p.textSize * 0.35f, p)

            val w = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textAlign = Paint.Align.CENTER; if (tfb != null) typeface = tfb
                textSize = (if (word.length <= 5) 52f else 40f) * k
            }
            c.drawText(word, cx, cy + 6f * k, w)
            val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = TEXT2; textAlign = Paint.Align.CENTER; textSize = 14f * k; if (tf != null) typeface = tf }
            var t = sub
            while (s.measureText(t) > 2 * (r - 34f * k) && t.length > 4) t = t.dropLast(2)
            c.drawText(t, cx, cy + 34f * k, s)
            return b
        }

        /** Asks the launcher to place the widget (Android 8+). Returns false if the launcher cannot. */
        fun requestPin(ctx: Context): Boolean = try {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.isRequestPinAppWidgetSupported && mgr.requestPinAppWidget(ComponentName(ctx, SealWidget::class.java), null, null)
        } catch (e: Exception) { false }
    }
}
