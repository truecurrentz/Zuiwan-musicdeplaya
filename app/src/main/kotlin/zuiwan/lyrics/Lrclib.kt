package zuiwan.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
data class LrclibEntry(
    val id: Long = 0L,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)

class LrclibSource(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchlrclib(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): Result<String> = runCatching {
        val cleanTitle = cleantitle(title)
        val cleanArtist = artist.substringBefore(",").substringBefore("&").trim()

        val exactRes = http.get("https://lrclib.net/api/get") {
            header("User-Agent", "Zuiwan/1.0.0 (https://github.com/truecurrentz/Zuiwan-musicdeplaya)")
            parameter("track_name", cleanTitle)
            parameter("artist_name", cleanArtist)
            if (!album.isNullOrBlank()) parameter("album_name", album)
            if (durationSec > 0) parameter("duration", durationSec)
        }
        if (exactRes.status.isSuccess()) {
            val item = runCatching { json.decodeFromString<LrclibEntry>(exactRes.bodyAsText()) }.getOrNull()
            val text = item?.syncedLyrics?.takeIf { it.isNotBlank() } ?: item?.plainLyrics?.takeIf { it.isNotBlank() }
            if (text != null) return@runCatching text
        }

        val candidates = searchlrclib("$cleanArtist $cleanTitle").getOrDefault(emptyList())
        val best = candidates
            .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            .minByOrNull { entry ->
                val durDiff = if (durationSec > 0 && entry.duration != null) {
                    abs(entry.duration - durationSec)
                } else {
                    4.0
                }
                val syncBonus = if (!entry.syncedLyrics.isNullOrBlank()) 0.0 else 12.0
                durDiff + syncBonus
            } ?: error("No LrcLib match")

        if (durationSec > 0 && best.duration != null && abs(best.duration - durationSec) > 15.0) {
            error("Duration mismatch on LrcLib")
        }
        best.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: best.plainLyrics?.takeIf { it.isNotBlank() }
            ?: error("Empty LrcLib entry")
    }

    suspend fun searchlrclib(query: String): Result<List<LrclibEntry>> = runCatching {
        val res = http.get("https://lrclib.net/api/search") {
            header("User-Agent", "Zuiwan/1.0.0 (https://github.com/truecurrentz/Zuiwan-musicdeplaya)")
            parameter("q", query)
        }
        if (!res.status.isSuccess()) return@runCatching emptyList()
        json.decodeFromString<List<LrclibEntry>>(res.bodyAsText())
    }

    private fun cleantitle(raw: String): String =
        raw.replace(Regex("""\s*\((?:Official|Lyric|Audio|Video|Visualizer|HD|4K)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[(?:Official|Lyric|Audio|Video|Visualizer|HD|4K)[^\]]*]""", RegexOption.IGNORE_CASE), "")
            .trim()
}
