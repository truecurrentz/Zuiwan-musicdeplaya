package zuiwan.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.zuiwan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zuiwan.Activity
import zuiwan.App
import zuiwan.model.NoticeKind
import zuiwan.state.Hub

@UnstableApi
class AudioService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    private var watchedMediaId: String? = null
    private var fallbackCount: Int = 0
    private var trackReachedReady: Boolean = false
    private var announceSwitch: Boolean = false
    private var lastSwitchAtMs: Long = 0L
    private var stallJob: Job? = null
    private var endJob: Job? = null
    private var skipStreak: Int = 0
    private val rebufferTimes = ArrayDeque<Long>()

    override fun onCreate() {
        super.onCreate()
        val hub = (application as App).hub

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel)
                .build()
                .apply { setSmallIcon(R.drawable.note) },
        )

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            arrayOf(hub.normalizer, hub.equalizer, hub.effects, hub.spectrum),
                            SilenceSkippingAudioProcessor(),
                            SonicAudioProcessor(),
                        ),
                    )
                    .build()
        }

        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            hub.vault.playbacksource(hub.normalizer),
            extractors,
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 90_000, 1_200, 2_000)
            .build()

        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) {
                        hub.spectrum.decayframe()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val nextId = mediaItem?.mediaId
                    if (nextId != watchedMediaId) {
                        watchedMediaId = nextId
                        fallbackCount = 0
                        trackReachedReady = false
                        announceSwitch = false
                        rebufferTimes.clear()
                        stallJob?.cancel()
                        endJob?.cancel()
                    }
                    syncwatchdog(hub)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onended(hub)
                        return
                    }
                    syncwatchdog(hub)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    syncwatchdog(hub)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val mediaId = player.currentMediaItem?.mediaId ?: return
                    if (nearend()) {
                        onended(hub)
                        return
                    }
                    if (mediaId.startsWith("local_")) {
                        skipbroken(hub)
                        return
                    }
                    triggerfallback(hub, mediaId, isStall = false)
                }
            },
        )

        serviceScope.launch {
            hub.store.settings.collectLatest { prefs ->
                val pluginSkip = "silence_trim" in prefs.enabledPlugins
                player.skipSilenceEnabled = prefs.skipSilence || pluginSkip
                hub.normalizer.enabled = prefs.normalizeAudio
            }
        }

        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Activity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(launchIntent)
            .build()

        hub.pilot.attachplayer(player)
    }

    private fun syncwatchdog(hub: Hub) {
        val item = player.currentMediaItem
        val mediaId = item?.mediaId
        val scheme = item?.localConfiguration?.uri?.scheme?.lowercase().orEmpty()
        if (mediaId == null || mediaId.startsWith("local_") || scheme == "content" || scheme == "file") {
            stallJob?.cancel()
            endJob?.cancel()
            return
        }

        when (player.playbackState) {
            Player.STATE_READY -> {
                stallJob?.cancel()
                endJob?.cancel()
                trackReachedReady = true
                skipStreak = 0
                val activeProvider = hub.streams.activeProvider.value
                hub.streams.markhealthy(activeProvider)
                if (announceSwitch) {
                    announceSwitch = false
                    hub.shownotice(
                        NoticeKind.SUCCESS,
                        "Switched to ${activeProvider.label}",
                        "Streaming smoothly on backup provider",
                    )
                }
            }

            Player.STATE_BUFFERING -> {
                if (!player.playWhenReady) {
                    stallJob?.cancel()
                    return
                }
                if (nearend()) {
                    scheduleend(hub)
                    return
                }
                if (!hub.settings.value.autoFallback || !isonline()) {
                    stallJob?.cancel()
                    return
                }
                if (mediaId in hub.downloadedIds.value) {
                    stallJob?.cancel()
                    return
                }

                val now = System.currentTimeMillis()
                if (trackReachedReady && now - lastSwitchAtMs > 3_500L) {
                    while (rebufferTimes.isNotEmpty() && now - rebufferTimes.first() > 25_000L) {
                        rebufferTimes.removeFirst()
                    }
                    rebufferTimes.addLast(now)
                    if (rebufferTimes.size >= 3) {
                        rebufferTimes.clear()
                        triggerfallback(hub, mediaId, isStall = true)
                        return
                    }
                }

                if (stallJob?.isActive == true) return
                val startPos = player.currentPosition.coerceAtLeast(0L)
                val startBuf = player.bufferedPosition.coerceAtLeast(0L)
                val waitMs = if (trackReachedReady) 4_200L else 5_500L

                stallJob = serviceScope.launch {
                    delay(waitMs)
                    val curId = player.currentMediaItem?.mediaId
                    if (curId != mediaId) return@launch
                    if (player.playbackState != Player.STATE_BUFFERING || !player.playWhenReady) return@launch
                    if (!hub.settings.value.autoFallback || !isonline()) return@launch
                    val curPos = player.currentPosition.coerceAtLeast(0L)
                    val curBuf = player.bufferedPosition.coerceAtLeast(0L)
                    if (curPos <= startPos + 300L && curBuf <= startBuf + 1_500L) {
                        if (nearend()) {
                            onended(hub)
                        } else {
                            triggerfallback(hub, mediaId, isStall = true)
                        }
                    }
                }
            }

            else -> {
                stallJob?.cancel()
            }
        }
    }

    private fun remainingms(): Long {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return -1L
        return (duration - player.currentPosition).coerceAtLeast(0L)
    }

    private fun nearend(): Boolean {
        val remaining = remainingms()
        return remaining >= 0L && remaining <= 2_800L
    }

    private fun skipbroken(hub: Hub) {
        if (skipStreak >= 3) {
            player.pause()
            hub.shownotice(
                NoticeKind.ERROR,
                "Playback Unavailable",
                "Several tracks could not be streamed. Check your connection",
            )
            return
        }
        skipStreak += 1
        onended(hub)
    }

    private fun scheduleend(hub: Hub) {
        if (endJob?.isActive == true) return
        endJob = serviceScope.launch {
            delay(1_400L)
            if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady && nearend()) {
                onended(hub)
            }
        }
    }

    private fun onended(hub: Hub) {
        stallJob?.cancel()
        endJob?.cancel()
        rebufferTimes.clear()
        lastSwitchAtMs = System.currentTimeMillis()
        runCatching {
            val canAdvance = player.mediaItemCount > 1 && player.hasNextMediaItem()
            when {
                canAdvance -> {
                    player.seekToNextMediaItem()
                    player.play()
                }

                player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0 -> {
                    player.seekTo(0, 0L)
                    player.play()
                }

                else -> player.pause()
            }
        }
        syncwatchdog(hub)
    }

    private fun triggerfallback(
        hub: Hub,
        mediaId: String,
        isStall: Boolean,
    ) {
        stallJob?.cancel()
        if (nearend()) {
            onended(hub)
            return
        }
        if (fallbackCount >= 6) {
            skipbroken(hub)
            return
        }
        fallbackCount += 1
        lastSwitchAtMs = System.currentTimeMillis()
        rebufferTimes.clear()

        val activeSpec = hub.vault.activeSpec.value?.takeIf { it.id == mediaId }
            ?: hub.streams.peekstream(mediaId)?.spec
        val curProvider = activeSpec?.provider ?: hub.streams.activeProvider.value

        if (hub.settings.value.autoFallback || !isStall) {
            hub.streams.markstalled(mediaId, curProvider, activeSpec?.client)
        } else {
            hub.streams.dropstream(mediaId)
        }
        hub.vault.evictstream(mediaId)

        if (isStall && hub.settings.value.autoFallback) {
            announceSwitch = true
            hub.shownotice(
                NoticeKind.INFO,
                "Switching Music Provider",
                "Buffering on ${curProvider.label} - Finding faster mirror",
            )
        } else if (!isStall && hub.settings.value.autoFallback && fallbackCount >= 2) {
            announceSwitch = true
        }

        if (player.mediaItemCount > 0) {
            val idx = player.currentMediaItemIndex
            val resumePos = player.currentPosition.coerceAtLeast(0L)
            player.stop()
            if (idx in 0 until player.mediaItemCount) {
                player.seekTo(idx, resumePos)
            }
            player.prepare()
            player.play()
        }
    }

    private fun isonline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = mediaSession?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stallJob?.cancel()
        endJob?.cancel()
        val hub = (application as App).hub
        hub.pilot.detachplayer(player)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
