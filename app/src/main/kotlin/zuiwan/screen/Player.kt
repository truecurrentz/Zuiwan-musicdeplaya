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
import androidx.compose.runtime.LaunchedEffect
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
import zuiwan.model.fallbackart
import zuiwan.model.formatclock
import zuiwan.model.trackart
import zuiwan.sheet.queueview
import zuiwan.sheet.sleepsheet
import zuiwan.sheet.temposheet
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.artimage
import zuiwan.ui.iconbtn
import zuiwan.ui.reactivevisual
import zuiwan.ui.Route
import zuiwan.ui.thinslider
import zuiwan.ui.ZIcons
import java.io.File
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
    val glassTokens = LocalGlass.current
    val context = LocalContext.current

    val state by hub.pilot.state.collectAsState()
    val likedIds by hub.likedIds.collectAsState()
    val sleepState by hub.pilot.sleep.state.collectAsState()
    val eqProfile by hub.eq.collectAsState()
    val fxProfile by hub.audioEffects.collectAsState()
    val settings by hub.settings.collectAsState()
    val spectrumFrame by hub.spectrum.frame.collectAsState()
    val pluginCss by hub.pluginCss.collectAsState()
    val pluginJs by hub.pluginJs.collectAsState()

    val track = state.current ?: return
    val isLiked = track.id in likedIds
    val customPlayerBg = remember(settings.playerBgPath) {
        settings.playerBgPath.takeIf { it.isNotBlank() && File(it).exists() }
    }
    val customBanner = remember(settings.bannerPath) {
        settings.bannerPath.takeIf { it.isNotBlank() && File(it).exists() }
    }
    val hiResArt = remember(track.id, track.artwork, customPlayerBg) {
        customPlayerBg ?: track.trackart(1080)
    }
    val backupArt = remember(track.id) { track.fallbackart() }

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

    if (showSleep) sleepsheet(onDismiss = { showSleep = false })
    if (showTempo) temposheet(onDismiss = { showTempo = false })

    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMs = (state.durationMs - state.positionMs).coerceAtLeast(0L)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(glassTokens.bg),
    ) {
        val stageHeight = maxHeight * 0.62f

        playerbg(
            artworkUrl = hiResArt,
            fallbackUrl = backupArt,
            stageHeight = stageHeight,
            fullBleedTop = pane == PlayerPane.COVER,
            blurEnabled = glassTokens.blurEnabled,
            blurRadius = glassTokens.blurRadius,
            isDark = glassTokens.isDark,
            baseBg = glassTokens.bg,
        )

        if (settings.playerVisualBg) {
            reactivevisual(
                mode = settings.visualMode,
                preset = settings.shaderPreset,
                frame = spectrumFrame,
                customCss = "$pluginCss\n${settings.customCss}",
                customJs = pluginJs,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassTokens.bg.copy(alpha = 0.42f)),
            )
        }

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
                            artimage(
                                url = track.trackart(360),
                                fallbackUrl = backupArt,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(50.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = glassTokens.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artistline(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            iconbtn(
                                icon = ZIcons.Search,
                                onClick = { showLyricSearch = true },
                                size = 38.dp,
                                iconSize = 19.dp,
                            )
                            iconbtn(
                                icon = ZIcons.MoreHoriz,
                                onClick = { onTrackMenu(track) },
                                size = 38.dp,
                                iconSize = 19.dp,
                            )
                            iconbtn(
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

                        thinslider(
                            progress = progress,
                            onSeek = { frac ->
                                if (state.durationMs > 0L) {
                                    hub.pilot.seekto((frac * state.durationMs).toLong())
                                }
                            },
                            activeColor = glassTokens.ink,
                            trackColor = glassTokens.inkMuted.copy(alpha = 0.30f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = formatclock(state.positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = glassTokens.inkSecondary,
                            )
                            Text(
                                text = "-${formatclock(remainingMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = glassTokens.inkSecondary,
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        transport(
                            playing = state.playing,
                            buffering = state.buffering,
                            tint = glassTokens.ink,
                            compact = true,
                            onPrev = { hub.pilot.skipprev() },
                            onToggle = { hub.pilot.toggleplay() },
                            onNext = { hub.pilot.skipnext() },
                        )

                        Spacer(Modifier.height(6.dp))

                        playerbar(
                            pane = activePane,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            eqEnabled = eqProfile.enabled || fxProfile.enabled,
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
                            onOpenVisual = {
                                onCollapse()
                                onNavigate(Route.Visualizer)
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
                            artimage(
                                url = track.trackart(360),
                                fallbackUrl = backupArt,
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
                                    color = glassTokens.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artistline(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            iconbtn(
                                icon = if (state.playing) ZIcons.Pause else ZIcons.Play,
                                onClick = { hub.pilot.toggleplay() },
                                size = 40.dp,
                                iconSize = 22.dp,
                            )
                            iconbtn(
                                icon = ZIcons.ChevronDown,
                                onClick = { pane = PlayerPane.COVER },
                                size = 40.dp,
                                iconSize = 20.dp,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        queueview(
                            onClose = { pane = PlayerPane.COVER },
                            onTrackMenu = onTrackMenu,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        Spacer(Modifier.height(8.dp))

                        playerbar(
                            pane = activePane,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            eqEnabled = eqProfile.enabled || fxProfile.enabled,
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
                            onOpenVisual = {
                                onCollapse()
                                onNavigate(Route.Visualizer)
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
                            iconbtn(
                                icon = ZIcons.ChevronDown,
                                onClick = onCollapse,
                                size = 40.dp,
                                iconSize = 21.dp,
                            )
                            Text(
                                text = "Playing from ${state.queueTitle}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (glassTokens.isDark) Color.White.copy(alpha = 0.88f) else glassTokens.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                textAlign = TextAlign.Center,
                            )
                            iconbtn(
                                icon = ZIcons.Sparkles,
                                onClick = {
                                    onCollapse()
                                    onNavigate(Route.Visualizer)
                                },
                                size = 40.dp,
                                iconSize = 20.dp,
                            )
                            iconbtn(
                                icon = ZIcons.MoreHoriz,
                                onClick = { onTrackMenu(track) },
                                size = 40.dp,
                                iconSize = 21.dp,
                            )
                        }

                        if (customBanner != null) {
                            Spacer(Modifier.height(10.dp))
                            AsyncImage(
                                model = File(customBanner),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(74.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        onCollapse()
                                        onNavigate(Route.Studio)
                                    },
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
                                    color = glassTokens.ink,
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
                                    color = glassTokens.inkSecondary,
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
                            iconbtn(
                                icon = if (isLiked) ZIcons.HeartFilled else ZIcons.Heart,
                                active = isLiked,
                                onClick = { hub.togglelike(track) },
                                size = 42.dp,
                                iconSize = 22.dp,
                            )
                            iconbtn(
                                icon = ZIcons.MoreHoriz,
                                onClick = { onTrackMenu(track) },
                                size = 42.dp,
                                iconSize = 22.dp,
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        thinslider(
                            progress = progress,
                            onSeek = { frac ->
                                if (state.durationMs > 0L) {
                                    hub.pilot.seekto((frac * state.durationMs).toLong())
                                }
                            },
                            activeColor = glassTokens.ink,
                            trackColor = glassTokens.inkMuted.copy(alpha = 0.30f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = formatclock(state.positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = glassTokens.inkSecondary,
                            )
                            Text(
                                text = "-${formatclock(remainingMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = glassTokens.inkSecondary,
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        transport(
                            playing = state.playing,
                            buffering = state.buffering,
                            tint = glassTokens.ink,
                            compact = false,
                            onPrev = { hub.pilot.skipprev() },
                            onToggle = { hub.pilot.toggleplay() },
                            onNext = { hub.pilot.skipnext() },
                        )

                        Spacer(Modifier.height(22.dp))

                        volumebar(
                            volume = volumeFrac,
                            tint = glassTokens.ink,
                            mutedTint = glassTokens.inkSecondary,
                            onVolumeChange = { next ->
                                volumeFrac = next
                                val target = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            },
                        )

                        Spacer(Modifier.height(18.dp))

                        playerbar(
                            pane = activePane,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            eqEnabled = eqProfile.enabled || fxProfile.enabled,
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
                            onOpenVisual = {
                                onCollapse()
                                onNavigate(Route.Visualizer)
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
private fun playerbar(
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
    onOpenVisual: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenTempo: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        iconbtn(
            icon = ZIcons.Lyrics,
            active = pane == PlayerPane.LYRICS,
            tint = if (pane == PlayerPane.LYRICS) glassTokens.accent else glassTokens.inkSecondary,
            onClick = onToggleLyrics,
            size = 42.dp,
            iconSize = 23.dp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(glassTokens.glassElevated)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            baricon(
                icon = ZIcons.Shuffle,
                active = shuffle,
                onClick = onToggleShuffle,
            )
            baricon(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) ZIcons.RepeatOne else ZIcons.Repeat,
                active = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onCycleRepeat,
            )
            baricon(
                icon = ZIcons.Sparkles,
                active = false,
                onClick = onOpenVisual,
            )
            baricon(
                icon = ZIcons.Equalizer,
                active = eqEnabled,
                onClick = onOpenEq,
            )
            baricon(
                icon = ZIcons.Timer,
                active = sleepActive,
                onClick = onOpenSleep,
            )
            baricon(
                icon = ZIcons.Speed,
                active = tempoActive,
                onClick = onOpenTempo,
            )
        }

        iconbtn(
            icon = ZIcons.Queue,
            active = pane == PlayerPane.QUEUE,
            tint = if (pane == PlayerPane.QUEUE) glassTokens.accent else glassTokens.inkSecondary,
            onClick = onToggleQueue,
            size = 42.dp,
            iconSize = 23.dp,
        )
    }
}

@Composable
private fun playerbg(
    artworkUrl: String?,
    fallbackUrl: String?,
    stageHeight: Dp,
    fullBleedTop: Boolean,
    blurEnabled: Boolean,
    blurRadius: Float,
    isDark: Boolean,
    baseBg: Color,
) {
    val hazeState = rememberHazeState()
    var activeArt by remember(artworkUrl, fallbackUrl) {
        mutableStateOf(artworkUrl.orEmpty().ifBlank { fallbackUrl.orEmpty() })
    }
    var triedFallback by remember(artworkUrl, fallbackUrl) { mutableStateOf(false) }

    LaunchedEffect(artworkUrl, fallbackUrl) {
        activeArt = artworkUrl.orEmpty().ifBlank { fallbackUrl.orEmpty() }
        triedFallback = false
    }
    val radDp = blurRadius.coerceIn(8f, 96f).dp
    val blurStyle = remember(blurEnabled, radDp, isDark, baseBg) {
        HazeBlurStyle {
            blurEnabled(blurEnabled)
            blurRadius(radDp)
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
            if (activeArt.isNotBlank()) {
                AsyncImage(
                    model = activeArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = {
                        val alt = fallbackUrl.orEmpty()
                        if (!triedFallback && alt.isNotBlank() && alt != activeArt) {
                            triedFallback = true
                            activeArt = alt
                        }
                    },
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

        if (fullBleedTop && activeArt.isNotBlank()) {
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
                    model = activeArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = {
                        val alt = fallbackUrl.orEmpty()
                        if (!triedFallback && alt.isNotBlank() && alt != activeArt) {
                            triedFallback = true
                            activeArt = alt
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun transport(
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
private fun volumebar(
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
        thinslider(
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
private fun baricon(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) glassTokens.accentSoft else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) glassTokens.accent else glassTokens.inkSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
