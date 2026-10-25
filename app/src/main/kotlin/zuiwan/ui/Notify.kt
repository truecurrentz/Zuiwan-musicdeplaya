package zuiwan.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zuiwan.model.NoticeKind
import zuiwan.model.NoticeState
import zuiwan.theme.LocalGlass

@Composable
fun liveisland(
    notice: NoticeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = notice.visible && notice.title.isNotBlank(),
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { -it - 36 },
            ) + scaleIn(
                initialScale = 0.82f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetY = { -it - 28 },
            ) + scaleOut(
                targetScale = 0.86f,
                animationSpec = tween(200),
            ) + fadeOut(tween(180)),
        ) {
            glass(
                shape = shape,
                strong = true,
                frosted = true,
                onClick = when {
                    notice.onAction != null -> {
                        {
                            notice.onAction.invoke()
                            onDismiss()
                        }
                    }
                    notice.kind != NoticeKind.SYNC && notice.kind != NoticeKind.LOADING -> onDismiss
                    else -> null
                },
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 11.dp),
                modifier = Modifier
                    .widthIn(min = 210.dp, max = 380.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = glassTokens.glassShadow,
                        spotColor = glassTokens.glassShadow,
                    )
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        noticeorb(kind = notice.kind, noticeId = notice.id)

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = notice.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = glassTokens.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (notice.subtitle.isNotBlank()) {
                                AnimatedContent(
                                    targetState = notice.subtitle,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + slideInVertically { it / 3 })
                                            .togetherWith(fadeOut(tween(140)) + slideOutVertically { -it / 3 })
                                    },
                                    label = "sub",
                                ) { sub ->
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = glassTokens.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        val prog = notice.progress
                        if (prog != null && (notice.kind == NoticeKind.SYNC || notice.kind == NoticeKind.LOADING)) {
                            val animPct by animateFloatAsState(
                                targetValue = (prog * 100f).coerceIn(0f, 100f),
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "pct",
                            )
                            Text(
                                text = "${animPct.toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                                color = glassTokens.accent,
                            )
                        } else if (notice.actionLabel.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(glassTokens.accentSoft)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = notice.actionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    ),
                                    color = glassTokens.accent,
                                )
                            }
                        }
                    }

                    val prog = notice.progress
                    if (prog != null && (notice.kind == NoticeKind.SYNC || notice.kind == NoticeKind.LOADING)) {
                        val smoothProg by animateFloatAsState(
                            targetValue = prog.coerceIn(0.03f, 1f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            label = "bar",
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(glassTokens.ink.copy(alpha = 0.09f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(smoothProg)
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(glassTokens.accent, glassTokens.accentSecondary),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun noticeorb(
    kind: NoticeKind,
    noticeId: Long,
) {
    val glassTokens = LocalGlass.current
    val infinite = rememberInfiniteTransition(label = "orb")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
        ),
        label = "spin",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    when (kind) {
        NoticeKind.SYNC, NoticeKind.LOADING -> {
            Box(
                modifier = Modifier.size(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .size(30.dp)
                        .scale(pulse),
                ) {
                    drawCircle(
                        color = glassTokens.accent.copy(alpha = 0.14f),
                    )
                    rotate(spin) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    glassTokens.accent.copy(alpha = 0.35f),
                                    glassTokens.accent,
                                    glassTokens.accentSecondary,
                                ),
                            ),
                            startAngle = 15f,
                            sweepAngle = 290f,
                            useCenter = false,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Icon(
                    imageVector = if (kind == NoticeKind.SYNC) ZIcons.Sync else ZIcons.Sparkles,
                    contentDescription = null,
                    tint = glassTokens.accent,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (kind == NoticeKind.SYNC) spin * 0.5f else 0f),
                )
            }
        }

        NoticeKind.SUCCESS -> {
            var drawTarget by remember(noticeId) { mutableFloatStateOf(0f) }
            LaunchedEffect(noticeId) { drawTarget = 1f }
            val strokeProg by animateFloatAsState(
                targetValue = drawTarget,
                animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                label = "chk",
            )
            val popScale by animateFloatAsState(
                targetValue = if (drawTarget > 0f) 1f else 0.6f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                label = "pop",
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(popScale)
                    .clip(CircleShape)
                    .background(glassTokens.success.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(15.dp)) {
                    val w = size.width
                    val h = size.height
                    val p1 = Offset(w * 0.18f, h * 0.54f)
                    val p2 = Offset(w * 0.43f, h * 0.78f)
                    val p3 = Offset(w * 0.84f, h * 0.24f)
                    val strokeW = 2.2.dp.toPx()

                    val leg1 = (strokeProg / 0.42f).coerceIn(0f, 1f)
                    if (leg1 > 0f) {
                        drawLine(
                            color = glassTokens.success,
                            start = p1,
                            end = Offset(
                                p1.x + (p2.x - p1.x) * leg1,
                                p1.y + (p2.y - p1.y) * leg1,
                            ),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round,
                        )
                    }
                    val leg2 = ((strokeProg - 0.42f) / 0.58f).coerceIn(0f, 1f)
                    if (leg2 > 0f) {
                        drawLine(
                            color = glassTokens.success,
                            start = p2,
                            end = Offset(
                                p2.x + (p3.x - p2.x) * leg2,
                                p2.y + (p3.y - p2.y) * leg2,
                            ),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        NoticeKind.ERROR -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(glassTokens.danger.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Alert,
                    contentDescription = null,
                    tint = glassTokens.danger,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        NoticeKind.INFO -> {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(glassTokens.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Sparkles,
                    contentDescription = null,
                    tint = glassTokens.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        NoticeKind.UPDATE -> {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(glassTokens.accent, glassTokens.accentSecondary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ZIcons.Update,
                    contentDescription = null,
                    tint = glassTokens.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
