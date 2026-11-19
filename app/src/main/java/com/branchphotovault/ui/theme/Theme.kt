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
    secondary = MossGreen,
    tertiary = Amber,
    background = Cream,
    surface = CardWhite,
    surfaceVariant = Mist,
    outline = MintOutline,
    onSurfaceVariant = MossGreen,
    onPrimary = Cream,
    onSecondary = Cream,
    onTertiary = Slate,
    onBackground = Slate,
    onSurface = Slate,
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA68AF6), onPrimary = Color(0xFF19172B),
    secondary = Color(0xFFC4B9E2), background = Color(0xFF10111D),
    surface = Color(0xFF25243A), surfaceVariant = Color(0xFF302D47),
    onSurface = Color(0xFFF5F3FF), onBackground = Color(0xFFF5F3FF),
    onSurfaceVariant = Color(0xFFA7A4BE), outline = Color(0xFF625A7C), error = Color(0xFFFF9BA9))

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
