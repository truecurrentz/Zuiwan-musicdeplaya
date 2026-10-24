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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zuiwan.model.FeedItem
import zuiwan.model.SearchTab
import zuiwan.model.SuggestBundle
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.AlbumGridCard
import zuiwan.ui.ArtImage
import zuiwan.ui.BrandBar
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.GlassIconButton
import zuiwan.ui.MediaCard
import zuiwan.ui.QuickPickTile
import zuiwan.ui.Route
import zuiwan.ui.ScrollHeaderHost
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons
import zuiwan.ui.rememberheaderscroll

@Composable
fun viewsearch(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()

    val recentQueries by hub.recentQueries.collectAsState()
    val playback by hub.pilot.state.collectAsState()
    val explore by hub.exploreFeed.collectAsState()
    val exploreLoading by hub.exploreLoading.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable { mutableStateOf(SearchTab.ALL) }
    var suggestions by remember { mutableStateOf(SuggestBundle(emptyList(), emptyList())) }
    var results by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var continuation by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    val headerScroll = rememberheaderscroll()
    val listState = rememberLazyListState()

    val lifted by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12
        }
    }

    LaunchedEffect(query, submitted) {
        val clean = query.trim()
        if (clean.isEmpty() || clean == submitted) {
            suggestions = SuggestBundle(emptyList(), emptyList())
            return@LaunchedEffect
        }
        delay(220)
        hub.tube.suggest(clean).onSuccess { suggestions = it }
    }

    fun executeSearch(term: String, tab: SearchTab = activeTab) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        query = clean
        submitted = clean
        activeTab = tab
        hub.savesearch(clean)
        scope.launch {
            searching = true
            hub.tube.search(clean, tab)
                .onSuccess { page ->
                    results = page.items
                    continuation = page.continuation
                }
                .onFailure {
                    results = emptyList()
                    continuation = null
                }
            searching = false
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }

    LaunchedEffect(nearEnd, continuation) {
        val token = continuation
        if (nearEnd && !token.isNullOrBlank() && !searching) {
            searching = true
            hub.tube.searchmore(token, activeTab).onSuccess { more ->
                results = results + more.items
                continuation = more.continuation
            }
            searching = false
        }
    }

    ScrollHeaderHost(
        scroll = headerScroll,
        lifted = lifted,
        header = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrandBar(
                    actions = {
                        GlassIconButton(
                            icon = ZIcons.Settings,
                            onClick = { onNavigate(Route.Settings) },
                        )
                    },
                )

                GlassBox(
                    shape = RoundedCornerShape(999.dp),
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = ZIcons.Search,
                            contentDescription = null,
                            tint = glass.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Artists, songs, albums, playlists",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = glass.inkSecondary,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    if (it.isEmpty()) {
                                        submitted = ""
                                        results = emptyList()
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = glass.ink),
                                cursorBrush = SolidColor(glass.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { executeSearch(query) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (query.isNotEmpty()) {
                            GlassIconButton(
                                icon = ZIcons.Close,
                                onClick = {
                                    query = ""
                                    submitted = ""
                                    results = emptyList()
                                },
                                size = 30.dp,
                                iconSize = 16.dp,
                            )
                        }
                    }
                }

                if (submitted.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    ) {
                        SearchTab.entries.forEach { tab ->
                            GlassChip(
                                label = tab.label,
                                selected = activeTab == tab,
                                onClick = { executeSearch(submitted, tab) },
                            )
                        }
                    }
                }
            }
        },
    ) { topPad ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPad + 6.dp,
                bottom = bottomPadding.calculateBottomPadding() + 28.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                query.isNotBlank() && query.trim() != submitted -> {
                    items(suggestions.queries) { suggestion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { executeSearch(suggestion) }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Icon(ZIcons.Search, contentDescription = null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(suggestion, style = MaterialTheme.typography.bodyLarge, color = glass.ink)
                        }
                    }
                    items(suggestions.directHits, key = { it.key }) { item ->
                        SearchItemRow(
                            item = item,
                            playingId = playback.current?.id,
                            onNavigate = onNavigate,
                            onPlay = { song -> hub.pilot.audioplay(song, "Search") },
                            onMore = onTrackMenu,
                        )
                    }
                }

                submitted.isNotEmpty() -> {
                    if (searching && results.isEmpty()) {
                        item(key = "loader") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                            ) {
                                CircularProgressIndicator(color = glass.accent)
                            }
                        }
                    }
                    items(results, key = { "${activeTab.name}_${it.key}" }) { item ->
                        SearchItemRow(
                            item = item,
                            playingId = playback.current?.id,
                            onNavigate = onNavigate,
                            onPlay = { song ->
                                val pool = results.mapNotNull { (it as? FeedItem.SongCard)?.track }
                                val start = pool.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                hub.pilot.playtracks(pool.ifEmpty { listOf(song) }, start, "Search: $submitted")
                            },
                            onMore = onTrackMenu,
                        )
                    }
                }

                else -> {
                    if (recentQueries.isNotEmpty()) {
                        item(key = "recent_head") {
                            SectionHeader(
                                title = "Recent searches",
                                actionLabel = "Clear",
                                onAction = { hub.clearsearches() },
                            )
                        }
                        item(key = "recent_chips") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(recentQueries, key = { it }) { past ->
                                    GlassChip(
                                        label = past,
                                        selected = false,
                                        icon = ZIcons.History,
                                        onClick = { executeSearch(past) },
                                    )
                                }
                            }
                        }
                    }

                    if (explore.moods.isNotEmpty()) {
                        item(key = "moods_head") {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(title = "Browse categories")
                        }
                        val pairs = explore.moods.chunked(2)
                        items(pairs) { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                            ) {
                                rowItems.forEach { tile ->
                                    val accentTint = Color(tile.stripeColor or 0xFF000000L)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        accentTint.copy(alpha = if (glass.isDark) 0.34f else 0.22f),
                                                        glass.glassElevated,
                                                    ),
                                                ),
                                            )
                                            .clickable {
                                                onNavigate(Route.Browse(tile.browseId, tile.params, tile.title))
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                    ) {
                                        Text(
                                            text = tile.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = glass.ink,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.align(Alignment.BottomStart),
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (explore.releases.isNotEmpty()) {
                        item(key = "releases_head") {
                            Spacer(Modifier.height(10.dp))
                            SectionHeader(title = "New releases")
                        }
                        item(key = "releases_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(explore.releases, key = { it.id }) { album ->
                                    AlbumGridCard(album = album) {
                                        onNavigate(Route.Album(album.id))
                                    }
                                }
                            }
                        }
                    }

                    explore.charts.forEachIndexed { cIdx, chart ->
                        item(key = "chart_head_$cIdx") {
                            Spacer(Modifier.height(10.dp))
                            SectionHeader(title = chart.title)
                        }
                        item(key = "chart_row_$cIdx") {
                            val songs = chart.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                            if (songs.isNotEmpty()) {
                                val cols = songs.take(15).chunked(3)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(cols) { group ->
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            group.forEach { song ->
                                                QuickPickTile(
                                                    track = song,
                                                    playing = playback.current?.id == song.id,
                                                    onClick = {
                                                        val idx = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                                        hub.pilot.playtracks(songs, idx, chart.title)
                                                    },
                                                    onMore = { onTrackMenu(song) },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(chart.items, key = { it.key }) { entry ->
                                        MediaCard(
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

                    if (exploreLoading && explore.moods.isEmpty() && explore.releases.isEmpty()) {
                        item(key = "exp_loading") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 56.dp),
                            ) {
                                CircularProgressIndicator(color = glass.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchItemRow(
    item: FeedItem,
    playingId: String?,
    onNavigate: (Route) -> Unit,
    onPlay: (Track) -> Unit,
    onMore: (Track) -> Unit,
) {
    val glass = LocalGlass.current
    when (item) {
        is FeedItem.SongCard -> {
            SongRow(
                track = item.track,
                playing = playingId == item.track.id,
                onClick = { onPlay(item.track) },
                onMore = { onMore(item.track) },
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
        is FeedItem.AlbumCard -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Album(item.album.id)) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                ArtImage(
                    url = item.album.artwork,
                    shape = RoundedCornerShape(10.dp),
                    fallbackIcon = ZIcons.Album,
                    modifier = Modifier.size(54.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.album.title, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        "Album",
                        item.album.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                        item.album.year?.toString(),
                    ).joinToString(" - ")
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        is FeedItem.ArtistCard -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Artist(item.artist.id)) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                ArtImage(
                    url = item.artist.artwork,
                    shape = CircleShape,
                    fallbackIcon = ZIcons.Artist,
                    modifier = Modifier.size(54.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.artist.name, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1)
                    Text(item.artist.subscribers ?: "Artist", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary, maxLines = 1)
                }
            }
        }
        is FeedItem.PlaylistCard -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Route.Playlist(item.playlist.id)) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                ArtImage(
                    url = item.playlist.artwork,
                    shape = RoundedCornerShape(10.dp),
                    fallbackIcon = ZIcons.Playlist,
                    modifier = Modifier.size(54.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.playlist.title, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull("Playlist", item.playlist.author, item.playlist.countText).joinToString(" - ")
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
