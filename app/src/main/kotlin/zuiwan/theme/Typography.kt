package zuiwan.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import zuiwan.model.FontChoice
import java.io.File

fun buildtype(
    choice: FontChoice = FontChoice.SANS,
    customFontPath: String = "",
    scale: Float = 1f,
): Typography {
    val family = when (choice) {
        FontChoice.SANS -> FontFamily.SansSerif
        FontChoice.ROUNDED -> runCatching {
            FontFamily(Typeface.create("sans-serif-rounded", Typeface.NORMAL))
        }.getOrDefault(FontFamily.SansSerif)
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONO -> FontFamily.Monospace
        FontChoice.CURSIVE -> FontFamily.Cursive
        FontChoice.CUSTOM -> {
            val f = customFontPath.takeIf { it.isNotBlank() }?.let(::File)
            if (f != null && f.exists() && f.length() > 128L) {
                runCatching { FontFamily(Typeface.createFromFile(f)) }
                    .getOrDefault(FontFamily.SansSerif)
            } else {
                FontFamily.SansSerif
            }
        }
    }
    val s = scale.coerceIn(0.82f, 1.30f)
    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (32f * s).sp,
            lineHeight = (38f * s).sp,
            letterSpacing = (-0.6).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (24f * s).sp,
            lineHeight = (30f * s).sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (20f * s).sp,
            lineHeight = (26f * s).sp,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (18f * s).sp,
            lineHeight = (24f * s).sp,
            letterSpacing = (-0.15).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15f * s).sp,
            lineHeight = (20f * s).sp,
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (13f * s).sp,
            lineHeight = (18f * s).sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (15f * s).sp,
            lineHeight = (22f * s).sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (13f * s).sp,
            lineHeight = (18f * s).sp,
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13f * s).sp,
            lineHeight = (16f * s).sp,
            letterSpacing = 0.2.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * s).sp,
            lineHeight = (14f * s).sp,
            letterSpacing = 0.3.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (10f * s).sp,
            lineHeight = (13f * s).sp,
            letterSpacing = 0.4.sp,
        ),
    )
}

val ZuiwanTypography = buildtype()
