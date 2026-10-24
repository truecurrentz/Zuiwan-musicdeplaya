package zuiwan.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.PlaylistDetail
import zuiwan.model.Track
import zuiwan.model.upscaleart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.DetailHero
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassPillButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@Composable
fun viewplaylist(
    playlistId: String,
    local: Boolean,
    onBack: () -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val playback by hub.pilot.state.collectAsState()

    var detail by remember(playlistId, local) { mutableStateOf<PlaylistDetail?>(null) }
    var loading by remember(playlistId, local) { mutableStateOf(true) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val isLocal = local || playlistId.startsWith("local_")

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        if (uri != null) hub.exportm3u(playlistId, uri)
    }

    suspend fun reload() {
        loading = true
        detail = if (isLocal) {
            hub.loadlocalplaylist(playlistId)
        } else {
            hub.tube.loadplaylist(playlistId).getOrNull()
        }
        loading = false
    }

    LaunchedEffect(playlistId, local) {
        reload()
    }

    LaunchedEffect(nearEnd, detail?.continuation) {
        val cont = detail?.continuation
        if (nearEnd && !cont.isNullOrBlank() && !loading && !isLocal) {
            loading = true
            hub.tube.loadmoreplaylist(cont).onSuccess { (more, nextCont) ->
                val cur = detail ?: return@onSuccess
                detail = cur.copy(
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
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
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
                        hub.renameplaylist(playlistId, renameText)
                        showRename = false
                        scope.launch { reload() }
                    },
                ) {
                    Text("Save", color = glass.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel", color = glass.inkSecondary)
                }
            },
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (loading && detail == null) {
            item(key = "top") {
                GlassTopBar(title = "Playlist", onBack = onBack)
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
            val heroArt = upscaleart(
                info.playlist.artwork.ifBlank { info.tracks.firstOrNull()?.artwork.orEmpty() },
                1080,
            )
            val metaText = listOfNotNull(
                info.playlist.author,
                "${info.tracks.size} songs",
            ).joinToString(" - ")

            item(key = "hero") {
                DetailHero(
                    title = info.playlist.title,
                    artworkUrl = heroArt,
                    fallbackIcon = ZIcons.Playlist,
                    onBack = onBack,
                    subtitle = info.description?.takeIf { it.isNotBlank() },
                    metadata = metaText,
                    onTitleClick = if (isLocal) {
                        {
                            renameText = info.playlist.title
                            showRename = true
                        }
                    } else {
                        null
                    },
                    topActions = {
                        if (isLocal) {
                            GlassIconButton(
                                icon = ZIcons.Share,
                                onClick = { exportLauncher.launch("${info.playlist.title}.m3u") },
                            )
                            GlassIconButton(
                                icon = ZIcons.Trash,
                                onClick = {
                                    hub.deleteplaylist(playlistId)
                                    onBack()
                                },
                            )
                        }
                    },
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
                                hub.pilot.playtracks(info.tracks.shuffled(), 0, info.playlist.title, fetchRadio = false)
                            },
                            size = 46.dp,
                            iconSize = 20.dp,
                        )
                        GlassPillButton(
                            text = "Play",
                            icon = ZIcons.Play,
                            highContrast = true,
                            onClick = {
                                hub.pilot.playtracks(info.tracks, 0, info.playlist.title, fetchRadio = false)
                            },
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
                    playing = playback.current?.id == song.id,
                    cardBackground = playback.current?.id == song.id,
                    onClick = {
                        hub.pilot.playtracks(info.tracks, idx, info.playlist.title, fetchRadio = false)
                    },
                    onMore = { onTrackMenu(song) },
                    trailing = if (isLocal) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (idx > 0) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val list = info.tracks.map { it.id }.toMutableList()
                                                val moved = list.removeAt(idx)
                                                list.add(idx - 1, moved)
                                                hub.reorderplaylist(playlistId, list)
                                                scope.launch { reload() }
                                            },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.ArrowUp,
                                            contentDescription = "Move up",
                                            tint = glass.inkSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                if (idx < info.tracks.lastIndex) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val list = info.tracks.map { it.id }.toMutableList()
                                                val moved = list.removeAt(idx)
                                                list.add(idx + 1, moved)
                                                hub.reorderplaylist(playlistId, list)
                                                scope.launch { reload() }
                                            },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.ArrowDown,
                                            contentDescription = "Move down",
                                            tint = glass.inkSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            hub.removefromplaylist(playlistId, song.id)
                                            scope.launch { reload() }
                                        },
                                ) {
                                    Icon(
                                        imageVector = ZIcons.Close,
                                        contentDescription = "Remove",
                                        tint = glass.inkMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }
}
