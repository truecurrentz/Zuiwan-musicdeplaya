package zuiwan.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.QualityMode
import zuiwan.model.ThemeChoice
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassPillButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.Route
import zuiwan.ui.ScrollHeaderHost
import zuiwan.ui.ZIcons
import zuiwan.ui.rememberheaderscroll
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    MAIN("Settings"),
    APPEARANCE("Appearance"),
    AUDIO("Player & Audio"),
    CONTENT("Content & Privacy"),
    STORAGE("Storage & Backup"),
    ABOUT("About"),
}

private val regionCatalog = listOf(
    "US" to "United States",
    "PH" to "Philippines",
    "GB" to "United Kingdom",
    "JP" to "Japan",
    "KR" to "South Korea",
    "CA" to "Canada",
    "AU" to "Australia",
    "DE" to "Germany",
    "FR" to "France",
    "BR" to "Brazil",
    "ID" to "Indonesia",
    "SG" to "Singapore",
    "TW" to "Taiwan",
    "IN" to "India",
)

private val languageCatalog = listOf(
    "en" to "English",
    "fil" to "Filipino",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "id" to "Indonesian",
)

@Composable
fun viewsettings(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()

    val settings by hub.settings.collectAsState()
    val session by hub.session.collectAsState()
    val eqProfile by hub.eq.collectAsState()
    val downloadedTracks by hub.downloadedTracks.collectAsState()

    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    val initialUsage = remember { hub.vault.cacheusage() }
    var streamBytes by remember { mutableLongStateOf(initialUsage.first) }
    var downloadBytes by remember { mutableLongStateOf(initialUsage.second) }

    var showLastFm by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var lfmUser by remember(session.lastFmUser) { mutableStateOf(session.lastFmUser) }
    var lfmPass by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val headerScroll = rememberheaderscroll()

    BackHandler(enabled = page != SettingsPage.MAIN) {
        page = SettingsPage.MAIN
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                hub.backup.exportbackup(uri)
                    .onSuccess { statusMsg = "Backup saved successfully" }
                    .onFailure { statusMsg = it.message ?: "Backup failed" }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                hub.backup.restorebackup(uri)
                    .onSuccess { statusMsg = "Backup restored - restart Zuiwan to reload database" }
                    .onFailure { statusMsg = it.message ?: "Restore failed" }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
            title = { Text("Streaming Quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple(QualityMode.HIGH, "High", "Best clarity for Wi-Fi and headphones"),
                        Triple(QualityMode.AUTO, "Balanced", "Adapts smoothly to connection speed"),
                        Triple(QualityMode.LOW, "Data Saver", "Minimizes mobile data usage"),
                    ).forEach { (mode, title, desc) ->
                        DialogChoiceRow(
                            title = title,
                            subtitle = desc,
                            selected = settings.quality == mode,
                            onClick = {
                                scope.launch { hub.store.updatesettings { it.copy(quality = mode) } }
                                showQualityDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close", color = glass.inkSecondary)
                }
            },
        )
    }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
            title = { Text("Content Country") },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(regionCatalog, key = { it.first }) { (code, label) ->
                        DialogChoiceRow(
                            title = label,
                            subtitle = code,
                            selected = settings.contentCountry == code,
                            onClick = {
                                scope.launch {
                                    hub.store.updatesettings { it.copy(contentCountry = code) }
                                    hub.refreshhome()
                                    hub.refreshexplore()
                                }
                                showCountryDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryDialog = false }) {
                    Text("Close", color = glass.inkSecondary)
                }
            },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
            title = { Text("Content Language") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(languageCatalog, key = { it.first }) { (code, label) ->
                        DialogChoiceRow(
                            title = label,
                            subtitle = code.uppercase(),
                            selected = settings.contentLanguage == code,
                            onClick = {
                                scope.launch {
                                    hub.store.updatesettings { it.copy(contentLanguage = code) }
                                    hub.refreshhome()
                                }
                                showLanguageDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = glass.inkSecondary)
                }
            },
        )
    }

    if (showLastFm) {
        AlertDialog(
            onDismissRequest = { showLastFm = false },
            containerColor = glass.glassStrong,
            titleContentColor = glass.ink,
            textContentColor = glass.ink,
            title = { Text("Last.fm Scrobbler") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = lfmUser,
                        onValueChange = { lfmUser = it },
                        label = { Text("Username") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = lfmPass,
                        onValueChange = { lfmPass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            hub.scrobbler.loginlastfm(lfmUser.trim(), lfmPass)
                                .onSuccess { key ->
                                    hub.store.updatesession {
                                        it.copy(lastFmUser = lfmUser.trim(), lastFmSession = key, scrobbleEnabled = true)
                                    }
                                    statusMsg = "Connected to Last.fm as ${lfmUser.trim()}"
                                    showLastFm = false
                                }
                                .onFailure { err ->
                                    statusMsg = err.message
                                    showLastFm = false
                                }
                        }
                    },
                ) {
                    Text("Connect", color = glass.accent)
                }
            },
            dismissButton = {
                Row {
                    if (session.lastFmSession.isNotBlank()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    hub.store.updatesession {
                                        it.copy(lastFmUser = "", lastFmSession = "", scrobbleEnabled = false)
                                    }
                                }
                                showLastFm = false
                            },
                        ) {
                            Text("Disconnect", color = glass.danger)
                        }
                    }
                    TextButton(onClick = { showLastFm = false }) {
                        Text("Cancel", color = glass.inkSecondary)
                    }
                }
            },
        )
    }

    val listState = rememberLazyListState()
    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    ScrollHeaderHost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            GlassTopBar(
                title = page.title,
                onBack = {
                    if (page != SettingsPage.MAIN) {
                        page = SettingsPage.MAIN
                    } else {
                        onBack()
                    }
                },
            )
        },
    ) { topPad ->
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settingsSubpage",
        ) { currentPage ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = topPad + 8.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 32.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                statusMsg?.let { msg ->
                    item(key = "status_banner") {
                        GlassBox(
                            shape = RoundedCornerShape(18.dp),
                            accented = true,
                            onClick = { statusMsg = null },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(ZIcons.Close, null, tint = glass.inkSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                when (currentPage) {
                    SettingsPage.MAIN -> {
                        item(key = "account_hero") {
                            GlassBox(
                                shape = RoundedCornerShape(24.dp),
                                elevated = true,
                                contentPadding = PaddingValues(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                            ) {
                                if (session.active) {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ArtImage(
                                                url = session.accountAvatar,
                                                shape = CircleShape,
                                                fallbackIcon = ZIcons.User,
                                                modifier = Modifier.size(54.dp),
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = session.accountName.ifBlank { "YouTube Music" },
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = glass.ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = session.accountHandle.ifBlank { session.accountEmail.ifBlank { "Signed in" } },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = glass.inkSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            GlassPillButton(
                                                text = "Sync Library",
                                                icon = ZIcons.Refresh,
                                                primary = true,
                                                onClick = {
                                                    hub.syncaccount()
                                                    statusMsg = "Syncing library from YouTube Music..."
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                            GlassPillButton(
                                                text = "Sign Out",
                                                icon = ZIcons.Close,
                                                onClick = { hub.logout() },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(glass.accentSoft),
                                        ) {
                                            Icon(
                                                imageVector = ZIcons.User,
                                                contentDescription = null,
                                                tint = glass.accent,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "YouTube Music",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = glass.ink,
                                            )
                                            Text(
                                                text = "Sign in to sync playlists, likes & mixes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glass.inkSecondary,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        GlassPillButton(
                                            text = "Sign In",
                                            icon = ZIcons.User,
                                            primary = true,
                                            onClick = { onNavigate(Route.Login) },
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "group_experience") {
                            SettingsGroupTitle("Experience")
                            SettingsGroupColumn {
                                val themeName = if (settings.theme == ThemeChoice.DARK) "Dark" else "White"
                                val blurDesc = if (settings.blurEnabled) "Frosted blur" else "Minimal glass"
                                SettingsNavItem(
                                    icon = ZIcons.Palette,
                                    title = "Appearance",
                                    subtitle = "$themeName theme - $blurDesc",
                                    position = GroupPos.TOP,
                                    onClick = { page = SettingsPage.APPEARANCE },
                                )
                                SettingsNavItem(
                                    icon = ZIcons.Wave,
                                    title = "Player & Audio",
                                    subtitle = "${qualitylabel(settings.quality)} quality - ${if (eqProfile.enabled) eqProfile.name else "Parametric EQ"}",
                                    position = GroupPos.MIDDLE,
                                    onClick = { page = SettingsPage.AUDIO },
                                )
                                val countryLabel = regionCatalog.firstOrNull { it.first == settings.contentCountry }?.second ?: settings.contentCountry
                                SettingsNavItem(
                                    icon = ZIcons.Language,
                                    title = "Content & Privacy",
                                    subtitle = "$countryLabel - Lyrics & Last.fm",
                                    position = GroupPos.BOTTOM,
                                    onClick = { page = SettingsPage.CONTENT },
                                )
                            }
                        }

                        item(key = "group_system") {
                            SettingsGroupTitle("Data & System")
                            SettingsGroupColumn {
                                SettingsNavItem(
                                    icon = ZIcons.Storage,
                                    title = "Storage & Backup",
                                    subtitle = "${formatbytes(streamBytes + downloadBytes)} used - Backup & restore",
                                    position = GroupPos.TOP,
                                    onClick = {
                                        val fresh = hub.vault.cacheusage()
                                        streamBytes = fresh.first
                                        downloadBytes = fresh.second
                                        page = SettingsPage.STORAGE
                                    },
                                )
                                SettingsNavItem(
                                    icon = ZIcons.Equalizer,
                                    title = "Parametric Equalizer",
                                    subtitle = if (eqProfile.enabled) "Active: ${eqProfile.name}" else "10-band studio DSP",
                                    position = GroupPos.MIDDLE,
                                    onClick = { onNavigate(Route.Equalizer) },
                                )
                                SettingsNavItem(
                                    icon = ZIcons.Info,
                                    title = "About Zuiwan",
                                    subtitle = "Version 1.0.0",
                                    position = GroupPos.BOTTOM,
                                    onClick = { page = SettingsPage.ABOUT },
                                )
                            }
                        }
                    }

                    SettingsPage.APPEARANCE -> {
                        item(key = "theme_cards") {
                            SettingsGroupTitle("Theme")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                            ) {
                                ThemePreviewCard(
                                    title = "Dark",
                                    subtitle = "Deep obsidian glass",
                                    icon = ZIcons.Moon,
                                    previewBg = Color(0xFF0A0D14),
                                    previewCard = Color(0xFF171C28),
                                    previewInk = Color(0xFFF3F6FC),
                                    selected = settings.theme == ThemeChoice.DARK,
                                    onClick = {
                                        scope.launch { hub.store.updatesettings { it.copy(theme = ThemeChoice.DARK) } }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ThemePreviewCard(
                                    title = "White",
                                    subtitle = "Crisp daylight glass",
                                    icon = ZIcons.Sun,
                                    previewBg = Color(0xFFF7F9FC),
                                    previewCard = Color(0xFFE6ECF5),
                                    previewInk = Color(0xFF0F172A),
                                    selected = settings.theme == ThemeChoice.WHITE,
                                    onClick = {
                                        scope.launch { hub.store.updatesettings { it.copy(theme = ThemeChoice.WHITE) } }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        item(key = "surface_options") {
                            SettingsGroupTitle("Surfaces & Color")
                            SettingsGroupColumn {
                                SettingsSwitchItem(
                                    icon = ZIcons.Sparkles,
                                    title = "Frosted Glass Blur",
                                    subtitle = "Real-time backdrop blur on bars, dock, and player",
                                    checked = settings.blurEnabled,
                                    position = GroupPos.TOP,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(blurEnabled = v) } }
                                    },
                                )
                                SettingsSwitchItem(
                                    icon = ZIcons.Palette,
                                    title = "Dynamic Artwork Accent",
                                    subtitle = "Blend interface highlights from the playing cover art",
                                    checked = settings.dynamicColors,
                                    position = GroupPos.BOTTOM,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(dynamicColors = v) } }
                                    },
                                )
                            }
                        }
                    }

                    SettingsPage.AUDIO -> {
                        item(key = "audio_quality") {
                            SettingsGroupTitle("Streaming & DSP")
                            SettingsGroupColumn {
                                SettingsNavItem(
                                    icon = ZIcons.Wave,
                                    title = "Streaming Quality",
                                    subtitle = qualitylabel(settings.quality),
                                    position = GroupPos.TOP,
                                    onClick = { showQualityDialog = true },
                                )
                                SettingsNavItem(
                                    icon = ZIcons.Equalizer,
                                    title = "Parametric Equalizer",
                                    subtitle = if (eqProfile.enabled) "Active preset: ${eqProfile.name}" else "Tap to configure 10-band EQ",
                                    position = GroupPos.BOTTOM,
                                    onClick = { onNavigate(Route.Equalizer) },
                                )
                            }
                        }

                        item(key = "playback_switches") {
                            SettingsGroupTitle("Playback Behavior")
                            SettingsGroupColumn {
                                SettingsSwitchItem(
                                    icon = ZIcons.VolumeHigh,
                                    title = "Audio Normalization",
                                    subtitle = "Balance loudness evenly across songs",
                                    checked = settings.normalizeAudio,
                                    position = GroupPos.TOP,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(normalizeAudio = v) } }
                                    },
                                )
                                SettingsSwitchItem(
                                    icon = ZIcons.Speed,
                                    title = "Skip Silence",
                                    subtitle = "Trim silent pauses at the start and end of tracks",
                                    checked = settings.skipSilence,
                                    position = GroupPos.MIDDLE,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(skipSilence = v) } }
                                    },
                                )
                                SettingsSwitchItem(
                                    icon = ZIcons.Radio,
                                    title = "Auto Radio Queue",
                                    subtitle = "Continue with similar tracks when the queue finishes",
                                    checked = settings.autoQueue,
                                    position = GroupPos.MIDDLE,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(autoQueue = v) } }
                                    },
                                )
                                SettingsSwitchItem(
                                    icon = ZIcons.Queue,
                                    title = "Persistent Queue",
                                    subtitle = "Remember queue and playback position across restarts",
                                    checked = settings.persistentQueue,
                                    position = GroupPos.BOTTOM,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(persistentQueue = v) } }
                                    },
                                )
                            }
                        }

                        item(key = "crossfade_card") {
                            SettingsGroupTitle("Transitions")
                            GlassBox(
                                shape = RoundedCornerShape(24.dp),
                                elevated = true,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 2.dp),
                            ) {
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column {
                                            Text("Crossfade", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                                            Text(
                                                text = "Smoothly overlap ending and upcoming songs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glass.inkSecondary,
                                            )
                                        }
                                        Text(
                                            text = if (settings.crossfadeSec == 0) "Off" else "${settings.crossfadeSec}s",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = glass.accent,
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Slider(
                                        value = settings.crossfadeSec.toFloat(),
                                        onValueChange = { next ->
                                            val sec = next.roundToInt()
                                            scope.launch { hub.store.updatesettings { it.copy(crossfadeSec = sec) } }
                                        },
                                        valueRange = 0f..12f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = glass.accent,
                                            activeTrackColor = glass.accent,
                                            inactiveTrackColor = glass.glassBorder,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.CONTENT -> {
                        item(key = "catalog_group") {
                            SettingsGroupTitle("Catalog & Region")
                            val countryLabel = regionCatalog.firstOrNull { it.first == settings.contentCountry }?.second ?: settings.contentCountry
                            val langLabel = languageCatalog.firstOrNull { it.first == settings.contentLanguage }?.second ?: settings.contentLanguage
                            SettingsGroupColumn {
                                SettingsNavItem(
                                    icon = ZIcons.Globe,
                                    title = "Content Country",
                                    subtitle = "$countryLabel (${settings.contentCountry})",
                                    position = GroupPos.TOP,
                                    onClick = { showCountryDialog = true },
                                )
                                SettingsNavItem(
                                    icon = ZIcons.Language,
                                    title = "Content Language",
                                    subtitle = langLabel,
                                    position = GroupPos.BOTTOM,
                                    onClick = { showLanguageDialog = true },
                                )
                            }
                        }

                        item(key = "lyrics_privacy_group") {
                            SettingsGroupTitle("Lyrics & History")
                            SettingsGroupColumn {
                                SettingsSwitchItem(
                                    icon = ZIcons.Lyrics,
                                    title = "Romanize Lyrics",
                                    subtitle = "Show pronunciation for Japanese, Korean, Chinese & Cyrillic",
                                    checked = settings.romanizeLyrics,
                                    position = GroupPos.TOP,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(romanizeLyrics = v) } }
                                    },
                                )
                                SettingsSwitchItem(
                                    icon = ZIcons.History,
                                    title = "Pause Listening History",
                                    subtitle = "Stop recording local playback history and stats",
                                    checked = settings.pauseHistory,
                                    position = GroupPos.BOTTOM,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(pauseHistory = v) } }
                                    },
                                )
                            }
                        }

                        item(key = "scrobble_group") {
                            SettingsGroupTitle("Scrobbling")
                            SettingsGroupColumn {
                                SettingsNavItem(
                                    icon = ZIcons.Radio,
                                    title = "Last.fm",
                                    subtitle = if (session.lastFmSession.isNotBlank()) {
                                        "Connected as ${session.lastFmUser}"
                                    } else {
                                        "Connect account to scrobble plays"
                                    },
                                    position = GroupPos.SINGLE,
                                    onClick = { showLastFm = true },
                                )
                            }
                        }
                    }

                    SettingsPage.STORAGE -> {
                        item(key = "storage_meter") {
                            SettingsGroupTitle("Disk Usage")
                            val totalBytes = (streamBytes + downloadBytes).coerceAtLeast(1L)
                            val dlFrac = (downloadBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val cacheFrac = (streamBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                            GlassBox(
                                shape = RoundedCornerShape(24.dp),
                                elevated = true,
                                contentPadding = PaddingValues(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column {
                                            Text(
                                                text = formatbytes(streamBytes + downloadBytes),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = glass.ink,
                                            )
                                            Text(
                                                text = "${downloadedTracks.size} offline songs stored",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glass.inkSecondary,
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(CircleShape)
                                            .background(glass.glassFill),
                                    ) {
                                        if (downloadBytes > 0L) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(dlFrac.coerceAtLeast(0.06f))
                                                    .background(glass.accent),
                                            )
                                        }
                                        if (streamBytes > 0L) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(cacheFrac.coerceAtLeast(0.06f))
                                                    .background(glass.accentSecondary),
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        LegendDot(glass.accent, "Downloads (${formatbytes(downloadBytes)})")
                                        LegendDot(glass.accentSecondary, "Cache (${formatbytes(streamBytes)})")
                                    }
                                }
                            }
                        }

                        item(key = "cache_actions") {
                            SettingsGroupTitle("Maintenance")
                            SettingsGroupColumn {
                                SettingsNavItem(
                                    icon = ZIcons.Trash,
                                    title = "Clear Stream Cache",
                                    subtitle = "Free ${formatbytes(streamBytes)} of temporary cached audio",
                                    position = GroupPos.SINGLE,
                                    onClick = {
                                        hub.vault.clearstreamcache()
                                        streamBytes = 0L
                                        statusMsg = "Stream cache cleared"
                                    },
                                )
                            }
                        }

                        item(key = "backup_group") {
                            SettingsGroupTitle("Backup & Restore")
                            SettingsGroupColumn {
                                SettingsNavItem(
                                    icon = ZIcons.Download,
                                    title = "Create Backup",
                                    subtitle = "Export playlists, likes, history & settings (.zip)",
                                    position = GroupPos.TOP,
                                    onClick = { exportBackupLauncher.launch("zuiwan-backup.zip") },
                                )
                                SettingsNavItem(
                                    icon = ZIcons.Backup,
                                    title = "Restore Backup",
                                    subtitle = "Import a saved Zuiwan archive (.zip)",
                                    position = GroupPos.BOTTOM,
                                    onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "*/*")) },
                                )
                            }
                        }
                    }

                    SettingsPage.ABOUT -> {
                        item(key = "about_hero") {
                            GlassBox(
                                shape = RoundedCornerShape(28.dp),
                                elevated = true,
                                contentPadding = PaddingValues(vertical = 32.dp, horizontal = 24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = ZIcons.Wordmark,
                                        contentDescription = "Zuiwan",
                                        tint = glass.ink,
                                        modifier = Modifier.height(34.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(glass.accentSoft)
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = "Version 1.0.0",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = glass.accent,
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Refined YouTube Music streaming client crafted with Dark & White frosted glass, synced word lyrics, offline vault, and 10-band studio DSP.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = glass.inkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class GroupPos {
    TOP,
    MIDDLE,
    BOTTOM,
    SINGLE,
}

private fun groupshape(pos: GroupPos): RoundedCornerShape = when (pos) {
    GroupPos.TOP -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    GroupPos.MIDDLE -> RoundedCornerShape(6.dp)
    GroupPos.BOTTOM -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    GroupPos.SINGLE -> RoundedCornerShape(22.dp)
}

@Composable
private fun SettingsGroupTitle(text: String) {
    val glass = LocalGlass.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = glass.accent,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroupColumn(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: GroupPos,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    GlassBox(
        shape = groupshape(position),
        elevated = true,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.accentSoft),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.ink,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = ZIcons.ChevronRight,
                contentDescription = null,
                tint = glass.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    position: GroupPos,
    onToggle: (Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    GlassBox(
        shape = groupshape(position),
        elevated = true,
        onClick = { onToggle(!checked) },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.accentSoft),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glass.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.ink,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.inkSecondary,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = glass.onAccent,
                    checkedTrackColor = glass.accent,
                ),
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    previewBg: Color,
    previewCard: Color,
    previewInk: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    GlassBox(
        shape = RoundedCornerShape(22.dp),
        elevated = !selected,
        accented = selected,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(previewBg)
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(glass.accent),
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(56.dp)
                                .clip(CircleShape)
                                .background(previewInk.copy(alpha = 0.85f)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewCard),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) glass.accent else glass.inkSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = glass.ink,
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.inkSecondary,
                        maxLines = 1,
                    )
                }
                if (selected) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(glass.accent),
                    ) {
                        Icon(
                            imageVector = ZIcons.Check,
                            contentDescription = null,
                            tint = glass.onAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) glass.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) glass.accent else glass.ink,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = glass.inkSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = ZIcons.Check,
                contentDescription = null,
                tint = glass.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = glass.inkSecondary,
        )
    }
}

private fun qualitylabel(mode: QualityMode): String = when (mode) {
    QualityMode.HIGH -> "High"
    QualityMode.AUTO -> "Balanced"
    QualityMode.LOW -> "Data Saver"
}

private fun formatbytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return when {
        mb >= 1024.0 -> "%.1f GB".format(mb / 1024.0)
        mb >= 10.0 -> "${mb.roundToInt()} MB"
        mb > 0.05 -> "%.1f MB".format(mb)
        else -> "0 MB"
    }
}
