package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistRef(
    val id: String? = null,
    val name: String,
)

@Serializable
data class AlbumRef(
    val id: String? = null,
    val name: String,
)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val duration: Int = 0,
    val artwork: String = "",
    val explicit: Boolean = false,
    val video: Boolean = false,
    val liked: Boolean = false,
    val downloaded: Boolean = false,
    val setVideoId: String? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val localUri: String? = null,
    val folderName: String? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
)

fun Track.islocal(): Boolean =
    !localUri.isNullOrBlank() || id.startsWith("local_")

@Serializable
data class AudioSpec(
    val id: String,
    val itag: Int,
    val mime: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int,
    val lengthBytes: Long,
    val loudnessDb: Double? = null,
    val perceptualDb: Double? = null,
    val client: String = "",
)

@Serializable
enum class QualityMode(val label: String) {
    AUTO("Auto"),
    HIGH("High"),
    LOW("Data Saver"),
}

fun Track.artistline(): String =
    artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

fun Track.trackart(size: Int = 544): String {
    val primary = upscaleart(artwork, size)
    if (primary.isNotBlank()) return primary
    return fallbackart().orEmpty()
}

fun Track.fallbackart(): String? =
    if (id.length in 8..16 && !id.startsWith("local_") && !id.startsWith("auto_") && !id.startsWith("yt_")) {
        "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    } else {
        null
    }

fun upscaleart(url: String, size: Int = 544): String {
    val raw = url.trim()
    if (raw.isEmpty()) return ""
    val normalized = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
        else -> raw
    }
    return when {
        normalized.contains("yt3.ggpht.com") || normalized.contains("yt3.googleusercontent.com") -> {
            if (normalized.contains("?")) {
                normalized
            } else {
                val base = normalized.substringBefore("=w").substringBefore("=s")
                "$base=s$size-c-k-c0x00ffffff-no-rj"
            }
        }
        normalized.contains("lh3.googleusercontent.com") -> {
            if (normalized.contains("?")) {
                normalized
            } else {
                val base = normalized.substringBefore("=w").substringBefore("=s")
                "$base=w$size-h$size-l90-rj"
            }
        }
        normalized.contains("i.ytimg.com/vi/") || normalized.contains("img.youtube.com/vi/") -> {
            val vid = normalized.substringAfter("/vi/").substringBefore("/").substringBefore("?")
            if (vid.isNotBlank()) {
                "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
            } else {
                normalized.substringBefore("?")
            }
        }
        normalized.contains("i.ytimg.com/vi_webp/") -> {
            val vid = normalized.substringAfter("/vi_webp/").substringBefore("/").substringBefore("?")
            if (vid.isNotBlank()) {
                "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
            } else {
                normalized
            }
        }
        else -> normalized
    }
}

fun formatclock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1_000L
    val hours = totalSec / 3_600L
    val mins = (totalSec % 3_600L) / 60L
    val secs = totalSec % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}
