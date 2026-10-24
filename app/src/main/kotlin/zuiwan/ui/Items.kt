package zuiwan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistSummary
import zuiwan.model.FeedItem
import zuiwan.model.PlaylistSummary
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.formatclock
import zuiwan.theme.LocalGlass

@Composable
fun ArtImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    fallbackIcon: ImageVector = ZIcons.Album,
) {
    val glass = LocalGlass.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(glass.glassElevated)
            .border(0.75.dp, glass.glassBorder, shape),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = glass.inkMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.accent,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null && onAction != null) {
            GlassBox(
                shape = RoundedCornerShape(999.dp),
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.inkSecondary,
                )
            }
        }
    }
}

@Composable
fun SongRow(
    track: Track,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    liked: Boolean = track.liked,
    downloaded: Boolean = track.downloaded,
    indexNumber: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (playing) glass.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (indexNumber != null) {
            Text(
                text = indexNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (playing) glass.accent else glass.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            ArtImage(
                url = track.artwork,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(50.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (playing) glass.accent else glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.explicit) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(glass.glassElevated)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.inkSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (downloaded) {
                    Icon(
                        imageVector = ZIcons.Check,
                        contentDescription = null,
                        tint = glass.accent,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (liked) {
                    Icon(
                        imageVector = ZIcons.HeartFilled,
                        contentDescription = null,
                        tint = glass.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
                val metaText = buildString {
                    append(track.artistline())
                    if (track.duration > 0) {
                        append(" • ")
                        append(formatclock(track.duration * 1_000L))
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onMore != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
            ) {
                Icon(
                    imageVector = ZIcons.More,
                    contentDescription = null,
                    tint = glass.inkSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun QuickPickTile(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    GlassBox(
        shape = RoundedCornerShape(18.dp),
        accented = playing,
        onClick = onClick,
        contentPadding = PaddingValues(10.dp),
        modifier = modifier.width(276.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ArtImage(
                url = track.artwork,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (playing) glass.accent else glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artistline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
            ) {
                Icon(
                    imageVector = ZIcons.More,
                    contentDescription = null,
                    tint = glass.inkSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun MediaCard(
    item: FeedItem,
    onClick: () -> Unit,
    onLongOrMore: (() -> Unit)? = null,
    width: Dp = 152.dp,
) {
    val glass = LocalGlass.current
    val isArtist = item is FeedItem.ArtistCard
    val artUrl = when (item) {
        is FeedItem.SongCard -> item.track.artwork
        is FeedItem.AlbumCard -> item.album.artwork
        is FeedItem.ArtistCard -> item.artist.artwork
        is FeedItem.PlaylistCard -> item.playlist.artwork
    }
    val title = when (item) {
        is FeedItem.SongCard -> item.track.title
        is FeedItem.AlbumCard -> item.album.title
        is FeedItem.ArtistCard -> item.artist.name
        is FeedItem.PlaylistCard -> item.playlist.title
    }
    val subtitle = when (item) {
        is FeedItem.SongCard -> item.track.artistline()
        is FeedItem.AlbumCard -> listOfNotNull(
            item.album.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
            item.album.year?.toString(),
        ).joinToString(" • ")
        is FeedItem.ArtistCard -> item.artist.subscribers ?: "Artist"
        is FeedItem.PlaylistCard -> item.playlist.author ?: item.playlist.countText.orEmpty()
    }

    GlassBox(
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.width(width),
    ) {
        Column(
            horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ArtImage(
                url = artUrl,
                shape = if (isArtist) CircleShape else RoundedCornerShape(14.dp),
                fallbackIcon = if (isArtist) ZIcons.Artist else ZIcons.Album,
                modifier = Modifier.size(width - 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle.ifBlank { "Music" },
                style = MaterialTheme.typography.bodyMedium,
                color = glass.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AlbumGridCard(album: AlbumSummary, onClick: () -> Unit) {
    MediaCard(item = FeedItem.AlbumCard(album), onClick = onClick, width = 160.dp)
}

@Composable
fun ArtistGridCard(artist: ArtistSummary, onClick: () -> Unit) {
    MediaCard(item = FeedItem.ArtistCard(artist), onClick = onClick, width = 152.dp)
}

@Composable
fun PlaylistGridCard(playlist: PlaylistSummary, onClick: () -> Unit) {
    MediaCard(item = FeedItem.PlaylistCard(playlist), onClick = onClick, width = 160.dp)
}
