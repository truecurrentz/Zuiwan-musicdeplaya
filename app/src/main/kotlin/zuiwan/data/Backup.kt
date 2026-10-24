package zuiwan.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zuiwan.model.ArtistRef
import zuiwan.model.Track
import java.util.UUID

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val songs: List<SongRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val playlists: List<PlaylistRow> = emptyList(),
    val playlistTracks: List<PlaylistTrackRow> = emptyList(),
    val history: List<HistoryRow> = emptyList(),
)

object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportbackup(dao: MusicDao): String {
        val payload = BackupPayload(
            songs = dao.snapshotsongs(),
            albums = dao.snapshotalbums(),
            artists = dao.snapshotartists(),
            playlists = dao.snapshotplaylists(),
            playlistTracks = dao.snapshotplaylisttracks(),
            history = dao.snapshothistory(),
        )
        return json.encodeToString(payload)
    }

    suspend fun importbackup(dao: MusicDao, rawJson: String): Result<Int> =
        runCatching {
            val payload = json.decodeFromString<BackupPayload>(rawJson)
            if (payload.songs.isNotEmpty()) dao.upsertsongs(payload.songs)
            payload.albums.forEach { dao.upsertalbum(it) }
            payload.artists.forEach { dao.upsertartist(it) }
            payload.playlists.forEach { dao.upsertplaylist(it) }
            if (payload.playlistTracks.isNotEmpty()) {
                dao.insertplaylisttracks(payload.playlistTracks)
            }
            payload.history.forEach { dao.inserthistory(it.copy(entryId = 0L)) }
            payload.songs.size + payload.playlists.size
        }

    fun exportm3u(title: String, tracks: List<Track>): String =
        buildString {
            appendLine("#EXTM3U")
            appendLine("#PLAYLIST:$title")
            tracks.forEach { track ->
                appendLine("#EXTINF:${track.duration},${track.artistline()} - ${track.title}")
                appendLine("https://music.youtube.com/watch?v=${track.id}")
            }
        }

    suspend fun importm3u(
        dao: MusicDao,
        name: String,
        content: String,
        resolveQuery: suspend (String) -> Track?,
    ): String {
        val playlistId = "local_${UUID.randomUUID()}"
        val cleanName = name.ifBlank { "Imported Playlist" }
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        val collected = mutableListOf<Track>()
        var pendingInfo: String? = null

        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                pendingInfo = line.substringAfter(",", "").trim().takeIf { it.isNotBlank() }
                continue
            }
            if (line.startsWith("#")) continue

            val ytId = extractvideoid(line)
            if (ytId != null) {
                val label = pendingInfo ?: ytId
                val artistPart = label.substringBefore(" - ", "").trim()
                val titlePart = label.substringAfter(" - ", label).trim()
                collected += Track(
                    id = ytId,
                    title = titlePart.ifBlank { ytId },
                    artists = if (artistPart.isNotBlank()) listOf(ArtistRef(null, artistPart)) else emptyList(),
                    artwork = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg",
                )
            } else {
                val query = pendingInfo ?: line.replace(",", " - ")
                resolveQuery(query)?.let { collected += it }
            }
            pendingInfo = null
        }

        val cover = collected.firstOrNull()?.artwork.orEmpty()
        dao.upsertplaylist(
            PlaylistRow(
                id = playlistId,
                title = cleanName,
                artwork = cover,
            ),
        )
        dao.upsertsongs(collected.map { it.torow(dao.findsong(it.id)) })
        dao.insertplaylisttracks(
            collected.distinctBy { it.id }.mapIndexed { index, track ->
                PlaylistTrackRow(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = index,
                )
            },
        )
        return playlistId
    }

    private fun extractvideoid(text: String): String? {
        val vMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(text)
        if (vMatch != null) return vMatch.groupValues[1]
        val shortMatch = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(text)
        if (shortMatch != null) return shortMatch.groupValues[1]
        if (text.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return text
        return null
    }
}
