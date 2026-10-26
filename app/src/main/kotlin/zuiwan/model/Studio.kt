package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
enum class VisualMode(val label: String) {
    SHADER("WebGL Shader"),
    BARS("Spectrum Bars"),
    ORB("Reactive Orb"),
    RIBBON("Wave Ribbon"),
}

@Serializable
enum class ShaderPreset(val label: String) {
    AURORA("Aurora Currents"),
    NEBULA("Cosmic Nebula"),
    RINGS("Sonic Rings"),
    GRID("Cyber Horizon"),
    LIQUID("Liquid Prism"),
}

@Serializable
enum class FontChoice(val label: String) {
    SANS("Geometric Sans"),
    ROUNDED("Soft Rounded"),
    SERIF("Editorial Serif"),
    MONO("Studio Mono"),
    CURSIVE("Expressive Script"),
    CUSTOM("Custom Font"),
}

@Serializable
data class AudioEffects(
    val enabled: Boolean = false,
    val bassBoost: Float = 0f,
    val stereoWide: Float = 0f,
    val reverb: Float = 0f,
    val spatial8d: Boolean = false,
    val balance: Float = 0f,
    val gainDb: Float = 0f,
    val nightLimit: Boolean = false,
)

@Serializable
data class PluginSpec(
    val id: String,
    val name: String,
    val author: String = "Zuiwan",
    val version: String = "1.0.0",
    val description: String = "",
    val builtin: Boolean = false,
    val css: String = "",
    val js: String = "",
    val bassBoost: Float = 0f,
    val stereoWide: Float = 0f,
    val reverb: Float = 0f,
    val spatial8d: Boolean = false,
    val skipSilence: Boolean = false,
    val nightLimit: Boolean = false,
)

data class SpectrumFrame(
    val bands: List<Float> = List(32) { 0f },
    val wave: List<Float> = List(64) { 0f },
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val rms: Float = 0f,
    val beat: Float = 0f,
    val stamp: Long = 0L,
)

data class CssTheme(
    val accent: Long? = null,
    val accentSecondary: Long? = null,
    val bg: Long? = null,
    val ink: Long? = null,
    val glassOpacity: Float? = null,
    val blurDp: Float? = null,
    val fontScale: Float? = null,
)
