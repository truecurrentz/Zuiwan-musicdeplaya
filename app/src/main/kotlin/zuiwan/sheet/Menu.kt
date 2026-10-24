package zuiwan.sheet

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassIconButton
import zuiwan.ui.Route
import zuiwan.ui.ZIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    track: Track,
    onDismiss: () -> Unit,
    onNavigate: (Route) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowInfo: (Track) -> Unit,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val context = LocalContext.current
    val likedIds by hub.likedIds.collectAsState()
    val downloadedIds by hub.downloadedIds.collectAsState()
    val dlProgress by hub.vault.downloadProgress.collectAsState()
    val isLiked = track.id in likedIds
    val isDownloaded = track.id in downloadedIds
    val activeProgress = dlProgress[track.id]

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
                .padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArtImage(
                    url = track.artwork,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artistline(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(
                    icon = if (isLiked) ZIcons.HeartFilled else ZIcons.Heart,
                    active = isLiked,
                    onClick = { hub.togglelike(track) },
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = glass.glassBorder)
            Spacer(Modifier.height(6.dp))

            MenuActionRow(ZIcons.Play, "Play next") {
                hub.pilot.playnext(track)
                onDismiss()
            }
            MenuActionRow(ZIcons.Queue, "Add to queue") {
                hub.pilot.enqueue(track)
                onDismiss()
            }
            MenuActionRow(ZIcons.Radio, "Start instant radio") {
                hub.pilot.audioplay(track, "${track.title} Radio")
                onDismiss()
            }
            MenuActionRow(ZIcons.Playlist, "Add to playlist") {
                onDismiss()
                onAddToPlaylist(track)
            }
            MenuActionRow(
                icon = if (isDownloaded) ZIcons.Trash else ZIcons.Download,
                label = when {
                    activeProgress != null -> "Downloading ${(activeProgress * 100).toInt()}% (tap to cancel)"
                    isDownloaded -> "Remove offline download"
                    else -> "Download for offline"
                },
            ) {
                when {
                    activeProgress != null -> hub.vault.canceldownload(track.id)
                    isDownloaded -> hub.vault.removedownload(track.id)
                    else -> hub.vault.downloadtrack(track)
                }
                onDismiss()
            }

            track.artists.firstOrNull()?.id?.let { artistId ->
                MenuActionRow(ZIcons.Artist, "Go to artist") {
                    onDismiss()
                    onNavigate(Route.Artist(artistId))
                }
            }
            track.album?.id?.let { albumId ->
                MenuActionRow(ZIcons.Album, "Go to album") {
                    onDismiss()
                    onNavigate(Route.Album(albumId))
                }
            }
            MenuActionRow(ZIcons.Info, "Audio details") {
                onDismiss()
                onShowInfo(track)
            }
            MenuActionRow(ZIcons.Share, "Share link") {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${track.title} — ${track.artistline()}\nhttps://music.youtube.com/watch?v=${track.id}",
                    )
                }
                context.startActivity(Intent.createChooser(share, track.title))
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 12.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glass.inkSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = glass.ink,
        )
    }
}
