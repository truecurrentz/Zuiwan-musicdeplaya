package zuiwan.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassPillButton
import zuiwan.ui.ZIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    track: Track,
    onDismiss: () -> Unit,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val playlists by hub.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Add to Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = glass.ink,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New playlist name", color = glass.inkMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = glass.accent,
                        unfocusedBorderColor = glass.glassBorder,
                        focusedTextColor = glass.ink,
                        unfocusedTextColor = glass.ink,
                    ),
                    modifier = Modifier.weight(1f),
                )
                GlassPillButton(
                    text = "Create",
                    icon = ZIcons.Add,
                    primary = true,
                    onClick = {
                        if (newName.isNotBlank()) {
                            hub.createplaylist(newName) { createdId ->
                                hub.addtoplaylist(createdId, track)
                                onDismiss()
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "Create your first playlist above to save songs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.inkSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    hub.addtoplaylist(pl.id, track)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            ArtImage(
                                url = pl.artwork,
                                shape = RoundedCornerShape(12.dp),
                                fallbackIcon = ZIcons.Playlist,
                                modifier = Modifier.size(46.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.title, style = MaterialTheme.typography.titleMedium, color = glass.ink)
                                Text(pl.countText ?: "0 songs", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
