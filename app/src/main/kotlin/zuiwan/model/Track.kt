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
) {
    fun artistline(): String =
        artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

    fun cleanartwork(size: Int = 544): String = upscaleart(artwork, size)
}

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

enum class QualityMode(val label: String) {
    AUTO("Auto"),
    HIGH("High (256 kbps)"),
    LOW("Data Saver"),
}

fun upscaleart(url: String, size: Int = 544): String {
    if (url.isBlank()) return ""
    return when {
        url.contains("lh3.googleusercontent.com") || url.contains("yt3.ggpht.com") -> {
            val base = url.substringBefore("=w").substringBefore("=s")
            "$base=w$size-h$size-l90-rj"
        }
        url.contains("i.ytimg.com/vi/") -> {
            if (size >= 544) {
                url.replace("default.jpg", "hqdefault.jpg")
                    .replace("mqhqdefault.jpg", "hqdefault.jpg")
                    .replace("sdhqdefault.jpg", "sddefault.jpg")
            } else {
                url
            }
        }
        else -> url
    }
}

fun formatclock(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) {
        "%d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%d:%02d".format(mins, secs)
    }
}
