/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

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

enum class Screen { HOME, SCANNING, RESULT, LINK, TOOLS, REPORTS, SETTINGS, ABOUT, LEGAL, ASK, BANKAPPS, REPAIR, WHATSAPP, TRACKERS, HELP, SCAMREPORT }

/** App-wide state. Lives as long as the process, so a rotation or theme change keeps the scan. */
object App {
    val scope = MainScope()
    lateinit var ctx: Context

    var screen by mutableStateOf(Screen.HOME)
    /** where the back arrow of a service screen returns to: the tab it was opened from */
    var back by mutableStateOf(Screen.TOOLS)
    private val tabs = setOf(Screen.HOME, Screen.TOOLS, Screen.REPORTS, Screen.SETTINGS)
    fun go(s: Screen) { if (screen in tabs) back = screen; screen = s }
    /** the launch screen with the logo, shown once per process start */
    var splash by mutableStateOf(true)
    var scanStep by mutableIntStateOf(0)
    var device: DeviceData? by mutableStateOf(null)
    var result: ScanResult? by mutableStateOf(null)
    var lastScore by mutableStateOf<Int?>(null)
    var lastScanAt by mutableStateOf(0L)
    var lastVerdict by mutableStateOf<String?>(null)
    var scanDeep by mutableStateOf(false)
    var autoHours by mutableIntStateOf(0)
    var usageAccess by mutableStateOf(false)
    var rootDetected by mutableStateOf(false)
    var rootOn by mutableStateOf(false)
    var rootBusy by mutableStateOf(false)
    var rootMsg by mutableStateOf("")
    var rootUndo by mutableStateOf(listOf<RootScan.Undo>())
    var notifyAllowed by mutableStateOf(true)

    var linkText by mutableStateOf("")
    var linkBusy by mutableStateOf(false)
    var linkResult: UrlResult? by mutableStateOf(null)
    /** set when the link arrived because the user tapped it in another app (Horus Shield chosen as the browser) */
    var linkTapped by mutableStateOf(false)
    /** instant on-phone result for a tapped link: the bank or wallet the site pretends to be */
    var linkLookAlike: UrlCheck.LookAlike? by mutableStateOf(null)
    /** Horus Shield is the app that opens links, so every tapped link is checked first */
    var linkGuard by mutableStateOf(false)

    var intel: IntelData? by mutableStateOf(null)
    var intelBusy by mutableStateOf(false)
    var intelMsg by mutableStateOf("")

    var update: UpdateInfo? by mutableStateOf(null)
    var updateBusy by mutableStateOf(false)
    var updateProgress by mutableIntStateOf(-1)
    var updateMsg by mutableStateOf("")
    var needInstallPermission by mutableStateOf(false)

    /** "system", "dark" or "light" */
    var theme by mutableStateOf("dark")
    /** true right after a scan finishes, so the result screen plays the stamp once */
    var justScanned by mutableStateOf(false)
    /** protection score of the scan before the one on screen */
    var scoreBefore by mutableStateOf<Int?>(null)

    // repair check
    var repairBusy by mutableStateOf(false)
    var repairSavedAt by mutableStateOf(0L)
    var repairChanges: List<RepairCheck.Change>? by mutableStateOf(null)
    // bank apps screen: a quick read of the phone, no full scan needed
    var bankApps: List<EgApps.Found>? by mutableStateOf(null)
    var bankWatchers: List<EgApps.Watcher> by mutableStateOf(emptyList())
    // scam reports
    var reportsVersion by mutableIntStateOf(0)
    var reportMsg by mutableStateOf("")

    fun chooseTheme(t: String) { theme = t; Prefs.put("theme", t) }

    fun init(c: Context) {
        ctx = c.applicationContext
        Prefs.init(ctx)
        L.load()
        Studio.load(ctx)
        scope.launch {
            val a = withContext(Dispatchers.IO) { Studio.fetchAlert() }
            Studio.alert = a?.takeIf { !Studio.dismissed(it) }
        }
        theme = Prefs.str("theme") ?: "dark"
        lastScore = Prefs.str("lastScore")?.toIntOrNull()
        lastScanAt = Prefs.long("lastScanAt")
        lastVerdict = Prefs.str("lastVerdict")
        autoHours = AutoScan.hours()
        rootOn = RootScan.enabled()
        rootUndo = RootScan.undoList()
        scope.launch { rootDetected = withContext(Dispatchers.IO) { RootScan.looksRooted(ctx) } }
        if (intel == null) scope.launch {
            intel = withContext(Dispatchers.IO) { Intel.get(ctx) }
            val age = intel?.let { Intel.ageDays(it) } ?: 99
            val checked = Prefs.long("intelChecked")
            if (age >= 1 && System.currentTimeMillis() - checked > 6 * 3600_000L) updateIntel(quiet = true)
        }
        maybeCheckUpdate()
        UpdateCheck.schedule(ctx)
        IntelWork.schedule(ctx)
        repairSavedAt = RepairCheck.savedAt(ctx)
        if (ScamReports.list().any { !it.sent }) scope.launch { if (withContext(Dispatchers.IO) { ScamReports.sendPending() } > 0) reportsVersion++ }
        // evidence files hold full system logs: never keep them longer than a day
        scope.launch(Dispatchers.IO) {
            val old = System.currentTimeMillis() - 24 * 3600_000L
            listOf("evidence", "updates").forEach { d -> java.io.File(ctx.cacheDir, d).listFiles()?.forEach { if (it.lastModified() < old) it.delete() } }
        }
    }

    fun setLang(code: String) {
        L.lang = if (code in listOf("en", "ar", "eg")) code else "en"
        Prefs.put("lang", L.lang)
        val d = device; val i = intel
        if (d != null && i != null) result = Analyzer.analyze(d, i)
    }

    fun refreshFromPrefs() {
        lastScore = Prefs.str("lastScore")?.toIntOrNull()
        lastScanAt = Prefs.long("lastScanAt")
        lastVerdict = Prefs.str("lastVerdict")
    }

    fun setAuto(h: Int) {
        autoHours = h
        AutoScan.apply(ctx, h)
    }

    fun startScan(deep: Boolean = true) {
        if (screen == Screen.SCANNING) return
        scanDeep = deep
        screen = Screen.SCANNING
        scanStep = 0
        scope.launch {
            val started = System.currentTimeMillis()
            val i = intel ?: withContext(Dispatchers.IO) { Intel.get(ctx) }.also { intel = it }
            val d = withContext(Dispatchers.IO) { Collector.collect(ctx, deep) { step -> scope.launch { scanStep = step } } }
            val r = withContext(Dispatchers.Default) { Analyzer.analyze(d, i) }
            val wait = 1800 - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            withContext(Dispatchers.IO) { Collector.saveSnapshot(d, r) }
            device = d; result = r
            lastScore = r.score; lastScanAt = r.atMillis
            Prefs.put("lastScore", r.score.toString()); Prefs.putLong("lastScanAt", r.atMillis)
            lastVerdict = r.verdict; Prefs.put("lastVerdict", r.verdict)
            scoreBefore = History.list().firstOrNull()?.score
            History.add(r, auto = false)
            justScanned = true
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
            var r = withContext(Dispatchers.IO) { UrlCheck.check(text, i) }
            if (r.ok && r.domain.isNotEmpty() && ScamReports.reportedDomain(r.domain)) r = r.copy(
                verdict = if (r.verdict == "danger") "danger" else "suspicious",
                signals = listOf(Signal("bad", L.t("أنت أبلغت عن هذا الموقع من قبل على أنه احتيال.", "إنت بلّغت عن الموقع ده قبل كده إنه نصب.", "You reported this site as a scam before."))) + r.signals,
            )
            linkResult = r
            linkBusy = false
        }
    }

    /** Reads the phone (quick, no deep scan) and fills the bank apps screen. */
    fun loadBankApps() {
        scope.launch {
            val d = withContext(Dispatchers.IO) { Collector.collect(ctx, false) { } }
            bankApps = withContext(Dispatchers.Default) { EgApps.found(d) }
            bankWatchers = withContext(Dispatchers.Default) { EgApps.watchers(d) }
        }
    }

    fun repairSave() {
        if (repairBusy) return
        repairBusy = true
        scope.launch {
            withContext(Dispatchers.IO) { val d = Collector.collect(ctx, false) { }; RepairCheck.save(ctx, d) }
            repairSavedAt = RepairCheck.savedAt(ctx); repairChanges = null
            repairBusy = false
        }
    }

    fun repairCompare() {
        if (repairBusy) return
        repairBusy = true
        scope.launch {
            repairChanges = withContext(Dispatchers.IO) { val d = Collector.collect(ctx, false) { }; RepairCheck.compare(ctx, d) }
            repairBusy = false
        }
    }

    fun repairClear() { RepairCheck.clear(ctx); repairSavedAt = 0L; repairChanges = null }

    fun report(kind: String, value: String, channel: String) {
        ScamReports.add(kind, value, channel)
        reportsVersion++
        reportMsg = L.t("جارٍ الإرسال...", "بيبعت...", "Sending...")
        scope.launch {
            val n = withContext(Dispatchers.IO) { ScamReports.sendPending() }
            reportsVersion++
            reportMsg = if (n > 0) L.t("وصل البلاغ. شكرًا لأنك تحمي غيرك.", "البلاغ وصل. شكرًا إنك بتحمي غيرك.", "Report received. Thank you for protecting others.")
                else L.t("حُفظ البلاغ على هاتفك، وسيُرسل تلقائيًا عند توفر الاتصال.", "البلاغ اتحفظ على موبايلك، وهيتبعت لوحده أول ما النت يبقى متاح.", "Saved on your phone. It will be sent automatically when a connection is available.")
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
                err == "badsig" -> L.t("توقيع الملف الذي نُزّل غير سليم، لذا رُفض.", "الملف اللي نزل توقيعه مش سليم، فاترفض.", "The downloaded file failed the signature check and was rejected.")
                err != null -> L.t("تعذّر الوصول إلى خادم التحديث. تحقق من اتصالك بالإنترنت.", "مقدرناش نوصل لسيرفر التحديث. اتأكد من النت.", "Could not reach the update server. Check your connection.")
                (intel?.generatedAt ?: "") == before -> L.t("قاعدة التهديدات محدّثة بالفعل.", "قاعدة التهديدات محدّثة بالفعل.", "Threat intel is already up to date.")
                else -> L.t("حُدّثت قاعدة التهديدات.", "اتحدّثت قاعدة التهديدات.", "Threat intel updated.")
            }
            intelBusy = false
        }
    }

    /** Called on every app open; asks GitHub at most every 30 minutes. */
    fun maybeCheckUpdate() {
        if (update == null && System.currentTimeMillis() - Prefs.long("updateChecked") > 30 * 60_000L) checkUpdate(quiet = true)
    }

    fun checkUpdate(quiet: Boolean = false) {
        if (updateBusy) return
        updateBusy = true
        if (!quiet) updateMsg = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { try { Result.success(Updater.check()) } catch (e: Exception) { Result.failure(e) } }
            r.onSuccess { info ->
                update = info
                // seen in the app already, so the background check will not notify for it again
                if (info != null) Prefs.put("notifiedUpdate", info.code.toString())
                if (!quiet && info == null) updateMsg = L.t("لديك أحدث نسخة.", "عندك آخر نسخة.", "You have the latest version.")
            }.onFailure { if (!quiet) updateMsg = L.t("تعذّر الوصول إلى خادم التحديث. تحقق من اتصالك بالإنترنت.", "مقدرناش نوصل لسيرفر التحديث. اتأكد من النت.", "Could not reach the update server. Check your connection.") }
            updateBusy = false
        }
    }

    /** Download, verify and open Android's installer. Asks for the install permission first if needed. */
    fun installUpdate() {
        val info = update ?: return
        if (updateBusy) return
        if (!Updater.canInstall(ctx)) { needInstallPermission = true; return }
        needInstallPermission = false
        updateBusy = true; updateProgress = 0; updateMsg = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try { Result.success(Updater.download(ctx, info) { p -> scope.launch { updateProgress = p } }) } catch (e: Exception) { Result.failure(e) }
            }
            updateBusy = false; updateProgress = -1
            r.onSuccess { f ->
                try { Updater.install(ctx, f) } catch (e: Exception) { updateMsg = L.t("تعذّر فتح شاشة التثبيت.", "مقدرناش نفتح شاشة التسطيب.", "Could not open the installer.") }
            }.onFailure { e ->
                updateMsg = if (e.message == "sha") L.t("الملف الذي نُزّل لا يطابق النسخة الرسمية، لذا رُفض ولم يُثبَّت.", "الملف اللي نزل مش مطابق للنسخة الرسمية، فاترفض ومتسطّبش.", "The download did not match the official build, so it was rejected and not installed.")
                else L.t("لم يكتمل التنزيل بشكل صحيح. حاول مرة أخرى.", "التنزيل موقفش صح. جرّب تاني.", "The download did not finish. Try again.")
            }
        }
    }

    // ------------------------------------------------------------------ root mode
    fun enableRoot() {
        if (rootBusy) return
        rootBusy = true; rootMsg = ""
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Su.granted() }
            if (ok) { Prefs.put("rootMode", "1"); rootOn = true; rootMsg = L.t("فُعّل وضع Root، وسيستخدمه الفحص العميق التالي.", "وضع Root اتفعّل. الفحص العميق الجاي هيستخدمه.", "Root mode is on. The next deep scan will use it.") }
            else rootMsg = L.t("رُفضت صلاحية Root أو أنها غير موجودة. إن كان لديك Magisk، فافتحه واسمح لـ Horus Shield من Superuser.", "صلاحية Root اترفضت أو مش موجودة. لو عندك Magisk، افتحه واسمح لـ Horus Shield من Superuser.", "Root access was refused or is not available. If you use Magisk, open it and allow Horus Shield under Superuser.")
            rootBusy = false
        }
    }

    fun disableRoot() { Prefs.put("rootMode", null); rootOn = false; rootMsg = "" }

    /** Runs a root response action after the user confirmed it; returns through rootMsg. */
    fun rootAction(fx: Fix, pkg: String) {
        if (rootBusy) return
        rootBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (fx) {
                    Fix.ROOT_STOP -> RootScan.stop(pkg)
                    Fix.ROOT_DISABLE -> RootScan.disable(pkg)
                    Fix.ROOT_BLOCKNET -> {
                        val uid = try { ctx.packageManager.getApplicationInfo(pkg, 0).uid } catch (e: Exception) { -1 }
                        uid >= 10000 && RootScan.blockNet(pkg, uid)
                    }
                    else -> false
                }
            }
            rootUndo = RootScan.undoList()
            rootMsg = if (ok) L.t("نُفّذ على $pkg.", "اتنفّذ على $pkg.", "Done for $pkg.") else L.t("لم يُنفَّذ. تأكد من أن Horus Shield ما زال مسموحًا له بصلاحية Root.", "متنفّذش. اتأكد إن Horus Shield لسه مسموحله بـ Root.", "It did not work. Make sure Horus Shield still has root access.")
            rootBusy = false
        }
    }

    fun rootUndoAction(u: RootScan.Undo) {
        if (rootBusy) return
        rootBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { RootScan.undo(u.kind, u.pkg, u.uid) }
            rootUndo = RootScan.undoList()
            rootMsg = if (ok) L.t("أُعيد: ${u.pkg}", "اترجع: ${u.pkg}", "Restored: ${u.pkg}") else L.t("تعذّرت الإعادة.", "مقدرناش نرجّعه.", "Could not restore it.")
            rootBusy = false
        }
    }

    fun exportEvidence(c: Context) {
        if (rootBusy) return
        rootBusy = true; rootMsg = L.t("جارٍ جمع السجلات...", "بيجمع السجلات...", "Collecting logs...")
        scope.launch {
            val summary = result?.let { Actions.summary(it, device) } ?: ""
            val i = withContext(Dispatchers.IO) { try { RootScan.exportEvidence(ctx, summary) } catch (e: Exception) { null } }
            rootBusy = false
            if (i == null) rootMsg = L.t("تعذّر جمع السجلات.", "مقدرناش نجمع السجلات.", "Could not collect the logs.")
            else { rootMsg = ""; try { c.startActivity(i) } catch (e: Exception) { } }
        }
    }
}
