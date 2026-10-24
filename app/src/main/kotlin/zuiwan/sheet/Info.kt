package zuiwan.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zuiwan.data.tospec
import zuiwan.model.AudioSpec
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.formatclock
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.GlassBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoSheet(
    track: Track,
    onDismiss: () -> Unit,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val liveSpec by hub.vault.activeSpec.collectAsState()
    var storedSpec by remember { mutableStateOf<AudioSpec?>(null) }

    LaunchedEffect(track.id) {
        storedSpec = withContext(Dispatchers.IO) {
            hub.dao.getformat(track.id)?.tospec()
        }
    }

    val spec = storedSpec ?: liveSpec?.takeIf { it.id == track.id }

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
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Audio Details", style = MaterialTheme.typography.headlineSmall, color = glass.ink)
            Spacer(Modifier.height(14.dp))
            GlassBox(
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoLine("Title", track.title)
                    InfoLine("Artists", track.artistline())
                    track.album?.name?.let { InfoLine("Album", it) }
                    InfoLine("Duration", formatclock(track.duration * 1_000L))
                    InfoLine("Track ID", track.id)
                    if (spec != null) {
                        InfoLine("Format", "${spec.mime} (${spec.codecs.ifBlank { "opus" }})")
                        InfoLine("Bitrate", "${spec.bitrate / 1000} kbps")
                        InfoLine("Sample rate", "${spec.sampleRate} Hz")
                        InfoLine("Itag", spec.itag.toString())
                        spec.perceptualDb?.let { InfoLine("Loudness", "%.1f LUFS".format(it)) }
                            ?: spec.loudnessDb?.let { InfoLine("Loudness", "%.1f dB".format(it)) }
                        InfoLine("Stream client", spec.client)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val glass = LocalGlass.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = glass.ink)
    }
}
