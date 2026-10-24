package zuiwan.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
fun rememberheaderscroll(): HeaderScroll = remember { HeaderScroll() }

@Composable
fun ScrollHeaderHost(
    scroll: HeaderScroll,
    lifted: Boolean,
    overlayHero: Boolean = false,
    fallbackHeight: Dp = 88.dp,
    header: @Composable () -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    val glass = LocalGlass.current
    val haze = LocalHaze.current
    val density = LocalDensity.current

    LaunchedEffect(lifted) {
        if (!lifted && scroll.offsetPx < 0f) {
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
            .nestedScroll(scroll.connection),
    ) {
        content(insetDp)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { scroll.updateheight(it.height.toFloat()) }
                .graphicsLayer {
                    translationY = scroll.offsetPx
                    alpha = scroll.visibility
                },
        ) {
            if (lifted) {
                if (haze != null && glass.blurEnabled) {
                    FrostedBarBackdrop(
                        hazeState = haze,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to glass.bg.copy(alpha = 0.94f),
                                    0.78f to glass.bg.copy(alpha = 0.82f),
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
fun AmbientBackdrop(
    artworkUrl: String? = null,
    intense: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glass.bg),
    ) {
        if (intense && !artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
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
                        if (glass.isDark) {
                            glass.bg.copy(alpha = 0.68f)
                        } else {
                            glass.bg.copy(alpha = 0.78f)
                        },
                    ),
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val topAlpha = if (glass.isDark) {
                if (intense) 0.24f else 0.10f
            } else {
                if (intense) 0.14f else 0.06f
            }
            val sideAlpha = if (glass.isDark) {
                if (intense) 0.15f else 0.06f
            } else {
                if (intense) 0.10f else 0.04f
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.accent.copy(alpha = topAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.08f),
                    radius = size.maxDimension * 0.65f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.accentSecondary.copy(alpha = sideAlpha),
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
fun FrostedBarBackdrop(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val baseSurface = if (glass.isDark) Color(0xFF141620) else Color(0xFFF7F8FC)
    if (hazeState != null && glass.blurEnabled) {
        val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
        val style = remember(glass.isDark, baseSurface) {
            HazeBlurStyle {
                backgroundColor(baseSurface)
                blurRadius(28.dp)
                noiseFactor(0.05f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            baseSurface.copy(alpha = if (glass.isDark) 0.74f else 0.80f),
                        ),
                    ),
                )
                fallbackColorEffect(HazeColorEffect.tint(baseSurface.copy(alpha = 0.90f)))
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
            modifier = modifier.background(glass.glassStrong),
        )
    }
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
    strong: Boolean = false,
    frosted: Boolean = false,
    accented: Boolean = false,
    bordered: Boolean = false,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val haze = LocalHaze.current
    val useLiveBlur = frosted && haze != null && glass.blurEnabled

    val baseFill = when {
        accented -> glass.accentSoft
        useLiveBlur -> Color.Transparent
        strong -> glass.glassStrong
        elevated -> glass.glassElevated
        else -> glass.glassFill
    }

    val borderMod = if (bordered) {
        Modifier.border(
            BorderStroke(0.6.dp, glass.glassBorder),
            shape,
        )
    } else {
        Modifier
    }

    val shadowMod = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = glass.glassShadow,
            spotColor = glass.glassShadow,
        )
    } else {
        Modifier
    }

    val clickMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowMod)
            .clip(shape)
            .background(baseFill)
            .then(borderMod)
            .then(clickMod),
    ) {
        if (useLiveBlur) {
            FrostedBarBackdrop(
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
fun BrandBar(
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = ZIcons.Wordmark,
            contentDescription = "Zuiwan",
            tint = glass.ink,
            modifier = Modifier
                .height(25.dp)
                .width(104.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 21.dp,
    active: Boolean = false,
    solidAccent: Boolean = false,
    softFill: Boolean = false,
    tint: Color? = null,
) {
    val glass = LocalGlass.current
    when {
        solidAccent -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = glass.accent.copy(alpha = 0.34f),
                        spotColor = glass.accent.copy(alpha = 0.34f),
                    )
                    .clip(CircleShape)
                    .background(glass.accent)
                    .clickable(onClick = onClick),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: glass.onAccent,
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
                    .background(if (active) glass.accentSoft else glass.glassElevated)
                    .clickable(onClick = onClick),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: if (active) glass.accent else glass.ink,
                    modifier = Modifier.size(iconSize),
                )
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
                    tint = tint ?: if (active) glass.accent else glass.ink,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) glass.accent else glass.glassElevated
    val contentTint = if (selected) glass.onAccent else glass.ink
    val iconTint = if (selected) glass.onAccent else glass.inkSecondary

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
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(
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
                        color = glass.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.inkSecondary,
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
fun GlassPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    highContrast: Boolean = false,
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(999.dp)
    when {
        highContrast -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = modifier
                    .height(48.dp)
                    .shadow(10.dp, shape, ambientColor = glass.glassShadow, spotColor = glass.glassShadow)
                    .clip(shape)
                    .background(glass.pillSolid)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.onPillSolid,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.onPillSolid,
                )
            }
        }
        primary -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = modifier
                    .height(46.dp)
                    .shadow(8.dp, shape, ambientColor = glass.accent.copy(alpha = 0.28f), spotColor = glass.accent.copy(alpha = 0.28f))
                    .clip(shape)
                    .background(glass.accent)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.onAccent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.onAccent,
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
                    .background(glass.glassElevated)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.ink,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.ink,
                )
            }
        }
    }
}

@Composable
fun DetailHero(
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
    val glass = LocalGlass.current
    var descExpanded by remember { mutableStateOf(false) }
    val artShape = if (circularArt) CircleShape else RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
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
                                    glass.accent.copy(alpha = if (glass.isDark) 0.30f else 0.18f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to glass.bg.copy(alpha = if (glass.isDark) 0.38f else 0.50f),
                            0.45f to glass.bg.copy(alpha = if (glass.isDark) 0.64f else 0.74f),
                            0.78f to glass.bg.copy(alpha = if (glass.isDark) 0.90f else 0.92f),
                            1.0f to glass.bg,
                        ),
                    ),
            )
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
                    GlassIconButton(
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
                        .size(220.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = artShape,
                            ambientColor = glass.accent.copy(alpha = 0.25f),
                            spotColor = glass.glassShadow,
                        )
                        .clip(artShape),
                ) {
                    artworkSlot()
                }
            } else if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(220.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = artShape,
                            ambientColor = glass.accent.copy(alpha = 0.25f),
                            spotColor = glass.glassShadow,
                        )
                        .clip(artShape)
                        .background(glass.glassElevated),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(artShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    glass.accent.copy(alpha = if (glass.isDark) 0.26f else 0.18f),
                                    glass.accentSecondary.copy(alpha = if (glass.isDark) 0.18f else 0.12f),
                                ),
                            ),
                        ),
                ) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = glass.accent,
                        modifier = Modifier.size(78.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = glass.ink,
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
                    color = glass.accent,
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
                    color = glass.inkSecondary,
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
                    color = glass.inkSecondary,
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
