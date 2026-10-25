package zuiwan.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.MatchResult
import zuiwan.model.MatchStage
import zuiwan.model.NoticeKind
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.artimage
import zuiwan.ui.chip
import zuiwan.ui.iconbtn
import zuiwan.ui.pillbtn
import zuiwan.ui.sectionbar
import zuiwan.ui.topbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun viewrecognize(
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    val hub = LocalHub.current
    val scope = rememberCoroutineScope()
    val stage by hub.recognizer.stage.collectAsState()
    val level by hub.recognizer.level.collectAsState()
    val history by hub.recognizedHistory.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            hub.recognizer.startlisten(hub.scope)
        } else {
            hub.shownotice(NoticeKind.ERROR, "Microphone Permission Needed", "Allow microphone access to listen")
        }
    }

    fun triggerlisten() {
        if (hub.recognizer.hasperm()) {
            hub.recognizer.startlisten(hub.scope)
        } else {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val cur = hub.recognizer.stage.value
            if (cur is MatchStage.Listening || cur is MatchStage.Processing) {
                hub.recognizer.stoplisten()
            } else {
                hub.recognizer.resetmatch()
            }
        }
    }

    fun playmatch(match: MatchResult) {
        val ready = match.matchedTrack
        if (ready != null) {
            hub.pilot.playtracks(listOf(ready), 0, "Recognized")
            return
        }
        scope.launch {
            hub.shownotice(NoticeKind.LOADING, "Opening Track", "${match.title} · ${match.artist}")
            val resolved = hub.recognizer.resolvetrack(match).matchedTrack
            if (resolved != null) {
                hub.hidenotice()
                hub.pilot.playtracks(listOf(resolved), 0, "Recognized")
            } else {
                hub.hidenotice()
                onOpenSearch("${match.title} ${match.artist}".trim())
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        topbar(
            title = "Recognize Music",
            onBack = {
                hub.recognizer.stoplisten()
                onBack()
            },
            actions = {
                if (history.isNotEmpty()) {
                    iconbtn(
                        icon = ZIcons.Trash,
                        onClick = { hub.clearmatches() },
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.95f))
                            .togetherWith(fadeOut(tween(170)) + scaleOut(targetScale = 0.95f))
                    },
                    label = "stage",
                ) { curStage ->
                    when (curStage) {
                        is MatchStage.Matched -> {
                            matchcard(
                                match = curStage.result,
                                onPlay = { playmatch(curStage.result) },
                                onSearch = {
                                    onOpenSearch("${curStage.result.title} ${curStage.result.artist}".trim())
                                },
                                onMore = {
                                    curStage.result.matchedTrack?.let(onTrackMenu)
                                },
                                onAgain = { triggerlisten() },
                            )
                        }

                        else -> {
                            listenorb(
                                stage = curStage,
                                level = level,
                                onTap = {
                                    when (curStage) {
                                        is MatchStage.Listening, is MatchStage.Processing ->
                                            hub.recognizer.cancellisten()
                                        else -> triggerlisten()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    sectionbar(title = "Recognized Songs")
                }

                items(history, key = { "${it.id}_${it.recognizedAt}" }) { item ->
                    historyitem(
                        item = item,
                        onPlay = { playmatch(item) },
                        onSearch = { onOpenSearch("${item.title} ${item.artist}".trim()) },
                        onDelete = { hub.dropmatch(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun listenorb(
    stage: MatchStage,
    level: Float,
    onTap: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    val listening = stage is MatchStage.Listening
    val processing = stage is MatchStage.Processing

    val infinite = rememberInfiniteTransition(label = "waves")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
        ),
        label = "wave",
    )
    val spinAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "spin",
    )
    val idleBreath by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val liveBoost by animateFloatAsState(
        targetValue = if (listening) (1f + level * 0.24f) else idleBreath,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "boost",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(268.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerRadius = 64.dp.toPx()
                val maxRadius = size.minDimension / 2f
                if (listening) {
                    for (ring in 0..2) {
                        val p = (wavePhase + ring / 3f) % 1f
                        val r = centerRadius + (maxRadius - centerRadius) * p + (level * 18.dp.toPx())
                        val alpha = (1f - p) * (0.28f + level * 0.28f)
                        drawCircle(
                            color = glassTokens.accent.copy(alpha = alpha.coerceIn(0f, 0.55f)),
                            radius = r.coerceAtMost(maxRadius),
                        )
                    }
                } else if (processing) {
                    drawCircle(
                        color = glassTokens.accent.copy(alpha = 0.12f),
                        radius = centerRadius * 1.32f,
                    )
                    rotate(spinAngle) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    glassTokens.accent,
                                    glassTokens.accentSecondary,
                                    Color.Transparent,
                                ),
                            ),
                            startAngle = 0f,
                            sweepAngle = 260f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                } else {
                    drawCircle(
                        color = glassTokens.accent.copy(alpha = 0.09f),
                        radius = centerRadius * 1.25f * idleBreath,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(134.dp)
                    .scale(liveBoost)
                    .clip(CircleShape)
                    .background(
                        if (listening || processing) {
                            Brush.linearGradient(
                                listOf(glassTokens.accent, glassTokens.accentSecondary),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(glassTokens.glassStrong, glassTokens.glassElevated),
                            )
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTap,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (processing) ZIcons.Sparkles else ZIcons.Mic,
                    contentDescription = "Recognize",
                    tint = if (listening || processing) glassTokens.onAccent else glassTokens.accent,
                    modifier = Modifier.size(46.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val statusText = when (stage) {
            is MatchStage.Ready -> "Click to listen to the current."
            is MatchStage.Listening, is MatchStage.Processing -> "Listening to the currents"
            is MatchStage.Cancelled -> "The currents was cancelled"
            is MatchStage.NoMatch -> stage.message
            is MatchStage.Error -> stage.message
            is MatchStage.Matched -> stage.result.title
        }

        val hintText = when (stage) {
            is MatchStage.Cancelled, is MatchStage.NoMatch, is MatchStage.Error ->
                "Click to listen to the current."
            else -> ""
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = glassTokens.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (hintText.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = glassTokens.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTap,
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun matchcard(
    match: MatchResult,
    onPlay: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onAgain: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(glassTokens.glassFill)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        artimage(
            url = match.artwork,
            modifier = Modifier.size(196.dp),
            shape = RoundedCornerShape(24.dp),
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = match.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = glassTokens.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = match.artist,
            style = MaterialTheme.typography.titleMedium,
            color = glassTokens.inkSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val badges = buildList {
            match.album?.takeIf { it.isNotBlank() }?.let(::add)
            match.genre?.takeIf { it.isNotBlank() }?.let(::add)
            match.releaseDate?.takeIf { it.isNotBlank() }?.let(::add)
        }
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = badges.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = glassTokens.inkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pillbtn(
                text = "Play Now",
                icon = ZIcons.Play,
                primary = true,
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            )
            pillbtn(
                text = "Search",
                icon = ZIcons.Search,
                primary = false,
                onClick = onSearch,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chip(
                label = "Click to listen to the current.",
                selected = false,
                leadingIcon = ZIcons.Mic,
                onClick = onAgain,
            )
            if (match.matchedTrack != null) {
                chip(
                    label = "More Options",
                    selected = false,
                    leadingIcon = ZIcons.More,
                    onClick = onMore,
                )
            }
        }
    }
}

@Composable
private fun historyitem(
    item: MatchResult,
    onPlay: () -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        artimage(
            url = item.artwork,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(13.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = glassTokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                add(item.artist)
                add(dateFmt.format(Date(item.recognizedAt)))
            }.joinToString(" · ")
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = glassTokens.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        iconbtn(
            icon = ZIcons.Search,
            size = 34.dp,
            iconSize = 17.dp,
            tint = glassTokens.inkMuted,
            onClick = onSearch,
        )
        iconbtn(
            icon = ZIcons.Close,
            size = 34.dp,
            iconSize = 16.dp,
            tint = glassTokens.inkMuted,
            onClick = onDelete,
        )
    }
}
