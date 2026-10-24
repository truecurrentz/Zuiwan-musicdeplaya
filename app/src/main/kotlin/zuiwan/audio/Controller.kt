package zuiwan.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zuiwan.data.MusicDao
import zuiwan.data.PlayEventEntity
import zuiwan.data.PrefStore
import zuiwan.data.QueueEntity
import zuiwan.data.toentity
import zuiwan.data.totrack
import zuiwan.dsp.EqualizerProcessor
import zuiwan.lyrics.LyricsExtractor
import zuiwan.model.EqProfile
import zuiwan.model.LyricPayload
import zuiwan.model.Track
import zuiwan.model.upscaleart
import zuiwan.tube.TubeClient
import kotlin.math.abs

data class PlaybackState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val queueTitle: String = "Queue",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
)

@UnstableApi
class AudioPilot(
    private val context: Context,
    private val dao: MusicDao,
    private val store: PrefStore,
    private val tube: TubeClient,
    private val lyricsExtractor: LyricsExtractor,
    private val equalizer: EqualizerProcessor,
    private val vault: AudioVault,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val book = QueueBook()
    val sleep = SleepTimer(scope) { pause() }

    private var controller: MediaController? = null
    private var directPlayer: ExoPlayer? = null
    private var pendingAction: ((Player) -> Unit)? = null

    private var lyricsJob: Job? = null
    private var radioJob: Job? = null
    private var activePlayStartMs: Long = 0L
    private var activeLoggedTrackId: String? = null
    @Volatile
    private var latestSettings = zuiwan.data.AppSettings()

    private val stateFlow = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = stateFlow.asStateFlow()

    private val lyricsFlow = MutableStateFlow(LyricPayload())
    val lyrics: StateFlow<LyricPayload> = lyricsFlow.asStateFlow()

    private val lyricsLoadingFlow = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = lyricsLoadingFlow.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncstate(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordlisten()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                if (sleep.trackended()) return
            }
            activePlayStartMs = System.currentTimeMillis()
            activeLoggedTrackId = mediaItem?.mediaId
            syncstate(activeplayer() ?: return)
            val current = stateFlow.value.current
            if (current != null) {
                scope.launch(Dispatchers.IO) {
                    val existing = dao.gettrack(current.id)
                    if (existing == null) dao.puttrack(current.toentity())
                }
                refreshlyrics(current, force = false)
                autofill()
            }
            persistqueue()
        }
    }

    init {
        connectservice()
        startclock()
        scope.launch(Dispatchers.IO) {
            store.settings.collect { latestSettings = it }
        }
        scope.launch(Dispatchers.IO) {
            val savedEq = store.eq.first()
            equalizer.applyeq(savedEq)
            restorequeue()
        }
    }

    private fun connectservice() {
        val token = SessionToken(context, ComponentName(context, AudioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val ctrl = future.get()
                    controller = ctrl
                    ctrl.addListener(playerListener)
                    pendingAction?.let { act ->
                        pendingAction = null
                        act(ctrl)
                    }
                    syncstate(ctrl)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun attachplayer(player: ExoPlayer) {
        directPlayer = player
    }

    fun detachplayer(player: ExoPlayer) {
        if (directPlayer === player) {
            recordlisten()
            directPlayer = null
        }
    }

    private fun activeplayer(): Player? = controller ?: directPlayer

    private fun withplayer(block: (Player) -> Unit) {
        val p = activeplayer()
        if (p != null) {
            block(p)
        } else {
            pendingAction = block
            connectservice()
        }
    }

    private fun startclock() {
        scope.launch {
            while (isActive) {
                val p = activeplayer()
                if (p != null) {
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration.takeIf { it > 0L } ?: ((stateFlow.value.current?.duration ?: 0) * 1_000L)
                    crossfade(p, pos, dur)
                    stateFlow.value = stateFlow.value.copy(
                        playing = p.isPlaying,
                        buffering = p.playbackState == Player.STATE_BUFFERING,
                        positionMs = pos,
                        durationMs = dur,
                    )
                }
                delay(200L)
            }
        }
    }

    private fun crossfade(player: Player, posMs: Long, durMs: Long) {
        val fadeSec = latestSettings.crossfadeSec
        if (fadeSec <= 0 || durMs <= 8_000L || !player.hasNextMediaItem()) {
            if (player.volume != 1f) player.volume = 1f
            return
        }
        val windowMs = fadeSec * 1_000L
        val targetVol = when {
            posMs < windowMs / 2 -> (posMs.toFloat() / (windowMs / 2f)).coerceIn(0.25f, 1f)
            durMs - posMs in 1..windowMs -> ((durMs - posMs).toFloat() / windowMs.toFloat()).coerceIn(0.15f, 1f)
            else -> 1f
        }
        if (abs(player.volume - targetVol) > 0.03f) {
            player.volume = targetVol
        }
    }

    fun audioplay(
        track: Track,
        contextTitle: String = track.title,
        fetchRadio: Boolean = false,
    ) {
        playtracks(
            tracks = listOf(track),
            startIndex = 0,
            title = contextTitle,
            fetchRadio = fetchRadio,
        )
    }

    fun playtracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        title: String = "Queue",
        shuffleStart: Boolean = false,
        fetchRadio: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        radioJob?.cancel()
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        book.register(tracks)
        book.setcontext(title = title, nextToken = null, seedId = tracks[safeIndex].id)
        val items = tracks.map(book::mediaitem)

        withplayer { p ->
            p.shuffleModeEnabled = shuffleStart
            p.setMediaItems(items, safeIndex, 0L)
            p.prepare()
            p.play()
            syncstate(p)
        }

        val current = tracks[safeIndex]
        activePlayStartMs = System.currentTimeMillis()
        activeLoggedTrackId = current.id
        refreshlyrics(current, force = false)

        if (fetchRadio || latestSettings.autoRadio) {
            loadradio(current.id, force = fetchRadio)
        }
        persistqueue()
    }

    fun playnext(track: Track) {
        book.register(track)
        withplayer { p ->
            val insertAt = if (p.mediaItemCount == 0) 0 else (p.currentMediaItemIndex + 1).coerceAtMost(p.mediaItemCount)
            p.addMediaItem(insertAt, book.mediaitem(track))
            if (p.mediaItemCount == 1) {
                p.prepare()
                p.play()
            }
            syncstate(p)
            persistqueue()
        }
    }

    fun enqueue(track: Track) {
        book.register(track)
        withplayer { p ->
            p.addMediaItem(book.mediaitem(track))
            if (p.mediaItemCount == 1) {
                p.prepare()
                p.play()
            }
            syncstate(p)
            persistqueue()
        }
    }

    fun enqueuemany(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        book.register(tracks)
        withplayer { p ->
            p.addMediaItems(tracks.map(book::mediaitem))
            syncstate(p)
            persistqueue()
        }
    }

    fun jumpqueue(index: Int) {
        withplayer { p ->
            if (index in 0 until p.mediaItemCount) {
                p.seekToDefaultPosition(index)
                p.play()
                syncstate(p)
            }
        }
    }

    fun movequeue(from: Int, to: Int) {
        withplayer { p ->
            if (from in 0 until p.mediaItemCount && to in 0 until p.mediaItemCount && from != to) {
                p.moveMediaItem(from, to)
                syncstate(p)
                persistqueue()
            }
        }
    }

    fun removequeue(index: Int) {
        withplayer { p ->
            if (index in 0 until p.mediaItemCount) {
                p.removeMediaItem(index)
                syncstate(p)
                persistqueue()
            }
        }
    }

    fun clearupcoming() {
        radioJob?.cancel()
        book.continuation = null
        withplayer { p ->
            val cur = p.currentMediaItemIndex
            if (cur >= 0 && cur + 1 < p.mediaItemCount) {
                p.removeMediaItems(cur + 1, p.mediaItemCount)
            }
            if (cur > 0) {
                p.removeMediaItems(0, cur)
            }
            syncstate(p)
            persistqueue()
        }
    }

    fun clearqueue() {
        radioJob?.cancel()
        book.continuation = null
        withplayer { p ->
            p.stop()
            p.clearMediaItems()
            syncstate(p)
            scope.launch(Dispatchers.IO) { dao.clearqueue() }
        }
    }

    fun toggleplay() {
        withplayer { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                    if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
                    p.prepare()
                }
                p.play()
            }
            syncstate(p)
        }
    }

    fun pause() {
        withplayer { p ->
            p.pause()
            syncstate(p)
        }
    }

    fun seekto(positionMs: Long) {
        withplayer { p ->
            p.seekTo(positionMs.coerceAtLeast(0L))
            stateFlow.value = stateFlow.value.copy(positionMs = positionMs.coerceAtLeast(0L))
        }
    }

    fun skipnext() {
        withplayer { p ->
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
            } else if (p.mediaItemCount > 0) {
                p.seekTo(0, 0L)
            }
            syncstate(p)
        }
    }

    fun skipprev() {
        withplayer { p ->
            if (p.currentPosition > 3_500L || !p.hasPreviousMediaItem()) {
                p.seekTo(0L)
            } else {
                p.seekToPreviousMediaItem()
            }
            syncstate(p)
        }
    }

    fun toggleshuffle() {
        withplayer { p ->
            p.shuffleModeEnabled = !p.shuffleModeEnabled
            syncstate(p)
        }
    }

    fun cyclerepeat() {
        withplayer { p ->
            p.repeatMode = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            syncstate(p)
        }
    }

    fun settempo(speed: Float, pitch: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 2.5f)
        val safePitch = pitch.coerceIn(0.25f, 2.5f)
        withplayer { p ->
            p.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
            syncstate(p)
        }
        scope.launch(Dispatchers.IO) {
            store.updatesettings { it.copy(speed = safeSpeed, pitch = safePitch) }
        }
    }

    fun seteq(profile: EqProfile) {
        equalizer.applyeq(profile)
        scope.launch(Dispatchers.IO) {
            store.saveeq(profile)
        }
    }

    fun refreshlyrics(track: Track? = stateFlow.value.current, force: Boolean = false) {
        val target = track ?: return
        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            lyricsLoadingFlow.value = true
            val prefs = store.settings.first()
            val payload = lyricsExtractor.extractlyrics(
                track = target,
                romanize = prefs.romanizeLyrics,
                forceRefresh = force,
            )
            lyricsFlow.value = payload
            lyricsLoadingFlow.value = false
        }
    }

    fun markliked(trackId: String, liked: Boolean) {
        book.lookup(trackId)?.let { existing ->
            book.register(existing.copy(liked = liked))
        }
        val cur = stateFlow.value
        stateFlow.value = cur.copy(
            current = cur.current?.let { if (it.id == trackId) it.copy(liked = liked) else it },
            queue = cur.queue.map { if (it.id == trackId) it.copy(liked = liked) else it },
        )
    }

    private fun loadradio(videoId: String, force: Boolean = false) {
        radioJob?.cancel()
        radioJob = scope.launch(Dispatchers.IO) {
            if (!force) {
                val prefs = store.settings.first()
                if (!prefs.autoRadio) return@launch
            }
            val bundle = tube.loadnext(videoId = videoId).getOrNull() ?: return@launch
            book.continuation = bundle.continuation
            val extras = bundle.tracks.filter { it.id != videoId }
            if (extras.isEmpty()) return@launch
            scope.launch(Dispatchers.Main) {
                val p = activeplayer() ?: return@launch
                val existingIds = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
                val fresh = extras.filterNot { it.id in existingIds }
                if (fresh.isNotEmpty()) {
                    book.register(fresh)
                    p.addMediaItems(fresh.map(book::mediaitem))
                    syncstate(p)
                    persistqueue()
                }
            }
        }
    }

    private fun autofill() {
        val p = activeplayer() ?: return
        val remaining = p.mediaItemCount - p.currentMediaItemIndex - 1
        if (remaining > 3) return
        val cont = book.continuation
        val curId = stateFlow.value.current?.id ?: return
        scope.launch(Dispatchers.IO) {
            val prefs = store.settings.first()
            if (!prefs.autoRadio) return@launch
            val bundle = if (!cont.isNullOrBlank()) {
                tube.loadnext(continuation = cont).getOrNull()
            } else {
                tube.loadnext(videoId = curId).getOrNull()
            } ?: return@launch
            book.continuation = bundle.continuation
            scope.launch(Dispatchers.Main) {
                val active = activeplayer() ?: return@launch
                val ids = (0 until active.mediaItemCount).map { active.getMediaItemAt(it).mediaId }.toSet()
                val fresh = bundle.tracks.filterNot { it.id in ids }
                if (fresh.isNotEmpty()) {
                    book.register(fresh)
                    active.addMediaItems(fresh.map(book::mediaitem))
                    syncstate(active)
                }
            }
        }
    }

    private fun syncstate(player: Player) {
        val count = player.mediaItemCount
        val items = (0 until count).mapNotNull { idx ->
            val mi = player.getMediaItemAt(idx)
            (mi.localConfiguration?.tag as? Track)
                ?: book.lookup(mi.mediaId)
                ?: Track(
                    id = mi.mediaId,
                    title = mi.mediaMetadata.title?.toString() ?: "Track",
                    artwork = upscaleart(mi.mediaMetadata.artworkUri?.toString().orEmpty())
                        .ifBlank { "https://i.ytimg.com/vi/${mi.mediaId}/hqdefault.jpg" },
                )
        }
        val curIndex = player.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val curTrack = items.getOrNull(curIndex)
        val dur = player.duration.takeIf { it > 0L } ?: ((curTrack?.duration ?: 0) * 1_000L)
        stateFlow.value = PlaybackState(
            current = curTrack,
            queue = items,
            currentIndex = curIndex,
            queueTitle = book.queueTitle.value,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = dur,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            speed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
        )
    }

    private fun recordlisten() {
        val id = activeLoggedTrackId ?: return
        val started = activePlayStartMs.takeIf { it > 0L } ?: return
        val playedMs = System.currentTimeMillis() - started
        activePlayStartMs = 0L
        if (playedMs < 15_000L) return

        val track = book.lookup(id)
        scope.launch(Dispatchers.IO) {
            val prefs = store.settings.first()
            if (prefs.pauseHistory) return@launch
            if (track != null && dao.gettrack(id) == null) {
                dao.puttrack(track.toentity())
            }
            val now = System.currentTimeMillis()
            dao.recordplay(id, playedMs, now)
            dao.addevent(PlayEventEntity(trackId = id, timestamp = now, playTimeMs = playedMs))
        }
    }

    private fun persistqueue() {
        val snap = stateFlow.value
        scope.launch(Dispatchers.IO) {
            val prefs = store.settings.first()
            if (!prefs.persistentQueue) return@launch
            dao.clearqueue()
            if (snap.queue.isEmpty()) return@launch
            snap.queue.forEach { t ->
                if (dao.gettrack(t.id) == null) dao.puttrack(t.toentity())
            }
            dao.savequeue(
                snap.queue.mapIndexed { idx, t ->
                    QueueEntity(
                        slot = idx,
                        trackId = t.id,
                        queueTitle = snap.queueTitle,
                        activeIndex = snap.currentIndex,
                    )
                },
            )
        }
    }

    private suspend fun restorequeue() {
        val prefs = store.settings.first()
        if (!prefs.persistentQueue) return
        val rows = dao.loadqueue()
        if (rows.isEmpty()) return
        val tracks = rows.mapNotNull { dao.gettrack(it.trackId)?.totrack() }
        if (tracks.isEmpty()) return
        val startIdx = rows.first().activeIndex.coerceIn(0, tracks.lastIndex)
        val qTitle = rows.first().queueTitle
        book.register(tracks)
        book.setcontext(qTitle)
        scope.launch(Dispatchers.Main) {
            withplayer { p ->
                if (p.mediaItemCount == 0) {
                    p.setMediaItems(tracks.map(book::mediaitem), startIdx, 0L)
                    p.prepare()
                    syncstate(p)
                    refreshlyrics(tracks[startIdx], force = false)
                }
            }
        }
    }
}
