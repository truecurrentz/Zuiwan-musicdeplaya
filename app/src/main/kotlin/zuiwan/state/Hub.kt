package zuiwan.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zuiwan.audio.AudioPilot
import zuiwan.audio.AudioVault
import zuiwan.audio.MusicRecognizer
import zuiwan.data.AppSettings
import zuiwan.data.BackupManager
import zuiwan.data.MusicDao
import zuiwan.data.MusicDb
import zuiwan.data.PlaylistEntity
import zuiwan.data.PlaylistTrackEntity
import zuiwan.data.PrefStore
import zuiwan.data.SearchHistoryEntity
import zuiwan.data.toentity
import zuiwan.data.tomatch
import zuiwan.data.tosummary
import zuiwan.data.totrack
import zuiwan.dsp.EqualizerProcessor
import zuiwan.dsp.NormalizerProcessor
import zuiwan.lyrics.LyricsExtractor
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistRef
import zuiwan.model.ArtistStat
import zuiwan.model.ArtistSummary
import zuiwan.model.EqProfile
import zuiwan.model.ExploreFeed
import zuiwan.model.HomeFeed
import zuiwan.model.MatchResult
import zuiwan.model.MoodChip
import zuiwan.model.NoticeKind
import zuiwan.model.NoticeState
import zuiwan.model.PlaylistDetail
import zuiwan.model.PlaylistSummary
import zuiwan.model.ReleaseNote
import zuiwan.model.SessionData
import zuiwan.model.StatsSummary
import zuiwan.model.StatsWindow
import zuiwan.model.Track
import zuiwan.model.TrackStat
import zuiwan.model.UpdateStatus
import zuiwan.model.trackart
import zuiwan.model.upscaleart
import app.zuiwan.BuildConfig
import zuiwan.tube.AppUpdater
import zuiwan.tube.CloudSync
import zuiwan.tube.DraftSong
import zuiwan.tube.ExportKind
import zuiwan.tube.PlaylistTransfer
import zuiwan.tube.StreamEngine
import zuiwan.tube.TubeClient
import java.io.File
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
    val sync: CloudSync = CloudSync(dao, tube)
    val transfer: PlaylistTransfer = PlaylistTransfer(appContext, dao, tube, sync)
    val recognizer: MusicRecognizer = MusicRecognizer(appContext, dao, tube)
    val updater: AppUpdater = AppUpdater(appContext, tube)
    val streams: StreamEngine = StreamEngine(appContext) { tube.innerTube }
    val lyrics: LyricsExtractor = LyricsExtractor(dao, tube)

    private val updateStateFlow = MutableStateFlow(UpdateStatus())
    val updateState: StateFlow<UpdateStatus> = updateStateFlow.asStateFlow()
    private var pendingInstallApk: File? = null

    private val noticeFlow = MutableStateFlow(NoticeState())
    val notice: StateFlow<NoticeState> = noticeFlow.asStateFlow()
    private var noticeHideJob: Job? = null

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

    val topTracks: StateFlow<List<Track>> = dao.mostplayed()
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
        dao.linkcount(),
    ) { rows, _ ->
        rows.map { row ->
            val tracks = dao.playlisttracks(row.id).map { it.totrack() }
            val fixedRowArt = upscaleart(row.artwork)
            val thumbs = buildList {
                if (fixedRowArt.isNotBlank()) add(fixedRowArt)
                tracks.forEach { t ->
                    val art = t.trackart()
                    if (art.isNotBlank() && art !in this && size < 4) {
                        add(art)
                    }
                }
            }
            val countLabel = if (row.local || tracks.isNotEmpty()) {
                "${tracks.size} songs"
            } else {
                "Playlist"
            }
            PlaylistSummary(
                id = row.id,
                title = row.title,
                author = row.author ?: if (row.local) "You" else "YouTube Music",
                countText = countLabel,
                artwork = fixedRowArt.ifBlank { thumbs.firstOrNull().orEmpty() },
                local = row.local,
                remoteId = row.remoteId,
                editable = row.editable,
                thumbnails = thumbs,
                songCount = tracks.size,
            )
        }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val searchHistory: StateFlow<List<String>> = dao.recentsearches()
        .map { rows -> rows.map { it.query } }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val recognizedHistory: StateFlow<List<MatchResult>> = dao.recognizedlist()
        .map { rows -> rows.map { it.tomatch() } }
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

    fun savealbum(album: AlbumSummary) {
        scope.launch {
            val saved = savedAlbums.value.any { it.id == album.id }
            dao.putalbum(album.copy(saved = !saved).toentity(savedOverride = !saved))
        }
    }

    fun saveartist(artist: ArtistSummary) {
        scope.launch {
            val saved = savedArtists.value.any { it.id == artist.id }
            val next = !saved
            dao.putartist(artist.copy(saved = next).toentity(savedOverride = next))
            artist.channelId?.let { tube.subscriberemote(it, next) }
        }
    }

    fun saveplaylist(playlist: PlaylistSummary) {
        scope.launch {
            val cleanRemote = (playlist.remoteId ?: playlist.id).removePrefix("VL").removePrefix("yt_")
            val existing = dao.getplaylist(playlist.id) ?: dao.getremoteplaylist(cleanRemote)
            if (existing != null) {
                dao.unlinkall(existing.id)
                dao.deleteplaylist(existing.id)
                if (!existing.local && !existing.remoteId.isNullOrBlank()) {
                    tube.likelist(existing.remoteId, false)
                }
                shownotice(NoticeKind.INFO, "Removed from Library", playlist.title)
            } else {
                val localId = "yt_$cleanRemote"
                dao.putplaylist(
                    PlaylistEntity(
                        id = localId,
                        title = playlist.title,
                        author = playlist.author ?: "YouTube Music",
                        artwork = upscaleart(playlist.artwork),
                        local = false,
                        remoteId = cleanRemote,
                        editable = playlist.editable,
                    ),
                )
                tube.likelist(cleanRemote, true)
                runCatching { sync.syncone(localId) }
                shownotice(NoticeKind.SUCCESS, "Saved to Library", playlist.title)
            }
        }
    }

    fun createplaylist(
        title: String,
        syncYoutube: Boolean = false,
        onCreated: (String) -> Unit = {},
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        scope.launch {
            if (syncYoutube && tube.isauthed()) {
                shownotice(NoticeKind.SYNC, "Creating Playlist", "Syncing $clean with YouTube Music", 0.25f)
                val remoteRes = tube.createremote(clean)
                val remoteId = remoteRes.getOrNull()
                if (!remoteId.isNullOrBlank()) {
                    val id = "yt_$remoteId"
                    val author = session.value.accountName.ifBlank { "YouTube Music" }
                    dao.putplaylist(
                        PlaylistEntity(
                            id = id,
                            title = clean,
                            author = author,
                            artwork = "",
                            local = false,
                            remoteId = remoteId,
                            editable = true,
                        ),
                    )
                    shownotice(NoticeKind.SUCCESS, "Playlist Created", "$clean synced with YouTube Music")
                    withContext(Dispatchers.Main) { onCreated(id) }
                    return@launch
                }
            }

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
            shownotice(
                kind = if (syncYoutube) NoticeKind.INFO else NoticeKind.SUCCESS,
                title = "Playlist Created",
                subtitle = if (syncYoutube) "$clean saved locally" else clean,
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
            if (!cur.remoteId.isNullOrBlank() && cur.editable) {
                tube.renameremote(cur.remoteId, clean)
            }
            shownotice(NoticeKind.SUCCESS, "Playlist Renamed", clean)
        }
    }

    fun deleteplaylist(id: String) {
        scope.launch {
            val cur = dao.getplaylist(id)
            dao.unlinkall(id)
            dao.deleteplaylist(id)
            if (cur != null && !cur.remoteId.isNullOrBlank()) {
                if (cur.editable) {
                    tube.deleteremote(cur.remoteId)
                } else {
                    tube.likelist(cur.remoteId, false)
                }
            }
            shownotice(NoticeKind.INFO, "Playlist Deleted", cur?.title.orEmpty())
        }
    }

    fun addtrack(playlistId: String, track: Track) {
        scope.launch {
            if (dao.gettrack(track.id) == null) {
                dao.puttrack(track.toentity())
            }
            val pos = (dao.maxpos(playlistId) ?: -1) + 1
            dao.linktrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = pos,
                ),
            )
            val pl = dao.getplaylist(playlistId)
            val art = track.trackart()
            if (pl != null && pl.artwork.isBlank() && art.isNotBlank()) {
                dao.putplaylist(pl.copy(artwork = art))
            }
            shownotice(NoticeKind.SUCCESS, "Added to ${pl?.title ?: "Playlist"}", track.title)
            if (pl != null && !pl.remoteId.isNullOrBlank() && pl.editable && tube.isauthed()) {
                tube.addremote(pl.remoteId, track.id)
                runCatching { sync.syncone(pl.id) }
            }
        }
    }

    fun droptrack(playlistId: String, trackId: String) {
        scope.launch {
            val pl = dao.getplaylist(playlistId)
            val setVid = dao.getsetvideo(playlistId, trackId)
            dao.unlinktrack(playlistId, trackId)
            if (pl != null && !pl.remoteId.isNullOrBlank() && pl.editable && !setVid.isNullOrBlank()) {
                tube.removeremote(pl.remoteId, trackId, setVid)
            }
        }
    }

    fun reorderplaylist(playlistId: String, orderedTrackIds: List<String>) {
        scope.launch {
            val oldLinks = dao.playlistlinks(playlistId).associateBy { it.trackId }
            dao.unlinkall(playlistId)
            orderedTrackIds.forEachIndexed { idx, trackId ->
                dao.linktrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = idx,
                        setVideoId = oldLinks[trackId]?.setVideoId,
                    ),
                )
            }
        }
    }

    suspend fun localplaylist(playlistId: String): PlaylistDetail? =
        withContext(Dispatchers.IO) {
            val entity = dao.getplaylist(playlistId)
                ?: dao.getremoteplaylist(playlistId.removePrefix("VL").removePrefix("yt_"))
                ?: return@withContext null
            val links = dao.playlistlinks(entity.id).associateBy { it.trackId }
            val tracks = dao.playlisttracks(entity.id).map { row ->
                row.totrack().copy(setVideoId = links[row.id]?.setVideoId)
            }
            val thumbs = tracks.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4)
            PlaylistDetail(
                playlist = entity.tosummary(tracks.size).copy(
                    artwork = upscaleart(entity.artwork).ifBlank { thumbs.firstOrNull().orEmpty() },
                    thumbnails = thumbs,
                    songCount = tracks.size,
                ),
                tracks = tracks,
            )
        }

    fun syncplaylist(playlistId: String, onFinished: () -> Unit = {}) {
        scope.launch {
            if (!tube.isauthed()) {
                shownotice(NoticeKind.ERROR, "YouTube Music Sync", "Sign in to YouTube Music first")
                return@launch
            }
            val pl = dao.getplaylist(playlistId)
            if (pl != null && pl.remoteId.isNullOrBlank()) {
                shownotice(NoticeKind.SYNC, "Syncing to YouTube Music", pl.title, 0.08f)
                sync.pushlocal(playlistId) { step, prog ->
                    updatenotice(NoticeKind.SYNC, "Syncing ${pl.title}", step, prog)
                }.onSuccess {
                    shownotice(NoticeKind.SUCCESS, "Synced to YouTube Music", pl.title)
                    withContext(Dispatchers.Main) { onFinished() }
                }.onFailure { err ->
                    shownotice(NoticeKind.ERROR, "Playlist Sync Failed", err.message ?: "Unable to sync playlist")
                }
            } else {
                val label = pl?.title ?: "Playlist"
                shownotice(NoticeKind.SYNC, "Syncing $label", "Fetching tracks from YouTube Music", 0.35f)
                sync.syncone(playlistId).onSuccess { count ->
                    shownotice(NoticeKind.SUCCESS, "Playlist Synced", "$count songs in $label")
                    withContext(Dispatchers.Main) { onFinished() }
                }.onFailure { err ->
                    shownotice(NoticeKind.ERROR, "Playlist Sync Failed", err.message ?: "Unable to refresh playlist")
                }
            }
        }
    }

    fun startimport(
        title: String,
        songs: List<DraftSong>,
        syncYoutube: Boolean,
        targetPlaylistId: String? = null,
        onCreated: (String) -> Unit = {},
    ) {
        scope.launch {
            shownotice(NoticeKind.LOADING, "Importing Playlist", "Preparing ${songs.size} songs", 0.04f)
            transfer.runimport(
                title = title,
                songs = songs,
                syncYoutube = syncYoutube,
                targetPlaylistId = targetPlaylistId,
                onStep = { t, sub, prog ->
                    val kind = if (t.startsWith("Syncing")) NoticeKind.SYNC else NoticeKind.LOADING
                    updatenotice(kind, t, sub, prog)
                },
            ).onSuccess { newId ->
                shownotice(NoticeKind.SUCCESS, "Playlist Imported", title)
                withContext(Dispatchers.Main) { onCreated(newId) }
            }.onFailure { err ->
                shownotice(NoticeKind.ERROR, "Import Failed", err.message ?: "Unable to import playlist")
            }
        }
    }

    fun exporturi(uri: Uri, title: String, tracks: List<Track>, kind: ExportKind) {
        scope.launch {
            shownotice(NoticeKind.LOADING, "Exporting Playlist", "${tracks.size} songs · ${kind.label}", 0.5f)
            transfer.writedoc(uri, title, tracks, kind)
                .onSuccess { count ->
                    shownotice(NoticeKind.SUCCESS, "Playlist Exported", "$count songs saved as ${kind.label}")
                }
                .onFailure { err ->
                    shownotice(NoticeKind.ERROR, "Export Failed", err.message ?: "Unable to save playlist")
                }
        }
    }

    fun exportsave(title: String, tracks: List<Track>, kind: ExportKind) {
        scope.launch {
            shownotice(NoticeKind.LOADING, "Saving Playlist", "${tracks.size} songs · ${kind.label}", 0.5f)
            transfer.savedocs(title, tracks, kind)
                .onSuccess { path ->
                    shownotice(NoticeKind.SUCCESS, "Saved to Documents", path)
                }
                .onFailure { err ->
                    shownotice(NoticeKind.ERROR, "Export Failed", err.message ?: "Unable to save playlist")
                }
        }
    }

    fun exportshare(title: String, tracks: List<Track>, kind: ExportKind) {
        scope.launch {
            transfer.sharefile(title, tracks, kind)
                .onFailure { err ->
                    shownotice(NoticeKind.ERROR, "Share Failed", err.message ?: "Unable to share playlist")
                }
        }
    }

    fun exportlist(playlistId: String, uri: Uri) {
        scope.launch {
            val detail = localplaylist(playlistId) ?: return@launch
            exporturi(uri, detail.playlist.title, detail.tracks, ExportKind.M3U)
        }
    }

    fun importlist(uri: Uri) {
        scope.launch {
            shownotice(NoticeKind.LOADING, "Reading Playlist", "Analyzing file format", 0.05f)
            transfer.inspecturi(uri)
                .onSuccess { bundle ->
                    startimport(
                        title = bundle.title,
                        songs = bundle.songs,
                        syncYoutube = false,
                    )
                }
                .onFailure { err ->
                    shownotice(NoticeKind.ERROR, "Import Failed", err.message ?: "Unsupported playlist file")
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
            val totalTime = dao.timesince(since) ?: 0L
            val totalPlays = dao.countsince(since)
            val topTracks = dao.toptracks(since, 15).map { row ->
                TrackStat(
                    track = row.track.totrack(),
                    plays = row.plays,
                    totalTimeMs = row.playTime,
                )
            }
            val savedArtistMap = savedArtists.value.associateBy { it.id }
            val artistBuckets = linkedMapOf<String, Triple<ArtistSummary, Int, Long>>()
            topTracks.forEach { stat ->
                val ref = stat.track.artists.firstOrNull() ?: return@forEach
                val key = ref.id ?: ref.name
                val cur = artistBuckets[key]
                val savedArt = ref.id?.let { savedArtistMap[it]?.artwork }.orEmpty()
                val summary = cur?.first ?: ArtistSummary(
                    id = ref.id ?: key,
                    name = ref.name,
                    artwork = savedArt.ifBlank { stat.track.trackart() },
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
            dao.clearevents()
            shownotice(NoticeKind.INFO, "History Cleared")
        }
    }

    fun dropmatch(id: String) {
        scope.launch { dao.deleterecognized(id) }
    }

    fun clearmatches() {
        scope.launch {
            dao.clearrecognized()
            shownotice(NoticeKind.INFO, "Recognition History Cleared")
        }
    }

    fun shownotice(
        kind: NoticeKind,
        title: String,
        subtitle: String = "",
        progress: Float? = null,
        actionLabel: String = "",
        onAction: (() -> Unit)? = null,
    ) {
        noticeHideJob?.cancel()
        val id = System.nanoTime()
        noticeFlow.value = NoticeState(
            id = id,
            kind = kind,
            title = title,
            subtitle = subtitle,
            progress = progress,
            visible = true,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        if (kind != NoticeKind.SYNC && kind != NoticeKind.LOADING) {
            val holdMs = if (kind == NoticeKind.UPDATE) 5_800L else 3_300L
            noticeHideJob = scope.launch {
                delay(holdMs)
                if (noticeFlow.value.id == id) {
                    noticeFlow.value = noticeFlow.value.copy(visible = false)
                }
            }
        }
    }

    fun updatenotice(
        kind: NoticeKind,
        title: String,
        subtitle: String = "",
        progress: Float? = null,
    ) {
        noticeHideJob?.cancel()
        val currentId = noticeFlow.value.id.takeIf { it != 0L && noticeFlow.value.visible } ?: System.nanoTime()
        noticeFlow.value = NoticeState(
            id = currentId,
            kind = kind,
            title = title,
            subtitle = subtitle,
            progress = progress,
            visible = true,
        )
    }

    fun hidenotice() {
        noticeHideJob?.cancel()
        noticeFlow.value = noticeFlow.value.copy(visible = false)
    }

    fun savesession(next: SessionData) {
        scope.launch {
            val clean = next.copy(
                dataSyncId = next.dataSyncId.substringBefore("||"),
                authUser = next.authUser.ifBlank { "0" },
            )
            store.updatesession { clean }
            tube.applysession(clean)
            tube.ensurevisitor()
            shownotice(NoticeKind.SYNC, "YouTube Music Connected", "Loading your profile", 0.04f)
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
            refreshhome(selectedChipFlow.value)
            syncaccount()
        }
    }

    fun syncaccount() {
        scope.launch {
            if (!tube.isauthed()) {
                shownotice(NoticeKind.ERROR, "YouTube Music Sync", "Sign in to YouTube Music first")
                return@launch
            }
            if (sync.syncing.value) return@launch
            shownotice(NoticeKind.SYNC, "Syncing YouTube Music", "Connecting to your library", 0.05f)
            if (session.value.accountName.isBlank()) {
                tube.fetchaccount().getOrNull()?.let { profile ->
                    store.updatesession { cur ->
                        cur.copy(
                            accountName = profile.name,
                            accountHandle = profile.handle,
                            accountEmail = profile.email,
                            accountAvatar = profile.avatar,
                        )
                    }
                }
            }
            sync.syncall { title, sub, prog ->
                updatenotice(NoticeKind.SYNC, title, sub, prog)
            }.onSuccess { report ->
                val summary = buildList {
                    add("${report.likedCount} songs")
                    add("${report.playlistCount} playlists")
                    if (report.albumCount > 0) add("${report.albumCount} albums")
                    if (report.artistCount > 0) add("${report.artistCount} artists")
                }.joinToString(" · ")
                shownotice(
                    kind = NoticeKind.SUCCESS,
                    title = "YouTube Music Synced",
                    subtitle = summary,
                )
            }.onFailure { err ->
                shownotice(
                    kind = NoticeKind.ERROR,
                    title = "Sync Interrupted",
                    subtitle = err.message ?: "Unable to sync YouTube Music",
                )
            }
        }
    }

    fun logout() {
        scope.launch {
            store.updatesession { SessionData() }
            tube.applysession(SessionData())
            shownotice(NoticeKind.INFO, "Signed Out", "YouTube Music disconnected")
        }
    }

    fun loadchangelog(force: Boolean = false) {
        scope.launch {
            val curSettings = settings.value
            val res = updater.loadreleases(
                force = force,
                includePre = curSettings.nightlyUpdates,
            )
            res.onSuccess { list ->
                updateStateFlow.value = updateStateFlow.value.copy(
                    releases = list,
                    error = null,
                )
            }
        }
    }

    fun runupdater(force: Boolean = false, manual: Boolean = false) {
        if (updateStateFlow.value.checking) return
        scope.launch {
            val curSettings = runCatching { store.settings.first() }.getOrElse { settings.value }

            if (!manual && !curSettings.checkUpdates) {
                updateStateFlow.value = updateStateFlow.value.copy(
                    checking = false,
                    hasUpdate = false,
                )
                updater.clearalert()
                return@launch
            }

            updateStateFlow.value = updateStateFlow.value.copy(checking = true, error = null)

            val result = updater.checkupdate(
                force = force || manual,
                includeNightly = curSettings.nightlyUpdates,
            )

            result.onSuccess { (release, hasNew) ->
                val list = updater.loadreleases(
                    force = false,
                    includePre = curSettings.nightlyUpdates,
                ).getOrDefault(emptyList())

                val prev = updateStateFlow.value
                updateStateFlow.value = prev.copy(
                    checking = false,
                    hasUpdate = hasNew && release != null,
                    latest = release,
                    releases = list,
                    lastCheckedAt = System.currentTimeMillis(),
                    error = null,
                )

                if (hasNew && release != null) {
                    if (manual || (curSettings.updateNotifications && release.tagName != curSettings.lastNotifiedTag)) {
                        if (updater.postalert(release)) {
                            store.updatesettings { it.copy(lastNotifiedTag = release.tagName) }
                        }
                    }
                } else {
                    updater.clearalert()
                }
            }.onFailure { err ->
                val msg = err.message ?: "Unable to check for updates"
                updateStateFlow.value = updateStateFlow.value.copy(
                    checking = false,
                    error = msg,
                )
            }
        }
    }

    fun resumepending() {
        val pending = pendingInstallApk ?: return
        if (!updater.caninstall()) return
        pendingInstallApk = null
        updater.launchapk(pending)
    }

    fun installupdate(target: ReleaseNote? = null) {
        val release = target ?: updateStateFlow.value.latest ?: return
        if (updateStateFlow.value.downloading) return

        if (!updater.isdirectapk(release.downloadUrl)) {
            updater.openbrowser(release.downloadUrl)
            return
        }

        val curState = updateStateFlow.value
        val existing = curState.readyApkPath
            ?.takeIf { curState.readyTag == release.tagName }
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }

        if (existing != null) {
            if (!updater.caninstall()) {
                pendingInstallApk = existing
                updater.opensources()
                return
            }
            updater.launchapk(existing)
            return
        }

        scope.launch {
            updateStateFlow.value = updateStateFlow.value.copy(
                downloading = true,
                downloadProgress = 0.02f,
                error = null,
            )
            updater.postprogress(release, 0.02f)

            updater.downloadapk(release) { prog ->
                updateStateFlow.value = updateStateFlow.value.copy(
                    downloading = true,
                    downloadProgress = prog,
                )
                updater.postprogress(release, prog)
            }.onSuccess { apkFile ->
                updateStateFlow.value = updateStateFlow.value.copy(
                    downloading = false,
                    downloadProgress = 1f,
                    readyTag = release.tagName,
                    readyApkPath = apkFile.absolutePath,
                    error = null,
                )
                updater.clearalert()

                if (!updater.caninstall()) {
                    pendingInstallApk = apkFile
                    updater.opensources()
                } else {
                    updater.launchapk(apkFile)
                }
            }.onFailure { err ->
                val msg = err.message ?: "Download failed"
                updateStateFlow.value = updateStateFlow.value.copy(
                    downloading = false,
                    downloadProgress = 0f,
                    error = msg,
                )
                updater.clearalert()
            }
        }
    }
}
