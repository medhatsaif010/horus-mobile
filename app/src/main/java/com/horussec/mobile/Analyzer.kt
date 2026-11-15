package com.horussec.mobile

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

    fun isSideloaded(a: AppInfo, selfPkg: String) = !a.system && a.pkg != selfPkg && (a.installer == null || a.installer !in stores)

    fun analyze(d: DeviceData, intel: IntelData): ScanResult {
        val findings = ArrayList<Finding>()
        val checks = ArrayList<Check>()
        val verified = ArrayList<String>()
        val byPkg = d.apps.associateBy { it.pkg }
        val user = d.apps.filter { !it.system && it.pkg != d.selfPkg }
        fun name(p: String): String { val a = byPkg[p]; return if (a == null || a.label == p) p else "${a.label} ($p)" }
        fun date(p: String): String = byPkg[p]?.firstInstall?.let { if (it > 0) day.format(Date(it)) else "" } ?: ""
        fun isSide(a: AppInfo) = isSideloaded(a, d.selfPkg)
        fun inst(a: AppInfo) = a.installer ?: L.t("ملف APK أو جهة مش معروفة", "APK file or unknown source")

        // 1. known stalkerware (by package name or by signing certificate)
        val stalkerPkgs = HashSet<String>()
        for (a in user) {
            val byName = intel.byPackage[a.pkg]
            val byCert = a.certSha1.firstNotNullOfOrNull { intel.byCert[it] }
            val s = byName ?: byCert ?: continue
            stalkerPkgs.add(a.pkg)
            val ev = arrayListOf(
                "${L.t("التطبيق", "App")}: ${name(a.pkg)} ${a.version}",
                "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}",
                "${L.t("المصدر", "Installer")}: ${inst(a)}",
                "${L.t("مطابق لـ", "Matches")}: Echap stalkerware-indicators (${s.name}${if (byName == null) L.t(", بالتوقيع الرقمي", ", by signing certificate") else ""})",
            )
            if (a.pkg in d.accessibility) ev.add("Accessibility: ${L.t("مفعّلة", "enabled")}")
            if (a.pkg in d.admins) ev.add("Device admin: ${L.t("أيوه", "yes")}")
            if (!a.launcher) ev.add(L.t("مخفي من الشاشة الرئيسية", "Hidden from the home screen"))
            findings.add(Finding(
                key = "stalkerware:${a.pkg}", sev = Sev.CRIT, ioc = true, stalker = true, pkg = a.pkg,
                title = L.t("برنامج تجسس معروف: ${s.name}", "Known stalkerware: ${s.name}"),
                why = L.t("البرامج دي بيسطّبها حد مسك الموبايل في إيده، وبتبعت الرسايل والمكالمات والموقع والصور لحد تاني من غير ما صاحب الموبايل يعرف.",
                    "Someone with access to the phone installs these. They send messages, calls, location and photos to another person without the owner knowing."),
                todo = L.t("متمسحهوش على طول لو حاسس إن فيه خطر عليك، لأن اللي سطّبه هيعرف. احفظ التقرير ده (شارك الملخص) واطلب مساعدة من حد تثق فيه، وبعدين امسحه وغيّر باسورد حساب Google من جهاز تاني.",
                    "Do not remove it right away if you feel at risk: whoever installed it will notice. Save this report (Share summary), get help from someone you trust, then remove it and change your Google password from another device."),
                evidence = ev, fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
            ))
        }
        if (intel.stalkerware.isNotEmpty() && stalkerPkgs.isEmpty())
            verified.add(L.t("مفيش أي برنامج من قايمة برامج التجسس المعروفة (${intel.stalkerware.size} برنامج)، بالاسم أو بالتوقيع", "None of the ${intel.stalkerware.size} known stalkerware apps, by name or signature"))

        // 2. hidden apps with sensitive access
        val hidden = HashSet<String>()
        for (a in user) {
            if (a.launcher || a.pkg in stalkerPkgs) continue
            val g = a.granted.filter { it in sensitive }
            val power = ArrayList<String>()
            if (a.pkg in d.accessibility) power.add("Accessibility")
            if (a.pkg in d.admins) power.add("Device admin")
            if (a.pkg in d.notifListeners) power.add(L.t("قراية الإشعارات", "Notification access"))
            if (power.isEmpty() && g.size < 2) continue
            hidden.add(a.pkg)
            val high = power.isNotEmpty()
            findings.add(Finding(
                key = "hidden-app:${a.pkg}", sev = if (high) Sev.HIGH else Sev.MED, ioc = high, pkg = a.pkg,
                title = L.t("تطبيق مخفي وليه صلاحيات حساسة: ${a.label}", "Hidden app with sensitive access: ${a.label}"),
                why = L.t("تطبيق ملوش أيقونة على الشاشة ومعاه صلاحيات زي الرسايل أو الموقع أو الميكروفون. ده الشكل المعتاد لبرامج التجسس اللي لسه مش في القوايم.",
                    "An app with no home-screen icon that holds access like SMS, location or the microphone. That is how stalkerware not yet on any list usually looks."),
                todo = L.t("لو مش عارف التطبيق ده، افتح إعداداته واسحب صلاحياته وبعدين امسحه.", "If you do not recognise it, open its settings, remove its permissions, then uninstall it."),
                evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)}", "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}", "${L.t("المصدر", "Installer")}: ${inst(a)}") +
                    power.map { "${L.t("صلاحية", "Access")}: $it" } + g.map { "${L.t("إذن", "Permission")}: ${short(it)}" },
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
                title = L.t("تطبيق باسم ${hit.first} متسطّب من برّه المتجر: غالبًا مزيف", "${hit.first} app installed outside the store: probably fake"),
                why = L.t("تطبيقات البنوك والمحافظ الرسمية بتتنزّل من المتجر بس. نسخة بنفس الاسم جاية من لينك على واتساب أو تيليجرام هي أشهر طريقة لسرقة الحسابات وأكواد OTP في مصر.",
                    "Official bank and wallet apps only come from the app store. A copy with the same name from a WhatsApp or Telegram link is the most common way accounts and OTP codes are stolen."),
                todo = L.t("امسحه دلوقتي ونزّل التطبيق الرسمي من Google Play بس. لو كنت دخلت بياناتك فيه، كلّم البنك حالًا واقفل الكارت أو المحفظة وغيّر الـ PIN.",
                    "Uninstall it now and get the official app from Google Play only. If you entered your details in it, call your bank right away, block the card or wallet and change the PIN."),
                evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)} ${a.version}", "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}", "${L.t("المصدر", "Installer")}: ${inst(a)}"),
                fixes = listOf(Fix.UNINSTALL, Fix.APP_SETTINGS),
            ))
        }
        val bankerPkgs = HashSet<String>()
        for (a in user) {
            if (!isSide(a) || a.pkg in finHits || a.pkg in stalkerPkgs || a.pkg in hidden) continue
            val sms = a.granted.filter { it == Manifest.permission.READ_SMS || it == Manifest.permission.RECEIVE_SMS }
            val pw = ArrayList<String>()
            if (a.pkg in d.accessibility) pw.add("Accessibility")
            if (a.overlay) pw.add(L.t("الظهور فوق التطبيقات", "Display over other apps"))
            if (a.pkg in d.notifListeners) pw.add(L.t("قراية الإشعارات", "Notification access"))
            if (sms.isEmpty() || pw.isEmpty()) continue
            bankerPkgs.add(a.pkg)
            findings.add(Finding(
                key = "banker-pattern:${a.pkg}", sev = Sev.HIGH, ioc = true, pkg = a.pkg,
                title = L.t("تطبيق من برّه المتجر بيقرا الرسايل ومعاه صلاحيات تحكم: ${a.label}", "Sideloaded app reads SMS and has control access: ${a.label}"),
                why = L.t("قراية الرسايل مع Accessibility أو الظهور فوق التطبيقات هي التركيبة اللي أحصنة طروادة البنكية محتاجاها علشان تسرق كود OTP وتحوّل فلوس من غير ما تحس.",
                    "Reading SMS together with Accessibility or screen overlays is exactly what banking trojans need to steal OTP codes and move money unnoticed."),
                todo = L.t("لو مش عارف التطبيق ده كويس امسحه، وراجع حركات حساباتك البنكية والمحافظ.", "If you do not know this app well, uninstall it and check your bank and wallet transactions."),
                evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)}", "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}") +
                    sms.map { "${L.t("إذن", "Permission")}: ${short(it)}" } + pw.map { "${L.t("صلاحية", "Access")}: $it" },
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
                title = L.t("تطبيق ليه صلاحية Accessibility (يشوف الشاشة ويدوس بدالك): ${a.label}", "App with Accessibility access (sees the screen, taps for you): ${a.label}"),
                why = if (a.overlay) L.t("Accessibility مع الظهور فوق التطبيقات هو أسلوب أحصنة طروادة البنكية: بيرسم شاشة دخول مزيفة فوق تطبيق البنك ويقرا اللي بتكتبه.",
                    "Accessibility plus screen overlays is the banking-trojan technique: it draws a fake login over your bank app and reads what you type.")
                else L.t("Accessibility بتدّي التطبيق قدرة يشوف كل اللي على الشاشة ويدوس بدالك. التطبيقات اللي محتاجاها فعلًا قليلة، زي قارئ الشاشة أو مدير الباسوردات.",
                    "Accessibility lets an app see everything on screen and tap for you. Few apps really need it, such as screen readers or password managers."),
                todo = L.t("لو مش عارف ليه محتاجها، اقفلها من إعدادات Accessibility.", "If you do not know why it needs this, turn it off in Accessibility settings."),
                evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)}", "${L.t("المصدر", "Installer")}: ${inst(a)}", "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}"),
                fixes = listOf(Fix.ACCESSIBILITY, Fix.APP_SETTINGS),
            ))
        }
        val admUser = user.filter { it.pkg in d.admins && it.pkg !in skip }
        for (a in admUser) {
            findings.add(Finding(
                key = "device-admin:${a.pkg}", sev = Sev.MED, pkg = a.pkg,
                title = L.t("تطبيق متعمله Device admin (صعب يتمسح): ${a.label}", "App is a device admin (hard to remove): ${a.label}"),
                why = L.t("Device admin بيخلّي التطبيق يقفل الشاشة أو يمسح الموبايل، وبيمنع مسحه بالطريقة العادية. بتستخدمه تطبيقات الشغل، وكمان برامج التجسس.",
                    "Device admin lets an app lock or wipe the phone and blocks normal uninstalling. Work apps use it, and so does stalkerware."),
                todo = L.t("لو مش تطبيق شغل أو حماية إنت عارفه: الأمان › Device admin apps، اقفله وبعدين امسحه.", "If it is not a work or security app you know: Security › Device admin apps, turn it off, then uninstall."),
                evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)}", "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}"),
                fixes = listOf(Fix.SECURITY),
            ))
        }
        val nlUser = user.filter { it.pkg in d.notifListeners && it.pkg !in skip }
        if (nlUser.isNotEmpty()) findings.add(Finding(
            key = "notification-access", sev = Sev.LOW,
            title = L.t("تطبيقات بتقرا كل الإشعارات، ومنها أكواد التحقق (${nlUser.size})", "Apps that read all notifications, including codes (${nlUser.size})"),
            why = L.t("أي تطبيق بيقرا الإشعارات يقدر يشوف رسايل واتساب وأكواد OTP اللي بتوصلك.", "Any app with notification access can read WhatsApp messages and the OTP codes you receive."),
            todo = L.t("سيب بس اللي محتاجه فعلًا، زي الساعة الذكية.", "Keep only the ones you really need, such as a smartwatch app."),
            evidence = nlUser.map { name(it.pkg) }, fixes = listOf(Fix.NOTIFICATIONS),
        ))

        // 5. sideloading
        val side = user.filter { isSide(it) }
        if (side.isNotEmpty()) findings.add(Finding(
            key = "sideloaded-apps", sev = if (side.size >= 3) Sev.MED else Sev.LOW,
            title = L.t("تطبيقات اتسطبت من برّه المتجر (${side.size})", "Apps installed from outside an app store (${side.size})"),
            why = L.t("أغلب البرامج الضارة على أندرويد بتيجي كملف APK من لينك على واتساب أو تيليجرام أو موقع، مش من Play.",
                "Most Android malware arrives as an APK from a WhatsApp, Telegram or website link, not from Play."),
            todo = L.t("امسح أي تطبيق منهم مش عارف جه منين.", "Uninstall any of these you do not know the origin of."),
            evidence = side.take(20).map { "${name(it.pkg)}  ·  ${date(it.pkg)}  ·  ${inst(it)}" },
        ))
        val installers = user.filter { it.canInstall == true && it.installer != null && it.pkg !in stores }
        if (installers.isNotEmpty()) findings.add(Finding(
            key = "install-unknown-apps", sev = Sev.LOW,
            title = L.t("تطبيقات مسموحلها تسطّب تطبيقات تانية (${installers.size})", "Apps allowed to install other apps (${installers.size})"),
            why = L.t("لو تطبيق منهم اتخدع أو كان ضار، يقدر ينزّل ويسطّب تطبيقات من غير متجر.", "If one of them is tricked or malicious, it can install apps without a store."),
            todo = L.t("اقفلها للكل إلا لو محتاجها دلوقتي.", "Turn it off for all of them unless you need it right now."),
            evidence = installers.map { name(it.pkg) }, fixes = listOf(Fix.UNKNOWN_SOURCES),
        ))

        // 6. device integrity
        val rootPk = d.apps.filter { it.pkg in rootApps }
        val rooted = d.suPaths.isNotEmpty() || rootPk.isNotEmpty()
        if (rooted) findings.add(Finding(
            key = "rooted", sev = Sev.HIGH,
            title = L.t("الموبايل متعمله Root", "The phone is rooted"),
            why = L.t("الـ Root بيشيل أهم حماية في أندرويد: أي تطبيق ياخد الصلاحية يقدر يقرا بيانات كل التطبيقات التانية، ومنها تطبيقات البنوك.",
                "Root removes Android's main protection: any app given root can read every other app's data, including banking apps."),
            todo = L.t("لو مش إنت اللي عامله، اعمل Factory reset ونزّل السوفت وير الرسمي.", "If you did not root it yourself, factory reset and install the official firmware."),
            evidence = d.suPaths.map { "su: $it" } + rootPk.map { name(it.pkg) },
        ))
        if (d.buildTags.contains("test-keys")) findings.add(Finding(
            key = "debug-build", sev = Sev.MED,
            title = L.t("السوفت وير مش نسخة رسمية نهائية (test-keys)", "Firmware is not an official release build (test-keys)"),
            why = L.t("ده غالبًا ROM معدّل، ومش بياخد تحديثات الأمان الرسمية.", "This is usually a modified ROM that does not get official security updates."),
            todo = L.t("نزّل السوفت وير الرسمي للموبايل.", "Install the official firmware."), evidence = listOf("Build tags: ${d.buildTags}"),
        ))

        // 7. patch level and Android KEV
        val patch = try { LocalDate.parse(d.patch) } catch (e: Exception) { null }
        val patchAge = patch?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }
        if (patch != null && patchAge != null) {
            if (patchAge > 120) findings.add(Finding(
                key = "android-patch", sev = Sev.HIGH,
                title = L.t("آخر تحديث أمني على الموبايل من ${d.patch} ($patchAge يوم)", "Last security update was ${d.patch} ($patchAge days ago)"),
                why = L.t("Google بتصلّح ثغرات أندرويد كل شهر. الموبايل المتأخر أكتر من 3 شهور بيبقى مفتوح لثغرات معروفة ومستغلة.",
                    "Google fixes Android flaws every month. A phone more than 3 months behind is open to known, exploited flaws."),
                todo = L.t("الإعدادات › تحديث النظام، ونزّل أي تحديث. لو مفيش تحديثات خالص، الشركة بطّلت تدعم الموبايل ده.", "Settings › System update, and install any update. If there are none at all, the maker no longer supports this phone."),
                evidence = listOf("Security patch: ${d.patch}", "Android ${d.release} (SDK ${d.sdk})"), fixes = listOf(Fix.UPDATE),
            )) else if (patchAge > 60) findings.add(Finding(
                key = "android-patch", sev = Sev.LOW,
                title = L.t("التحديث الأمني متأخر شوية (${d.patch})", "Security update is a little behind (${d.patch})"),
                why = L.t("فيه تحديثات أمنية أحدث نزلت.", "Newer security updates are available."),
                todo = L.t("الإعدادات › تحديث النظام.", "Settings › System update."), fixes = listOf(Fix.UPDATE),
            )) else verified.add(L.t("الموبايل عليه تحديث أمني حديث (${d.patch})", "Recent security update installed (${d.patch})"))
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
                title = L.t("ثغرات مستغلة اتكشفت بعد آخر تحديث على موبايلك (${rel.size})", "Exploited flaws disclosed after your last update (${rel.size})"),
                why = L.t("دي ثغرات CISA متأكدة إنها بتتستغل فعلًا، واتكشفت بعد تاريخ آخر تحديث أمني عندك، فغالبًا مش متصلحة.",
                    "CISA confirms these are actively exploited, and they were disclosed after your last security update, so they are probably not fixed on this phone."),
                todo = L.t("نزّل آخر تحديث للنظام.", "Install the latest system update."),
                evidence = rel.take(8).map { "${it.cve}: ${it.vendor} ${it.product} (CISA KEV ${it.dateAdded})" }, fixes = listOf(Fix.UPDATE),
            ))
        }

        // 8. network trust
        if (d.userCerts.isNotEmpty()) findings.add(Finding(
            key = "user-ca", sev = Sev.MED,
            title = L.t("فيه شهادة أمان متضافة على الموبايل بإيد (${d.userCerts.size})", "Manually added security certificates (${d.userCerts.size})"),
            why = L.t("الشهادة دي بتخلّي اللي ركّبها يقدر يفك تشفير جزء من الترافيك ويشوفه. بتتركّب في موبايلات الشغل، وبتتركّب كمان للتجسس.",
                "This certificate lets whoever installed it decrypt and read some of your traffic. Work phones use it, and so does spying."),
            todo = L.t("لو مش موبايل شغل: الأمان › Encryption & credentials › User credentials، وامسحها.", "If this is not a work phone: Security › Encryption & credentials › User credentials, and remove it."),
            evidence = d.userCerts.take(5), fixes = listOf(Fix.SECURITY),
        ))
        if (d.proxy != null) findings.add(Finding(
            key = "proxy", sev = Sev.MED,
            title = L.t("الإنترنت بيعدّي على Proxy", "Internet traffic goes through a proxy"),
            why = L.t("الـ Proxy بيخلّي جهاز تاني يشوف المواقع اللي بتدخلها. لو مش إنت اللي حاطه، حد غيّر إعدادات الواي فاي.",
                "A proxy lets another machine see the sites you visit. If you did not set it, someone changed your Wi-Fi settings."),
            todo = L.t("إعدادات الواي فاي › الشبكة دي › Proxy: خليه None.", "Wi-Fi settings › this network › Proxy: set it to None."),
            evidence = listOf("Proxy: ${d.proxy}"), fixes = listOf(Fix.NETWORK),
        ))
        if (d.adbWifi) findings.add(Finding(
            key = "adb-wifi", sev = Sev.MED,
            title = L.t("Wireless debugging مفتوح", "Wireless debugging is on"),
            why = L.t("أي حد على نفس الواي فاي وعنده كود الاقتران يقدر يتحكم في الموبايل.", "Anyone on the same Wi-Fi with the pairing code can control the phone."),
            todo = L.t("اقفله من خيارات المطورين.", "Turn it off in Developer options."), fixes = listOf(Fix.DEVELOPER),
        ))
        if (!d.secure) findings.add(Finding(
            key = "no-lock", sev = Sev.HIGH,
            title = L.t("مفيش قفل شاشة", "No screen lock"),
            why = L.t("أي حد يمسك الموبايل دقيقة يقدر يسطّب برنامج تجسس أو يفتح حساباتك.", "Anyone holding the phone for a minute can install stalkerware or open your accounts."),
            todo = L.t("حط PIN من 6 أرقام على الأقل.", "Set a PIN of at least 6 digits."), fixes = listOf(Fix.LOCK),
        ))

        // 8b. deep scan
        val deep = d.deep
        if (deep != null) {
            for ((pkg, h) in deep.apkHashes) {
                if (h !in intel.malwareHashes || pkg in stalkerPkgs) continue
                findings.add(Finding(
                    key = "known-malware:$pkg", sev = Sev.CRIT, ioc = true, pkg = pkg,
                    title = L.t("تطبيق خبيث معروف: ${byPkg[pkg]?.label ?: pkg}", "Known malware app: ${byPkg[pkg]?.label ?: pkg}"),
                    why = L.t("بصمة ملف التطبيق (SHA-256) متسجلة في MalwareBazaar كبرمجية خبيثة.", "The app file's SHA-256 is listed in MalwareBazaar as malware."),
                    todo = L.t("امسحه دلوقتي، وغيّر باسورد حساب Google وحسابات البنوك من جهاز تاني.", "Uninstall it now and change your Google and banking passwords from another device."),
                    evidence = listOf("${L.t("التطبيق", "App")}: ${name(pkg)}", "SHA-256: $h"), fixes = listOf(Fix.UNINSTALL, Fix.APP_SETTINGS),
                ))
            }
            for (a in user) {
                if (a.pkg in skip || a.pkg in stalkerPkgs) continue
                val pretendsSystem = systemLike.matches(a.label.trim()) || a.pkg.startsWith("com.android.") || a.pkg.startsWith("com.google.android.")
                if (pretendsSystem && isSide(a)) findings.add(Finding(
                    key = "fake-system:${a.pkg}", sev = Sev.HIGH, ioc = true, pkg = a.pkg,
                    title = L.t("تطبيق متنكّر في شكل تطبيق نظام: ${a.label}", "App disguised as a system app: ${a.label}"),
                    why = L.t("اسمه أو اسم حزمته شبه تطبيقات أندرويد أو Google، بس اتسطّب من برّه المتجر. برامج التجسس بتعمل كده علشان محدش يشك فيها.",
                        "Its name or package looks like an Android or Google app, but it was installed from outside a store. Stalkerware does this to avoid suspicion."),
                    todo = L.t("افتح إعداداته وشوف صلاحياته. لو مش إنت اللي مسطّبه، امسحه.", "Open its settings and review its permissions. If you did not install it, uninstall it."),
                    evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)}", "${L.t("المصدر", "Installer")}: ${inst(a)}", "${L.t("اتسطّب", "Installed")}: ${date(a.pkg)}") +
                        (if (a.pkg in deep.bootReceivers) listOf(L.t("بيشتغل لوحده لما الموبايل يفتح", "Starts itself when the phone boots")) else emptyList()),
                    fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
                ))
            }
            val hooks = d.apps.filter { it.pkg in hookApps }
            if (hooks.isNotEmpty()) findings.add(Finding(
                key = "hooking", sev = Sev.HIGH, ioc = true,
                title = L.t("أداة تعديل تطبيقات (Xposed / LSPosed) متسطّبة", "App-hooking framework installed (Xposed / LSPosed)"),
                why = L.t("الأدوات دي بتسمح بتغيير سلوك أي تطبيق، ومنها تطبيقات البنوك، وبتستخدمها برامج تجسس متقدمة.", "These frameworks can change how any app behaves, including banking apps, and advanced spyware uses them."),
                todo = L.t("لو مش إنت اللي مسطّبها، اعمل Factory reset.", "If you did not install it, factory reset the phone."),
                evidence = hooks.map { name(it.pkg) },
            ))
            if (deep.dnsBad.isNotEmpty()) findings.add(Finding(
                key = "dns-hijack", sev = Sev.HIGH, ioc = true,
                title = L.t("الشبكة اللي إنت عليها بتغيّر عناوين المواقع (DNS)", "The network you are on changes site addresses (DNS)"),
                why = L.t("سألنا عن مواقع عناوينها ثابتة ومعروفة، والرد جه بعناوين تانية. ده معناه إن الراوتر أو الشبكة ممكن تكون مخترقة وبتوديك لمواقع مزيفة.",
                    "We looked up sites whose addresses are fixed and well known, and got different answers. The router or network may be compromised and sending you to fake sites."),
                todo = L.t("اقطع الواي فاي ده وكمّل على باقة الموبايل، ومتدخلش حسابات البنك من الشبكة دي. ولو دي شبكة بيتك، افحصها من Horus Sec على الكمبيوتر وغيّر باسورد الراوتر.",
                    "Leave this Wi-Fi and use mobile data, and do not open banking apps on this network. If it is your home network, check it with Horus Sec on a computer and change the router password."),
                evidence = deep.dnsBad, fixes = listOf(Fix.NETWORK),
            ))
            deep.deviceOwner?.let { o ->
                val a = byPkg[o]
                if (a != null && !a.system && a.installer !in mdmInstallers) findings.add(Finding(
                    key = "device-owner:$o", sev = Sev.HIGH, ioc = true, pkg = o,
                    title = L.t("تطبيق متحكم في الموبايل بالكامل (Device owner): ${a.label}", "An app fully controls the phone (device owner): ${a.label}"),
                    why = L.t("Device owner بيدّي التطبيق تحكم كامل: يقرا ويقفل ويمسح ويمنع حذف نفسه. ده طبيعي في موبايلات الشركات بس.",
                        "A device owner has full control: it can monitor, lock, wipe and stop its own removal. That is only normal on company phones."),
                    todo = L.t("لو ده مش موبايل شغل، اعمل Factory reset، لأنه مش هيتشال بالطريقة العادية.", "If this is not a work phone, factory reset it, because it cannot be removed normally."),
                    evidence = listOf("${L.t("التطبيق", "App")}: ${name(o)}", "${L.t("المصدر", "Installer")}: ${inst(a)}"), fixes = listOf(Fix.SECURITY),
                ))
            }
            for (a in user) {
                val tx = deep.uploads[a.pkg] ?: continue
                if (tx < 50L * 1024 * 1024 || a.pkg in stalkerPkgs) continue
                val hiddenApp = !a.launcher
                if (!hiddenApp && !isSide(a)) continue
                findings.add(Finding(
                    key = "uploads:${a.pkg}", sev = if (hiddenApp) Sev.HIGH else Sev.MED, ioc = hiddenApp, pkg = a.pkg,
                    title = L.t("${a.label} رفع ${tx / (1024 * 1024)} ميجا في آخر 7 أيام", "${a.label} uploaded ${tx / (1024 * 1024)} MB in the last 7 days"),
                    why = if (hiddenApp) L.t("تطبيق مخفي بيبعت بيانات كتير لبرّه. ده الشكل المعتاد لبرامج التجسس اللي بتبعت الصور والتسجيلات.", "A hidden app sending a lot of data out. That is how stalkerware uploading photos and recordings usually looks.")
                    else L.t("تطبيق من برّه المتجر بيبعت بيانات كتير. ممكن يكون طبيعي (نسخ احتياطي مثلًا)، وممكن لأ.", "A sideloaded app sending a lot of data. It may be normal (a backup app, say), or not."),
                    todo = L.t("لو مش عارف التطبيق ده بيرفع إيه، امسحه.", "If you do not know what this app uploads, uninstall it."),
                    evidence = listOf("${L.t("التطبيق", "App")}: ${name(a.pkg)}", "Upload: ${tx / (1024 * 1024)} MB / 7d"), fixes = listOf(Fix.APP_SETTINGS, Fix.UNINSTALL),
                ))
            }
            val debugSigned = user.filter { isSide(it) && it.certSubjects.any { s -> s.contains("CN=Android Debug", true) } && it.pkg !in skip }
            if (debugSigned.isNotEmpty()) findings.add(Finding(
                key = "debug-signed", sev = Sev.LOW,
                title = L.t("تطبيقات متوقّعة بتوقيع تجريبي (${debugSigned.size})", "Apps signed with a test key (${debugSigned.size})"),
                why = L.t("التطبيقات الحقيقية بتتوقّع بمفتاح الشركة. التوقيع التجريبي معناه إن حد عمل التطبيق ده بنفسه أو عدّل عليه.", "Real apps are signed with their maker's key. A test signature means someone built or modified this app themselves."),
                todo = L.t("سيبه بس لو إنت عارف مين عامله.", "Keep it only if you know who made it."), evidence = debugSigned.map { name(it.pkg) },
            ))
            if (!deep.usageAccess) verified.add(L.t("فحص استهلاك البيانات متعملش: محتاج إذن Usage access", "Data-usage check skipped: needs Usage access"))
            if (deep.apkHashes.isNotEmpty()) verified.add(
                if (intel.malwareHashes.isEmpty()) L.t("اتحسبت بصمة ${deep.apkHashes.size} تطبيق، بس قايمة البصمات الخبيثة لسه فاضية في قاعدة التهديدات", "${deep.apkHashes.size} app fingerprints taken, but the malware-hash list is still empty in the threat intel")
                else L.t("بصمات ${deep.apkHashes.size} تطبيق من برّه المتجر مش في قايمة البرمجيات الخبيثة (${intel.malwareHashes.size})", "${deep.apkHashes.size} sideloaded app fingerprints are not in the malware list (${intel.malwareHashes.size})"))
            if (deep.dnsChecked && deep.dnsBad.isEmpty()) verified.add(L.t("الـ DNS على الشبكة دي بيرد صح", "DNS on this network answers honestly"))
        }

        // 9. hardening checklist
        val P = CheckState.PASS; val W = CheckState.WARN; val F = CheckState.FAIL
        checks.add(Check(L.t("قفل الشاشة", "Screen lock"), if (d.secure) P else F, if (d.secure) L.t("موجود", "Set") else L.t("مفيش", "None"), L.t("PIN أقوى من Pattern", "A PIN is stronger than a pattern"), Fix.LOCK))
        val encOk = d.encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || d.encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER || d.sdk >= 29
        checks.add(Check(L.t("تشفير الموبايل", "Encryption"), if (encOk) P else F, if (encOk) L.t("مشفّر", "Encrypted") else L.t("مش مشفّر", "Not encrypted")))
        checks.add(Check(L.t("إصدار Android", "Android version"), if (d.sdk >= 33) P else if (d.sdk >= 31) W else F, "Android ${d.release} (SDK ${d.sdk})",
            if (d.sdk < 33) L.t("الإصدار ده غالبًا مبقاش بياخد تحديثات أمنية", "This version probably no longer gets security updates") else ""))
        checks.add(Check(L.t("التحديث الأمني", "Security update"), when { patchAge == null -> W; patchAge <= 60 -> P; patchAge <= 120 -> W; else -> F }, d.patch.ifEmpty { "?" }, "", Fix.UPDATE))
        checks.add(Check("USB debugging", if (d.adb) W else P, if (d.adb) L.t("مفتوح", "On") else L.t("مقفول", "Off"), if (d.adb) L.t("اقفله لو مش بتستخدمه", "Turn it off if you are not using it") else "", if (d.adb) Fix.DEVELOPER else null))
        if (d.adbWifi) checks.add(Check("Wireless debugging", F, L.t("مفتوح", "On"), "", Fix.DEVELOPER))
        checks.add(Check(L.t("خيارات المطورين", "Developer options"), if (d.devOptions) W else P, if (d.devOptions) L.t("مفتوحة", "On") else L.t("مقفولة", "Off"), "", if (d.devOptions) Fix.DEVELOPER else null))
        checks.add(Check("Private DNS", when (d.privateDnsActive) { true -> P; else -> W },
            when { d.privateDnsActive == true && !d.privateDnsHost.isNullOrEmpty() -> d.privateDnsHost!!; d.privateDnsActive == true -> L.t("تلقائي", "Automatic"); d.privateDnsActive == null -> L.t("مش معروف", "Unknown"); else -> L.t("مقفول", "Off") },
            L.t("Private DNS بفلتر (زي NextDNS) بيحجب مواقع النصب في أي شبكة", "A filtering Private DNS (such as NextDNS) blocks scam sites on any network"), Fix.NETWORK))
        checks.add(Check("Root", if (rooted) F else P, if (rooted) L.t("موجود", "Present") else L.t("مش موجود", "Not found")))
        checks.add(Check(L.t("شهادات متضافة بإيد", "User certificates"), if (d.userCerts.isEmpty()) P else W, d.userCerts.size.toString(), "", if (d.userCerts.isEmpty()) null else Fix.SECURITY))
        checks.add(Check("VPN", P, if (d.vpn) L.t("شغال دلوقتي", "Active now") else L.t("مش شغال", "Not active"), if (d.vpn) L.t("اتأكد إنه VPN إنت اللي مسطّبه", "Make sure it is a VPN you installed") else "", Fix.VPN))

        if (accUser.isEmpty() && hidden.isEmpty()) verified.add(L.t("مفيش تطبيقات من برّه ليها صلاحية Accessibility", "No third-party app has Accessibility access"))
        if (admUser.isEmpty()) verified.add(L.t("مفيش تطبيقات Device admin غير بتاعة النظام", "No third-party device admin apps"))
        verified.add(L.t("اتفحص ${d.apps.size} تطبيق، منهم ${user.size} متسطّب من المستخدم", "${d.apps.size} apps checked, ${user.size} installed by the user"))

        // 10. what changed since last scan
        val changes = ArrayList<String>()
        d.prev?.let { p ->
            user.filter { it.pkg !in p.apps }.take(15).forEach { changes.add(L.t("تطبيق جديد: ", "New app: ") + name(it.pkg)) }
            d.accessibility.filter { it !in p.acc && byPkg[it]?.system != true }.forEach { changes.add(L.t("صلاحية Accessibility جديدة: ", "New Accessibility access: ") + name(it)) }
            d.admins.filter { it !in p.admins && byPkg[it]?.system != true }.forEach { changes.add(L.t("Device admin جديد: ", "New device admin: ") + name(it)) }
            val gone = p.apps.count { it !in byPkg }
            if (gone > 0) changes.add(L.t("$gone تطبيق اتمسح", "$gone apps removed"))
        }

        // 11. exposure: who can hear and see you
        fun exp(label: String, perms: Set<String>) = Exposure(label, user.filter { a -> a.granted.any { it in perms } }.map { it.label }.sorted())
        val exposure = listOf(
            exp(L.t("الميكروفون", "Microphone"), setOf(Manifest.permission.RECORD_AUDIO)),
            exp(L.t("الكاميرا", "Camera"), setOf(Manifest.permission.CAMERA)),
            exp(L.t("الموقع بالظبط", "Precise location"), setOf(Manifest.permission.ACCESS_FINE_LOCATION)),
            exp(L.t("الموقع في الخلفية", "Location in background"), setOf("android.permission.ACCESS_BACKGROUND_LOCATION")),
            exp(L.t("الرسايل SMS", "SMS messages"), setOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)),
            exp(L.t("سجل المكالمات", "Call log"), setOf(Manifest.permission.READ_CALL_LOG)),
            exp(L.t("جهات الاتصال", "Contacts"), setOf(Manifest.permission.READ_CONTACTS)),
        )

        // score
        val sorted = findings.sortedBy { it.sev.rank }
        var lows = 0; var penalty = 0
        for (f in sorted) { if (f.sev == Sev.LOW) lows += 1 else penalty += f.sev.weight }
        penalty += minOf(lows, 6)
        penalty += minOf(checks.map { when (it.state) { CheckState.FAIL -> 3; CheckState.WARN -> 1; else -> 0 } }.sum(), 8)
        var score = (100 - penalty).coerceIn(0, 100)
        val compromised = sorted.any { it.sev == Sev.CRIT && it.ioc }
        if (compromised) score = minOf(score, 20)
        val verdict = when { compromised -> "compromised"; sorted.any { it.ioc } -> "suspicious"; else -> "clean" }
        val grade = when { score >= 85 -> L.t("ممتاز", "Excellent"); score >= 70 -> L.t("كويس", "Good"); score >= 50 -> L.t("محتاج شغل", "Needs work"); else -> L.t("ضعيف", "Weak") }
        val headline = when (verdict) {
            "compromised" -> L.t("فيه علامات قوية على تجسس أو تطبيق مزيف. اقرا التعليمات قبل ما تمسح أي حاجة.", "Strong signs of stalkerware or a fake app. Read the advice before removing anything.")
            "suspicious" -> L.t("فيه حاجات محتاجة تتأكد منها قبل ما نحكم.", "Some things need checking before a verdict.")
            else -> L.t("مفيش علامات تجسس أو تطبيقات مزيفة. تحت الحاجات اللي تقوّي بيها الموبايل.", "No signs of stalkerware or fake apps. Below are ways to harden the phone.")
        }
        if (changes.isEmpty() && d.prev == null) changes.add(L.t("ده أول فحص، والفحص الجاي هيقولك إيه اللي اتغيّر.", "This is the first scan. The next one will show what changed."))
        else {
            val prevScore = d.prev?.score
            if (prevScore != null && prevScore != score) changes.add(0, L.t("رقم الأمان كان $prevScore", "Security score was $prevScore"))
        }

        return ScanResult(
            atMillis = d.atMillis, score = score, grade = grade, verdict = verdict, headline = headline,
            findings = sorted, checks = checks, changes = changes, verified = verified, exposure = exposure,
            appCount = d.apps.size, userAppCount = user.size, intelDate = intel.date,
            deep = d.deep != null, iocCount = sorted.count { it.ioc },
        )
    }
}
