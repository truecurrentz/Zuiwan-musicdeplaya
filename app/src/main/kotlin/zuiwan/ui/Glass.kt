package zuiwan.ui

import app.zuiwan.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import zuiwan.model.upscaleart
import zuiwan.theme.LocalGlass
import zuiwan.theme.LocalHaze

@Stable
class HeaderScroll {
    var heightPx by mutableFloatStateOf(0f)
        private set
    var offsetPx by mutableFloatStateOf(0f)

    val visibility: Float
        get() = if (heightPx <= 1f) 1f else (1f + offsetPx / heightPx).coerceIn(0f, 1f)

    fun updateheight(px: Float) {
        if (px <= 0f) return
        heightPx = px
        offsetPx = offsetPx.coerceIn(-px, 0f)
    }

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val h = heightPx
            if (h <= 0f) return Offset.Zero
            val dy = consumed.y
            if (dy != 0f) {
                offsetPx = (offsetPx + dy).coerceIn(-h, 0f)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val h = heightPx
            if (h > 0f && offsetPx < -1f && offsetPx > -h + 1f) {
                val target = if (offsetPx > -h * 0.5f) 0f else -h
                animate(
                    initialValue = offsetPx,
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ ->
                    offsetPx = value
                }
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun headerscroll(): HeaderScroll = remember { HeaderScroll() }

@Composable
fun scrollhost(
    scroll: HeaderScroll,
    lifted: Boolean,
    overlayHero: Boolean = false,
    pinned: Boolean = false,
    fallbackHeight: Dp = 88.dp,
    header: @Composable () -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    val glassTokens = LocalGlass.current
    val haze = LocalHaze.current
    val density = LocalDensity.current

    val headerMask = remember {
        Brush.verticalGradient(
            0f to Color.Black,
            0.78f to Color.Black,
            1f to Color.Transparent,
        )
    }

    LaunchedEffect(lifted, pinned) {
        if ((pinned || !lifted) && scroll.offsetPx < 0f) {
            animate(
                initialValue = scroll.offsetPx,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ ->
                scroll.offsetPx = value
            }
        }
    }

    val insetDp = if (overlayHero) {
        0.dp
    } else if (scroll.heightPx > 1f) {
        with(density) { scroll.heightPx.toDp() }
    } else {
        fallbackHeight
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { base -> if (pinned) base else base.nestedScroll(scroll.connection) },
    ) {
        content(insetDp)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { scroll.updateheight(it.height.toFloat()) }
                .graphicsLayer {
                    translationY = if (pinned) 0f else scroll.offsetPx
                    alpha = if (pinned) 1f else scroll.visibility
                },
        ) {
            if (lifted) {
                if (haze != null && glassTokens.blurEnabled) {
                    barblur(
                        hazeState = haze,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = headerMask,
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to glassTokens.bg.copy(alpha = 0.94f),
                                    0.78f to glassTokens.bg.copy(alpha = 0.80f),
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                }
            }
            header()
        }
    }
}

@Composable
fun backdrop(
    artworkUrl: String? = null,
    intense: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    val cleanArt = remember(artworkUrl) { artworkUrl?.let { upscaleart(it, 544) }.orEmpty() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glassTokens.bg),
    ) {
        if (intense && cleanArt.isNotBlank()) {
            AsyncImage(
                model = cleanArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(68.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (glassTokens.isDark) {
                            glassTokens.bg.copy(alpha = 0.68f)
                        } else {
                            glassTokens.bg.copy(alpha = 0.78f)
                        },
                    ),
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val topAlpha = if (glassTokens.isDark) {
                if (intense) 0.24f else 0.09f
            } else {
                if (intense) 0.14f else 0.05f
            }
            val sideAlpha = if (glassTokens.isDark) {
                if (intense) 0.15f else 0.05f
            } else {
                if (intense) 0.10f else 0.03f
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glassTokens.accent.copy(alpha = topAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.08f),
                    radius = size.maxDimension * 0.65f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glassTokens.accentSecondary.copy(alpha = sideAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.28f),
                    radius = size.maxDimension * 0.58f,
                ),
            )
        }
    }
}

@Composable
fun barblur(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val glassTokens = LocalGlass.current
    val baseSurface = if (glassTokens.isDark) Color(0xFF131620) else Color(0xFFF6F8FC)
    if (hazeState != null && glassTokens.blurEnabled) {
        val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
        val style = remember(glassTokens.isDark, baseSurface) {
            HazeBlurStyle {
                backgroundColor(baseSurface)
                blurRadius(28.dp)
                noiseFactor(0.04f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            baseSurface.copy(alpha = if (glassTokens.isDark) 0.72f else 0.78f),
                        ),
                    ),
                )
                fallbackColorEffect(HazeColorEffect.tint(baseSurface.copy(alpha = 0.88f)))
            }
        }
        Box(
            modifier = modifier.hazeBlur(
                input = hazeInput,
                style = style,
                expandLayerBounds = true,
            ),
        )
    } else {
        Box(
            modifier = modifier.background(glassTokens.glassStrong),
        )
    }
}

@Composable
fun glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
    strong: Boolean = false,
    frosted: Boolean = false,
    accented: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassTokens = LocalGlass.current
    val haze = LocalHaze.current
    val useLiveBlur = frosted && haze != null && glassTokens.blurEnabled

    val baseFill = when {
        accented -> glassTokens.accentSoft
        useLiveBlur -> Color.Transparent
        strong -> glassTokens.glassStrong
        elevated -> glassTokens.glassElevated
        else -> glassTokens.glassFill
    }

    val clickMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseFill)
            .then(clickMod),
    ) {
        if (useLiveBlur) {
            barblur(
                hazeState = haze,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            contentAlignment = contentAlignment,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun brandbar(
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glassTokens = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.wordmark),
            contentDescription = "Zuiwan",
            colorFilter = ColorFilter.tint(glassTokens.ink),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(25.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun iconbtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 21.dp,
    active: Boolean = false,
    solidAccent: Boolean = false,
    softFill: Boolean = false,
    showBadge: Boolean = false,
    tint: Color? = null,
) {
    val glassTokens = LocalGlass.current
    when {
        solidAccent -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(glassTokens.accent)
                    .clickable(onClick = onClick),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: glassTokens.onAccent,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
        softFill -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (active) glassTokens.accentSoft else glassTokens.glassElevated)
                    .clickable(onClick = onClick),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: if (active) glassTokens.accent else glassTokens.ink,
                    modifier = Modifier.size(iconSize),
                )
                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(glassTokens.accent),
                    )
                }
            }
        }
        else -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: if (active) glassTokens.accent else glassTokens.ink,
                    modifier = Modifier.size(iconSize),
                )
                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 7.dp, end = 7.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(glassTokens.accent),
                    )
                }
            }
        }
    }
}

@Composable
fun chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val glassTokens = LocalGlass.current
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) glassTokens.accent else glassTokens.glassElevated
    val contentTint = if (selected) glassTokens.onAccent else glassTokens.ink
    val iconTint = if (selected) glassTokens.onAccent else glassTokens.inkSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentTint,
        )
    }
}

@Composable
fun topbar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glassTokens = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            iconbtn(
                icon = ZIcons.Back,
                onClick = onBack,
            )
        } else {
            Spacer(Modifier.width(6.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            AnimatedVisibility(
                visible = showTitle && title.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = glassTokens.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = glassTokens.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun pillbtn(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    highContrast: Boolean = false,
) {
    val glassTokens = LocalGlass.current
    val shape = RoundedCornerShape(999.dp)
    when {
        highContrast -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = modifier
                    .height(48.dp)
                    .clip(shape)
                    .background(glassTokens.pillSolid)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassTokens.onPillSolid,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = glassTokens.onPillSolid,
                )
            }
        }
        primary -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = modifier
                    .height(46.dp)
                    .clip(shape)
                    .background(glassTokens.accent)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassTokens.onAccent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = glassTokens.onAccent,
                )
            }
        }
        else -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = modifier
                    .height(46.dp)
                    .clip(shape)
                    .background(glassTokens.glassElevated)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassTokens.ink,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = glassTokens.ink,
                )
            }
        }
    }
}

@Composable
fun hero(
    title: String,
    artworkUrl: String?,
    fallbackIcon: ImageVector,
    onBack: (() -> Unit)? = null,
    circularArt: Boolean = false,
    artworkSlot: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    metadata: String? = null,
    description: String? = null,
    onTitleClick: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    primaryActions: @Composable RowScope.() -> Unit = {},
) {
    val glassTokens = LocalGlass.current
    var descExpanded by remember { mutableStateOf(false) }
    val artShape = if (circularArt) CircleShape else RoundedCornerShape(22.dp)
    val cleanArt = remember(artworkUrl) { artworkUrl?.let { upscaleart(it, 1080) }.orEmpty() }
    val heroFade = remember(glassTokens.isDark) {
        val peak = if (glassTokens.isDark) 0.56f else 0.36f
        val mid = if (glassTokens.isDark) 0.30f else 0.18f
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = peak),
            0.46f to Color.Black.copy(alpha = mid),
            0.80f to Color.Black.copy(alpha = 0.08f),
            1.0f to Color.Transparent,
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = heroFade,
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            if (cleanArt.isNotBlank()) {
                AsyncImage(
                    model = cleanArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(64.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    glassTokens.accent.copy(alpha = if (glassTokens.isDark) 0.34f else 0.22f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = if (onBack != null) 8.dp else 56.dp, bottom = 18.dp),
        ) {
            if (onBack != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    iconbtn(
                        icon = ZIcons.Back,
                        onClick = onBack,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = topActions,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (artworkSlot != null) {
                Box(
                    modifier = Modifier
                        .size(216.dp)
                        .clip(artShape),
                ) {
                    artworkSlot()
                }
            } else {
                artimage(
                    url = cleanArt,
                    shape = artShape,
                    fallbackIcon = fallbackIcon,
                    iconScale = 0.36f,
                    modifier = Modifier.size(216.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = glassTokens.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = glassTokens.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier),
                )
            }

            if (!metadata.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassTokens.inkSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassTokens.inkSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = if (descExpanded) 12 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .clickable { descExpanded = !descExpanded },
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                content = primaryActions,
            )
        }
    }
}
