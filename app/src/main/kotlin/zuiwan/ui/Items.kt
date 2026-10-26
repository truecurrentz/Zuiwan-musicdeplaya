package zuiwan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistSummary
import zuiwan.model.FeedItem
import zuiwan.model.PlaylistSummary
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.fallbackart
import zuiwan.model.formatclock
import zuiwan.model.trackart
import zuiwan.model.upscaleart
import zuiwan.theme.LocalGlass
import kotlin.random.Random

@Composable
fun playbars(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val bars = remember { List(3) { Animatable(0.32f) } }

    LaunchedEffect(active) {
        if (!active) {
            bars.forEachIndexed { idx, anim ->
                anim.snapTo(if (idx == 1) 0.55f else 0.30f)
            }
            return@LaunchedEffect
        }
        bars.forEachIndexed { idx, anim ->
            launch {
                delay(idx * 90L)
                while (true) {
                    val next = Random.nextFloat() * 0.78f + 0.22f
                    anim.animateTo(next, animationSpec = tween(220))
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier,
    ) {
        bars.forEach { bar ->
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.5.dp),
            ) {
                val h = (bar.value * size.height).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, size.height - h),
                    size = size.copy(height = h),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
fun artimage(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    fallbackIcon: ImageVector = ZIcons.Album,
    iconScale: Float = 0.42f,
    playing: Boolean = false,
) {
    val glassTokens = LocalGlass.current
    val primaryUrl = remember(url) { url?.let { upscaleart(it) }.orEmpty() }
    val secondaryUrl = remember(fallbackUrl) { fallbackUrl?.let { upscaleart(it) }.orEmpty() }

    var useSecondary by remember(primaryUrl, secondaryUrl) { mutableStateOf(false) }
    var failedAll by remember(primaryUrl, secondaryUrl) { mutableStateOf(false) }

    val activeUrl = when {
        failedAll -> ""
        !useSecondary && primaryUrl.isNotBlank() -> primaryUrl
        secondaryUrl.isNotBlank() -> secondaryUrl
        else -> ""
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(glassTokens.glassElevated),
    ) {
        if (activeUrl.isNotBlank()) {
            AsyncImage(
                model = activeUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = {
                    if (!useSecondary && secondaryUrl.isNotBlank() && secondaryUrl != primaryUrl) {
                        useSecondary = true
                    } else {
                        failedAll = true
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = glassTokens.inkMuted,
                modifier = Modifier.fillMaxSize(iconScale),
            )
        }
        if (playing) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.44f)),
            ) {
                playbars(
                    color = Color.White,
                    modifier = Modifier.height(18.dp),
                )
            }
        }
    }
}

@Composable
fun covergrid(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    fallbackIcon: ImageVector = ZIcons.Playlist,
    iconTint: Color? = null,
    preferIcon: Boolean = false,
) {
    val glassTokens = LocalGlass.current
    val clean = remember(thumbnails) {
        thumbnails
            .map { upscaleart(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        glassTokens.accent.copy(alpha = if (glassTokens.isDark) 0.22f else 0.14f),
                        glassTokens.accentSecondary.copy(alpha = if (glassTokens.isDark) 0.14f else 0.09f),
                    ),
                ),
            ),
    ) {
        when {
            preferIcon || clean.isEmpty() -> {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = iconTint ?: glassTokens.accent,
                    modifier = Modifier.fillMaxSize(0.44f),
                )
            }
            clean.size < 4 -> {
                artimage(
                    url = clean[0],
                    shape = RectangleShape,
                    fallbackIcon = fallbackIcon,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        artimage(
                            url = clean[0],
                            shape = RectangleShape,
                            fallbackIcon = fallbackIcon,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        artimage(
                            url = clean[1],
                            shape = RectangleShape,
                            fallbackIcon = fallbackIcon,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        artimage(
                            url = clean[2],
                            shape = RectangleShape,
                            fallbackIcon = fallbackIcon,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        artimage(
                            url = clean[3],
                            shape = RectangleShape,
                            fallbackIcon = fallbackIcon,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun sectionbar(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val glassTokens = LocalGlass.current
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
                    color = glassTokens.accent,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = glassTokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null && onAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(glassTokens.glassElevated)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = glassTokens.ink,
                )
                Icon(
                    imageVector = ZIcons.ArrowForward,
                    contentDescription = null,
                    tint = glassTokens.inkSecondary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
fun songrow(
    track: Track,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    liked: Boolean = track.liked,
    downloaded: Boolean = track.downloaded,
    indexNumber: Int? = null,
    cardBackground: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val glassTokens = LocalGlass.current
    val compact = glassTokens.compact
    val shape = RoundedCornerShape(if (compact) 11.dp else 14.dp)
    val rowBg = when {
        playing -> glassTokens.accentSoft
        cardBackground -> glassTokens.glassFill
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 11.dp else 14.dp,
                vertical = if (compact) 5.dp else 9.dp,
            ),
    ) {
        if (indexNumber != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(if (compact) 24.dp else 30.dp),
            ) {
                if (playing) {
                    playbars(
                        color = glassTokens.accent,
                        modifier = Modifier.height(if (compact) 14.dp else 17.dp),
                    )
                } else {
                    Text(
                        text = indexNumber.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = glassTokens.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        } else {
            artimage(
                url = track.trackart(),
                fallbackUrl = track.fallbackart(),
                shape = RoundedCornerShape(if (compact) 9.dp else 11.dp),
                fallbackIcon = ZIcons.Note,
                playing = playing,
                modifier = Modifier.size(if (compact) 40.dp else 50.dp),
            )
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (playing) glassTokens.accent else glassTokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.explicit) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(glassTokens.glassElevated)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall,
                            color = glassTokens.inkSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (liked) {
                    Icon(
                        imageVector = ZIcons.HeartFilled,
                        contentDescription = null,
                        tint = glassTokens.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (downloaded) {
                    Icon(
                        imageVector = ZIcons.Offline,
                        contentDescription = null,
                        tint = glassTokens.accent,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (track.localUri != null || track.id.startsWith("local_")) {
                    Icon(
                        imageVector = ZIcons.Folder,
                        contentDescription = null,
                        tint = glassTokens.accentSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                val metaText = buildString {
                    append(track.artistline())
                    if (track.duration > 0) {
                        append(" - ")
                        append(formatclock(track.duration * 1_000L))
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassTokens.inkSecondary,
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
                    tint = glassTokens.inkSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun picktile(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width(292.dp)
            .clip(shape)
            .background(if (playing) glassTokens.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        artimage(
            url = track.trackart(),
            fallbackUrl = track.fallbackart(),
            shape = RoundedCornerShape(11.dp),
            fallbackIcon = ZIcons.Note,
            playing = playing,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) glassTokens.accent else glassTokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artistline(),
                style = MaterialTheme.typography.bodyMedium,
                color = glassTokens.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
        ) {
            Icon(
                imageVector = ZIcons.More,
                contentDescription = null,
                tint = glassTokens.inkSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun mediacard(
    item: FeedItem,
    onClick: () -> Unit,
    onLongOrMore: (() -> Unit)? = null,
    width: Dp = 152.dp,
) {
    val glassTokens = LocalGlass.current
    val isArtist = item is FeedItem.ArtistCard
    val artUrl = when (item) {
        is FeedItem.SongCard -> item.track.trackart()
        is FeedItem.AlbumCard -> upscaleart(item.album.artwork)
        is FeedItem.ArtistCard -> upscaleart(item.artist.artwork)
        is FeedItem.PlaylistCard -> upscaleart(item.playlist.artwork)
    }
    val fallbackUrl = (item as? FeedItem.SongCard)?.track?.fallbackart()
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
        ).joinToString(" - ")
        is FeedItem.ArtistCard -> item.artist.subscribers ?: "Artist"
        is FeedItem.PlaylistCard -> item.playlist.author ?: item.playlist.countText.orEmpty()
    }

    if (isArtist) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
        ) {
            artimage(
                url = artUrl,
                shape = CircleShape,
                fallbackIcon = ZIcons.Artist,
                modifier = Modifier.size(width - 12.dp),
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = glassTokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle.ifBlank { "Artist" },
                style = MaterialTheme.typography.bodyMedium,
                color = glassTokens.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
        ) {
            artimage(
                url = artUrl,
                fallbackUrl = fallbackUrl,
                shape = RoundedCornerShape(14.dp),
                fallbackIcon = when (item) {
                    is FeedItem.PlaylistCard -> ZIcons.Playlist
                    is FeedItem.SongCard -> ZIcons.Note
                    else -> ZIcons.Album
                },
                modifier = Modifier.size(width - 8.dp),
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glassTokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle.ifBlank { "Music" },
                style = MaterialTheme.typography.bodyMedium,
                color = glassTokens.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun albumcard(album: AlbumSummary, onClick: () -> Unit) {
    mediacard(item = FeedItem.AlbumCard(album), onClick = onClick, width = 156.dp)
}

@Composable
fun artistcard(artist: ArtistSummary, onClick: () -> Unit) {
    mediacard(item = FeedItem.ArtistCard(artist), onClick = onClick, width = 152.dp)
}

@Composable
fun playlistcard(playlist: PlaylistSummary, onClick: () -> Unit) {
    mediacard(item = FeedItem.PlaylistCard(playlist), onClick = onClick, width = 156.dp)
}
