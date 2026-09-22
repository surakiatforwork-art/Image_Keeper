package com.branchphotovault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = VaultGreen,
    secondary = MossGreen,
    tertiary = Amber,
    background = Cream,
    surface = CardWhite,
    surfaceVariant = Mist,
    outline = MintOutline,
    onPrimary = Cream,
    onSecondary = Cream,
    onTertiary = Slate,
    onBackground = Slate,
    onSurface = Slate,
    error = Danger
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun BranchPhotoVaultTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
