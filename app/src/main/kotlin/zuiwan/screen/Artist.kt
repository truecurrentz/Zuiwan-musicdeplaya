package zuiwan.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.ArtistDetail
import zuiwan.model.FeedItem
import zuiwan.model.Track
import zuiwan.model.trackart
import zuiwan.model.upscaleart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.hero
import zuiwan.ui.iconbtn
import zuiwan.ui.topbar
import zuiwan.ui.mediacard
import zuiwan.ui.Route
import zuiwan.ui.scrollhost
import zuiwan.ui.sectionbar
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll

@Composable
fun viewartist(
    artistId: String,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val scope = rememberCoroutineScope()
    val savedArtists by hub.savedArtists.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var detail by remember(artistId) { mutableStateOf<ArtistDetail?>(null) }
    var loading by remember(artistId) { mutableStateOf(true) }

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()

    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }
    val pastHero by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
    }

    LaunchedEffect(artistId) {
        loading = true
        detail = hub.tube.loadartist(artistId).getOrNull()
        loading = false
    }

    val isSaved = savedArtists.any { it.id == artistId }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        overlayHero = true,
        header = {
            topbar(
                title = detail?.artist?.name ?: "Artist",
                showTitle = pastHero || detail == null,
                onBack = onBack,
                actions = {
                    detail?.let { info ->
                        iconbtn(
                            icon = ZIcons.Radio,
                            onClick = {
                                val radioId = info.radioPlaylistId ?: info.shufflePlaylistId
                                if (!radioId.isNullOrBlank()) {
                                    scope.launch {
                                        val next = hub.tube.loadnext(playlistId = radioId).getOrNull()
                                        if (next != null && next.tracks.isNotEmpty()) {
                                            hub.pilot.playtracks(next.tracks, 0, "${info.artist.name} Radio")
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { _ ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (loading && detail == null) {
                item(key = "loader") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 140.dp, bottom = 80.dp),
                    ) {
                        CircularProgressIndicator(color = glassTokens.accent)
                    }
                }
            }

            detail?.let { info ->
                val topSongs = info.sections
                    .flatMap { it.items }
                    .mapNotNull { (it as? FeedItem.SongCard)?.track }
                val meta = listOfNotNull(info.listeners, info.artist.subscribers).joinToString(" - ")

                item(key = "hero") {
                    hero(
                        title = info.artist.name,
                        artworkUrl = upscaleart(info.artist.artwork, 1080).ifBlank {
                            topSongs.firstOrNull()?.trackart(1080).orEmpty()
                        },
                        fallbackIcon = ZIcons.Artist,
                        circularArt = true,
                        metadata = meta.ifBlank { null },
                        description = info.description?.takeIf { it.isNotBlank() },
                        primaryActions = {
                            iconbtn(
                                icon = if (isSaved) ZIcons.HeartFilled else ZIcons.Heart,
                                active = isSaved,
                                softFill = true,
                                size = 48.dp,
                                iconSize = 21.dp,
                                onClick = { hub.saveartist(info.artist) },
                            )
                            iconbtn(
                                icon = ZIcons.Play,
                                solidAccent = true,
                                size = 64.dp,
                                iconSize = 28.dp,
                                onClick = {
                                    if (topSongs.isNotEmpty()) {
                                        hub.pilot.playtracks(topSongs, 0, info.artist.name)
                                    } else if (!info.shufflePlaylistId.isNullOrBlank()) {
                                        scope.launch {
                                            val next = hub.tube.loadnext(playlistId = info.shufflePlaylistId).getOrNull()
                                            if (next != null && next.tracks.isNotEmpty()) {
                                                hub.pilot.playtracks(next.tracks, 0, info.artist.name)
                                            }
                                        }
                                    }
                                },
                            )
                            iconbtn(
                                icon = ZIcons.Shuffle,
                                softFill = true,
                                size = 48.dp,
                                iconSize = 21.dp,
                                onClick = {
                                    if (topSongs.isNotEmpty()) {
                                        hub.pilot.playtracks(topSongs.shuffled(), 0, info.artist.name)
                                    } else if (!info.shufflePlaylistId.isNullOrBlank()) {
                                        scope.launch {
                                            val next = hub.tube.loadnext(playlistId = info.shufflePlaylistId).getOrNull()
                                            if (next != null && next.tracks.isNotEmpty()) {
                                                hub.pilot.playtracks(next.tracks, 0, info.artist.name)
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    )
                }

                info.sections.forEachIndexed { idx, section ->
                    item(key = "sec_head_$idx") {
                        Spacer(Modifier.height(10.dp))
                        sectionbar(
                            title = section.title,
                            actionLabel = if (section.browseId != null) "See all" else null,
                            onAction = section.browseId?.let { bid ->
                                { onNavigate(Route.Browse(bid, section.params, section.title)) }
                            },
                        )
                    }

                    val allSongs = section.items.all { it is FeedItem.SongCard }
                    if (allSongs) {
                        val songs = section.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                        items(songs.take(6), key = { "${idx}_${it.id}" }) { song ->
                            songrow(
                                track = song,
                                playing = playback.current?.id == song.id,
                                onClick = {
                                    val pos = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    hub.pilot.playtracks(songs, pos, info.artist.name)
                                },
                                onMore = { onTrackMenu(song) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    } else {
                        item(key = "sec_row_$idx") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(section.items, key = { it.key }) { item ->
                                    mediacard(
                                        item = item,
                                        onClick = {
                                            when (item) {
                                                is FeedItem.SongCard -> hub.pilot.audioplay(item.track, info.artist.name)
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
}
