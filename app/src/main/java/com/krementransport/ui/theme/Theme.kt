package com.krementransport.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.krementransport.data.prefs.AppearancePreference

/**
 * Resolves the stored preference against the system setting. The result — not
 * `isSystemInDarkTheme()` — is what the map's colour scheme must follow, or an in-app override
 * would repaint the chrome and leave the map behind.
 */
@Composable
fun AppearancePreference.isDark(): Boolean = when (this) {
    AppearancePreference.System -> isSystemInDarkTheme()
    AppearancePreference.Light -> false
    AppearancePreference.Dark -> true
}

@Composable
fun KremenTransportTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The map is edge to edge under transparent bars, so the icons have to follow the
            // app's resolved theme rather than the system's.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
