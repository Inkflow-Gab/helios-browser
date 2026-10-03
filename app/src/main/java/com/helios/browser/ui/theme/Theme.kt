package com.helios.browser.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HeliosColorScheme = darkColorScheme(
    primary = HeliosBlue,
    onPrimary = HeliosTextPrimary,
    primaryContainer = HeliosBlueVariant,
    secondary = HeliosPurple,
    onSecondary = HeliosTextPrimary,
    background = HeliosOledBackground,
    onBackground = HeliosTextPrimary,
    surface = HeliosDarkSurface,
    onSurface = HeliosTextPrimary,
    surfaceVariant = HeliosDarkCard,
    onSurfaceVariant = HeliosTextSecondary
)

@Composable
fun HeliosTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = HeliosOledBackground.toArgb()
            window.navigationBarColor = HeliosOledBackground.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = HeliosColorScheme,
        typography = Typography,
        content = content
    )
}
