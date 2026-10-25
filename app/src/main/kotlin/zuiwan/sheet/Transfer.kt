package zuiwan.sheet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.Track
import zuiwan.state.Hub
import zuiwan.theme.LocalGlass
import zuiwan.tube.CsvMap
import zuiwan.tube.ExportKind
import zuiwan.tube.ImportBundle
import zuiwan.ui.ZIcons
import zuiwan.ui.artimage
import zuiwan.ui.chip
import zuiwan.ui.pillbtn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun createsheet(
    hub: Hub,
    onDismiss: () -> Unit,
    onOpenImport: (() -> Unit)? = null,
    onCreated: (String) -> Unit = {},
) {
    val glassTokens = LocalGlass.current
    val session by hub.session.collectAsState()
    val loggedIn = session.active
    var title by remember { mutableStateOf("") }
    var syncYoutube by remember(loggedIn) { mutableStateOf(loggedIn) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glassTokens.bgSecondary,
        contentColor = glassTokens.ink,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "New Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = glassTokens.ink,
                )
                if (onOpenImport != null) {
                    chip(
                        label = "Import",
                        selected = false,
                        leadingIcon = ZIcons.Import,
                        onClick = {
                            onDismiss()
                            onOpenImport()
                        },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(glassTokens.glassFill)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (title.isEmpty()) {
                    Text(
                        text = "Playlist title",
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassTokens.inkMuted,
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassTokens.ink),
                    cursorBrush = SolidColor(glassTokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (loggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(glassTokens.glassFill)
                        .clickable { syncYoutube = !syncYoutube }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(glassTokens.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ZIcons.Cloud,
                            contentDescription = null,
                            tint = glassTokens.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync with YouTube Music",
                            style = MaterialTheme.typography.titleSmall,
                            color = glassTokens.ink,
                        )
                        Text(
                            text = "Create on your YouTube Music account and sync songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassTokens.inkSecondary,
                        )
                    }
                    Switch(
                        checked = syncYoutube,
                        onCheckedChange = { syncYoutube = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = glassTokens.onAccent,
                            checkedTrackColor = glassTokens.accent,
                            uncheckedThumbColor = glassTokens.inkSecondary,
                            uncheckedTrackColor = glassTokens.glassStrong,
                            uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pillbtn(
                    text = "Cancel",
                    icon = ZIcons.Close,
                    primary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                pillbtn(
                    text = "Create",
                    icon = ZIcons.Check,
                    primary = true,
                    onClick = {
                        val clean = title.trim()
                        if (clean.isNotEmpty()) {
                            onDismiss()
                            hub.createplaylist(
                                title = clean,
                                syncYoutube = loggedIn && syncYoutube,
                                onCreated = onCreated,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun importsheet(
    hub: Hub,
    targetPlaylistId: String? = null,
    onDismiss: () -> Unit,
    onImported: (String) -> Unit = {},
) {
    val glassTokens = LocalGlass.current
    val scope = rememberCoroutineScope()
    val session by hub.session.collectAsState()
    val loggedIn = session.active

    var mode by remember { mutableIntStateOf(0) }
    var rawText by remember { mutableStateOf("") }
    var bundle by remember { mutableStateOf<ImportBundle?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var syncYoutube by remember(loggedIn) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            busy = true
            errorMsg = null
            scope.launch {
                hub.transfer.inspecturi(uri)
                    .onSuccess { res ->
                        bundle = res
                        playlistName = res.title
                    }
                    .onFailure { err ->
                        errorMsg = err.message ?: "Unable to parse selected file"
                    }
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glassTokens.bgSecondary,
        contentColor = glassTokens.ink,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (targetPlaylistId != null) "Import Songs" else "Import Playlist",
                style = MaterialTheme.typography.titleLarge,
                color = glassTokens.ink,
            )
            Text(
                text = "Supports CSV, Exportify, M3U/M3U8, JSON, PLS, XSPF, TXT, YouTube Music & Spotify links, or pasted song lists.",
                style = MaterialTheme.typography.bodySmall,
                color = glassTokens.inkSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chip(
                    label = "Choose File",
                    selected = mode == 0,
                    leadingIcon = ZIcons.Folder,
                    onClick = { mode = 0 },
                )
                chip(
                    label = "Link or Song List",
                    selected = mode == 1,
                    leadingIcon = ZIcons.Globe,
                    onClick = { mode = 1 },
                )
            }

            if (mode == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(glassTokens.glassFill)
                        .clickable { filePicker.launch(arrayOf("*/*")) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(glassTokens.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ZIcons.Import,
                            contentDescription = null,
                            tint = glassTokens.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bundle != null) "Choose Another File" else "Select Playlist File",
                            style = MaterialTheme.typography.titleSmall,
                            color = glassTokens.ink,
                        )
                        Text(
                            text = ".csv, .m3u, .m3u8, .json, .pls, .xspf, .txt",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassTokens.inkSecondary,
                        )
                    }
                    Icon(
                        imageVector = ZIcons.ChevronRight,
                        contentDescription = null,
                        tint = glassTokens.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 180.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(glassTokens.glassFill)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        if (rawText.isEmpty()) {
                            Text(
                                text = "Paste a YouTube Music or Spotify playlist URL, or lines like:\nArtist - Song Title",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glassTokens.inkMuted,
                            )
                        }
                        BasicTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = glassTokens.ink),
                            cursorBrush = SolidColor(glassTokens.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    pillbtn(
                        text = "Analyze Input",
                        icon = ZIcons.Sparkles,
                        primary = false,
                        onClick = {
                            if (rawText.isNotBlank() && !busy) {
                                busy = true
                                errorMsg = null
                                scope.launch {
                                    hub.transfer.inspecttext(rawText)
                                        .onSuccess { res ->
                                            bundle = res
                                            if (playlistName.isBlank()) playlistName = res.title
                                        }
                                        .onFailure { err ->
                                            errorMsg = err.message ?: "Could not parse playlist text"
                                        }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (busy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = glassTokens.accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Reading playlist...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkSecondary,
                    )
                }
            }

            errorMsg?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassTokens.danger.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = ZIcons.Alert,
                        contentDescription = null,
                        tint = glassTokens.danger,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.danger,
                    )
                }
            }

            bundle?.let { cur ->
                if (targetPlaylistId == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(glassTokens.glassFill)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        if (playlistName.isEmpty()) {
                            Text(
                                text = "Playlist name",
                                style = MaterialTheme.typography.bodyLarge,
                                color = glassTokens.inkMuted,
                            )
                        }
                        BasicTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassTokens.ink),
                            cursorBrush = SolidColor(glassTokens.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                cur.csv?.let { csv ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(glassTokens.glassFill)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "CSV Column Mapping",
                                style = MaterialTheme.typography.titleSmall,
                                color = glassTokens.ink,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Header row",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glassTokens.inkSecondary,
                                )
                                Switch(
                                    checked = csv.hasHeader,
                                    onCheckedChange = { checked ->
                                        bundle = hub.transfer.applycsv(cur, csv.copy(hasHeader = checked))
                                    },
                                )
                            }
                        }

                        colpicker(
                            label = "Title",
                            headers = csv.headers,
                            selected = csv.titleCol,
                            allowNone = false,
                            onSelect = { idx ->
                                bundle = hub.transfer.applycsv(cur, csv.copy(titleCol = idx))
                            },
                        )
                        colpicker(
                            label = "Artist",
                            headers = csv.headers,
                            selected = csv.artistCol,
                            allowNone = true,
                            onSelect = { idx ->
                                bundle = hub.transfer.applycsv(cur, csv.copy(artistCol = idx))
                            },
                        )
                        if (csv.headers.size >= 3) {
                            colpicker(
                                label = "Video ID",
                                headers = csv.headers,
                                selected = csv.idCol,
                                allowNone = true,
                                onSelect = { idx ->
                                    bundle = hub.transfer.applycsv(cur, csv.copy(idCol = idx))
                                },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(glassTokens.glassFill)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${cur.songs.size} tracks detected",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = glassTokens.accent,
                    )
                    cur.songs.take(4).forEach { s ->
                        val line = if (s.artist.isNotBlank()) "${s.title} · ${s.artist}" else s.title
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = glassTokens.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (cur.songs.size > 4) {
                        Text(
                            text = "+ ${cur.songs.size - 4} more songs",
                            style = MaterialTheme.typography.labelSmall,
                            color = glassTokens.inkMuted,
                        )
                    }
                }

                if (loggedIn && targetPlaylistId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(glassTokens.glassFill)
                            .clickable { syncYoutube = !syncYoutube }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = ZIcons.Cloud,
                            contentDescription = null,
                            tint = glassTokens.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Sync imported playlist to YouTube Music",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glassTokens.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = syncYoutube,
                            onCheckedChange = { syncYoutube = it },
                        )
                    }
                }

                pillbtn(
                    text = "Import ${cur.songs.size} Songs",
                    icon = ZIcons.Check,
                    primary = true,
                    onClick = {
                        if (cur.songs.isNotEmpty()) {
                            val finalTitle = playlistName.trim().ifBlank { cur.title }
                            onDismiss()
                            hub.startimport(
                                title = finalTitle,
                                songs = cur.songs,
                                syncYoutube = loggedIn && syncYoutube,
                                targetPlaylistId = targetPlaylistId,
                                onCreated = onImported,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun colpicker(
    label: String,
    headers: List<String>,
    selected: Int,
    allowNone: Boolean,
    onSelect: (Int) -> Unit,
) {
    val glassTokens = LocalGlass.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = glassTokens.inkMuted,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (allowNone) {
                chip(
                    label = "None",
                    selected = selected < 0,
                    onClick = { onSelect(-1) },
                )
            }
            headers.forEachIndexed { idx, name ->
                chip(
                    label = name.take(18),
                    selected = selected == idx,
                    onClick = { onSelect(idx) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun exportsheet(
    hub: Hub,
    title: String,
    tracks: List<Track>,
    artwork: String = "",
    onDismiss: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    var kind by remember { mutableStateOf(ExportKind.CSV) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null) {
            onDismiss()
            hub.exporturi(uri, title, tracks, kind)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glassTokens.bgSecondary,
        contentColor = glassTokens.ink,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                artimage(
                    url = artwork.ifBlank { tracks.firstOrNull()?.artwork },
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Playlist",
                        style = MaterialTheme.typography.labelMedium,
                        color = glassTokens.accent,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = glassTokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${tracks.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.inkSecondary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportKind.entries.forEach { k ->
                    chip(
                        label = "${k.label} (.${k.ext})",
                        selected = kind == k,
                        onClick = { kind = k },
                    )
                }
            }

            val formatHint = when (kind) {
                ExportKind.CSV -> "Exports Title, Artist, Album, and YouTube Video ID — compatible with Metrolist, Excel, and Spotify tools."
                ExportKind.M3U -> "Exports an extended #EXTM3U playlist with durations and direct YouTube Music links."
                ExportKind.TXT -> "Exports a clean numbered tracklist with song links for notes and messaging."
            }
            Text(
                text = formatHint,
                style = MaterialTheme.typography.bodySmall,
                color = glassTokens.inkSecondary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pillbtn(
                    text = "Share ${kind.label} File",
                    icon = ZIcons.Share,
                    primary = true,
                    onClick = {
                        onDismiss()
                        hub.exportshare(title, tracks, kind)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pillbtn(
                        text = "Save to Documents",
                        icon = ZIcons.Download,
                        primary = false,
                        onClick = {
                            onDismiss()
                            hub.exportsave(title, tracks, kind)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    pillbtn(
                        text = "Pick Folder",
                        icon = ZIcons.Folder,
                        primary = false,
                        onClick = {
                            val clean = title.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "Playlist" }
                            saveLauncher.launch("$clean.${kind.ext}")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
