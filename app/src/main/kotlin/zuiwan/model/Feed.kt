package zuiwan.model

data class MoodChip(
    val title: String,
    val params: String,
)

data class MoodTile(
    val title: String,
    val browseId: String,
    val params: String?,
    val stripeColor: Long,
)

sealed interface FeedItem {
    val key: String
    val title: String
    val subtitle: String
    val artwork: String

    data class SongCard(val track: Track) : FeedItem {
        override val key: String get() = "song:${track.id}"
        override val title: String get() = track.title
        override val subtitle: String get() = track.artistline()
        override val artwork: String get() = track.artwork
    }

    data class AlbumCard(val album: AlbumSummary) : FeedItem {
        override val key: String get() = "album:${album.id}"
        override val title: String get() = album.title
        override val subtitle: String get() = album.subtitle()
        override val artwork: String get() = album.artwork
    }

    data class ArtistCard(val artist: ArtistSummary) : FeedItem {
        override val key: String get() = "artist:${artist.id}"
        override val title: String get() = artist.name
        override val subtitle: String get() = artist.subscribers ?: "Artist"
        override val artwork: String get() = artist.artwork
    }

    data class PlaylistCard(val playlist: PlaylistSummary) : FeedItem {
        override val key: String get() = "playlist:${playlist.id}"
        override val title: String get() = playlist.title
        override val subtitle: String
            get() = listOfNotNull(playlist.author, playlist.countText).joinToString(" - ")
        override val artwork: String get() = playlist.artwork
    }
}

data class FeedShelf(
    val title: String,
    val subtitle: String? = null,
    val items: List<FeedItem>,
    val browseId: String? = null,
    val params: String? = null,
)

data class HomeFeed(
    val chips: List<MoodChip> = emptyList(),
    val shelves: List<FeedShelf> = emptyList(),
    val continuation: String? = null,
)

data class ExploreFeed(
    val releases: List<AlbumSummary> = emptyList(),
    val moods: List<MoodTile> = emptyList(),
    val charts: List<FeedShelf> = emptyList(),
)

enum class SearchTab(val label: String, val filterParam: String?) {
    ALL("All", null),
    SONGS("Songs", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("Videos", "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
    ALBUMS("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
    ARTISTS("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
    PLAYLISTS("Playlists", "EgeKAQQoAEABagoQAxAEEAoQCRAF"),
    FEATURED("Featured", "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"),
}

data class SearchPage(
    val shelves: List<FeedShelf> = emptyList(),
    val items: List<FeedItem> = emptyList(),
    val continuation: String? = null,
)
