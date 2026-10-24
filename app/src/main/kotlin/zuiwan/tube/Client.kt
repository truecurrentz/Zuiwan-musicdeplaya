package zuiwan.tube

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.YouTubeLocale
import com.metrolist.innertubex.models.response.GetTranscriptResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import zuiwan.model.AccountProfile
import zuiwan.model.AlbumDetail
import zuiwan.model.AlbumSummary
import zuiwan.model.ArtistDetail
import zuiwan.model.ExploreFeed
import zuiwan.model.FeedItem
import zuiwan.model.FeedShelf
import zuiwan.model.HomeFeed
import zuiwan.model.MoodTile
import zuiwan.model.PlaylistDetail
import zuiwan.model.SearchPage
import zuiwan.model.SearchTab
import zuiwan.model.SessionData
import zuiwan.model.Track

class TubeClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    val http: HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(ContentEncoding) {
            gzip(0.9f)
            deflate(0.8f)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 25_000L
        }
    }

    val innerTube: InnerTube = InnerTube(http)

    fun applylocale(region: String, language: String) {
        innerTube.locale = YouTubeLocale(
            gl = region.uppercase().ifBlank { "US" },
            hl = language.lowercase().ifBlank { "en" },
        )
    }

    fun applysession(data: SessionData) {
        if (data.active) {
            innerTube.replaceSession(
                cookie = data.cookie,
                visitorData = data.visitorData.takeIf { it.isNotBlank() },
                dataSyncId = data.dataSyncId.takeIf { it.isNotBlank() },
                authUser = data.authUser.ifBlank { "0" },
                useLoginForBrowse = true,
            )
        } else {
            innerTube.replaceSession(
                cookie = null,
                visitorData = null,
                dataSyncId = null,
                authUser = "0",
                useLoginForBrowse = false,
            )
        }
    }

    suspend fun loadhome(params: String? = null, continuation: String? = null): Result<HomeFeed> =
        runCatching {
            val res = if (continuation != null) {
                innerTube.browse(YouTubeClient.WEB_REMIX, continuation = continuation)
            } else {
                innerTube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_home", params = params)
            }
            val root = parseobj(res.bodyAsText())
            TubeParser.parsehome(root)
        }

    suspend fun loadexplore(): Result<ExploreFeed> =
        runCatching {
            coroutineScope {
                val releasesTask = async { loadreleases().getOrDefault(emptyList()) }
                val moodsTask = async { loadmoods().getOrDefault(emptyList()) }
                val chartsTask = async { loadcharts().getOrDefault(emptyList()) }
                ExploreFeed(
                    releases = releasesTask.await(),
                    moods = moodsTask.await(),
                    charts = chartsTask.await(),
                )
            }
        }

    suspend fun loadreleases(): Result<List<AlbumSummary>> =
        runCatching {
            val res = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_new_releases_albums")
            val root = parseobj(res.bodyAsText())
            val sections = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
                ?.firstOrNull().obj()
                .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
                .orEmpty()
            sections.flatMap { sec ->
                val shelf = sec.obj()?.let(TubeParser::parseshelf)
                shelf?.items.orEmpty().mapNotNull { (it as? FeedItem.AlbumCard)?.album }
            }
        }

    suspend fun loadmoods(): Result<List<MoodTile>> =
        runCatching {
            val res = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_moods_and_genres")
            val root = parseobj(res.bodyAsText())
            val sections = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
                ?.firstOrNull().obj()
                .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
            TubeParser.parsemoods(sections)
        }

    suspend fun loadcharts(): Result<List<FeedShelf>> =
        runCatching {
            val res = innerTube.browse(
                YouTubeClient.WEB_REMIX,
                browseId = "FEmusic_charts",
                params = "ggMGCgQIgAQ%3D",
            )
            val root = parseobj(res.bodyAsText())
            val sections = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
                ?.firstOrNull().obj()
                .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
                .orEmpty()
            sections.mapNotNull { it.obj()?.let(TubeParser::parseshelf) }
        }

    suspend fun loadbrowse(browseId: String, params: String? = null): Result<List<FeedShelf>> =
        runCatching {
            val res = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = browseId, params = params)
            val root = parseobj(res.bodyAsText())
            val title = root.path("header", "musicHeaderRenderer", "title").runtext()
            val sections = root.patharr("contents", "singleColumnBrowseResultsRenderer", "tabs")
                ?.firstOrNull().obj()
                .patharr("tabRenderer", "content", "sectionListRenderer", "contents")
                .orEmpty()
            val shelves = sections.mapNotNull { it.obj()?.let(TubeParser::parseshelf) }
            if (shelves.size == 1 && title.isNotBlank()) {
                listOf(shelves.first().copy(title = title))
            } else {
                shelves
            }
        }

    suspend fun loadalbum(browseId: String): Result<AlbumDetail> =
        runCatching {
            val res = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = browseId)
            val root = parseobj(res.bodyAsText())
            val (album, versions) = TubeParser.parsealbum(browseId, root)
            val tracks = if (!album.playlistId.isNullOrBlank()) {
                val vlRes = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = "VL${album.playlistId}")
                val vlRoot = parseobj(vlRes.bodyAsText())
                val (initial, firstCont) = TubeParser.parsealbumtracks(vlRoot, album)
                val all = initial.toMutableList()
                var cont = firstCont
                var guard = 0
                while (cont != null && guard < 15) {
                    guard++
                    val moreRes = innerTube.browse(YouTubeClient.WEB_REMIX, continuation = cont)
                    val (moreTracks, nextCont) = TubeParser.parsealbumtracks(parseobj(moreRes.bodyAsText()), album)
                    if (moreTracks.isEmpty()) break
                    all += moreTracks
                    cont = nextCont
                }
                all
            } else {
                TubeParser.parsealbumtracks(root, album).first
            }
            val refinedAlbum = if (album.artists.isEmpty() && tracks.isNotEmpty()) {
                album.copy(artists = tracks.first().artists)
            } else {
                album
            }
            AlbumDetail(album = refinedAlbum, tracks = tracks, versions = versions)
        }

    suspend fun loadartist(browseId: String): Result<ArtistDetail> =
        runCatching {
            val res = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = browseId)
            TubeParser.parseartist(browseId, parseobj(res.bodyAsText()))
        }

    suspend fun loadplaylist(playlistId: String): Result<PlaylistDetail> =
        runCatching {
            val clean = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val res = innerTube.browse(
                YouTubeClient.WEB_REMIX,
                browseId = clean,
                setLogin = innerTube.hasSapCookieAuth(),
            )
            TubeParser.parseplaylist(playlistId.removePrefix("VL"), parseobj(res.bodyAsText()))
        }

    suspend fun loadmoreplaylist(continuation: String): Result<Pair<List<Track>, String?>> =
        runCatching {
            val res = innerTube.browse(
                YouTubeClient.WEB_REMIX,
                continuation = continuation,
                setLogin = innerTube.hasSapCookieAuth(),
            )
            TubeParser.parseplaylistmore(parseobj(res.bodyAsText()))
        }

    suspend fun suggestquery(input: String): Result<SuggestionBundle> =
        runCatching {
            if (input.isBlank()) return@runCatching SuggestionBundle()
            val res = innerTube.getSearchSuggestions(YouTubeClient.WEB_REMIX, input = input)
            TubeParser.parsesuggestions(parseobj(res.bodyAsText()))
        }

    suspend fun searchmusic(
        query: String,
        tab: SearchTab = SearchTab.ALL,
        continuation: String? = null,
    ): Result<SearchPage> =
        runCatching {
            val res = if (continuation != null) {
                innerTube.search(YouTubeClient.WEB_REMIX, continuation = continuation, setLogin = false)
            } else {
                innerTube.search(
                    YouTubeClient.WEB_REMIX,
                    query = query,
                    params = tab.filterParam,
                    setLogin = false,
                )
            }
            TubeParser.parsesearch(parseobj(res.bodyAsText()), filtered = tab != SearchTab.ALL || continuation != null)
        }

    suspend fun loadnext(
        videoId: String? = null,
        playlistId: String? = null,
        continuation: String? = null,
    ): Result<NextBundle> =
        runCatching {
            val res = innerTube.next(
                client = YouTubeClient.WEB_REMIX,
                videoId = videoId,
                playlistId = playlistId,
                continuation = continuation,
            )
            val first = TubeParser.parsenext(parseobj(res.bodyAsText()))
            if (continuation == null && first.tracks.size <= 1 && !first.automixPlaylistId.isNullOrBlank()) {
                val radioRes = innerTube.next(
                    client = YouTubeClient.WEB_REMIX,
                    playlistId = first.automixPlaylistId,
                )
                val radio = TubeParser.parsenext(parseobj(radioRes.bodyAsText()))
                first.copy(
                    tracks = (first.tracks + radio.tracks).distinctBy { it.id },
                    continuation = radio.continuation ?: first.continuation,
                )
            } else {
                first
            }
        }

    suspend fun fetchtubelyrics(videoId: String): Result<Pair<String, Boolean>> =
        runCatching {
            val transcriptResult = runCatching {
                val tr = innerTube.getTranscript(YouTubeClient.WEB, videoId).body<GetTranscriptResponse>()
                val lines = tr.actions
                    ?.firstOrNull()
                    ?.updateEngagementPanelAction
                    ?.content
                    ?.transcriptRenderer
                    ?.body
                    ?.transcriptBodyRenderer
                    ?.cueGroups
                    .orEmpty()
                    .mapNotNull { g ->
                        val cue = g.transcriptCueGroupRenderer?.cues?.firstOrNull()?.transcriptCueRenderer ?: return@mapNotNull null
                        val ms = cue.startOffsetMs ?: return@mapNotNull null
                        val txt = cue.cue?.simpleText?.trim('♪', ' ')?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        "[%02d:%02d.%03d]%s".format(ms / 60_000, (ms / 1_000) % 60, ms % 1_000, txt)
                    }
                if (lines.size >= 3) lines.joinToString("\n") else null
            }.getOrNull()

            if (!transcriptResult.isNullOrBlank()) {
                return@runCatching transcriptResult to true
            }

            val next = loadnext(videoId = videoId).getOrThrow()
            val browseId = next.lyricsBrowseId ?: error("No YouTube lyrics tab")
            val res = innerTube.browse(YouTubeClient.WEB_REMIX, browseId = browseId)
            val plain = TubeParser.parselyricsbrowse(parseobj(res.bodyAsText())) ?: error("Empty YouTube lyrics")
            plain to false
        }

    suspend fun fetchaccount(): Result<AccountProfile> =
        runCatching {
            val res = innerTube.accountMenu(YouTubeClient.WEB_REMIX)
            TubeParser.parseaccount(parseobj(res.bodyAsText())) ?: error("Account info unavailable")
        }

    suspend fun synclibrary(): Result<List<Track>> =
        runCatching {
            if (!innerTube.hasSapCookieAuth()) return@runCatching emptyList()
            val likedDetail = loadplaylist("LM").getOrNull()
            likedDetail?.tracks.orEmpty().map { it.copy(liked = true) }
        }

    suspend fun likeremote(videoId: String, liked: Boolean) {
        if (!innerTube.hasSapCookieAuth()) return
        runCatching {
            if (liked) {
                innerTube.likeVideo(YouTubeClient.WEB_REMIX, videoId)
            } else {
                innerTube.unlikeVideo(YouTubeClient.WEB_REMIX, videoId)
            }
        }
    }

    suspend fun subscriberemote(channelId: String, subscribe: Boolean) {
        if (!innerTube.hasSapCookieAuth() || channelId.isBlank()) return
        runCatching {
            if (subscribe) {
                innerTube.subscribeChannel(YouTubeClient.WEB_REMIX, channelId, "EgIIAhgA")
            } else {
                innerTube.unsubscribeChannel(YouTubeClient.WEB_REMIX, channelId, "EgIIAhgA")
            }
        }
    }

    private fun parseobj(raw: String): JsonObject =
        json.parseToJsonElement(raw).obj() ?: JsonObject(emptyMap())
}
