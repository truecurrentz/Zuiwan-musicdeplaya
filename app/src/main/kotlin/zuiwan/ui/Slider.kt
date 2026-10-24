package zuiwan.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import zuiwan.theme.LocalGlass
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WaveSlider(
    progress: Float,
    playing: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(progress) }
    val activeProgress = (if (dragging) dragValue else progress).coerceIn(0f, 1f)

    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
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
        val midY = size.height / 2f
        val pad = 8.dp.toPx()
        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
        val thumbX = pad + usable * activeProgress
        val strokePx = 3.2.dp.toPx()

        drawLine(
            color = glass.inkMuted.copy(alpha = 0.30f),
            start = Offset(thumbX, midY),
            end = Offset(size.width - pad, midY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )

        if (playing && !dragging && activeProgress > 0.02f) {
            val waveAmp = 3.5.dp.toPx()
            val waveLen = 28.dp.toPx()
            val path = Path().apply {
                moveTo(pad, midY)
                var x = pad
                while (x <= thumbX) {
                    val y = midY + sin(((x - pad) / waveLen) * (2f * PI.toFloat()) - phase) * waveAmp
                    lineTo(x, y)
                    x += 3f
                }
            }
            drawPath(
                path = path,
                color = glass.accent,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        } else if (thumbX > pad) {
            drawLine(
                color = glass.accent,
                start = Offset(pad, midY),
                end = Offset(thumbX, midY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        val pillW = if (dragging) 8.dp.toPx() else 6.dp.toPx()
        val pillH = if (dragging) 22.dp.toPx() else 16.dp.toPx()
        drawRoundRect(
            color = glass.accent,
            topLeft = Offset(thumbX - pillW / 2f, midY - pillH / 2f),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(pillW / 2f, pillW / 2f),
        )
    }
}
