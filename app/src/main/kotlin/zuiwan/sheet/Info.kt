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
import zuiwan.ui.glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun infosheet(
    track: Track,
    onDismiss: () -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Song Info", style = MaterialTheme.typography.headlineSmall, color = glassTokens.ink)
            Spacer(Modifier.height(14.dp))
            glass(
                shape = RoundedCornerShape(20.dp),
                elevated = true,
                contentPadding = PaddingValues(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    inforow("Title", track.title)
                    inforow("Artists", track.artistline())
                    track.album?.name?.let { inforow("Album", it) }
                    if (track.duration > 0) {
                        inforow("Duration", formatclock(track.duration * 1_000L))
                    }
                    inforow("Explicit", if (track.explicit) "Yes" else "No")
                    inforow("Video ID", track.id)
                }
            }
        }
    }
}

@Composable
private fun inforow(label: String, value: String) {
    val glassTokens = LocalGlass.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = glassTokens.inkSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = glassTokens.ink)
    }
}
