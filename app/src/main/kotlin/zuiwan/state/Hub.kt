package zuiwan.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zuiwan.audio.AudioPilot
import zuiwan.audio.AudioVault
import zuiwan.data.AppSettings
import zuiwan.data.BackupManager
import zuiwan.data.MusicDao
import zuiwan.data.MusicDb
import zuiwan.data.PlaylistEntity
import zuiwan.data.PlaylistTrackEntity
import zuiwan.data.PrefStore
import zuiwan.data.SearchHistoryEntity
import zuiwan.data.toentity
import zuiwan.data.tosummary
import zuiwan.data.totrack
import zuiwan.dsp.EqualizerProcessor
import zuiwan.dsp.NormalizerProcessor
import zuiwan.lyrics.LyricsExtractor
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistStat
import zuiwan.model.ArtistSummary
import zuiwan.model.EqProfile
import zuiwan.model.ExploreFeed
import zuiwan.model.HomeFeed
import zuiwan.model.MoodChip
import zuiwan.model.PlaylistDetail
import zuiwan.model.PlaylistSummary
import zuiwan.model.SessionData
import zuiwan.model.StatsSummary
import zuiwan.model.StatsWindow
import zuiwan.model.Track
import zuiwan.model.TrackStat
import zuiwan.tube.ScrobbleClient
import zuiwan.tube.StreamEngine
import zuiwan.tube.TubeClient
import java.util.UUID

val LocalHub = staticCompositionLocalOf<Hub> { error("Hub missing") }

@OptIn(UnstableApi::class)
class Hub(val appContext: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: MusicDb = MusicDb.open(appContext)
    val dao: MusicDao = db.dao()
    val store: PrefStore = PrefStore(appContext)
    val backup: BackupManager = BackupManager(appContext, db)

    val tube: TubeClient = TubeClient()
    val scrobbler: ScrobbleClient = ScrobbleClient(tube.http)
    val streams: StreamEngine = StreamEngine(appContext) { tube.innerTube }
    val lyrics: LyricsExtractor = LyricsExtractor(dao, tube)

    val normalizer: NormalizerProcessor = NormalizerProcessor()
    val equalizer: EqualizerProcessor = EqualizerProcessor()

    val settings: StateFlow<AppSettings> = store.settings.stateIn(
        scope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    val session: StateFlow<SessionData> = store.session.stateIn(
        scope,
        SharingStarted.Eagerly,
        SessionData(),
    )

    val eq: StateFlow<EqProfile> = store.eq.stateIn(
        scope,
        SharingStarted.Eagerly,
        EqProfile(),
    )

    val vault: AudioVault = AudioVault(
        context = appContext,
        dao = dao,
        streamEngine = streams,
        qualityProvider = { settings.value.quality },
    )

    val pilot: AudioPilot = AudioPilot(
        context = appContext,
        dao = dao,
        store = store,
        tube = tube,
        scrobbler = scrobbler,
        lyricsExtractor = lyrics,
        equalizer = equalizer,
        vault = vault,
    )

    val likedTracks: StateFlow<List<Track>> = dao.likedtracks()
        .map { rows -> rows.map { it.totrack() } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val likedIds: StateFlow<Set<String>> = likedTracks
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val downloadedTracks: StateFlow<List<Track>> = dao.downloadedtracks()
        .map { rows -> rows.map { it.totrack() } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val downloadedIds: StateFlow<Set<String>> = downloadedTracks
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val historyTracks: StateFlow<List<Track>> = dao.recenttracks()
        .map { rows -> rows.map { it.totrack() } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val savedAlbums: StateFlow<List<AlbumSummary>> = dao.savedalbums()
        .map { rows -> rows.map { it.tosummary() } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val savedArtists: StateFlow<List<ArtistSummary>> = dao.savedartists()
        .map { rows -> rows.map { it.tosummary() } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = combine(
        dao.allplaylists(),
        likedTracks,
    ) { rows, _ ->
        rows.map { row ->
            val cnt = dao.playlistcount(row.id)
            row.tosummary(cnt)
        }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val searchHistory: StateFlow<List<String>> = dao.recentsearches()
        .map { rows -> rows.map { it.query } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val homeFeedFlow = MutableStateFlow(HomeFeed())
    val homeFeed: StateFlow<HomeFeed> = homeFeedFlow.asStateFlow()

    private val homeLoadingFlow = MutableStateFlow(true)
    val homeLoading: StateFlow<Boolean> = homeLoadingFlow.asStateFlow()

    private val homeErrorFlow = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = homeErrorFlow.asStateFlow()

    private val selectedChipFlow = MutableStateFlow<MoodChip?>(null)
    val selectedChip: StateFlow<MoodChip?> = selectedChipFlow.asStateFlow()

    private val exploreFeedFlow = MutableStateFlow(ExploreFeed())
    val exploreFeed: StateFlow<ExploreFeed> = exploreFeedFlow.asStateFlow()

    private val exploreLoadingFlow = MutableStateFlow(true)
    val exploreLoading: StateFlow<Boolean> = exploreLoadingFlow.asStateFlow()

    init {
        scope.launch {
            store.settings.collectLatest { s ->
                tube.applylocale(s.contentCountry, s.contentLanguage)
            }
        }
        scope.launch {
            store.session.collectLatest { sess ->
                tube.applysession(sess)
                streams.prewarm()
                refreshhome(selectedChipFlow.value)
            }
        }
        scope.launch {
            refreshexplore()
        }
    }

    fun refreshhome(chip: MoodChip? = selectedChipFlow.value) {
        selectedChipFlow.value = chip
        scope.launch {
            homeLoadingFlow.value = true
            homeErrorFlow.value = null
            tube.loadhome(params = chip?.params)
                .onSuccess { feed ->
                    val mergedChips = feed.chips.ifEmpty { homeFeedFlow.value.chips }
                    homeFeedFlow.value = feed.copy(chips = mergedChips)
                }
                .onFailure { err ->
                    homeErrorFlow.value = err.message ?: "Unable to load feed"
                }
            homeLoadingFlow.value = false
        }
    }

    fun morehome() {
        val cont = homeFeedFlow.value.continuation ?: return
        if (homeLoadingFlow.value) return
        scope.launch {
            tube.loadhome(continuation = cont).onSuccess { more ->
                val cur = homeFeedFlow.value
                homeFeedFlow.value = cur.copy(
                    shelves = cur.shelves + more.shelves,
                    continuation = more.continuation,
                )
            }
        }
    }

    fun refreshexplore() {
        scope.launch {
            exploreLoadingFlow.value = true
            tube.loadexplore().onSuccess { feed ->
                exploreFeedFlow.value = feed
            }
            exploreLoadingFlow.value = false
        }
    }

    fun togglelike(track: Track) {
        scope.launch {
            val currentlyLiked = track.id in likedIds.value
            val nextLiked = !currentlyLiked
            val existing = dao.gettrack(track.id)
            if (existing == null) {
                dao.puttrack(track.copy(liked = nextLiked).toentity())
            }
            dao.setliked(
                id = track.id,
                liked = nextLiked,
                at = if (nextLiked) System.currentTimeMillis() else null,
            )
            pilot.markliked(track.id, nextLiked)
            tube.likeremote(track.id, nextLiked)
        }
    }

    fun togglesavealbum(album: AlbumSummary) {
        scope.launch {
            val saved = savedAlbums.value.any { it.id == album.id }
            dao.putalbum(album.copy(saved = !saved).toentity(savedOverride = !saved))
        }
    }

    fun togglesaveartist(artist: ArtistSummary) {
        scope.launch {
            val saved = savedArtists.value.any { it.id == artist.id }
            val next = !saved
            dao.putartist(artist.copy(saved = next).toentity(savedOverride = next))
            artist.channelId?.let { tube.subscriberemote(it, next) }
        }
    }

    fun createplaylist(title: String, onCreated: (String) -> Unit = {}) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        scope.launch {
            val id = "local_${UUID.randomUUID().toString().take(8)}"
            dao.putplaylist(
                PlaylistEntity(
                    id = id,
                    title = clean,
                    author = "You",
                    artwork = "",
                    local = true,
                    editable = true,
                ),
            )
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun renameplaylist(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        scope.launch {
            val cur = dao.getplaylist(id) ?: return@launch
            dao.putplaylist(cur.copy(title = clean))
        }
    }

    fun deleteplaylist(id: String) {
        scope.launch {
            dao.clearplaylisttracks(id)
            dao.deleteplaylist(id)
        }
    }

    fun addtoplaylist(playlistId: String, track: Track) {
        scope.launch {
            if (dao.gettrack(track.id) == null) {
                dao.puttrack(track.toentity())
            }
            val pos = (dao.maxplaylistpos(playlistId) ?: -1) + 1
            dao.putplaylisttrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = pos,
                ),
            )
            val pl = dao.getplaylist(playlistId)
            if (pl != null && pl.artwork.isBlank() && track.artwork.isNotBlank()) {
                dao.putplaylist(pl.copy(artwork = track.artwork))
            }
        }
    }

    fun removefromplaylist(playlistId: String, trackId: String) {
        scope.launch {
            dao.removeplaylisttrack(playlistId, trackId)
        }
    }

    fun reorderplaylist(playlistId: String, orderedTrackIds: List<String>) {
        scope.launch {
            dao.clearplaylisttracks(playlistId)
            orderedTrackIds.forEachIndexed { idx, trackId ->
                dao.putplaylisttrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = idx,
                    ),
                )
            }
        }
    }

    suspend fun loadlocalplaylist(playlistId: String): PlaylistDetail? =
        withContext(Dispatchers.IO) {
            val entity = dao.getplaylist(playlistId) ?: return@withContext null
            val tracks = dao.playlisttracks(playlistId).map { it.totrack() }
            PlaylistDetail(
                playlist = entity.tosummary(tracks.size),
                tracks = tracks,
            )
        }

    fun exportm3u(playlistId: String, uri: Uri) {
        scope.launch {
            val detail = loadlocalplaylist(playlistId) ?: return@launch
            val body = buildString {
                appendLine("#EXTM3U")
                appendLine("#PLAYLIST:${detail.playlist.title}")
                detail.tracks.forEach { t ->
                    val artist = t.artists.joinToString(", ") { it.name }
                    appendLine("#EXTINF:${t.duration},$artist - ${t.title}")
                    appendLine("https://music.youtube.com/watch?v=${t.id}")
                }
            }
            appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(body)
            }
        }
    }

    fun importm3u(uri: Uri) {
        scope.launch {
            val lines = appContext.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readLines()
                .orEmpty()
            if (lines.isEmpty()) return@launch
            val title = lines.firstOrNull { it.startsWith("#PLAYLIST:") }
                ?.removePrefix("#PLAYLIST:")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Playlist"
            val id = "local_${UUID.randomUUID().toString().take(8)}"
            dao.putplaylist(
                PlaylistEntity(
                    id = id,
                    title = title,
                    author = "Imported",
                    artwork = "",
                    local = true,
                    editable = true,
                ),
            )
            var pendingMeta: Pair<String, String>? = null
            var pos = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    val info = trimmed.substringAfter(",", "")
                    val artist = info.substringBefore(" - ", "").trim()
                    val songTitle = info.substringAfter(" - ", info).trim()
                    pendingMeta = artist to songTitle
                } else if (trimmed.contains("watch?v=") || trimmed.contains("youtu.be/")) {
                    val videoId = when {
                        trimmed.contains("watch?v=") -> trimmed.substringAfter("watch?v=").substringBefore("&")
                        else -> trimmed.substringAfter("youtu.be/").substringBefore("?")
                    }.trim()
                    if (videoId.isNotBlank()) {
                        val existing = dao.gettrack(videoId)?.totrack()
                        val resolved = existing ?: Track(
                            id = videoId,
                            title = pendingMeta?.second?.ifBlank { videoId } ?: videoId,
                            artists = pendingMeta?.first?.takeIf { it.isNotBlank() }
                                ?.let { listOf(zuiwan.model.ArtistRef(null, it)) }
                                .orEmpty(),
                            artwork = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        )
                        dao.puttrack(resolved.toentity())
                        dao.putplaylisttrack(PlaylistTrackEntity(id, videoId, pos++))
                    }
                    pendingMeta = null
                }
            }
        }
    }

    suspend fun loadstats(window: StatsWindow): StatsSummary =
        withContext(Dispatchers.IO) {
            val since = if (window.days != null) {
                System.currentTimeMillis() - window.days * 86_400_000L
            } else {
                0L
            }
            val totalTime = dao.playtimesince(since) ?: 0L
            val totalPlays = dao.playcountsince(since)
            val topTracks = dao.toptracks(since, 15).map { row ->
                TrackStat(
                    track = row.track.totrack(),
                    plays = row.plays,
                    totalTimeMs = row.playTime,
                )
            }
            val artistBuckets = linkedMapOf<String, Triple<ArtistSummary, Int, Long>>()
            topTracks.forEach { stat ->
                val ref = stat.track.artists.firstOrNull() ?: return@forEach
                val key = ref.id ?: ref.name
                val cur = artistBuckets[key]
                val summary = cur?.first ?: ArtistSummary(
                    id = ref.id ?: key,
                    name = ref.name,
                    artwork = stat.track.artwork,
                )
                artistBuckets[key] = Triple(
                    summary,
                    (cur?.second ?: 0) + stat.plays,
                    (cur?.third ?: 0L) + stat.totalTimeMs,
                )
            }
            val topArtists = artistBuckets.values
                .sortedByDescending { it.third }
                .take(10)
                .map { (artist, plays, time) ->
                    ArtistStat(artist = artist, plays = plays, totalTimeMs = time)
                }
            StatsSummary(
                totalMinutes = totalTime / 60_000L,
                totalPlays = totalPlays,
                uniqueTracks = topTracks.size,
                uniqueArtists = topArtists.size,
                topTracks = topTracks,
                topArtists = topArtists,
            )
        }

    fun savesearch(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        scope.launch {
            dao.putsearch(SearchHistoryEntity(query = clean))
        }
    }

    fun deletesearch(query: String) {
        scope.launch { dao.deletesearch(query) }
    }

    fun clearsearches() {
        scope.launch { dao.clearsearches() }
    }

    fun clearhistory() {
        scope.launch {
            dao.clearhistory()
            dao.clearplayevents()
        }
    }

    fun savesession(next: SessionData) {
        scope.launch {
            store.updatesession { next }
            tube.applysession(next)
            val profile = tube.fetchaccount().getOrNull()
            if (profile != null) {
                store.updatesession { cur ->
                    cur.copy(
                        accountName = profile.name,
                        accountHandle = profile.handle,
                        accountEmail = profile.email,
                        accountAvatar = profile.avatar,
                    )
                }
            }
            syncaccount()
        }
    }

    fun syncaccount() {
        scope.launch {
            tube.synclibrary().onSuccess { remoteLiked ->
                val now = System.currentTimeMillis()
                remoteLiked.forEachIndexed { idx, t ->
                    val existing = dao.gettrack(t.id)
                    if (existing == null) {
                        dao.puttrack(t.copy(liked = true).toentity().copy(likedAt = now - idx))
                    } else if (!existing.liked) {
                        dao.setliked(t.id, true, now - idx)
                    }
                }
            }
        }
    }

    fun logout() {
        scope.launch {
            store.updatesession {
                SessionData(
                    lastFmUser = it.lastFmUser,
                    lastFmSession = it.lastFmSession,
                    scrobbleEnabled = it.scrobbleEnabled,
                )
            }
        }
    }
}
