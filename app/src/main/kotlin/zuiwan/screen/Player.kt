package zuiwan.screen

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.formatclock
import zuiwan.model.upscaleart
import zuiwan.sheet.QueueView
import zuiwan.sheet.SleepSheet
import zuiwan.sheet.TempoSheet
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassIconButton
import zuiwan.ui.Route
import zuiwan.ui.ThinSlider
import zuiwan.ui.ZIcons
import kotlin.math.roundToInt

private enum class PlayerPane {
    COVER,
    LYRICS,
    QUEUE,
}

@Composable
fun viewplayer(
    onCollapse: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val context = LocalContext.current

    val state by hub.pilot.state.collectAsState()
    val likedIds by hub.likedIds.collectAsState()
    val sleepState by hub.pilot.sleep.state.collectAsState()
    val eqProfile by hub.eq.collectAsState()

    val track = state.current ?: return
    val isLiked = track.id in likedIds
    val hiResArt = remember(track.artwork) { upscaleart(track.artwork, 1080) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVol = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volumeFrac by remember(track.id) {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.toFloat(),
        )
    }

    var pane by remember { mutableStateOf(PlayerPane.COVER) }
    var showLyricSearch by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showTempo by remember { mutableStateOf(false) }

    BackHandler {
        if (pane != PlayerPane.COVER) {
            pane = PlayerPane.COVER
        } else {
            onCollapse()
        }
    }

    if (showSleep) SleepSheet(onDismiss = { showSleep = false })
    if (showTempo) TempoSheet(onDismiss = { showTempo = false })

    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMs = (state.durationMs - state.positionMs).coerceAtLeast(0L)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(glass.bg),
    ) {
        val stageHeight = maxHeight * 0.62f

        ImmersiveGlassBackdrop(
            artworkUrl = hiResArt,
            stageHeight = stageHeight,
            fullBleedTop = pane == PlayerPane.COVER,
            blurEnabled = glass.blurEnabled,
            isDark = glass.isDark,
            baseBg = glass.bg,
        )

        AnimatedContent(
            targetState = pane,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "playerMode",
        ) { activePane ->
            when (activePane) {
                PlayerPane.LYRICS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ArtImage(
                                url = track.artwork,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(50.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artistline(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            GlassIconButton(
                                icon = ZIcons.Search,
                                onClick = { showLyricSearch = true },
                                size = 38.dp,
                                iconSize = 19.dp,
                            )
                            GlassIconButton(
                                icon = ZIcons.MoreHoriz,
                                onClick = { onTrackMenu(track) },
                                size = 38.dp,
                                iconSize = 19.dp,
                            )
                            GlassIconButton(
                                icon = ZIcons.Close,
                                onClick = { pane = PlayerPane.COVER },
                                size = 38.dp,
                                iconSize = 19.dp,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        viewlyrics(
                            showHeaderControls = false,
                            externalFinderOpen = showLyricSearch,
                            onDismissExternalFinder = { showLyricSearch = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        Spacer(Modifier.height(10.dp))

                        ThinSlider(
                            progress = progress,
                            onSeek = { frac ->
                                if (state.durationMs > 0L) {
                                    hub.pilot.seekto((frac * state.durationMs).toLong())
                                }
                            },
                            activeColor = glass.ink,
                            trackColor = glass.inkMuted.copy(alpha = 0.30f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = formatclock(state.positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = glass.inkSecondary,
                            )
                            Text(
                                text = "-${formatclock(remainingMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = glass.inkSecondary,
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        AppleTransportRow(
                            playing = state.playing,
                            buffering = state.buffering,
                            tint = glass.ink,
                            compact = true,
                            onPrev = { hub.pilot.skipprev() },
                            onToggle = { hub.pilot.toggleplay() },
                            onNext = { hub.pilot.skipnext() },
                        )

                        Spacer(Modifier.height(6.dp))

                        PlayerBottomBar(
                            pane = activePane,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            eqEnabled = eqProfile.enabled,
                            sleepActive = sleepState.active,
                            tempoActive = state.speed != 1f || state.pitch != 1f,
                            onToggleLyrics = { pane = PlayerPane.COVER },
                            onToggleQueue = { pane = PlayerPane.QUEUE },
                            onToggleShuffle = { hub.pilot.toggleshuffle() },
                            onCycleRepeat = { hub.pilot.cyclerepeat() },
                            onOpenEq = {
                                onCollapse()
                                onNavigate(Route.Equalizer)
                            },
                            onOpenSleep = { showSleep = true },
                            onOpenTempo = { showTempo = true },
                        )
                    }
                }

                PlayerPane.QUEUE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        ) {
                            ArtImage(
                                url = track.artwork,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(50.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { pane = PlayerPane.COVER },
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artistline(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            GlassIconButton(
                                icon = if (state.playing) ZIcons.Pause else ZIcons.Play,
                                onClick = { hub.pilot.toggleplay() },
                                size = 40.dp,
                                iconSize = 22.dp,
                            )
                            GlassIconButton(
                                icon = ZIcons.ChevronDown,
                                onClick = { pane = PlayerPane.COVER },
                                size = 40.dp,
                                iconSize = 20.dp,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        QueueView(
                            onClose = { pane = PlayerPane.COVER },
                            onTrackMenu = onTrackMenu,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        Spacer(Modifier.height(8.dp))

                        PlayerBottomBar(
                            pane = activePane,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            eqEnabled = eqProfile.enabled,
                            sleepActive = sleepState.active,
                            tempoActive = state.speed != 1f || state.pitch != 1f,
                            onToggleLyrics = { pane = PlayerPane.LYRICS },
                            onToggleQueue = { pane = PlayerPane.COVER },
                            onToggleShuffle = { hub.pilot.toggleshuffle() },
                            onCycleRepeat = { hub.pilot.cyclerepeat() },
                            onOpenEq = {
                                onCollapse()
                                onNavigate(Route.Equalizer)
                            },
                            onOpenSleep = { showSleep = true },
                            onOpenTempo = { showTempo = true },
                        )
                    }
                }

                PlayerPane.COVER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            GlassIconButton(
                                icon = ZIcons.ChevronDown,
                                onClick = onCollapse,
                                size = 40.dp,
                                iconSize = 21.dp,
                            )
                            Text(
                                text = "Playing from ${state.queueTitle}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (glass.isDark) Color.White.copy(alpha = 0.88f) else glass.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                textAlign = TextAlign.Center,
                            )
                            GlassIconButton(
                                icon = ZIcons.MoreHoriz,
                                onClick = { onTrackMenu(track) },
                                size = 40.dp,
                                iconSize = 21.dp,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 26.sp,
                                    lineHeight = 31.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .clickable(enabled = track.album?.id != null) {
                                            track.album?.id?.let { aid ->
                                                onCollapse()
                                                onNavigate(Route.Album(aid))
                                            }
                                        },
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = track.artistline(),
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = glass.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .basicMarquee()
                                        .clickable {
                                            track.artists.firstOrNull()?.id?.let { artistId ->
                                                onCollapse()
                                                onNavigate(Route.Artist(artistId))
                                            }
                                        },
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            GlassIconButton(
                                icon = if (isLiked) ZIcons.HeartFilled else ZIcons.Heart,
                                active = isLiked,
                                onClick = { hub.togglelike(track) },
                                size = 42.dp,
                                iconSize = 22.dp,
                            )
                            GlassIconButton(
                                icon = ZIcons.MoreHoriz,
                                onClick = { onTrackMenu(track) },
                                size = 42.dp,
                                iconSize = 22.dp,
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        ThinSlider(
                            progress = progress,
                            onSeek = { frac ->
                                if (state.durationMs > 0L) {
                                    hub.pilot.seekto((frac * state.durationMs).toLong())
                                }
                            },
                            activeColor = glass.ink,
                            trackColor = glass.inkMuted.copy(alpha = 0.30f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = formatclock(state.positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = glass.inkSecondary,
                            )
                            Text(
                                text = "-${formatclock(remainingMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = glass.inkSecondary,
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        AppleTransportRow(
                            playing = state.playing,
                            buffering = state.buffering,
                            tint = glass.ink,
                            compact = false,
                            onPrev = { hub.pilot.skipprev() },
                            onToggle = { hub.pilot.toggleplay() },
                            onNext = { hub.pilot.skipnext() },
                        )

                        Spacer(Modifier.height(22.dp))

                        VolumeBarRow(
                            volume = volumeFrac,
                            tint = glass.ink,
                            mutedTint = glass.inkSecondary,
                            onVolumeChange = { next ->
                                volumeFrac = next
                                val target = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            },
                        )

                        Spacer(Modifier.height(18.dp))

                        PlayerBottomBar(
                            pane = activePane,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            eqEnabled = eqProfile.enabled,
                            sleepActive = sleepState.active,
                            tempoActive = state.speed != 1f || state.pitch != 1f,
                            onToggleLyrics = { pane = PlayerPane.LYRICS },
                            onToggleQueue = { pane = PlayerPane.QUEUE },
                            onToggleShuffle = { hub.pilot.toggleshuffle() },
                            onCycleRepeat = { hub.pilot.cyclerepeat() },
                            onOpenEq = {
                                onCollapse()
                                onNavigate(Route.Equalizer)
                            },
                            onOpenSleep = { showSleep = true },
                            onOpenTempo = { showTempo = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomBar(
    pane: PlayerPane,
    shuffle: Boolean,
    repeatMode: Int,
    eqEnabled: Boolean,
    sleepActive: Boolean,
    tempoActive: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenTempo: () -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlassIconButton(
            icon = ZIcons.Lyrics,
            active = pane == PlayerPane.LYRICS,
            tint = if (pane == PlayerPane.LYRICS) glass.accent else glass.inkSecondary,
            onClick = onToggleLyrics,
            size = 42.dp,
            iconSize = 23.dp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(glass.glassElevated)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            CapsuleActionIcon(
                icon = ZIcons.Shuffle,
                active = shuffle,
                onClick = onToggleShuffle,
            )
            CapsuleActionIcon(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) ZIcons.RepeatOne else ZIcons.Repeat,
                active = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onCycleRepeat,
            )
            CapsuleActionIcon(
                icon = ZIcons.Equalizer,
                active = eqEnabled,
                onClick = onOpenEq,
            )
            CapsuleActionIcon(
                icon = ZIcons.Timer,
                active = sleepActive,
                onClick = onOpenSleep,
            )
            CapsuleActionIcon(
                icon = ZIcons.Speed,
                active = tempoActive,
                onClick = onOpenTempo,
            )
        }

        GlassIconButton(
            icon = ZIcons.Queue,
            active = pane == PlayerPane.QUEUE,
            tint = if (pane == PlayerPane.QUEUE) glass.accent else glass.inkSecondary,
            onClick = onToggleQueue,
            size = 42.dp,
            iconSize = 23.dp,
        )
    }
}

@Composable
private fun ImmersiveGlassBackdrop(
    artworkUrl: String?,
    stageHeight: Dp,
    fullBleedTop: Boolean,
    blurEnabled: Boolean,
    isDark: Boolean,
    baseBg: Color,
) {
    val hazeState = rememberHazeState()
    val blurStyle = remember(blurEnabled, isDark, baseBg) {
        HazeBlurStyle {
            blurEnabled(blurEnabled)
            blurRadius(56.dp)
            noiseFactor(0.05f)
            backgroundColor(baseBg)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(
                        baseBg.copy(alpha = if (isDark) 0.46f else 0.62f),
                    ),
                ),
            )
            fallbackColorEffect(HazeColorEffect.tint(baseBg.copy(alpha = 0.78f)))
        }
    }

    val stageFadeMask = remember {
        Brush.verticalGradient(
            0.0f to Color.Black,
            0.44f to Color.Black,
            0.62f to Color.Black.copy(alpha = 0.88f),
            0.78f to Color.Black.copy(alpha = 0.42f),
            1.0f to Color.Transparent,
        )
    }

    val lowerScrim = remember(isDark, baseBg) {
        Brush.verticalGradient(
            0.0f to baseBg.copy(alpha = if (isDark) 0.16f else 0.24f),
            0.46f to baseBg.copy(alpha = if (isDark) 0.42f else 0.58f),
            0.78f to baseBg.copy(alpha = if (isDark) 0.78f else 0.86f),
            1.0f to baseBg.copy(alpha = if (isDark) 0.92f else 0.95f),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .hazeSource(hazeState),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = 180f
                            scaleX = 1.25f
                            scaleY = 1.25f
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(baseBg),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = blurStyle,
                    performanceMode = HazePerformanceMode.Adaptive,
                    expandLayerBounds = true,
                )
                .background(lowerScrim),
        )

        if (fullBleedTop && !artworkUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = stageFadeMask,
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AppleTransportRow(
    playing: Boolean,
    buffering: Boolean,
    tint: Color,
    compact: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    val sideTarget = if (compact) 56.dp else 68.dp
    val sideIcon = if (compact) 34.dp else 44.dp
    val mainTarget = if (compact) 68.dp else 84.dp
    val mainIcon = if (compact) 44.dp else 56.dp

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(sideTarget),
        ) {
            Icon(
                imageVector = ZIcons.FastRewind,
                contentDescription = "Previous",
                tint = tint,
                modifier = Modifier.size(sideIcon),
            )
        }

        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(mainTarget),
        ) {
            if (buffering) {
                CircularProgressIndicator(
                    color = tint,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(mainIcon * 0.72f),
                )
            } else {
                Icon(
                    imageVector = if (playing) ZIcons.Pause else ZIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = tint,
                    modifier = Modifier.size(mainIcon),
                )
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(sideTarget),
        ) {
            Icon(
                imageVector = ZIcons.FastForward,
                contentDescription = "Next",
                tint = tint,
                modifier = Modifier.size(sideIcon),
            )
        }
    }
}

@Composable
private fun VolumeBarRow(
    volume: Float,
    tint: Color,
    mutedTint: Color,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = ZIcons.VolumeMute,
            contentDescription = null,
            tint = mutedTint,
            modifier = Modifier.size(18.dp),
        )
        ThinSlider(
            progress = volume,
            onSeek = onVolumeChange,
            activeColor = tint,
            trackColor = mutedTint.copy(alpha = 0.28f),
            idleThickness = 5.dp,
            activeThickness = 9.dp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = ZIcons.VolumeHigh,
            contentDescription = null,
            tint = mutedTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CapsuleActionIcon(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) glass.accentSoft else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) glass.accent else glass.inkSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
