package zuiwan.model

enum class StatRange(val label: String, val days: Int?) {
    WEEK("7 Days", 7),
    MONTH("30 Days", 30),
    YEAR("1 Year", 365),
    ALL("All Time", null),
}

data class RankedTrack(
    val track: Track,
    val plays: Int,
    val listenMs: Long,
)

data class RankedArtist(
    val name: String,
    val id: String?,
    val artwork: String,
    val plays: Int,
    val listenMs: Long,
)

data class RankedAlbum(
    val id: String,
    val title: String,
    val artwork: String,
    val plays: Int,
    val listenMs: Long,
)

data class StatSummary(
    val totalPlays: Int = 0,
    val totalMinutes: Long = 0L,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val topTracks: List<RankedTrack> = emptyList(),
    val topArtists: List<RankedArtist> = emptyList(),
    val topAlbums: List<RankedAlbum> = emptyList(),
)
