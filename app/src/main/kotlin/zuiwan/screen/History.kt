package zuiwan.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.ScrollHeaderHost
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons
import zuiwan.ui.rememberheaderscroll

@Composable
fun viewhistory(
    onBack: () -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val history by hub.historyTracks.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    val headerScroll = rememberheaderscroll()
    val listState = rememberLazyListState()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    ScrollHeaderHost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            GlassTopBar(title = "History", onBack = onBack) {
                if (history.isNotEmpty()) {
                    GlassIconButton(
                        icon = ZIcons.Play,
                        onClick = { hub.pilot.playtracks(history, 0, "Listening History") },
                    )
                    GlassIconButton(
                        icon = ZIcons.Trash,
                        onClick = { hub.clearhistory() },
                    )
                }
            }
        },
    ) { topPad ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPad + 6.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (history.isEmpty()) {
                item(key = "empty") {
                    GlassBox(
                        shape = RoundedCornerShape(20.dp),
                        elevated = true,
                        contentPadding = PaddingValues(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "Songs you play for at least 15 seconds appear here.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = glass.inkSecondary,
                        )
                    }
                }
            } else {
                items(history, key = { it.id }) { song ->
                    SongRow(
                        track = song,
                        playing = playback.current?.id == song.id,
                        onClick = {
                            val idx = history.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                            hub.pilot.playtracks(history, idx, "Listening History")
                        },
                        onMore = { onTrackMenu(song) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
