package zuiwan.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import zuiwan.model.Track
import zuiwan.model.formatclock
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.iconbtn
import zuiwan.ui.songrow

@Composable
fun queueview(
    onClose: () -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val density = LocalDensity.current
    val state by hub.pilot.state.collectAsState()
    val listState = rememberLazyListState()

    var locked by rememberSaveable { mutableStateOf(false) }
    var draggingIdx by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 68.dp.toPx() }

    val upcoming = remember(state.queue, state.currentIndex) {
        val counts = HashMap<String, Int>(state.queue.size)
        state.queue.mapIndexedNotNull { idx, track ->
            if (idx <= state.currentIndex) return@mapIndexedNotNull null
            val nextCount = (counts[track.id] ?: 0) + 1
            counts[track.id] = nextCount
            Triple(idx, "${track.id}#$nextCount", track)
        }
    }

    val upcomingMs = remember(upcoming) {
        upcoming.sumOf { (_, _, t) -> t.duration.coerceAtLeast(0).toLong() } * 1_000L
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    append("${upcoming.size} in queue")
                    if (upcomingMs > 0L) {
                        append(" - ")
                        append(formatclock(upcomingMs))
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                iconbtn(
                    icon = ZIcons.Shuffle,
                    active = state.shuffle,
                    onClick = { hub.pilot.toggleshuffle() },
                    size = 38.dp,
                    iconSize = 19.dp,
                )
                iconbtn(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) ZIcons.RepeatOne else ZIcons.Repeat,
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = { hub.pilot.cyclerepeat() },
                    size = 38.dp,
                    iconSize = 19.dp,
                )
                iconbtn(
                    icon = if (locked) ZIcons.Lock else ZIcons.Unlock,
                    active = locked,
                    onClick = { locked = !locked },
                    size = 38.dp,
                    iconSize = 19.dp,
                )
                if (upcoming.isNotEmpty() && !locked) {
                    iconbtn(
                        icon = ZIcons.Trash,
                        tint = glassTokens.danger,
                        onClick = { hub.pilot.clearupcoming() },
                        size = 38.dp,
                        iconSize = 19.dp,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (upcoming.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp),
                ) {
                    Icon(
                        imageVector = ZIcons.Queue,
                        contentDescription = null,
                        tint = glassTokens.inkMuted,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No songs in queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = glassTokens.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add songs with Play next or Add to queue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(
                    items = upcoming,
                    key = { (_, stableKey, _) -> stableKey },
                ) { (idx, _, item) ->
                    val isDragging = draggingIdx == idx
                    val minIdx = (state.currentIndex + 1).coerceAtMost(state.queue.lastIndex.coerceAtLeast(0))

                    songrow(
                        track = item,
                        playing = false,
                        onClick = { hub.pilot.jumpqueue(idx) },
                        onMore = onTrackMenu?.let { handler -> { handler(item) } },
                        trailing = if (!locked) {
                            {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { hub.pilot.removequeue(idx) },
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
                                            .size(36.dp)
                                            .pointerInput(idx, state.queue.size, minIdx) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        draggingIdx = idx
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
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetPx += dragAmount
                                                        val steps = (dragOffsetPx / rowHeightPx).toInt()
                                                        if (steps != 0 && draggingIdx in state.queue.indices) {
                                                            val target = (draggingIdx + steps).coerceIn(minIdx, state.queue.lastIndex)
                                                            if (target != draggingIdx) {
                                                                hub.pilot.movequeue(draggingIdx, target)
                                                                draggingIdx = target
                                                                dragOffsetPx -= steps * rowHeightPx
                                                            }
                                                        }
                                                    },
                                                )
                                            },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.Drag,
                                            contentDescription = "Reorder",
                                            tint = glassTokens.inkSecondary,
                                            modifier = Modifier.size(18.dp),
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
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun queuesheet(
    onDismiss: () -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
) {
    val glassTokens = LocalGlass.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glassTokens.glassStrong,
        contentColor = glassTokens.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp),
        ) {
            queueview(
                onClose = onDismiss,
                onTrackMenu = onTrackMenu,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}
