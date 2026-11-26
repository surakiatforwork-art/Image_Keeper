package com.branchphotovault

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.branchphotovault.data.model.AppThemeMode
import com.branchphotovault.ui.theme.BranchPhotoVaultTheme

class MainActivity : ComponentActivity() {
    private val volumeShutterKeys = VolumeShutterKeyDispatcher()
    private var volumeShutterCallback: (() -> Unit)? = null

    fun registerVolumeShutter(callback: () -> Unit) {
        volumeShutterCallback = callback
    }

    fun unregisterVolumeShutter(callback: () -> Unit) {
        if (volumeShutterCallback === callback) volumeShutterCallback = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volumeShutterKeys.handle(
                keyCode = event.keyCode,
                action = event.action,
                repeatCount = event.repeatCount,
                capture = volumeShutterCallback
            )
        ) return true
        return super.dispatchKeyEvent(event)
    }


    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
    }

    // This is a window-level preference, not a system setting. Match the current
    // resolution and request up to 120 Hz; Android can still honor battery,
    // thermal and user display restrictions. Older 60 Hz phones keep their mode.
    @Suppress("DEPRECATION")
    private fun requestHighRefreshRate() {
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) this.display
            else windowManager.defaultDisplay
        val current = display?.mode ?: return
        val candidate = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight && it.refreshRate <= 120.5f }
            .maxByOrNull { it.refreshRate } ?: return
        if (candidate.refreshRate <= 60.5f) return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != candidate.modeId) {
            attributes.preferredDisplayModeId = candidate.modeId
            window.attributes = attributes
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BranchPhotoVaultApplication

        setContent {
            val themeMode by app.container.settingsRepository.appThemeModeFlow
                .collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val useDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            BranchPhotoVaultTheme(darkTheme = useDark) {
                CompositionLocalProvider(LocalAppContainer provides app.container) {
                    BranchPhotoVaultApp()
                }
            }
        }
    }
}

