package com.branchphotovault

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
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
            BranchPhotoVaultTheme {
                CompositionLocalProvider(LocalAppContainer provides app.container) {
                    BranchPhotoVaultApp()
                }
            }
        }
    }
}

