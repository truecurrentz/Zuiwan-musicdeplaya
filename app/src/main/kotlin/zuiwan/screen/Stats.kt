package zuiwan.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zuiwan.model.StatsSummary
import zuiwan.model.StatsWindow
import zuiwan.model.Track
import zuiwan.model.fallbackart
import zuiwan.model.trackart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.artimage
import zuiwan.ui.glass
import zuiwan.ui.chip
import zuiwan.ui.topbar
import zuiwan.ui.Route
import zuiwan.ui.scrollhost
import zuiwan.ui.sectionbar
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll
import kotlin.math.roundToInt

@Composable
fun viewstats(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    var window by remember { mutableStateOf(StatsWindow.MONTH) }
    var summary by remember { mutableStateOf(StatsSummary()) }

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    LaunchedEffect(window) {
        summary = hub.loadstats(window)
    }

    val ringColors = listOf(
        glassTokens.accent,
        glassTokens.accentSecondary,
        Color(0xFF34D399),
        Color(0xFFFBBF24),
    )

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            Column(modifier = Modifier.fillMaxWidth()) {
                topbar(
                    title = "Stats",
                    subtitle = "Your top songs, artists, and playback history",
                    onBack = onBack,
                ) {
                    Icon(
                        imageVector = ZIcons.Sparkles,
                        contentDescription = null,
                        tint = glassTokens.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                ) {
                    StatsWindow.entries.forEach { w ->
                        chip(
                            label = w.label,
                            selected = window == w,
                            onClick = { window = w },
                        )
                    }
                }
            }
        },
    ) { topPad ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPad + 6.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero_metrics") {
                glass(
                    shape = RoundedCornerShape(26.dp),
                    elevated = true,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(glassTokens.accentSoft)
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            Column {
                                Text(
                                    text = "Total Time Listened",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = glassTokens.ink,
                                )
                                Spacer(Modifier.height(6.dp))
                                val hrs = summary.totalMinutes / 60L
                                val mins = summary.totalMinutes % 60L
                                Text(
                                    text = if (hrs > 0L) "%d:%02d".format(hrs, mins) else "${summary.totalMinutes}m",
                                    fontSize = 36.sp,
                                    lineHeight = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = glassTokens.ink,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        ) {
                            statcol("Total Plays", summary.totalPlays.toString(), Modifier.weight(1f))
                            statcol("Unique Songs", summary.topTracks.size.toString(), Modifier.weight(1f))
                            statcol("Unique Artists", summary.uniqueArtists.toString(), Modifier.weight(1f))
                        }
                    }
                }
            }

            if (summary.topArtists.isNotEmpty()) {
                item(key = "breakdown_head") {
                    Spacer(Modifier.height(6.dp))
                    sectionbar(title = "Artist breakdown")
                }
                item(key = "breakdown_card") {
                    val topSlice = summary.topArtists.take(4)
                    val totalMs = topSlice.sumOf { it.totalTimeMs.coerceAtLeast(1L) }.toFloat().coerceAtLeast(1f)
                    glass(
                        shape = RoundedCornerShape(26.dp),
                        elevated = true,
                        contentPadding = PaddingValues(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Canvas(modifier = Modifier.size(124.dp)) {
                                val stroke = 20.dp.toPx()
                                var start = -90f
                                topSlice.forEachIndexed { idx, stat ->
                                    val sweep = 360f * (stat.totalTimeMs.coerceAtLeast(1L).toFloat() / totalMs)
                                    drawArc(
                                        color = ringColors[idx % ringColors.size],
                                        startAngle = start,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                                    )
                                    start += sweep
                                }
                            }

                            Spacer(Modifier.width(22.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                topSlice.forEachIndexed { idx, stat ->
                                    val pct = ((stat.totalTimeMs.coerceAtLeast(1L).toFloat() / totalMs) * 100f).roundToInt()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(ringColors[idx % ringColors.size]),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stat.artist.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = glassTokens.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = glassTokens.inkSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val favArtist = summary.topArtists.firstOrNull()
            if (favArtist != null) {
                item(key = "fav_artist") {
                    glass(
                        shape = RoundedCornerShape(24.dp),
                        elevated = true,
                        onClick = {
                            if (favArtist.artist.id.startsWith("UC")) {
                                onNavigate(Route.Artist(favArtist.artist.id))
                            }
                        },
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            artimage(
                                url = favArtist.artist.artwork,
                                shape = CircleShape,
                                fallbackIcon = ZIcons.Artist,
                                modifier = Modifier.size(68.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Your Favourite Artist",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glassTokens.accent,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = favArtist.artist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = glassTokens.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${favArtist.plays} plays - ${favArtist.totalTimeMs / 60_000L} min",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                )
                            }
                        }
                    }
                }
            }

            val favSong = summary.topTracks.firstOrNull()
            if (favSong != null) {
                item(key = "fav_song") {
                    glass(
                        shape = RoundedCornerShape(24.dp),
                        elevated = true,
                        onClick = { hub.pilot.audioplay(favSong.track, "Stats") },
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            artimage(
                                url = favSong.track.trackart(),
                                fallbackUrl = favSong.track.fallbackart(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(68.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Your Favourite Song",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glassTokens.accent,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = favSong.track.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = glassTokens.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${favSong.plays} times - ${favSong.totalTimeMs / 60_000L} min",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                )
                            }
                        }
                    }
                }
            }

            if (summary.topTracks.isNotEmpty()) {
                item(key = "tracks_head") {
                    Spacer(Modifier.height(8.dp))
                    sectionbar(title = "Top Tracks", subtitle = window.label)
                }
                itemsIndexed(summary.topTracks, key = { _, it -> it.track.id }) { idx, stat ->
                    songrow(
                        track = stat.track,
                        indexNumber = idx + 1,
                        onClick = { hub.pilot.audioplay(stat.track, "Top Tracks") },
                        onMore = { onTrackMenu(stat.track) },
                        trailing = {
                            Text(
                                text = "${stat.plays}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = glassTokens.accent,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }

            if (summary.topArtists.isNotEmpty()) {
                item(key = "artists_head") {
                    Spacer(Modifier.height(12.dp))
                    sectionbar(title = "Top Artists", subtitle = window.label)
                }
                itemsIndexed(summary.topArtists, key = { _, it -> it.artist.id }) { idx, stat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (stat.artist.id.startsWith("UC")) {
                                    onNavigate(Route.Artist(stat.artist.id))
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = glassTokens.inkMuted,
                            modifier = Modifier.width(26.dp),
                        )
                        artimage(
                            url = stat.artist.artwork,
                            shape = CircleShape,
                            fallbackIcon = ZIcons.Artist,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stat.artist.name, style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                            Text("${stat.totalTimeMs / 60_000L} min listened", style = MaterialTheme.typography.bodyMedium, color = glassTokens.inkSecondary)
                        }
                        Text("${stat.plays} plays", style = MaterialTheme.typography.labelLarge, color = glassTokens.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun statcol(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = glassTokens.accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = glassTokens.inkSecondary,
        )
    }
}
