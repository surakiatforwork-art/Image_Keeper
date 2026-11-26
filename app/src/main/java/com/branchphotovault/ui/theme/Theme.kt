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
    primaryContainer = Color(0xFFEAE3FF),
    onPrimaryContainer = Color(0xFF38237B),
    secondary = MossGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8F8),
    onSecondaryContainer = Slate,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF2FF),
    onTertiaryContainer = Color(0xFF12446C),
    background = Cream,
    onBackground = Slate,
    surface = CardWhite,
    onSurface = Slate,
    surfaceVariant = Mist,
    onSurfaceVariant = MossGreen,
    outline = MintOutline,
    outlineVariant = Color(0xFFE9E4F5),
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB49CFF),
    onPrimary = Color(0xFF25124F),
    primaryContainer = Color(0xFF30205F),
    onPrimaryContainer = Color(0xFFF4ECFF),
    secondary = Color(0xFFC6B9E4),
    onSecondary = Color(0xFF251D3C),
    secondaryContainer = Color(0xFF38304B),
    onSecondaryContainer = Color(0xFFF4ECFF),
    tertiary = Color(0xFF83D3FF),
    onTertiary = Color(0xFF0A3050),
    tertiaryContainer = Color(0xFF153A5B),
    onTertiaryContainer = Color(0xFFDAF2FF),
    background = Color(0xFF131329),
    onBackground = Color(0xFFF1EDFF),
    surface = Color(0xFF20203A),
    onSurface = Color(0xFFF1EDFF),
    surfaceVariant = Color(0xFF2A2948),
    onSurfaceVariant = Color(0xFFCBC4E2),
    outline = Color(0xFF514B75),
    outlineVariant = Color(0xFF393456),
    error = Color(0xFFFFA8BD)
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
                // The toolbar is purple in light mode and deep indigo in dark mode:
                // both need white status-bar glyphs; navigation follows theme contrast.
                isAppearanceLightStatusBars = false
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
