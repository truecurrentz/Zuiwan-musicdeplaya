package zuiwan.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zuiwan.model.FeedItem
import zuiwan.model.SearchPage
import zuiwan.model.SearchTab
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.albumcard
import zuiwan.ui.artimage
import zuiwan.ui.brandbar
import zuiwan.ui.glass
import zuiwan.ui.chip
import zuiwan.ui.iconbtn
import zuiwan.ui.mediacard
import zuiwan.ui.picktile
import zuiwan.ui.Route
import zuiwan.ui.sectionbar
import zuiwan.ui.songrow
import zuiwan.ui.ZIcons

private enum class DiscoverPane(val label: String, val icon: ImageVector) {
    EXPLORE("Explore", ZIcons.Explore),
    SUGGESTIONS("Suggestions", ZIcons.Sparkles),
}

@Composable
fun viewsearch(
    initialQuery: String = "",
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val recentSearches by hub.searchHistory.collectAsState()
    val explore by hub.exploreFeed.collectAsState()
    val exploreLoading by hub.exploreLoading.collectAsState()
    val homeFeed by hub.homeFeed.collectAsState()
    val history by hub.historyTracks.collectAsState()
    val playback by hub.pilot.state.collectAsState()
    val updateState by hub.updateState.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(SearchTab.ALL) }
    var pane by rememberSaveable { mutableStateOf(DiscoverPane.SUGGESTIONS) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestedItems by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var page by remember { mutableStateOf(SearchPage()) }
    var loading by remember { mutableStateOf(false) }

    val uniqueSongs = remember(history, homeFeed, explore) {
        val fromHome = homeFeed.shelves
            .flatMap { it.items }
            .mapNotNull { (it as? FeedItem.SongCard)?.track }
        val fromCharts = explore.charts
            .flatMap { it.items }
            .mapNotNull { (it as? FeedItem.SongCard)?.track }
        (history + fromHome + fromCharts)
            .distinctBy { it.id }
            .take(12)
    }

    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }

    fun runsearch(q: String, targetTab: SearchTab = tab) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        submitted = clean
        tab = targetTab
        hub.savesearch(clean)
        focusManager.clearFocus()
        scope.launch {
            loading = true
            page = hub.tube.searchmusic(clean, targetTab).getOrDefault(SearchPage())
            loading = false
        }
    }

    LaunchedEffect(initialQuery) {
        val clean = initialQuery.trim()
        if (clean.isNotEmpty() && submitted != clean) {
            query = clean
            runsearch(clean)
        }
    }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.isEmpty() || clean == submitted) {
            if (clean.isEmpty()) {
                suggestions = emptyList()
                suggestedItems = emptyList()
            }
            return@LaunchedEffect
        }
        delay(220L)
        hub.tube.suggestquery(clean).onSuccess { bundle ->
            suggestions = bundle.queries
            suggestedItems = bundle.items
        }
    }

    LaunchedEffect(nearEnd, page.continuation) {
        val cont = page.continuation
        if (nearEnd && !cont.isNullOrBlank() && !loading && submitted.isNotBlank()) {
            loading = true
            hub.tube.searchmusic(submitted, tab, continuation = cont).onSuccess { more ->
                page = page.copy(
                    items = (page.items + more.items).distinctBy { it.key },
                    continuation = more.continuation,
                )
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(glassTokens.bg),
        ) {
            brandbar {
                iconbtn(
                    icon = ZIcons.Mic,
                    onClick = { onNavigate(Route.Recognize) },
                )
                iconbtn(
                    icon = ZIcons.Settings,
                    showBadge = updateState.hasUpdate,
                    onClick = { onNavigate(Route.Settings) },
                )
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                glass(
                    shape = RoundedCornerShape(999.dp),
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = ZIcons.Search,
                            contentDescription = null,
                            tint = glassTokens.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search YouTube Music",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = glassTokens.inkSecondary,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    if (it.isBlank()) submitted = ""
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassTokens.ink),
                                cursorBrush = SolidColor(glassTokens.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { runsearch(query) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (query.isNotEmpty()) {
                            iconbtn(
                                icon = ZIcons.Close,
                                onClick = {
                                    query = ""
                                    submitted = ""
                                    page = SearchPage()
                                },
                                size = 28.dp,
                                iconSize = 15.dp,
                            )
                        } else {
                            iconbtn(
                                icon = ZIcons.Mic,
                                onClick = { onNavigate(Route.Recognize) },
                                size = 30.dp,
                                iconSize = 18.dp,
                            )
                            iconbtn(
                                icon = ZIcons.Globe,
                                onClick = { pane = DiscoverPane.EXPLORE },
                                size = 30.dp,
                                iconSize = 18.dp,
                            )
                        }
                    }
                }
            }

            if (submitted.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SearchTab.entries.forEach { t ->
                        chip(
                            label = t.label,
                            selected = tab == t,
                            onClick = { runsearch(submitted, t) },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    DiscoverPane.entries.forEach { item ->
                        val active = pane == item
                        val tint = if (active) glassTokens.accent else glassTokens.inkSecondary
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { pane = item }
                                .padding(top = 8.dp),
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = tint,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(68.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (active) glassTokens.accent else Color.Transparent),
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = 6.dp,
                bottom = bottomPadding.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (submitted.isBlank()) {
                if (query.isNotBlank() && (suggestions.isNotEmpty() || suggestedItems.isNotEmpty())) {
                    items(suggestions) { sug ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = sug
                                    runsearch(sug)
                                }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Icon(ZIcons.Search, null, tint = glassTokens.inkSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(sug, style = MaterialTheme.typography.titleMedium, color = glassTokens.ink)
                        }
                    }
                    items(suggestedItems, key = { it.key }) { item ->
                        feedrow(item, playback.current?.id, onNavigate, onTrackMenu) { song ->
                            hub.pilot.audioplay(song, "Search")
                        }
                    }
                } else if (pane == DiscoverPane.SUGGESTIONS) {
                    if (recentSearches.isNotEmpty()) {
                        item(key = "recent_head") {
                            Spacer(Modifier.height(6.dp))
                            sectionbar(
                                title = "Recent searches",
                                actionLabel = "Clear",
                                onAction = { hub.clearsearches() },
                            )
                        }
                        item(key = "recent_chips") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 18.dp, vertical = 4.dp),
                            ) {
                                recentSearches.take(10).forEach { term ->
                                    chip(
                                        label = term,
                                        selected = false,
                                        leadingIcon = ZIcons.History,
                                        onClick = {
                                            query = term
                                            runsearch(term)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (uniqueSongs.isNotEmpty()) {
                        item(key = "unique_head") {
                            Spacer(Modifier.height(10.dp))
                            sectionbar(
                                title = "Unique Songs",
                                actionLabel = "Play all",
                                onAction = {
                                    hub.pilot.playtracks(uniqueSongs, 0, "Unique Songs")
                                },
                            )
                        }
                        itemsIndexed(uniqueSongs, key = { _, s -> s.id }) { _, song ->
                            songrow(
                                track = song,
                                playing = playback.current?.id == song.id,
                                onClick = {
                                    hub.pilot.audioplay(song, "Unique Songs")
                                },
                                onMore = { onTrackMenu(song) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                } else {
                    if (exploreLoading && explore.releases.isEmpty() && explore.moods.isEmpty()) {
                        item(key = "exp_loader") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                            ) {
                                CircularProgressIndicator(color = glassTokens.accent)
                            }
                        }
                    }

                    if (explore.releases.isNotEmpty()) {
                        item(key = "exp_releases_head") {
                            Spacer(Modifier.height(10.dp))
                            sectionbar(title = "New Releases")
                        }
                        item(key = "exp_releases_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(explore.releases, key = { it.id }) { album ->
                                    albumcard(album = album) {
                                        onNavigate(Route.Album(album.id))
                                    }
                                }
                            }
                        }
                    }

                    explore.charts.forEachIndexed { cIdx, chart ->
                        item(key = "exp_chart_head_$cIdx") {
                            Spacer(Modifier.height(10.dp))
                            sectionbar(title = chart.title)
                        }
                        item(key = "exp_chart_row_$cIdx") {
                            val songs = chart.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                            if (songs.isNotEmpty()) {
                                val cols = songs.take(16).chunked(4)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(cols) { group ->
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            group.forEach { song ->
                                                picktile(
                                                    track = song,
                                                    playing = playback.current?.id == song.id,
                                                    onClick = {
                                                        hub.pilot.audioplay(song, chart.title)
                                                    },
                                                    onMore = { onTrackMenu(song) },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(chart.items, key = { it.key }) { entry ->
                                        mediacard(
                                            item = entry,
                                            onClick = {
                                                when (entry) {
                                                    is FeedItem.SongCard -> hub.pilot.audioplay(entry.track, chart.title)
                                                    is FeedItem.AlbumCard -> onNavigate(Route.Album(entry.album.id))
                                                    is FeedItem.ArtistCard -> onNavigate(Route.Artist(entry.artist.id))
                                                    is FeedItem.PlaylistCard -> onNavigate(Route.Playlist(entry.playlist.id))
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (explore.moods.isNotEmpty()) {
                        item(key = "exp_moods_head") {
                            Spacer(Modifier.height(12.dp))
                            sectionbar(title = "Moods & Genres")
                        }
                        val pairs = explore.moods.chunked(2)
                        items(pairs) { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 5.dp),
                            ) {
                                rowItems.forEach { tile ->
                                    val stripe = Color(tile.stripeColor or 0xFF000000L)
                                    glass(
                                        shape = RoundedCornerShape(16.dp),
                                        elevated = true,
                                        onClick = {
                                            onNavigate(Route.Browse(tile.browseId, tile.params, tile.title))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(5.dp)
                                                    .height(54.dp)
                                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                                    .background(stripe),
                                            )
                                            Text(
                                                text = tile.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = glassTokens.ink,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                if (tab == SearchTab.ALL) {
                    page.shelves.forEachIndexed { idx, shelf ->
                        item(key = "s_head_$idx") {
                            Spacer(Modifier.height(8.dp))
                            sectionbar(title = shelf.title)
                        }
                        val allSongs = shelf.items.all { it is FeedItem.SongCard }
                        if (allSongs) {
                            val songs = shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                            items(songs, key = { "${idx}_${it.id}" }) { song ->
                                songrow(
                                    track = song,
                                    playing = playback.current?.id == song.id,
                                    onClick = { hub.pilot.audioplay(song, submitted) },
                                    onMore = { onTrackMenu(song) },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        } else {
                            item(key = "s_cards_$idx") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(shelf.items, key = { it.key }) { item ->
                                        mediacard(
                                            item = item,
                                            onClick = {
                                                when (item) {
                                                    is FeedItem.SongCard -> hub.pilot.audioplay(item.track, submitted)
                                                    is FeedItem.AlbumCard -> onNavigate(Route.Album(item.album.id))
                                                    is FeedItem.ArtistCard -> onNavigate(Route.Artist(item.artist.id))
                                                    is FeedItem.PlaylistCard -> onNavigate(Route.Playlist(item.playlist.id))
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(page.items, key = { it.key }) { item ->
                        feedrow(item, playback.current?.id, onNavigate, onTrackMenu) { song ->
                            hub.pilot.audioplay(song, submitted)
                        }
                    }
                }

                if (loading) {
                    item(key = "search_loader") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        ) {
                            CircularProgressIndicator(color = glassTokens.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun feedrow(
    item: FeedItem,
    playingId: String?,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onPlaySong: (Track) -> Unit,
) {
    val glassTokens = LocalGlass.current
    when (item) {
        is FeedItem.SongCard -> songrow(
            track = item.track,
            playing = playingId == item.track.id,
            onClick = { onPlaySong(item.track) },
            onMore = { onTrackMenu(item.track) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        is FeedItem.AlbumCard -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.Album(item.album.id)) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            artimage(item.album.artwork, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.album.title, style = MaterialTheme.typography.titleMedium, color = glassTokens.ink, maxLines = 1)
                Text(
                    listOfNotNull(
                        item.album.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                        item.album.year?.toString(),
                    ).joinToString(" - ").ifBlank { "Album" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                )
            }
            Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
        }

        is FeedItem.ArtistCard -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.Artist(item.artist.id)) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            artimage(item.artist.artwork, shape = CircleShape, fallbackIcon = ZIcons.Artist, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.artist.name, style = MaterialTheme.typography.titleMedium, color = glassTokens.ink, maxLines = 1)
                Text(item.artist.subscribers ?: "Artist", style = MaterialTheme.typography.bodyMedium, color = glassTokens.inkSecondary, maxLines = 1)
            }
            Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
        }

        is FeedItem.PlaylistCard -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.Playlist(item.playlist.id)) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            artimage(item.playlist.artwork, shape = RoundedCornerShape(12.dp), fallbackIcon = ZIcons.Playlist, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.playlist.title, style = MaterialTheme.typography.titleMedium, color = glassTokens.ink, maxLines = 1)
                Text(
                    listOfNotNull(item.playlist.author, item.playlist.countText).joinToString(" - ").ifBlank { "Playlist" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassTokens.inkSecondary,
                    maxLines = 1,
                )
            }
            Icon(ZIcons.ChevronRight, null, tint = glassTokens.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}
