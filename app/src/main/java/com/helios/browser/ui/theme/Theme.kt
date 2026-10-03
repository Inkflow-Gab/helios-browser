package com.helios.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.helios.browser.ui.adaptive.findActivity

private val HeliosColorScheme = darkColorScheme(
    primary = HeliosSun,
    onPrimary = HeliosOledBackground,
    primaryContainer = HeliosSun.copy(alpha = 0.18f),
    secondary = HeliosBlue,
    onSecondary = HeliosTextPrimary,
    tertiary = HeliosPurple,
    onTertiary = HeliosTextPrimary,
    background = HeliosOledBackground,
    onBackground = HeliosTextPrimary,
    surface = HeliosDarkSurface,
    onSurface = HeliosTextPrimary,
    surfaceVariant = HeliosDarkCard,
    onSurfaceVariant = HeliosTextSecondary,
    outline = HeliosTextTertiary,
    error = HeliosShieldRed
)

/**
 * Dark/OLED-only theme. Helios has never shipped a light mode and the layouts are tuned for black
 * backgrounds, so `darkColorScheme` is used unconditionally rather than gated on system setting.
 *
 * Bar colours are deliberately left alone: `MainActivity` calls `enableEdgeToEdge()`, which makes
 * them transparent. Painting them back to an opaque black here would undo that and put a solid
 * band across the gesture area. Only the icon appearance is forced.
 */
@Composable
fun HeliosTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context.findActivity()
        if (activity != null) {
            SideEffect {
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = HeliosColorScheme,
        typography = Typography,
        content = content
    )
}