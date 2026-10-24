package zuiwan.screen

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zuiwan.model.FeedItem
import zuiwan.model.SearchPage
import zuiwan.model.SearchTab
import zuiwan.model.Track
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ArtImage
import zuiwan.ui.GlassBox
import zuiwan.ui.GlassChip
import zuiwan.ui.MediaCard
import zuiwan.ui.Route
import zuiwan.ui.SectionHeader
import zuiwan.ui.SongRow
import zuiwan.ui.ZIcons

@Composable
fun viewsearch(
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    bottomPadding: PaddingValues,
) {
    val hub = LocalHub.current
    val glass = LocalGlass.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val recentSearches by hub.searchHistory.collectAsState()
    val playback by hub.pilot.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(SearchTab.ALL) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestedItems by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var page by remember { mutableStateOf(SearchPage()) }
    var loading by remember { mutableStateOf(false) }

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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            GlassBox(
                shape = RoundedCornerShape(22.dp),
                elevated = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = ZIcons.Search,
                        contentDescription = null,
                        tint = glass.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Songs, artists, albums, playlists...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = glass.inkMuted,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                if (it.isBlank()) submitted = ""
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = glass.ink),
                            cursorBrush = SolidColor(glass.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runsearch(query) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable {
                                    query = ""
                                    submitted = ""
                                    page = SearchPage()
                                },
                        ) {
                            Icon(
                                imageVector = ZIcons.Close,
                                contentDescription = null,
                                tint = glass.inkSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SearchTab.entries.forEach { t ->
                    GlassChip(
                        label = t.label,
                        selected = tab == t,
                        onClick = { runsearch(submitted, t) },
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp),
            modifier = Modifier.weight(1f),
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
                            Icon(ZIcons.Search, null, tint = glass.inkSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(sug, style = MaterialTheme.typography.titleMedium, color = glass.ink)
                        }
                    }
                    items(suggestedItems, key = { it.key }) { item ->
                        RenderFeedRow(item, playback.current?.id, onNavigate, onTrackMenu) { song ->
                            hub.pilot.audioplay(song, "Search")
                        }
                    }
                } else if (recentSearches.isNotEmpty()) {
                    item(key = "recent_head") {
                        SectionHeader(
                            title = "Recent Searches",
                            actionLabel = "Clear",
                            onAction = { hub.clearsearches() },
                        )
                    }
                    items(recentSearches, key = { it }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = item
                                    runsearch(item)
                                }
                                .padding(horizontal = 22.dp, vertical = 11.dp),
                        ) {
                            Icon(ZIcons.History, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = item,
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { hub.deletesearch(item) },
                            ) {
                                Icon(ZIcons.Close, null, tint = glass.inkMuted, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            } else {
                if (tab == SearchTab.ALL) {
                    page.shelves.forEachIndexed { idx, shelf ->
                        item(key = "s_head_$idx") {
                            Spacer(Modifier.height(6.dp))
                            SectionHeader(title = shelf.title)
                        }
                        val allSongs = shelf.items.all { it is FeedItem.SongCard }
                        if (allSongs) {
                            val songs = shelf.items.mapNotNull { (it as? FeedItem.SongCard)?.track }
                            items(songs, key = { "${idx}_${it.id}" }) { song ->
                                SongRow(
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
                                        MediaCard(
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
                        RenderFeedRow(item, playback.current?.id, onNavigate, onTrackMenu) { song ->
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
                            CircularProgressIndicator(color = glass.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderFeedRow(
    item: FeedItem,
    playingId: String?,
    onNavigate: (Route) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onPlaySong: (Track) -> Unit,
) {
    val glass = LocalGlass.current
    when (item) {
        is FeedItem.SongCard -> SongRow(
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
            ArtImage(item.album.artwork, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.album.title, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1)
                Text(
                    listOfNotNull(
                        item.album.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                        item.album.year?.toString(),
                    ).joinToString(" • ").ifBlank { "Album" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.inkSecondary,
                    maxLines = 1,
                )
            }
            Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
        }

        is FeedItem.ArtistCard -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.Artist(item.artist.id)) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            ArtImage(item.artist.artwork, shape = CircleShape, fallbackIcon = ZIcons.Artist, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.artist.name, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1)
                Text(item.artist.subscribers ?: "Artist", style = MaterialTheme.typography.bodyMedium, color = glass.inkSecondary, maxLines = 1)
            }
            Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
        }

        is FeedItem.PlaylistCard -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(Route.Playlist(item.playlist.id)) }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            ArtImage(item.playlist.artwork, shape = RoundedCornerShape(12.dp), fallbackIcon = ZIcons.Playlist, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.playlist.title, style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1)
                Text(
                    listOfNotNull(item.playlist.author, item.playlist.countText).joinToString(" • ").ifBlank { "Playlist" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.inkSecondary,
                    maxLines = 1,
                )
            }
            Icon(ZIcons.ChevronRight, null, tint = glass.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}
