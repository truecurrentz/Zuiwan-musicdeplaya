package zuiwan.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import zuiwan.lyrics.LyricParser
import zuiwan.model.ShaderPreset
import zuiwan.model.VisualMode
import zuiwan.model.artistline
import zuiwan.model.fallbackart
import zuiwan.model.trackart
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.artimage
import zuiwan.ui.chip
import zuiwan.ui.glass
import zuiwan.ui.iconbtn
import zuiwan.ui.reactivevisual

@Composable
fun viewvisual(
    onBack: () -> Unit,
    onOpenStudio: () -> Unit = {},
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val state by hub.pilot.state.collectAsState()
    val settings by hub.settings.collectAsState()
    val frame by hub.spectrum.frame.collectAsState()
    val lyricsPayload by hub.pilot.lyrics.collectAsState()
    val pluginCss by hub.pluginCss.collectAsState()
    val pluginJs by hub.pluginJs.collectAsState()

    var showHud by remember { mutableStateOf(true) }
    var showLyrics by remember { mutableStateOf(true) }

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    val track = state.current
    val activeIdx = remember(lyricsPayload.lines, state.positionMs, lyricsPayload.synced) {
        if (!lyricsPayload.synced) -1 else LyricParser.activeline(lyricsPayload.lines, state.positionMs)
    }
    val activeLine = lyricsPayload.lines.getOrNull(activeIdx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04050A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                showHud = !showHud
            },
    ) {
        reactivevisual(
            mode = settings.visualMode,
            preset = settings.shaderPreset,
            frame = frame,
            customCss = "$pluginCss\n${settings.customCss}",
            customJs = pluginJs,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = if (showHud) 0.45f else 0.12f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (showHud) 0.68f else 0.25f),
                    ),
                ),
        )

        if (showLyrics && activeLine != null && activeLine.text.isNotBlank()) {
            val syllables = remember(activeLine.timeMs, activeLine.endMs, activeLine.text, activeLine.words) {
                LyricParser.syllablesfor(activeLine)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            ) {
                if (settings.syllableLyrics && syllables.isNotEmpty()) {
                    val unsung = Color.White.copy(alpha = 0.36f)
                    val styled = buildAnnotatedString {
                        syllables.forEach { syl ->
                            val dur = (syl.endMs - syl.startMs).coerceAtLeast(35L)
                            val prog = ((state.positionMs - syl.startMs).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                            val col = when {
                                prog <= 0f -> unsung
                                prog < 1f -> lerp(unsung, glassTokens.accent, prog)
                                else -> Color.White
                            }
                            withStyle(
                                SpanStyle(
                                    color = col,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(
                                        color = if (prog > 0f && prog < 1f) glassTokens.accent else Color.Black.copy(alpha = 0.65f),
                                        offset = Offset.Zero,
                                        blurRadius = 22f,
                                    ),
                                ),
                            ) {
                                append(syl.text)
                            }
                        }
                    }
                    Text(
                        text = styled,
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = activeLine.text,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    iconbtn(
                        icon = ZIcons.ArrowDown,
                        tint = Color.White,
                        onClick = onBack,
                    )
                    Text(
                        text = "VISUALIZER",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        iconbtn(
                            icon = ZIcons.Lyrics,
                            tint = if (showLyrics) glassTokens.accent else Color.White.copy(alpha = 0.72f),
                            onClick = { showLyrics = !showLyrics },
                        )
                        iconbtn(
                            icon = ZIcons.Palette,
                            tint = Color.White,
                            onClick = onOpenStudio,
                        )
                        iconbtn(
                            icon = if (settings.fullscreenMode) ZIcons.Minimize else ZIcons.Fullscreen,
                            tint = if (settings.fullscreenMode) glassTokens.accent else Color.White,
                            onClick = {
                                scope.launch {
                                    hub.store.updatesettings { it.copy(fullscreenMode = !it.fullscreenMode) }
                                }
                            },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VisualMode.entries) { mode ->
                            chip(
                                label = mode.label,
                                selected = settings.visualMode == mode,
                                onClick = {
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(visualMode = mode) }
                                    }
                                },
                            )
                        }
                    }

                    if (settings.visualMode == VisualMode.SHADER) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ShaderPreset.entries) { preset ->
                                chip(
                                    label = preset.label,
                                    selected = settings.shaderPreset == preset,
                                    onClick = {
                                        scope.launch {
                                            hub.store.updatesettings { it.copy(shaderPreset = preset) }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    glass(
                        shape = RoundedCornerShape(26.dp),
                        strong = true,
                        frosted = true,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            artimage(
                                url = track?.trackart(),
                                fallbackUrl = track?.fallbackart(),
                                shape = RoundedCornerShape(14.dp),
                                fallbackIcon = ZIcons.Note,
                                modifier = Modifier.size(52.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.title ?: "Ready to Visualize",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = glassTokens.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(),
                                )
                                Text(
                                    text = track?.artistline() ?: "Play any track to drive live shaders",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            iconbtn(
                                icon = ZIcons.Prev,
                                onClick = { hub.pilot.skipprev() },
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(glassTokens.accent)
                                    .clickable { hub.pilot.toggleplay() },
                            ) {
                                Icon(
                                    imageVector = if (state.playing) ZIcons.Pause else ZIcons.Play,
                                    contentDescription = null,
                                    tint = glassTokens.onAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            iconbtn(
                                icon = ZIcons.Next,
                                onClick = { hub.pilot.skipnext() },
                            )
                        }
                    }
                }
            }
        }
    }
}
