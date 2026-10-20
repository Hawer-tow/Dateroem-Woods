package com.dateroem.woodsales.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The app's own themes (Color.kt: Forest, Dark, Light) - never the phone's
// system theme or Material You's wallpaper colours: a fixed brand identity
// that each person can switch in Settings.
@Composable
fun DateroemWoodSalesTheme(content: @Composable () -> Unit) {
    val palette = DateroemThemeState.palette
    val colors = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = WoodOn,
            secondary = palette.accentHover,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.background,
            onSurfaceVariant = palette.textMuted,
            error = palette.danger
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = WoodOn,
            secondary = palette.accentHover,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            error = palette.danger
        )
    }

    // Dark status-bar and navigation-bar icons on Light, light ones otherwise.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
