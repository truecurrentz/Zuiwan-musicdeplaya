package zuiwan.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.ArtistSummary
import zuiwan.model.FeedItem
import zuiwan.model.ThemeChoice
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.BrandBar
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.MediaCard
import zuiwan.ui.QuickPickTile
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.ZIcons

@Composable
fun viewhome(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()

    val feed by hub.homeFeed.collectAsState()
    val loading by hub.homeLoading.collectAsState()
    val error by hub.homeError.collectAsState()
    val selectedChip by hub.selectedChip.collectAsState()
    val history by hub.historyTracks.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    val listState = rememberLazyListState()
    val nearBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 2
        }
    }

    LaunchedEffect(nearBottom, feed.continuation) {
        if (nearBottom && feed.continuation != null) {
            hub.morehome()
        }
    }

    val keepListeningArtists = remember(history) {
        history
            .mapNotNull { track ->
                val ref = track.artists.firstOrNull { !it.id.isNullOrBlank() } ?: return@mapNotNull null
                ArtistSummary(
                    id = ref.id!!,
                    name = ref.name,
                    artwork = track.artwork,
                )
            }
            .distinctBy { it.id }
            .take(8)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "topbar") {
            BrandBar {
                GlassIconButton(
                    icon = ZIcons.History,
                    onClick = { onNavigate(Route.History) },
                )
                GlassIconButton(
                    icon = ZIcons.Stats,
                    onClick = { onNavigate(Route.Stats) },
                )
                GlassIconButton(
                    icon = if (glass.isDark) ZIcons.Sun else ZIcons.Moon,
                    onClick = {
                        scope.launch {
                            val next = if (glass.isDark) ThemeChoice.WHITE else ThemeChoice.DARK
                            hub.store.updatesettings { it.copy(theme = next) }
                        }
                    },
                )
                GlassIconButton(
                    icon = ZIcons.Settings,
                    onClick = { onNavigate(Route.Settings) },
                )
            }
        }

        if (feed.chips.isNotEmpty()) {
            item(key = "chips") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    feed.chips.forEach { chip ->
                        val active = selectedChip?.params == chip.params
                        GlassChip(
                            label = chip.title,
                            selected = active,
                            onClick = { hub.refreshhome(if (active) null else chip) },
                        )
                    }
                }
            }
        }

        if (history.isNotEmpty() && selectedChip == null && feed.shelves.none { it.title.contains("Quick", ignoreCase = true) }) {
            item(key = "quick_recent_head") {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "Quick picks",
                    actionLabel = "Play all",
                    onAction = { hub.pilot.playtracks(history.take(20), 0, "Quick picks", fetchRadio = true) },
                )
            }
            item(key = "quick_recent_row") {
                val cols = history.take(16).chunked(4)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(cols) { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { song ->
                                QuickPickTile(
                                    track = song,
                                    playing = playback.current?.id == song.id,
                                    onClick = { hub.pilot.audioplay(song, "Quick picks") },
                                    onMore = { onTrackMenu(song) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (keepListeningArtists.isNotEmpty() && selectedChip == null) {
            item(key = "keep_listening_head") {
                Spacer(Modifier.height(10.dp))
                SectionHeader(
                    title = "Keep listening",
                    actionLabel = "History",
                    onAction = { onNavigate(Route.History) },
                )
            }
            item(key = "keep_listening_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(keepListeningArtists, key = { it.id }) { artist ->
                        MediaCard(
                            item = FeedItem.ArtistCard(artist),
                            width = 148.dp,
                            onClick = { onNavigate(Route.Artist(artist.id)) },
                        )
                    }
                }
            }
        }

        if (loading && feed.shelves.isEmpty()) {
            item(key = "loading") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 72.dp),
                ) {
                    CircularProgressIndicator(color = glass.accent)
                }
            }
        } else if (error != null && feed.shelves.isEmpty()) {
            item(key = "error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 56.dp),
                ) {
                    Text(
                        text = error ?: "Connection issue",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.inkSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassPillButton(
                        text = "Retry",
                        icon = ZIcons.Refresh,
                        primary = true,
                        onClick = { hub.refreshhome(selectedChip) },
                    )
                }
            }
        }

        feed.shelves.forEachIndexed { shelfIdx, shelf ->
            val allSongs = shelf.items.all { it is FeedItem.SongCard }
            item(key = "shelf_head_$shelfIdx") {
                Spacer(Modifier.height(12.dp))
                SectionHeader(
                    title = shelf.title,
                    subtitle = shelf.subtitle,
                    actionLabel = when {
                        allSongs && shelf.items.size > 1 -> "Play all"
                        shelf.browseId != null -> "More"
                        else -> null
                    },
                    onAction = {
                        if (allSongs && shelf.items.size > 1) {
                            val tracks = shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                            hub.pilot.playtracks(tracks, 0, shelf.title, fetchRadio = true)
                        } else if (shelf.browseId != null) {
                            onNavigate(Route.Browse(shelf.browseId, shelf.params, shelf.title))
                        }
                    },
                )
            }

            item(key = "shelf_body_$shelfIdx") {
                if (allSongs) {
                    val songs = shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                    val columns = songs.chunked(4)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(columns) { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.forEach { song ->
                                    QuickPickTile(
                                        track = song,
                                        playing = playback.current?.id == song.id,
                                        onClick = {
                                            val start = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                            hub.pilot.playtracks(songs, start, shelf.title, fetchRadio = true)
                                        },
                                        onMore = { onTrackMenu(song) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.items, key = { it.key }) { entry ->
                            MediaCard(
                                item = entry,
                                onClick = {
                                    when (entry) {
                                        is FeedItem.SongCard -> hub.pilot.audioplay(entry.track, shelf.title)
                                        is FeedItem.AlbumCard -> onNavigate(Route.Album(entry.album.id))
                                        is FeedItem.ArtistCard -> onNavigate(Route.Artist(entry.artist.id))
                                        is FeedItem.PlaylistCard -> onNavigate(Route.Playlist(entry.playlist.id, local = false))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
