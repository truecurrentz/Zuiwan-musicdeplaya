package zuiwan.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.FeedItem
import zuiwan.model.FeedShelf
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.BrandBar
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.MediaCard
import zuiwan.ui.QuickPickTile
import zuiwan.ui.Route
import zuiwan.ui.ScrollHeaderHost
import zuiwan.ui.SectionHeader
import zuiwan.ui.ZIcons
import zuiwan.ui.rememberheaderscroll

@Composable
fun viewhome(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val feed by hub.homeFeed.collectAsState()
    val loading by hub.homeLoading.collectAsState()
    val error by hub.homeError.collectAsState()
    val quickPicks by hub.quickPicks.collectAsState()
    val history by hub.historyTracks.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    val headerScroll = rememberheaderscroll()
    val listState = rememberLazyListState()

    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 2
        }
    }

    LaunchedEffect(nearEnd, feed.continuation) {
        if (nearEnd && !feed.continuation.isNullOrBlank() && !loading) {
            hub.loadmorehome()
        }
    }

    val picksPool = remember(quickPicks, history, feed.shelves) {
        when {
            quickPicks.isNotEmpty() -> quickPicks
            history.isNotEmpty() -> history.take(12)
            else -> feed.shelves
                .flatMap { it.items }
                .mapNotNull { (it as? FeedItem.SongCard)?.track }
                .distinctBy { it.id }
                .take(12)
        }
    }

    ScrollHeaderHost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrandBar(
                    actions = {
                        GlassIconButton(
                            icon = ZIcons.History,
                            onClick = { onNavigate(Route.History) },
                        )
                        GlassIconButton(
                            icon = ZIcons.Trend,
                            onClick = { onNavigate(Route.Stats) },
                        )
                        GlassIconButton(
                            icon = ZIcons.Settings,
                            onClick = { onNavigate(Route.Settings) },
                        )
                    },
                )

                if (feed.chips.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(feed.chips, key = { it.params }) { chip ->
                            GlassChip(
                                label = chip.title,
                                selected = chip.selected,
                                onClick = {
                                    hub.refreshhome(if (chip.selected) null else chip.params)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { topPad ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPad + 6.dp,
                bottom = bottomPadding.calculateBottomPadding() + 28.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (picksPool.isNotEmpty()) {
                item(key = "qp_head") {
                    SectionHeader(
                        title = "Quick picks",
                        actionLabel = "Play all",
                        onAction = {
                            hub.pilot.playtracks(picksPool, 0, "Quick picks")
                        },
                    )
                }
                item(key = "qp_grid") {
                    val columns = picksPool.chunked(4)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(columns) { col ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                col.forEach { song ->
                                    QuickPickTile(
                                        track = song,
                                        playing = playback.current?.id == song.id,
                                        onClick = {
                                            val startIdx = picksPool.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                            hub.pilot.playtracks(picksPool, startIdx, "Quick picks", fetchRadio = true)
                                        },
                                        onMore = { onTrackMenu(song) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (history.size >= 3) {
                item(key = "recent_head") {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(
                        title = "Keep listening",
                        actionLabel = "History",
                        onAction = { onNavigate(Route.History) },
                    )
                }
                item(key = "recent_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(history.take(10), key = { "hist_${it.id}" }) { song ->
                            MediaCard(
                                item = FeedItem.SongCard(song),
                                onClick = { hub.pilot.audioplay(song, "Recently Played") },
                            )
                        }
                    }
                }
            }

            feed.shelves.forEachIndexed { idx, shelf ->
                item(key = "shelf_head_${idx}_${shelf.title}") {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        actionLabel = if (shelf.browseId != null) "More" else null,
                        onAction = shelf.browseId?.let { bid ->
                            { onNavigate(Route.Browse(bid, shelf.params, shelf.title)) }
                        },
                    )
                }
                item(key = "shelf_row_${idx}_${shelf.title}") {
                    ShelfRow(
                        shelf = shelf,
                        currentTrackId = playback.current?.id,
                        onPlaySong = { song, list ->
                            val start = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                            hub.pilot.playtracks(list, start, shelf.title)
                        },
                        onNavigate = onNavigate,
                        onTrackMenu = onTrackMenu,
                    )
                }
            }

            if (loading && feed.shelves.isEmpty()) {
                item(key = "loading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                    ) {
                        CircularProgressIndicator(color = glass.accent)
                    }
                }
            }

            if (error != null && feed.shelves.isEmpty() && !loading) {
                item(key = "error") {
                    GlassBox(
                        shape = RoundedCornerShape(24.dp),
                        elevated = true,
                        contentPadding = PaddingValues(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Could not reach YouTube Music",
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.inkSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            GlassPillButton(
                                text = "Retry",
                                icon = ZIcons.Refresh,
                                primary = true,
                                onClick = { hub.refreshhome() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: FeedShelf,
    currentTrackId: String?,
    onPlaySong: (Track, List<Track>) -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val allSongs = remember(shelf.items) {
        shelf.items.all { it is FeedItem.SongCard } && shelf.items.size >= 4
    }

    if (allSongs) {
        val songs = remember(shelf.items) {
            shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
        }
        val cols = remember(songs) { songs.chunked(4) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cols) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { song ->
                        QuickPickTile(
                            track = song,
                            playing = currentTrackId == song.id,
                            onClick = { onPlaySong(song, songs) },
                            onMore = { onTrackMenu(song) },
                        )
                    }
                }
            }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.items, key = { it.key }) { item ->
                MediaCard(
                    item = item,
                    onClick = {
                        when (item) {
                            is FeedItem.SongCard -> {
                                val songs = shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                                onPlaySong(item.track, songs.ifEmpty { listOf(item.track) })
                            }
                            is FeedItem.AlbumCard -> onNavigate(Route.Album(item.album.id))
                            is FeedItem.ArtistCard -> onNavigate(Route.Artist(item.artist.id))
                            is FeedItem.PlaylistCard -> onNavigate(Route.Playlist(item.playlist.id))
                        }
                    },
                )
            }
        }
    }
}
