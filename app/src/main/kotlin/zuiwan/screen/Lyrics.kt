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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassIconButton
import zuiwan.ui.ZIcons

@Composable
fun viewlyrics(
    modifier: Modifier = Modifier,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()

    val state by hub.pilot.state.collectAsState()
    val payload by hub.pilot.lyrics.collectAsState()
    val loading by hub.pilot.lyricsLoading.collectAsState()
    val settings by hub.settings.collectAsState()

    var plainMode by remember { mutableStateOf(false) }
    var showFinder by remember { mutableStateOf(false) }
    var searchQuery by remember(state.current?.id) {
        mutableStateOf(
            state.current?.let { "${it.title} ${it.artistline()}" }.orEmpty(),
        )
    }
    var candidates by remember { mutableStateOf<List<LyricPayload>>(emptyList()) }

    val activeIdx = if (payload.synced && !plainMode) {
        LyricParser.activeline(payload.lines, state.positionMs)
    } else {
        -1
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeIdx, plainMode) {
        if (activeIdx >= 0 && !plainMode && !listState.isScrollInProgress) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
    }

    if (showFinder) {
        AlertDialog(
            onDismissRequest = { showFinder = false },
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
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
                        GlassIconButton(
                            icon = ZIcons.Search,
                            onClick = {
                                scope.launch {
                                    candidates = hub.lyrics.searchlyrics(searchQuery, settings.romanizeLyrics)
                                }
                            },
                        )
                    }
                    candidates.take(5).forEach { cand ->
                        GlassBox(
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val track = state.current ?: return@GlassBox
                                scope.launch {
                                    hub.lyrics.applylyrics(track.id, cand)
                                    hub.pilot.refreshlyrics(track, force = false)
                                    showFinder = false
                                }
                            },
                            contentPadding = PaddingValues(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(cand.source, style = MaterialTheme.typography.titleSmall, color = glass.accent)
                                Text(
                                    cand.lines.take(2).joinToString(" / ") { it.text },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.inkSecondary,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFinder = false }) {
                    Text("Close", color = glass.accent)
                }
            },
        )
    }

    GlassBox(
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        label = "Synced",
                        selected = !plainMode,
                        onClick = { plainMode = false },
                    )
                    GlassChip(
                        label = "Plain",
                        selected = plainMode,
                        onClick = { plainMode = true },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassIconButton(
                        icon = ZIcons.Search,
                        size = 34.dp,
                        iconSize = 16.dp,
                        onClick = {
                            showFinder = true
                            scope.launch {
                                candidates = hub.lyrics.searchlyrics(searchQuery, settings.romanizeLyrics)
                            }
                        },
                    )
                    GlassIconButton(
                        icon = ZIcons.Refresh,
                        size = 34.dp,
                        iconSize = 16.dp,
                        onClick = { hub.pilot.refreshlyrics(force = true) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (loading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator(color = glass.accent)
                }
            } else if (payload.lines.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No lyrics found",
                            style = MaterialTheme.typography.titleLarge,
                            color = glass.inkSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap the search button above to pick a lyric match",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.inkMuted,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(payload.lines) { idx, line ->
                        LyricLineView(
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
                        Text(
                            text = "Source: ${payload.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.inkMuted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineView(
    line: LyricLine,
    active: Boolean,
    plainMode: Boolean,
    positionMs: Long,
    showRomanized: Boolean,
    onSeek: () -> Unit,
) {
    val glass = LocalGlass.current
    val baseColor by animateColorAsState(
        targetValue = when {
            plainMode -> glass.ink
            active -> glass.ink
            else -> glass.inkMuted.copy(alpha = 0.52f)
        },
        animationSpec = tween(220),
        label = "lyricColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSeek)
            .padding(vertical = 4.dp, horizontal = 4.dp),
    ) {
        if (active && line.words.isNotEmpty() && !plainMode) {
            val styled = buildAnnotatedString {
                line.words.forEach { w ->
                    val sung = positionMs >= w.startMs
                    withStyle(
                        SpanStyle(
                            color = if (sung) glass.accent else glass.ink.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(w.text)
                    }
                }
            }
            Text(
                text = styled,
                fontSize = 23.sp,
                lineHeight = 31.sp,
            )
        } else {
            Text(
                text = line.text.ifBlank { "♪" },
                fontSize = if (active && !plainMode) 23.sp else 18.sp,
                lineHeight = if (active && !plainMode) 31.sp else 25.sp,
                fontWeight = if (active && !plainMode) FontWeight.Bold else FontWeight.SemiBold,
                color = baseColor,
            )
        }

        if (showRomanized && !line.romanized.isNullOrBlank()) {
            Text(
                text = line.romanized,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active || plainMode) glass.accent.copy(alpha = 0.9f) else glass.inkMuted.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (!line.translated.isNullOrBlank()) {
            Text(
                text = line.translated,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active || plainMode) glass.inkSecondary else glass.inkMuted.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
