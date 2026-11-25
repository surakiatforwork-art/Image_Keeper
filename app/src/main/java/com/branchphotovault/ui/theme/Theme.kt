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
    secondary = MossGreen,
    onSecondary = Color.White,
    tertiary = Amber,
    onTertiary = Slate,
    background = Cream,
    onBackground = Slate,
    surface = CardWhite,
    onSurface = Slate,
    surfaceVariant = Mist,
    onSurfaceVariant = MossGreen,
    outline = MintOutline,
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5B8),
    onPrimary = Color(0xFF10251E),
    secondary = Color(0xFFA2B7AB),
    onSecondary = Color(0xFF18271F),
    tertiary = Color(0xFFE0B77D),
    background = Color(0xFF111716),
    onBackground = Color(0xFFEAF0EC),
    surface = Color(0xFF1B2321),
    onSurface = Color(0xFFEAF0EC),
    surfaceVariant = Color(0xFF27312D),
    onSurfaceVariant = Color(0xFFAFBDB5),
    outline = Color(0xFF394840),
    error = Color(0xFFFFA3A8)
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
