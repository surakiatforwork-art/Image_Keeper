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

