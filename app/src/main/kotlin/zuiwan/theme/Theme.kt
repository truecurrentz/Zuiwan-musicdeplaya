package zuiwan.theme

import android.app.Activity
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
fun apptheme(
    choice: ThemeChoice,
    dynamicColors: Boolean,
    blurEnabled: Boolean = true,
    artworkUrl: String?,
    content: @Composable () -> Unit,
) {
    val useDark = choice == ThemeChoice.DARK
    val (artPrimary, artSecondary) = artcolors(artworkUrl, dynamicColors, useDark)

    val glass = if (useDark) {
        darkglass(artPrimary, artSecondary, blurEnabled = blurEnabled)
    } else {
        whiteglass(artPrimary, artSecondary, blurEnabled = blurEnabled)
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
