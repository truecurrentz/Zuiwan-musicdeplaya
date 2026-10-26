package zuiwan.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zuiwan.Activity
import zuiwan.App

@UnstableApi
class AudioService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

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
                context: android.content.Context,
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
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
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

                override fun onPlayerError(error: PlaybackException) {
                    val mediaId = player.currentMediaItem?.mediaId ?: return
                    if (mediaId.startsWith("local_")) return
                    val activeClient = hub.vault.activeSpec.value?.client
                    if (activeClient != null) {
                        hub.streams.markfailed(mediaId, activeClient)
                    } else {
                        hub.streams.dropstream(mediaId)
                    }
                    if (player.mediaItemCount > 0) {
                        player.prepare()
                        player.play()
                    }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = mediaSession?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
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
