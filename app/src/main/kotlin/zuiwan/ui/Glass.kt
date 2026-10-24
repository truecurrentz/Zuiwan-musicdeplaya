package zuiwan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import zuiwan.theme.LocalGlass

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
                    .blur(64.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (glass.isDark) {
                            glass.bg.copy(alpha = 0.72f)
                        } else {
                            glass.bg.copy(alpha = 0.80f)
                        },
                    ),
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val topAuraAlpha = if (glass.isDark) {
                if (intense) 0.34f else 0.20f
            } else {
                if (intense) 0.22f else 0.14f
            }
            val sideAuraAlpha = if (glass.isDark) {
                if (intense) 0.24f else 0.14f
            } else {
                if (intense) 0.18f else 0.10f
            }

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.accent.copy(alpha = topAuraAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.10f),
                    radius = size.maxDimension * 0.70f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glass.accentSecondary.copy(alpha = sideAuraAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.32f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
        }
    }
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevated: Boolean = false,
    strong: Boolean = false,
    accented: Boolean = false,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val baseFill = when {
        accented -> glass.accentSoft
        strong -> glass.glassStrong
        elevated -> glass.glassElevated
        else -> glass.glassFill
    }
    val sheenTop = if (glass.isDark) {
        Color.White.copy(alpha = if (strong) 0.08f else 0.12f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            if (accented) glass.accent.copy(alpha = 0.55f) else glass.glassHighlight,
            if (accented) glass.accent.copy(alpha = 0.22f) else glass.glassBorder,
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
        contentAlignment = contentAlignment,
        modifier = modifier
            .then(shadowMod)
            .clip(shape)
            .background(baseFill)
            .background(
                Brush.verticalGradient(
                    colors = listOf(sheenTop, Color.Transparent),
                ),
            )
            .border(BorderStroke(1.dp, borderBrush), shape)
            .then(clickMod)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
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
                .shadow(10.dp, CircleShape, ambientColor = glass.accent.copy(alpha = 0.45f), spotColor = glass.accent.copy(alpha = 0.45f))
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(glass.accent, glass.accentSecondary.copy(alpha = 0.9f)),
                    ),
                )
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
        GlassBox(
            shape = CircleShape,
            elevated = active,
            accented = active,
            onClick = onClick,
            contentAlignment = Alignment.Center,
            modifier = modifier.size(size),
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = glass.onAccent,
            )
        }
    } else {
        GlassBox(
            shape = shape,
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
            modifier = modifier,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = glass.inkSecondary,
                        modifier = Modifier.size(15.dp),
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
}

@Composable
fun GlassTopBar(
    title: String,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = ZIcons.Back,
                onClick = onBack,
                size = 40.dp,
                iconSize = 19.dp,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(999.dp)
    if (primary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .height(48.dp)
                .shadow(10.dp, shape, ambientColor = glass.accent.copy(alpha = 0.4f), spotColor = glass.accent.copy(alpha = 0.4f))
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(glass.accent, glass.accentSecondary.copy(alpha = 0.92f)),
                    ),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glass.onAccent,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = glass.onAccent,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    } else {
        GlassBox(
            shape = shape,
            elevated = true,
            onClick = onClick,
            contentAlignment = Alignment.Center,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = modifier.height(48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.ink,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
