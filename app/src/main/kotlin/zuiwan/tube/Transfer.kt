package zuiwan.tube

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import zuiwan.data.MusicDao
import zuiwan.data.PlaylistEntity
import zuiwan.data.PlaylistTrackEntity
import zuiwan.data.toentity
import zuiwan.model.AlbumRef
import zuiwan.model.ArtistRef
import zuiwan.model.FeedItem
import zuiwan.model.SearchTab
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.upscaleart
import java.io.File
import java.util.UUID

enum class ExportKind(
    val label: String,
    val ext: String,
    val mime: String,
) {
    CSV("CSV", "csv", "text/csv"),
    M3U("M3U", "m3u", "audio/x-mpegurl"),
    TXT("Text", "txt", "text/plain"),
}

data class DraftSong(
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val duration: Int = 0,
    val videoId: String? = null,
    val artwork: String = "",
)

data class CsvMap(
    val headers: List<String>,
    val rows: List<List<String>>,
    val hasHeader: Boolean,
    val titleCol: Int,
    val artistCol: Int,
    val albumCol: Int = -1,
    val idCol: Int = -1,
)

data class ImportBundle(
    val title: String,
    val songs: List<DraftSong>,
    val csv: CsvMap? = null,
)

class PlaylistTransfer(
    private val context: Context,
    private val dao: MusicDao,
    private val tube: TubeClient,
    private val sync: CloudSync,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val audioExts = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac", "webm")

    suspend fun inspecturi(uri: Uri): Result<ImportBundle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = readname(uri)
                val baseTitle = fileName
                    ?.substringBeforeLast('.')
                    ?.replace('_', ' ')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Imported Playlist"
                val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }?.removePrefix("\uFEFF") ?: error("Unable to read file")

                when (ext) {
                    "m3u", "m3u8" -> parsem3u(raw, baseTitle)
                    "csv", "tsv" -> parsecsv(raw, baseTitle, forceTab = ext == "tsv")
                    "json" -> parsejson(raw, baseTitle)
                    "pls" -> parsepls(raw, baseTitle)
                    "xspf", "xml" -> parsexspf(raw, baseTitle)
                    else -> inspecttext(raw, baseTitle).getOrThrow()
                }
            }
        }

    suspend fun inspecttext(rawInput: String, fallbackName: String = "Imported Playlist"): Result<ImportBundle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = rawInput.removePrefix("\uFEFF").trim()
                check(raw.isNotBlank()) { "Playlist content is empty" }

                val singleLine = !raw.contains('\n') && !raw.contains('\r')
                if (singleLine) {
                    parseurl(raw)?.let { return@runCatching it }
                }

                when {
                    raw.startsWith("#EXTM3U", ignoreCase = true) || raw.contains("#EXTINF:", ignoreCase = true) ->
                        parsem3u(raw, fallbackName)
                    (raw.startsWith("{") && raw.endsWith("}")) || (raw.startsWith("[") && raw.endsWith("]")) ->
                        parsejson(raw, fallbackName)
                    raw.startsWith("[playlist]", ignoreCase = true) ->
                        parsepls(raw, fallbackName)
                    raw.contains("<trackList", ignoreCase = true) && raw.contains("<track>", ignoreCase = true) ->
                        parsexspf(raw, fallbackName)
                    looksgrid(raw) ->
                        parsecsv(raw, fallbackName, forceTab = false)
                    else ->
                        parseplain(raw, fallbackName)
                }
            }
        }

    fun applycsv(bundle: ImportBundle, map: CsvMap): ImportBundle {
        val dataRows = if (map.hasHeader && map.rows.size > 1) map.rows.drop(1) else map.rows
        val songs = dataRows.mapNotNull { cols ->
            val rawTitle = cols.getOrNull(map.titleCol)?.trim().orEmpty()
            val rawArtist = if (map.artistCol >= 0) cols.getOrNull(map.artistCol)?.trim().orEmpty() else ""
            val rawAlbum = if (map.albumCol >= 0) cols.getOrNull(map.albumCol)?.trim()?.takeIf { it.isNotBlank() } else null
            val rawIdCell = if (map.idCol >= 0) cols.getOrNull(map.idCol)?.trim().orEmpty() else ""
            val vid = extractvid(rawIdCell)
            if (rawTitle.isBlank() && vid == null) return@mapNotNull null
            DraftSong(
                title = rawTitle.ifBlank { vid.orEmpty() },
                artist = rawArtist.replace(';', ','),
                album = rawAlbum,
                videoId = vid,
            )
        }
        return bundle.copy(songs = songs, csv = map)
    }

    suspend fun runimport(
        title: String,
        songs: List<DraftSong>,
        syncYoutube: Boolean,
        targetPlaylistId: String? = null,
        onStep: (title: String, subtitle: String, progress: Float?) -> Unit = { _, _, _ -> },
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(songs.isNotEmpty()) { "No songs found to import" }
            val cleanTitle = title.trim().ifBlank { "Imported Playlist" }

            onStep("Importing $cleanTitle", "Preparing ${songs.size} tracks", 0.04f)

            val existingTarget = targetPlaylistId?.let { dao.getplaylist(it) }
            val remoteId = when {
                existingTarget != null -> existingTarget.remoteId
                syncYoutube && tube.isauthed() -> {
                    onStep("Importing $cleanTitle", "Creating playlist on YouTube Music", 0.08f)
                    tube.createremote(cleanTitle).getOrNull()
                }
                else -> null
            }

            val localId = existingTarget?.id
                ?: if (remoteId != null) "yt_$remoteId" else "local_${UUID.randomUUID()}"

            if (existingTarget == null) {
                dao.putplaylist(
                    PlaylistEntity(
                        id = localId,
                        title = cleanTitle,
                        author = if (remoteId != null) "YouTube Music" else "You",
                        artwork = songs.firstOrNull { it.artwork.isNotBlank() }?.artwork.orEmpty(),
                        local = remoteId == null,
                        remoteId = remoteId,
                        editable = true,
                    ),
                )
            }

            val gate = Semaphore(4)
            var finishedCount = 0
            val resolved = coroutineScope {
                songs.map { draft ->
                    async {
                        gate.withPermit {
                            val track = resolvedraft(draft)
                            synchronized(this@PlaylistTransfer) {
                                finishedCount++
                                val frac = 0.10f + 0.76f * (finishedCount.toFloat() / songs.size.coerceAtLeast(1))
                                val label = draft.title.ifBlank { track?.title ?: "Track" }
                                onStep(
                                    "Importing $cleanTitle",
                                    "Matched $finishedCount of ${songs.size} · $label",
                                    frac,
                                )
                            }
                            track
                        }
                    }
                }.awaitAll()
            }.filterNotNull()

            check(resolved.isNotEmpty()) { "Could not match any tracks on YouTube Music" }

            var pos = (dao.maxpos(localId) ?: -1) + 1
            val existingIds = dao.playlisttracks(localId).map { it.id }.toHashSet()
            var firstArt = dao.getplaylist(localId)?.artwork.orEmpty()

            resolved.forEach { track ->
                val saved = dao.gettrack(track.id)
                if (saved == null) {
                    dao.puttrack(track.toentity())
                }
                if (firstArt.isBlank() && track.artwork.isNotBlank()) {
                    firstArt = upscaleart(track.artwork)
                }
                if (track.id !in existingIds) {
                    existingIds += track.id
                    dao.linktrack(
                        PlaylistTrackEntity(
                            playlistId = localId,
                            trackId = track.id,
                            position = pos++,
                        ),
                    )
                }
            }

            dao.getplaylist(localId)?.let { pl ->
                if (pl.artwork.isBlank() && firstArt.isNotBlank()) {
                    dao.putplaylist(pl.copy(artwork = firstArt))
                }
            }

            if (!remoteId.isNullOrBlank() && tube.isauthed()) {
                resolved.forEachIndexed { idx, track ->
                    val frac = 0.86f + 0.12f * ((idx + 1).toFloat() / resolved.size.coerceAtLeast(1))
                    onStep("Syncing $cleanTitle", "Uploading ${idx + 1} of ${resolved.size} to YouTube Music", frac)
                    tube.addremote(remoteId, track.id)
                }
                runCatching { sync.syncone(localId) }
            }

            onStep("Playlist Imported", "${resolved.size} songs added to $cleanTitle", 1.0f)
            localId
        }
    }

    fun formattext(title: String, tracks: List<Track>, kind: ExportKind): String =
        when (kind) {
            ExportKind.CSV -> buildString {
                append('\uFEFF')
                appendLine("Title,Artist,Album,YouTube Video ID")
                tracks.forEach { t ->
                    append(escfield(t.title))
                    append(',')
                    append(escfield(t.artistline()))
                    append(',')
                    append(escfield(t.album?.name.orEmpty()))
                    append(',')
                    appendLine(escfield(t.id))
                }
            }

            ExportKind.M3U -> buildString {
                appendLine("#EXTM3U")
                appendLine("#PLAYLIST:${title.replace('\n', ' ')}")
                tracks.forEach { t ->
                    val dur = if (t.duration > 0) t.duration else -1
                    appendLine("#EXTINF:$dur,${t.artistline()} - ${t.title}")
                    appendLine("#YTM:${t.id}")
                    appendLine("https://music.youtube.com/watch?v=${t.id}")
                }
            }

            ExportKind.TXT -> buildString {
                appendLine("$title (${tracks.size} songs)")
                appendLine()
                tracks.forEachIndexed { idx, t ->
                    appendLine("${idx + 1}. ${t.artistline()} - ${t.title} — https://music.youtube.com/watch?v=${t.id}")
                }
            }
        }

    suspend fun writedoc(
        uri: Uri,
        title: String,
        tracks: List<Track>,
        kind: ExportKind,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val body = formattext(title, tracks, kind)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot write to selected file")
            tracks.size
        }
    }

    suspend fun savedocs(
        title: String,
        tracks: List<Track>,
        kind: ExportKind,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safe = cleanname(title)
            val fullName = "$safe.${kind.ext}"
            val body = formattext(title, tracks, kind).toByteArray(Charsets.UTF_8)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                    put(MediaStore.MediaColumns.MIME_TYPE, kind.mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/Zuiwan")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    values,
                ) ?: error("Unable to create export file")
                context.contentResolver.openOutputStream(uri)?.use { it.write(body) }
                    ?: error("Unable to write export file")
                "Documents/Zuiwan/$fullName"
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Zuiwan")
                dir.mkdirs()
                val file = File(dir, fullName)
                file.writeBytes(body)
                file.name
            }
        }
    }

    suspend fun sharefile(
        title: String,
        tracks: List<Track>,
        kind: ExportKind,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val safe = cleanname(title)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "$safe.${kind.ext}")
            file.writeText(formattext(title, tracks, kind), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = kind.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Export $title").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    private suspend fun resolvedraft(draft: DraftSong): Track? {
        val vid = draft.videoId?.takeIf { it.length == 11 }
        if (vid != null && draft.title.isNotBlank() && draft.title != vid && draft.artist.isNotBlank()) {
            return Track(
                id = vid,
                title = draft.title,
                artists = draft.artist.split(',').map { it.trim() }.filter { it.isNotBlank() }.map { ArtistRef(null, it) }
                    .ifEmpty { listOf(ArtistRef(null, draft.artist)) },
                album = draft.album?.takeIf { it.isNotBlank() }?.let { AlbumRef(null, it) },
                duration = draft.duration,
                artwork = draft.artwork.ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" },
            )
        }
        if (vid != null) {
            val byId = tube.loadnext(videoId = vid).getOrNull()?.tracks?.firstOrNull { it.id == vid }
                ?: tube.loadnext(videoId = vid).getOrNull()?.tracks?.firstOrNull()
            if (byId != null) return byId
            if (draft.title.isNotBlank()) {
                return Track(
                    id = vid,
                    title = draft.title,
                    artists = if (draft.artist.isNotBlank()) listOf(ArtistRef(null, draft.artist)) else emptyList(),
                    album = draft.album?.let { AlbumRef(null, it) },
                    duration = draft.duration,
                    artwork = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                )
            }
        }
        val query = listOf(draft.title, draft.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isBlank()) return null

        val hit = tube.searchmusic(query, SearchTab.SONGS).getOrNull()
            ?.items
            ?.firstNotNullOfOrNull { (it as? FeedItem.SongCard)?.track }
        if (hit != null) return hit

        return tube.searchmusic(query, SearchTab.VIDEOS).getOrNull()
            ?.items
            ?.firstNotNullOfOrNull { (it as? FeedItem.SongCard)?.track }
    }

    private suspend fun parseurl(url: String): ImportBundle? {
        if (url.contains("open.spotify.com/playlist/", ignoreCase = true)) {
            val spId = url.substringAfter("playlist/").substringBefore('?').substringBefore('/').trim()
            if (spId.isNotBlank()) {
                return spotifylist(spId)
            }
        }

        val listParam = when {
            url.contains("list=") -> url.substringAfter("list=").substringBefore('&').substringBefore('#').trim()
            url.startsWith("VL") && url.length > 6 -> url.removePrefix("VL")
            (url.startsWith("PL") || url.startsWith("OLAK5uy_") || url.startsWith("RDCLAK5uy_")) && !url.contains(' ') -> url
            else -> null
        }

        if (!listParam.isNullOrBlank()) {
            val detail = tube.fullplaylist(listParam, maxPages = 30).getOrThrow()
            val drafts = detail.tracks.map { t ->
                DraftSong(
                    title = t.title,
                    artist = t.artistline(),
                    album = t.album?.name,
                    duration = t.duration,
                    videoId = t.id,
                    artwork = t.artwork,
                )
            }
            return ImportBundle(
                title = detail.playlist.title.ifBlank { "YouTube Playlist" },
                songs = drafts,
            )
        }
        return null
    }

    private suspend fun spotifylist(playlistId: String): ImportBundle {
        val html = tube.http.get("https://open.spotify.com/embed/playlist/$playlistId") {
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
        }.bodyAsText()

        val nextJson = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1)
        if (!nextJson.isNullOrBlank()) {
            val root = runCatching { json.parseToJsonElement(nextJson) as? JsonObject }.getOrNull()
            val entity = root
                ?.let { (it["props"] as? JsonObject)?.get("pageProps") as? JsonObject }
                ?.let { (it["state"] as? JsonObject)?.get("data") as? JsonObject }
                ?.get("entity") as? JsonObject
            if (entity != null) {
                val plName = (entity["name"] as? JsonPrimitive)?.contentOrNull
                    ?: (entity["title"] as? JsonPrimitive)?.contentOrNull
                    ?: "Spotify Playlist"
                val trackArr = entity["trackList"] as? JsonArray
                val songs = trackArr.orEmpty().mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val t = (o["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    val a = (o["subtitle"] as? JsonPrimitive)?.contentOrNull?.replace('\u00A0', ' ')?.trim().orEmpty()
                    if (t.isBlank()) null else DraftSong(title = t, artist = a)
                }
                if (songs.isNotEmpty()) {
                    return ImportBundle(title = plName, songs = songs)
                }
            }
        }
        error("Unable to read public Spotify playlist. Export it as CSV or paste its tracklist.")
    }

    private fun parsem3u(raw: String, fallbackName: String): ImportBundle {
        var playlistTitle = fallbackName
        var pendingArtist = ""
        var pendingTitle = ""
        var pendingDur = 0
        var pendingId: String? = null
        val songs = mutableListOf<DraftSong>()

        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    playlistTitle = line.substringAfter(':').trim().ifBlank { playlistTitle }
                }
                line.startsWith("#YTM:", ignoreCase = true) -> {
                    pendingId = line.substringAfter(':').trim().takeIf { it.length == 11 }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val info = line.substringAfter(':')
                    pendingDur = info.substringBefore(',').trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val meta = info.substringAfter(',', "").trim()
                    val parts = splitsong(meta)
                    pendingArtist = parts.first
                    pendingTitle = parts.second
                }
                line.startsWith("#") -> Unit
                else -> {
                    val vid = pendingId ?: extractvid(line)
                    if (pendingTitle.isNotBlank()) {
                        songs += DraftSong(
                            title = pendingTitle,
                            artist = pendingArtist,
                            duration = pendingDur,
                            videoId = vid,
                        )
                    } else if (vid != null) {
                        songs += DraftSong(title = vid, videoId = vid)
                    } else {
                        val filePart = line.substringAfterLast('/').substringAfterLast('\\')
                        val noExt = filePart.substringBeforeLast('.', filePart)
                            .replace(Regex("""^\d{1,3}[\s._-]+"""), "")
                            .trim()
                        if (noExt.isNotBlank()) {
                            val (art, ttl) = splitsong(noExt)
                            songs += DraftSong(title = ttl, artist = art)
                        }
                    }
                    pendingArtist = ""
                    pendingTitle = ""
                    pendingDur = 0
                    pendingId = null
                }
            }
        }
        if (pendingTitle.isNotBlank()) {
            songs += DraftSong(title = pendingTitle, artist = pendingArtist, duration = pendingDur, videoId = pendingId)
        }
        return ImportBundle(title = playlistTitle, songs = songs)
    }

    private fun parsecsv(raw: String, fallbackName: String, forceTab: Boolean): ImportBundle {
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val delim = when {
            forceTab || firstLine.count { it == '\t' } >= 2 -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }
        val rows = parsegrid(raw, delim).filter { row -> row.any { it.isNotBlank() } }
        check(rows.isNotEmpty()) { "CSV file is empty" }

        val first = rows.first().map { it.trim() }
        val lower = first.map { it.lowercase() }

        val headerKeywords = listOf("title", "track", "song", "name", "artist", "creator", "album", "video", "id", "uri", "url")
        val hasHeader = lower.any { col -> headerKeywords.any { kw -> col.contains(kw) } }

        val titleIdx = lower.indexOfFirst {
            it == "track name" || it == "title" || it == "song" || it == "track" || it == "song name" || it == "name"
        }.takeIf { it >= 0 } ?: lower.indexOfFirst { it.contains("title") || it.contains("track") || it.contains("song") }
            .takeIf { it >= 0 } ?: 0

        val artistIdx = lower.indexOfFirst {
            it == "artist name(s)" || it == "artist" || it == "artists" || it == "artist name" || it == "creator"
        }.takeIf { it >= 0 } ?: lower.indexOfFirst { it.contains("artist") || it.contains("author") }
            .takeIf { it >= 0 } ?: (if (first.size > 1 && titleIdx != 1) 1 else -1)

        val albumIdx = lower.indexOfFirst {
            it == "album name" || it == "album"
        }.takeIf { it >= 0 } ?: lower.indexOfFirst { it.contains("album") }

        val idIdx = lower.indexOfFirst {
            it.contains("youtube") || it == "video id" || it == "videoid" || it == "id" || it == "url"
        }

        val headers = if (hasHeader) {
            first.mapIndexed { idx, h -> h.ifBlank { "Column ${idx + 1}" } }
        } else {
            List(first.size) { idx -> "Column ${idx + 1}" }
        }

        val map = CsvMap(
            headers = headers,
            rows = rows,
            hasHeader = hasHeader,
            titleCol = titleIdx.coerceIn(0, (headers.size - 1).coerceAtLeast(0)),
            artistCol = artistIdx,
            albumCol = albumIdx,
            idCol = idIdx,
        )
        return applycsv(ImportBundle(title = fallbackName, songs = emptyList(), csv = map), map)
    }

    private fun parsejson(raw: String, fallbackName: String): ImportBundle {
        val elem = json.parseToJsonElement(raw)
        var detectedTitle = fallbackName
        val songs = mutableListOf<DraftSong>()

        fun pullobj(o: JsonObject) {
            val nestedTrack = o["track"] as? JsonObject
            val src = nestedTrack ?: o

            val title = listOf("title", "name", "trackName", "song", "songName")
                .firstNotNullOfOrNull { (src[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()

            val artist = listOf("artist", "artistName", "uploaderName", "author", "creator", "subtitle")
                .firstNotNullOfOrNull { (src[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                ?: (src["artists"] as? JsonArray)?.mapNotNull { a ->
                    when (a) {
                        is JsonPrimitive -> a.contentOrNull
                        is JsonObject -> (a["name"] as? JsonPrimitive)?.contentOrNull
                        else -> null
                    }
                }?.joinToString(", ").orEmpty()

            val album = listOf("album", "albumName")
                .firstNotNullOfOrNull { (src[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                ?: ((src["album"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull

            val idRaw = listOf("videoId", "youtubeId", "id", "url", "uri")
                .firstNotNullOfOrNull { (src[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            val vid = extractvid(idRaw)

            if (title.isNotBlank() || vid != null) {
                songs += DraftSong(
                    title = title.ifBlank { vid.orEmpty() },
                    artist = artist,
                    album = album,
                    videoId = vid,
                )
            }
        }

        fun walk(el: JsonElement) {
            when (el) {
                is JsonArray -> el.forEach(::walk)
                is JsonObject -> {
                    val plTitle = listOf("name", "title", "playlistName")
                        .firstNotNullOfOrNull { (el[it] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                    val container = listOf("tracks", "items", "songs", "relatedStreams", "streams", "playlists")
                        .firstNotNullOfOrNull { el[it] as? JsonArray }
                    if (container != null) {
                        if (plTitle != null && detectedTitle == fallbackName) detectedTitle = plTitle
                        container.forEach(::walk)
                    } else {
                        pullobj(el)
                    }
                }
                else -> Unit
            }
        }

        walk(elem)
        return ImportBundle(title = detectedTitle, songs = songs)
    }

    private fun parsepls(raw: String, fallbackName: String): ImportBundle {
        val titles = LinkedHashMap<Int, String>()
        val files = LinkedHashMap<Int, String>()
        raw.lineSequence().map { it.trim() }.forEach { line ->
            Regex("""^Title(\d+)=(.+)$""", RegexOption.IGNORE_CASE).find(line)?.let { m ->
                titles[m.groupValues[1].toInt()] = m.groupValues[2].trim()
            }
            Regex("""^File(\d+)=(.+)$""", RegexOption.IGNORE_CASE).find(line)?.let { m ->
                files[m.groupValues[1].toInt()] = m.groupValues[2].trim()
            }
        }
        val keys = (titles.keys + files.keys).sorted()
        val songs = keys.mapNotNull { idx ->
            val t = titles[idx].orEmpty()
            val f = files[idx].orEmpty()
            val vid = extractvid(f)
            if (t.isNotBlank()) {
                val (art, song) = splitsong(t)
                DraftSong(title = song, artist = art, videoId = vid)
            } else if (vid != null) {
                DraftSong(title = vid, videoId = vid)
            } else {
                null
            }
        }
        return ImportBundle(title = fallbackName, songs = songs)
    }

    private fun parsexspf(raw: String, fallbackName: String): ImportBundle {
        val plTitle = Regex("""<playlist[^>]*>.*?<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName
        val songs = Regex("""<track>(.*?)</track>""", RegexOption.DOT_MATCHES_ALL).findAll(raw).mapNotNull { m ->
            val block = m.groupValues[1]
            val title = Regex("""<title>(.*?)</title>""").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val artist = Regex("""<creator>(.*?)</creator>""").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val album = Regex("""<album>(.*?)</album>""").find(block)?.groupValues?.getOrNull(1)?.trim()
            val loc = Regex("""<location>(.*?)</location>""").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val vid = extractvid(loc)
            if (title.isBlank() && vid == null) null else DraftSong(title = title.ifBlank { vid.orEmpty() }, artist = artist, album = album, videoId = vid)
        }.toList()
        return ImportBundle(title = plTitle, songs = songs)
    }

    private fun parseplain(raw: String, fallbackName: String): ImportBundle {
        val songs = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .mapNotNull { line ->
                val vid = extractvid(line)
                val cleaned = line
                    .replace(Regex("""https?://\S+"""), "")
                    .replace(Regex("""^\s*\d{1,4}[.)\-:]\s+"""), "")
                    .trim(' ', '-', '—', '–', '|', '•')
                if (cleaned.isBlank() && vid != null) {
                    DraftSong(title = vid, videoId = vid)
                } else if (cleaned.isNotBlank()) {
                    val noExt = if (audioExts.contains(cleaned.substringAfterLast('.', "").lowercase())) {
                        cleaned.substringBeforeLast('.')
                    } else {
                        cleaned
                    }
                    val (art, ttl) = splitsong(noExt)
                    DraftSong(title = ttl, artist = art, videoId = vid)
                } else {
                    null
                }
            }
            .toList()
        return ImportBundle(title = fallbackName, songs = songs)
    }

    private fun splitsong(raw: String): Pair<String, String> {
        val seps = listOf(" - ", " — ", " – ", " ~ ")
        for (sep in seps) {
            val idx = raw.indexOf(sep)
            if (idx > 0) {
                val left = raw.substring(0, idx).trim()
                val right = raw.substring(idx + sep.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) return left to right
            }
        }
        val byMatch = Regex("""^["“]?(.+?)["”]?\s+by\s+(.+)$""", RegexOption.IGNORE_CASE).find(raw)
        if (byMatch != null) {
            return byMatch.groupValues[2].trim() to byMatch.groupValues[1].trim()
        }
        return "" to raw.trim()
    }

    private fun looksgrid(raw: String): Boolean {
        val sample = raw.lineSequence().filter { it.isNotBlank() }.take(4).toList()
        if (sample.size < 2) return false
        val firstCommas = sample.first().count { it == ',' }
        val firstTabs = sample.first().count { it == '\t' }
        val firstSemis = sample.first().count { it == ';' }
        return (firstCommas >= 1 && sample.all { it.count { c -> c == ',' } >= 1 }) ||
            (firstTabs >= 1 && sample.all { it.count { c -> c == '\t' } >= 1 }) ||
            (firstSemis >= 1 && sample.all { it.count { c -> c == ';' } >= 1 })
    }

    private fun parsegrid(raw: String, delim: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < raw.length && raw[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delim && !inQuotes -> {
                    currentRow.add(cell.toString())
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < raw.length && raw[i + 1] == '\n') i++
                    currentRow.add(cell.toString())
                    cell.clear()
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            rows.add(currentRow)
        }
        return rows
    }

    private fun extractvid(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (Regex("""^[A-Za-z0-9_-]{11}$""").matches(trimmed)) return trimmed
        Regex("""(?:v=|youtu\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})""").find(trimmed)?.let {
            return it.groupValues[1]
        }
        return null
    }

    private fun readname(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun cleanname(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "Zuiwan_Playlist" }
            .take(80)

    private fun escfield(value: String): String {
        val clean = value.replace("\r\n", " ").replace('\n', ' ')
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}
