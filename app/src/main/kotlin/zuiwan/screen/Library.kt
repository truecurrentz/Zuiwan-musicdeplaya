package zuiwan.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.Route
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

private enum class LibSection(val label: String) {
    LIKED("Liked"),
    PLAYLISTS("Playlists"),
    DOWNLOADS("Downloads"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

@Composable
fun viewlibrary(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current

    val liked by hub.likedTracks.collectAsState()
    val downloads by hub.downloadedTracks.collectAsState()
    val playlists by hub.playlists.collectAsState()
    val albums by hub.savedAlbums.collectAsState()
    val artists by hub.savedArtists.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var section by rememberSaveable { mutableStateOf(LibSection.LIKED) }
    var showCreate by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    val importM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) hub.importm3u(uri)
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (draftName.isNotBlank()) {
                            hub.createplaylist(draftName) { newId ->
                                showCreate = false
                                draftName = ""
                                onNavigate(Route.Playlist(newId, local = true))
                            }
                        }
                    },
                ) {
                    Text("Create", color = glass.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text("Cancel", color = glass.inkSecondary)
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "top") {
            GlassTopBar(title = "Library") {
                GlassIconButton(
                    icon = ZIcons.Equalizer,
                    onClick = { onNavigate(Route.Equalizer) },
                )
                GlassIconButton(
                    icon = ZIcons.Add,
                    onClick = { showCreate = true },
                )
            }
        }

        item(key = "bento") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                GlassBox(
                    shape = RoundedCornerShape(20.dp),
                    accented = section == LibSection.LIKED,
                    onClick = { section = LibSection.LIKED },
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Column {
                        Icon(ZIcons.HeartFilled, null, tint = glass.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Liked Songs", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                        Text("${liked.size} tracks", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
                    }
                }
                GlassBox(
                    shape = RoundedCornerShape(20.dp),
                    accented = section == LibSection.DOWNLOADS,
                    onClick = { section = LibSection.DOWNLOADS },
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Column {
                        Icon(ZIcons.Download, null, tint = glass.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Downloads", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                        Text("${downloads.size} offline", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
                    }
                }
            }
        }

        item(key = "tabs") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                LibSection.entries.forEach { item ->
                    GlassChip(
                        label = item.label,
                        selected = section == item,
                        onClick = { section = item },
                    )
                }
            }
        }

        when (section) {
            LibSection.LIKED -> {
                if (liked.isNotEmpty()) {
                    item(key = "liked_actions") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            GlassPillButton(
                                text = "Play All",
                                icon = ZIcons.Play,
                                primary = true,
                                onClick = { hub.pilot.playtracks(liked, 0, "Liked Songs") },
                                modifier = Modifier.weight(1f),
                            )
                            GlassPillButton(
                                text = "Shuffle",
                                icon = ZIcons.Shuffle,
                                onClick = { hub.pilot.playtracks(liked.shuffled(), 0, "Liked Songs") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    items(liked, key = { it.id }) { song ->
                        SongRow(
                            track = song,
                            playing = playback.current?.id == song.id,
                            liked = true,
                            onClick = {
                                val idx = liked.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                hub.pilot.playtracks(liked, idx, "Liked Songs")
                            },
                            onMore = { onTrackMenu(song) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                } else {
                    item(key = "empty_liked") {
                        EmptyGlassNotice("Tap the heart on any song to save it to Liked Songs.")
                    }
                }
            }

            LibSection.DOWNLOADS -> {
                if (downloads.isNotEmpty()) {
                    item(key = "dl_actions") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            GlassPillButton(
                                text = "Play Offline",
                                icon = ZIcons.Play,
                                primary = true,
                                onClick = { hub.pilot.playtracks(downloads, 0, "Downloads", fetchRadio = false) },
                                modifier = Modifier.weight(1f),
                            )
                            GlassPillButton(
                                text = "Shuffle",
                                icon = ZIcons.Shuffle,
                                onClick = { hub.pilot.playtracks(downloads.shuffled(), 0, "Downloads", fetchRadio = false) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    items(downloads, key = { it.id }) { song ->
                        SongRow(
                            track = song,
                            playing = playback.current?.id == song.id,
                            downloaded = true,
                            onClick = {
                                val idx = downloads.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                hub.pilot.playtracks(downloads, idx, "Downloads", fetchRadio = false)
                            },
                            onMore = { onTrackMenu(song) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                } else {
                    item(key = "empty_dl") {
                        EmptyGlassNotice("Download songs from any menu to listen completely offline.")
                    }
                }
            }

            LibSection.PLAYLISTS -> {
                item(key = "pl_actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        GlassPillButton(
                            text = "New Playlist",
                            icon = ZIcons.Add,
                            primary = true,
                            onClick = { showCreate = true },
                            modifier = Modifier.weight(1f),
                        )
                        GlassPillButton(
                            text = "Import M3U",
                            icon = ZIcons.Playlist,
                            onClick = { importM3uLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (playlists.isEmpty()) {
                    item(key = "empty_pl") {
                        EmptyGlassNotice("Create local playlists or import .m3u files anytime.")
                    }
                } else {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Route.Playlist(pl.id, local = pl.local)) }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        ) {
                            ArtImage(
                                url = pl.artwork,
                                shape = RoundedCornerShape(14.dp),
                                fallbackIcon = ZIcons.Playlist,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.title, style = MaterialTheme.typography.titleMedium, color = glass.ink)
                                Text(
                                    listOfNotNull(pl.author, pl.countText).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.inkSecondary,
                                )
                            }
                            Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LibSection.ALBUMS -> {
                if (albums.isEmpty()) {
                    item(key = "empty_albums") {
                        EmptyGlassNotice("Saved albums appear here for quick access.")
                    }
                } else {
                    items(albums, key = { it.id }) { album ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Route.Album(album.id)) }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        ) {
                            ArtImage(album.artwork, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(album.title, style = MaterialTheme.typography.titleMedium, color = glass.ink)
                                Text(
                                    album.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.inkSecondary,
                                )
                            }
                            Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LibSection.ARTISTS -> {
                if (artists.isEmpty()) {
                    item(key = "empty_artists") {
                        EmptyGlassNotice("Followed artists appear here.")
                    }
                } else {
                    items(artists, key = { it.id }) { artist ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Route.Artist(artist.id)) }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        ) {
                            ArtImage(artist.artwork, shape = CircleShape, fallbackIcon = ZIcons.Artist, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(artist.name, style = MaterialTheme.typography.titleMedium, color = glass.ink)
                                Text(artist.subscribers ?: "Artist", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
                            }
                            Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGlassNotice(text: String) {
    val glass = LocalGlass.current
    GlassBox(
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = glass.inkSecondary,
        )
    }
}
