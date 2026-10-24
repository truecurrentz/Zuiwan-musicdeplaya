package zuiwan.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.BrandBar
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

private enum class LibSection(val label: String, val icon: ImageVector) {
    OVERVIEW("Library", ZIcons.Waveform),
    PLAYLISTS("Playlists", ZIcons.Playlist),
    LIKED("Liked", ZIcons.HeartFilled),
    DOWNLOADS("Offline", ZIcons.Offline),
    ALBUMS("Albums", ZIcons.Album),
    ARTISTS("Artists", ZIcons.Artist),
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
    val history by hub.historyTracks.collectAsState()
    val playlists by hub.playlists.collectAsState()
    val albums by hub.savedAlbums.collectAsState()
    val artists by hub.savedArtists.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var section by rememberSaveable { mutableStateOf(LibSection.OVERVIEW) }
    var showCreate by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    val heroPool = remember(liked, history, downloads) {
        (liked + history + downloads).distinctBy { it.id }
    }
    val heroLead = heroPool.firstOrNull()

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
            BrandBar {
                GlassIconButton(
                    icon = ZIcons.Equalizer,
                    onClick = { onNavigate(Route.Equalizer) },
                )
                GlassIconButton(
                    icon = ZIcons.Add,
                    onClick = { showCreate = true },
                )
                GlassIconButton(
                    icon = ZIcons.Settings,
                    onClick = { onNavigate(Route.Settings) },
                )
            }
        }

        item(key = "tabs") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                LibSection.entries.forEach { item ->
                    GlassChip(
                        label = item.label,
                        leadingIcon = item.icon,
                        selected = section == item,
                        onClick = { section = item },
                    )
                }
            }
        }

        when (section) {
            LibSection.OVERVIEW -> {
                item(key = "hero_most_played") {
                    GlassBox(
                        shape = RoundedCornerShape(26.dp),
                        elevated = true,
                        contentPadding = PaddingValues(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ArtImage(
                                    url = heroLead?.artwork,
                                    shape = RoundedCornerShape(18.dp),
                                    fallbackIcon = ZIcons.Waveform,
                                    modifier = Modifier.size(76.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(glass.accentSoft)
                                            .padding(horizontal = 10.dp, vertical = 3.dp),
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.Star,
                                            contentDescription = null,
                                            tint = glass.accent,
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Text(
                                            text = "MOST PLAYED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = glass.accent,
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = heroLead?.title ?: "Your Collection",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = glass.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (heroPool.isEmpty()) "Save or stream tracks to begin" else "${heroPool.size} songs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = glass.inkSecondary,
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                GlassPillButton(
                                    text = "Play all",
                                    icon = ZIcons.Play,
                                    primary = true,
                                    onClick = {
                                        if (heroPool.isNotEmpty()) {
                                            hub.pilot.playtracks(heroPool, 0, "Library Mix", fetchRadio = true)
                                        }
                                    },
                                )
                                GlassIconButton(
                                    icon = ZIcons.Shuffle,
                                    onClick = {
                                        if (heroPool.isNotEmpty()) {
                                            hub.pilot.playtracks(heroPool.shuffled(), 0, "Library Mix", fetchRadio = true)
                                        }
                                    },
                                    size = 44.dp,
                                    iconSize = 19.dp,
                                )
                            }
                        }
                    }
                }

                item(key = "bento_grid") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BentoCollectionCard(
                                icon = ZIcons.HeartFilled,
                                title = "Liked songs",
                                subtitle = "${liked.size} tracks",
                                onClick = { section = LibSection.LIKED },
                                modifier = Modifier.weight(1f),
                            )
                            BentoCollectionCard(
                                icon = ZIcons.Offline,
                                title = "Offline",
                                subtitle = "${downloads.size} downloaded",
                                onClick = { section = LibSection.DOWNLOADS },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BentoCollectionCard(
                                icon = ZIcons.Refresh,
                                title = "Cached",
                                subtitle = "Instant playback",
                                onClick = { onNavigate(Route.History) },
                                modifier = Modifier.weight(1f),
                            )
                            BentoCollectionCard(
                                icon = ZIcons.Trend,
                                title = "My top 50",
                                subtitle = "All time",
                                onClick = { onNavigate(Route.Stats) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    item(key = "overview_pl_head") {
                        Spacer(Modifier.height(6.dp))
                        SectionHeader(
                            title = "Playlists",
                            actionLabel = "All",
                            onAction = { section = LibSection.PLAYLISTS },
                        )
                    }
                    items(playlists.take(5), key = { "ov_${it.id}" }) { pl ->
                        PlaylistListRow(
                            title = pl.title,
                            subtitle = listOfNotNull(pl.author, pl.countText).joinToString(" - "),
                            artwork = pl.artwork,
                            onClick = { onNavigate(Route.Playlist(pl.id, local = pl.local)) },
                        )
                    }
                }
            }

            LibSection.LIKED -> {
                if (liked.isNotEmpty()) {
                    item(key = "liked_actions") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            GlassPillButton(
                                text = "Play all",
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
                        EmptyGlassNotice("Tap the heart icon on any song to save it to Liked songs.")
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
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            GlassPillButton(
                                text = "Play offline",
                                icon = ZIcons.Play,
                                primary = true,
                                onClick = { hub.pilot.playtracks(downloads, 0, "Offline", fetchRadio = false) },
                                modifier = Modifier.weight(1f),
                            )
                            GlassPillButton(
                                text = "Shuffle",
                                icon = ZIcons.Shuffle,
                                onClick = { hub.pilot.playtracks(downloads.shuffled(), 0, "Offline", fetchRadio = false) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    items(downloads, key = { it.id }) { song ->
                        SongRow(
                            track = song,
                            playing = playback.current?.id == song.id,
                            downloaded = true,
                            onClick = {
                                val idx = downloads.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                hub.pilot.playtracks(downloads, idx, "Offline", fetchRadio = false)
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
                            .padding(horizontal = 16.dp, vertical = 6.dp),
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
                }
                if (playlists.isEmpty()) {
                    item(key = "empty_pl") {
                        EmptyGlassNotice("Create local playlists or import .m3u files anytime.")
                    }
                } else {
                    items(playlists, key = { it.id }) { pl ->
                        PlaylistListRow(
                            title = pl.title,
                            subtitle = listOfNotNull(pl.author, pl.countText).joinToString(" - "),
                            artwork = pl.artwork,
                            onClick = { onNavigate(Route.Playlist(pl.id, local = pl.local)) },
                        )
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
private fun BentoCollectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    GlassBox(
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(glass.accentSoft),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistListRow(
    title: String,
    subtitle: String,
    artwork: String?,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        ArtImage(
            url = artwork,
            shape = RoundedCornerShape(14.dp),
            fallbackIcon = ZIcons.Playlist,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary, maxLines = 1)
        }
        Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmptyGlassNotice(text: String) {
    val glass = LocalGlass.current
    GlassBox(
        shape = RoundedCornerShape(22.dp),
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
