package zuiwan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import zuiwan.model.Track
import zuiwan.model.trackart
import zuiwan.screen.viewalbum
import zuiwan.screen.viewartist
import zuiwan.screen.viewbrowse
import zuiwan.screen.viewequalizer
import zuiwan.screen.viewexplore
import zuiwan.screen.viewhistory
import zuiwan.screen.viewhome
import zuiwan.screen.viewlibrary
import zuiwan.screen.viewlogin
import zuiwan.screen.viewplayer
import zuiwan.screen.viewplaylist
import zuiwan.screen.viewrecognize
import zuiwan.screen.viewsearch
import zuiwan.screen.viewsettings
import zuiwan.screen.viewstats
import zuiwan.sheet.pickersheet
import zuiwan.sheet.infosheet
import zuiwan.sheet.menusheet
import zuiwan.state.Hub
import zuiwan.state.LocalHub
import zuiwan.theme.LocalHaze
import zuiwan.theme.apptheme
import zuiwan.ui.backdrop
import zuiwan.ui.floatingbar
import zuiwan.ui.liveisland
import zuiwan.ui.miniplayer
import zuiwan.ui.NavStack
import zuiwan.ui.Route

class Activity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)
    private var installUpdateReq by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        incomingUrl = intent?.dataString
        installUpdateReq = intent?.getBooleanExtra("install_update", false) == true
        val hub = (application as App).hub

        setContent {
            CompositionLocalProvider(LocalHub provides hub) {
                rootview(
                    hub = hub,
                    deepLink = incomingUrl,
                    onDeepLinkConsumed = { incomingUrl = null },
                    installUpdate = installUpdateReq,
                    onInstallUpdateConsumed = { installUpdateReq = false },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as App).hub.resumepending()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUrl = intent.dataString
        if (intent.getBooleanExtra("install_update", false)) {
            installUpdateReq = true
        }
    }
}

@Composable
private fun rootview(
    hub: Hub,
    deepLink: String?,
    onDeepLinkConsumed: () -> Unit,
    installUpdate: Boolean,
    onInstallUpdateConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val settings by hub.settings.collectAsState()
    val playback by hub.pilot.state.collectAsState()
    val likedIds by hub.likedIds.collectAsState()
    val notice by hub.notice.collectAsState()

    val nav = remember { NavStack() }
    val stateHolder = rememberSaveableStateHolder()
    val rootHaze = rememberHazeState()

    var playerExpanded by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var permAsked by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hub.runupdater(force = true, manual = false)
    }

    LaunchedEffect(settings.checkUpdates, settings.nightlyUpdates) {
        if (!settings.checkUpdates) {
            hub.runupdater(force = false, manual = false)
            return@LaunchedEffect
        }
        if (!permAsked &&
            settings.updateNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permAsked = true
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hub.runupdater(force = false, manual = false)
        }
    }

    LaunchedEffect(installUpdate) {
        if (installUpdate) {
            onInstallUpdateConsumed()
            playerExpanded = false
            val target = hub.updateState.value.latest
                ?: hub.updater.checkupdate(
                    force = true,
                    includeNightly = settings.nightlyUpdates,
                ).getOrNull()?.first
            if (target != null) {
                hub.installupdate(target)
            }
        }
    }

    LaunchedEffect(deepLink) {
        val url = deepLink ?: return@LaunchedEffect
        onDeepLinkConsumed()
        when {
            url.contains("watch?v=") -> {
                val vid = url.substringAfter("watch?v=").substringBefore("&")
                if (vid.isNotBlank()) {
                    val next = hub.tube.loadnext(videoId = vid).getOrNull()
                    val first = next?.tracks?.firstOrNull()
                    if (first != null) {
                        hub.pilot.audioplay(first, next.title ?: first.title)
                        playerExpanded = true
                    }
                }
            }
            url.contains("youtu.be/") -> {
                val vid = url.substringAfter("youtu.be/").substringBefore("?")
                if (vid.isNotBlank()) {
                    val next = hub.tube.loadnext(videoId = vid).getOrNull()
                    val first = next?.tracks?.firstOrNull()
                    if (first != null) {
                        hub.pilot.audioplay(first, next.title ?: first.title)
                        playerExpanded = true
                    }
                }
            }
            url.contains("playlist?list=") -> {
                val pid = url.substringAfter("playlist?list=").substringBefore("&")
                if (pid.isNotBlank()) nav.open(Route.Playlist(pid))
            }
            url.contains("/channel/") -> {
                val cid = url.substringAfter("/channel/").substringBefore("?").substringBefore("/")
                if (cid.isNotBlank()) nav.open(Route.Artist(cid))
            }
        }
    }

    BackHandler(enabled = !playerExpanded && nav.stack.size > 1) {
        nav.pop()
    }

    apptheme(
        choice = settings.theme,
        dynamicColors = settings.dynamicColors,
        blurEnabled = settings.blurEnabled,
        artworkUrl = playback.current?.trackart(),
    ) {
        CompositionLocalProvider(LocalHaze provides rootHaze) {
            val hasMini = playback.current != null
            val contentBottom = PaddingValues(bottom = if (hasMini) 164.dp else 96.dp)

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(rootHaze),
                ) {
                    backdrop(artworkUrl = playback.current?.trackart(), intense = false)

                    val activeRoute = nav.current
                    AnimatedContent(
                        targetState = activeRoute,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        contentKey = { it.key },
                        label = "routeTransition",
                    ) { route ->
                        stateHolder.SaveableStateProvider(route.key) {
                            when (route) {
                                Route.Home -> viewhome(
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                Route.Explore -> viewexplore(
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                is Route.Search -> viewsearch(
                                    initialQuery = route.initialQuery,
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                Route.Recognize -> viewrecognize(
                                    bottomPadding = contentBottom,
                                    onBack = { nav.pop() },
                                    onOpenSearch = { q ->
                                        nav.pop()
                                        nav.open(Route.Search(q))
                                    },
                                    onTrackMenu = { menuTrack = it },
                                )
                                Route.Library -> viewlibrary(
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                is Route.Album -> viewalbum(
                                    albumId = route.id,
                                    onBack = { nav.pop() },
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                is Route.Artist -> viewartist(
                                    artistId = route.id,
                                    onBack = { nav.pop() },
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                is Route.Playlist -> viewplaylist(
                                    playlistId = route.id,
                                    local = route.local,
                                    onBack = { nav.pop() },
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                is Route.Browse -> viewbrowse(
                                    browseId = route.browseId,
                                    params = route.params,
                                    title = route.title,
                                    onBack = { nav.pop() },
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                Route.History -> viewhistory(
                                    onBack = { nav.pop() },
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                Route.Stats -> viewstats(
                                    onBack = { nav.pop() },
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
                                )
                                Route.Equalizer -> viewequalizer(
                                    onBack = { nav.pop() },
                                    bottomPadding = contentBottom,
                                )
                                Route.Settings -> viewsettings(
                                    onBack = { nav.pop() },
                                    onNavigate = nav::open,
                                    bottomPadding = contentBottom,
                                )
                                Route.Login -> viewlogin(
                                    onBack = { nav.pop() },
                                )
                            }
                        }
                    }
                }

                if (nav.current !is Route.Login) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        if (hasMini) {
                            val currentTrack = playback.current!!
                            miniplayer(
                                state = playback,
                                liked = currentTrack.id in likedIds,
                                onExpand = { playerExpanded = true },
                                onTogglePlay = { hub.pilot.toggleplay() },
                                onNext = { hub.pilot.skipnext() },
                                onPrev = { hub.pilot.skipprev() },
                                onLike = { hub.togglelike(currentTrack) },
                                connectedBelow = true,
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        floatingbar(
                            activeTab = nav.activeTab,
                            onSelect = { tab -> nav.switchtab(tab) },
                            connectedAbove = hasMini,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = playerExpanded && playback.current != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    viewplayer(
                        onCollapse = { playerExpanded = false },
                        onNavigate = nav::open,
                        onTrackMenu = { menuTrack = it },
                    )
                }

                menuTrack?.let { target ->
                    menusheet(
                        track = target,
                        onDismiss = { menuTrack = null },
                        onNavigate = { route ->
                            playerExpanded = false
                            nav.open(route)
                        },
                        onAddToPlaylist = { playlistTrack = it },
                        onShowInfo = { infoTrack = it },
                    )
                }

                playlistTrack?.let { target ->
                    pickersheet(
                        track = target,
                        onDismiss = { playlistTrack = null },
                    )
                }

                infoTrack?.let { target ->
                    infosheet(
                        track = target,
                        onDismiss = { infoTrack = null },
                    )
                }

                liveisland(
                    notice = notice,
                    onDismiss = { hub.hidenotice() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}
