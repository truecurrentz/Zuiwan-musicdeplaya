package zuiwan.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistSummary(
    val id: String,
    val name: String,
    val artwork: String = "",
    val subscribers: String? = null,
    val channelId: String? = null,
    val saved: Boolean = false,
)

data class ArtistSection(
    val title: String,
    val items: List<FeedItem>,
    val browseId: String? = null,
    val params: String? = null,
)

data class ArtistDetail(
    val artist: ArtistSummary,
    val listeners: String? = null,
    val description: String? = null,
    val shufflePlaylistId: String? = null,
    val radioPlaylistId: String? = null,
    val sections: List<ArtistSection> = emptyList(),
)
