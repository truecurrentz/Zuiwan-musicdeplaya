package zuiwan.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zuiwan.model.AlbumRef
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistRef
import zuiwan.model.ArtistSummary
import zuiwan.model.AudioSpec
import zuiwan.model.LyricPayload
import zuiwan.model.MatchResult
import zuiwan.model.PlaylistSummary
import zuiwan.model.Track
import zuiwan.model.trackart
import zuiwan.model.upscaleart

private val entityJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistsJson: String = "[]",
    val albumId: String? = null,
    val albumName: String? = null,
    val duration: Int = 0,
    val artwork: String = "",
    val explicit: Boolean = false,
    val video: Boolean = false,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val downloaded: Boolean = false,
    val downloadedAt: Long? = null,
    val playCount: Int = 0,
    val totalPlayTimeMs: Long = 0L,
    val lastPlayedAt: Long? = null,
)

fun TrackEntity.totrack(): Track {
    val parsed = runCatching {
        entityJson.decodeFromString<List<ArtistRef>>(artistsJson)
    }.getOrDefault(emptyList())
    val repairedArt = upscaleart(artwork).ifBlank {
        if (id.length in 8..16 && !id.startsWith("local_") && !id.startsWith("auto_") && !id.startsWith("yt_")) {
            "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        } else {
            ""
        }
    }
    return Track(
        id = id,
        title = title,
        artists = parsed,
        album = albumName?.takeIf { it.isNotBlank() }?.let { AlbumRef(albumId, it) },
        duration = duration,
        artwork = repairedArt,
        explicit = explicit,
        video = video,
        liked = liked,
        downloaded = downloaded,
    )
}

fun Track.toentity(): TrackEntity =
    TrackEntity(
        id = id,
        title = title,
        artistsJson = entityJson.encodeToString(artists),
        albumId = album?.id,
        albumName = album?.name,
        duration = duration,
        artwork = trackart(),
        explicit = explicit,
        video = video,
        liked = liked,
        likedAt = if (liked) System.currentTimeMillis() else null,
        downloaded = downloaded,
        downloadedAt = if (downloaded) System.currentTimeMillis() else null,
    )

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val artistsJson: String = "[]",
    val year: Int? = null,
    val artwork: String = "",
    val explicit: Boolean = false,
    val saved: Boolean = true,
    val savedAt: Long = System.currentTimeMillis(),
)

fun AlbumEntity.tosummary(): AlbumSummary =
    AlbumSummary(
        id = id,
        playlistId = playlistId,
        title = title,
        artists = runCatching { entityJson.decodeFromString<List<ArtistRef>>(artistsJson) }.getOrDefault(emptyList()),
        year = year,
        artwork = upscaleart(artwork),
        explicit = explicit,
        saved = saved,
    )

fun AlbumSummary.toentity(savedOverride: Boolean = saved): AlbumEntity =
    AlbumEntity(
        id = id,
        playlistId = playlistId,
        title = title,
        artistsJson = entityJson.encodeToString(artists),
        year = year,
        artwork = upscaleart(artwork),
        explicit = explicit,
        saved = savedOverride,
    )

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artwork: String = "",
    val subscribers: String? = null,
    val channelId: String? = null,
    val saved: Boolean = true,
    val savedAt: Long = System.currentTimeMillis(),
)

fun ArtistEntity.tosummary(): ArtistSummary =
    ArtistSummary(
        id = id,
        name = name,
        artwork = upscaleart(artwork),
        subscribers = subscribers,
        channelId = channelId,
        saved = saved,
    )

fun ArtistSummary.toentity(savedOverride: Boolean = saved): ArtistEntity =
    ArtistEntity(
        id = id,
        name = name,
        artwork = upscaleart(artwork),
        subscribers = subscribers,
        channelId = channelId,
        saved = savedOverride,
    )

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val artwork: String = "",
    val local: Boolean = true,
    val remoteId: String? = null,
    val editable: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

fun PlaylistEntity.tosummary(count: Int): PlaylistSummary =
    PlaylistSummary(
        id = id,
        title = title,
        author = author ?: "You",
        countText = "$count songs",
        artwork = upscaleart(artwork),
        local = local,
        remoteId = remoteId,
        editable = editable,
    )

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val setVideoId: String? = null,
)

@Entity(tableName = "recognized")
data class RecognizedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String = "",
    val genre: String? = null,
    val releaseDate: String? = null,
    val videoId: String? = null,
    val recognizedAt: Long = System.currentTimeMillis(),
)

fun RecognizedEntity.tomatch(): MatchResult =
    MatchResult(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artwork = upscaleart(artwork),
        genre = genre,
        releaseDate = releaseDate,
        videoId = videoId,
        recognizedAt = recognizedAt,
    )

fun MatchResult.toentity(): RecognizedEntity =
    RecognizedEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artwork = upscaleart(artwork),
        genre = genre,
        releaseDate = releaseDate,
        videoId = videoId ?: matchedTrack?.id,
        recognizedAt = recognizedAt,
    )

@Entity(
    tableName = "play_events",
    indices = [Index("trackId"), Index("timestamp")],
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val timestamp: Long,
    val playTimeMs: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val source: String,
    val synced: Boolean,
    val raw: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

fun LyricsEntity.topayload(): LyricPayload =
    LyricPayload(
        source = source,
        synced = synced,
        raw = raw,
    )

fun LyricPayload.toentity(trackId: String): LyricsEntity =
    LyricsEntity(
        trackId = trackId,
        source = source,
        synced = synced,
        raw = raw,
    )

@Entity(tableName = "formats")
data class FormatEntity(
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
)

fun FormatEntity.tospec(): AudioSpec =
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

fun AudioSpec.toentity(): FormatEntity =
    FormatEntity(
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

@Entity(tableName = "saved_queue")
data class QueueEntity(
    @PrimaryKey val slot: Int,
    val trackId: String,
    val queueTitle: String,
    val activeIndex: Int,
)

data class TopTrackRow(
    @Embedded val track: TrackEntity,
    val plays: Int,
    val playTime: Long,
)
