package zuiwan.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import zuiwan.model.PlaylistDetail
import zuiwan.model.PlaylistSummary
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.trackart
import zuiwan.model.upscaleart
import zuiwan.sheet.exportsheet
import zuiwan.sheet.importsheet
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.hero
import zuiwan.ui.glass
import zuiwan.ui.iconbtn
import zuiwan.ui.topbar
import zuiwan.ui.covergrid
import zuiwan.ui.scrollhost
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll

@Composable
fun viewplaylist(
    playlistId: String,
    local: Boolean,
    onBack: () -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val playback by hub.pilot.state.collectAsState()
    val savedPlaylists by hub.playlists.collectAsState()
    val likedTracks by hub.likedTracks.collectAsState()
    val downloadedTracks by hub.downloadedTracks.collectAsState()
    val historyTracks by hub.historyTracks.collectAsState()
    val topTracks by hub.topTracks.collectAsState()
    val session by hub.session.collectAsState()

    val isAuto = playlistId.startsWith("auto_")
    val cleanRemoteId = playlistId.removePrefix("VL").removePrefix("yt_")
    val storedPl = remember(savedPlaylists, playlistId) {
        savedPlaylists.firstOrNull { it.id == playlistId || it.remoteId == cleanRemoteId }
    }
    val dbPlaylistId = storedPl?.id ?: playlistId
    val isStored = !isAuto && (storedPl != null || local || playlistId.startsWith("local_") || playlistId.startsWith("yt_"))
    val isLocalEditable = !isAuto && (local && storedPl == null || playlistId.startsWith("local_") || storedPl?.editable == true)

    var remoteOrLocalDetail by remember(playlistId, local) { mutableStateOf<PlaylistDetail?>(null) }
    var loading by remember(playlistId, local) { mutableStateOf(!isAuto) }
    var showRename by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImportSongs by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    var draggingIdx by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 68.dp.toPx() }

    val headerScroll = headerscroll()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        query = ""
    }

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
    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }

    suspend fun reload() {
        if (isAuto) return
        loading = true
        if (isStored) {
            var localRes = hub.localplaylist(dbPlaylistId)
            val remoteRef = storedPl?.remoteId ?: playlistId.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")
            if ((localRes == null || localRes.tracks.isEmpty()) && !remoteRef.isNullOrBlank()) {
                runCatching { hub.sync.syncone(dbPlaylistId) }
                localRes = hub.localplaylist(dbPlaylistId) ?: hub.tube.loadplaylist(remoteRef).getOrNull()
            }
            remoteOrLocalDetail = localRes
        } else {
            remoteOrLocalDetail = hub.tube.loadplaylist(playlistId).getOrNull()
        }
        loading = false
    }

    LaunchedEffect(playlistId, local, savedPlaylists.size) {
        reload()
    }

    val detail: PlaylistDetail? = when (playlistId) {
        "auto_liked" -> PlaylistDetail(
            playlist = PlaylistSummary(
                id = "auto_liked",
                title = "Liked Songs",
                author = "Your Library",
                local = true,
                thumbnails = likedTracks.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
                songCount = likedTracks.size,
            ),
            tracks = likedTracks,
        )
        "auto_offline" -> PlaylistDetail(
            playlist = PlaylistSummary(
                id = "auto_offline",
                title = "Offline",
                author = "Downloaded Music",
                local = true,
                thumbnails = downloadedTracks.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
                songCount = downloadedTracks.size,
            ),
            tracks = downloadedTracks,
        )
        "auto_cached" -> PlaylistDetail(
            playlist = PlaylistSummary(
                id = "auto_cached",
                title = "Cached",
                author = "Recent & Instant Playback",
                local = true,
                thumbnails = historyTracks.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
                songCount = historyTracks.size,
            ),
            tracks = historyTracks,
        )
        "auto_top" -> {
            val pool = topTracks.ifEmpty { historyTracks }.take(50)
            PlaylistDetail(
                playlist = PlaylistSummary(
                    id = "auto_top",
                    title = "My Top 50",
                    author = "Most Played",
                    local = true,
                    thumbnails = pool.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4),
                    songCount = pool.size,
                ),
                tracks = pool,
            )
        }
        else -> remoteOrLocalDetail
    }

    LaunchedEffect(nearEnd, remoteOrLocalDetail?.continuation) {
        val cont = remoteOrLocalDetail?.continuation
        if (nearEnd && !cont.isNullOrBlank() && !loading && !isLocalEditable && !isAuto) {
            loading = true
            hub.tube.moreplaylist(cont).onSuccess { (more, nextCont) ->
                val cur = remoteOrLocalDetail ?: return@onSuccess
                remoteOrLocalDetail = cur.copy(
                    tracks = cur.tracks + more,
                    continuation = nextCont,
                )
            }
            loading = false
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = glassTokens.glassStrong,
            titleContentColor = glassTokens.ink,
            textContentColor = glassTokens.ink,
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        hub.renameplaylist(dbPlaylistId, renameText)
                        showRename = false
                        scope.launch { reload() }
                    },
                ) {
                    Text("Save", color = glassTokens.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel", color = glassTokens.inkSecondary)
                }
            },
        )
    }

    if (showExport && detail != null) {
        exportsheet(
            hub = hub,
            title = detail.playlist.title,
            tracks = detail.tracks,
            artwork = detail.playlist.artwork,
            onDismiss = { showExport = false },
        )
    }

    if (showImportSongs && isLocalEditable) {
        importsheet(
            hub = hub,
            targetPlaylistId = dbPlaylistId,
            onDismiss = { showImportSongs = false },
            onImported = {
                scope.launch { reload() }
            },
        )
    }

    val autoFallbackIcon = when (playlistId) {
        "auto_liked" -> ZIcons.HeartFilled
        "auto_offline" -> ZIcons.Offline
        "auto_cached" -> ZIcons.History
        "auto_top" -> ZIcons.Trend
        else -> ZIcons.Playlist
    }

    val isBookmarked = remember(savedPlaylists, playlistId) {
        savedPlaylists.any { it.id == playlistId || it.remoteId == cleanRemoteId }
    }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        overlayHero = true,
        header = {
            topbar(
                title = detail?.playlist?.title ?: "Playlist",
                showTitle = pastHero || detail == null,
                onBack = {
                    if (searchOpen) {
                        searchOpen = false
                        query = ""
                    } else {
                        onBack()
                    }
                },
                actions = {
                    if (detail != null) {
                        iconbtn(
                            icon = if (searchOpen) ZIcons.Close else ZIcons.Search,
                            onClick = {
                                if (searchOpen) {
                                    searchOpen = false
                                    query = ""
                                } else {
                                    searchOpen = true
                                }
                            },
                        )
                        if (detail.tracks.isNotEmpty()) {
                            iconbtn(
                                icon = ZIcons.Export,
                                onClick = { showExport = true },
                            )
                        }
                        if (!isAuto && (isStored || detail.tracks.isNotEmpty())) {
                            Box {
                                iconbtn(
                                    icon = ZIcons.MoreHoriz,
                                    onClick = { menuOpen = true },
                                )
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    containerColor = glassTokens.glassStrong,
                                ) {
                                    if (session.active && isStored) {
                                        val hasRemote = !storedPl?.remoteId.isNullOrBlank() || playlistId.startsWith("yt_")
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (hasRemote) "Sync with YouTube Music" else "Sync to YouTube Music",
                                                    color = glassTokens.ink,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (hasRemote) ZIcons.Sync else ZIcons.Cloud,
                                                    null,
                                                    tint = glassTokens.accent,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            onClick = {
                                                menuOpen = false
                                                hub.syncplaylist(dbPlaylistId) {
                                                    scope.launch { reload() }
                                                }
                                            },
                                        )
                                    }
                                    if (isLocalEditable) {
                                        DropdownMenuItem(
                                            text = { Text("Import songs", color = glassTokens.ink) },
                                            leadingIcon = { Icon(ZIcons.Import, null, tint = glassTokens.inkSecondary, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                menuOpen = false
                                                showImportSongs = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename playlist", color = glassTokens.ink) },
                                            leadingIcon = { Icon(ZIcons.Edit, null, tint = glassTokens.inkSecondary, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                menuOpen = false
                                                renameText = detail.playlist.title
                                                showRename = true
                                            },
                                        )
                                    }
                                    if (detail.tracks.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Export playlist", color = glassTokens.ink) },
                                            leadingIcon = { Icon(ZIcons.Export, null, tint = glassTokens.inkSecondary, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                menuOpen = false
                                                showExport = true
                                            },
                                        )
                                    }
                                    if (isStored) {
                                        DropdownMenuItem(
                                            text = { Text("Delete playlist", color = glassTokens.danger) },
                                            leadingIcon = { Icon(ZIcons.Trash, null, tint = glassTokens.danger, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                menuOpen = false
                                                hub.deleteplaylist(dbPlaylistId)
                                                onBack()
                                            },
                                        )
                                    }
                                }
                            }
                        }
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
                val thumbs = info.playlist.thumbnails.ifEmpty {
                    info.tracks.mapNotNull { it.trackart().takeIf(String::isNotBlank) }.distinct().take(4)
                }
                val rawHeroArt = info.playlist.artwork.ifBlank { thumbs.firstOrNull().orEmpty() }
                val heroArt = upscaleart(rawHeroArt, 1080)

                val totalSecs = info.tracks.sumOf { it.duration.coerceAtLeast(0) }
                val durationLabel = formatspan(totalSecs)
                val metaText = listOfNotNull(
                    "${info.tracks.size} songs",
                    durationLabel,
                ).joinToString(" - ")

                val cleanQ = query.trim().lowercase()
                val visibleTracks = if (cleanQ.isBlank()) {
                    info.tracks.mapIndexed { i, t -> i to t }
                } else {
                    info.tracks.mapIndexed { i, t -> i to t }.filter { (_, s) ->
                        s.title.lowercase().contains(cleanQ) ||
                            s.artistline().lowercase().contains(cleanQ)
                    }
                }

                item(key = "hero") {
                    hero(
                        title = info.playlist.title,
                        artworkUrl = heroArt,
                        fallbackIcon = autoFallbackIcon,
                        artworkSlot = if (isAuto || (info.playlist.artwork.isBlank() && thumbs.isNotEmpty())) {
                            {
                                covergrid(
                                    thumbnails = thumbs,
                                    fallbackIcon = autoFallbackIcon,
                                    preferIcon = isAuto && thumbs.isEmpty(),
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            null
                        },
                        subtitle = info.playlist.author,
                        metadata = metaText,
                        description = info.description?.takeIf { it.isNotBlank() },
                        onTitleClick = if (isLocalEditable) {
                            {
                                renameText = info.playlist.title
                                showRename = true
                            }
                        } else {
                            null
                        },
                        primaryActions = {
                            if (!isAuto && !isLocalEditable) {
                                iconbtn(
                                    icon = if (isBookmarked) ZIcons.HeartFilled else ZIcons.Heart,
                                    active = isBookmarked,
                                    softFill = true,
                                    size = 48.dp,
                                    iconSize = 21.dp,
                                    onClick = { hub.saveplaylist(info.playlist) },
                                )
                            } else if (playlistId != "auto_offline") {
                                iconbtn(
                                    icon = ZIcons.Download,
                                    softFill = true,
                                    size = 48.dp,
                                    iconSize = 21.dp,
                                    onClick = { info.tracks.forEach { hub.vault.downloadtrack(it) } },
                                )
                            }

                            iconbtn(
                                icon = ZIcons.Play,
                                solidAccent = true,
                                size = 64.dp,
                                iconSize = 28.dp,
                                onClick = {
                                    if (info.tracks.isNotEmpty()) {
                                        hub.pilot.playtracks(
                                            info.tracks,
                                            0,
                                            info.playlist.title,
                                            fetchRadio = false,
                                        )
                                    }
                                },
                            )

                            iconbtn(
                                icon = ZIcons.Shuffle,
                                softFill = true,
                                size = 48.dp,
                                iconSize = 21.dp,
                                onClick = {
                                    if (info.tracks.isNotEmpty()) {
                                        hub.pilot.playtracks(
                                            info.tracks.shuffled(),
                                            0,
                                            info.playlist.title,
                                            fetchRadio = false,
                                        )
                                    }
                                },
                            )
                        },
                    )
                }

                if (searchOpen) {
                    item(key = "pl_search") {
                        glass(
                            shape = RoundedCornerShape(999.dp),
                            elevated = true,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(ZIcons.Search, null, tint = glassTokens.inkSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "Filter songs in playlist",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = glassTokens.inkSecondary,
                                        )
                                    }
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassTokens.ink),
                                        cursorBrush = SolidColor(glassTokens.accent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                    )
                                }
                                if (query.isNotEmpty()) {
                                    iconbtn(
                                        icon = ZIcons.Close,
                                        onClick = { query = "" },
                                        size = 28.dp,
                                        iconSize = 15.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (visibleTracks.isEmpty()) {
                    item(key = "empty_tracks") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 40.dp),
                        ) {
                            Text(
                                text = if (cleanQ.isNotBlank()) "No matching songs" else "No songs in this playlist yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = glassTokens.inkSecondary,
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = visibleTracks,
                    key = { _, pair -> "${pair.second.id}_${pair.first}" },
                ) { viewIdx, (origIdx, song) ->
                    val isDragging = draggingIdx == origIdx && cleanQ.isBlank()
                    songrow(
                        track = song,
                        playing = playback.current?.id == song.id,
                        onClick = {
                            hub.pilot.playtracks(info.tracks, origIdx, info.playlist.title, fetchRadio = false)
                        },
                        onMore = { onTrackMenu(song) },
                        trailing = if (isLocalEditable && cleanQ.isBlank()) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                hub.droptrack(dbPlaylistId, song.id)
                                                scope.launch { reload() }
                                            },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.Close,
                                            contentDescription = "Remove",
                                            tint = glassTokens.inkMuted,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .pointerInput(origIdx, info.tracks.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingIdx = origIdx
                                                        dragOffsetPx = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingIdx = -1
                                                        dragOffsetPx = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggingIdx = -1
                                                        dragOffsetPx = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetPx += dragAmount.y
                                                        val steps = (dragOffsetPx / rowHeightPx).toInt()
                                                        if (steps != 0 && draggingIdx in info.tracks.indices) {
                                                            val target = (draggingIdx + steps).coerceIn(0, info.tracks.lastIndex)
                                                            if (target != draggingIdx) {
                                                                val ids = info.tracks.map { it.id }.toMutableList()
                                                                val moved = ids.removeAt(draggingIdx)
                                                                ids.add(target, moved)
                                                                draggingIdx = target
                                                                dragOffsetPx -= steps * rowHeightPx
                                                                hub.reorderplaylist(dbPlaylistId, ids)
                                                                scope.launch { reload() }
                                                            }
                                                        }
                                                    },
                                                )
                                            },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.Drag,
                                            contentDescription = "Reorder",
                                            tint = glassTokens.inkMuted,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onTrackMenu(song) },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.More,
                                            contentDescription = null,
                                            tint = glassTokens.inkSecondary,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetPx else 0f
                            }
                            .padding(horizontal = 8.dp),
                    )
                }

                if (loading && viewnone(detail == null)) {
                    item(key = "more_loader") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                        ) {
                            CircularProgressIndicator(color = glassTokens.accent, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun viewnone(flag: Boolean): Boolean = !flag

private fun formatspan(totalSeconds: Int): String? {
    if (totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "$hours hr $minutes min"
        minutes > 0 -> "$minutes min"
        else -> "${totalSeconds}s"
    }
}
