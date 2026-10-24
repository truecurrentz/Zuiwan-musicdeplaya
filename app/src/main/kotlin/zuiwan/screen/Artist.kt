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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.ArtistDetail
import zuiwan.model.FeedItem
import zuiwan.model.Track
import zuiwan.model.upscaleart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.DetailHero
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.MediaCard
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@Composable
fun viewartist(
    artistId: String,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val savedArtists by hub.savedArtists.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var detail by remember(artistId) { mutableStateOf<ArtistDetail?>(null) }
    var loading by remember(artistId) { mutableStateOf(true) }

    LaunchedEffect(artistId) {
        loading = true
        detail = hub.tube.loadartist(artistId).getOrNull()
        loading = false
    }

    val isSaved = savedArtists.any { it.id == artistId }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (loading && detail == null) {
            item(key = "top") {
                GlassTopBar(title = "Artist", onBack = onBack)
            }
            item(key = "loader") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                ) {
                    CircularProgressIndicator(color = glass.accent)
                }
            }
        }

        detail?.let { info ->
            val topSongs = info.sections
                .flatMap { it.items }
                .mapNotNull { (it as? FeedItem.SongCard)?.track }
            val meta = listOfNotNull(info.listeners, info.artist.subscribers).joinToString(" - ")

            item(key = "hero") {
                DetailHero(
                    title = info.artist.name,
                    artworkUrl = upscaleart(info.artist.artwork, 1080),
                    fallbackIcon = ZIcons.Artist,
                    onBack = onBack,
                    metadata = meta.ifBlank { null },
                    primaryActions = {
                        GlassIconButton(
                            icon = ZIcons.Shuffle,
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
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                        GlassPillButton(
                            text = "Play",
                            icon = ZIcons.Play,
                            highContrast = true,
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
                        GlassIconButton(
                            icon = if (isSaved) ZIcons.Check else ZIcons.Heart,
                            active = isSaved,
                            onClick = { hub.togglesaveartist(info.artist) },
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                        GlassIconButton(
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
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                    },
                )
            }

            if (!info.description.isNullOrBlank()) {
                item(key = "bio_excerpt") {
                    Text(
                        text = info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.inkSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            info.sections.forEachIndexed { idx, section ->
                item(key = "sec_head_$idx") {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(
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
                        SongRow(
                            track = song,
                            playing = playback.current?.id == song.id,
                            onClick = {
                                val pos = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                hub.pilot.playtracks(songs, pos, info.artist.name)
                            },
                            onMore = { onTrackMenu(song) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                } else {
                    item(key = "sec_row_$idx") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.items, key = { it.key }) { item ->
                                MediaCard(
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
