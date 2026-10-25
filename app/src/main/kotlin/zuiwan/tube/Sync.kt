package zuiwan.tube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import zuiwan.data.AlbumEntity
import zuiwan.data.ArtistEntity
import zuiwan.data.MusicDao
import zuiwan.data.PlaylistEntity
import zuiwan.data.PlaylistTrackEntity
import zuiwan.data.toentity
import zuiwan.model.FeedItem
import zuiwan.model.Track
import zuiwan.model.upscaleart
import java.util.UUID

data class SyncReport(
    val likedCount: Int,
    val playlistCount: Int,
    val albumCount: Int,
    val artistCount: Int,
)

class CloudSync(
    private val dao: MusicDao,
    private val tube: TubeClient,
) {
    private val lock = Mutex()
    private val activeFlow = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = activeFlow.asStateFlow()

    suspend fun syncall(
        onStep: (title: String, subtitle: String, progress: Float?) -> Unit = { _, _, _ -> },
    ): Result<SyncReport> = withContext(Dispatchers.IO) {
        if (!tube.isauthed()) {
            return@withContext Result.failure(IllegalStateException("Sign in to YouTube Music to sync"))
        }
        if (!lock.tryLock()) {
            return@withContext Result.failure(IllegalStateException("Sync is already running"))
        }
        activeFlow.value = true
        try {
            runCatching {
                onStep("Syncing YouTube Music", "Fetching liked songs", 0.10f)
                val lmTracks = tube.fullplaylist("LM", maxPages = 35).getOrNull()?.tracks.orEmpty()
                val lvTracks = tube.loadlibrary("FEmusic_liked_videos", maxPages = 15)
                    .getOrNull()
                    .orEmpty()
                    .mapNotNull { (it as? FeedItem.SongCard)?.track }

                val combinedLiked = LinkedHashMap<String, Track>()
                lmTracks.forEach { combinedLiked[it.id] = it.copy(liked = true) }
                lvTracks.forEach { t ->
                    if (!combinedLiked.containsKey(t.id)) {
                        combinedLiked[t.id] = t.copy(liked = true)
                    }
                }

                val now = System.currentTimeMillis()
                val totalLiked = combinedLiked.size
                combinedLiked.values.forEachIndexed { idx, track ->
                    mergetrack(track, forceLiked = true, fallbackLikedAt = now - idx * 10L)
                }

                onStep(
                    "Syncing YouTube Music",
                    if (totalLiked > 0) "$totalLiked liked songs · Loading playlists" else "Loading playlists",
                    0.32f,
                )

                val remotePlItems = tube.loadlibrary("FEmusic_liked_playlists", maxPages = 20)
                    .getOrNull()
                    .orEmpty()
                    .mapNotNull { (it as? FeedItem.PlaylistCard)?.playlist }
                    .filter { pl ->
                        pl.id.isNotBlank() &&
                            pl.id != "LM" &&
                            pl.id != "SE" &&
                            pl.id != "RDPN" &&
                            !pl.id.startsWith("FEmusic_")
                    }
                    .distinctBy { it.id }

                val existingAll = dao.allplaylistslist()
                val byRemote = existingAll.filter { !it.remoteId.isNullOrBlank() }.groupBy { it.remoteId!! }
                byRemote.values.forEach { dupes ->
                    if (dupes.size > 1) {
                        dupes.drop(1).forEach { extra ->
                            dao.unlinkall(extra.id)
                            dao.deleteplaylist(extra.id)
                        }
                    }
                }

                val syncedLocalEntities = mutableListOf<PlaylistEntity>()
                remotePlItems.forEach { remote ->
                    val current = dao.getremoteplaylist(remote.id) ?: dao.getplaylist(remote.id)
                    val entity = if (current == null) {
                        PlaylistEntity(
                            id = "yt_${remote.id}",
                            title = remote.title,
                            author = remote.author,
                            artwork = upscaleart(remote.artwork),
                            local = false,
                            remoteId = remote.id,
                            editable = remote.editable || remote.id.startsWith("PL"),
                        )
                    } else {
                        current.copy(
                            title = remote.title.ifBlank { current.title },
                            author = remote.author ?: current.author,
                            artwork = upscaleart(remote.artwork).ifBlank { current.artwork },
                            remoteId = remote.id,
                            editable = current.editable || remote.editable || remote.id.startsWith("PL"),
                        )
                    }
                    dao.putplaylist(entity)
                    syncedLocalEntities += entity
                }

                val plTotal = syncedLocalEntities.size
                if (plTotal > 0) {
                    val gate = Semaphore(4)
                    var doneCount = 0
                    coroutineScope {
                        syncedLocalEntities.map { entity ->
                            async {
                                gate.withPermit {
                                    runCatching { syncinner(entity) }
                                    synchronized(this@CloudSync) {
                                        doneCount++
                                        val frac = 0.34f + 0.40f * (doneCount.toFloat() / plTotal.coerceAtLeast(1))
                                        onStep(
                                            "Syncing YouTube Music",
                                            "Playlist $doneCount of $plTotal · ${entity.title}",
                                            frac,
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }

                onStep("Syncing YouTube Music", "Syncing saved albums", 0.80f)
                val remoteAlbums = tube.loadlibrary("FEmusic_liked_albums", maxPages = 15)
                    .getOrNull()
                    .orEmpty()
                    .mapNotNull { (it as? FeedItem.AlbumCard)?.album }
                    .distinctBy { it.id }

                remoteAlbums.forEachIndexed { idx, album ->
                    val prev = dao.getalbum(album.id)
                    val base = album.toentity(savedOverride = true)
                    dao.putalbum(
                        base.copy(
                            playlistId = base.playlistId ?: prev?.playlistId,
                            artistsJson = if (album.artists.isNotEmpty()) base.artistsJson else (prev?.artistsJson ?: base.artistsJson),
                            year = base.year ?: prev?.year,
                            artwork = base.artwork.ifBlank { prev?.artwork.orEmpty() },
                            savedAt = prev?.savedAt ?: (now - idx * 10L),
                        ),
                    )
                }

                onStep("Syncing YouTube Music", "Syncing subscribed artists", 0.92f)
                val subArtists = tube.loadlibrary("FEmusic_library_corpus_artists", maxPages = 15)
                    .getOrNull()
                    .orEmpty()
                    .mapNotNull { (it as? FeedItem.ArtistCard)?.artist }
                val libArtists = if (subArtists.isEmpty()) {
                    tube.loadlibrary("FEmusic_library_corpus_track_artists", maxPages = 10)
                        .getOrNull()
                        .orEmpty()
                        .mapNotNull { (it as? FeedItem.ArtistCard)?.artist }
                } else {
                    emptyList()
                }
                val remoteArtists = (subArtists + libArtists).distinctBy { it.id }
                remoteArtists.forEachIndexed { idx, artist ->
                    val prev = dao.getartist(artist.id)
                    val base = artist.toentity(savedOverride = true)
                    dao.putartist(
                        base.copy(
                            artwork = base.artwork.ifBlank { prev?.artwork.orEmpty() },
                            subscribers = base.subscribers ?: prev?.subscribers,
                            channelId = base.channelId ?: artist.id.takeIf { it.startsWith("UC") } ?: prev?.channelId,
                            savedAt = prev?.savedAt ?: (now - idx * 10L),
                        ),
                    )
                }

                onStep("Syncing YouTube Music", "Finishing up", 1.0f)
                SyncReport(
                    likedCount = totalLiked,
                    playlistCount = plTotal,
                    albumCount = remoteAlbums.size,
                    artistCount = remoteArtists.size,
                )
            }
        } finally {
            activeFlow.value = false
            lock.unlock()
        }
    }

    suspend fun syncone(playlistId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanRemote = playlistId.removePrefix("VL").removePrefix("yt_")
                val existing = dao.getplaylist(playlistId)
                    ?: dao.getremoteplaylist(cleanRemote)
                    ?: run {
                        val detail = tube.fullplaylist(cleanRemote, maxPages = 25).getOrThrow()
                        val created = PlaylistEntity(
                            id = "yt_$cleanRemote",
                            title = detail.playlist.title,
                            author = detail.playlist.author,
                            artwork = upscaleart(detail.playlist.artwork),
                            local = false,
                            remoteId = cleanRemote,
                            editable = detail.playlist.editable,
                        )
                        dao.putplaylist(created)
                        created
                    }
                syncinner(existing)
            }
        }

    suspend fun pushlocal(
        localPlaylistId: String,
        onStep: (String, Float?) -> Unit = { _, _ -> },
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(tube.isauthed()) { "Sign in to YouTube Music first" }
            val pl = dao.getplaylist(localPlaylistId) ?: error("Playlist not found")
            val remoteId = pl.remoteId?.takeIf { it.isNotBlank() } ?: run {
                onStep("Creating playlist on YouTube Music", 0.1f)
                val newId = tube.createremote(pl.title).getOrThrow()
                dao.putplaylist(
                    pl.copy(
                        remoteId = newId,
                        editable = true,
                    ),
                )
                newId
            }

            val localTracks = dao.playlisttracks(pl.id)
            val existingLinks = dao.playlistlinks(pl.id).associateBy { it.trackId }
            val toUpload = localTracks.filter { existingLinks[it.id]?.setVideoId.isNullOrBlank() }

            toUpload.forEachIndexed { idx, track ->
                val frac = 0.15f + 0.70f * ((idx + 1).toFloat() / toUpload.size.coerceAtLeast(1))
                onStep("Uploading ${idx + 1} of ${toUpload.size} · ${track.title}", frac)
                tube.addremote(remoteId, track.id)
            }

            onStep("Refreshing playlist links", 0.92f)
            val refreshed = dao.getplaylist(pl.id) ?: pl.copy(remoteId = remoteId, editable = true)
            syncinner(refreshed)
            remoteId
        }
    }

    private suspend fun syncinner(entity: PlaylistEntity): Int {
        val remoteId = entity.remoteId?.takeIf { it.isNotBlank() } ?: return 0
        val detail = tube.fullplaylist(remoteId, maxPages = 25).getOrThrow()

        val currentLinks = dao.playlistlinks(entity.id)
        val remoteIds = detail.tracks.map { it.id }.toSet()
        val isEditable = entity.editable || detail.playlist.editable

        if (isEditable) {
            val unsyncedLocal = currentLinks.filter { it.setVideoId.isNullOrBlank() && it.trackId !in remoteIds }
            if (unsyncedLocal.isNotEmpty()) {
                unsyncedLocal.forEach { pending ->
                    tube.addremote(remoteId, pending.trackId)
                }
            }
        }

        val finalDetail = if (isEditable && currentLinks.any { it.setVideoId.isNullOrBlank() && it.trackId !in remoteIds }) {
            tube.fullplaylist(remoteId, maxPages = 25).getOrNull() ?: detail
        } else {
            detail
        }

        val art = upscaleart(finalDetail.playlist.artwork)
            .ifBlank { finalDetail.tracks.firstOrNull()?.artwork.orEmpty() }
            .ifBlank { entity.artwork }

        dao.putplaylist(
            entity.copy(
                title = finalDetail.playlist.title.ifBlank { entity.title },
                author = finalDetail.playlist.author ?: entity.author,
                artwork = art,
                editable = isEditable,
            ),
        )

        dao.unlinkall(entity.id)
        val now = System.currentTimeMillis()
        finalDetail.tracks.forEachIndexed { idx, track ->
            mergetrack(track, forceLiked = false, fallbackLikedAt = null)
            dao.linktrack(
                PlaylistTrackEntity(
                    playlistId = entity.id,
                    trackId = track.id,
                    position = idx,
                    addedAt = now + idx,
                    setVideoId = track.setVideoId,
                ),
            )
        }
        return finalDetail.tracks.size
    }

    private suspend fun mergetrack(
        track: Track,
        forceLiked: Boolean,
        fallbackLikedAt: Long?,
    ) {
        val existing = dao.gettrack(track.id)
        if (existing == null) {
            val entity = track.copy(liked = forceLiked || track.liked).toentity()
            dao.puttrack(
                if (forceLiked && fallbackLikedAt != null) {
                    entity.copy(liked = true, likedAt = fallbackLikedAt)
                } else {
                    entity
                },
            )
        } else {
            val fresh = track.toentity()
            val freshArt = upscaleart(track.artwork).ifBlank { existing.artwork }
            dao.puttrack(
                existing.copy(
                    title = track.title.ifBlank { existing.title },
                    artistsJson = if (track.artists.isNotEmpty()) fresh.artistsJson else existing.artistsJson,
                    albumId = track.album?.id ?: existing.albumId,
                    albumName = track.album?.name ?: existing.albumName,
                    duration = if (track.duration > 0) track.duration else existing.duration,
                    artwork = freshArt,
                    liked = if (forceLiked) true else existing.liked,
                    likedAt = if (forceLiked) (existing.likedAt ?: fallbackLikedAt ?: System.currentTimeMillis()) else existing.likedAt,
                ),
            )
        }
    }
}
