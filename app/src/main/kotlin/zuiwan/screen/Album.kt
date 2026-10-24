package zuiwan.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zuiwan.model.AlbumDetail
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.AlbumGridCard
import zuiwan.ui.ArtImage
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
        item(key = "topbar") {
            GlassTopBar(title = "Album", onBack = onBack) {
                detail?.album?.let { album ->
                    GlassIconButton(
                        icon = if (saved) ZIcons.HeartFilled else ZIcons.Heart,
                        active = saved,
                        onClick = { hub.togglesavealbum(album) },
                    )
                    GlassIconButton(
                        icon = ZIcons.Download,
                        onClick = {
                            detail?.tracks?.forEach { hub.vault.downloadtrack(it) }
                        },
                    )
                }
            }
        }

        if (loading) {
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
            item(key = "hero") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    ArtImage(
                        url = info.album.artwork,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.size(220.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = info.album.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = glass.ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    val artistText = info.album.artists.joinToString(", ") { it.name }
                    Text(
                        text = listOfNotNull(
                            artistText.takeIf { it.isNotBlank() },
                            info.album.year?.toString(),
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                            info.album.artists.firstOrNull()?.id?.let { onNavigate(Route.Artist(it)) }
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GlassPillButton(
                            text = "Play",
                            icon = ZIcons.Play,
                            primary = true,
                            onClick = {
                                hub.pilot.playtracks(info.tracks, 0, info.album.title)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GlassPillButton(
                            text = "Shuffle",
                            icon = ZIcons.Shuffle,
                            onClick = {
                                hub.pilot.playtracks(info.tracks.shuffled(), 0, info.album.title)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            itemsIndexed(info.tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, song ->
                SongRow(
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
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(title = "Other Versions")
                }
                item(key = "versions_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
