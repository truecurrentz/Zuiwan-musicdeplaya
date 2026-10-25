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
    val endMs: Long = 0L,
    val text: String,
    val romanized: String? = null,
    val translated: String? = null,
    val words: List<LyricWord> = emptyList(),
)

@Serializable
data class LyricPayload(
    val source: String = "",
    val synced: Boolean = false,
    val wordSynced: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val raw: String = "",
)
