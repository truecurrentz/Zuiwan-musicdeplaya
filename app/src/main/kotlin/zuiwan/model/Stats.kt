package zuiwan.model

enum class StatsWindow(val label: String, val days: Int?) {
    WEEK("7 Days", 7),
    MONTH("30 Days", 30),
    YEAR("1 Year", 365),
    ALL("All Time", null),
}

data class TrackStat(
    val track: Track,
    val plays: Int,
    val totalTimeMs: Long,
)

data class ArtistStat(
    val artist: ArtistSummary,
    val plays: Int,
    val totalTimeMs: Long,
)

data class StatsSummary(
    val totalMinutes: Long = 0L,
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val topTracks: List<TrackStat> = emptyList(),
    val topArtists: List<ArtistStat> = emptyList(),
)
