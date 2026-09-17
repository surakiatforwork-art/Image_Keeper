package com.branchphotovault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.branchphotovault.ui.theme.BranchPhotoVaultTheme

class MainActivity : ComponentActivity() {

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

