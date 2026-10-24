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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.formatclock
import zuiwan.theme.LocalGlass
import zuiwan.ui.GlassBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoSheet(
    track: Track,
    onDismiss: () -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Song Info", style = MaterialTheme.typography.headlineSmall, color = glass.ink)
            Spacer(Modifier.height(14.dp))
            GlassBox(
                shape = RoundedCornerShape(20.dp),
                elevated = true,
                contentPadding = PaddingValues(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoLine("Title", track.title)
                    InfoLine("Artists", track.artistline())
                    track.album?.name?.let { InfoLine("Album", it) }
                    if (track.duration > 0) {
                        InfoLine("Duration", formatclock(track.duration * 1_000L))
                    }
                    InfoLine("Explicit", if (track.explicit) "Yes" else "No")
                    InfoLine("Video ID", track.id)
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
