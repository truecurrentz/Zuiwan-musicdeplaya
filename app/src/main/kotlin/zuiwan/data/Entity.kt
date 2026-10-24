package zuiwan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zuiwan.model.AlbumRef
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistRef
import zuiwan.model.ArtistSummary
import zuiwan.model.AudioSpec
import zuiwan.model.PlaylistSummary
import zuiwan.model.Track

private val codec = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
@Entity(tableName = "songs")
data class SongRow(
    @PrimaryKey val id: String,
    val title: String,
    val artistsJson: String,
    val albumId: String? = null,
    val albumName: String? = null,
    val duration: Int = 0,
    val artwork: String = "",
    val explicit: Boolean = false,
    val video: Boolean = false,
    val liked: Boolean = false,
    val likedAt: Long = 0L,
    val downloaded: Boolean = false,
    val downloadedAt: Long = 0L,
    val cachedAt: Long = 0L,
    val playCount: Int = 0,
    val totalListenMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun totrack(): Track {
        val parsedArtists = runCatching {
            codec.decodeFromString<List<ArtistRef>>(artistsJson)
        }.getOrDefault(emptyList())
        return Track(
            id = id,
            title = title,
            artists = parsedArtists,
            album = albumName?.takeIf { it.isNotBlank() }?.let { AlbumRef(albumId, it) },
            duration = duration,
            artwork = artwork,
            explicit = explicit,
            video = video,
            liked = liked,
            downloaded = downloaded,
        )
    }
}

fun Track.torow(existing: SongRow? = null): SongRow =
    SongRow(
        id = id,
        title = title,
        artistsJson = codec.encodeToString(artists),
        albumId = album?.id ?: existing?.albumId,
        albumName = album?.name ?: existing?.albumName,
        duration = if (duration > 0) duration else (existing?.duration ?: 0),
        artwork = artwork.ifBlank { existing?.artwork.orEmpty() },
        explicit = explicit || (existing?.explicit == true),
        video = video || (existing?.video == true),
        liked = existing?.liked ?: liked,
        likedAt = existing?.likedAt ?: 0L,
        downloaded = existing?.downloaded ?: downloaded,
        downloadedAt = existing?.downloadedAt ?: 0L,
        cachedAt = existing?.cachedAt ?: 0L,
        playCount = existing?.playCount ?: 0,
        totalListenMs = existing?.totalListenMs ?: 0L,
        updatedAt = System.currentTimeMillis(),
    )

@Serializable
@Entity(tableName = "albums")
data class AlbumRow(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val artistsJson: String = "[]",
    val year: Int? = null,
    val artwork: String = "",
    val explicit: Boolean = false,
    val saved: Boolean = true,
    val savedAt: Long = System.currentTimeMillis(),
) {
    fun toalbum(): AlbumSummary {
        val parsed = runCatching {
            codec.decodeFromString<List<ArtistRef>>(artistsJson)
        }.getOrDefault(emptyList())
        return AlbumSummary(
            id = id,
            playlistId = playlistId,
            title = title,
            artists = parsed,
            year = year,
            artwork = artwork,
            explicit = explicit,
            saved = saved,
        )
    }
}

fun AlbumSummary.toalbumrow(savedFlag: Boolean = true): AlbumRow =
    AlbumRow(
        id = id,
        playlistId = playlistId,
        title = title,
        artistsJson = codec.encodeToString(artists),
        year = year,
        artwork = artwork,
        explicit = explicit,
        saved = savedFlag,
        savedAt = System.currentTimeMillis(),
    )

@Serializable
@Entity(tableName = "artists")
data class ArtistRow(
    @PrimaryKey val id: String,
    val name: String,
    val artwork: String = "",
    val subscribers: String? = null,
    val channelId: String? = null,
    val saved: Boolean = true,
    val savedAt: Long = System.currentTimeMillis(),
) {
    fun toartist(): ArtistSummary =
        ArtistSummary(
            id = id,
            name = name,
            artwork = artwork,
            subscribers = subscribers,
            channelId = channelId,
            saved = saved,
        )
}

fun ArtistSummary.toartistrow(savedFlag: Boolean = true): ArtistRow =
    ArtistRow(
        id = id,
        name = name,
        artwork = artwork,
        subscribers = subscribers,
        channelId = channelId,
        saved = savedFlag,
        savedAt = System.currentTimeMillis(),
    )

@Serializable
@Entity(tableName = "playlists")
data class PlaylistRow(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val artwork: String = "",
    val remoteId: String? = null,
    val editable: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toplaylist(count: Int = 0): PlaylistSummary =
        PlaylistSummary(
            id = id,
            title = title,
            author = author ?: "Local Playlist",
            countText = "$count songs",
            artwork = artwork,
            local = true,
            remoteId = remoteId,
            editable = editable,
        )
}

@Serializable
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrackRow(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "history",
    indices = [Index("trackId"), Index("playedAt")],
)
data class HistoryRow(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0L,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val listenMs: Long = 0L,
)

@Serializable
@Entity(tableName = "queries")
data class QueryRow(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "lyrics")
data class LyricRow(
    @PrimaryKey val trackId: String,
    val raw: String,
    val synced: Boolean,
    val source: String,
    val offsetMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "formats")
data class FormatRow(
    @PrimaryKey val id: String,
    val itag: Int,
    val mime: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int,
    val lengthBytes: Long,
    val loudnessDb: Double? = null,
    val perceptualDb: Double? = null,
    val client: String = "",
) {
    fun tospec(): AudioSpec =
        AudioSpec(
            id = id,
            itag = itag,
            mime = mime,
            codecs = codecs,
            bitrate = bitrate,
            sampleRate = sampleRate,
            lengthBytes = lengthBytes,
            loudnessDb = loudnessDb,
            perceptualDb = perceptualDb,
            client = client,
        )
}

fun AudioSpec.toformatrow(): FormatRow =
    FormatRow(
        id = id,
        itag = itag,
        mime = mime,
        codecs = codecs,
        bitrate = bitrate,
        sampleRate = sampleRate,
        lengthBytes = lengthBytes,
        loudnessDb = loudnessDb,
        perceptualDb = perceptualDb,
        client = client,
    )
