package zuiwan.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zuiwan.model.FeedItem
import zuiwan.model.FeedShelf
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.albumcard
import zuiwan.ui.glass
import zuiwan.ui.iconbtn
import zuiwan.ui.topbar
import zuiwan.ui.mediacard
import zuiwan.ui.picktile
import zuiwan.ui.Route
import zuiwan.ui.scrollhost
import zuiwan.ui.sectionbar
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll

@Composable
fun viewexplore(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val explore by hub.exploreFeed.collectAsState()
    val loading by hub.exploreLoading.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        pinned = true,
        header = {
            topbar(title = "Explore") {
                iconbtn(
                    icon = ZIcons.Refresh,
                    onClick = { hub.refreshexplore() },
                )
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
            if (loading && explore.releases.isEmpty() && explore.moods.isEmpty()) {
                item(key = "loader") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 72.dp),
                    ) {
                        CircularProgressIndicator(color = glassTokens.accent)
                    }
                }
            }

            if (explore.releases.isNotEmpty()) {
                item(key = "releases_head") {
                    sectionbar(title = "New Releases", subtitle = "Fresh Albums & Singles")
                }
                item(key = "releases_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(explore.releases, key = { it.id }) { album ->
                            albumcard(album = album) {
                                onNavigate(Route.Album(album.id))
                            }
                        }
                    }
                }
            }

            explore.charts.forEachIndexed { cIdx, chart ->
                val songs = chart.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                item(key = "chart_head_$cIdx") {
                    Spacer(Modifier.height(10.dp))
                    sectionbar(
                        title = chart.title,
                        subtitle = "Global Charts",
                        actionLabel = if (songs.size > 1) "Play all" else null,
                        onAction = if (songs.size > 1) {
                            { hub.pilot.playtracks(songs, 0, chart.title) }
                        } else {
                            null
                        },
                    )
                }
                item(key = "chart_row_$cIdx") {
                    if (songs.isNotEmpty()) {
                        val cols = songs.take(15).chunked(3)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(cols) { group ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    group.forEach { song ->
                                        picktile(
                                            track = song,
                                            playing = playback.current?.id == song.id,
                                            onClick = {
                                                hub.pilot.audioplay(song, chart.title)
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
                            items(chart.items, key = { it.key }) { entry ->
                                mediacard(
                                    item = entry,
                                    onClick = {
                                        when (entry) {
                                            is FeedItem.SongCard -> hub.pilot.audioplay(entry.track, chart.title)
                                            is FeedItem.AlbumCard -> onNavigate(Route.Album(entry.album.id))
                                            is FeedItem.ArtistCard -> onNavigate(Route.Artist(entry.artist.id))
                                            is FeedItem.PlaylistCard -> onNavigate(Route.Playlist(entry.playlist.id))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (explore.moods.isNotEmpty()) {
                item(key = "moods_head") {
                    Spacer(Modifier.height(12.dp))
                    sectionbar(title = "Moods & Genres", subtitle = "Curated Atmospheres")
                }
                val pairs = explore.moods.chunked(2)
                items(pairs) { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 5.dp),
                    ) {
                        rowItems.forEach { tile ->
                            val stripe = Color(tile.stripeColor or 0xFF000000L)
                            glass(
                                shape = RoundedCornerShape(16.dp),
                                elevated = true,
                                onClick = {
                                    onNavigate(Route.Browse(tile.browseId, tile.params, tile.title))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                            .background(stripe),
                                    )
                                    Text(
                                        text = tile.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = glassTokens.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun viewbrowse(
    browseId: String,
    params: String?,
    title: String,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val playback by hub.pilot.state.collectAsState()
    var shelves by remember(browseId, params) { mutableStateOf<List<FeedShelf>>(emptyList()) }
    var loading by remember(browseId, params) { mutableStateOf(true) }

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    LaunchedEffect(browseId, params) {
        loading = true
        shelves = hub.tube.loadbrowse(browseId, params).getOrDefault(emptyList())
        loading = false
    }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            topbar(title = title, onBack = onBack)
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
            if (loading) {
                item(key = "loading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                    ) {
                        CircularProgressIndicator(color = glassTokens.accent)
                    }
                }
            }
            shelves.forEachIndexed { idx, shelf ->
                val songs = shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                val onlySongs = songs.size == shelf.items.size && songs.isNotEmpty()
                item(key = "b_head_$idx") {
                    sectionbar(
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        actionLabel = if (onlySongs && songs.size > 1) "Play all" else null,
                        onAction = if (onlySongs && songs.size > 1) {
                            { hub.pilot.playtracks(songs, 0, shelf.title) }
                        } else {
                            null
                        },
                    )
                }
                if (onlySongs) {
                    items(songs, key = { "${idx}_${it.id}" }) { song ->
                        songrow(
                            track = song,
                            playing = playback.current?.id == song.id,
                            onClick = { hub.pilot.audioplay(song, shelf.title) },
                            onMore = { onTrackMenu(song) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                } else {
                    item(key = "b_row_$idx") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(shelf.items, key = { it.key }) { item ->
                                mediacard(
                                    item = item,
                                    onClick = {
                                        when (item) {
                                            is FeedItem.SongCard -> hub.pilot.audioplay(item.track, shelf.title)
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
            }
        }
    }
}
