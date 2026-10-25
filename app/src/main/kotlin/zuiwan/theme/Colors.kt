package zuiwan.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.HazeState

@Immutable
data class GlassTokens(
    val isDark: Boolean,
    val blurEnabled: Boolean,
    val bg: Color,
    val bgSecondary: Color,
    val glassFill: Color,
    val glassElevated: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val pillSolid: Color,
    val onPillSolid: Color,
    val danger: Color,
    val success: Color,
)

val LocalGlass = staticCompositionLocalOf {
    darkglass(Color(0xFF7CB8FF), Color(0xFFA78BFA), blurEnabled = true)
}

val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

fun darkglass(
    accent: Color,
    secondary: Color,
    blurEnabled: Boolean = true,
): GlassTokens {
    val tuned = tuneaccent(accent, dark = true)
    val onAcc = if (tuned.luminance() > 0.44f) Color(0xFF0A0D14) else Color(0xFFFAFAFC)
    return GlassTokens(
        isDark = true,
        blurEnabled = blurEnabled,
        bg = Color(0xFF0B0C10),
        bgSecondary = Color(0xFF13151D),
        glassFill = Color(0x12FFFFFF),
        glassElevated = Color(0x1CFFFFFF),
        glassStrong = Color(0xE0151720),
        glassBorder = Color(0x14FFFFFF),
        glassHighlight = Color(0x28FFFFFF),
        glassShadow = Color(0x70000000),
        ink = Color(0xFFFAFAFC),
        inkSecondary = Color(0xFFA2A8B6),
        inkMuted = Color(0xFF686E7C),
        accent = tuned,
        accentSecondary = tuneaccent(secondary, dark = true),
        accentSoft = tuned.copy(alpha = 0.16f),
        onAccent = onAcc,
        pillSolid = Color(0xFFFFFFFF),
        onPillSolid = Color(0xFF0B0C10),
        danger = Color(0xFFFF6B81),
        success = Color(0xFF34D399),
    )
}

fun whiteglass(
    accent: Color,
    secondary: Color,
    blurEnabled: Boolean = true,
): GlassTokens {
    val tuned = tuneaccent(accent, dark = false)
    val onAcc = if (tuned.luminance() > 0.50f) Color(0xFF0B0D13) else Color(0xFFFFFFFF)
    return GlassTokens(
        isDark = false,
        blurEnabled = blurEnabled,
        bg = Color(0xFFFFFFFF),
        bgSecondary = Color(0xFFF3F5FA),
        glassFill = Color(0x0B0F172A),
        glassElevated = Color(0x130F172A),
        glassStrong = Color(0xECFFFFFF),
        glassBorder = Color(0x120F172A),
        glassHighlight = Color(0xFFFFFFFF),
        glassShadow = Color(0x140F172A),
        ink = Color(0xFF0B0D13),
        inkSecondary = Color(0xFF555B69),
        inkMuted = Color(0xFF8B92A0),
        accent = tuned,
        accentSecondary = tuneaccent(secondary, dark = false),
        accentSoft = tuned.copy(alpha = 0.13f),
        onAccent = onAcc,
        pillSolid = Color(0xFF0B0D13),
        onPillSolid = Color(0xFFFFFFFF),
        danger = Color(0xFFE11D48),
        success = Color(0xFF059669),
    )
}

fun tuneaccent(raw: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (raw.red * 255).toInt(),
        (raw.green * 255).toInt(),
        (raw.blue * 255).toInt(),
        hsv,
    )
    hsv[1] = if (dark) hsv[1].coerceIn(0.32f, 0.72f) else hsv[1].coerceIn(0.50f, 0.86f)
    hsv[2] = if (dark) hsv[2].coerceIn(0.82f, 0.98f) else hsv[2].coerceIn(0.46f, 0.78f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
