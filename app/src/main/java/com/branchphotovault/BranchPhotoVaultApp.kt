package com.branchphotovault

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.branchphotovault.navigation.BranchPhotoVaultNavHost

@Composable
fun BranchPhotoVaultApp() {
    Surface(modifier = Modifier) {
        BranchPhotoVaultNavHost()
    }
}

