package zuiwan.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import zuiwan.model.ThemeChoice

@Composable
fun ZuiwanTheme(
    choice: ThemeChoice,
    dynamicColors: Boolean,
    artworkUrl: String?,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (choice) {
        ThemeChoice.SYSTEM -> systemDark
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK, ThemeChoice.BLACK -> true
    }
    val pureBlack = choice == ThemeChoice.BLACK
    val (artPrimary, artSecondary) = rememberartcolors(artworkUrl, dynamicColors)

    val glass = if (useDark) {
        darkglass(artPrimary, artSecondary, pureBlack = pureBlack)
    } else {
        lightglass(artPrimary, artSecondary)
    }

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = glass.accent,
            onPrimary = glass.onAccent,
            secondary = glass.accentSecondary,
            background = glass.bg,
            onBackground = glass.ink,
            surface = glass.bgSecondary,
            onSurface = glass.ink,
            surfaceVariant = glass.glassFill,
            onSurfaceVariant = glass.inkSecondary,
            error = glass.danger,
        )
    } else {
        lightColorScheme(
            primary = glass.accent,
            onPrimary = glass.onAccent,
            secondary = glass.accentSecondary,
            background = glass.bg,
            onBackground = glass.ink,
            surface = glass.bgSecondary,
            onSurface = glass.ink,
            surfaceVariant = glass.glassFill,
            onSurfaceVariant = glass.inkSecondary,
            error = glass.danger,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !useDark
            insets.isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZuiwanTypography,
            content = content,
        )
    }
}
