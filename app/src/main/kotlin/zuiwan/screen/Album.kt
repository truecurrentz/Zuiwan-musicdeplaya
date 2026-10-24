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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import zuiwan.model.AlbumDetail
import zuiwan.model.Track
import zuiwan.model.trackart
import zuiwan.model.upscaleart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.albumcard
import zuiwan.ui.hero
import zuiwan.ui.iconbtn
import zuiwan.ui.topbar
import zuiwan.ui.Route
import zuiwan.ui.scrollhost
import zuiwan.ui.sectionbar
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll

@Composable
fun viewalbum(
    albumId: String,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val savedAlbums by hub.savedAlbums.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var detail by remember(albumId) { mutableStateOf<AlbumDetail?>(null) }
    var loading by remember(albumId) { mutableStateOf(true) }

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

    LaunchedEffect(albumId) {
        loading = true
        detail = hub.tube.loadalbum(albumId).getOrNull()
        loading = false
    }

    val saved = savedAlbums.any { it.id == albumId }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        overlayHero = true,
        header = {
            topbar(
                title = detail?.album?.title ?: "Album",
                showTitle = pastHero || detail == null,
                onBack = onBack,
                actions = {
                    detail?.let { info ->
                        iconbtn(
                            icon = ZIcons.Download,
                            onClick = { info.tracks.forEach { hub.vault.downloadtrack(it) } },
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
                val artistText = info.album.artists.joinToString(", ") { it.name }
                val totalSecs = info.tracks.sumOf { it.duration.coerceAtLeast(0) }
                val mins = totalSecs / 60
                val metaText = listOfNotNull(
                    info.album.year?.toString(),
                    "${info.tracks.size} songs",
                    if (mins > 0) "$mins min" else null,
                ).joinToString(" - ")

                item(key = "hero") {
                    hero(
                        title = info.album.title,
                        artworkUrl = upscaleart(info.album.artwork, 1080).ifBlank {
                            info.tracks.firstOrNull()?.trackart(1080).orEmpty()
                        },
                        fallbackIcon = ZIcons.Album,
                        subtitle = artistText.ifBlank { null },
                        onSubtitleClick = info.album.artists.firstOrNull()?.id?.let { aid ->
                            { onNavigate(Route.Artist(aid)) }
                        },
                        metadata = metaText,
                        description = info.description?.takeIf { it.isNotBlank() },
                        primaryActions = {
                            iconbtn(
                                icon = if (saved) ZIcons.HeartFilled else ZIcons.Heart,
                                active = saved,
                                softFill = true,
                                size = 48.dp,
                                iconSize = 21.dp,
                                onClick = { hub.savealbum(info.album) },
                            )
                            iconbtn(
                                icon = ZIcons.Play,
                                solidAccent = true,
                                size = 64.dp,
                                iconSize = 28.dp,
                                onClick = {
                                    hub.pilot.playtracks(info.tracks, 0, info.album.title)
                                },
                            )
                            iconbtn(
                                icon = ZIcons.Shuffle,
                                softFill = true,
                                size = 48.dp,
                                iconSize = 21.dp,
                                onClick = {
                                    hub.pilot.playtracks(info.tracks.shuffled(), 0, info.album.title)
                                },
                            )
                        },
                    )
                }

                itemsIndexed(info.tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, song ->
                    songrow(
                        track = song,
                        indexNumber = idx + 1,
                        playing = playback.current?.id == song.id,
                        onClick = { hub.pilot.playtracks(info.tracks, idx, info.album.title) },
                        onMore = { onTrackMenu(song) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                if (info.versions.isNotEmpty()) {
                    item(key = "versions_head") {
                        Spacer(Modifier.height(16.dp))
                        sectionbar(title = "Other Versions")
                    }
                    item(key = "versions_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(info.versions, key = { it.id }) { alt ->
                                albumcard(alt) { onNavigate(Route.Album(alt.id)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
