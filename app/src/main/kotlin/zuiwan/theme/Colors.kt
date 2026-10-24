package zuiwan.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class GlassTokens(
    val isDark: Boolean,
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
    val danger: Color,
    val success: Color,
)

val LocalGlass = staticCompositionLocalOf {
    darkglass(Color(0xFF38BDF8), Color(0xFF818CF8), pureBlack = false)
}

fun darkglass(
    accent: Color,
    secondary: Color,
    pureBlack: Boolean,
): GlassTokens {
    val tuned = tuneaccent(accent, dark = true)
    val onAcc = if (tuned.luminance() > 0.46f) Color(0xFF06121E) else Color(0xFFF8FAFC)
    return GlassTokens(
        isDark = true,
        bg = if (pureBlack) Color(0xFF000000) else Color(0xFF090D16),
        bgSecondary = if (pureBlack) Color(0xFF070B12) else Color(0xFF111827),
        glassFill = if (pureBlack) Color(0x1CFFFFFF) else Color(0x1EFFFFFF),
        glassElevated = if (pureBlack) Color(0x28FFFFFF) else Color(0x2CFFFFFF),
        glassStrong = if (pureBlack) Color(0xD80A0E17) else Color(0xDE101726),
        glassBorder = Color(0x2EFFFFFF),
        glassHighlight = Color(0x48FFFFFF),
        glassShadow = Color(0x78000000),
        ink = Color(0xFFF8FAFC),
        inkSecondary = Color(0xFFA5B4C8),
        inkMuted = Color(0xFF6B7B93),
        accent = tuned,
        accentSecondary = tuneaccent(secondary, dark = true),
        accentSoft = tuned.copy(alpha = 0.18f),
        onAccent = onAcc,
        danger = Color(0xFFFB7185),
        success = Color(0xFF34D399),
    )
}

fun lightglass(
    accent: Color,
    secondary: Color,
): GlassTokens {
    val tuned = tuneaccent(accent, dark = false)
    val onAcc = if (tuned.luminance() > 0.52f) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    return GlassTokens(
        isDark = false,
        bg = Color(0xFFF3F6FC),
        bgSecondary = Color(0xFFE7EEF8),
        glassFill = Color(0xC7FFFFFF),
        glassElevated = Color(0xE6FFFFFF),
        glassStrong = Color(0xF2FFFFFF),
        glassBorder = Color(0xFFD6E0EE),
        glassHighlight = Color(0xFFFFFFFF),
        glassShadow = Color(0x1A0F172A),
        ink = Color(0xFF0F172A),
        inkSecondary = Color(0xFF475569),
        inkMuted = Color(0xFF7B8BA1),
        accent = tuned,
        accentSecondary = tuneaccent(secondary, dark = false),
        accentSoft = tuned.copy(alpha = 0.14f),
        onAccent = onAcc,
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
    hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
    hsv[2] = if (dark) hsv[2].coerceIn(0.76f, 0.98f) else hsv[2].coerceIn(0.48f, 0.78f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
