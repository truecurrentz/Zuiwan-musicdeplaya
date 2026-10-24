package zuiwan.screen

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.AlbumDetail
import zuiwan.model.Track
import zuiwan.model.upscaleart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.AlbumGridCard
import zuiwan.ui.DetailHero
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@Composable
fun viewalbum(
    albumId: String,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val savedAlbums by hub.savedAlbums.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var detail by remember(albumId) { mutableStateOf<AlbumDetail?>(null) }
    var loading by remember(albumId) { mutableStateOf(true) }

    LaunchedEffect(albumId) {
        loading = true
        detail = hub.tube.loadalbum(albumId).getOrNull()
        loading = false
    }

    val saved = savedAlbums.any { it.id == albumId }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (loading && detail == null) {
            item(key = "topbar") {
                GlassTopBar(title = "Album", onBack = onBack)
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
            val artistText = info.album.artists.joinToString(", ") { it.name }
            val metaText = listOfNotNull(
                info.album.year?.toString(),
                "${info.tracks.size} songs",
            ).joinToString(" - ")

            item(key = "hero") {
                DetailHero(
                    title = info.album.title,
                    artworkUrl = upscaleart(info.album.artwork, 1080),
                    fallbackIcon = ZIcons.Album,
                    onBack = onBack,
                    subtitle = artistText.ifBlank { null },
                    onSubtitleClick = info.album.artists.firstOrNull()?.id?.let { aid ->
                        { onNavigate(Route.Artist(aid)) }
                    },
                    metadata = metaText,
                    primaryActions = {
                        GlassIconButton(
                            icon = ZIcons.Download,
                            onClick = { info.tracks.forEach { hub.vault.downloadtrack(it) } },
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                        GlassIconButton(
                            icon = ZIcons.Shuffle,
                            onClick = {
                                hub.pilot.playtracks(info.tracks.shuffled(), 0, info.album.title)
                            },
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                        GlassPillButton(
                            text = "Play",
                            icon = ZIcons.Play,
                            highContrast = true,
                            onClick = {
                                hub.pilot.playtracks(info.tracks, 0, info.album.title)
                            },
                        )
                        GlassIconButton(
                            icon = if (saved) ZIcons.Check else ZIcons.Heart,
                            active = saved,
                            onClick = { hub.togglesavealbum(info.album) },
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                    },
                )
            }

            item(key = "songs_head") {
                SectionHeader(title = "Songs")
            }

            itemsIndexed(info.tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, song ->
                SongRow(
                    track = song,
                    indexNumber = idx + 1,
                    playing = playback.current?.id == song.id,
                    cardBackground = playback.current?.id == song.id,
                    onClick = { hub.pilot.playtracks(info.tracks, idx, info.album.title) },
                    onMore = { onTrackMenu(song) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            if (info.versions.isNotEmpty()) {
                item(key = "versions_head") {
                    Spacer(Modifier.height(14.dp))
                    SectionHeader(title = "Other Versions")
                }
                item(key = "versions_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        items(info.versions, key = { it.id }) { alt ->
                            AlbumGridCard(alt) { onNavigate(Route.Album(alt.id)) }
                        }
                    }
                }
            }
        }
    }
}
