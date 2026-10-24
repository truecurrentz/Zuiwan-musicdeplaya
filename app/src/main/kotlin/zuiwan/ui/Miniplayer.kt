package zuiwan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zuiwan.audio.PlaybackState
import zuiwan.model.artistline
import zuiwan.model.fallbackart
import zuiwan.model.trackart
import zuiwan.theme.LocalGlass

@Composable
fun miniplayer(
    state: PlaybackState,
    liked: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLike: () -> Unit,
    connectedBelow: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val glassTokens = LocalGlass.current
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var dragX by remember { mutableFloatStateOf(0f) }

    val barShape = if (connectedBelow) {
        RoundedCornerShape(
            topStart = 26.dp,
            topEnd = 26.dp,
            bottomStart = 12.dp,
            bottomEnd = 12.dp,
        )
    } else {
        RoundedCornerShape(26.dp)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                    },
                    onDragEnd = {
                        if (dragX < -90f) onNext() else if (dragX > 90f) onPrev()
                        dragX = 0f
                    },
                )
            },
    ) {
        glass(
            shape = barShape,
            strong = true,
            frosted = true,
            onClick = onExpand,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (progress > 0.01f) {
                        Canvas(modifier = Modifier.size(48.dp)) {
                            val stroke = 2.4.dp.toPx()
                            drawArc(
                                color = glassTokens.accent,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    artimage(
                        url = track.trackart(),
                        fallbackUrl = track.fallbackart(),
                        shape = CircleShape,
                        fallbackIcon = ZIcons.Note,
                        modifier = Modifier.size(42.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = glassTokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = track.artistline(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassTokens.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    iconbtn(
                        icon = ZIcons.FastRewind,
                        onClick = onPrev,
                        size = 38.dp,
                        iconSize = 17.dp,
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(glassTokens.accent)
                            .clickable(onClick = onTogglePlay),
                    ) {
                        if (state.buffering) {
                            CircularProgressIndicator(
                                color = glassTokens.onAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                imageVector = if (state.playing) ZIcons.Pause else ZIcons.Play,
                                contentDescription = null,
                                tint = glassTokens.onAccent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    iconbtn(
                        icon = ZIcons.FastForward,
                        onClick = onNext,
                        size = 38.dp,
                        iconSize = 17.dp,
                    )
                }
            }
        }
    }
}
