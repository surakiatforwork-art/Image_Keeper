package com.branchphotovault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = VaultGreen,
    secondary = MossGreen,
    tertiary = Amber,
    background = Cream,
    surface = Cream,
    onPrimary = Cream,
    onSecondary = Cream,
    onTertiary = Slate,
    onBackground = Slate,
    onSurface = Slate,
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    secondary = MossGreen,
    tertiary = Cream,
    background = Slate,
    surface = Slate,
    onPrimary = Slate,
    onSecondary = Cream,
    onTertiary = Slate,
    onBackground = Cream,
    onSurface = Cream,
    error = Danger
)

@Composable
fun BranchPhotoVaultTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

