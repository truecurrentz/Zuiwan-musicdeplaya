package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val romanized: String? = null,
    val translated: String? = null,
    val words: List<LyricWord> = emptyList(),
)

@Serializable
enum class LyricSource(val label: String) {
    BETTER("BetterLyrics"),
    LRCLIB("LRCLIB"),
    KUGOU("KuGou"),
    YOUTUBE("YouTube"),
    LOCAL("Saved"),
}

@Serializable
data class LyricSheet(
    val trackId: String,
    val synced: Boolean,
    val lines: List<LyricLine>,
    val raw: String,
    val source: LyricSource,
    val offsetMs: Long = 0L,
)
