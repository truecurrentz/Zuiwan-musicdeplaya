package zuiwan.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zuiwan.theme.LocalGlass

@Composable
fun thinslider(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color? = null,
    trackColor: Color? = null,
    idleThickness: Dp = 6.dp,
    activeThickness: Dp = 11.dp,
) {
    val glassTokens = LocalGlass.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(progress) }
    val value = (if (dragging) dragValue else progress).coerceIn(0f, 1f)

    val barHeight by animateDpAsState(
        targetValue = if (dragging) activeThickness else idleThickness,
        animationSpec = tween(160),
        label = "barThickness",
    )

    val fill = activeColor ?: glassTokens.ink
    val rail = trackColor ?: glassTokens.inkMuted.copy(alpha = 0.28f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragValue)
                    },
                    onDragCancel = {
                        dragging = false
                    },
                )
            },
    ) {
        val hPx = barHeight.toPx()
        val topY = (size.height - hPx) / 2f
        val radius = CornerRadius(hPx / 2f, hPx / 2f)

        drawRoundRect(
            color = rail,
            topLeft = Offset(0f, topY),
            size = Size(size.width, hPx),
            cornerRadius = radius,
        )

        val activeWidth = (size.width * value).coerceAtLeast(if (value > 0f) hPx else 0f)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(0f, topY),
                size = Size(activeWidth.coerceAtMost(size.width), hPx),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun waveslider(
    progress: Float,
    playing: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    thinslider(
        progress = progress,
        onSeek = onSeek,
        activeColor = glassTokens.accent,
        trackColor = glassTokens.inkMuted.copy(alpha = 0.25f),
        modifier = modifier,
    )
}
