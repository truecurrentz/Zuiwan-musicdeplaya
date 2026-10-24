package zuiwan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import zuiwan.model.Track
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
import zuiwan.screen.viewsearch
import zuiwan.screen.viewsettings
import zuiwan.screen.viewstats
import zuiwan.sheet.PlaylistPickerSheet
import zuiwan.sheet.TrackInfoSheet
import zuiwan.sheet.TrackMenuSheet
import zuiwan.state.Hub
import zuiwan.state.LocalHub
import zuiwan.theme.LocalHaze
import zuiwan.theme.ZuiwanTheme
import zuiwan.ui.AmbientBackdrop
import zuiwan.ui.FloatingGlassDock
import zuiwan.ui.GlassMiniplayer
import zuiwan.ui.NavStack
import zuiwan.ui.Route

class Activity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        incomingUrl = intent?.dataString
        val hub = (application as App).hub

        setContent {
            CompositionLocalProvider(LocalHub provides hub) {
                ZuiwanRoot(
                    hub = hub,
                    deepLink = incomingUrl,
                    onDeepLinkConsumed = { incomingUrl = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingUrl = intent.dataString
    }
}

@Composable
private fun ZuiwanRoot(
    hub: Hub,
    deepLink: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val settings by hub.settings.collectAsState()
    val playback by hub.pilot.state.collectAsState()
    val likedIds by hub.likedIds.collectAsState()

    val nav = remember { NavStack() }
    val stateHolder = rememberSaveableStateHolder()
    val rootHaze = rememberHazeState()

    var playerExpanded by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(deepLink) {
        val url = deepLink ?: return@LaunchedEffect
        onDeepLinkConsumed()
        when {
            url.contains("watch?v=") -> {
                val vid = url.substringAfter("watch?v=").substringBefore("&")
                if (vid.isNotBlank()) {
                    val next = hub.tube.loadnext(videoId = vid).getOrNull()
                    if (next != null && next.tracks.isNotEmpty()) {
                        hub.pilot.playtracks(next.tracks, 0, next.title ?: "Shared")
                        playerExpanded = true
                    }
                }
            }
            url.contains("youtu.be/") -> {
                val vid = url.substringAfter("youtu.be/").substringBefore("?")
                if (vid.isNotBlank()) {
                    val next = hub.tube.loadnext(videoId = vid).getOrNull()
                    if (next != null && next.tracks.isNotEmpty()) {
                        hub.pilot.playtracks(next.tracks, 0, next.title ?: "Shared")
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

    ZuiwanTheme(
        choice = settings.theme,
        dynamicColors = settings.dynamicColors,
        blurEnabled = settings.blurEnabled,
        artworkUrl = playback.current?.artwork,
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
                    AmbientBackdrop(artworkUrl = playback.current?.artwork, intense = false)

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
                                Route.Search -> viewsearch(
                                    onNavigate = nav::open,
                                    onTrackMenu = { menuTrack = it },
                                    bottomPadding = contentBottom,
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
                            GlassMiniplayer(
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
                        FloatingGlassDock(
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
                    TrackMenuSheet(
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
                    PlaylistPickerSheet(
                        track = target,
                        onDismiss = { playlistTrack = null },
                    )
                }

                infoTrack?.let { target ->
                    TrackInfoSheet(
                        track = target,
                        onDismiss = { infoTrack = null },
                    )
                }
            }
        }
    }
}
