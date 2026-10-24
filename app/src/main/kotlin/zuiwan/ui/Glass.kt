package zuiwan.ui

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import zuiwan.theme.LocalGlass
import zuiwan.theme.LocalHaze

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
                if (intense) 0.28f else 0.11f
            } else {
                if (intense) 0.18f else 0.07f
            }
            val sideAlpha = if (glass.isDark) {
                if (intense) 0.18f else 0.07f
            } else {
                if (intense) 0.12f else 0.05f
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
    val baseSurface = if (glass.isDark) Color(0xFF1A1C26) else Color(0xFFF6F7FB)
    if (hazeState != null && glass.blurEnabled) {
        val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
        val style = remember(glass.isDark, baseSurface) {
            HazeBlurStyle {
                backgroundColor(baseSurface)
                blurRadius(30.dp)
                noiseFactor(0.08f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            baseSurface.copy(alpha = if (glass.isDark) 0.72f else 0.78f),
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

    val sheenTop = if (glass.isDark) {
        Color.White.copy(alpha = if (strong || frosted) 0.10f else 0.08f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            if (accented) glass.accent.copy(alpha = 0.52f) else glass.glassHighlight,
            if (accented) glass.accent.copy(alpha = 0.18f) else glass.glassBorder,
        ),
    )

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
            .border(BorderStroke(0.9.dp, borderBrush), shape)
            .then(clickMod),
    ) {
        if (useLiveBlur) {
            FrostedBarBackdrop(
                hazeState = haze,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(sheenTop, Color.Transparent),
                    ),
                ),
        )
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
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = ZIcons.Brand,
                contentDescription = null,
                tint = glass.ink,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "Zuiwan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = glass.ink,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    iconSize: Dp = 19.dp,
    active: Boolean = false,
    solidAccent: Boolean = false,
    tint: Color? = null,
) {
    val glass = LocalGlass.current
    if (solidAccent) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = glass.accent.copy(alpha = 0.36f),
                    spotColor = glass.accent.copy(alpha = 0.36f),
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
    } else {
        val fill = when {
            active -> glass.accentSoft
            else -> glass.glassElevated
        }
        val rim = if (active) glass.accent.copy(alpha = 0.48f) else glass.glassBorder
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(0.85.dp, rim, CircleShape)
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
    if (selected) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = modifier
                .clip(shape)
                .background(glass.accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = glass.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = glass.onAccent,
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = modifier
                .clip(shape)
                .background(glass.glassElevated)
                .border(0.85.dp, glass.glassBorder, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = glass.inkSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = glass.ink,
            )
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = ZIcons.Back,
                onClick = onBack,
                size = 40.dp,
                iconSize = 19.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    .shadow(8.dp, shape, ambientColor = glass.accent.copy(alpha = 0.32f), spotColor = glass.accent.copy(alpha = 0.32f))
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
                    .border(0.85.dp, glass.glassBorder, shape)
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
    onBack: () -> Unit,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    metadata: String? = null,
    onTitleClick: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
    primaryActions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    val heroText = if (glass.isDark) Color.White else glass.ink
    val topScrim = if (glass.isDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 396.dp)
            .background(glass.bg),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .background(glass.bgSecondary),
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = glass.inkMuted,
                    modifier = Modifier.size(84.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to topScrim,
                        0.22f to Color.Transparent,
                        0.48f to Color.Transparent,
                        0.76f to glass.bg.copy(alpha = 0.84f),
                        1.0f to glass.bg,
                    ),
                ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            GlassIconButton(
                icon = ZIcons.Back,
                onClick = onBack,
                size = 40.dp,
                iconSize = 19.dp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = topActions,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 164.dp, bottom = 20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = heroText,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier,
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
                    modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier,
                )
            }

            if (!metadata.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroText.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
                content = primaryActions,
            )
        }
    }
}
