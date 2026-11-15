package com.horussec.mobile

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
import com.horussec.mobile.ui.AboutScreen
import com.horussec.mobile.ui.C
import com.horussec.mobile.ui.HomeScreen
import com.horussec.mobile.ui.LinkScreen
import com.horussec.mobile.ui.ResultScreen
import com.horussec.mobile.ui.ScanningScreen

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
            MaterialTheme(colorScheme = darkColorScheme(primary = C.Gold, onPrimary = C.Navy, background = C.Navy, surface = C.Card, onSurface = Color.White, onBackground = Color.White)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (L.ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = C.Navy, contentColor = Color.White) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            BackHandler(enabled = App.screen != Screen.HOME && App.screen != Screen.SCANNING) { App.screen = Screen.HOME }
                            when (App.screen) {
                                Screen.HOME -> HomeScreen()
                                Screen.SCANNING -> ScanningScreen()
                                Screen.RESULT -> ResultScreen()
                                Screen.LINK -> LinkScreen()
                                Screen.ABOUT -> AboutScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i != null && BuildConfig.DEBUG) {
            // test hooks used by the automated smoke test (debug builds only)
            i.getStringExtra("horus_lang")?.let { App.setLang(it == "ar") }
            when (i.getStringExtra("horus_screen")) { "about" -> App.screen = Screen.ABOUT; "home" -> App.screen = Screen.HOME }
            if (i.getBooleanExtra("horus_autoscan", false)) App.startScan()
        }
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        App.linkResult = null
        App.screen = Screen.LINK
        App.checkLink(UrlCheck.extract(text) ?: text)
        i.action = Intent.ACTION_MAIN
    }
}
