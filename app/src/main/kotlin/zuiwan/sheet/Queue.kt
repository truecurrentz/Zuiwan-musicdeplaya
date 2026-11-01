package zuiwan.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val state by hub.pilot.state.collectAsState()
    val listState = rememberLazyListState()

    var locked by rememberSaveable { mutableStateOf(false) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var pendingSwapLazyIdx by remember { mutableIntStateOf(-1) }
    var dragList by remember { mutableStateOf<List<Triple<Int, String, Track>>?>(null) }

    val baseUpcoming = remember(state.queue, state.currentIndex) {
        val counts = HashMap<String, Int>(state.queue.size)
        state.queue.mapIndexedNotNull { idx, track ->
            if (idx <= state.currentIndex) return@mapIndexedNotNull null
            val nextCount = (counts[track.id] ?: 0) + 1
            counts[track.id] = nextCount
            Triple(idx, "${track.id}#$nextCount", track)
        }
    }

    val latestBase by rememberUpdatedState(baseUpcoming)
    val latestCurrentIdx by rememberUpdatedState(state.currentIndex)
    val upcoming = if (draggingKey != null && dragList != null) dragList!! else baseUpcoming

    fun stepdrag() {
        val activeKey = draggingKey ?: return
        val currentList = dragList ?: return
        val fromLocal = currentList.indexOfFirst { it.second == activeKey }
        if (fromLocal !in currentList.indices) return

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val draggedItem = visibleItems.firstOrNull { it.key == activeKey } ?: return
        if (pendingSwapLazyIdx >= 0) {
            if (draggedItem.index == pendingSwapLazyIdx) return
            pendingSwapLazyIdx = -1
        }

        val draggedCenterY = draggedItem.offset + dragOffsetPx + draggedItem.size * 0.5f
        val keyToLocal = HashMap<Any, Int>(currentList.size)
        currentList.forEachIndexed { localIdx, entry -> keyToLocal[entry.second] = localIdx }

        val targetItem = (if (dragOffsetPx > 0f) {
            visibleItems.lastOrNull { cand ->
                val candLocal = keyToLocal[cand.key] ?: return@lastOrNull false
                candLocal > fromLocal &&
                    cand.index > draggedItem.index &&
                    draggedCenterY > cand.offset + cand.size * 0.5f
            }
        } else if (dragOffsetPx < 0f) {
            visibleItems.firstOrNull { cand ->
                val candLocal = keyToLocal[cand.key] ?: return@firstOrNull false
                candLocal < fromLocal &&
                    cand.index < draggedItem.index &&
                    draggedCenterY < cand.offset + cand.size * 0.5f
            }
        } else {
            null
        }) ?: return

        val toLocal = keyToLocal[targetItem.key] ?: return
        if (toLocal == fromLocal || toLocal !in currentList.indices) return

        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        val anchorTouched = draggedItem.index == anchorIndex || targetItem.index == anchorIndex

        val newLayoutOffset = if (toLocal > fromLocal) {
            targetItem.offset + targetItem.size - draggedItem.size
        } else {
            targetItem.offset
        }
        pendingSwapLazyIdx = draggedItem.index
        dragOffsetPx = draggedItem.offset + dragOffsetPx - newLayoutOffset

        val baseQueueOffset = latestCurrentIdx + 1
        val mutable = currentList.toMutableList()
        val moved = mutable.removeAt(fromLocal)
        mutable.add(toLocal, moved)
        dragList = mutable.mapIndexed { i, entry ->
            Triple(baseQueueOffset + i, entry.second, entry.third)
        }
        hub.pilot.movequeue(baseQueueOffset + fromLocal, baseQueueOffset + toLocal)

        if (anchorTouched) {
            scope.launch {
                listState.scrollToItem(anchorIndex, anchorOffset)
            }
        }
    }

    fun enddrag() {
        draggingKey = null
        dragOffsetPx = 0f
        pendingSwapLazyIdx = -1
        dragList = null
    }

    LaunchedEffect(draggingKey) {
        val activeKey = draggingKey ?: return@LaunchedEffect
        val edgePx = with(density) { 72.dp.toPx() }
        val maxStepPx = with(density) { 12.dp.toPx() }
        while (draggingKey == activeKey) {
            val layout = listState.layoutInfo
            val draggedItem = layout.visibleItemsInfo.firstOrNull { it.key == activeKey }
            if (draggedItem != null) {
                val visualTop = draggedItem.offset + dragOffsetPx
                val visualBottom = visualTop + draggedItem.size
                val topZone = layout.viewportStartOffset + edgePx
                val bottomZone = layout.viewportEndOffset - edgePx
                val scrollDelta = when {
                    visualTop < topZone && listState.canScrollBackward -> {
                        val frac = ((topZone - visualTop) / edgePx).coerceIn(0.1f, 1f)
                        -maxStepPx * frac
                    }
                    visualBottom > bottomZone && listState.canScrollForward -> {
                        val frac = ((visualBottom - bottomZone) / edgePx).coerceIn(0.1f, 1f)
                        maxStepPx * frac
                    }
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    val consumed = listState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        dragOffsetPx += consumed
                        stepdrag()
                    }
                }
            }
            delay(16L)
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
                        onClick = {
                            enddrag()
                            hub.pilot.clearupcoming()
                        },
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
                ) { (idx, stableKey, item) ->
                    val isDragging = draggingKey == stableKey
                    val coordRef = remember(stableKey) { arrayOfNulls<LayoutCoordinates>(1) }
                    val latestIdx by rememberUpdatedState(idx)

                    songrow(
                        track = item,
                        playing = false,
                        cardBackground = isDragging,
                        onClick = {
                            if (draggingKey == null) {
                                hub.pilot.jumpqueue(latestIdx)
                            }
                        },
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
                                            .clickable {
                                                enddrag()
                                                hub.pilot.removequeue(latestIdx)
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
                                            .size(40.dp)
                                            .onGloballyPositioned { coordRef[0] = it }
                                            .pointerInput(stableKey) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    down.consume()
                                                    var lastRootY = coordRef[0]?.localToRoot(down.position)?.y ?: 0f
                                                    dragList = latestBase
                                                    draggingKey = stableKey
                                                    dragOffsetPx = 0f
                                                    pendingSwapLazyIdx = -1
                                                    var pointerId = down.id
                                                    try {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                                                ?: event.changes.firstOrNull { it.pressed }
                                                                ?: break
                                                            pointerId = change.id
                                                            if (!change.pressed) {
                                                                change.consume()
                                                                break
                                                            }
                                                            change.consume()
                                                            val coords = coordRef[0]
                                                            if (coords != null && coords.isAttached) {
                                                                val nowRootY = coords.localToRoot(change.position).y
                                                                val deltaY = nowRootY - lastRootY
                                                                lastRootY = nowRootY
                                                                dragOffsetPx += deltaY
                                                                stepdrag()
                                                            }
                                                        }
                                                    } finally {
                                                        enddrag()
                                                    }
                                                }
                                            },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.Drag,
                                            contentDescription = "Reorder",
                                            tint = if (isDragging) glassTokens.accent else glassTokens.inkSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .then(if (!isDragging) Modifier.animateItem() else Modifier)
                            .zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetPx else 0f
                                val s = if (isDragging) 1.02f else 1f
                                scaleX = s
                                scaleY = s
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
