package zuiwan.tube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import zuiwan.model.AccountProfile
import zuiwan.model.AlbumDetail
import zuiwan.model.AlbumRef
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistDetail
import zuiwan.model.ArtistRef
import zuiwan.model.ArtistSection
import zuiwan.model.ArtistSummary
import zuiwan.model.FeedItem
import zuiwan.model.FeedShelf
import zuiwan.model.HomeFeed
import zuiwan.model.MoodChip
import zuiwan.model.MoodTile
import zuiwan.model.PlaylistDetail
import zuiwan.model.PlaylistSummary
import zuiwan.model.SearchPage
import zuiwan.model.Track
import zuiwan.model.upscaleart

data class NextBundle(
    val title: String? = null,
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val continuation: String? = null,
    val lyricsBrowseId: String? = null,
    val automixPlaylistId: String? = null,
)

data class SuggestionBundle(
    val queries: List<String> = emptyList(),
    val items: List<FeedItem> = emptyList(),
)

object TubeParser {
    fun parseresponsive(
        renderer: JsonObject,
        fallbackAlbum: AlbumSummary? = null,
        fallbackArtists: List<ArtistRef> = emptyList(),
    ): FeedItem? {
        val flex = renderer["flexColumns"].arr().orEmpty()
        val col0Runs = flex.getOrNull(0).obj()
            .path("musicResponsiveListItemFlexColumnRenderer", "text")
            .runs()
        val title = col0Runs.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() } ?: return null

        val col1Runs = flex.drop(1).flatMap { col ->
            col.obj().path("musicResponsiveListItemFlexColumnRenderer", "text").runs()
        }
        val groups = col1Runs.splitgroups()
        val fixedRuns = renderer["fixedColumns"].arr()
            ?.firstOrNull().obj()
            .path("musicResponsiveListItemFlexColumnRenderer", "text")
            .runs()

        val thumb = upscaleart(renderer["thumbnail"].lastthumb(), 544)
        val explicit = renderer["badges"].arr().hasexplicit()

        val directVideoId = renderer.pathstr("playlistItemData", "videoId")
            ?: renderer.pathstr(
                "overlay",
                "musicItemThumbnailOverlayRenderer",
                "content",
                "musicPlayButtonRenderer",
                "playNavigationEndpoint",
                "watchEndpoint",
                "videoId",
            )
            ?: renderer.pathstr("navigationEndpoint", "watchEndpoint", "videoId")
            ?: col0Runs.firstOrNull()?.videoId

        val browseEndpoint = renderer.pathobj("navigationEndpoint", "browseEndpoint")
        val browseId = browseEndpoint?.get("browseId").str()
        val pageType = browseEndpoint.pathstr(
            "browseEndpointContextSupportedConfigs",
            "browseEndpointContextMusicConfig",
            "pageType",
        )

        if (directVideoId != null) {
            val watchType = renderer.pathstr(
                "overlay",
                "musicItemThumbnailOverlayRenderer",
                "content",
                "musicPlayButtonRenderer",
                "playNavigationEndpoint",
                "watchEndpoint",
                "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig",
                "musicVideoType",
            ) ?: renderer.pathstr(
                "navigationEndpoint",
                "watchEndpoint",
                "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig",
                "musicVideoType",
            )
            val isVideo = watchType != null && watchType != "MUSIC_VIDEO_TYPE_ATV"

            val artists = col1Runs.extractartists()
                .ifEmpty { fallbackArtists }
                .ifEmpty { fallbackAlbum?.artists.orEmpty() }

            val albumRun = col1Runs.firstOrNull { r ->
                r.browseId != null && (
                    r.browseId.startsWith("MPREb_") ||
                        r.pageType == "MUSIC_PAGE_TYPE_ALBUM"
                    )
            }
            val albumRef = albumRun?.let { AlbumRef(it.browseId, it.text) }
                ?: fallbackAlbum?.let { AlbumRef(it.id, it.title) }

            val duration = fixedRuns.firstNotNullOfOrNull { parsetime(it.text) }
                ?: groups.asReversed().firstNotNullOfOrNull { g ->
                    parsetime(g.joinToString("") { it.text })
                }
                ?: 0

            val (addToken, removeToken) = extracttokens(renderer.patharr("menu", "menuRenderer", "items"))
            val setVideoId = renderer.pathstr("playlistItemData", "playlistSetVideoId")

            return FeedItem.SongCard(
                Track(
                    id = directVideoId,
                    title = title,
                    artists = artists,
                    album = albumRef,
                    duration = duration,
                    artwork = thumb
                        .ifBlank { fallbackAlbum?.artwork.orEmpty() }
                        .ifBlank { "https://i.ytimg.com/vi/$directVideoId/hqdefault.jpg" },
                    explicit = explicit,
                    video = isVideo,
                    setVideoId = setVideoId,
                    libraryAddToken = addToken,
                    libraryRemoveToken = removeToken,
                ),
            )
        }

        if (browseId != null) {
            return when {
                pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                    val playlistId = renderer.pathstr(
                        "overlay",
                        "musicItemThumbnailOverlayRenderer",
                        "content",
                        "musicPlayButtonRenderer",
                        "playNavigationEndpoint",
                        "watchPlaylistEndpoint",
                        "playlistId",
                    )
                    val year = groups.lastOrNull()?.firstOrNull()?.text?.toIntOrNull()
                    FeedItem.AlbumCard(
                        AlbumSummary(
                            id = browseId,
                            playlistId = playlistId,
                            title = title,
                            artists = col1Runs.extractartists(),
                            year = year,
                            artwork = thumb,
                            explicit = explicit,
                        ),
                    )
                }

                pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                    pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                    browseId.startsWith("UC") -> {
                    val subText = groups.lastOrNull()?.joinToString("") { it.text }
                    FeedItem.ArtistCard(
                        ArtistSummary(
                            id = browseId,
                            name = title,
                            artwork = thumb,
                            subscribers = subText,
                        ),
                    )
                }

                pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL") -> {
                    val cleanId = browseId.removePrefix("VL")
                    if (cleanId.isBlank() || cleanId.startsWith("FEmusic_")) return null
                    val author = col1Runs.extractartists().firstOrNull()?.name
                        ?: groups.getOrNull(1)?.joinToString("") { it.text }
                    val count = groups.lastOrNull()?.joinToString("") { it.text }
                    val editable = canedit(renderer.patharr("menu", "menuRenderer", "items"))
                    FeedItem.PlaylistCard(
                        PlaylistSummary(
                            id = cleanId,
                            title = title,
                            author = author,
                            countText = count,
                            artwork = thumb,
                            remoteId = cleanId,
                            editable = editable,
                        ),
                    )
                }

                else -> null
            }
        }
        return null
    }

    fun parsetile(renderer: JsonObject): FeedItem? {
        val title = renderer["title"].runtext().takeIf { it.isNotBlank() } ?: return null
        val subRuns = renderer["subtitle"].runs()
        val groups = subRuns.splitgroups()
        val rawThumb = renderer["thumbnailRenderer"].lastthumb()
            .ifBlank { renderer["thumbnail"].lastthumb() }
        val thumb = upscaleart(rawThumb, 544)
        val explicit = renderer["subtitleBadges"].arr().hasexplicit()

        val nav = renderer["navigationEndpoint"].obj()
        val watch = nav.pathobj("watchEndpoint")
        val browse = nav.pathobj("browseEndpoint")
        val browseId = browse?.get("browseId").str()
        val pageType = browse.pathstr(
            "browseEndpointContextSupportedConfigs",
            "browseEndpointContextMusicConfig",
            "pageType",
        )

        val videoId = watch?.get("videoId").str()
        if (videoId != null) {
            val watchType = watch.pathstr(
                "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig",
                "musicVideoType",
            )
            return FeedItem.SongCard(
                Track(
                    id = videoId,
                    title = title,
                    artists = subRuns.extractartists(),
                    duration = groups.lastOrNull()?.let { parsetime(it.joinToString("") { r -> r.text }) } ?: 0,
                    artwork = thumb.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
                    explicit = explicit,
                    video = watchType != null && watchType != "MUSIC_VIDEO_TYPE_ATV",
                ),
            )
        }

        if (browseId != null) {
            return when {
                pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_") -> {
                    val playlistId = renderer.pathstr(
                        "thumbnailOverlay",
                        "musicItemThumbnailOverlayRenderer",
                        "content",
                        "musicPlayButtonRenderer",
                        "playNavigationEndpoint",
                        "watchPlaylistEndpoint",
                        "playlistId",
                    )
                    val year = groups.lastOrNull()?.firstOrNull()?.text?.toIntOrNull()
                    FeedItem.AlbumCard(
                        AlbumSummary(
                            id = browseId,
                            playlistId = playlistId,
                            title = title,
                            artists = subRuns.extractartists(),
                            year = year,
                            artwork = thumb,
                            explicit = explicit,
                        ),
                    )
                }

                pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                    pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                    browseId.startsWith("UC") -> {
                    FeedItem.ArtistCard(
                        ArtistSummary(
                            id = browseId,
                            name = title,
                            artwork = thumb,
                            subscribers = subRuns.joinToString("") { it.text }.takeIf { it.isNotBlank() },
                        ),
                    )
                }

                else -> {
                    val cleanId = browseId.removePrefix("VL")
                    if (cleanId.isBlank() || cleanId.startsWith("FEmusic_")) return null
                    val editable = canedit(renderer.patharr("menu", "menuRenderer", "items"))
                    FeedItem.PlaylistCard(
                        PlaylistSummary(
                            id = cleanId,
                            title = title,
                            author = groups.firstOrNull()?.joinToString("") { it.text },
                            countText = groups.getOrNull(1)?.joinToString("") { it.text },
                            artwork = thumb,
                            remoteId = cleanId,
                            editable = editable,
                        ),
                    )
                }
            }
        }
        return null
    }

    fun parseshelf(section: JsonObject): FeedShelf? {
        section["musicCarouselShelfRenderer"].obj()?.let { carousel ->
            val header = carousel.pathobj("header", "musicCarouselShelfBasicHeaderRenderer")
            val title = header?.get("title").runtext().orEmpty()
            val subtitle = header?.get("strapline").runtext()?.takeIf { it.isNotBlank() }
            val moreBrowse = header.pathobj("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint")
            val items = carousel["contents"].arr().orEmpty().mapNotNull { entry ->
                val o = entry.obj() ?: return@mapNotNull null
                o["musicTwoRowItemRenderer"].obj()?.let(::parsetile)
                    ?: o["musicResponsiveListItemRenderer"].obj()?.let { parseresponsive(it) }
            }
            if (title.isNotBlank() && items.isNotEmpty()) {
                return FeedShelf(
                    title = title,
                    subtitle = subtitle,
                    items = items,
                    browseId = moreBrowse?.get("browseId").str(),
                    params = moreBrowse?.get("params").str(),
                )
            }
        }

        section["musicShelfRenderer"].obj()?.let { shelf ->
            val title = shelf["title"].runtext().ifBlank { "Tracks" }
            val moreBrowse = shelf.pathobj("bottomEndpoint", "browseEndpoint")
            val items = shelf["contents"].arr().orEmpty().mapNotNull { entry ->
                entry.obj()?.get("musicResponsiveListItemRenderer").obj()?.let { parseresponsive(it) }
            }
            if (items.isNotEmpty()) {
                return FeedShelf(
                    title = title,
                    items = items,
                    browseId = moreBrowse?.get("browseId").str(),
                    params = moreBrowse?.get("params").str(),
                )
            }
        }

        section["gridRenderer"].obj()?.let { grid ->
            val title = grid.path("header", "gridHeaderRenderer", "title").runtext().ifBlank { "Featured" }
            val items = grid["items"].arr().orEmpty().mapNotNull { entry ->
                entry.obj()?.get("musicTwoRowItemRenderer").obj()?.let(::parsetile)
            }
            if (items.isNotEmpty()) {
                return FeedShelf(title = title, items = items)
            }
        }

        section["musicCardShelfRenderer"].obj()?.let { card ->
            val headerTitle = card.path("header", "musicCardShelfHeaderBasicRenderer", "title")
                .runtext()
                .ifBlank { "Top Result" }
            val mainTitle = card["title"].runtext()
            val subRuns = card["subtitle"].runs()
            val thumb = upscaleart(card["thumbnail"].lastthumb(), 544)
            val onTap = card["onTap"].obj()
            val videoId = onTap.pathstr("watchEndpoint", "videoId")
            val browseId = onTap.pathstr("browseEndpoint", "browseId")
            val pageType = onTap.pathstr(
                "browseEndpoint",
                "browseEndpointContextSupportedConfigs",
                "browseEndpointContextMusicConfig",
                "pageType",
            )
            val results = mutableListOf<FeedItem>()
            if (mainTitle.isNotBlank()) {
                when {
                    videoId != null -> results += FeedItem.SongCard(
                        Track(
                            id = videoId,
                            title = mainTitle,
                            artists = subRuns.extractartists(),
                            artwork = thumb,
                            explicit = card["subtitleBadges"].arr().hasexplicit(),
                        ),
                    )
                    browseId != null && (pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC")) ->
                        results += FeedItem.ArtistCard(
                            ArtistSummary(
                                id = browseId,
                                name = mainTitle,
                                artwork = thumb,
                                subscribers = subRuns.joinToString("") { it.text },
                            ),
                        )
                    browseId != null && (pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_")) ->
                        results += FeedItem.AlbumCard(
                            AlbumSummary(
                                id = browseId,
                                title = mainTitle,
                                artists = subRuns.extractartists(),
                                artwork = thumb,
                            ),
                        )
                    browseId != null ->
                        results += FeedItem.PlaylistCard(
                            PlaylistSummary(
                                id = browseId.removePrefix("VL"),
                                title = mainTitle,
                                author = subRuns.joinToString("") { it.text },
                                artwork = thumb,
                            ),
                        )
                }
            }
            card["contents"].arr().orEmpty().forEach { nested ->
                nested.obj()?.get("musicResponsiveListItemRenderer").obj()?.let {
                    parseresponsive(it)?.let(results::add)
                }
            }
            if (results.isNotEmpty()) {
                return FeedShelf(title = headerTitle, items = results.distinctBy { it.key })
            }
        }
        return null
    }

    fun parsehome(root: JsonObject): HomeFeed {
        val contSection = root.pathobj("continuationContents", "sectionListContinuation")
        if (contSection != null) {
            val shelves = contSection["contents"].arr().orEmpty().mapNotNull { it.obj()?.let(::parseshelf) }
            return HomeFeed(
                chips = emptyList(),
                shelves = shelves,
                continuation = contSection.findcontinuation(),
            )
        }

        val sectionList = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .pathobj("tabRenderer", "content", "sectionListRenderer")

        val chips = sectionList.patharr("header", "chipCloudRenderer", "chips")
            .orEmpty()
            .mapNotNull { chipEl ->
                val chip = chipEl.obj()?.get("chipCloudChipRenderer").obj() ?: return@mapNotNull null
                val label = chip["text"].runtext().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val params = chip.pathstr("navigationEndpoint", "browseEndpoint", "params") ?: return@mapNotNull null
                MoodChip(title = label, params = params)
            }

        val shelves = sectionList?.get("contents").arr().orEmpty().mapNotNull { it.obj()?.let(::parseshelf) }
        return HomeFeed(
            chips = chips,
            shelves = shelves,
            continuation = sectionList.findcontinuation(),
        )
    }

    fun parsemoods(sections: JsonArray?): List<MoodTile> =
        sections.orEmpty().flatMap { sec ->
            val o = sec.obj() ?: return@flatMap emptyList()
            val buttons = o.patharr("musicCarouselShelfRenderer", "contents")
                ?: o.patharr("gridRenderer", "items")
                ?: return@flatMap emptyList()
            buttons.mapNotNull { btnEl ->
                val btn = btnEl.obj()?.get("musicNavigationButtonRenderer").obj() ?: return@mapNotNull null
                val title = btn["buttonText"].runtext().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val browse = btn.pathobj("clickCommand", "browseEndpoint") ?: return@mapNotNull null
                val browseId = browse["browseId"].str() ?: return@mapNotNull null
                val color = btn.path("solid", "leftStripeColor").longnum() ?: 0xFF38BDF8L
                MoodTile(
                    title = title,
                    browseId = browseId,
                    params = browse["params"].str(),
                    stripeColor = color,
                )
            }
        }

    fun parsealbum(browseId: String, root: JsonObject): Pair<AlbumSummary, List<AlbumSummary>> {
        val responsiveHeader = root.patharr("contents", "twoColumnBrowseResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?.firstOrNull().obj()
            ?.get("musicResponsiveHeaderRenderer").obj()
        val detailHeader = root.pathobj("header", "musicDetailHeaderRenderer")

        val title = responsiveHeader?.get("title")?.runtext()?.takeIf { it.isNotBlank() }
            ?: detailHeader?.get("title")?.runtext().orEmpty()
        val artists = responsiveHeader?.get("straplineTextOne")?.runs()?.extractartists()
            ?: detailHeader?.get("subtitle")?.runs()?.extractartists()
            ?: emptyList()
        val subRuns = responsiveHeader?.get("subtitle")?.runs()
            ?: detailHeader?.get("subtitle")?.runs()
            ?: emptyList()
        val year = subRuns.lastOrNull()?.text?.toIntOrNull()
        val thumb = upscaleart(
            (responsiveHeader?.get("thumbnail") ?: detailHeader?.get("thumbnail")).lastthumb(),
            1080,
        )
        val canonical = root.pathstr("microformat", "microformatDataRenderer", "urlCanonical")
        val playlistId = canonical?.substringAfterLast("=", "")?.takeIf { it.isNotBlank() }
            ?: detailHeader.patharr("menu", "menuRenderer", "topLevelButtons")
                ?.firstOrNull().obj()
                .pathstr("buttonRenderer", "navigationEndpoint", "watchPlaylistEndpoint", "playlistId")

        val versions = root.patharr(
            "contents",
            "twoColumnBrowseResultsRenderer",
            "secondaryContents",
            "sectionListRenderer",
            "contents",
        )?.drop(1)?.flatMap { sec ->
            sec.obj().patharr("musicCarouselShelfRenderer", "contents").orEmpty().mapNotNull { item ->
                (item.obj()?.get("musicTwoRowItemRenderer").obj()?.let(::parsetile) as? FeedItem.AlbumCard)?.album
            }
        }.orEmpty()

        val finalAlbumThumb = thumb.ifBlank {
            upscaleart(root.path("microformat", "microformatDataRenderer", "thumbnail").lastthumb(), 1080)
        }

        return AlbumSummary(
            id = browseId,
            playlistId = playlistId,
            title = title.ifBlank { "Album" },
            artists = artists,
            year = year,
            artwork = finalAlbumThumb,
        ) to versions
    }

    fun albumtracks(root: JsonObject, album: AlbumSummary): Pair<List<Track>, String?> {
        val twoColShelf = root.patharr(
            "contents",
            "twoColumnBrowseResultsRenderer",
            "secondaryContents",
            "sectionListRenderer",
            "contents",
        )?.firstOrNull().obj()
        val singleColShelf = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?.firstOrNull().obj()

        val rawItems = twoColShelf.patharr("musicPlaylistShelfRenderer", "contents")
            ?: twoColShelf.patharr("musicShelfRenderer", "contents")
            ?: singleColShelf.patharr("musicShelfRenderer", "contents")
            ?: root["onResponseReceivedActions"].arr()
                ?.firstOrNull().obj()
                .patharr("appendContinuationItemsAction", "continuationItems")
            ?: root.patharr("continuationContents", "musicPlaylistShelfContinuation", "contents")
            ?: JsonArray(emptyList())

        val tracks = rawItems.mapIndexedNotNull { idx, item ->
            val renderer = item.obj()?.get("musicResponsiveListItemRenderer").obj() ?: return@mapIndexedNotNull null
            val parsed = (parseresponsive(renderer, fallbackAlbum = album) as? FeedItem.SongCard)?.track
                ?: return@mapIndexedNotNull null
            parsed.copy(
                artists = parsed.artists.ifEmpty { album.artists },
                album = AlbumRef(album.id, album.title),
                artwork = parsed.artwork.ifBlank { album.artwork },
            )
        }
        val cont = rawItems.findcontinuation()
            ?: root.pathobj("continuationContents", "musicPlaylistShelfContinuation").findcontinuation()
        return tracks to cont
    }

    fun parseartist(browseId: String, root: JsonObject): ArtistDetail {
        val immersive = root.pathobj("header", "musicImmersiveHeaderRenderer")
        val visual = root.pathobj("header", "musicVisualHeaderRenderer")
        val basic = root.pathobj("header", "musicHeaderRenderer")
        val tabbed = root.pathobj("header", "c4TabbedHeaderRenderer")

        val name = immersive?.get("title")?.runtext()?.takeIf { it.isNotBlank() }
            ?: visual?.get("title")?.runtext()?.takeIf { it.isNotBlank() }
            ?: tabbed?.get("title")?.runtext()?.takeIf { it.isNotBlank() }
            ?: basic?.get("title")?.runtext()
            ?: "Artist"
        val rawArtistThumb = listOfNotNull(
            immersive?.get("thumbnail")?.lastthumb(),
            visual?.get("foregroundThumbnail")?.lastthumb(),
            visual?.get("thumbnail")?.lastthumb(),
            tabbed?.get("avatar")?.lastthumb(),
            basic?.get("thumbnail")?.lastthumb(),
            root.path("microformat", "microformatDataRenderer", "thumbnail").lastthumb(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        val thumb = upscaleart(rawArtistThumb, 1080)
        val subButton = immersive.pathobj("subscriptionButton", "subscribeButtonRenderer")
            ?: visual.pathobj("subscriptionButton", "subscribeButtonRenderer")
        val subscribers = immersive.path("subscriptionButton2", "subscribeButtonRenderer", "subscriberCountWithSubscribeText")
            .runtext()
            .ifBlank { subButton?.get("shortSubscriberCountText").runtext() }
            .takeIf { it.isNotBlank() }
        val listeners = immersive?.get("monthlyListenerCount")?.runtext()?.takeIf { it.isNotBlank() }
        val channelId = subButton?.get("channelId").str()
        val subscribed = subButton?.get("subscribed").flag() == true

        val shuffleId = immersive.pathstr("playButton", "buttonRenderer", "navigationEndpoint", "watchEndpoint", "playlistId")
            ?: immersive.pathstr("playButton", "buttonRenderer", "navigationEndpoint", "watchPlaylistEndpoint", "playlistId")
        val radioId = immersive.pathstr("startRadioButton", "buttonRenderer", "navigationEndpoint", "watchEndpoint", "playlistId")
            ?: immersive.pathstr("startRadioButton", "buttonRenderer", "navigationEndpoint", "watchPlaylistEndpoint", "playlistId")

        val rawSections = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            .orEmpty()

        val description = rawSections.firstNotNullOfOrNull { sec ->
            sec.obj().path("musicDescriptionShelfRenderer", "description").runtext().takeIf { it.isNotBlank() }
        } ?: immersive?.get("description")?.runtext()?.takeIf { it.isNotBlank() }

        val sections = rawSections.mapNotNull { secEl ->
            val shelf = secEl.obj()?.let(::parseshelf) ?: return@mapNotNull null
            ArtistSection(
                title = shelf.title,
                items = shelf.items,
                browseId = shelf.browseId,
                params = shelf.params,
            )
        }

        return ArtistDetail(
            artist = ArtistSummary(
                id = browseId,
                name = name,
                artwork = thumb,
                subscribers = subscribers,
                channelId = channelId,
                saved = subscribed,
            ),
            listeners = listeners,
            description = description,
            shufflePlaylistId = shuffleId,
            radioPlaylistId = radioId,
            sections = sections,
        )
    }

    fun parseplaylist(playlistId: String, root: JsonObject): PlaylistDetail {
        val base = root.patharr("contents", "twoColumnBrowseResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?.firstOrNull().obj()
        val editable = base?.get("musicEditablePlaylistDetailHeaderRenderer") != null
        val header = base?.get("musicResponsiveHeaderRenderer").obj()
            ?: base.pathobj("musicEditablePlaylistDetailHeaderRenderer", "header", "musicResponsiveHeaderRenderer")
            ?: root.pathobj("header", "musicDetailHeaderRenderer")
            ?: root.pathobj("header", "musicResponsiveHeaderRenderer")

        val title = header?.get("title")?.runtext()?.ifBlank { "Playlist" } ?: "Playlist"
        val author = header?.get("straplineTextOne")?.runtext()?.takeIf { it.isNotBlank() }
            ?: header?.get("subtitle")?.runs()?.extractartists()?.firstOrNull()?.name
        val secondSub = header?.get("secondSubtitle")?.runtext()?.takeIf { it.isNotBlank() }
        val description = header.path("description", "musicDescriptionShelfRenderer", "description")
            .runtext()
            .ifBlank { header?.get("description").runtext() }
            .takeIf { it.isNotBlank() }
        val rawPlThumb = header?.get("thumbnail").lastthumb()
            .ifBlank { root.path("microformat", "microformatDataRenderer", "thumbnail").lastthumb() }
        val thumb = upscaleart(rawPlThumb, 1080)

        val secondaryShelf = root.patharr(
            "contents",
            "twoColumnBrowseResultsRenderer",
            "secondaryContents",
            "sectionListRenderer",
            "contents",
        )?.firstOrNull().obj()
        val singleShelf = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?.firstOrNull().obj()

        val rawTracks = secondaryShelf.patharr("musicPlaylistShelfRenderer", "contents")
            ?: secondaryShelf.patharr("musicShelfRenderer", "contents")
            ?: singleShelf.patharr("musicPlaylistShelfRenderer", "contents")
            ?: singleShelf.patharr("musicShelfRenderer", "contents")
            ?: JsonArray(emptyList())

        val tracks = rawTracks.mapNotNull { item ->
            val r = item.obj()?.get("musicResponsiveListItemRenderer").obj() ?: return@mapNotNull null
            (parseresponsive(r) as? FeedItem.SongCard)?.track
        }
        val cont = rawTracks.findcontinuation()
            ?: secondaryShelf.pathobj("musicPlaylistShelfRenderer").findcontinuation()

        return PlaylistDetail(
            playlist = PlaylistSummary(
                id = playlistId.removePrefix("VL"),
                title = title,
                author = author,
                countText = secondSub ?: "${tracks.size} songs",
                artwork = thumb.ifBlank { tracks.firstOrNull()?.artwork.orEmpty() },
                local = false,
                remoteId = playlistId.removePrefix("VL"),
                editable = editable,
            ),
            description = description,
            tracks = tracks,
            continuation = cont,
        )
    }

    fun moretracks(root: JsonObject): Pair<List<Track>, String?> {
        val raw = root["onResponseReceivedActions"].arr()
            ?.firstOrNull().obj()
            .patharr("appendContinuationItemsAction", "continuationItems")
            ?: root.patharr("continuationContents", "musicPlaylistShelfContinuation", "contents")
            ?: root.patharr("continuationContents", "musicShelfContinuation", "contents")
            ?: JsonArray(emptyList())
        val tracks = raw.mapNotNull { el ->
            val r = el.obj()?.get("musicResponsiveListItemRenderer").obj() ?: return@mapNotNull null
            (parseresponsive(r) as? FeedItem.SongCard)?.track
        }
        val cont = raw.findcontinuation()
            ?: root.pathobj("continuationContents", "musicPlaylistShelfContinuation").findcontinuation()
            ?: root.pathobj("continuationContents", "musicShelfContinuation").findcontinuation()
        return tracks to cont
    }

    fun parsesearch(root: JsonObject, filtered: Boolean): SearchPage {
        val contShelf = root.pathobj("continuationContents", "musicShelfContinuation")
        if (contShelf != null) {
            val items = contShelf["contents"].arr().orEmpty().mapNotNull { el ->
                el.obj()?.get("musicResponsiveListItemRenderer").obj()?.let { parseresponsive(it) }
            }
            return SearchPage(
                items = items,
                continuation = contShelf.findcontinuation(),
            )
        }

        val sections = root.patharr("contents", "tabbedSearchResultsRenderer", "tabs")
            ?.firstOrNull().obj()
            .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?: root.patharr("contents", "sectionListRenderer", "contents")
            ?: JsonArray(emptyList())

        if (filtered) {
            val items = mutableListOf<FeedItem>()
            var cont: String? = null
            sections.forEach { secEl ->
                val shelf = secEl.obj()?.get("musicShelfRenderer").obj() ?: return@forEach
                shelf["contents"].arr().orEmpty().forEach { entry ->
                    entry.obj()?.get("musicResponsiveListItemRenderer").obj()?.let { r ->
                        parseresponsive(r)?.let(items::add)
                    }
                }
                if (cont == null) cont = shelf.findcontinuation()
            }
            return SearchPage(items = items.distinctBy { it.key }, continuation = cont)
        }

        val shelves = sections.mapNotNull { it.obj()?.let(::parseshelf) }
        return SearchPage(shelves = shelves)
    }

    fun parsesuggestions(root: JsonObject): SuggestionBundle {
        val sections = root["contents"].arr().orEmpty()
        val queries = sections.getOrNull(0).obj()
            .patharr("searchSuggestionsSectionRenderer", "contents")
            .orEmpty()
            .mapNotNull { entry ->
                entry.obj()
                    .path("searchSuggestionRenderer", "suggestion")
                    .runtext()
                    .takeIf { it.isNotBlank() }
            }
        val items = sections.getOrNull(1).obj()
            .patharr("searchSuggestionsSectionRenderer", "contents")
            .orEmpty()
            .mapNotNull { entry ->
                entry.obj()?.get("musicResponsiveListItemRenderer").obj()?.let { parseresponsive(it) }
            }
        return SuggestionBundle(queries = queries, items = items)
    }

    fun parsenext(root: JsonObject): NextBundle {
        val tabs = root.patharr(
            "contents",
            "singleColumnMusicWatchNextResultsRenderer",
            "tabbedRenderer",
            "watchNextTabbedResultsRenderer",
            "tabs",
        )
        val queueRenderer = tabs?.getOrNull(0).obj().pathobj("tabRenderer", "content", "musicQueueRenderer")
        val panel = root.pathobj("continuationContents", "playlistPanelContinuation")
            ?: queueRenderer.pathobj("content", "playlistPanelRenderer")
        val headerTitle = queueRenderer.path("header", "musicQueueHeaderRenderer", "subtitle").runtext()
            .takeIf { it.isNotBlank() }

        val lyricsBrowseId = tabs?.getOrNull(1).obj()
            .pathstr("tabRenderer", "endpoint", "browseEndpoint", "browseId")

        val rawContents = panel?.get("contents").arr().orEmpty()
        var currentIndex = 0
        val tracks = mutableListOf<Track>()
        var automixId: String? = null

        rawContents.forEach { el ->
            val o = el.obj() ?: return@forEach
            val video = o["playlistPanelVideoRenderer"].obj()
                ?: o.pathobj("playlistPanelVideoWrapperRenderer", "primaryRenderer", "playlistPanelVideoRenderer")
            if (video != null) {
                val id = video["videoId"].str() ?: return@forEach
                val title = video["title"].runtext().ifBlank { "Track" }
                val byline = video["longBylineText"].runs()
                val groups = byline.splitgroups()
                val artists = byline.extractartists()
                val albumRun = byline.firstOrNull { r ->
                    r.browseId != null && (r.browseId.startsWith("MPREb_") || r.pageType == "MUSIC_PAGE_TYPE_ALBUM")
                }
                val duration = parsetime(video["lengthText"].runtext()) ?: 0
                val thumb = upscaleart(video["thumbnail"].lastthumb(), 544)
                val explicit = video["badges"].arr().hasexplicit()
                val (addToken, removeToken) = extracttokens(video.patharr("menu", "menuRenderer", "items"))
                if (video["selected"].flag() == true) {
                    currentIndex = tracks.size
                }
                tracks += Track(
                    id = id,
                    title = title,
                    artists = artists.ifEmpty {
                        groups.firstOrNull()?.joinToString("") { it.text }
                            ?.takeIf { it.isNotBlank() }
                            ?.let { listOf(ArtistRef(null, it)) }
                            .orEmpty()
                    },
                    album = albumRun?.let { AlbumRef(it.browseId, it.text) },
                    duration = duration,
                    artwork = thumb.ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" },
                    explicit = explicit,
                    setVideoId = video["playlistSetVideoId"].str(),
                    libraryAddToken = addToken,
                    libraryRemoveToken = removeToken,
                )
            }
            o.pathstr(
                "automixPreviewVideoRenderer",
                "content",
                "automixPlaylistVideoRenderer",
                "navigationEndpoint",
                "watchPlaylistEndpoint",
                "playlistId",
            )?.let { automixId = it }
        }

        return NextBundle(
            title = headerTitle,
            tracks = tracks,
            currentIndex = currentIndex,
            continuation = panel.findcontinuation(),
            lyricsBrowseId = lyricsBrowseId,
            automixPlaylistId = automixId,
        )
    }

    fun parselyrics(root: JsonObject): String? {
        val sections = root.patharr("contents", "sectionListRenderer", "contents")
            ?: root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
                ?.firstOrNull().obj()
                .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            ?: return null
        return sections.firstNotNullOfOrNull { el ->
            el.obj().path("musicDescriptionShelfRenderer", "description")
                .runtext()
                .takeIf { it.isNotBlank() }
        }
    }

    fun parseaccount(root: JsonObject): AccountProfile? {
        val header = root["actions"].arr()
            ?.firstOrNull().obj()
            .pathobj("openPopupAction", "popup", "multiPageMenuRenderer", "header", "activeAccountHeaderRenderer")
        if (header != null) {
            val name = header["accountName"].runtext().takeIf { it.isNotBlank() } ?: return null
            val handle = header["channelHandle"].runtext()
            val email = header["email"].runtext()
            val avatar = upscaleart(header["accountPhoto"].lastthumb(), 256)
            return AccountProfile(name = name, handle = handle, email = email, avatar = avatar)
        }
        val sections = root.patharr("contents")
        val selected = sections.orEmpty().firstNotNullOfOrNull { secEl ->
            secEl.obj().patharr("accountSectionListRenderer", "contents").orEmpty().firstNotNullOfOrNull { itemSec ->
                itemSec.obj().patharr("accountItemSectionRenderer", "contents").orEmpty().firstNotNullOfOrNull { accEl ->
                    val item = accEl.obj()?.get("accountItem").obj()
                    item?.takeIf { it["isSelected"].flag() == true } ?: item
                }
            }
        } ?: return null
        val name = selected["accountName"].runtext().takeIf { it.isNotBlank() } ?: return null
        val handle = selected["channelHandle"].runtext()
        val avatar = upscaleart(selected["accountPhoto"].lastthumb(), 256)
        return AccountProfile(name = name, handle = handle, email = "", avatar = avatar)
    }

    fun parselibrary(root: JsonObject): Pair<List<FeedItem>, String?> {
        val items = mutableListOf<FeedItem>()
        var cont: String? = null

        fun takeentry(entry: JsonObject) {
            entry["musicTwoRowItemRenderer"].obj()?.let { parsetile(it)?.let(items::add) }
            entry["musicResponsiveListItemRenderer"].obj()?.let { parseresponsive(it)?.let(items::add) }
        }

        fun takesection(sec: JsonObject) {
            sec["gridRenderer"].obj()?.let { grid ->
                grid["items"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
                if (cont == null) cont = grid.findcontinuation() ?: grid["items"].arr().findcontinuation()
            }
            sec["musicShelfRenderer"].obj()?.let { shelf ->
                shelf["contents"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
                if (cont == null) cont = shelf.findcontinuation() ?: shelf["contents"].arr().findcontinuation()
            }
            sec["musicPlaylistShelfRenderer"].obj()?.let { shelf ->
                shelf["contents"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
                if (cont == null) cont = shelf.findcontinuation() ?: shelf["contents"].arr().findcontinuation()
            }
            sec["musicCarouselShelfRenderer"].obj()?.let { shelf ->
                shelf["contents"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
            }
            sec["itemSectionRenderer"].obj()?.let { wrap ->
                wrap["contents"].arr().orEmpty().forEach { it.obj()?.let(::takesection) }
                if (cont == null) cont = wrap.findcontinuation()
            }
            takeentry(sec)
        }

        root.pathobj("continuationContents", "gridContinuation")?.let { grid ->
            grid["items"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
            val next = grid.findcontinuation() ?: grid["items"].arr().findcontinuation()
            return items.distinctBy { it.key } to next
        }
        root.pathobj("continuationContents", "musicShelfContinuation")?.let { shelf ->
            shelf["contents"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
            val next = shelf.findcontinuation() ?: shelf["contents"].arr().findcontinuation()
            return items.distinctBy { it.key } to next
        }
        root.pathobj("continuationContents", "musicPlaylistShelfContinuation")?.let { shelf ->
            shelf["contents"].arr().orEmpty().forEach { it.obj()?.let(::takeentry) }
            val next = shelf.findcontinuation() ?: shelf["contents"].arr().findcontinuation()
            return items.distinctBy { it.key } to next
        }
        root.pathobj("continuationContents", "sectionListContinuation")?.let { secList ->
            secList["contents"].arr().orEmpty().forEach { it.obj()?.let(::takesection) }
            val next = cont ?: secList.findcontinuation()
            return items.distinctBy { it.key } to next
        }
        root["onResponseReceivedActions"].arr()
            ?.firstOrNull().obj()
            .patharr("appendContinuationItemsAction", "continuationItems")
            ?.let { raw ->
                raw.forEach { it.obj()?.let(::takesection) }
                val next = cont ?: raw.findcontinuation()
                return items.distinctBy { it.key } to next
            }

        val singleTabs = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs").orEmpty()
        singleTabs.forEach { tab ->
            val secList = tab.obj().pathobj("tabRenderer", "content", "sectionListRenderer")
            secList?.get("contents").arr().orEmpty().forEach { it.obj()?.let(::takesection) }
            if (cont == null) cont = secList.findcontinuation()
        }

        val twoColTabs = root.patharr("contents", "twoColumnBrowseResultsRenderer", "tabs").orEmpty()
        twoColTabs.forEach { tab ->
            val secList = tab.obj().pathobj("tabRenderer", "content", "sectionListRenderer")
            secList?.get("contents").arr().orEmpty().forEach { it.obj()?.let(::takesection) }
            if (cont == null) cont = secList.findcontinuation()
        }

        val twoColSecondary = root.pathobj("contents", "twoColumnBrowseResultsRenderer", "secondaryContents", "sectionListRenderer")
        twoColSecondary?.get("contents").arr().orEmpty().forEach { it.obj()?.let(::takesection) }
        if (cont == null) cont = twoColSecondary.findcontinuation()

        return items.distinctBy { it.key } to cont
    }

    private fun canedit(menuItems: JsonArray?): Boolean =
        menuItems.orEmpty().any { itemEl ->
            val o = itemEl.obj() ?: return@any false
            val svc = o["menuServiceItemRenderer"].obj()
            val nav = o["menuNavigationItemRenderer"].obj()
            val icon = svc.pathstr("icon", "iconType") ?: nav.pathstr("icon", "iconType")
            svc?.get("serviceEndpoint")?.obj()?.containsKey("deletePlaylistEndpoint") == true ||
                nav?.get("navigationEndpoint")?.obj()?.containsKey("confirmDialogEndpoint") == true && icon == "DELETE" ||
                icon == "EDIT" ||
                icon == "DELETE"
        }

    private fun extracttokens(menuItems: JsonArray?): Pair<String?, String?> {
        var add: String? = null
        var remove: String? = null
        menuItems.orEmpty().forEach { itemEl ->
            val toggle = itemEl.obj()?.get("toggleMenuServiceItemRenderer").obj() ?: return@forEach
            val icon = toggle.pathstr("defaultIcon", "iconType") ?: return@forEach
            val defTok = toggle.pathstr("defaultServiceEndpoint", "feedbackEndpoint", "feedbackToken")
            val togTok = toggle.pathstr("toggledServiceEndpoint", "feedbackEndpoint", "feedbackToken")
            if (icon == "LIBRARY_ADD" || icon == "BOOKMARK_BORDER") {
                add = defTok
                remove = togTok
            } else if (icon == "LIBRARY_SAVED" || icon == "LIBRARY_REMOVE" || icon == "BOOKMARK") {
                remove = defTok
                add = togTok
            }
        }
        return add to remove
    }
}
