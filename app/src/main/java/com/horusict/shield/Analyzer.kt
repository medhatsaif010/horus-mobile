/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.Manifest
import android.app.admin.DevicePolicyManager
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Turns DeviceData into findings, checks and a score. Same rules as the Windows program's phone scan. */
object Analyzer {
    private val stores = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket", "com.hihonor.appmarket",
        "com.xiaomi.market", "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market", "com.bbk.appstore",
        "com.vivo.appstore", "com.amazon.venezia", "com.transsnet.store", "com.google.android.feedback",
        "com.facebook.system", "com.facebook.appmanager", "com.google.android.apps.work.clouddpc",
    )
    private val sensitive = setOf(
        "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
        "android.permission.READ_CALL_LOG", "android.permission.PROCESS_OUTGOING_CALLS", "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS", "android.permission.READ_PHONE_STATE",
    )
    private val rootApps = setOf("com.topjohnwu.magisk", "me.weishu.kernelsu", "eu.chainfire.supersu", "me.bmax.apatch")
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun short(p: String) = p.removePrefix("android.permission.")
    private val hookApps = setOf("de.robv.android.xposed.installer", "org.lsposed.manager", "io.github.lsposed.manager", "org.meowcat.edxposed.manager", "com.saurik.substrate", "io.va.exposed", "io.va.exposed64", "com.android.vending.billing.InAppBillingService.COIN")
    private val systemLike = Regex("^(system|android system|system service|system update|settings|google play services|google services|wi-?fi|battery|update|device health|security update|تحديث النظام|خدمات النظام|الإعدادات|النظام|خدمات جوجل)$", RegexOption.IGNORE_CASE)
    private val mdmInstallers = setOf("com.google.android.apps.work.clouddpc", "com.android.vending")

    /** Signing-certificate SHA-1s of Google's own apps; an app signed with one of these is really Google's. */
    private val googleCerts = setOf("38918a453d07199354f8b19af05ec6562ced5788", "24bb24c05e47e0aefa68a58a766179d9b613a600")
    private fun isGoogleSigned(a: AppInfo) = a.certSha1.any { it.replace(":", "").lowercase() in googleCerts }

    /**
     * The installer name can be set by whoever installs the app ("pm install -i com.android.vending" from a computer),
     * so an app that claims a store but was really installed over adb counts as installed from outside a store.
     */
    fun spoofedStore(a: AppInfo) = !a.system && a.installer != null && a.installer in stores && a.initiator in adbInitiators
    private val adbInitiators = setOf("com.android.shell", "root")

    fun isSideloaded(a: AppInfo, selfPkg: String) = !a.system && a.pkg != selfPkg && (a.installer == null || a.installer !in stores || spoofedStore(a))

    fun analyze(d: DeviceData, intel: IntelData): ScanResult {
        val findings = ArrayList<Finding>()
        val checks = ArrayList<Check>()
        val verified = ArrayList<String>()
        val byPkg = d.apps.associateBy { it.pkg }
        val user = d.apps.filter { !it.system && it.pkg != d.selfPkg }
        fun name(p: String): String { val a = byPkg[p]; return if (a == null || a.label == p) p else "${a.label} ($p)" }
        fun date(p: String): String = byPkg[p]?.firstInstall?.let { if (it > 0) day.format(Date(it)) else "" } ?: ""
        fun isSide(a: AppInfo) = isSideloaded(a, d.selfPkg)
        fun inst(a: AppInfo) = if (spoofedStore(a)) L.t("كمبيوتر عبر USB (يدّعي ${a.installer})", "كمبيوتر بالكابل (بيدّعي ${a.installer})", "a computer over USB (claims ${a.installer})") else a.installer ?: L.t("ملف APK أو مصدر غير معروف", "ملف APK أو جهة مش معروفة", "APK file or unknown source")

        // 1. known stalkerware (by package name or by signing certificate)
        val stalkerPkgs = HashSet<String>()
        for (a in user) {
            val byName = intel.byPackage[a.pkg]
            val byCert = a.certSha1.firstNotNullOfOrNull { intel.byCert[it] }
            val s = byName ?: byCert ?: continue
            stalkerPkgs.add(a.pkg)
            val ev = arrayListOf(
                "${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)} ${a.version}",
                "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}",
                "${L.t("المصدر", "المصدر", "Installer")}: ${inst(a)}",
                "${L.t("مطابق لـ", "مطابق لـ", "Matches")}: Echap stalkerware-indicators (${s.name}${if (byName == null) L.t("، بالتوقيع الرقمي", ", بالتوقيع الرقمي", ", by signing certificate") else ""})",
            )
            if (a.pkg in d.accessibility) ev.add("Accessibility: ${L.t("مفعّلة", "مفعّلة", "enabled")}")
            if (a.pkg in d.admins) ev.add("Device admin: ${L.t("نعم", "أيوه", "yes")}")
            if (!a.launcher) ev.add(L.t("مخفي من الشاشة الرئيسية", "مخفي من الشاشة الرئيسية", "Hidden from the home screen"))
            findings.add(Finding(
                key = "stalkerware:${a.pkg}", sev = Sev.CRIT, ioc = true, stalker = true, pkg = a.pkg,
                title = L.t("برنامج تجسس معروف: ${s.name}", "برنامج تجسس معروف: ${s.name}", "Known stalkerware: ${s.name}"),
                why = L.t("يثبّت هذه البرامجَ شخصٌ أمسك بالهاتف، وهي ترسل الرسائل والمكالمات والموقع والصور إلى طرف آخر دون علم صاحب الهاتف.", "البرامج دي بيسطّبها حد مسك الموبايل في إيده، وبتبعت الرسايل والمكالمات والموقع والصور لحد تاني من غير ما صاحب الموبايل يعرف.",
                    "Someone with access to the phone installs these. They send messages, calls, location and photos to another person without the owner knowing."),
                todo = L.t("لا تحذفه فورًا إن شعرت بأن هناك خطرًا عليك، لأن من ثبّته سيعلم بذلك. احفظ هذا التقرير (مشاركة الملخص) واطلب المساعدة من شخص تثق به، ثم احذفه وغيّر كلمة مرور حساب Google من جهاز آخر.", "متمسحهوش على طول لو حاسس إن فيه خطر عليك، لأن اللي سطّبه هيعرف. احفظ التقرير ده (شارك الملخص) واطلب مساعدة من حد تثق فيه، وبعدين امسحه وغيّر باسورد حساب Google من جهاز تاني.",
                    "Do not remove it right away if you feel at risk: whoever installed it will notice. Save this report (Share summary), get help from someone you trust, then remove it and change your Google password from another device."),
                evidence = ev, fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
            ))
        }
        if (intel.stalkerware.isNotEmpty() && stalkerPkgs.isEmpty())
            verified.add(L.t("لا يوجد أي برنامج من قائمة برامج التجسس المعروفة (${intel.stalkerware.size} برنامج)، بالاسم أو بالتوقيع", "مفيش أي برنامج من قايمة برامج التجسس المعروفة (${intel.stalkerware.size} برنامج)، بالاسم أو بالتوقيع", "None of the ${intel.stalkerware.size} known stalkerware apps, by name or signature"))

        // 2. hidden apps with sensitive access
        val hidden = HashSet<String>()
        for (a in user) {
            if (a.launcher || a.pkg in stalkerPkgs) continue
            val g = a.granted.filter { it in sensitive }
            val power = ArrayList<String>()
            if (a.pkg in d.accessibility) power.add("Accessibility")
            if (a.pkg in d.admins) power.add("Device admin")
            if (a.pkg in d.notifListeners) power.add(L.t("الوصول إلى الإشعارات", "قراية الإشعارات", "Notification access"))
            if (power.isEmpty() && g.size < 2) continue
            hidden.add(a.pkg)
            val high = power.isNotEmpty()
            findings.add(Finding(
                key = "hidden-app:${a.pkg}", sev = if (high) Sev.HIGH else Sev.MED, ioc = high, pkg = a.pkg,
                title = L.t("تطبيق مخفي يملك صلاحيات حساسة: ${a.label}", "تطبيق مخفي وليه صلاحيات حساسة: ${a.label}", "Hidden app with sensitive access: ${a.label}"),
                why = L.t("تطبيق بلا أيقونة على الشاشة ويملك صلاحيات مثل الرسائل أو الموقع أو الميكروفون. هذا هو الشكل المعتاد لبرامج التجسس التي لم تُدرج في القوائم بعد.", "تطبيق ملوش أيقونة على الشاشة ومعاه صلاحيات زي الرسايل أو الموقع أو الميكروفون. ده الشكل المعتاد لبرامج التجسس اللي لسه مش في القوايم.",
                    "An app with no home-screen icon that holds access like SMS, location or the microphone. That is how stalkerware not yet on any list usually looks."),
                todo = L.t("إن لم تكن تعرف هذا التطبيق، فافتح إعداداته واسحب صلاحياته ثم احذفه.", "لو مش عارف التطبيق ده، افتح إعداداته واسحب صلاحياته وبعدين امسحه.", "If you do not recognise it, open its settings, remove its permissions, then uninstall it."),
                evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}", "${L.t("المصدر", "المصدر", "Installer")}: ${inst(a)}") +
                    power.map { "${L.t("صلاحية", "صلاحية", "Access")}: $it" } + g.map { "${L.t("إذن", "إذن", "Permission")}: ${short(it)}" },
                fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
            ))
        }

        // 3. fake banking / wallet apps and the banking-trojan pattern
        val finHits = HashSet<String>()
        for (a in user) {
            if (!isSide(a) || a.pkg in stalkerPkgs) continue
            val hit = intel.finance.firstOrNull { (_, rx) -> rx.containsMatchIn(a.pkg) || rx.containsMatchIn(a.label) } ?: continue
            finHits.add(a.pkg)
            findings.add(Finding(
                key = "fake-finance:${a.pkg}", sev = Sev.CRIT, ioc = true, pkg = a.pkg,
                title = L.t("تطبيق باسم ${hit.first} مثبّت من خارج المتجر: غالبًا مزيّف", "تطبيق باسم ${hit.first} متسطّب من برّه المتجر: غالبًا مزيف", "${hit.first} app installed outside the store: probably fake"),
                why = L.t("تُنزَّل تطبيقات البنوك والمحافظ الرسمية من المتجر فقط. والنسخة التي تحمل الاسم نفسه وتأتي من رابط على واتساب أو تيليجرام هي أشهر وسيلة لسرقة الحسابات ورموز OTP في مصر.", "تطبيقات البنوك والمحافظ الرسمية بتتنزّل من المتجر بس. نسخة بنفس الاسم جاية من لينك على واتساب أو تيليجرام هي أشهر طريقة لسرقة الحسابات وأكواد OTP في مصر.",
                    "Official bank and wallet apps only come from the app store. A copy with the same name from a WhatsApp or Telegram link is the most common way accounts and OTP codes are stolen."),
                todo = L.t("احذفه الآن ونزّل التطبيق الرسمي من Google Play فقط. وإن كنت قد أدخلت بياناتك فيه، فاتصل بالبنك فورًا وأوقف البطاقة أو المحفظة وغيّر الرقم السري.", "امسحه دلوقتي ونزّل التطبيق الرسمي من Google Play بس. لو كنت دخلت بياناتك فيه، كلّم البنك حالًا واقفل الكارت أو المحفظة وغيّر الـ PIN.",
                    "Uninstall it now and get the official app from Google Play only. If you entered your details in it, call your bank right away, block the card or wallet and change the PIN."),
                evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)} ${a.version}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}", "${L.t("المصدر", "المصدر", "Installer")}: ${inst(a)}"),
                fixes = listOf(Fix.UNINSTALL, Fix.APP_SETTINGS),
            ))
        }
        val bankerPkgs = HashSet<String>()
        for (a in user) {
            if (!isSide(a) || a.pkg in finHits || a.pkg in stalkerPkgs || a.pkg in hidden) continue
            val sms = a.granted.filter { it == Manifest.permission.READ_SMS || it == Manifest.permission.RECEIVE_SMS }
            val pw = ArrayList<String>()
            if (a.pkg in d.accessibility) pw.add("Accessibility")
            if (a.overlay) pw.add(L.t("الظهور فوق التطبيقات", "الظهور فوق التطبيقات", "Display over other apps"))
            if (a.pkg in d.notifListeners) pw.add(L.t("الوصول إلى الإشعارات", "قراية الإشعارات", "Notification access"))
            if (sms.isEmpty() || pw.isEmpty()) continue
            bankerPkgs.add(a.pkg)
            findings.add(Finding(
                key = "banker-pattern:${a.pkg}", sev = Sev.HIGH, ioc = true, pkg = a.pkg,
                title = L.t("تطبيق من خارج المتجر يقرأ الرسائل ويملك صلاحيات تحكم: ${a.label}", "تطبيق من برّه المتجر بيقرا الرسايل ومعاه صلاحيات تحكم: ${a.label}", "Sideloaded app reads SMS and has control access: ${a.label}"),
                why = L.t("الجمع بين قراءة الرسائل وAccessibility أو الظهور فوق التطبيقات هو ما تحتاجه أحصنة طروادة المصرفية لسرقة رمز OTP وتحويل الأموال دون أن تشعر.", "قراية الرسايل مع Accessibility أو الظهور فوق التطبيقات هي التركيبة اللي أحصنة طروادة البنكية محتاجاها علشان تسرق كود OTP وتحوّل فلوس من غير ما تحس.",
                    "Reading SMS together with Accessibility or screen overlays is exactly what banking trojans need to steal OTP codes and move money unnoticed."),
                todo = L.t("إن لم تكن تعرف هذا التطبيق جيدًا فاحذفه، وراجع حركات حساباتك البنكية ومحافظك.", "لو مش عارف التطبيق ده كويس امسحه، وراجع حركات حساباتك البنكية والمحافظ.", "If you do not know this app well, uninstall it and check your bank and wallet transactions."),
                evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}") +
                    sms.map { "${L.t("إذن", "إذن", "Permission")}: ${short(it)}" } + pw.map { "${L.t("صلاحية", "صلاحية", "Access")}: $it" },
                fixes = listOf(Fix.UNINSTALL, Fix.APP_SETTINGS),
            ))
        }

        // 4. powerful access
        val skip = stalkerPkgs + hidden + finHits + bankerPkgs
        val accUser = user.filter { it.pkg in d.accessibility && it.pkg !in skip }
        for (a in accUser) {
            val side = isSide(a)
            findings.add(Finding(
                key = "accessibility:${a.pkg}", sev = if (side || a.overlay) Sev.HIGH else Sev.MED, ioc = side && a.overlay, pkg = a.pkg,
                title = L.t("تطبيق يملك صلاحية Accessibility (يرى الشاشة وينقر بدلًا منك): ${a.label}", "تطبيق ليه صلاحية Accessibility (يشوف الشاشة ويدوس بدالك): ${a.label}", "App with Accessibility access (sees the screen, taps for you): ${a.label}"),
                why = if (a.overlay) L.t("الجمع بين Accessibility والظهور فوق التطبيقات هو أسلوب أحصنة طروادة المصرفية: ترسم شاشة دخول مزيفة فوق تطبيق البنك وتقرأ ما تكتبه.", "Accessibility مع الظهور فوق التطبيقات هو أسلوب أحصنة طروادة البنكية: بيرسم شاشة دخول مزيفة فوق تطبيق البنك ويقرا اللي بتكتبه.",
                    "Accessibility plus screen overlays is the banking-trojan technique: it draws a fake login over your bank app and reads what you type.")
                else L.t("تمنح Accessibility التطبيقَ القدرةَ على رؤية كل ما على الشاشة والنقر بدلًا منك. والتطبيقات التي تحتاجها فعلًا قليلة، مثل قارئ الشاشة أو مدير كلمات المرور.", "Accessibility بتدّي التطبيق قدرة يشوف كل اللي على الشاشة ويدوس بدالك. التطبيقات اللي محتاجاها فعلًا قليلة، زي قارئ الشاشة أو مدير الباسوردات.",
                    "Accessibility lets an app see everything on screen and tap for you. Few apps really need it, such as screen readers or password managers."),
                todo = L.t("إن لم تكن تعرف سبب حاجته إليها، فأوقفها من إعدادات Accessibility.", "لو مش عارف ليه محتاجها، اقفلها من إعدادات Accessibility.", "If you do not know why it needs this, turn it off in Accessibility settings."),
                evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "${L.t("المصدر", "المصدر", "Installer")}: ${inst(a)}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}"),
                fixes = listOf(Fix.ACCESSIBILITY, Fix.APP_SETTINGS),
            ))
        }
        val admUser = user.filter { it.pkg in d.admins && it.pkg !in skip }
        for (a in admUser) {
            findings.add(Finding(
                key = "device-admin:${a.pkg}", sev = Sev.MED, pkg = a.pkg,
                title = L.t("تطبيق مفعّل كمسؤول جهاز (يصعب حذفه): ${a.label}", "تطبيق متعمله Device admin (صعب يتمسح): ${a.label}", "App is a device admin (hard to remove): ${a.label}"),
                why = L.t("يتيح Device admin للتطبيق قفل الشاشة أو مسح الهاتف، ويمنع حذفه بالطريقة العادية. تستخدمه تطبيقات العمل، وكذلك برامج التجسس.", "Device admin بيخلّي التطبيق يقفل الشاشة أو يمسح الموبايل، وبيمنع مسحه بالطريقة العادية. بتستخدمه تطبيقات الشغل، وكمان برامج التجسس.",
                    "Device admin lets an app lock or wipe the phone and blocks normal uninstalling. Work apps use it, and so does stalkerware."),
                todo = L.t("إن لم يكن تطبيق عمل أو حماية تعرفه: الأمان › Device admin apps، ثم أوقفه واحذفه.", "لو مش تطبيق شغل أو حماية إنت عارفه: الأمان › Device admin apps، اقفله وبعدين امسحه.", "If it is not a work or security app you know: Security › Device admin apps, turn it off, then uninstall."),
                evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}"),
                fixes = listOf(Fix.SECURITY),
            ))
        }
        val nlUser = user.filter { it.pkg in d.notifListeners && it.pkg !in skip }
        if (nlUser.isNotEmpty()) findings.add(Finding(
            key = "notification-access", sev = Sev.LOW,
            title = L.t("تطبيقات تقرأ جميع الإشعارات، ومنها رموز التحقق (${nlUser.size})", "تطبيقات بتقرا كل الإشعارات، ومنها أكواد التحقق (${nlUser.size})", "Apps that read all notifications, including codes (${nlUser.size})"),
            why = L.t("أي تطبيق يقرأ الإشعارات يستطيع رؤية رسائل واتساب ورموز OTP التي تصلك.", "أي تطبيق بيقرا الإشعارات يقدر يشوف رسايل واتساب وأكواد OTP اللي بتوصلك.", "Any app with notification access can read WhatsApp messages and the OTP codes you receive."),
            todo = L.t("أبقِ فقط ما تحتاجه فعلًا، مثل الساعة الذكية.", "سيب بس اللي محتاجه فعلًا، زي الساعة الذكية.", "Keep only the ones you really need, such as a smartwatch app."),
            evidence = nlUser.map { name(it.pkg) }, fixes = listOf(Fix.NOTIFICATIONS),
        ))

        // 5. sideloading
        // 5a. installed from a computer while pretending to come from an app store
        for (a in user.filter { spoofedStore(it) && it.pkg !in stalkerPkgs }) findings.add(Finding(
            key = "spoofed-installer:${a.pkg}", sev = Sev.HIGH, ioc = true, pkg = a.pkg,
            title = L.t("تطبيق ثُبّت من كمبيوتر ويدّعي أنه من المتجر: ${a.label}", "تطبيق اتسطّب من كمبيوتر وبيدّعي إنه من المتجر: ${a.label}", "App installed from a computer that claims to come from a store: ${a.label}"),
            why = L.t("سجّل أندرويد أن هذا التطبيق ثُبّت عبر كابل USB من كمبيوتر (adb)، بينما يقول إن مصدره ${a.installer}. لا يفعل ذلك إلا شخص أمسك بالهاتف ويريد أن يبدو التطبيق قادمًا من المتجر، وهو أسلوب يُستخدم لتثبيت برامج التجسس.",
                "أندرويد مسجّل إن التطبيق ده اتسطّب بكابل USB من كمبيوتر (adb)، وفي نفس الوقت بيقول إن مصدره ${a.installer}. محدش بيعمل كده غير حد مسك الموبايل وعايز التطبيق يبان إنه من المتجر، وده أسلوب بيتستخدم لتسطيب برامج التجسس.",
                "Android recorded that this app was installed over USB from a computer (adb), while it says it came from ${a.installer}. Only someone with the phone in hand does this, to make an app look like it came from the store. It is a known way to plant spyware."),
            todo = L.t("إن لم تكن أنت أو شخص تثق به من ثبّته، فلا تحذفه فورًا إن شعرت بخطر، واحفظ الملخص واطلب المساعدة، ثم احذفه وغيّر كلمة مرور Google من جهاز آخر.",
                "لو مش إنت أو حد بتثق فيه اللي سطّبه، متمسحوش على طول لو حاسس بخطر، احفظ الملخص واطلب مساعدة، وبعدين امسحه وغيّر باسورد Google من جهاز تاني.",
                "If neither you nor someone you trust installed it, do not delete it straight away if you feel at risk: save the summary and get help, then remove it and change your Google password from another device."),
            evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "${L.t("المصدر المُدّعى", "المصدر اللي بيقوله", "Claimed source")}: ${a.installer}",
                "${L.t("من بدأ التثبيت", "مين بدأ التسطيب", "Install started by")}: ${a.initiator}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}"),
            fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL), stalker = true,
        ))
        val side = user.filter { isSide(it) }
        if (side.isNotEmpty()) findings.add(Finding(
            key = "sideloaded-apps", sev = if (side.size >= 3) Sev.MED else Sev.LOW,
            title = L.t("تطبيقات مثبّتة من خارج المتجر (${side.size})", "تطبيقات اتسطبت من برّه المتجر (${side.size})", "Apps installed from outside an app store (${side.size})"),
            why = L.t("تأتي أغلب البرامج الضارة على أندرويد كملف APK من رابط على واتساب أو تيليجرام أو موقع ما، وليس من Play.", "أغلب البرامج الضارة على أندرويد بتيجي كملف APK من لينك على واتساب أو تيليجرام أو موقع، مش من Play.",
                "Most Android malware arrives as an APK from a WhatsApp, Telegram or website link, not from Play."),
            todo = L.t("احذف أي تطبيق منها لا تعرف مصدره.", "امسح أي تطبيق منهم مش عارف جه منين.", "Uninstall any of these you do not know the origin of."),
            evidence = side.take(20).map { "${name(it.pkg)}  ·  ${date(it.pkg)}  ·  ${inst(it)}" },
        ))
        val installers = user.filter { it.canInstall == true && it.installer != null && it.pkg !in stores }
        if (installers.isNotEmpty()) findings.add(Finding(
            key = "install-unknown-apps", sev = Sev.LOW,
            title = L.t("تطبيقات مسموح لها بتثبيت تطبيقات أخرى (${installers.size})", "تطبيقات مسموحلها تسطّب تطبيقات تانية (${installers.size})", "Apps allowed to install other apps (${installers.size})"),
            why = L.t("إن خُدع أحدها أو كان ضارًا، فيمكنه تنزيل تطبيقات وتثبيتها دون متجر.", "لو تطبيق منهم اتخدع أو كان ضار، يقدر ينزّل ويسطّب تطبيقات من غير متجر.", "If one of them is tricked or malicious, it can install apps without a store."),
            todo = L.t("أوقف هذا الإذن للجميع إلا إذا كنت تحتاجه الآن.", "اقفلها للكل إلا لو محتاجها دلوقتي.", "Turn it off for all of them unless you need it right now."),
            evidence = installers.map { name(it.pkg) }, fixes = listOf(Fix.UNKNOWN_SOURCES),
        ))

        // 6. device integrity
        val rootPk = d.apps.filter { it.pkg in rootApps }
        val rooted = d.suPaths.isNotEmpty() || rootPk.isNotEmpty()
        if (rooted) findings.add(Finding(
            key = "rooted", sev = Sev.HIGH,
            title = L.t("الهاتف عليه صلاحية Root", "الموبايل متعمله Root", "The phone is rooted"),
            why = L.t("يزيل الـ Root أهم حماية في أندرويد: أي تطبيق يحصل على الصلاحية يستطيع قراءة بيانات جميع التطبيقات الأخرى، ومنها تطبيقات البنوك.", "الـ Root بيشيل أهم حماية في أندرويد: أي تطبيق ياخد الصلاحية يقدر يقرا بيانات كل التطبيقات التانية، ومنها تطبيقات البنوك.",
                "Root removes Android's main protection: any app given root can read every other app's data, including banking apps."),
            todo = L.t("إن لم تكن أنت من فعّله، فأعد ضبط المصنع وثبّت النظام الرسمي.", "لو مش إنت اللي عامله، اعمل Factory reset ونزّل السوفت وير الرسمي.", "If you did not root it yourself, factory reset and install the official firmware."),
            evidence = d.suPaths.map { "su: $it" } + rootPk.map { name(it.pkg) },
        ))
        if (d.buildTags.contains("test-keys")) findings.add(Finding(
            key = "debug-build", sev = Sev.MED,
            title = L.t("النظام ليس نسخة رسمية نهائية (test-keys)", "السوفت وير مش نسخة رسمية نهائية (test-keys)", "Firmware is not an official release build (test-keys)"),
            why = L.t("غالبًا هذا نظام معدَّل، ولا يتلقى تحديثات الأمان الرسمية.", "ده غالبًا ROM معدّل، ومش بياخد تحديثات الأمان الرسمية.", "This is usually a modified ROM that does not get official security updates."),
            todo = L.t("ثبّت النظام الرسمي للهاتف.", "نزّل السوفت وير الرسمي للموبايل.", "Install the official firmware."), evidence = listOf("Build tags: ${d.buildTags}"),
        ))

        // 7. patch level and Android KEV
        val patch = try { LocalDate.parse(d.patch) } catch (e: Exception) { null }
        val patchAge = patch?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }
        if (patch != null && patchAge != null) {
            if (patchAge > 120) findings.add(Finding(
                key = "android-patch", sev = Sev.HIGH,
                title = L.t("آخر تحديث أمني على الهاتف بتاريخ ${d.patch} (منذ $patchAge يومًا)", "آخر تحديث أمني على الموبايل من ${d.patch} ($patchAge يوم)", "Last security update was ${d.patch} ($patchAge days ago)"),
                why = L.t("تُصلح Google ثغرات أندرويد كل شهر، والهاتف المتأخر أكثر من 3 أشهر يبقى مكشوفًا لثغرات معروفة ومستغلة.", "Google بتصلّح ثغرات أندرويد كل شهر. الموبايل المتأخر أكتر من 3 شهور بيبقى مفتوح لثغرات معروفة ومستغلة.",
                    "Google fixes Android flaws every month. A phone more than 3 months behind is open to known, exploited flaws."),
                todo = L.t("الإعدادات › تحديث النظام، ثم ثبّت أي تحديث متاح. وإن لم تتوفر أي تحديثات، فقد توقفت الشركة عن دعم هذا الهاتف.", "الإعدادات › تحديث النظام، ونزّل أي تحديث. لو مفيش تحديثات خالص، الشركة بطّلت تدعم الموبايل ده.", "Settings › System update, and install any update. If there are none at all, the maker no longer supports this phone."),
                evidence = listOf("Security patch: ${d.patch}", "Android ${d.release} (SDK ${d.sdk})"), fixes = listOf(Fix.UPDATE),
            )) else if (patchAge > 60) findings.add(Finding(
                key = "android-patch", sev = Sev.LOW,
                title = L.t("التحديث الأمني متأخر قليلًا (${d.patch})", "التحديث الأمني متأخر شوية (${d.patch})", "Security update is a little behind (${d.patch})"),
                why = L.t("صدرت تحديثات أمنية أحدث.", "فيه تحديثات أمنية أحدث نزلت.", "Newer security updates are available."),
                todo = L.t("الإعدادات › تحديث النظام.", "الإعدادات › تحديث النظام.", "Settings › System update."), fixes = listOf(Fix.UPDATE),
            )) else verified.add(L.t("الهاتف عليه تحديث أمني حديث (${d.patch})", "الموبايل عليه تحديث أمني حديث (${d.patch})", "Recent security update installed (${d.patch})"))
            val maker = d.maker.lowercase()
            val rel = intel.kev.filter { k ->
                val added = k.dateAdded ?: return@filter false
                added.isAfter(patch.plusDays(31)) && (
                    k.vendor == "Android" || k.vendor == "Google" ||
                        (k.vendor == "Qualcomm" && Regex("qcom|qualcomm|sm\\d|msm|kona|lahaina|taro|kalama|pineapple").containsMatchIn(d.soc)) ||
                        (k.vendor == "Samsung" && maker.contains("samsung")) ||
                        (k.vendor == "Arm" && Regex("mt\\d|mediatek|exynos|tensor|gs\\d|zuma|unisoc").containsMatchIn(d.soc)))
            }
            if (rel.isNotEmpty()) findings.add(Finding(
                key = "android-kev", sev = if (rel.size >= 3) Sev.HIGH else Sev.MED,
                title = L.t("ثغرات مستغلة اكتُشفت بعد آخر تحديث على هاتفك (${rel.size})", "ثغرات مستغلة اتكشفت بعد آخر تحديث على موبايلك (${rel.size})", "Exploited flaws disclosed after your last update (${rel.size})"),
                why = L.t("هذه ثغرات تؤكد CISA أنها مستغلة فعلًا، واكتُشفت بعد تاريخ آخر تحديث أمني لديك، لذا فهي غالبًا غير مُصلحة.", "دي ثغرات CISA متأكدة إنها بتتستغل فعلًا، واتكشفت بعد تاريخ آخر تحديث أمني عندك، فغالبًا مش متصلحة.",
                    "CISA confirms these are actively exploited, and they were disclosed after your last security update, so they are probably not fixed on this phone."),
                todo = L.t("ثبّت آخر تحديث للنظام.", "نزّل آخر تحديث للنظام.", "Install the latest system update."),
                evidence = rel.take(8).map { "${it.cve}: ${it.vendor} ${it.product} (CISA KEV ${it.dateAdded})" }, fixes = listOf(Fix.UPDATE),
            ))
        }

        // 8. network trust
        if (d.userCerts.isNotEmpty()) findings.add(Finding(
            key = "user-ca", sev = Sev.MED,
            title = L.t("توجد شهادة أمان أضيفت يدويًا إلى الهاتف (${d.userCerts.size})", "فيه شهادة أمان متضافة على الموبايل بإيد (${d.userCerts.size})", "Manually added security certificates (${d.userCerts.size})"),
            why = L.t("تتيح هذه الشهادة لمن ثبّتها فك تشفير جزء من حركة البيانات والاطلاع عليه. تُثبَّت على هواتف العمل، وتُثبَّت كذلك للتجسس.", "الشهادة دي بتخلّي اللي ركّبها يقدر يفك تشفير جزء من الترافيك ويشوفه. بتتركّب في موبايلات الشغل، وبتتركّب كمان للتجسس.",
                "This certificate lets whoever installed it decrypt and read some of your traffic. Work phones use it, and so does spying."),
            todo = L.t("إن لم يكن هاتف عمل: الأمان › Encryption & credentials › User credentials، ثم احذفها.", "لو مش موبايل شغل: الأمان › Encryption & credentials › User credentials، وامسحها.", "If this is not a work phone: Security › Encryption & credentials › User credentials, and remove it."),
            evidence = d.userCerts.take(5), fixes = listOf(Fix.SECURITY),
        ))
        if (d.proxy != null) findings.add(Finding(
            key = "proxy", sev = Sev.MED,
            title = L.t("الإنترنت يمر عبر خادم Proxy", "الإنترنت بيعدّي على Proxy", "Internet traffic goes through a proxy"),
            why = L.t("يتيح الـ Proxy لجهاز آخر رؤية المواقع التي تزورها. وإن لم تكن أنت من ضبطه، فقد غيّر أحدهم إعدادات الواي فاي.", "الـ Proxy بيخلّي جهاز تاني يشوف المواقع اللي بتدخلها. لو مش إنت اللي حاطه، حد غيّر إعدادات الواي فاي.",
                "A proxy lets another machine see the sites you visit. If you did not set it, someone changed your Wi-Fi settings."),
            todo = L.t("إعدادات الواي فاي › هذه الشبكة › Proxy: اجعله None.", "إعدادات الواي فاي › الشبكة دي › Proxy: خليه None.", "Wi-Fi settings › this network › Proxy: set it to None."),
            evidence = listOf("Proxy: ${d.proxy}"), fixes = listOf(Fix.NETWORK),
        ))
        if (d.adbWifi) findings.add(Finding(
            key = "adb-wifi", sev = Sev.MED,
            title = L.t("Wireless debugging مفعّل", "Wireless debugging مفتوح", "Wireless debugging is on"),
            why = L.t("يستطيع أي شخص على الشبكة نفسها ولديه رمز الاقتران التحكم في الهاتف.", "أي حد على نفس الواي فاي وعنده كود الاقتران يقدر يتحكم في الموبايل.", "Anyone on the same Wi-Fi with the pairing code can control the phone."),
            todo = L.t("أوقفه من خيارات المطورين.", "اقفله من خيارات المطورين.", "Turn it off in Developer options."), fixes = listOf(Fix.DEVELOPER),
        ))
        if (!d.secure) findings.add(Finding(
            key = "no-lock", sev = Sev.HIGH,
            title = L.t("لا يوجد قفل للشاشة", "مفيش قفل شاشة", "No screen lock"),
            why = L.t("يستطيع أي شخص يمسك الهاتف دقيقة واحدة تثبيت برنامج تجسس أو فتح حساباتك.", "أي حد يمسك الموبايل دقيقة يقدر يسطّب برنامج تجسس أو يفتح حساباتك.", "Anyone holding the phone for a minute can install stalkerware or open your accounts."),
            todo = L.t("اضبط رقمًا سريًا من 6 أرقام على الأقل.", "حط PIN من 6 أرقام على الأقل.", "Set a PIN of at least 6 digits."), fixes = listOf(Fix.LOCK),
        ))

        // 8b. deep scan
        val deep = d.deep
        if (deep != null) {
            for ((pkg, h) in deep.apkHashes) {
                if (h !in intel.malwareHashes || pkg in stalkerPkgs) continue
                if (byPkg[pkg]?.system == true) {
                    findings.add(Finding(
                        key = "preloaded-malware:$pkg", sev = Sev.CRIT, ioc = true, pkg = pkg,
                        title = L.t("تطبيق نظام خبيث جاء مع الهاتف: ${byPkg[pkg]?.label ?: pkg}", "تطبيق نظام خبيث جاي مع الموبايل: ${byPkg[pkg]?.label ?: pkg}", "Malicious system app shipped with the phone: ${byPkg[pkg]?.label ?: pkg}"),
                        why = L.t("بصمة هذا التطبيق مسجّلة كبرمجية خبيثة، وهو مثبّت كتطبيق نظام، أي أنه جاء مع الهاتف من المصنع أو من البائع. يحدث هذا في بعض الهواتف الرخيصة.", "بصمة التطبيق ده متسجلة كبرمجية خبيثة، وهو متسطّب كتطبيق نظام، يعني جه مع الموبايل من المصنع أو من البايع. ده بيحصل في بعض الموبايلات الرخيصة.",
                            "This app's fingerprint is listed as malware, and it is installed as a system app, so it came with the phone from the factory or the seller. This happens on some cheap phones."),
                        todo = L.t("لا يمكن حذفه بالطريقة العادية. من إعدادات التطبيق اضغط Disable إن كان متاحًا، وأعد الهاتف إلى البائع أو ثبّت النظام الرسمي من الشركة. ولا تستخدم هذا الهاتف لحساباتك البنكية.", "مش هيتمسح بالطريقة العادية. من إعدادات التطبيق دوس Disable لو متاح، ورجّع الموبايل للبايع أو نزّل السوفت وير الرسمي من الشركة. ومتستخدمش الموبايل ده لحسابات البنك.",
                            "It cannot be uninstalled normally. Tap Disable in its app settings if available, return the phone to the seller or install the official firmware, and do not use this phone for banking."),
                        evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(pkg)}", "SHA-256: $h", L.t("تطبيق نظام", "تطبيق نظام", "System app")), fixes = listOf(Fix.APP_SETTINGS),
                    ))
                    continue
                }
                findings.add(Finding(
                    key = "known-malware:$pkg", sev = Sev.CRIT, ioc = true, pkg = pkg,
                    title = L.t("تطبيق خبيث معروف: ${byPkg[pkg]?.label ?: pkg}", "تطبيق خبيث معروف: ${byPkg[pkg]?.label ?: pkg}", "Known malware app: ${byPkg[pkg]?.label ?: pkg}"),
                    why = L.t("بصمة ملف التطبيق (SHA-256) مسجّلة في MalwareBazaar كبرمجية خبيثة.", "بصمة ملف التطبيق (SHA-256) متسجلة في MalwareBazaar كبرمجية خبيثة.", "The app file's SHA-256 is listed in MalwareBazaar as malware."),
                    todo = L.t("احذفه الآن، وغيّر كلمة مرور حساب Google وحسابات البنوك من جهاز آخر.", "امسحه دلوقتي، وغيّر باسورد حساب Google وحسابات البنوك من جهاز تاني.", "Uninstall it now and change your Google and banking passwords from another device."),
                    evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(pkg)}", "SHA-256: $h"), fixes = listOf(Fix.UNINSTALL, Fix.APP_SETTINGS),
                ))
            }
            for (a in user) {
                if (a.pkg in skip || a.pkg in stalkerPkgs) continue
                val pretendsSystem = systemLike.matches(a.label.trim()) || a.pkg.startsWith("com.android.") || a.pkg.startsWith("com.google.android.")
                if (pretendsSystem && isSide(a) && !isGoogleSigned(a)) findings.add(Finding(
                    key = "fake-system:${a.pkg}", sev = Sev.HIGH, ioc = true, pkg = a.pkg,
                    title = L.t("تطبيق متنكّر في هيئة تطبيق نظام: ${a.label}", "تطبيق متنكّر في شكل تطبيق نظام: ${a.label}", "App disguised as a system app: ${a.label}"),
                    why = L.t("اسمه أو اسم حزمته يشبه تطبيقات أندرويد أو Google، لكنه مثبّت من خارج المتجر. تفعل برامج التجسس ذلك كي لا يشك فيها أحد.", "اسمه أو اسم حزمته شبه تطبيقات أندرويد أو Google، بس اتسطّب من برّه المتجر. برامج التجسس بتعمل كده علشان محدش يشك فيها.",
                        "Its name or package looks like an Android or Google app, but it was installed from outside a store. Stalkerware does this to avoid suspicion."),
                    todo = L.t("افتح إعداداته وراجع صلاحياته. وإن لم تكن أنت من ثبّته، فاحذفه.", "افتح إعداداته وشوف صلاحياته. لو مش إنت اللي مسطّبه، امسحه.", "Open its settings and review its permissions. If you did not install it, uninstall it."),
                    evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "${L.t("المصدر", "المصدر", "Installer")}: ${inst(a)}", "${L.t("تاريخ التثبيت", "اتسطّب", "Installed")}: ${date(a.pkg)}") +
                        (if (a.pkg in deep.bootReceivers) listOf(L.t("يعمل تلقائيًا عند تشغيل الهاتف", "بيشتغل لوحده لما الموبايل يفتح", "Starts itself when the phone boots")) else emptyList()),
                    fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
                ))
            }
            val hooks = d.apps.filter { it.pkg in hookApps }
            if (hooks.isNotEmpty()) findings.add(Finding(
                key = "hooking", sev = Sev.HIGH, ioc = true,
                title = L.t("أداة لتعديل التطبيقات (Xposed / LSPosed) مثبّتة", "أداة تعديل تطبيقات (Xposed / LSPosed) متسطّبة", "App-hooking framework installed (Xposed / LSPosed)"),
                why = L.t("تتيح هذه الأدوات تغيير سلوك أي تطبيق، ومنها تطبيقات البنوك، وتستخدمها برامج تجسس متقدمة.", "الأدوات دي بتسمح بتغيير سلوك أي تطبيق، ومنها تطبيقات البنوك، وبتستخدمها برامج تجسس متقدمة.", "These frameworks can change how any app behaves, including banking apps, and advanced spyware uses them."),
                todo = L.t("إن لم تكن أنت من ثبّتها، فأعد ضبط المصنع.", "لو مش إنت اللي مسطّبها، اعمل Factory reset.", "If you did not install it, factory reset the phone."),
                evidence = hooks.map { name(it.pkg) },
            ))
            if (deep.dnsBad.isNotEmpty()) findings.add(Finding(
                key = "dns-hijack", sev = Sev.HIGH, ioc = true,
                title = L.t("الشبكة التي تتصل بها تغيّر عناوين المواقع (DNS)", "الشبكة اللي إنت عليها بتغيّر عناوين المواقع (DNS)", "The network you are on changes site addresses (DNS)"),
                why = L.t("سألنا عن مواقع عناوينها ثابتة ومعروفة، فجاء الرد بعناوين أخرى. وهذا يعني أن الراوتر أو الشبكة قد يكونان مخترقين ويوجّهانك إلى مواقع مزيفة.", "سألنا عن مواقع عناوينها ثابتة ومعروفة، والرد جه بعناوين تانية. ده معناه إن الراوتر أو الشبكة ممكن تكون مخترقة وبتوديك لمواقع مزيفة.",
                    "We looked up sites whose addresses are fixed and well known, and got different answers. The router or network may be compromised and sending you to fake sites."),
                todo = L.t("افصل هذا الواي فاي واستخدم باقة الهاتف، ولا تدخل إلى حساباتك البنكية من هذه الشبكة. وإن كانت شبكة منزلك، فافحصها ببرنامج Horus Shield على الكمبيوتر وغيّر كلمة مرور الراوتر.", "اقطع الواي فاي ده وكمّل على باقة الموبايل، ومتدخلش حسابات البنك من الشبكة دي. ولو دي شبكة بيتك، افحصها من Horus Shield على الكمبيوتر وغيّر باسورد الراوتر.",
                    "Leave this Wi-Fi and use mobile data, and do not open banking apps on this network. If it is your home network, check it with Horus Shield on a computer and change the router password."),
                evidence = deep.dnsBad, fixes = listOf(Fix.NETWORK),
            ))
            deep.deviceOwner?.let { o ->
                val a = byPkg[o]
                if (a != null && !a.system && a.installer !in mdmInstallers) findings.add(Finding(
                    key = "device-owner:$o", sev = Sev.HIGH, ioc = true, pkg = o,
                    title = L.t("تطبيق يتحكم في الهاتف بالكامل (Device owner): ${a.label}", "تطبيق متحكم في الموبايل بالكامل (Device owner): ${a.label}", "An app fully controls the phone (device owner): ${a.label}"),
                    why = L.t("يمنح Device owner التطبيق تحكمًا كاملًا: يقرأ ويقفل ويمسح ويمنع حذف نفسه. وهذا طبيعي في هواتف الشركات فقط.", "Device owner بيدّي التطبيق تحكم كامل: يقرا ويقفل ويمسح ويمنع حذف نفسه. ده طبيعي في موبايلات الشركات بس.",
                        "A device owner has full control: it can monitor, lock, wipe and stop its own removal. That is only normal on company phones."),
                    todo = L.t("إن لم يكن هذا هاتف عمل، فأعد ضبط المصنع، لأنه لن يُزال بالطريقة العادية.", "لو ده مش موبايل شغل، اعمل Factory reset، لأنه مش هيتشال بالطريقة العادية.", "If this is not a work phone, factory reset it, because it cannot be removed normally."),
                    evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(o)}", "${L.t("المصدر", "المصدر", "Installer")}: ${inst(a)}"), fixes = listOf(Fix.SECURITY),
                ))
            }
            for (a in user) {
                val tx = deep.uploads[a.pkg] ?: continue
                if (tx < 50L * 1024 * 1024 || a.pkg in stalkerPkgs) continue
                val hiddenApp = !a.launcher
                if (!hiddenApp && !isSide(a)) continue
                findings.add(Finding(
                    key = "uploads:${a.pkg}", sev = if (hiddenApp) Sev.HIGH else Sev.MED, ioc = hiddenApp, pkg = a.pkg,
                    title = L.t("${a.label} رفع ${tx / (1024 * 1024)} ميجابايت خلال آخر 7 أيام", "${a.label} رفع ${tx / (1024 * 1024)} ميجا في آخر 7 أيام", "${a.label} uploaded ${tx / (1024 * 1024)} MB in the last 7 days"),
                    why = if (hiddenApp) L.t("تطبيق مخفي يرسل كمية كبيرة من البيانات إلى الخارج. هذا هو الشكل المعتاد لبرامج التجسس التي ترسل الصور والتسجيلات.", "تطبيق مخفي بيبعت بيانات كتير لبرّه. ده الشكل المعتاد لبرامج التجسس اللي بتبعت الصور والتسجيلات.", "A hidden app sending a lot of data out. That is how stalkerware uploading photos and recordings usually looks.")
                    else L.t("تطبيق من خارج المتجر يرسل كمية كبيرة من البيانات. قد يكون ذلك طبيعيًا (نسخ احتياطي مثلًا)، وقد لا يكون.", "تطبيق من برّه المتجر بيبعت بيانات كتير. ممكن يكون طبيعي (نسخ احتياطي مثلًا)، وممكن لأ.", "A sideloaded app sending a lot of data. It may be normal (a backup app, say), or not."),
                    todo = L.t("إن لم تكن تعرف ما يرفعه هذا التطبيق، فاحذفه.", "لو مش عارف التطبيق ده بيرفع إيه، امسحه.", "If you do not know what this app uploads, uninstall it."),
                    evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: ${name(a.pkg)}", "Upload: ${tx / (1024 * 1024)} MB / 7d"), fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
                ))
            }
            val debugSigned = user.filter { isSide(it) && it.certSubjects.any { s -> s.contains("CN=Android Debug", true) } && it.pkg !in skip }
            if (debugSigned.isNotEmpty()) findings.add(Finding(
                key = "debug-signed", sev = Sev.LOW,
                title = L.t("تطبيقات موقّعة بتوقيع تجريبي (${debugSigned.size})", "تطبيقات متوقّعة بتوقيع تجريبي (${debugSigned.size})", "Apps signed with a test key (${debugSigned.size})"),
                why = L.t("تُوقَّع التطبيقات الحقيقية بمفتاح الشركة المطوِّرة. والتوقيع التجريبي يعني أن شخصًا ما بنى هذا التطبيق بنفسه أو عدّل عليه.", "التطبيقات الحقيقية بتتوقّع بمفتاح الشركة. التوقيع التجريبي معناه إن حد عمل التطبيق ده بنفسه أو عدّل عليه.", "Real apps are signed with their maker's key. A test signature means someone built or modified this app themselves."),
                todo = L.t("أبقِه فقط إن كنت تعرف من صنعه.", "سيبه بس لو إنت عارف مين عامله.", "Keep it only if you know who made it."), evidence = debugSigned.map { name(it.pkg) },
            ))
            if (!deep.usageAccess) verified.add(L.t("لم يُجرَ فحص استهلاك البيانات: يلزم إذن Usage access", "فحص استهلاك البيانات متعملش: محتاج إذن Usage access", "Data-usage check skipped: needs Usage access"))
            if (deep.apkHashes.isNotEmpty()) verified.add(
                if (intel.malwareHashes.isEmpty()) L.t("حُسبت بصمات ${deep.apkHashes.size} تطبيق، لكن قائمة البصمات الخبيثة ما زالت فارغة في قاعدة التهديدات", "اتحسبت بصمة ${deep.apkHashes.size} تطبيق، بس قايمة البصمات الخبيثة لسه فاضية في قاعدة التهديدات", "${deep.apkHashes.size} app fingerprints taken, but the malware-hash list is still empty in the threat intel")
                else L.t("بصمات ${deep.apkHashes.size} تطبيق من خارج المتجر غير موجودة في قائمة البرمجيات الخبيثة (${intel.malwareHashes.size})", "بصمات ${deep.apkHashes.size} تطبيق من برّه المتجر مش في قايمة البرمجيات الخبيثة (${intel.malwareHashes.size})", "${deep.apkHashes.size} sideloaded app fingerprints are not in the malware list (${intel.malwareHashes.size})"))
            if (deep.dnsChecked && deep.dnsBad.isEmpty()) verified.add(L.t("خادم DNS على هذه الشبكة يرد بشكل صحيح", "الـ DNS على الشبكة دي بيرد صح", "DNS on this network answers honestly"))
        }

        // 8c. root mode (only when the user turned it on and granted su)
        val root = d.root
        if (root != null && !root.ok) verified.add(L.t("وضع Root مفعّل لكن صلاحية su مرفوضة، لذا لم يُجرَ الفحص بصلاحية Root", "وضع Root مفعّل بس صلاحية su مرفوضة، فالفحص بـ Root متعملش", "Root mode is on but su was refused, so the root checks did not run"))
        if (root != null && root.ok) {
            val byConn = root.conns.groupBy { it.pkg }
            for ((p, cs) in byConn) {
                val hits = cs.mapNotNull { c -> intel.c2Ips[c.remote]?.let { tag -> "${c.remote}:${c.port} (${c.proto}) → $tag" } }.distinct()
                if (hits.isEmpty()) continue
                val label = p.split(',').firstOrNull()?.let { byPkg[it]?.label } ?: p
                val pk = p.split(',').firstOrNull()?.takeIf { byPkg.containsKey(it) }
                findings.add(Finding(
                    key = "c2-conn:$p", sev = Sev.CRIT, ioc = true, pkg = pk,
                    title = L.t("$label متصل الآن بخادم تحكم معروف", "$label متصل دلوقتي بسيرفر تحكم معروف", "$label is connected right now to a known command server"),
                    why = L.t("العنوان الذي يتصل به التطبيق مسجّل في قواعد التهديدات كخادم يتحكم في برمجيات خبيثة. هذا ليس احتمالًا، بل اتصال قائم الآن.", "العنوان اللي التطبيق متصل بيه متسجل في قواعد التهديدات كسيرفر بيتحكم في برمجيات خبيثة. ده مش احتمال، ده اتصال شغال دلوقتي.",
                        "The address this app is talking to is listed in threat intel as a malware command server. This is not a guess: the connection is live now."),
                    todo = L.t("اقطع الإنترنت عن هذا التطبيق أو عطّله من هنا، واحفظ الدليل، ثم احذفه وغيّر كلمات مرورك من جهاز آخر.", "اقطع النت عن التطبيق ده أو عطّله من هنا، واحفظ الدليل، وبعدين امسحه وغيّر باسورداتك من جهاز تاني.",
                        "Cut this app's internet or disable it from here, save the evidence, then remove it and change your passwords from another device."),
                    evidence = listOf("${L.t("التطبيق", "التطبيق", "App")}: $p") + hits.take(8),
                    fixes = if (pk != null) listOf(Fix.ROOT_BLOCKNET, Fix.ROOT_DISABLE, Fix.APP_SETTINGS) else emptyList(),
                ))
            }
            val badExe = root.exes.filter { (_, e) ->
                Regex("^(/data/local/tmp|/sdcard|/storage|/data/data/|/data/user/|/data/media)").containsMatchIn(e) || e.contains("(deleted)") ||
                    Regex("frida|gum-js|gadget|xmrig|miner", RegexOption.IGNORE_CASE).containsMatchIn(e)
            }.map { (u, e) -> "uid $u: $e" }.distinct()
            if (badExe.isNotEmpty()) findings.add(Finding(
                key = "root-exe", sev = Sev.HIGH, ioc = true,
                title = L.t("برامج تعمل من أماكن غير طبيعية (${badExe.size})", "برامج شغالة من أماكن مش طبيعية (${badExe.size})", "Programs running from unusual places (${badExe.size})"),
                why = L.t("تعمل برامج أندرويد العادية من مجلدات النظام. أما البرنامج الذي يعمل من /data/local/tmp أو من الذاكرة، أو حُذف ملفه وهو يعمل، فهذا أسلوب أدوات التجسس والتعدين وأدوات مثل Frida.", "برامج أندرويد العادية بتشتغل من مجلدات النظام. برنامج شغال من /data/local/tmp أو من الذاكرة أو ملفه اتمسح وهو شغال، ده أسلوب أدوات التجسس والتعدين وأدوات زي Frida.",
                    "Normal Android programs run from system folders. A program running from /data/local/tmp, from storage, or whose file was deleted while running is how spyware, miners and tools like Frida operate."),
                todo = L.t("إن لم تكن أنت من شغّله، فاحفظ الدليل وأعد ضبط المصنع.", "لو مش إنت اللي مشغّله، احفظ الدليل واعمل Factory reset.", "If you did not start it, save the evidence and factory reset."),
                evidence = badExe.take(10),
            ))
            if (root.natRules.isNotEmpty()) findings.add(Finding(
                key = "root-nat", sev = Sev.HIGH, ioc = true,
                title = L.t("توجد قواعد تحوّل حركة بيانات الهاتف إلى مكان آخر", "فيه قواعد بتحوّل ترافيك الموبايل لمكان تاني", "Rules are redirecting the phone's traffic elsewhere"),
                why = L.t("قواعد DNAT أو REDIRECT في iptables تجعل حركة بياناتك تمر عبر جهاز أو برنامج آخر يستطيع رؤيتها. قد تكون من VPN أو مانع إعلانات، وقد تكون للتجسس.", "قواعد DNAT أو REDIRECT في iptables بتخلّي ترافيكك يعدّي على جهاز أو برنامج تاني يقدر يشوفه. ممكن تكون من VPN أو مانع إعلانات، وممكن تكون تجسس.",
                    "DNAT or REDIRECT rules in iptables send your traffic through another program or machine that can read it. They may come from a VPN or ad blocker, or from spying."),
                todo = L.t("إن لم تكن تعرف من أضافها، فأوقف أي VPN أو مانع إعلانات وأعد الفحص. وإن بقيت، فاحفظ الدليل.", "لو مش عارف مين حاطها، اقفل أي VPN أو مانع إعلانات وأعد الفحص. لو فضلت، احفظ الدليل.", "If you do not know who added them, turn off any VPN or ad blocker and rescan. If they stay, save the evidence."),
                evidence = root.natRules.take(8),
            ))
            if (root.selinux.equals("Permissive", true)) findings.add(Finding(
                key = "root-selinux", sev = Sev.HIGH, ioc = false,
                title = L.t("SELinux معطّل (Permissive)", "SELinux مقفول (Permissive)", "SELinux is off (Permissive)"),
                why = L.t("SELinux هو ما يعزل التطبيقات عن بعضها. وعندما يكون Permissive يتحرك أي تطبيق أو برنامج خبيث بحرية.", "SELinux هو اللي بيعزل التطبيقات عن بعض. لما يبقى Permissive أي تطبيق أو برنامج خبيث بيتحرك بحرية.", "SELinux keeps apps apart. In Permissive mode any app or malware moves freely."),
                todo = L.t("أعده إلى Enforcing، أو ثبّت نسخة نظام رسمية.", "ارجع لـ Enforcing، أو نزّل نسخة سوفت وير رسمية.", "Switch back to Enforcing, or install official firmware."), evidence = listOf("getenforce: ${root.selinux}"),
            ))
            if (root.hostsExtra.isNotEmpty()) {
                val sensitive = root.hostsExtra.filter { Regex("bank|nbe|cib|misr|instapay|fawry|vodafone|google|facebook|whatsapp|apple|microsoft", RegexOption.IGNORE_CASE).containsMatchIn(it) && !it.trim().startsWith("0.0.0.0") && !it.trim().startsWith("127.") }
                findings.add(Finding(
                    key = "root-hosts", sev = if (sensitive.isNotEmpty()) Sev.HIGH else Sev.LOW, ioc = sensitive.isNotEmpty(),
                    title = if (sensitive.isNotEmpty()) L.t("ملف hosts يحوّل مواقع بنوك أو حسابات إلى عناوين أخرى", "ملف hosts بيحوّل مواقع بنوك أو حسابات لعناوين تانية", "The hosts file sends bank or account sites to other addresses")
                    else L.t("ملف hosts معدَّل (${root.hostsExtra.size} سطر)", "ملف hosts متعدّل (${root.hostsExtra.size} سطر)", "The hosts file is modified (${root.hostsExtra.size} lines)"),
                    why = if (sensitive.isNotEmpty()) L.t("هذا يوجّهك إلى موقع مزيف رغم أنك كتبت العنوان الصحيح بنفسك.", "ده بيوديك لموقع مزيف وإنت كاتب العنوان الصح بإيدك.", "This sends you to a fake site even when you type the right address.")
                    else L.t("غالبًا مانع إعلانات مثل AdAway، وهذا طبيعي إن كنت أنت من ثبّته.", "غالبًا مانع إعلانات زي AdAway، وده طبيعي لو إنت مسطّبه.", "Usually an ad blocker such as AdAway, which is fine if you installed it."),
                    todo = L.t("إن لم تكن تعرف من عدّله، فأعده إلى أصله من مدير الـ Root.", "لو مش عارف مين عدّله، رجّعه للأصلي من الـ Root manager.", "If you do not know who changed it, restore it from your root manager."),
                    evidence = (sensitive.ifEmpty { root.hostsExtra }).take(8),
                ))
            }
            if (root.bootScripts.isNotEmpty()) findings.add(Finding(
                key = "root-boot", sev = Sev.MED,
                title = L.t("نصوص برمجية تعمل عند تشغيل الهاتف بصلاحية Root (${root.bootScripts.size})", "سكربتات بتشتغل مع تشغيل الموبايل بصلاحية Root (${root.bootScripts.size})", "Scripts that run as root at boot (${root.bootScripts.size})"),
                why = L.t("أي نص برمجي هنا يعمل بأعلى صلاحية مع كل تشغيل. تضعها الوحدات عادةً، لكنها أيضًا مكان مفضّل للأبواب الخلفية.", "أي سكربت هنا بيشتغل بأعلى صلاحية مع كل تشغيل. الموديولات بتحطها عادي، بس دي كمان مكان مفضّل للأبواب الخلفية.", "Anything here runs with full power at every boot. Modules put scripts here normally, but it is also a favourite spot for backdoors."),
                todo = L.t("راجع القائمة، واحذف أي نص برمجي لا تعرف مصدره.", "راجع القايمة، وامسح أي سكربت مش عارف جه منين.", "Review the list and remove any script you do not recognise."), evidence = root.bootScripts.take(12),
            ))
            if (root.modules.isNotEmpty()) findings.add(Finding(
                key = "root-modules", sev = Sev.LOW,
                title = L.t("وحدات Magisk مثبّتة (${root.modules.size})", "موديولات Magisk متسطّبة (${root.modules.size})", "Magisk modules installed (${root.modules.size})"),
                why = L.t("تعدّل الوحدات النظام نفسه، وأي وحدة لم تثبّتها أنت قد تكون بابًا خلفيًا.", "الموديولات بتعدّل النظام نفسه. أي موديول مش إنت اللي مسطّبه ممكن يكون باب خلفي.", "Modules change the system itself. Any module you did not install may be a backdoor."),
                todo = L.t("افتح Magisk › Modules وراجعها.", "افتح Magisk ← Modules وراجعها.", "Open Magisk › Modules and review them."), evidence = root.modules.map { it.replace('|', ' ') }.take(15),
            ))
            val tmpExec = root.tmpFiles.filter { it.length >= 10 && it.startsWith("-") && it.substring(0, 10).contains('x') }
            if (tmpExec.isNotEmpty()) findings.add(Finding(
                key = "root-tmp", sev = Sev.MED,
                title = L.t("ملفات تنفيذية في /data/local/tmp (${tmpExec.size})", "ملفات تنفيذية في /data/local/tmp (${tmpExec.size})", "Executable files in /data/local/tmp (${tmpExec.size})"),
                why = L.t("تستخدم أدوات الاختراق والتحليل هذا المكان لأنه يسمح بتشغيل البرامج.", "المكان ده بتستخدمه أدوات الاختراق والتحليل لأنه بيقبل تشغيل برامج.", "Hacking and analysis tools use this folder because programs can run from it."),
                todo = L.t("إن لم تكن أنت من وضعها، فاحفظ الدليل واحذفها.", "لو مش إنت اللي حاطها، احفظ الدليل وامسحها.", "If you did not put them there, save the evidence and delete them."), evidence = tmpExec.take(10),
            ))
            verified.add(L.t("وضع Root: فُحص ${root.conns.size} اتصالًا قائمًا و${root.exes.size} برنامجًا يعمل", "وضع Root: اتفحص ${root.conns.size} اتصال شغال و${root.exes.size} برنامج شغال", "Root mode: ${root.conns.size} live connections and ${root.exes.size} running programs checked"))
            if (byConn.values.flatten().none { intel.c2Ips.containsKey(it.remote) }) verified.add(L.t("لا يوجد اتصال قائم بأي خادم تحكم معروف (${intel.c2Ips.size} عنوان)", "مفيش اتصال شغال بأي سيرفر تحكم معروف (${intel.c2Ips.size} عنوان)", "No live connection to any known command server (${intel.c2Ips.size} addresses)"))
        }

        // 9. hardening checklist
        val P = CheckState.PASS; val W = CheckState.WARN; val F = CheckState.FAIL
        checks.add(Check(L.t("قفل الشاشة", "قفل الشاشة", "Screen lock"), if (d.secure) P else F, if (d.secure) L.t("موجود", "موجود", "Set") else L.t("غير موجود", "مفيش", "None"), L.t("الرقم السري (PIN) أقوى من النمط", "PIN أقوى من Pattern", "A PIN is stronger than a pattern"), Fix.LOCK))
        val encOk = d.encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || d.encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER || d.sdk >= 29
        checks.add(Check(L.t("تشفير الهاتف", "تشفير الموبايل", "Encryption"), if (encOk) P else F, if (encOk) L.t("مشفّر", "مشفّر", "Encrypted") else L.t("غير مشفّر", "مش مشفّر", "Not encrypted")))
        checks.add(Check(L.t("إصدار Android", "إصدار Android", "Android version"), if (d.sdk >= 33) P else if (d.sdk >= 31) W else F, "Android ${d.release} (SDK ${d.sdk})",
            if (d.sdk < 33) L.t("غالبًا لم يعد هذا الإصدار يتلقى تحديثات أمنية", "الإصدار ده غالبًا مبقاش بياخد تحديثات أمنية", "This version probably no longer gets security updates") else ""))
        checks.add(Check(L.t("التحديث الأمني", "التحديث الأمني", "Security update"), when { patchAge == null -> W; patchAge <= 60 -> P; patchAge <= 120 -> W; else -> F }, d.patch.ifEmpty { "?" }, "", Fix.UPDATE))
        checks.add(Check("USB debugging", if (d.adb) W else P, if (d.adb) L.t("مفعّل", "مفتوح", "On") else L.t("معطّل", "مقفول", "Off"), if (d.adb) L.t("عطّله إن لم تكن تستخدمه", "اقفله لو مش بتستخدمه", "Turn it off if you are not using it") else "", if (d.adb) Fix.DEVELOPER else null))
        if (d.adbWifi) checks.add(Check("Wireless debugging", F, L.t("مفعّل", "مفتوح", "On"), "", Fix.DEVELOPER))
        checks.add(Check(L.t("خيارات المطورين", "خيارات المطورين", "Developer options"), if (d.devOptions) W else P, if (d.devOptions) L.t("مفعّلة", "مفتوحة", "On") else L.t("معطّلة", "مقفولة", "Off"), "", if (d.devOptions) Fix.DEVELOPER else null))
        checks.add(Check("Private DNS", when (d.privateDnsActive) { true -> P; else -> W },
            when { d.privateDnsActive == true && !d.privateDnsHost.isNullOrEmpty() -> d.privateDnsHost!!; d.privateDnsActive == true -> L.t("تلقائي", "تلقائي", "Automatic"); d.privateDnsActive == null -> L.t("غير معروف", "مش معروف", "Unknown"); else -> L.t("معطّل", "مقفول", "Off") },
            L.t("Private DNS مع فلترة (مثل NextDNS) يحجب مواقع الاحتيال على أي شبكة", "Private DNS بفلتر (زي NextDNS) بيحجب مواقع النصب في أي شبكة", "A filtering Private DNS (such as NextDNS) blocks scam sites on any network"), Fix.NETWORK))
        checks.add(Check("Root", if (rooted) F else P, if (rooted) L.t("موجود", "موجود", "Present") else L.t("غير موجود", "مش موجود", "Not found")))
        checks.add(Check(L.t("شهادات مضافة يدويًا", "شهادات متضافة بإيد", "User certificates"), if (d.userCerts.isEmpty()) P else W, d.userCerts.size.toString(), "", if (d.userCerts.isEmpty()) null else Fix.SECURITY))
        checks.add(Check("VPN", P, if (d.vpn) L.t("يعمل الآن", "شغال دلوقتي", "Active now") else L.t("لا يعمل", "مش شغال", "Not active"), if (d.vpn) L.t("تأكد من أنه VPN ثبّته أنت", "اتأكد إنه VPN إنت اللي مسطّبه", "Make sure it is a VPN you installed") else "", Fix.VPN))

        if (accUser.isEmpty() && hidden.isEmpty()) verified.add(L.t("لا توجد تطبيقات من خارج المتجر تملك صلاحية Accessibility", "مفيش تطبيقات من برّه ليها صلاحية Accessibility", "No third-party app has Accessibility access"))
        if (admUser.isEmpty()) verified.add(L.t("لا توجد تطبيقات Device admin غير تطبيقات النظام", "مفيش تطبيقات Device admin غير بتاعة النظام", "No third-party device admin apps"))
        verified.add(L.t("فُحص ${d.apps.size} تطبيقًا، منها ${user.size} ثبّتها المستخدم", "اتفحص ${d.apps.size} تطبيق، منهم ${user.size} متسطّب من المستخدم", "${d.apps.size} apps checked, ${user.size} installed by the user"))

        // 10. what changed since last scan
        val changes = ArrayList<String>()
        d.prev?.let { p ->
            user.filter { it.pkg !in p.apps }.take(15).forEach { changes.add(L.t("تطبيق جديد: ", "تطبيق جديد: ", "New app: ") + name(it.pkg)) }
            d.accessibility.filter { it !in p.acc && byPkg[it]?.system != true }.forEach { changes.add(L.t("صلاحية Accessibility جديدة: ", "صلاحية Accessibility جديدة: ", "New Accessibility access: ") + name(it)) }
            d.admins.filter { it !in p.admins && byPkg[it]?.system != true }.forEach { changes.add(L.t("Device admin جديد: ", "Device admin جديد: ", "New device admin: ") + name(it)) }
            val gone = p.apps.count { it !in byPkg }
            if (gone > 0) changes.add(L.t("حُذف $gone تطبيق", "$gone تطبيق اتمسح", "$gone apps removed"))
        }

        // 11. exposure: who can hear and see you
        fun exp(label: String, perms: Set<String>) = Exposure(label, user.filter { a -> a.granted.any { it in perms } }.map { it.label }.sorted())
        val exposure = listOf(
            exp(L.t("الميكروفون", "الميكروفون", "Microphone"), setOf(Manifest.permission.RECORD_AUDIO)),
            exp(L.t("الكاميرا", "الكاميرا", "Camera"), setOf(Manifest.permission.CAMERA)),
            exp(L.t("الموقع الدقيق", "الموقع بالظبط", "Precise location"), setOf(Manifest.permission.ACCESS_FINE_LOCATION)),
            exp(L.t("الموقع في الخلفية", "الموقع في الخلفية", "Location in background"), setOf("android.permission.ACCESS_BACKGROUND_LOCATION")),
            exp(L.t("الرسائل SMS", "الرسايل SMS", "SMS messages"), setOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)),
            exp(L.t("سجل المكالمات", "سجل المكالمات", "Call log"), setOf(Manifest.permission.READ_CALL_LOG)),
            exp(L.t("جهات الاتصال", "جهات الاتصال", "Contacts"), setOf(Manifest.permission.READ_CONTACTS)),
        )

        // root response buttons on the findings that point at an app
        if (root != null && root.ok) {
            for (i in findings.indices) {
                val f = findings[i]
                if (f.pkg != null && f.ioc && byPkg[f.pkg]?.system != true && Fix.ROOT_DISABLE !in f.fixes)
                    findings[i] = f.copy(fixes = listOf(Fix.ROOT_STOP, Fix.ROOT_BLOCKNET, Fix.ROOT_DISABLE) + f.fixes)
            }
        }

        // score
        val sorted = findings.sortedBy { it.sev.rank }
        // one kind of problem found in many apps counts at most twice, so a long list cannot drive the score to zero on its own
        var lows = 0; var penalty = 0
        for ((_, g) in sorted.filter { it.sev != Sev.LOW }.groupBy { it.key.substringBefore(':') }) {
            penalty += minOf(g.map { it.sev.weight }.sum(), 2 * g.maxOf { it.sev.weight })
        }
        lows = sorted.count { it.sev == Sev.LOW }
        penalty += minOf(lows, 6)
        penalty += minOf(checks.map { when (it.state) { CheckState.FAIL -> 3; CheckState.WARN -> 1; else -> 0 } }.sum(), 8)
        var score = (100 - penalty).coerceIn(0, 100)
        val compromised = sorted.any { it.sev == Sev.CRIT && it.ioc }
        if (compromised) score = minOf(score, 20)
        val verdict = when { compromised -> "compromised"; sorted.any { it.ioc } -> "suspicious"; else -> "clean" }
        val grade = when { score >= 85 -> L.t("ممتاز", "ممتاز", "Excellent"); score >= 70 -> L.t("جيد", "كويس", "Good"); score >= 50 -> L.t("يحتاج إلى تحسين", "محتاج شغل", "Needs work"); else -> L.t("ضعيف", "ضعيف", "Weak") }
        val headline = when (verdict) {
            "compromised" -> L.t("توجد علامات قوية على تجسس أو تطبيق مزيف. اقرأ التعليمات قبل حذف أي شيء.", "فيه علامات قوية على تجسس أو تطبيق مزيف. اقرا التعليمات قبل ما تمسح أي حاجة.", "Strong signs of stalkerware or a fake app. Read the advice before removing anything.")
            "suspicious" -> L.t("توجد أمور تحتاج إلى التحقق منها قبل الحكم.", "فيه حاجات محتاجة تتأكد منها قبل ما نحكم.", "Some things need checking before a verdict.")
            else -> L.t("لا توجد علامات تجسس أو تطبيقات مزيفة. في الأسفل ما يمكنك فعله لتقوية حماية الهاتف.", "مفيش علامات تجسس أو تطبيقات مزيفة. تحت الحاجات اللي تقوّي بيها الموبايل.", "No signs of stalkerware or fake apps. Below are ways to harden the phone.")
        }
        if (changes.isEmpty() && d.prev == null) changes.add(L.t("هذا أول فحص، وسيخبرك الفحص التالي بما تغيّر.", "ده أول فحص، والفحص الجاي هيقولك إيه اللي اتغيّر.", "This is the first scan. The next one will show what changed."))
        else {
            val prevScore = d.prev?.score
            if (prevScore != null && prevScore != score) changes.add(0, L.t("كانت الحماية $prevScore", "الحماية كانت $prevScore", "Protection was $prevScore"))
        }

        return ScanResult(
            atMillis = d.atMillis, score = score, grade = grade, verdict = verdict, headline = headline,
            findings = sorted, checks = checks, changes = changes, verified = verified, exposure = exposure,
            appCount = d.apps.size, userAppCount = user.size, intelDate = intel.date,
            deep = d.deep != null, iocCount = sorted.count { it.ioc },
        )
    }
}
