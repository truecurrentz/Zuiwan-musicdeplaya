package zuiwan.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.covergrid
import zuiwan.ui.glass
import zuiwan.ui.pillbtn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pickersheet(
    track: Track,
    onDismiss: () -> Unit,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val playlists by hub.playlists.collectAsState()
    val session by hub.session.collectAsState()
    val loggedIn = session.active
    var newName by remember { mutableStateOf("") }
    var syncYoutube by remember(loggedIn) { mutableStateOf(loggedIn) }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Add to Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = glassTokens.ink,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                glass(
                    shape = RoundedCornerShape(16.dp),
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (newName.isEmpty()) {
                            Text(
                                text = "New playlist name",
                                style = MaterialTheme.typography.bodyLarge,
                                color = glassTokens.inkMuted,
                            )
                        }
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassTokens.ink),
                            cursorBrush = SolidColor(glassTokens.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                pillbtn(
                    text = "Create",
                    icon = ZIcons.Add,
                    primary = true,
                    onClick = {
                        if (newName.isNotBlank()) {
                            hub.createplaylist(
                                title = newName,
                                syncYoutube = loggedIn && syncYoutube,
                            ) { createdId ->
                                hub.addtrack(createdId, track)
                                onDismiss()
                            }
                        }
                    },
                )
            }

            if (loggedIn) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { syncYoutube = !syncYoutube }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = ZIcons.Cloud,
                        contentDescription = null,
                        tint = glassTokens.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Sync new playlist with YouTube Music",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = syncYoutube,
                        onCheckedChange = { syncYoutube = it },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "Create your first playlist above to save songs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassTokens.inkSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        val thumbs = pl.thumbnails.ifEmpty { listOfNotNull(pl.artwork.takeIf(String::isNotBlank)) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    hub.addtrack(pl.id, track)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            covergrid(
                                thumbnails = thumbs,
                                shape = RoundedCornerShape(12.dp),
                                fallbackIcon = ZIcons.Playlist,
                                modifier = Modifier.size(46.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.title, style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    if (!pl.remoteId.isNullOrBlank()) {
                                        Icon(
                                            imageVector = ZIcons.Cloud,
                                            contentDescription = null,
                                            tint = glassTokens.accent,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                    Text(pl.countText ?: "0 songs", style = MaterialTheme.typography.bodyMedium, color = glassTokens.inkSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
