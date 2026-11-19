/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.horusict.shield.ui.AboutScreen
import com.horusict.shield.ui.C
import com.horusict.shield.ui.HomeScreen
import com.horusict.shield.ui.LinkScreen
import com.horusict.shield.ui.ResultScreen
import com.horusict.shield.ui.ScanningScreen

private val horusType = androidx.compose.material3.Typography().let { t ->
    fun f(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = com.horusict.shield.ui.Cairo, lineHeight = androidx.compose.ui.unit.TextUnit(1.55f, androidx.compose.ui.unit.TextUnitType.Em))
    t.copy(
        displayLarge = f(t.displayLarge), displayMedium = f(t.displayMedium), displaySmall = f(t.displaySmall),
        headlineLarge = f(t.headlineLarge), headlineMedium = f(t.headlineMedium), headlineSmall = f(t.headlineSmall),
        titleLarge = f(t.titleLarge), titleMedium = f(t.titleMedium), titleSmall = f(t.titleSmall),
        bodyLarge = f(t.bodyLarge), bodyMedium = f(t.bodyMedium), bodySmall = f(t.bodySmall),
        labelLarge = f(t.labelLarge), labelMedium = f(t.labelMedium), labelSmall = f(t.labelSmall),
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        App.init(this)
        handle(intent)
        setContent {
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            val light = when (App.theme) { "light" -> true; "dark" -> false; else -> !sysDark }
            if (C.light != light) C.light = light
            androidx.compose.runtime.LaunchedEffect(light) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (light) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t),
                    navigationBarStyle = if (light) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t),
                )
            }
            MaterialTheme(colorScheme = (if (light) androidx.compose.material3.lightColorScheme(primary = C.Gold, onPrimary = C.OnGold, background = C.Bg, surface = C.Surface, onSurface = C.Text, onBackground = C.Text) else darkColorScheme(primary = C.Gold, onPrimary = C.OnGold, background = C.Bg, surface = C.Surface, onSurface = C.Text, onBackground = C.Text)), typography = horusType) {
                CompositionLocalProvider(LocalLayoutDirection provides if (L.ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = C.Bg, contentColor = C.Text) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            BackHandler(enabled = App.screen != Screen.HOME && App.screen != Screen.SCANNING) { App.screen = when (App.screen) { Screen.LEGAL -> Screen.ABOUT; Screen.ABOUT -> Screen.SETTINGS; Screen.LINK -> Screen.TOOLS; Screen.ASK -> com.horusict.shield.ui.Ask.back; else -> Screen.HOME } }
                            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(1400); App.splash = false }
                            if (App.splash) com.horusict.shield.ui.SplashScreen() else when (App.screen) {
                                Screen.HOME -> HomeScreen()
                                Screen.SCANNING -> ScanningScreen()
                                Screen.RESULT -> ResultScreen()
                                Screen.LINK -> LinkScreen()
                                Screen.SETTINGS -> com.horusict.shield.ui.SettingsScreen()
                                Screen.TOOLS -> com.horusict.shield.ui.ToolsScreen()
                                Screen.REPORTS -> com.horusict.shield.ui.ReportsScreen()
                                Screen.ABOUT -> AboutScreen()
                                Screen.LEGAL -> com.horusict.shield.ui.LegalScreen()
                                Screen.ASK -> com.horusict.shield.ui.AskScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (App.needInstallPermission && Updater.canInstall(this)) App.needInstallPermission = false
        if (App.screen == Screen.HOME) App.refreshFromPrefs()
        App.maybeCheckUpdate()
        App.usageAccess = DeepScan.hasUsageAccess(this)
        App.notifyAllowed = AutoScan.notificationsAllowed(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i != null && BuildConfig.DEBUG) {
            // test hooks used by the automated smoke test (debug builds only)
            i.getStringExtra("horus_lang")?.let { App.setLang(it) }
            i.getStringExtra("horus_theme")?.let { App.chooseTheme(it) }
            when (i.getStringExtra("horus_screen")) { "about" -> App.screen = Screen.ABOUT; "home" -> App.screen = Screen.HOME; "legal" -> App.screen = Screen.LEGAL; "settings" -> App.screen = Screen.SETTINGS; "link" -> App.screen = Screen.LINK; "tools" -> App.screen = Screen.TOOLS; "reports" -> App.screen = Screen.REPORTS; "ask" -> com.horusict.shield.ui.Ask.open(Screen.TOOLS) }
            if (i.getBooleanExtra("horus_autoscan", false)) App.startScan(deep = i.getBooleanExtra("horus_deep", true))
            if (i.getBooleanExtra("horus_rootmode", false)) { App.rootDetected = true; App.enableRoot() }
            if (i.hasExtra("horus_auto")) App.setAuto(i.getIntExtra("horus_auto", 0))
            if (i.getBooleanExtra("horus_runworker", false)) androidx.work.WorkManager.getInstance(this).enqueue(androidx.work.OneTimeWorkRequestBuilder<ScanWorker>().build())
            if (i.getBooleanExtra("horus_testnotify", false)) AutoScan.notify(this, AutoScan.neutralTitle(), AutoScan.neutralBody())
            i.getStringExtra("horus_ask")?.let { com.horusict.shield.ui.Ask.open(Screen.TOOLS); com.horusict.shield.ui.Ask.ask(it) }
            i.getStringExtra("horus_asktopic")?.let { com.horusict.shield.ui.Ask.open(Screen.RESULT, it) }
            if (i.getBooleanExtra("horus_selftest", false)) Thread { android.util.Log.i("HorusSelfTest", "signer=" + Updater.signerSelfTest(this) + " local192=" + UrlCheck.isLocalTarget("192.168.1.1") + " localhost=" + UrlCheck.isLocalTarget("localhost") + " public=" + UrlCheck.isLocalTarget("google.com")) }.start()
            if (i.getBooleanExtra("horus_testupdnotify", false)) UpdateCheck.post(this, UpdateInfo(BuildConfig.VERSION_CODE + 1, "1.0.${BuildConfig.VERSION_CODE + 1}", "", "", ""))
            if (i.getBooleanExtra("horus_fakeupdate", false)) App.update = UpdateInfo(BuildConfig.VERSION_CODE + 1, "1.0.${BuildConfig.VERSION_CODE + 1}", "https://github.com/medhatsaif010/horus-mobile/releases/download/apk/HorusSec-Mobile.apk", "0".repeat(64), "تجربة: شاشة التحديث")
        }
        if (i != null && i.getBooleanExtra("horus_open_about", false)) {
            i.removeExtra("horus_open_about")
            App.screen = Screen.ABOUT
            App.checkUpdate(quiet = true)
            return
        }
        val openTok = i?.getStringExtra("horus_open_result")
        if (i != null && openTok != null) {
            i.removeExtra("horus_open_result")
            val ok = openTok == Prefs.str("openToken")
            Prefs.put("openToken", null)
            if (!ok) return
            App.refreshFromPrefs()
            App.startScan(deep = true)
            return
        }
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        // shared text only fills in the field: the check starts when the user taps Check link,
        // so another app cannot make Horus Shield send requests on its behalf
        val t = text.take(4000)
        App.linkResult = null
        App.linkText = (UrlCheck.extract(t) ?: t).take(2048)
        App.screen = Screen.LINK
        i.action = Intent.ACTION_MAIN
    }
}
