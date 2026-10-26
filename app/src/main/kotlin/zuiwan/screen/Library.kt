package zuiwan.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistSummary
import zuiwan.model.PlaylistSummary
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.fallbackart
import zuiwan.model.trackart
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import zuiwan.sheet.createsheet
import zuiwan.sheet.importsheet
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.artimage
import zuiwan.ui.brandbar
import zuiwan.ui.glass
import zuiwan.ui.chip
import zuiwan.ui.iconbtn
import zuiwan.ui.pillbtn
import zuiwan.ui.covergrid
import zuiwan.ui.Route
import zuiwan.ui.scrollhost
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll

private enum class LibFilter(val label: String) {
    LIBRARY("Library"),
    PLAYLISTS("Playlists"),
    SONGS("Songs"),
    LOCAL("Local"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

private enum class SongScope(val label: String) {
    LIKED("Liked"),
    LOCAL("Local"),
    ALL("In Library"),
    DOWNLOADED("Offline"),
}

private enum class LibSort(val label: String) {
    ADDED("Date added"),
    NAME("Name"),
    ARTIST("Artist"),
    PLAYS("Most played"),
}

private sealed interface LibEntry {
    val key: String
    val name: String

    data class AutoEntry(
        val id: String,
        override val name: String,
        val subtitle: String,
        val icon: ImageVector,
        val thumbs: List<String>,
    ) : LibEntry {
        override val key: String = "auto_$id"
    }

    data class PlaylistEntry(val playlist: PlaylistSummary) : LibEntry {
        override val key: String = "pl_${playlist.id}"
        override val name: String = playlist.title
    }

    data class AlbumEntry(val album: AlbumSummary) : LibEntry {
        override val key: String = "al_${album.id}"
        override val name: String = album.title
    }

    data class ArtistEntry(val artist: ArtistSummary) : LibEntry {
        override val key: String = "ar_${artist.id}"
        override val name: String = artist.name
    }

    data class SongEntry(val track: Track) : LibEntry {
        override val key: String = "sg_${track.id}"
        override val name: String = track.title
    }
}

@Composable
fun viewlibrary(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val liked by hub.likedTracks.collectAsState()
    val downloads by hub.downloadedTracks.collectAsState()
    val localSongs by hub.localTracks.collectAsState()
    val history by hub.historyTracks.collectAsState()
    val topTracks by hub.topTracks.collectAsState()
    val playlists by hub.playlists.collectAsState()
    val albums by hub.savedAlbums.collectAsState()
    val artists by hub.savedArtists.collectAsState()
    val playback by hub.pilot.state.collectAsState()
    val session by hub.session.collectAsState()
    val syncing by hub.sync.syncing.collectAsState()

    var filter by rememberSaveable { mutableStateOf(LibFilter.LIBRARY) }
    var songScope by rememberSaveable { mutableStateOf(SongScope.LIKED) }
    var selectedFolder by rememberSaveable { mutableStateOf("") }
    var sortType by rememberSaveable { mutableStateOf(LibSort.ADDED) }
    var sortDesc by rememberSaveable { mutableStateOf(true) }
    var gridView by rememberSaveable { mutableStateOf(true) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) hub.scanlocal()
    }
    val importAudioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) hub.importaudio(uris)
    }

    fun triggerscan() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            hub.scanlocal()
        } else {
            audioPermLauncher.launch(perm)
        }
    }

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    BackHandler(enabled = searchOpen || filter != LibFilter.LIBRARY) {
        if (searchOpen) {
            searchOpen = false
            query = ""
        } else {
            filter = LibFilter.LIBRARY
        }
    }

    val useGrid = gridView && filter != LibFilter.SONGS && filter != LibFilter.LOCAL
    val lifted by remember(useGrid) {
        derivedStateOf {
            if (useGrid) {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 8
            } else {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
            }
        }
    }

    val cleanQuery = remember(query) { query.trim().lowercase() }

    val autoItems = remember(liked, downloads, localSongs, history, topTracks, cleanQuery) {
        val raw = listOf(
            LibEntry.AutoEntry(
                id = "auto_liked",
                name = "Liked",
                subtitle = "${liked.size} songs",
                icon = ZIcons.HeartFilled,
                thumbs = liked.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
            ),
            LibEntry.AutoEntry(
                id = "auto_local",
                name = "Local",
                subtitle = "${localSongs.size} songs",
                icon = ZIcons.Folder,
                thumbs = localSongs.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
            ),
            LibEntry.AutoEntry(
                id = "auto_offline",
                name = "Offline",
                subtitle = "${downloads.size} songs",
                icon = ZIcons.Offline,
                thumbs = downloads.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
            ),
            LibEntry.AutoEntry(
                id = "auto_cached",
                name = "Cached",
                subtitle = "${history.size} songs",
                icon = ZIcons.History,
                thumbs = history.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
            ),
            LibEntry.AutoEntry(
                id = "auto_top",
                name = "My Top 50",
                subtitle = "${topTracks.ifEmpty { history }.take(50).size} songs",
                icon = ZIcons.Trend,
                thumbs = topTracks.ifEmpty { history }.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
            ),
        )
        if (cleanQuery.isBlank()) raw else raw.filter { it.name.lowercase().contains(cleanQuery) }
    }

    val localFolders = remember(localSongs) {
        localSongs.mapNotNull { it.folderName?.takeIf(String::isNotBlank) }.distinct().sorted()
    }

    val allKnownSongs = remember(liked, downloads, localSongs, history) {
        (liked + localSongs + downloads + history).distinctBy { it.id }
    }

    val filteredSongs = remember(filter, songScope, selectedFolder, liked, downloads, localSongs, allKnownSongs, topTracks, sortType, sortDesc, cleanQuery) {
        val base = if (filter == LibFilter.LOCAL) {
            if (selectedFolder.isBlank()) localSongs else localSongs.filter { it.folderName == selectedFolder }
        } else {
            when (songScope) {
                SongScope.LIKED -> liked
                SongScope.LOCAL -> localSongs
                SongScope.ALL -> allKnownSongs
                SongScope.DOWNLOADED -> downloads
            }
        }
        val searched = if (cleanQuery.isBlank()) {
            base
        } else {
            base.filter { s ->
                s.title.lowercase().contains(cleanQuery) ||
                    s.artistline().lowercase().contains(cleanQuery) ||
                    (s.album?.name?.lowercase()?.contains(cleanQuery) == true) ||
                    (s.folderName?.lowercase()?.contains(cleanQuery) == true)
            }
        }
        val topRank = topTracks.mapIndexed { idx, t -> t.id to idx }.toMap()
        val sorted = when (sortType) {
            LibSort.ADDED -> searched
            LibSort.NAME -> searched.sortedBy { it.title.lowercase() }
            LibSort.ARTIST -> searched.sortedBy { it.artistline().lowercase() }
            LibSort.PLAYS -> searched.sortedBy { topRank[it.id] ?: Int.MAX_VALUE }
        }
        if (sortType == LibSort.ADDED) {
            if (sortDesc) sorted else sorted.reversed()
        } else if (sortType == LibSort.PLAYS) {
            if (sortDesc) sorted else sorted.reversed()
        } else {
            if (sortDesc) sorted.reversed() else sorted
        }
    }

    val mixEntries = remember(filter, autoItems, playlists, albums, artists, allKnownSongs, sortType, sortDesc, cleanQuery) {
        when (filter) {
            LibFilter.LIBRARY -> {
                val userItems = buildList {
                    playlists.forEach { pl ->
                        if (cleanQuery.isBlank() || pl.title.lowercase().contains(cleanQuery)) {
                            add(LibEntry.PlaylistEntry(pl))
                        }
                    }
                    albums.forEach { al ->
                        val artistNames = al.artists.joinToString(" ") { it.name }.lowercase()
                        if (cleanQuery.isBlank() || al.title.lowercase().contains(cleanQuery) || artistNames.contains(cleanQuery)) {
                            add(LibEntry.AlbumEntry(al))
                        }
                    }
                    artists.forEach { ar ->
                        if (cleanQuery.isBlank() || ar.name.lowercase().contains(cleanQuery)) {
                            add(LibEntry.ArtistEntry(ar))
                        }
                    }
                    if (cleanQuery.isNotBlank()) {
                        allKnownSongs.forEach { sg ->
                            if (sg.title.lowercase().contains(cleanQuery) || sg.artistline().lowercase().contains(cleanQuery)) {
                                add(LibEntry.SongEntry(sg))
                            }
                        }
                    }
                }
                val ordered = when (sortType) {
                    LibSort.NAME -> if (sortDesc) userItems.sortedByDescending { it.name.lowercase() } else userItems.sortedBy { it.name.lowercase() }
                    else -> if (sortDesc) userItems else userItems.reversed()
                }
                autoItems + ordered
            }

            LibFilter.PLAYLISTS -> {
                val pls = playlists
                    .filter { cleanQuery.isBlank() || it.title.lowercase().contains(cleanQuery) }
                    .map { LibEntry.PlaylistEntry(it) }
                val ordered = when (sortType) {
                    LibSort.NAME -> if (sortDesc) pls.sortedByDescending { it.name.lowercase() } else pls.sortedBy { it.name.lowercase() }
                    else -> if (sortDesc) pls else pls.reversed()
                }
                autoItems + ordered
            }

            LibFilter.ALBUMS -> {
                val als = albums
                    .filter { al ->
                        val artistNames = al.artists.joinToString(" ") { it.name }.lowercase()
                        cleanQuery.isBlank() || al.title.lowercase().contains(cleanQuery) || artistNames.contains(cleanQuery)
                    }
                    .let { list ->
                        when (sortType) {
                            LibSort.NAME -> if (sortDesc) list.sortedByDescending { it.title.lowercase() } else list.sortedBy { it.title.lowercase() }
                            LibSort.ARTIST -> if (sortDesc) list.sortedByDescending { it.subtitle().lowercase() } else list.sortedBy { it.subtitle().lowercase() }
                            else -> if (sortDesc) list else list.reversed()
                        }
                    }
                    .map { LibEntry.AlbumEntry(it) }
                als
            }

            LibFilter.ARTISTS -> {
                val ars = artists
                    .filter { cleanQuery.isBlank() || it.name.lowercase().contains(cleanQuery) }
                    .let { list ->
                        when (sortType) {
                            LibSort.NAME -> if (sortDesc) list.sortedByDescending { it.name.lowercase() } else list.sortedBy { it.name.lowercase() }
                            else -> if (sortDesc) list else list.reversed()
                        }
                    }
                    .map { LibEntry.ArtistEntry(it) }
                ars
            }

            LibFilter.SONGS, LibFilter.LOCAL -> emptyList()
        }
    }

    val sortOptions = remember(filter) {
        when (filter) {
            LibFilter.SONGS, LibFilter.LOCAL -> listOf(LibSort.ADDED, LibSort.NAME, LibSort.ARTIST, LibSort.PLAYS)
            LibFilter.ALBUMS -> listOf(LibSort.ADDED, LibSort.NAME, LibSort.ARTIST)
            else -> listOf(LibSort.ADDED, LibSort.NAME)
        }
    }

    LaunchedEffect(filter) {
        if (sortType !in sortOptions) {
            sortType = LibSort.ADDED
        }
    }

    val countLabel = when (filter) {
        LibFilter.SONGS, LibFilter.LOCAL -> "${filteredSongs.size} songs"
        LibFilter.PLAYLISTS -> "${playlists.size} playlists"
        LibFilter.ALBUMS -> "${albums.size} albums"
        LibFilter.ARTISTS -> "${artists.size} artists"
        LibFilter.LIBRARY -> null
    }

    if (showCreate) {
        createsheet(
            hub = hub,
            onDismiss = { showCreate = false },
            onOpenImport = { showImport = true },
            onCreated = { newId ->
                onNavigate(Route.Playlist(newId, local = true))
            },
        )
    }

    if (showImport) {
        importsheet(
            hub = hub,
            onDismiss = { showImport = false },
            onImported = { newId ->
                onNavigate(Route.Playlist(newId, local = true))
            },
        )
    }

    val spinAnim = rememberInfiniteTransition(label = "libsync")
    val syncSpin by spinAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "deg",
    )

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            brandbar {
                if (session.active) {
                    iconbtn(
                        icon = ZIcons.Sync,
                        active = syncing,
                        onClick = { hub.syncaccount() },
                        modifier = if (syncing) Modifier.rotate(syncSpin) else Modifier,
                    )
                }
                iconbtn(
                    icon = ZIcons.Folder,
                    onClick = { filter = LibFilter.LOCAL },
                )
                iconbtn(
                    icon = ZIcons.Import,
                    onClick = { showImport = true },
                )
                iconbtn(
                    icon = ZIcons.Add,
                    onClick = { showCreate = true },
                )
                iconbtn(
                    icon = ZIcons.Settings,
                    onClick = { onNavigate(Route.Settings) },
                )
            }
        },
    ) { topInset ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (filter == LibFilter.SONGS || filter == LibFilter.LOCAL) {
                val queueLabel = if (filter == LibFilter.LOCAL) {
                    selectedFolder.ifBlank { "Local Files" }
                } else {
                    songScope.label
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topInset + 4.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 24.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "chips") {
                        libchips(
                            filter = filter,
                            songScope = songScope,
                            onSelectFilter = { filter = it },
                            onSelectSongScope = { songScope = it },
                            onImportM3u = { showImport = true },
                        )
                    }

                    if (filter == LibFilter.LOCAL) {
                        item(key = "local_actions") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    pillbtn(
                                        text = "Scan Device",
                                        icon = ZIcons.Refresh,
                                        primary = true,
                                        onClick = ::triggerscan,
                                        modifier = Modifier.weight(1f),
                                    )
                                    pillbtn(
                                        text = "Add Audio Files",
                                        icon = ZIcons.Folder,
                                        onClick = { importAudioPicker.launch(arrayOf("audio/*")) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                if (localFolders.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                    ) {
                                        chip(
                                            label = "All Folders",
                                            selected = selectedFolder.isBlank(),
                                            onClick = { selectedFolder = "" },
                                        )
                                        localFolders.forEach { folder ->
                                            chip(
                                                label = folder,
                                                selected = selectedFolder == folder,
                                                leadingIcon = ZIcons.Folder,
                                                onClick = { selectedFolder = folder },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "toolbar") {
                        libtoolbar(
                            searchOpen = searchOpen,
                            query = query,
                            onQueryChange = { query = it },
                            onOpenSearch = { searchOpen = true },
                            onCloseSearch = {
                                searchOpen = false
                                query = ""
                                focusManager.clearFocus()
                            },
                            sortType = sortType,
                            sortOptions = sortOptions,
                            sortDesc = sortDesc,
                            onSortType = { sortType = it },
                            onToggleSortDir = { sortDesc = !sortDesc },
                            countLabel = countLabel,
                            showGridToggle = false,
                            gridView = false,
                            onToggleGrid = {},
                        )
                    }

                    if (filteredSongs.isNotEmpty()) {
                        item(key = "song_actions") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                pillbtn(
                                    text = "Play all",
                                    icon = ZIcons.Play,
                                    primary = true,
                                    onClick = {
                                        hub.pilot.playtracks(
                                            filteredSongs,
                                            0,
                                            queueLabel,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                pillbtn(
                                    text = "Shuffle",
                                    icon = ZIcons.Shuffle,
                                    onClick = {
                                        hub.pilot.playtracks(
                                            filteredSongs.shuffled(),
                                            0,
                                            queueLabel,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        items(filteredSongs, key = { it.id }) { song ->
                            songrow(
                                track = song,
                                playing = playback.current?.id == song.id,
                                onClick = {
                                    val idx = filteredSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    hub.pilot.playtracks(
                                        filteredSongs,
                                        idx,
                                        queueLabel,
                                    )
                                },
                                onMore = { onTrackMenu(song) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    } else {
                        item(key = "empty_songs") {
                            libempty(
                                icon = if (filter == LibFilter.LOCAL) ZIcons.Folder else if (cleanQuery.isNotBlank()) ZIcons.Search else ZIcons.Note,
                                text = when {
                                    cleanQuery.isNotBlank() -> "No matching songs in your library"
                                    filter == LibFilter.LOCAL -> "Tap Scan Device or Add Audio Files to play local MP3, FLAC, WAV, M4A, and OGG music"
                                    else -> "Songs you like or download appear here"
                                },
                            )
                        }
                    }
                }
            } else if (useGrid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInset + 4.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 80.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                        libchips(
                            filter = filter,
                            songScope = songScope,
                            onSelectFilter = { filter = it },
                            onSelectSongScope = { songScope = it },
                            onImportM3u = { showImport = true },
                            horizontalPad = 4.dp,
                        )
                    }

                    item(key = "toolbar", span = { GridItemSpan(maxLineSpan) }) {
                        libtoolbar(
                            searchOpen = searchOpen,
                            query = query,
                            onQueryChange = { query = it },
                            onOpenSearch = { searchOpen = true },
                            onCloseSearch = {
                                searchOpen = false
                                query = ""
                                focusManager.clearFocus()
                            },
                            sortType = sortType,
                            sortOptions = sortOptions,
                            sortDesc = sortDesc,
                            onSortType = { sortType = it },
                            onToggleSortDir = { sortDesc = !sortDesc },
                            countLabel = countLabel,
                            showGridToggle = true,
                            gridView = gridView,
                            onToggleGrid = { gridView = !gridView },
                            horizontalPad = 4.dp,
                        )
                    }

                    if (mixEntries.isEmpty()) {
                        item(key = "empty_grid", span = { GridItemSpan(maxLineSpan) }) {
                            libempty(
                                icon = if (cleanQuery.isNotBlank()) ZIcons.Search else ZIcons.Grid,
                                text = if (cleanQuery.isNotBlank()) "No results found in library" else "Saved items appear here",
                            )
                        }
                    } else {
                        items(mixEntries, key = { it.key }) { entry ->
                            griditem(
                                entry = entry,
                                playingId = playback.current?.id,
                                onNavigate = onNavigate,
                                onPlaySong = { song -> hub.pilot.audioplay(song, "Library") },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topInset + 4.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 80.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "chips") {
                        libchips(
                            filter = filter,
                            songScope = songScope,
                            onSelectFilter = { filter = it },
                            onSelectSongScope = { songScope = it },
                            onImportM3u = { showImport = true },
                        )
                    }

                    item(key = "toolbar") {
                        libtoolbar(
                            searchOpen = searchOpen,
                            query = query,
                            onQueryChange = { query = it },
                            onOpenSearch = { searchOpen = true },
                            onCloseSearch = {
                                searchOpen = false
                                query = ""
                                focusManager.clearFocus()
                            },
                            sortType = sortType,
                            sortOptions = sortOptions,
                            sortDesc = sortDesc,
                            onSortType = { sortType = it },
                            onToggleSortDir = { sortDesc = !sortDesc },
                            countLabel = countLabel,
                            showGridToggle = true,
                            gridView = gridView,
                            onToggleGrid = { gridView = !gridView },
                        )
                    }

                    if (mixEntries.isEmpty()) {
                        item(key = "empty_list") {
                            libempty(
                                icon = if (cleanQuery.isNotBlank()) ZIcons.Search else ZIcons.Grid,
                                text = if (cleanQuery.isNotBlank()) "No results found in library" else "Saved items appear here",
                            )
                        }
                    } else {
                        items(mixEntries, key = { it.key }) { entry ->
                            listitem(
                                entry = entry,
                                playingId = playback.current?.id,
                                onNavigate = onNavigate,
                                onTrackMenu = onTrackMenu,
                                onPlaySong = { song -> hub.pilot.audioplay(song, "Library") },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = (filter == LibFilter.LIBRARY || filter == LibFilter.PLAYLISTS) && headerScroll.visibility > 0.35f,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 18.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 14.dp,
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(glassTokens.accent)
                        .clickable { showCreate = true },
                ) {
                    Icon(
                        imageVector = ZIcons.Add,
                        contentDescription = "New playlist",
                        tint = glassTokens.onAccent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun libchips(
    filter: LibFilter,
    songScope: SongScope,
    onSelectFilter: (LibFilter) -> Unit,
    onSelectSongScope: (SongScope) -> Unit,
    onImportM3u: () -> Unit,
    horizontalPad: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPad, vertical = 4.dp),
    ) {
        if (filter == LibFilter.LIBRARY) {
            listOf(
                LibFilter.PLAYLISTS,
                LibFilter.SONGS,
                LibFilter.LOCAL,
                LibFilter.ALBUMS,
                LibFilter.ARTISTS,
            ).forEach { target ->
                chip(
                    label = target.label,
                    selected = false,
                    onClick = { onSelectFilter(target) },
                )
            }
        } else {
            chip(
                label = filter.label,
                selected = true,
                leadingIcon = ZIcons.Close,
                onClick = { onSelectFilter(LibFilter.LIBRARY) },
            )
            if (filter == LibFilter.SONGS) {
                SongScope.entries.forEach { scope ->
                    chip(
                        label = scope.label,
                        selected = songScope == scope,
                        onClick = { onSelectSongScope(scope) },
                    )
                }
            } else if (filter == LibFilter.PLAYLISTS) {
                chip(
                    label = "Import Playlist",
                    selected = false,
                    leadingIcon = ZIcons.Import,
                    onClick = onImportM3u,
                )
            }
        }
    }
}

@Composable
private fun libtoolbar(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    sortType: LibSort,
    sortOptions: List<LibSort>,
    sortDesc: Boolean,
    onSortType: (LibSort) -> Unit,
    onToggleSortDir: () -> Unit,
    countLabel: String?,
    showGridToggle: Boolean,
    gridView: Boolean,
    onToggleGrid: () -> Unit,
    horizontalPad: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val glassTokens = LocalGlass.current
    val focusRequester = remember { FocusRequester() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
    }

    if (searchOpen) {
        glass(
            shape = RoundedCornerShape(999.dp),
            elevated = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad, vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = ZIcons.Search,
                    contentDescription = null,
                    tint = glassTokens.inkSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search in library",
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassTokens.inkSecondary,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassTokens.ink),
                        cursorBrush = SolidColor(glassTokens.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                iconbtn(
                    icon = ZIcons.Close,
                    onClick = onCloseSearch,
                    size = 30.dp,
                    iconSize = 16.dp,
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad, vertical = 2.dp),
        ) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { sortMenuOpen = true }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = sortType.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = glassTokens.accent,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = ZIcons.ChevronDown,
                        contentDescription = null,
                        tint = glassTokens.accent,
                        modifier = Modifier.size(15.dp),
                    )
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    containerColor = glassTokens.glassStrong,
                ) {
                    sortOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = opt.label,
                                    color = if (opt == sortType) glassTokens.accent else glassTokens.ink,
                                )
                            },
                            onClick = {
                                onSortType(opt)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }

            iconbtn(
                icon = if (sortDesc) ZIcons.ArrowDown else ZIcons.ArrowUp,
                onClick = onToggleSortDir,
                size = 34.dp,
                iconSize = 16.dp,
                tint = glassTokens.inkSecondary,
            )

            Spacer(Modifier.weight(1f))

            if (!countLabel.isNullOrBlank()) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = glassTokens.inkSecondary,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }

            iconbtn(
                icon = ZIcons.Search,
                onClick = onOpenSearch,
                size = 38.dp,
                iconSize = 19.dp,
            )

            if (showGridToggle) {
                iconbtn(
                    icon = if (gridView) ZIcons.List else ZIcons.Grid,
                    onClick = onToggleGrid,
                    size = 38.dp,
                    iconSize = 19.dp,
                )
            }
        }
    }
}

@Composable
private fun griditem(
    entry: LibEntry,
    playingId: String?,
    onNavigate: (Route) -> Unit,
    onPlaySong: (Track) -> Unit,
) {
    val glassTokens = LocalGlass.current
    when (entry) {
        is LibEntry.AutoEntry -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate(Route.Playlist(entry.id, local = true)) }
                    .padding(6.dp),
            ) {
                covergrid(
                    thumbnails = entry.thumbs,
                    fallbackIcon = entry.icon,
                    preferIcon = entry.thumbs.isEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        is LibEntry.PlaylistEntry -> {
            val pl = entry.playlist
            val thumbs = pl.thumbnails.ifEmpty { listOfNotNull(pl.artwork.takeIf(String::isNotBlank)) }
            val synced = !pl.remoteId.isNullOrBlank()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate(Route.Playlist(pl.id, local = pl.local || synced)) }
                    .padding(6.dp),
            ) {
                covergrid(
                    thumbnails = thumbs,
                    fallbackIcon = ZIcons.Playlist,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pl.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (synced) {
                        Icon(
                            imageVector = ZIcons.Cloud,
                            contentDescription = null,
                            tint = glassTokens.accent,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = pl.countText ?: pl.author ?: "Playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        is LibEntry.AlbumEntry -> {
            val al = entry.album
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate(Route.Album(al.id)) }
                    .padding(6.dp),
            ) {
                artimage(
                    url = al.artwork,
                    shape = RoundedCornerShape(16.dp),
                    fallbackIcon = ZIcons.Album,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = al.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = al.subtitle().ifBlank { "Album" },
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        is LibEntry.ArtistEntry -> {
            val ar = entry.artist
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate(Route.Artist(ar.id)) }
                    .padding(6.dp),
            ) {
                artimage(
                    url = ar.artwork,
                    shape = CircleShape,
                    fallbackIcon = ZIcons.Artist,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ar.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = ar.subscribers ?: "Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        is LibEntry.SongEntry -> {
            val sg = entry.track
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlaySong(sg) }
                    .padding(6.dp),
            ) {
                artimage(
                    url = sg.trackart(),
                    fallbackUrl = sg.fallbackart(),
                    shape = RoundedCornerShape(16.dp),
                    fallbackIcon = ZIcons.Note,
                    playing = playingId == sg.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = sg.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (playingId == sg.id) glassTokens.accent else glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sg.artistline(),
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun listitem(
    entry: LibEntry,
    playingId: String?,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onPlaySong: (Track) -> Unit,
) {
    val glassTokens = LocalGlass.current
    when (entry) {
        is LibEntry.AutoEntry -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Playlist(entry.id, local = true)) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                covergrid(
                    thumbnails = entry.thumbs,
                    fallbackIcon = entry.icon,
                    preferIcon = entry.thumbs.isEmpty(),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glassTokens.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkSecondary,
                        maxLines = 1,
                    )
                }
                Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
            }
        }

        is LibEntry.PlaylistEntry -> {
            val pl = entry.playlist
            val thumbs = pl.thumbnails.ifEmpty { listOfNotNull(pl.artwork.takeIf(String::isNotBlank)) }
            val synced = !pl.remoteId.isNullOrBlank()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Playlist(pl.id, local = pl.local || synced)) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                covergrid(
                    thumbnails = thumbs,
                    fallbackIcon = ZIcons.Playlist,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pl.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glassTokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (synced) {
                            Icon(
                                imageVector = ZIcons.Cloud,
                                contentDescription = null,
                                tint = glassTokens.accent,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Text(
                            text = listOfNotNull(pl.author, pl.countText).joinToString(" - "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = glassTokens.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
            }
        }

        is LibEntry.AlbumEntry -> {
            val al = entry.album
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Album(al.id)) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                artimage(
                    url = al.artwork,
                    shape = RoundedCornerShape(13.dp),
                    fallbackIcon = ZIcons.Album,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = al.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glassTokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = al.subtitle().ifBlank { "Album" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
            }
        }

        is LibEntry.ArtistEntry -> {
            val ar = entry.artist
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Artist(ar.id)) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                artimage(
                    url = ar.artwork,
                    shape = CircleShape,
                    fallbackIcon = ZIcons.Artist,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ar.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glassTokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ar.subscribers ?: "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkSecondary,
                        maxLines = 1,
                    )
                }
                Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
            }
        }

        is LibEntry.SongEntry -> {
            songrow(
                track = entry.track,
                playing = playingId == entry.track.id,
                onClick = { onPlaySong(entry.track) },
                onMore = { onTrackMenu(entry.track) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun libempty(
    icon: ImageVector,
    text: String,
) {
    val glassTokens = LocalGlass.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 56.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glassTokens.inkMuted,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = glassTokens.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
