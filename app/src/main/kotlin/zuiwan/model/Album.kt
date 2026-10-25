package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
data class AlbumSummary(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val year: Int? = null,
    val artwork: String = "",
    val explicit: Boolean = false,
    val saved: Boolean = false,
) {
    fun subtitle(): String {
        val names = artists.joinToString(", ") { it.name }
        return listOfNotNull(
            names.takeIf { it.isNotBlank() },
            year?.toString(),
        ).joinToString(" - ")
    }
}

data class AlbumDetail(
    val album: AlbumSummary,
    val tracks: List<Track>,
    val versions: List<AlbumSummary> = emptyList(),
    val description: String? = null,
)
