package com.fluxie.browser

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluxie.browser.common.PreferencesRepository
import com.fluxie.browser.common.TrimPolicy
import com.fluxie.browser.lite.ModeSwitch
import com.fluxie.browser.ui.browser.BrowserViewModel
import com.fluxie.browser.ui.navigation.FluxieNavGraph
import com.fluxie.browser.ui.theme.FluxieTheme
import com.fluxie.browser.ui.theme.isDarkFor

class MainActivity : AppCompatActivity() {

    // Same instance FluxieNavGraph gets from viewModel(): both are scoped to this activity.
    private val browserViewModel: BrowserViewModel by viewModels()

    private var handedOverToLite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Lite mode: leave before Compose, Room, DataStore or the ViewModel are touched.
        if (PreferencesRepository(this).liteMode) {
            super.onCreate(savedInstanceState)
            handedOverToLite = true
            ModeSwitch.forwardToLite(this, intent?.data)
            return
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by browserViewModel.appSettings.collectAsStateWithLifecycle()
            FluxieTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                // System bar icons must follow the chosen theme, not only the device theme.
                val dark = isDarkFor(settings.themeMode)
                LaunchedEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                FluxieNavGraph()
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!handedOverToLite && TrimPolicy.shouldRelease(level)) browserViewModel.releaseBackgroundTabs()
    }
}
