package zuiwan.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.formatclock
import zuiwan.model.upscaleart
import zuiwan.sheet.QueueSheet
import zuiwan.sheet.SleepSheet
import zuiwan.sheet.TempoSheet
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.AmbientBackdrop
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassIconButton
import zuiwan.ui.Route
import zuiwan.ui.WaveSlider
import zuiwan.ui.ZIcons

@Composable
fun viewplayer(
    onCollapse: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current

    val state by hub.pilot.state.collectAsState()
    val likedIds by hub.likedIds.collectAsState()
    val sleepState by hub.pilot.sleep.state.collectAsState()
    val eqProfile by hub.eq.collectAsState()
    val spec by hub.vault.activeSpec.collectAsState()

    val track = state.current ?: return
    val isLiked = track.id in likedIds

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showTempo by remember { mutableStateOf(false) }

    BackHandler {
        if (showLyrics) {
            showLyrics = false
        } else {
            onCollapse()
        }
    }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false })
    if (showSleep) SleepSheet(onDismiss = { showSleep = false })
    if (showTempo) TempoSheet(onDismiss = { showTempo = false })

    val artScale by animateFloatAsState(
        targetValue = if (state.playing) 1.0f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(artworkUrl = track.artwork, intense = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                GlassIconButton(
                    icon = ZIcons.ChevronDown,
                    onClick = onCollapse,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent,
                    )
                    Text(
                        text = state.queueTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = glass.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(
                    icon = ZIcons.More,
                    onClick = { onTrackMenu(track) },
                )
            }

            Spacer(Modifier.height(14.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    label = "playerStage",
                ) { lyricsOpen ->
                    if (lyricsOpen) {
                        viewlyrics(modifier = Modifier.fillMaxSize())
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            ArtImage(
                                url = upscaleart(track.artwork, 1080),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f)
                                    .scale(artScale)
                                    .shadow(
                                        elevation = 28.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        ambientColor = glass.glassShadow,
                                        spotColor = glass.accent.copy(alpha = 0.35f),
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (spec != null && spec?.id == track.id) {
                val activeSpec = spec!!
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GlassBox(
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        val codec = activeSpec.codecs.uppercase().ifBlank { "OPUS" }
                        Text(
                            text = "$codec • ${activeSpec.bitrate / 1000} KBPS • ${activeSpec.sampleRate / 1000} KHZ",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = glass.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = track.artistline(),
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            track.artists.firstOrNull()?.id?.let { artistId ->
                                onCollapse()
                                onNavigate(Route.Artist(artistId))
                            }
                        },
                    )
                }
                GlassIconButton(
                    icon = if (isLiked) ZIcons.HeartFilled else ZIcons.Heart,
                    active = isLiked,
                    onClick = { hub.togglelike(track) },
                    size = 46.dp,
                    iconSize = 21.dp,
                )
            }

            Spacer(Modifier.height(14.dp))

            val progress = if (state.durationMs > 0L) {
                (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            WaveSlider(
                progress = progress,
                playing = state.playing,
                onSeek = { frac ->
                    if (state.durationMs > 0L) {
                        hub.pilot.seekto((frac * state.durationMs).toLong())
                    }
                },
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    text = formatclock(state.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.inkSecondary,
                )
                Text(
                    text = formatclock(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.inkSecondary,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                GlassIconButton(
                    icon = ZIcons.Shuffle,
                    active = state.shuffle,
                    onClick = { hub.pilot.toggleshuffle() },
                    size = 44.dp,
                )
                GlassIconButton(
                    icon = ZIcons.Prev,
                    onClick = { hub.pilot.skipprev() },
                    size = 54.dp,
                    iconSize = 22.dp,
                )
                if (state.buffering) {
                    GlassBox(
                        shape = CircleShape,
                        accented = true,
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(72.dp),
                    ) {
                        CircularProgressIndicator(
                            color = glass.accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                } else {
                    GlassIconButton(
                        icon = if (state.playing) ZIcons.Pause else ZIcons.Play,
                        onClick = { hub.pilot.toggleplay() },
                        size = 72.dp,
                        iconSize = 28.dp,
                        solidAccent = true,
                    )
                }
                GlassIconButton(
                    icon = ZIcons.Next,
                    onClick = { hub.pilot.skipnext() },
                    size = 54.dp,
                    iconSize = 22.dp,
                )
                GlassIconButton(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) ZIcons.RepeatOne else ZIcons.Repeat,
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = { hub.pilot.cyclerepeat() },
                    size = 44.dp,
                )
            }

            Spacer(Modifier.height(18.dp))

            GlassBox(
                shape = RoundedCornerShape(24.dp),
                elevated = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    UtilitySlot(
                        icon = ZIcons.Lyrics,
                        label = "Lyrics",
                        active = showLyrics,
                        onClick = { showLyrics = !showLyrics },
                    )
                    UtilitySlot(
                        icon = ZIcons.Queue,
                        label = "Queue",
                        active = false,
                        onClick = { showQueue = true },
                    )
                    UtilitySlot(
                        icon = ZIcons.Equalizer,
                        label = "EQ",
                        active = eqProfile.enabled,
                        onClick = {
                            onCollapse()
                            onNavigate(Route.Equalizer)
                        },
                    )
                    UtilitySlot(
                        icon = ZIcons.Timer,
                        label = "Sleep",
                        active = sleepState.active,
                        onClick = { showSleep = true },
                    )
                    UtilitySlot(
                        icon = ZIcons.Speed,
                        label = if (state.speed != 1f) "%.2gx".format(state.speed) else "Tempo",
                        active = state.speed != 1f || state.pitch != 1f,
                        onClick = { showTempo = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun UtilitySlot(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val tint = if (active) glass.accent else glass.inkSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
