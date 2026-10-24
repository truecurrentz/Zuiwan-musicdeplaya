package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val countText: String? = null,
    val artwork: String = "",
    val local: Boolean = false,
    val remoteId: String? = null,
    val editable: Boolean = false,
    val thumbnails: List<String> = emptyList(),
    val songCount: Int = 0,
)

data class PlaylistDetail(
    val playlist: PlaylistSummary,
    val description: String? = null,
    val tracks: List<Track> = emptyList(),
    val continuation: String? = null,
)

enum class AutoKind(val code: String, val title: String) {
    LIKED("liked", "Liked Songs"),
    DOWNLOADED("downloaded", "Downloaded"),
    CACHED("cached", "Cached"),
    TOP("top", "Most Played"),
}
