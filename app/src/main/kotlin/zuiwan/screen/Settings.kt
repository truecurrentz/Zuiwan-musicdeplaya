package zuiwan.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zuiwan.model.QualityMode
import zuiwan.model.ThemeChoice
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassPillButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.ZIcons
import kotlin.math.roundToInt

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

    val initialUsage = remember { hub.vault.cacheusage() }
    var streamBytes by remember { mutableLongStateOf(initialUsage.first) }
    var downloadBytes by remember { mutableLongStateOf(initialUsage.second) }

    var showLastFm by remember { mutableStateOf(false) }
    var lfmUser by remember { mutableStateOf(session.lastFmUser) }
    var lfmPass by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                hub.backup.exportbackup(uri)
                    .onSuccess { statusMsg = "Backup saved" }
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
                    .onSuccess { statusMsg = "Backup restored - restart app to reload DB" }
                    .onFailure { statusMsg = it.message ?: "Restore failed" }
            }
        }
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
                TextButton(onClick = { showLastFm = false }) {
                    Text("Cancel", color = glass.inkSecondary)
                }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "top") {
            GlassTopBar(title = "Settings", onBack = onBack)
        }

        statusMsg?.let { msg ->
            item(key = "status") {
                GlassBox(
                    shape = RoundedCornerShape(16.dp),
                    accented = true,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = glass.ink)
                }
            }
        }

        item(key = "theme_head") {
            SectionHeader(title = "Appearance", subtitle = "Dark & White Glass")
        }
        item(key = "theme_card") {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                elevated = true,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GlassPillButton(
                            text = "Dark",
                            icon = ZIcons.Moon,
                            primary = settings.theme == ThemeChoice.DARK,
                            onClick = {
                                scope.launch { hub.store.updatesettings { it.copy(theme = ThemeChoice.DARK) } }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GlassPillButton(
                            text = "White",
                            icon = ZIcons.Sun,
                            primary = settings.theme == ThemeChoice.WHITE,
                            onClick = {
                                scope.launch { hub.store.updatesettings { it.copy(theme = ThemeChoice.WHITE) } }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    SettingToggleRow(
                        title = "Frosted Backdrop Blur",
                        subtitle = "Real-time glass blur on floating bars and immersive player",
                        checked = settings.blurEnabled,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(blurEnabled = v) } }
                    }

                    SettingToggleRow(
                        title = "Dynamic Artwork Accent",
                        subtitle = "Tint glass highlights from the playing cover art",
                        checked = settings.dynamicColors,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(dynamicColors = v) } }
                    }
                }
            }
        }

        item(key = "account_head") {
            Spacer(Modifier.height(10.dp))
            SectionHeader(title = "YouTube Music Account", subtitle = "Cloud Sync")
        }
        item(key = "account_card") {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                if (session.active) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtImage(
                                url = session.accountAvatar,
                                shape = CircleShape,
                                fallbackIcon = ZIcons.User,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.accountName.ifBlank { "Signed In" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = glass.ink,
                                )
                                Text(
                                    text = session.accountHandle.ifBlank { session.accountEmail },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.inkSecondary,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassPillButton(
                                text = "Sync Liked",
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Guest Session", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                            Text(
                                "Sign in to sync your liked tracks and personalized mixes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.inkSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
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

        item(key = "audio_head") {
            Spacer(Modifier.height(10.dp))
            SectionHeader(title = "Audio & Playback", subtitle = "Engine & DSP")
        }
        item(key = "audio_card") {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Streaming Quality", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf(
                            QualityMode.HIGH to "High (Opus 160k)",
                            QualityMode.AUTO to "Auto",
                            QualityMode.LOW to "Data Saver",
                        ).forEach { (mode, label) ->
                            GlassChip(
                                label = label,
                                selected = settings.quality == mode,
                                onClick = {
                                    scope.launch { hub.store.updatesettings { it.copy(quality = mode) } }
                                },
                            )
                        }
                    }

                    SettingToggleRow(
                        title = "Audio Normalization",
                        subtitle = "Balance loudness across tracks (-14 LUFS)",
                        checked = settings.normalizeAudio,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(normalizeAudio = v) } }
                    }

                    SettingToggleRow(
                        title = "Skip Silence",
                        subtitle = "Trim dead air at start and end of tracks",
                        checked = settings.skipSilence,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(skipSilence = v) } }
                    }

                    SettingToggleRow(
                        title = "Auto Radio Queue",
                        subtitle = "Keep playing similar tracks when queue ends",
                        checked = settings.autoQueue,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(autoQueue = v) } }
                    }

                    SettingToggleRow(
                        title = "Persistent Queue",
                        subtitle = "Restore queue and position on app launch",
                        checked = settings.persistentQueue,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(persistentQueue = v) } }
                    }

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Crossfade Ramp", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                            Text(
                                text = if (settings.crossfadeSec == 0) "Off" else "${settings.crossfadeSec}s",
                                style = MaterialTheme.typography.labelLarge,
                                color = glass.accent,
                            )
                        }
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

                    GlassPillButton(
                        text = "Open Parametric Equalizer",
                        icon = ZIcons.Equalizer,
                        onClick = { onNavigate(Route.Equalizer) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "lyrics_head") {
            Spacer(Modifier.height(10.dp))
            SectionHeader(title = "Lyrics, Region & Scrobbling")
        }
        item(key = "lyrics_card") {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        title = "Romanize Non-Latin Lyrics",
                        subtitle = "Show pronunciation for Japanese, Korean, Chinese & Cyrillic",
                        checked = settings.romanizeLyrics,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(romanizeLyrics = v) } }
                    }

                    SettingToggleRow(
                        title = "Pause Listening History",
                        subtitle = "Stop recording plays and stats locally",
                        checked = settings.pauseHistory,
                    ) { v ->
                        scope.launch { hub.store.updatesettings { it.copy(pauseHistory = v) } }
                    }

                    Text("Content Region", style = MaterialTheme.typography.titleMedium, color = glass.ink)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf("US", "PH", "GB", "JP", "KR", "DE", "BR", "IN", "CA", "AU").forEach { code ->
                            GlassChip(
                                label = code,
                                selected = settings.contentCountry == code,
                                onClick = {
                                    scope.launch {
                                        hub.store.updatesettings { it.copy(contentCountry = code) }
                                        hub.refreshhome()
                                        hub.refreshexplore()
                                    }
                                },
                            )
                        }
                    }

                    GlassPillButton(
                        text = if (session.lastFmSession.isNotBlank()) "Last.fm: ${session.lastFmUser}" else "Connect Last.fm",
                        icon = ZIcons.Radio,
                        onClick = { showLastFm = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "storage_head") {
            Spacer(Modifier.height(10.dp))
            SectionHeader(title = "Storage & Backup")
        }
        item(key = "storage_card") {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Stream Cache", style = MaterialTheme.typography.bodyLarge, color = glass.inkSecondary)
                        Text("${streamBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.labelLarge, color = glass.ink)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Offline Downloads", style = MaterialTheme.typography.bodyLarge, color = glass.inkSecondary)
                        Text("${downloadBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.labelLarge, color = glass.ink)
                    }

                    GlassPillButton(
                        text = "Clear Stream Cache",
                        icon = ZIcons.Trash,
                        onClick = {
                            hub.vault.clearstreamcache()
                            streamBytes = 0L
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassPillButton(
                            text = "Backup",
                            icon = ZIcons.Download,
                            primary = true,
                            onClick = { exportBackupLauncher.launch("zuiwan-backup.zip") },
                            modifier = Modifier.weight(1f),
                        )
                        GlassPillButton(
                            text = "Restore",
                            icon = ZIcons.Refresh,
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "*/*")) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = glass.ink)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary)
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
