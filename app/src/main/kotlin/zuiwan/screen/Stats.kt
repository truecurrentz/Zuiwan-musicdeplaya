package zuiwan.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.StatsSummary
import zuiwan.model.StatsWindow
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassTopBar
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@Composable
fun viewstats(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    var window by remember { mutableStateOf(StatsWindow.MONTH) }
    var summary by remember { mutableStateOf(StatsSummary()) }

    LaunchedEffect(window) {
        summary = hub.loadstats(window)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "top") {
            GlassTopBar(title = "Listening Stats", onBack = onBack)
        }

        item(key = "windows") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                StatsWindow.entries.forEach { w ->
                    GlassChip(
                        label = w.label,
                        selected = window == w,
                        onClick = { window = w },
                    )
                }
            }
        }

        item(key = "metrics") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                StatMetricCard("Minutes", summary.totalMinutes.toString(), Modifier.weight(1f))
                StatMetricCard("Plays", summary.totalPlays.toString(), Modifier.weight(1f))
                StatMetricCard("Artists", summary.uniqueArtists.toString(), Modifier.weight(1f))
            }
        }

        if (summary.topTracks.isNotEmpty()) {
            item(key = "tracks_head") {
                SectionHeader(title = "Top Tracks", subtitle = window.label)
            }
            itemsIndexed(summary.topTracks, key = { _, it -> it.track.id }) { idx, stat ->
                SongRow(
                    track = stat.track,
                    indexNumber = idx + 1,
                    onClick = { hub.pilot.audioplay(stat.track, "Top Tracks") },
                    onMore = { onTrackMenu(stat.track) },
                    trailing = {
                        Text(
                            text = "${stat.plays}x",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.accent,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        if (summary.topArtists.isNotEmpty()) {
            item(key = "artists_head") {
                Spacer(Modifier.height(12.dp))
                SectionHeader(title = "Top Artists", subtitle = window.label)
            }
            itemsIndexed(summary.topArtists, key = { _, it -> it.artist.id }) { idx, stat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (stat.artist.id.startsWith("UC")) {
                                onNavigate(Route.Artist(stat.artist.id))
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.inkMuted,
                        modifier = Modifier.width(26.dp),
                    )
                    ArtImage(
                        url = stat.artist.artwork,
                        shape = CircleShape,
                        fallbackIcon = ZIcons.Artist,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stat.artist.name, style = MaterialTheme.typography.titleMedium, color = glass.ink)
                        Text("${stat.totalTimeMs / 60_000L} min listened", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
                    }
                    Text("${stat.plays} plays", style = MaterialTheme.typography.labelLarge, color = glass.accent)
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    GlassBox(
        shape = RoundedCornerShape(20.dp),
        elevated = true,
        contentPadding = PaddingValues(16.dp),
        modifier = modifier,
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = glass.accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = glass.inkSecondary,
            )
        }
    }
}
