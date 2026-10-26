package zuiwan.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import zuiwan.data.AppSettings
import zuiwan.model.ThemeChoice

@Composable
fun apptheme(
    choice: ThemeChoice,
    dynamicColors: Boolean,
    blurEnabled: Boolean = true,
    artworkUrl: String?,
    settings: AppSettings = AppSettings(
        theme = choice,
        dynamicColors = dynamicColors,
        blurEnabled = blurEnabled,
    ),
    pluginCss: String = "",
    forceFullscreen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = choice == ThemeChoice.DARK
    val (artPrimary, artSecondary) = artcolors(artworkUrl, dynamicColors, useDark)

    val mergedCss = remember(pluginCss, settings.customCss) {
        parsecss("$pluginCss\n${settings.customCss}")
    }
    val customAccent = remember(settings.customAccentHex, mergedCss.accent) {
        mergedCss.accent?.let { Color(it.toInt()) } ?: parsehex(settings.customAccentHex)
    }
    val customSecondary = remember(customAccent, mergedCss.accentSecondary) {
        mergedCss.accentSecondary?.let { Color(it.toInt()) } ?: customAccent
    }

    val effectiveAccent = customAccent ?: artPrimary
    val effectiveSecondary = customSecondary ?: artSecondary
    val effectiveBlur = mergedCss.blurDp ?: settings.blurRadius
    val effectiveOpacity = mergedCss.glassOpacity ?: settings.glassOpacity
    val bgOverride = mergedCss.bg?.let { Color(it.toInt()) }
    val inkOverride = mergedCss.ink?.let { Color(it.toInt()) }

    val glass = if (useDark) {
        darkglass(
            accent = effectiveAccent,
            secondary = effectiveSecondary,
            blurEnabled = blurEnabled,
            blurRadius = effectiveBlur,
            glassOpacity = effectiveOpacity,
            compact = settings.compactMode,
            bgOverride = bgOverride,
            inkOverride = inkOverride,
            keepRawAccent = customAccent != null,
        )
    } else {
        whiteglass(
            accent = effectiveAccent,
            secondary = effectiveSecondary,
            blurEnabled = blurEnabled,
            blurRadius = effectiveBlur,
            glassOpacity = effectiveOpacity,
            compact = settings.compactMode,
            bgOverride = bgOverride,
            inkOverride = inkOverride,
            keepRawAccent = customAccent != null,
        )
    }

    val typography = remember(
        settings.fontChoice,
        settings.customFontPath,
        mergedCss.fontScale,
        settings.compactMode,
    ) {
        val baseScale = mergedCss.fontScale ?: if (settings.compactMode) 0.93f else 1f
        buildtype(settings.fontChoice, settings.customFontPath, baseScale)
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

    val hideBars = settings.fullscreenMode || forceFullscreen
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !useDark
            insets.isAppearanceLightNavigationBars = !useDark
            insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideBars) {
                insets.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insets.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
