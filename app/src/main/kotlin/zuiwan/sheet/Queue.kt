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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import zuiwan.model.Track
import zuiwan.model.formatclock
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.GlassIconButton
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@Composable
fun QueueView(
    onClose: () -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val state by hub.pilot.state.collectAsState()
    val listState = rememberLazyListState()

    var locked by rememberSaveable { mutableStateOf(false) }
    var draggingIdx by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 68.dp.toPx() }

    LaunchedEffect(Unit) {
        if (state.currentIndex in state.queue.indices) {
            listState.scrollToItem((state.currentIndex - 1).coerceAtLeast(0))
        }
    }

    val totalMs = remember(state.queue) {
        state.queue.sumOf { it.duration.coerceAtLeast(0).toLong() } * 1_000L
    }

    val keyedQueue = remember(state.queue) {
        val counts = HashMap<String, Int>(state.queue.size)
        state.queue.mapIndexed { idx, track ->
            val nextCount = (counts[track.id] ?: 0) + 1
            counts[track.id] = nextCount
            Triple(idx, "${track.id}#$nextCount", track)
        }
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
                    text = state.queueTitle.ifBlank { "Playing Next" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    append("${state.queue.size} songs")
                    if (totalMs > 0L) {
                        append(" - ")
                        append(formatclock(totalMs))
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.inkSecondary,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                GlassIconButton(
                    icon = ZIcons.Shuffle,
                    active = state.shuffle,
                    onClick = { hub.pilot.toggleshuffle() },
                    size = 38.dp,
                    iconSize = 19.dp,
                )
                GlassIconButton(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) ZIcons.RepeatOne else ZIcons.Repeat,
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = { hub.pilot.cyclerepeat() },
                    size = 38.dp,
                    iconSize = 19.dp,
                )
                GlassIconButton(
                    icon = if (locked) ZIcons.Lock else ZIcons.Unlock,
                    active = locked,
                    onClick = { locked = !locked },
                    size = 38.dp,
                    iconSize = 19.dp,
                )
                if (state.queue.isNotEmpty() && !locked) {
                    GlassIconButton(
                        icon = ZIcons.Trash,
                        tint = glass.danger,
                        onClick = {
                            hub.pilot.clearqueue()
                            onClose()
                        },
                        size = 38.dp,
                        iconSize = 19.dp,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (keyedQueue.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = glass.inkSecondary,
                )
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
                    items = keyedQueue,
                    key = { (_, stableKey, _) -> stableKey },
                ) { (idx, _, item) ->
                    val isCurrent = idx == state.currentIndex
                    val isPlayed = idx < state.currentIndex
                    val isDragging = draggingIdx == idx

                    SongRow(
                        track = item,
                        playing = isCurrent,
                        onClick = {
                            if (isCurrent) {
                                hub.pilot.toggleplay()
                            } else {
                                hub.pilot.jumpqueue(idx)
                            }
                        },
                        onMore = onTrackMenu?.let { handler -> { handler(item) } },
                        trailing = if (!locked) {
                            {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    if (!isCurrent) {
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
                                                tint = glass.inkMuted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .pointerInput(idx, state.queue.size) {
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
                                                            val target = (draggingIdx + steps).coerceIn(0, state.queue.lastIndex)
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
                                            tint = glass.inkSecondary,
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
                            }
                            .alpha(if (isPlayed && !isDragging) 0.60f else 1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    onDismiss: () -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
) {
    val glass = LocalGlass.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glass.glassStrong,
        contentColor = glass.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp),
        ) {
            QueueView(
                onClose = onDismiss,
                onTrackMenu = onTrackMenu,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}
