package zuiwan.sheet

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.GlassBox
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val state by hub.pilot.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex in state.queue.indices) {
            listState.scrollToItem(state.currentIndex)
        }
    }

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
                .fillMaxHeight(0.82f)
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.queueTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.ink,
                    )
                    Text(
                        text = "${state.queue.size} tracks in queue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.inkSecondary,
                    )
                }
                if (state.queue.isNotEmpty()) {
                    GlassBox(
                        shape = RoundedCornerShape(999.dp),
                        onClick = {
                            hub.pilot.clearqueue()
                            onDismiss()
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.danger,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(
                    items = state.queue,
                    key = { idx, item -> "${item.id}_$idx" },
                ) { idx, item ->
                    SongRow(
                        track = item,
                        playing = idx == state.currentIndex,
                        onClick = { hub.pilot.jumpqueue(idx) },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (idx > 0) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { hub.pilot.movequeue(idx, idx - 1) },
                                    ) {
                                        Icon(
                                            imageVector = ZIcons.ArrowUp,
                                            contentDescription = "Move up",
                                            tint = glass.inkSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                if (idx < state.queue.lastIndex) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { hub.pilot.movequeue(idx, idx + 1) },
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
                        },
                    )
                }
            }
        }
    }
}
