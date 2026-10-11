package com.filetoolkit.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkRestrainedColorScheme = darkColorScheme(
    primary = ActionPrimaryBg,
    onPrimary = ActionPrimaryText,
    primaryContainer = SurfaceRaised,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = SurfaceCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = BorderMedium,
    outlineVariant = BorderSubtle,
    error = StateError,
    onError = PureBlack
)

@Composable
fun FileToolkitTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = PureBlack.toArgb()
            window.navigationBarColor = PureBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkRestrainedColorScheme,
        typography = Typography,
        content = content
    )
}
