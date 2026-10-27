package zuiwan.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.chrisbanes.haze.HazeState
import zuiwan.model.CssTheme

@Immutable
data class GlassTokens(
    val isDark: Boolean,
    val blurEnabled: Boolean,
    val blurRadius: Float,
    val glassOpacity: Float,
    val compact: Boolean,
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
    blurRadius: Float = 64f,
    glassOpacity: Float = 1.0f,
    compact: Boolean = false,
    bgOverride: Color? = null,
    inkOverride: Color? = null,
    keepRawAccent: Boolean = false,
): GlassTokens {
    val tuned = if (keepRawAccent) accent else tuneaccent(accent, dark = true)
    val tunedSec = if (keepRawAccent) secondary else tuneaccent(secondary, dark = true)
    val onAcc = if (tuned.luminance() > 0.46f) Color(0xFF0A0D14) else Color(0xFFFAFAFC)
    val op = glassOpacity.coerceIn(0.15f, 1.45f)
    val baseInk = inkOverride ?: Color(0xFFFAFAFC)
    val baseBg = bgOverride ?: Color(0xFF0B0C10)
    return GlassTokens(
        isDark = true,
        blurEnabled = blurEnabled && blurRadius > 1f,
        blurRadius = blurRadius.coerceIn(0f, 96f),
        glassOpacity = op,
        compact = compact,
        bg = baseBg,
        bgSecondary = Color(0xFF13151D),
        glassFill = Color.White.copy(alpha = (0.07f * op).coerceIn(0.02f, 0.32f)),
        glassElevated = Color.White.copy(alpha = (0.11f * op).coerceIn(0.03f, 0.42f)),
        glassStrong = Color(0xFF151720).copy(alpha = (0.88f * op).coerceIn(0.28f, 0.98f)),
        glassBorder = Color.White.copy(alpha = (0.08f * op).coerceIn(0.01f, 0.24f)),
        glassHighlight = Color.White.copy(alpha = (0.16f * op).coerceIn(0.04f, 0.35f)),
        glassShadow = Color(0x70000000),
        ink = baseInk,
        inkSecondary = baseInk.copy(alpha = 0.66f),
        inkMuted = baseInk.copy(alpha = 0.42f),
        accent = tuned,
        accentSecondary = tunedSec,
        accentSoft = tuned.copy(alpha = 0.17f),
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
    blurRadius: Float = 64f,
    glassOpacity: Float = 1.0f,
    compact: Boolean = false,
    bgOverride: Color? = null,
    inkOverride: Color? = null,
    keepRawAccent: Boolean = false,
): GlassTokens {
    val tuned = if (keepRawAccent) accent else tuneaccent(accent, dark = false)
    val tunedSec = if (keepRawAccent) secondary else tuneaccent(secondary, dark = false)
    val onAcc = if (tuned.luminance() > 0.50f) Color(0xFF0B0D13) else Color(0xFFFFFFFF)
    val op = glassOpacity.coerceIn(0.15f, 1.45f)
    val baseInk = inkOverride ?: Color(0xFF0B0D13)
    val baseBg = bgOverride ?: Color(0xFFFFFFFF)
    return GlassTokens(
        isDark = false,
        blurEnabled = blurEnabled && blurRadius > 1f,
        blurRadius = blurRadius.coerceIn(0f, 96f),
        glassOpacity = op,
        compact = compact,
        bg = baseBg,
        bgSecondary = Color(0xFFF3F5FA),
        glassFill = Color(0xFF0F172A).copy(alpha = (0.045f * op).coerceIn(0.015f, 0.22f)),
        glassElevated = Color(0xFF0F172A).copy(alpha = (0.075f * op).coerceIn(0.025f, 0.30f)),
        glassStrong = Color.White.copy(alpha = (0.92f * op).coerceIn(0.32f, 0.99f)),
        glassBorder = Color(0xFF0F172A).copy(alpha = (0.07f * op).coerceIn(0.01f, 0.20f)),
        glassHighlight = Color(0xFFFFFFFF),
        glassShadow = Color(0x140F172A),
        ink = baseInk,
        inkSecondary = baseInk.copy(alpha = 0.62f),
        inkMuted = baseInk.copy(alpha = 0.42f),
        accent = tuned,
        accentSecondary = tunedSec,
        accentSoft = tuned.copy(alpha = 0.14f),
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
    hsv[1] = if (dark) hsv[1].coerceIn(0.32f, 0.78f) else hsv[1].coerceIn(0.50f, 0.88f)
    hsv[2] = if (dark) hsv[2].coerceIn(0.82f, 0.98f) else hsv[2].coerceIn(0.46f, 0.82f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun parsehex(raw: String): Color? {
    val clean = raw.trim().removePrefix("#")
    val argb = when (clean.length) {
        6 -> clean.toLongOrNull(16)?.let { 0xFF000000L or it }
        8 -> clean.toLongOrNull(16)
        3 -> {
            val r = clean[0]
            val g = clean[1]
            val b = clean[2]
            "$r$r$g$g$b$b".toLongOrNull(16)?.let { 0xFF000000L or it }
        }
        else -> null
    } ?: return null
    return Color(argb)
}

private val cssPropRegex = Regex("""--([a-zA-Z0-9_-]+)\s*:\s*([^;}\n]+)""")

fun parsecss(css: String): CssTheme {
    if (css.isBlank()) return CssTheme()
    var acc: Long? = null
    var acc2: Long? = null
    var bg: Long? = null
    var ink: Long? = null
    var op: Float? = null
    var blur: Float? = null
    var fScale: Float? = null

    for (m in cssPropRegex.findAll(css)) {
        val key = m.groupValues[1].lowercase()
        val value = m.groupValues[2].trim()
        when (key) {
            "accent", "primary" -> parsehex(value)?.let { acc = it.toArgb().toLong() }
            "accent-secondary", "secondary" -> parsehex(value)?.let { acc2 = it.toArgb().toLong() }
            "bg", "background" -> parsehex(value)?.let { bg = it.toArgb().toLong() }
            "ink", "text", "foreground" -> parsehex(value)?.let { ink = it.toArgb().toLong() }
            "glass-opacity", "opacity" -> value.removeSuffix("%").toFloatOrNull()?.let {
                op = if (value.endsWith("%")) it / 100f else it
            }
            "blur", "backdrop-blur" -> value.lowercase().removeSuffix("px").removeSuffix("dp").trim()
                .toFloatOrNull()?.let { blur = it }
            "font-scale" -> value.toFloatOrNull()?.let { fScale = it.coerceIn(0.8f, 1.35f) }
        }
    }
    return CssTheme(
        accent = acc,
        accentSecondary = acc2,
        bg = bg,
        ink = ink,
        glassOpacity = op,
        blurDp = blur,
        fontScale = fScale,
    )
}
