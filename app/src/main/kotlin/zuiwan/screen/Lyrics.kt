package zuiwan.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import zuiwan.lyrics.LyricParser
import zuiwan.model.LyricLine
import zuiwan.model.LyricPayload
import zuiwan.model.artistline
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.glass
import zuiwan.ui.chip
import zuiwan.ui.iconbtn
import zuiwan.ui.ZIcons

@Composable
fun viewlyrics(
    modifier: Modifier = Modifier,
    showHeaderControls: Boolean = true,
    externalFinderOpen: Boolean = false,
    onDismissExternalFinder: () -> Unit = {},
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val scope = rememberCoroutineScope()

    val state by hub.pilot.state.collectAsState()
    val payload by hub.pilot.lyrics.collectAsState()
    val loading by hub.pilot.lyricsLoading.collectAsState()
    val settings by hub.settings.collectAsState()

    var plainMode by remember { mutableStateOf(false) }
    var internalFinder by remember { mutableStateOf(false) }
    val finderVisible = internalFinder || externalFinderOpen
    var searchQuery by remember(state.current?.id) {
        mutableStateOf(
            state.current?.let { "${it.title} ${it.artistline()}" }.orEmpty(),
        )
    }
    var candidates by remember { mutableStateOf<List<LyricPayload>>(emptyList()) }

    LaunchedEffect(finderVisible) {
        if (finderVisible && candidates.isEmpty() && searchQuery.isNotBlank()) {
            candidates = hub.lyrics.searchlyrics(searchQuery, settings.romanizeLyrics)
        }
    }

    val activeIdx = if (payload.synced && !plainMode) {
        LyricParser.activeline(payload.lines, state.positionMs)
    } else {
        -1
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeIdx, plainMode) {
        if (activeIdx >= 0 && !plainMode && !listState.isScrollInProgress) {
            listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0))
        }
    }

    fun closefinder() {
        internalFinder = false
        onDismissExternalFinder()
    }

    if (finderVisible) {
        AlertDialog(
            onDismissRequest = ::closefinder,
            containerColor = glassTokens.glassStrong,
            titleContentColor = glassTokens.ink,
            textContentColor = glassTokens.ink,
            title = { Text("Search Lyrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        iconbtn(
                            icon = ZIcons.Search,
                            onClick = {
                                scope.launch {
                                    candidates = hub.lyrics.searchlyrics(searchQuery, settings.romanizeLyrics)
                                }
                            },
                        )
                    }
                    candidates.take(5).forEach { cand ->
                        glass(
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val track = state.current ?: return@glass
                                scope.launch {
                                    hub.lyrics.applylyrics(track.id, cand)
                                    hub.pilot.refreshlyrics(track, force = false)
                                    closefinder()
                                }
                            },
                            contentPadding = PaddingValues(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(cand.source, style = MaterialTheme.typography.titleSmall, color = glassTokens.accent)
                                Text(
                                    cand.lines.take(2).joinToString(" / ") { it.text },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = ::closefinder) {
                    Text("Close", color = glassTokens.accent)
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showHeaderControls) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chip(
                        label = "Synced",
                        selected = !plainMode,
                        onClick = { plainMode = false },
                    )
                    chip(
                        label = "Plain",
                        selected = plainMode,
                        onClick = { plainMode = true },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    iconbtn(
                        icon = ZIcons.Search,
                        size = 34.dp,
                        iconSize = 16.dp,
                        onClick = { internalFinder = true },
                    )
                    iconbtn(
                        icon = ZIcons.Refresh,
                        size = 34.dp,
                        iconSize = 16.dp,
                        onClick = { hub.pilot.refreshlyrics(force = true) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (loading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator(color = glassTokens.accent)
            }
        } else if (payload.lines.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = ZIcons.Lyrics,
                        contentDescription = null,
                        tint = glassTokens.inkMuted,
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No lyrics found",
                        style = MaterialTheme.typography.titleLarge,
                        color = glassTokens.inkSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap the search button above to choose a lyric match",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(payload.lines) { idx, line ->
                    lyricline(
                        line = line,
                        active = !plainMode && idx == activeIdx,
                        plainMode = plainMode || !payload.synced,
                        positionMs = state.positionMs,
                        showRomanized = settings.romanizeLyrics,
                        onSeek = {
                            if (payload.synced) hub.pilot.seekto(line.timeMs)
                        },
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(
                            text = "Source: ${payload.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = glassTokens.inkMuted,
                        )
                        chip(
                            label = if (plainMode) "Synced" else "Plain",
                            selected = false,
                            onClick = { plainMode = !plainMode },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun lyricline(
    line: LyricLine,
    active: Boolean,
    plainMode: Boolean,
    positionMs: Long,
    showRomanized: Boolean,
    onSeek: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    val baseColor by animateColorAsState(
        targetValue = when {
            plainMode -> glassTokens.ink
            active -> glassTokens.ink
            else -> glassTokens.inkMuted.copy(alpha = 0.42f)
        },
        animationSpec = tween(220),
        label = "lyricColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSeek)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        if (line.text.isBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Icon(
                    imageVector = ZIcons.Note,
                    contentDescription = null,
                    tint = if (active) glassTokens.ink else glassTokens.inkMuted.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else if (active && line.words.isNotEmpty() && !plainMode) {
            val styled = buildAnnotatedString {
                line.words.forEach { w ->
                    val sung = positionMs >= w.startMs
                    withStyle(
                        SpanStyle(
                            color = if (sung) glassTokens.ink else glassTokens.ink.copy(alpha = 0.46f),
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(w.text)
                    }
                }
            }
            Text(
                text = styled,
                fontSize = 27.sp,
                lineHeight = 35.sp,
            )
        } else {
            Text(
                text = line.text,
                fontSize = if (active && !plainMode) 27.sp else 23.sp,
                lineHeight = if (active && !plainMode) 35.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                color = baseColor,
            )
        }

        if (showRomanized && !line.romanized.isNullOrBlank()) {
            Text(
                text = line.romanized,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = if (active || plainMode) glassTokens.ink.copy(alpha = 0.85f) else glassTokens.inkMuted.copy(alpha = 0.36f),
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        if (!line.translated.isNullOrBlank()) {
            Text(
                text = line.translated,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active || plainMode) glassTokens.inkSecondary else glassTokens.inkMuted.copy(alpha = 0.36f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
