package com.horussec.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SCANNING, RESULT, LINK, ABOUT }

/** App-wide state. Lives as long as the process, so a rotation or theme change keeps the scan. */
object App {
    val scope = MainScope()
    lateinit var ctx: Context

    var screen by mutableStateOf(Screen.HOME)
    var scanStep by mutableIntStateOf(0)
    var device: DeviceData? by mutableStateOf(null)
    var result: ScanResult? by mutableStateOf(null)
    var lastScore by mutableStateOf<Int?>(null)
    var lastScanAt by mutableStateOf(0L)

    var linkText by mutableStateOf("")
    var linkBusy by mutableStateOf(false)
    var linkResult: UrlResult? by mutableStateOf(null)

    var intel: IntelData? by mutableStateOf(null)
    var intelBusy by mutableStateOf(false)
    var intelMsg by mutableStateOf("")

    fun init(c: Context) {
        ctx = c.applicationContext
        Prefs.init(ctx)
        L.ar = (Prefs.str("lang") ?: "ar") == "ar"
        lastScore = Prefs.str("lastScore")?.toIntOrNull()
        lastScanAt = Prefs.long("lastScanAt")
        if (intel == null) scope.launch {
            intel = withContext(Dispatchers.IO) { Intel.get(ctx) }
            val age = intel?.let { Intel.ageDays(it) } ?: 99
            val checked = Prefs.long("intelChecked")
            if (age >= 2 && System.currentTimeMillis() - checked > 12 * 3600_000L) updateIntel(quiet = true)
        }
    }

    fun setLang(ar: Boolean) {
        L.ar = ar
        Prefs.put("lang", if (ar) "ar" else "en")
        val d = device; val i = intel
        if (d != null && i != null) result = Analyzer.analyze(d, i)
    }

    fun startScan() {
        if (screen == Screen.SCANNING) return
        screen = Screen.SCANNING
        scanStep = 0
        scope.launch {
            val started = System.currentTimeMillis()
            val i = intel ?: withContext(Dispatchers.IO) { Intel.get(ctx) }.also { intel = it }
            val d = withContext(Dispatchers.IO) { Collector.collect(ctx) { step -> scope.launch { scanStep = step } } }
            val r = withContext(Dispatchers.Default) { Analyzer.analyze(d, i) }
            val wait = 1800 - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            withContext(Dispatchers.IO) { Collector.saveSnapshot(d, r.score) }
            device = d; result = r
            lastScore = r.score; lastScanAt = r.atMillis
            Prefs.put("lastScore", r.score.toString()); Prefs.putLong("lastScanAt", r.atMillis)
            screen = Screen.RESULT
        }
    }

    fun checkLink(text: String) {
        if (linkBusy || text.isBlank()) return
        linkText = text
        linkBusy = true
        linkResult = null
        scope.launch {
            val i = intel ?: withContext(Dispatchers.IO) { Intel.get(ctx) }.also { intel = it }
            linkResult = withContext(Dispatchers.IO) { UrlCheck.check(text, i) }
            linkBusy = false
        }
    }

    fun updateIntel(quiet: Boolean = false) {
        if (intelBusy) return
        intelBusy = true
        if (!quiet) intelMsg = ""
        scope.launch {
            val before = intel?.generatedAt ?: ""
            val err = withContext(Dispatchers.IO) { Intel.update(ctx) }
            intel = withContext(Dispatchers.IO) { Intel.get(ctx) }
            if (!quiet) intelMsg = when {
                err == "badsig" -> L.t("الملف اللي نزل توقيعه مش سليم، فاترفض.", "The downloaded file failed the signature check and was rejected.")
                err != null -> L.t("مقدرناش نوصل لسيرفر التحديث. اتأكد من النت.", "Could not reach the update server. Check your connection.")
                (intel?.generatedAt ?: "") == before -> L.t("قاعدة التهديدات محدّثة بالفعل.", "Threat intel is already up to date.")
                else -> L.t("اتحدّثت قاعدة التهديدات.", "Threat intel updated.")
            }
            intelBusy = false
        }
    }
}
