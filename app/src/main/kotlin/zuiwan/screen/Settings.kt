package zuiwan.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import app.zuiwan.BuildConfig
import app.zuiwan.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.NoticeKind
import zuiwan.model.QualityMode
import zuiwan.model.ThemeChoice
import zuiwan.sheet.changelogsheet
import zuiwan.sheet.importsheet
import zuiwan.sheet.notebody
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.artimage
import zuiwan.ui.glass
import zuiwan.ui.pillbtn
import zuiwan.ui.topbar
import zuiwan.ui.Route
import zuiwan.ui.scrollhost
import zuiwan.ui.ZIcons
import zuiwan.ui.headerscroll
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    MAIN("Settings"),
    APPEARANCE("Appearance"),
    AUDIO("Player & Audio"),
    CONTENT("Content & Privacy"),
    STORAGE("Storage & Backup"),
    UPDATER("App Updates"),
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
    val glassTokens = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by hub.settings.collectAsState()
    val session by hub.session.collectAsState()
    val syncing by hub.sync.syncing.collectAsState()
    val eqProfile by hub.eq.collectAsState()
    val downloadedTracks by hub.downloadedTracks.collectAsState()
    val updateState by hub.updateState.collectAsState()

    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    val initialUsage = remember { hub.vault.cacheusage() }
    var streamBytes by remember { mutableLongStateOf(initialUsage.first) }
    var downloadBytes by remember { mutableLongStateOf(initialUsage.second) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showChangelogSheet by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            hub.shownotice(
                NoticeKind.INFO,
                "Notification Permission",
                "Allow notifications in system settings to receive update alerts",
            )
        }
    }

    val headerScroll = headerscroll()

    BackHandler(enabled = page != SettingsPage.MAIN) {
        page = SettingsPage.MAIN
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                hub.shownotice(NoticeKind.LOADING, "Creating Backup", "Archiving library & preferences", 0.45f)
                hub.backup.exportbackup(uri)
                    .onSuccess { hub.shownotice(NoticeKind.SUCCESS, "Backup Saved", "Archive exported successfully") }
                    .onFailure { hub.shownotice(NoticeKind.ERROR, "Backup Failed", it.message ?: "Unable to save backup") }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                hub.shownotice(NoticeKind.LOADING, "Restoring Backup", "Unpacking Zuiwan database", 0.5f)
                hub.backup.restorebackup(uri)
                    .onSuccess { hub.shownotice(NoticeKind.SUCCESS, "Backup Restored", "Restart Zuiwan to reload database") }
                    .onFailure { hub.shownotice(NoticeKind.ERROR, "Restore Failed", it.message ?: "Unable to restore archive") }
            }
        }
    }

    if (showImportSheet) {
        importsheet(
            hub = hub,
            onDismiss = { showImportSheet = false },
            onImported = { newId ->
                onNavigate(Route.Playlist(newId, local = true))
            },
        )
    }

    if (showChangelogSheet) {
        changelogsheet(
            releases = updateState.releases,
            loading = updateState.checking && updateState.releases.isEmpty(),
            onInstall = { rel ->
                showChangelogSheet = false
                hub.installupdate(rel)
            },
            onRefresh = { hub.loadchangelog(force = true) },
            onDismiss = { showChangelogSheet = false },
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = glassTokens.glassStrong,
            titleContentColor = glassTokens.ink,
            textContentColor = glassTokens.ink,
            title = { Text("Streaming Quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple(QualityMode.HIGH, "High", "Best clarity for Wi-Fi and headphones"),
                        Triple(QualityMode.AUTO, "Balanced", "Adapts smoothly to connection speed"),
                        Triple(QualityMode.LOW, "Data Saver", "Minimizes mobile data usage"),
                    ).forEach { (mode, title, desc) ->
                        choicerow(
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
                    Text("Close", color = glassTokens.inkSecondary)
                }
            },
        )
    }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            containerColor = glassTokens.glassStrong,
            titleContentColor = glassTokens.ink,
            textContentColor = glassTokens.ink,
            title = { Text("Content Country") },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(regionCatalog, key = { it.first }) { (code, label) ->
                        choicerow(
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
                    Text("Close", color = glassTokens.inkSecondary)
                }
            },
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = glassTokens.glassStrong,
            titleContentColor = glassTokens.ink,
            textContentColor = glassTokens.ink,
            title = { Text("Content Language") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(languageCatalog, key = { it.first }) { (code, label) ->
                        choicerow(
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
                    Text("Close", color = glassTokens.inkSecondary)
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

    scrollhost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            topbar(
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
                when (currentPage) {
                    SettingsPage.MAIN -> {
                        item(key = "account_hero") {
                            glass(
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
                                            artimage(
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
                                                    color = glassTokens.ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = session.accountHandle.ifBlank { session.accountEmail.ifBlank { "Signed in" } },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = glassTokens.inkSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            pillbtn(
                                                text = if (syncing) "Syncing..." else "Sync Library",
                                                icon = ZIcons.Sync,
                                                primary = true,
                                                onClick = { hub.syncaccount() },
                                                modifier = Modifier.weight(1f),
                                            )
                                            pillbtn(
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
                                                .background(glassTokens.accentSoft),
                                        ) {
                                            Icon(
                                                imageVector = ZIcons.User,
                                                contentDescription = null,
                                                tint = glassTokens.accent,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "YouTube Music",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = glassTokens.ink,
                                            )
                                            Text(
                                                text = "Sign in to sync playlists, likes & mixes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glassTokens.inkSecondary,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        pillbtn(
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
                            grouptitle("Experience")
                            groupbox {
                                val themeName = if (settings.theme == ThemeChoice.DARK) "Dark" else "White"
                                val blurDesc = if (settings.blurEnabled) "Frosted blur" else "Minimal glass"
                                navitem(
                                    icon = ZIcons.Palette,
                                    title = "Appearance",
                                    subtitle = "$themeName theme - $blurDesc",
                                    position = GroupPos.TOP,
                                    onClick = { page = SettingsPage.APPEARANCE },
                                )
                                navitem(
                                    icon = ZIcons.Waveform,
                                    title = "Player & Audio",
                                    subtitle = "${qualitylabel(settings.quality)} quality - ${if (eqProfile.enabled) eqProfile.name else "Parametric EQ"}",
                                    position = GroupPos.MIDDLE,
                                    onClick = { page = SettingsPage.AUDIO },
                                )
                                val countryLabel = regionCatalog.firstOrNull { it.first == settings.contentCountry }?.second ?: settings.contentCountry
                                navitem(
                                    icon = ZIcons.Language,
                                    title = "Content & Privacy",
                                    subtitle = "$countryLabel - Lyrics & History",
                                    position = GroupPos.BOTTOM,
                                    onClick = { page = SettingsPage.CONTENT },
                                )
                            }
                        }

                        item(key = "group_system") {
                            grouptitle("Data & System")
                            groupbox {
                                navitem(
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
                                navitem(
                                    icon = ZIcons.Equalizer,
                                    title = "Parametric Equalizer",
                                    subtitle = if (eqProfile.enabled) "Active: ${eqProfile.name}" else "10-band studio DSP",
                                    position = GroupPos.MIDDLE,
                                    onClick = { onNavigate(Route.Equalizer) },
                                )
                                val updSub = when {
                                    updateState.checking -> "Checking for updates..."
                                    else -> "Check for updates & phone notifications"
                                }
                                navitem(
                                    icon = ZIcons.Update,
                                    title = "App Updates",
                                    subtitle = updSub,
                                    position = GroupPos.MIDDLE,
                                    onClick = { page = SettingsPage.UPDATER },
                                )
                                navitem(
                                    icon = ZIcons.Notes,
                                    title = "Changelog",
                                    subtitle = "Release notes & version history",
                                    position = GroupPos.MIDDLE,
                                    onClick = {
                                        hub.loadchangelog()
                                        showChangelogSheet = true
                                    },
                                )
                                navitem(
                                    icon = ZIcons.Info,
                                    title = "About Zuiwan",
                                    subtitle = "A free music player, import, export ur songs, enjoy the free service!",
                                    position = GroupPos.BOTTOM,
                                    onClick = { page = SettingsPage.ABOUT },
                                )
                            }
                        }
                    }

                    SettingsPage.APPEARANCE -> {
                        item(key = "theme_cards") {
                            grouptitle("Theme")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                            ) {
                                themecard(
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
                                themecard(
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
                            grouptitle("Surfaces & Color")
                            groupbox {
                                switchitem(
                                    icon = ZIcons.Sparkles,
                                    title = "Frosted Glass Blur",
                                    subtitle = "Real-time backdrop blur on bars, dock, and player",
                                    checked = settings.blurEnabled,
                                    position = GroupPos.TOP,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(blurEnabled = v) } }
                                    },
                                )
                                switchitem(
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
                            grouptitle("Streaming & DSP")
                            groupbox {
                                navitem(
                                    icon = ZIcons.Waveform,
                                    title = "Streaming Quality",
                                    subtitle = qualitylabel(settings.quality),
                                    position = GroupPos.TOP,
                                    onClick = { showQualityDialog = true },
                                )
                                navitem(
                                    icon = ZIcons.Equalizer,
                                    title = "Parametric Equalizer",
                                    subtitle = if (eqProfile.enabled) "Active preset: ${eqProfile.name}" else "Tap to configure 10-band EQ",
                                    position = GroupPos.BOTTOM,
                                    onClick = { onNavigate(Route.Equalizer) },
                                )
                            }
                        }

                        item(key = "playback_switches") {
                            grouptitle("Playback Behavior")
                            groupbox {
                                switchitem(
                                    icon = ZIcons.VolumeHigh,
                                    title = "Audio Normalization",
                                    subtitle = "Balance loudness evenly across songs",
                                    checked = settings.normalizeAudio,
                                    position = GroupPos.TOP,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(normalizeAudio = v) } }
                                    },
                                )
                                switchitem(
                                    icon = ZIcons.Speed,
                                    title = "Skip Silence",
                                    subtitle = "Trim silent pauses at the start and end of tracks",
                                    checked = settings.skipSilence,
                                    position = GroupPos.MIDDLE,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(skipSilence = v) } }
                                    },
                                )
                                switchitem(
                                    icon = ZIcons.Radio,
                                    title = "Auto Radio Queue",
                                    subtitle = "Continue with similar tracks when the queue finishes",
                                    checked = settings.autoRadio,
                                    position = GroupPos.MIDDLE,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(autoRadio = v) } }
                                    },
                                )
                                switchitem(
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
                            grouptitle("Transitions")
                            glass(
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
                                            Text("Crossfade", style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                                            Text(
                                                text = "Smoothly overlap ending and upcoming songs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glassTokens.inkSecondary,
                                            )
                                        }
                                        Text(
                                            text = if (settings.crossfadeSec == 0) "Off" else "${settings.crossfadeSec}s",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = glassTokens.accent,
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
                                            thumbColor = glassTokens.accent,
                                            activeTrackColor = glassTokens.accent,
                                            inactiveTrackColor = glassTokens.glassBorder,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.CONTENT -> {
                        item(key = "catalog_group") {
                            grouptitle("Catalog & Region")
                            val countryLabel = regionCatalog.firstOrNull { it.first == settings.contentCountry }?.second ?: settings.contentCountry
                            val langLabel = languageCatalog.firstOrNull { it.first == settings.contentLanguage }?.second ?: settings.contentLanguage
                            groupbox {
                                navitem(
                                    icon = ZIcons.Globe,
                                    title = "Content Country",
                                    subtitle = "$countryLabel (${settings.contentCountry})",
                                    position = GroupPos.TOP,
                                    onClick = { showCountryDialog = true },
                                )
                                navitem(
                                    icon = ZIcons.Language,
                                    title = "Content Language",
                                    subtitle = langLabel,
                                    position = GroupPos.BOTTOM,
                                    onClick = { showLanguageDialog = true },
                                )
                            }
                        }

                        item(key = "lyrics_privacy_group") {
                            grouptitle("Lyrics & History")
                            groupbox {
                                switchitem(
                                    icon = ZIcons.Lyrics,
                                    title = "Romanize Lyrics",
                                    subtitle = "Show pronunciation for Japanese, Korean, Chinese & Cyrillic",
                                    checked = settings.romanizeLyrics,
                                    position = GroupPos.TOP,
                                    onToggle = { v ->
                                        scope.launch { hub.store.updatesettings { it.copy(romanizeLyrics = v) } }
                                    },
                                )
                                switchitem(
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
                    }

                    SettingsPage.STORAGE -> {
                        item(key = "storage_meter") {
                            grouptitle("Disk Usage")
                            val totalBytes = (streamBytes + downloadBytes).coerceAtLeast(1L)
                            val dlFrac = (downloadBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val cacheFrac = (streamBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                            glass(
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
                                                color = glassTokens.ink,
                                            )
                                            Text(
                                                text = "${downloadedTracks.size} offline songs stored",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glassTokens.inkSecondary,
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(CircleShape)
                                            .background(glassTokens.glassFill),
                                    ) {
                                        if (downloadBytes > 0L) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(dlFrac.coerceAtLeast(0.06f))
                                                    .background(glassTokens.accent),
                                            )
                                        }
                                        if (streamBytes > 0L) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(cacheFrac.coerceAtLeast(0.06f))
                                                    .background(glassTokens.accentSecondary),
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        legend(glassTokens.accent, "Downloads (${formatbytes(downloadBytes)})")
                                        legend(glassTokens.accentSecondary, "Cache (${formatbytes(streamBytes)})")
                                    }
                                }
                            }
                        }

                        item(key = "cache_actions") {
                            grouptitle("Maintenance")
                            groupbox {
                                navitem(
                                    icon = ZIcons.Trash,
                                    title = "Clear Stream Cache",
                                    subtitle = "Free ${formatbytes(streamBytes)} of temporary cached audio",
                                    position = GroupPos.SINGLE,
                                    onClick = {
                                        hub.vault.clearcache()
                                        streamBytes = 0L
                                        hub.shownotice(NoticeKind.SUCCESS, "Stream Cache Cleared", "Temporary audio cache freed")
                                    },
                                )
                            }
                        }

                        item(key = "backup_group") {
                            grouptitle("Playlists & Backup")
                            groupbox {
                                navitem(
                                    icon = ZIcons.Import,
                                    title = "Import Playlist",
                                    subtitle = "Import CSV, M3U, JSON, TXT, or YouTube & Spotify links",
                                    position = GroupPos.TOP,
                                    onClick = { showImportSheet = true },
                                )
                                navitem(
                                    icon = ZIcons.Download,
                                    title = "Create Backup",
                                    subtitle = "Export playlists, likes, history & settings (.zip)",
                                    position = GroupPos.MIDDLE,
                                    onClick = { exportBackupLauncher.launch("zuiwan-backup.zip") },
                                )
                                navitem(
                                    icon = ZIcons.Backup,
                                    title = "Restore Backup",
                                    subtitle = "Import a saved Zuiwan archive (.zip)",
                                    position = GroupPos.BOTTOM,
                                    onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "*/*")) },
                                )
                            }
                        }
                    }

                    SettingsPage.UPDATER -> {
                        item(key = "updater_current") {
                            grouptitle("Current Version")
                            groupbox {
                                glass(
                                    shape = groupshape(GroupPos.SINGLE),
                                    elevated = true,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
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
                                                .background(glassTokens.accentSoft),
                                        ) {
                                            Icon(
                                                imageVector = ZIcons.Info,
                                                contentDescription = null,
                                                tint = glassTokens.accent,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Version ${BuildConfig.VERSION_NAME}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = glassTokens.ink,
                                            )
                                            val buildLabel = if (BuildConfig.DEBUG) "Debug" else "Release"
                                            Text(
                                                text = "$buildLabel · Build ${BuildConfig.GIT_SHA}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = glassTokens.inkSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "updater_prefs") {
                            grouptitle("Update Settings")
                            groupbox {
                                switchitem(
                                    icon = ZIcons.Update,
                                    title = "Check for updates",
                                    subtitle = "Automatically check GitHub when Zuiwan opens",
                                    checked = settings.checkUpdates,
                                    position = if (settings.checkUpdates) GroupPos.TOP else GroupPos.SINGLE,
                                    onToggle = { enabled ->
                                        scope.launch {
                                            hub.store.updatesettings { it.copy(checkUpdates = enabled) }
                                            if (enabled) {
                                                hub.runupdater(force = true, manual = false)
                                            } else {
                                                hub.updater.clearalert()
                                            }
                                        }
                                    },
                                )
                                if (settings.checkUpdates) {
                                    switchitem(
                                        icon = ZIcons.Bell,
                                        title = "Update notifications",
                                        subtitle = "Notify when a new Zuiwan update arrives",
                                        checked = settings.updateNotifications,
                                        position = GroupPos.MIDDLE,
                                        onToggle = { enabled ->
                                            if (enabled &&
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            scope.launch {
                                                hub.store.updatesettings { it.copy(updateNotifications = enabled) }
                                                if (!enabled) hub.updater.clearalert()
                                            }
                                        },
                                    )
                                    switchitem(
                                        icon = ZIcons.Sparkles,
                                        title = "Include nightly builds",
                                        subtitle = "Also check pre-releases & newer CI builds",
                                        checked = settings.nightlyUpdates,
                                        position = GroupPos.BOTTOM,
                                        onToggle = { enabled ->
                                            scope.launch {
                                                hub.store.updatesettings { it.copy(nightlyUpdates = enabled) }
                                                hub.runupdater(force = true, manual = false)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        item(key = "updater_actions") {
                            grouptitle("Check & History")
                            groupbox {
                                val checkTitle = when {
                                    updateState.checking -> "Checking for updates..."
                                    updateState.hasUpdate && updateState.latest != null ->
                                        "Latest version: v${updateState.latest!!.versionName}"
                                    updateState.lastCheckedAt > 0L ->
                                        "Up to date · v${BuildConfig.VERSION_NAME}"
                                    else -> "Check for updates"
                                }
                                val checkSub = when {
                                    updateState.error != null -> updateState.error!!
                                    updateState.hasUpdate -> "Tap to install update"
                                    else -> "Tap to check GitHub releases now"
                                }
                                glass(
                                    shape = groupshape(GroupPos.TOP),
                                    elevated = true,
                                    onClick = {
                                        if (!updateState.checking) {
                                            if (updateState.hasUpdate && updateState.latest != null) {
                                                hub.installupdate(updateState.latest)
                                            } else {
                                                hub.runupdater(force = true, manual = true)
                                            }
                                        }
                                    },
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
                                                .background(glassTokens.accentSoft),
                                        ) {
                                            if (updateState.checking) {
                                                CircularProgressIndicator(
                                                    color = glassTokens.accent,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = ZIcons.Refresh,
                                                    contentDescription = null,
                                                    tint = glassTokens.accent,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = checkTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = glassTokens.ink,
                                            )
                                            Text(
                                                text = checkSub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (updateState.error != null) {
                                                    glassTokens.danger
                                                } else {
                                                    glassTokens.inkSecondary
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (updateState.hasUpdate) {
                                            Icon(
                                                imageVector = ZIcons.Download,
                                                contentDescription = null,
                                                tint = glassTokens.accent,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }

                                navitem(
                                    icon = ZIcons.Notes,
                                    title = "Changelog",
                                    subtitle = "View release notes on Zuiwan & GitHub",
                                    position = GroupPos.BOTTOM,
                                    onClick = {
                                        hub.loadchangelog()
                                        showChangelogSheet = true
                                    },
                                )
                            }
                        }

                        val found = updateState.latest?.takeIf { updateState.hasUpdate }
                        if (found != null) {
                            item(key = "updater_notes_card") {
                                grouptitle("Release Notes")
                                glass(
                                    shape = RoundedCornerShape(24.dp),
                                    elevated = true,
                                    contentPadding = PaddingValues(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 4.dp),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = found.title.ifBlank { "Zuiwan v${found.versionName}" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = glassTokens.ink,
                                        )
                                        notebody(
                                            raw = found.body.ifBlank {
                                                "New improvements and fixes ready to install."
                                            },
                                        )
                                        val btnText = when {
                                            updateState.downloading ->
                                                "Downloading ${(updateState.downloadProgress * 100f).toInt()}%..."
                                            updateState.readyTag == found.tagName &&
                                                !updateState.readyApkPath.isNullOrBlank() ->
                                                "Install v${found.versionName}"
                                            else -> "Update to v${found.versionName}"
                                        }
                                        pillbtn(
                                            text = btnText,
                                            icon = ZIcons.Download,
                                            primary = true,
                                            onClick = { hub.installupdate(found) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.ABOUT -> {
                        item(key = "about_hero") {
                            glass(
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
                                    Image(
                                        painter = painterResource(R.drawable.wordmark),
                                        contentDescription = "Zuiwan",
                                        contentScale = ContentScale.Fit,
                                        colorFilter = ColorFilter.tint(glassTokens.ink),
                                        modifier = Modifier.height(32.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(glassTokens.accentSoft)
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = "Version 1.0.0",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = glassTokens.accent,
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "A free music player, import, export ur songs, enjoy the free service!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = glassTokens.inkSecondary,
                                        textAlign = TextAlign.Center,
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
private fun grouptitle(text: String) {
    val glassTokens = LocalGlass.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = glassTokens.accent,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun groupbox(content: @Composable () -> Unit) {
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
private fun navitem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: GroupPos,
    showBadge: Boolean = false,
    onClick: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    glass(
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
                    .background(glassTokens.accentSoft),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassTokens.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glassTokens.ink,
                    )
                    if (showBadge) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(glassTokens.accent),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = ZIcons.ChevronRight,
                contentDescription = null,
                tint = glassTokens.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun switchitem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    position: GroupPos,
    onToggle: (Boolean) -> Unit,
) {
    val glassTokens = LocalGlass.current
    glass(
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
                    .background(glassTokens.accentSoft),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassTokens.accent,
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
                    color = glassTokens.ink,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassTokens.inkSecondary,
                )
            }
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
}

@Composable
private fun themecard(
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
    val glassTokens = LocalGlass.current
    glass(
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
                                .background(glassTokens.accent),
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
                            tint = if (selected) glassTokens.accent else glassTokens.inkSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = glassTokens.ink,
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = glassTokens.inkSecondary,
                        maxLines = 1,
                    )
                }
                if (selected) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(glassTokens.accent),
                    ) {
                        Icon(
                            imageVector = ZIcons.Check,
                            contentDescription = null,
                            tint = glassTokens.onAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun choicerow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glassTokens = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) glassTokens.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) glassTokens.accent else glassTokens.ink,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = glassTokens.inkSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = ZIcons.Check,
                contentDescription = null,
                tint = glassTokens.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun legend(color: Color, label: String) {
    val glassTokens = LocalGlass.current
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
            color = glassTokens.inkSecondary,
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
