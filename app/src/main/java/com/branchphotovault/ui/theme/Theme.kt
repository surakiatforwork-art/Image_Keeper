package com.branchphotovault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = VaultGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAF0FF),
    onPrimaryContainer = Color(0xFF094269),
    secondary = MossGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F2FA),
    onSecondaryContainer = Slate,
    tertiary = Amber,
    onTertiary = Slate,
    tertiaryContainer = Color(0xFFFFE6D9),
    onTertiaryContainer = Color(0xFF773E28),
    background = Cream,
    onBackground = Slate,
    surface = CardWhite,
    onSurface = Slate,
    surfaceVariant = Mist,
    onSurfaceVariant = MossGreen,
    outline = MintOutline,
    outlineVariant = Color(0xFFE2EDF5),
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D8FF),
    onPrimary = Color(0xFF063D5F),
    primaryContainer = Color(0xFF0B527A),
    onPrimaryContainer = Color(0xFFCBEFFF),
    secondary = Color(0xFFB1CEDF),
    onSecondary = Color(0xFF152B40),
    tertiary = Color(0xFFFFAC8E),
    onTertiary = Color(0xFF4B251B),
    background = Color(0xFF0C1927),
    onBackground = Color(0xFFEAF6FF),
    surface = Color(0xFF182839),
    onSurface = Color(0xFFEAF6FF),
    surfaceVariant = Color(0xFF23394C),
    onSurfaceVariant = Color(0xFFB4CBDD),
    outline = Color(0xFF3A5266),
    outlineVariant = Color(0xFF30485A),
    error = Color(0xFFFFA3AC)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun BranchPhotoVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
