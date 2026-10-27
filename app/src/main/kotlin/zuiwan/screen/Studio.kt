package zuiwan.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import zuiwan.model.FontChoice
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.theme.parsehex
import zuiwan.ui.Route
import zuiwan.ui.ZIcons
import zuiwan.ui.chip
import zuiwan.ui.glass
import zuiwan.ui.headerscroll
import zuiwan.ui.iconbtn
import zuiwan.ui.pillbtn
import zuiwan.ui.scrollhost
import zuiwan.ui.topbar
import java.io.File
import kotlin.math.roundToInt

private val accentSwatches = listOf(
    "#7CB8FF" to "Ice Blue",
    "#FF2D78" to "Rose Neon",
    "#A78BFA" to "Iris",
    "#34D399" to "Mint",
    "#F59E0B" to "Amber",
    "#38BDF8" to "Sky",
    "#FB7185" to "Sakura",
    "#E879F9" to "Orchid",
    "#2DD4BF" to "Teal",
    "#FACC15" to "Solar",
)

private val cssTemplates = listOf(
    "Sakura" to ":root {\n  --accent: #fb7185;\n  --accent-secondary: #f472b6;\n  --glass-opacity: 0.90;\n  --blur: 72px;\n}",
    "Cyber" to ":root {\n  --accent: #00f2fe;\n  --accent-secondary: #ff2d78;\n  --bg: #05060d;\n  --glass-opacity: 0.72;\n  --blur: 48px;\n}",
    "Emerald" to ":root {\n  --accent: #34d399;\n  --accent-secondary: #2dd4bf;\n  --bg: #07100d;\n  --glass-opacity: 0.85;\n}",
    "Minimal" to ":root {\n  --glass-opacity: 0.45;\n  --blur: 84px;\n  --font-scale: 0.96;\n}",
)

@Composable
fun viewstudio(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val scope = rememberCoroutineScope()

    val settings by hub.settings.collectAsState()
    val plugins by hub.plugins.collectAsState()

    var activeSection by remember { mutableIntStateOf(0) }
    var hexDraft by remember(settings.customAccentHex) { mutableStateOf(settings.customAccentHex) }
    var cssDraft by remember(settings.customCss) { mutableStateOf(settings.customCss) }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.pickasset(uri, "wallpaper")
    }
    val motionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.pickasset(uri, "motion")
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.pickasset(uri, "banner")
    }
    val playerBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.pickasset(uri, "playerbg")
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.pickasset(uri, "font")
    }
    val pluginPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.installplugin(uri)
    }

    val listState = rememberLazyListState()
    val headerScroll = headerscroll()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16
        }
    }

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            topbar(
                title = "Studio",
                onBack = onBack,
                actions = {
                    iconbtn(
                        icon = ZIcons.Sparkles,
                        onClick = { onNavigate(Route.Visualizer) },
                    )
                },
            )
        },
    ) { topInset ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topInset + 8.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
                start = 18.dp,
                end = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tabs = listOf(
                        0 to "Appearance",
                        1 to "Wallpapers & Motion",
                        2 to "Custom CSS",
                        3 to "Plugins",
                    )
                    items(tabs) { (idx, label) ->
                        chip(
                            label = label,
                            selected = activeSection == idx,
                            onClick = { activeSection = idx },
                        )
                    }
                }
            }

            when (activeSection) {
                0 -> {
                    item {
                        studiocard("Custom Accent Color") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                item {
                                    chip(
                                        label = "Dynamic Art",
                                        selected = settings.customAccentHex.isBlank(),
                                        onClick = {
                                            hexDraft = ""
                                            scope.launch {
                                                hub.store.updatesettings { it.copy(customAccentHex = "") }
                                            }
                                        },
                                    )
                                }
                                items(accentSwatches) { (hex, _) ->
                                    val c = parsehex(hex) ?: glassTokens.accent
                                    val selected = settings.customAccentHex.equals(hex, ignoreCase = true)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .clickable {
                                                hexDraft = hex
                                                scope.launch {
                                                    hub.store.updatesettings { it.copy(customAccentHex = hex) }
                                                }
                                            },
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = ZIcons.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = hexDraft,
                                    onValueChange = { hexDraft = it },
                                    singleLine = true,
                                    label = { Text("Hex (#RRGGBB)") },
                                    modifier = Modifier.weight(1f),
                                )
                                pillbtn(
                                    text = "Apply",
                                    icon = ZIcons.Check,
                                    primary = true,
                                    onClick = {
                                        val clean = hexDraft.trim()
                                        if (clean.isEmpty() || parsehex(clean) != null) {
                                            scope.launch {
                                                hub.store.updatesettings { it.copy(customAccentHex = clean) }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item {
                        studiocard("Blur & Opacity Controls") {
                            sliderfield(
                                label = "Glass Blur Radius",
                                valueText = "${settings.blurRadius.roundToInt()} dp",
                                value = settings.blurRadius,
                                range = 0f..96f,
                                onChange = { v ->
                                    scope.launch {
                                        hub.store.updatesettings {
                                            it.copy(blurRadius = v, blurEnabled = v > 1f)
                                        }
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            sliderfield(
                                label = "Glass Opacity",
                                valueText = "${(settings.glassOpacity * 100).roundToInt()}%",
                                value = settings.glassOpacity,
                                range = 0.20f..1.40f,
                                onChange = { v ->
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(glassOpacity = v) }
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            sliderfield(
                                label = "Backdrop Dim",
                                valueText = "${(settings.bgDim * 100).roundToInt()}%",
                                value = settings.bgDim,
                                range = 0.12f..0.92f,
                                onChange = { v ->
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(bgDim = v) }
                                    }
                                },
                            )
                        }
                    }

                    item {
                        studiocard("Font Selection") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(FontChoice.entries) { choice ->
                                    chip(
                                        label = choice.label,
                                        selected = settings.fontChoice == choice,
                                        onClick = {
                                            scope.launch {
                                                hub.store.updatesettings { it.copy(fontChoice = choice) }
                                            }
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (settings.customFontPath.isNotBlank()) {
                                        File(settings.customFontPath).name
                                    } else {
                                        "Import any .ttf or .otf typeface"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glassTokens.inkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                pillbtn(
                                    text = "Load Font",
                                    icon = ZIcons.Font,
                                    onClick = { fontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*")) },
                                )
                            }
                        }
                    }

                    item {
                        studiocard("Compact & Fullscreen Modes") {
                            studiotoggle(
                                title = "Compact Mode",
                                subtitle = "Tighter track rows, sleek miniplayer, and denser lists",
                                checked = settings.compactMode,
                                onToggle = { next ->
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(compactMode = next) }
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            studiotoggle(
                                title = "Fullscreen Mode",
                                subtitle = "Hide Android status and navigation bars edge-to-edge",
                                checked = settings.fullscreenMode,
                                onToggle = { next ->
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(fullscreenMode = next) }
                                    }
                                },
                            )
                        }
                    }
                }

                1 -> {
                    item {
                        studiocard("Custom Wallpaper") {
                            mediarow(
                                label = if (settings.wallpaperPath.isNotBlank()) "Custom wallpaper active" else "No custom wallpaper selected",
                                active = settings.wallpaperPath.isNotBlank(),
                                pickLabel = "Pick Image",
                                icon = ZIcons.Image,
                                onPick = { wallpaperPicker.launch(arrayOf("image/*")) },
                                onClear = {
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(wallpaperPath = "") }
                                    }
                                },
                            )
                        }
                    }

                    item {
                        studiocard("Background GIF / Video") {
                            mediarow(
                                label = when {
                                    settings.motionBgPath.isBlank() -> "Choose an animated GIF or looping MP4/WebM video"
                                    settings.motionIsVideo -> "Looping video background active"
                                    else -> "Animated GIF background active"
                                },
                                active = settings.motionBgPath.isNotBlank(),
                                pickLabel = "Pick GIF / Video",
                                icon = ZIcons.Video,
                                onPick = { motionPicker.launch(arrayOf("image/gif", "video/*")) },
                                onClear = {
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(motionBgPath = "", motionIsVideo = false) }
                                    }
                                },
                            )
                        }
                    }

                    item {
                        studiocard("Player Banner & Background") {
                            mediarow(
                                label = if (settings.bannerPath.isNotBlank()) "Custom player banner active" else "Personalize the banner header inside Now Playing",
                                active = settings.bannerPath.isNotBlank(),
                                pickLabel = "Set Banner",
                                icon = ZIcons.Image,
                                onPick = { bannerPicker.launch(arrayOf("image/*")) },
                                onClear = {
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(bannerPath = "") }
                                    }
                                },
                            )
                            Spacer(Modifier.height(14.dp))
                            mediarow(
                                label = if (settings.playerBgPath.isNotBlank()) "Custom player background active" else "Override the Now Playing backdrop artwork",
                                active = settings.playerBgPath.isNotBlank(),
                                pickLabel = "Set Background",
                                icon = ZIcons.Image,
                                onPick = { playerBgPicker.launch(arrayOf("image/*")) },
                                onClear = {
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(playerBgPath = "") }
                                    }
                                },
                            )
                            Spacer(Modifier.height(14.dp))
                            studiotoggle(
                                title = "Live Visualizer in Player Background",
                                subtitle = "Render reactive WebGL shaders behind Now Playing",
                                checked = settings.playerVisualBg,
                                onToggle = { next ->
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(playerVisualBg = next) }
                                    }
                                },
                            )
                        }
                    }
                }

                2 -> {
                    item {
                        studiocard("Custom CSS Engine") {
                            Text(
                                text = "Supports --accent, --accent-secondary, --bg, --ink, --glass-opacity, --blur, --font-scale, plus WebGL canvas rules.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glassTokens.inkSecondary,
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cssTemplates) { (name, code) ->
                                    chip(
                                        label = name,
                                        selected = false,
                                        onClick = { cssDraft = code },
                                    )
                                }
                                item {
                                    chip(
                                        label = "Clear",
                                        selected = false,
                                        onClick = { cssDraft = "" },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = cssDraft,
                                onValueChange = { cssDraft = it },
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = glassTokens.ink,
                                ),
                                minLines = 7,
                                maxLines = 14,
                                placeholder = {
                                    Text(
                                        ":root {\n  --accent: #7cb8ff;\n  --glass-opacity: 0.82;\n  --blur: 64px;\n}",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                pillbtn(
                                    text = "Apply CSS",
                                    icon = ZIcons.Code,
                                    primary = true,
                                    onClick = {
                                        scope.launch {
                                            hub.store.updatesettings { it.copy(customCss = cssDraft) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                3 -> {
                    item {
                        studiocard("Plugin Extensions") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "${settings.enabledPlugins.size} active",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = glassTokens.accent,
                                )
                                pillbtn(
                                    text = "Install Plugin",
                                    icon = ZIcons.Plugin,
                                    primary = true,
                                    onClick = {
                                        pluginPicker.launch(arrayOf("application/json", "text/*", "*/*"))
                                    },
                                )
                            }
                        }
                    }

                    items(plugins, key = { it.id }) { plugin ->
                        val enabled = plugin.id in settings.enabledPlugins
                        glass(
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(if (enabled) glassTokens.accentSoft else glassTokens.glassElevated),
                                ) {
                                    Icon(
                                        imageVector = ZIcons.Plugin,
                                        contentDescription = null,
                                        tint = if (enabled) glassTokens.accent else glassTokens.inkSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${plugin.name} · v${plugin.version}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = glassTokens.ink,
                                    )
                                    Text(
                                        text = plugin.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = glassTokens.inkSecondary,
                                    )
                                }
                                if (!plugin.builtin) {
                                    iconbtn(
                                        icon = ZIcons.Trash,
                                        tint = glassTokens.danger,
                                        onClick = { hub.removeplugin(plugin.id) },
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { hub.toggleplugin(plugin.id) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = glassTokens.onAccent,
                                        checkedTrackColor = glassTokens.accent,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun studiocard(
    title: String,
    content: @Composable () -> Unit,
) {
    val glassTokens = LocalGlass.current
    glass(
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = glassTokens.ink,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun sliderfield(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val glassTokens = LocalGlass.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = glassTokens.ink)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = glassTokens.accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = glassTokens.accent,
                activeTrackColor = glassTokens.accent,
                inactiveTrackColor = glassTokens.glassElevated,
            ),
        )
    }
}

@Composable
private fun studiotoggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val glassTokens = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = glassTokens.ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = glassTokens.inkSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = glassTokens.onAccent,
                checkedTrackColor = glassTokens.accent,
            ),
        )
    }
}

@Composable
private fun mediarow(
    label: String,
    active: Boolean,
    pickLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) glassTokens.accent else glassTokens.inkSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pillbtn(
                text = pickLabel,
                icon = icon,
                primary = true,
                onClick = onPick,
            )
            if (active) {
                pillbtn(
                    text = "Clear",
                    icon = ZIcons.Close,
                    onClick = onClear,
                )
            }
        }
    }
}
