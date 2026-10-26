package zuiwan.audio

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import zuiwan.data.AlbumEntity
import zuiwan.data.ArtistEntity
import zuiwan.data.MusicDao
import zuiwan.data.toentity
import zuiwan.data.totrack
import zuiwan.model.AlbumRef
import zuiwan.model.ArtistRef
import zuiwan.model.PluginSpec
import zuiwan.model.Track
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID

class LocalManager(
    private val context: Context,
    private val dao: MusicDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val builtinPlugins: List<PluginSpec> = listOf(
        PluginSpec(
            id = "bass_surge",
            name = "Bass Surge",
            author = "Zuiwan DSP",
            version = "1.2.0",
            description = "Adds warm analog 68 Hz sub-bass weight automatically.",
            builtin = true,
            bassBoost = 0.55f,
        ),
        PluginSpec(
            id = "orbit_8d",
            name = "8D Spatial Orbit",
            author = "Zuiwan Spatial",
            version = "1.1.0",
            description = "Smooth 360-degree binaural azimuth rotation around the listener.",
            builtin = true,
            spatial8d = true,
            stereoWide = 0.35f,
        ),
        PluginSpec(
            id = "velvet_hall",
            name = "Velvet Hall Ambience",
            author = "Zuiwan Acoustics",
            version = "1.0.4",
            description = "Subtle concert studio room reflections and wider soundstage.",
            builtin = true,
            reverb = 0.36f,
            stereoWide = 0.42f,
        ),
        PluginSpec(
            id = "silence_trim",
            name = "Smart Silence Skipper",
            author = "Zuiwan Engine",
            version = "1.0.0",
            description = "Automatically trims dead air and long silent gaps between tracks.",
            builtin = true,
            skipSilence = true,
        ),
        PluginSpec(
            id = "night_shield",
            name = "Night Comfort Shield",
            author = "Zuiwan Dynamics",
            version = "1.0.2",
            description = "Gentle soft-knee dynamic limiter for comfortable late-night listening.",
            builtin = true,
            nightLimit = true,
        ),
        PluginSpec(
            id = "neon_cyber",
            name = "Cyber Neon Theme Hook",
            author = "Zuiwan Visuals",
            version = "1.0.0",
            description = "Applies neon magenta-cyan CSS variables and WebGL bloom boost.",
            builtin = true,
            css = ":root { --accent: #FF3CAC; --accent-secondary: #00F2FE; }",
        ),
    )

    suspend fun scandevice(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ALBUM_ID)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    add(MediaStore.Audio.Media.DATA)
                }
            }.toTypedArray()

            val selection = "${MediaStore.Audio.Media.DURATION} >= 4000"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            var count = 0

            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.RELATIVE_PATH
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Audio.Media.DATA
                    },
                )

                val albumArtBase = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val trackId = "local_ms_$mediaId"
                    val existing = dao.gettrack(trackId)

                    val rawName = cursor.getString(nameCol).orEmpty().substringBeforeLast(".")
                    val rawTitle = cursor.getString(titleCol).orEmpty().trim()
                        .takeIf { it.isNotBlank() && it != "<unknown>" } ?: rawName.ifBlank { "Local Track" }
                    val rawArtist = cursor.getString(artistCol).orEmpty().trim()
                        .takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Local Artist"
                    val rawAlbum = cursor.getString(albumCol).orEmpty().trim()
                        .takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Local Audio"
                    val albumId = cursor.getLong(albumIdCol)
                    val durMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val rawFolder = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                    val folder = rawFolder.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
                        .ifBlank { "Music" }

                    val contentUri = ContentUris.withAppendedId(collection, mediaId)
                    val artUri = if (albumId > 0L) {
                        ContentUris.withAppendedId(albumArtBase, albumId).toString()
                    } else {
                        extractart(contentUri, trackId)
                    }

                    val localAlbumId = "local_alb_$albumId"
                    val localArtistId = "local_art_${rawArtist.lowercase().hashCode()}"

                    val merged = Track(
                        id = trackId,
                        title = existing?.title?.takeIf { it.isNotBlank() } ?: rawTitle,
                        artists = listOf(ArtistRef(localArtistId, rawArtist)),
                        album = AlbumRef(localAlbumId, rawAlbum),
                        duration = (durMs / 1000L).toInt(),
                        artwork = existing?.artwork?.takeIf { it.isNotBlank() } ?: artUri,
                        liked = existing?.liked ?: false,
                        downloaded = true,
                        localUri = contentUri.toString(),
                        folderName = folder,
                        trimStartMs = existing?.trimStartMs ?: 0L,
                        trimEndMs = existing?.trimEndMs ?: 0L,
                    )
                    dao.puttrack(
                        merged.toentity().copy(
                            playCount = existing?.playCount ?: 0,
                            totalPlayTimeMs = existing?.totalPlayTimeMs ?: 0L,
                            lastPlayedAt = existing?.lastPlayedAt,
                            likedAt = existing?.likedAt,
                        ),
                    )
                    if (dao.getalbum(localAlbumId) == null) {
                        dao.putalbum(
                            AlbumEntity(
                                id = localAlbumId,
                                title = rawAlbum,
                                artistsJson = json.encodeToString(listOf(ArtistRef(localArtistId, rawArtist))),
                                artwork = artUri,
                                saved = true,
                            ),
                        )
                    }
                    if (dao.getartist(localArtistId) == null) {
                        dao.putartist(
                            ArtistEntity(
                                id = localArtistId,
                                name = rawArtist,
                                artwork = artUri,
                                saved = true,
                            ),
                        )
                    }
                    count++
                }
            }
            count
        }
    }

    suspend fun importuris(uris: List<Uri>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var added = 0
            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val trackId = "local_uri_${uri.toString().hashCode().toUInt().toString(16)}"
                val retriever = MediaMetadataRetriever()
                var title = ""
                var artist = ""
                var album = ""
                var durSec = 0
                var artPath = ""
                try {
                    retriever.setDataSource(context, uri)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
                    durSec = ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt()
                    val pic = retriever.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        val artDir = File(context.filesDir, "localart").apply { mkdirs() }
                        val artFile = File(artDir, "$trackId.jpg")
                        artFile.writeBytes(pic)
                        artPath = Uri.fromFile(artFile).toString()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }

                if (title.isBlank()) {
                    title = displayname(uri).substringBeforeLast(".").ifBlank { "Imported Audio" }
                }
                if (artist.isBlank()) artist = "Local Artist"
                if (album.isBlank()) album = "Imported Files"

                val existing = dao.gettrack(trackId)
                val track = Track(
                    id = trackId,
                    title = title,
                    artists = listOf(ArtistRef("local_art_${artist.lowercase().hashCode()}", artist)),
                    album = AlbumRef("local_alb_${album.lowercase().hashCode()}", album),
                    duration = durSec,
                    artwork = artPath,
                    liked = existing?.liked ?: false,
                    downloaded = true,
                    localUri = uri.toString(),
                    folderName = "Imported",
                )
                dao.puttrack(track.toentity())
                added++
            }
            added
        }
    }

    private fun extractart(uri: Uri, trackId: String): String {
        val artDir = File(context.filesDir, "localart").apply { mkdirs() }
        val cached = File(artDir, "$trackId.jpg")
        if (cached.exists() && cached.length() > 0L) {
            return Uri.fromFile(cached).toString()
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val pic = retriever.embeddedPicture
            if (pic != null && pic.isNotEmpty()) {
                cached.writeBytes(pic)
                Uri.fromFile(cached).toString()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun savemetadata(
        track: Track,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        coverUri: Uri?,
        trimStartMs: Long,
        trimEndMs: Long,
    ): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.gettrack(track.id)
            val finalArt = if (coverUri != null) {
                val artDir = File(context.filesDir, "localart").apply { mkdirs() }
                val dest = File(artDir, "cover_${track.id.hashCode().toUInt().toString(16)}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(coverUri)?.use { ins ->
                    FileOutputStream(dest).use { outs -> ins.copyTo(outs) }
                }
                Uri.fromFile(dest).toString()
            } else {
                track.artwork
            }

            val updated = track.copy(
                title = newTitle.trim().ifBlank { track.title },
                artists = listOf(
                    ArtistRef(
                        id = track.artists.firstOrNull()?.id,
                        name = newArtist.trim().ifBlank { "Unknown Artist" },
                    ),
                ),
                album = newAlbum.trim().takeIf { it.isNotBlank() }?.let {
                    AlbumRef(track.album?.id, it)
                },
                artwork = finalArt,
                trimStartMs = trimStartMs.coerceAtLeast(0L),
                trimEndMs = trimEndMs.coerceAtLeast(0L),
            )
            val entity = updated.toentity().copy(
                playCount = existing?.playCount ?: 0,
                totalPlayTimeMs = existing?.totalPlayTimeMs ?: 0L,
                lastPlayedAt = existing?.lastPlayedAt,
                likedAt = existing?.likedAt,
            )
            dao.puttrack(entity)
            dao.gettrack(track.id)?.totrack() ?: updated
        }
    }

    suspend fun exporttrim(
        track: Track,
        startMs: Long,
        endMs: Long,
    ): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val srcUriStr = track.localUri ?: error("Select a local audio file to export a standalone clip")
            val srcUri = Uri.parse(srcUriStr)
            val editsDir = File(context.filesDir, "edits").apply { mkdirs() }
            val outFile = File(editsDir, "clip_${UUID.randomUUID().toString().take(8)}.m4a")

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, srcUri, null)
                var audioTrackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        break
                    }
                }
                if (audioTrackIndex < 0) error("No audio stream found in file")
                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)

                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val dstTrack = muxer.addTrack(format)
                muxer.start()

                val startUs = (startMs.coerceAtLeast(0L)) * 1000L
                val endUs = if (endMs > startMs) endMs * 1000L else Long.MAX_VALUE
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val buf = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()

                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buf, 0)
                    if (info.size < 0) break
                    val pts = extractor.sampleTime
                    if (pts < 0L || pts > endUs) break
                    info.presentationTimeUs = (pts - startUs).coerceAtLeast(0L)
                    info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(dstTrack, buf, info)
                    extractor.advance()
                }
                runCatching { muxer.stop() }
                runCatching { muxer.release() }
            } finally {
                runCatching { extractor.release() }
            }

            if (!outFile.exists() || outFile.length() < 512L) {
                error("This format uses live non-destructive cue trimming (saved on track)")
            }

            val clipDurSec = (((endMs - startMs).coerceAtLeast(1000L)) / 1000L).toInt()
            val newTrack = track.copy(
                id = "local_clip_${UUID.randomUUID().toString().take(8)}",
                title = "${track.title} (Edited)",
                duration = clipDurSec,
                downloaded = true,
                localUri = Uri.fromFile(outFile).toString(),
                folderName = "Edited Clips",
                trimStartMs = 0L,
                trimEndMs = 0L,
            )
            dao.puttrack(newTrack.toentity())
            newTrack
        }
    }

    suspend fun copyasset(uri: Uri, prefix: String): Result<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val rawName = displayname(uri).lowercase()
            val isVideo = mime.startsWith("video/") ||
                rawName.endsWith(".mp4") ||
                rawName.endsWith(".webm") ||
                rawName.endsWith(".mkv")
            val ext = when {
                rawName.endsWith(".ttf") -> "ttf"
                rawName.endsWith(".otf") -> "otf"
                rawName.endsWith(".gif") || mime.contains("gif") -> "gif"
                rawName.endsWith(".webm") -> "webm"
                isVideo -> "mp4"
                rawName.endsWith(".png") -> "png"
                else -> "jpg"
            }
            val dir = File(context.filesDir, "custom").apply { mkdirs() }
            dir.listFiles()?.filter { it.name.startsWith("${prefix}_") }?.forEach {
                runCatching { it.delete() }
            }
            val out = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { ins ->
                FileOutputStream(out).use { outs -> ins.copyTo(outs) }
            } ?: error("Unable to read selected file")
            out.absolutePath to isVideo
        }
    }

    suspend fun readtext(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to read file")
        }
    }

    suspend fun loadplugins(): List<PluginSpec> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "plugins").apply { mkdirs() }
        val custom = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<PluginSpec>(file.readText()) }.getOrNull()
            }
            .orEmpty()
        builtinPlugins + custom
    }

    suspend fun installplugin(uri: Uri): Result<PluginSpec> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to read plugin file")
            val name = displayname(uri)
            val spec = when {
                name.endsWith(".css", ignoreCase = true) -> PluginSpec(
                    id = "user_${UUID.randomUUID().toString().take(8)}",
                    name = name.substringBeforeLast(".").ifBlank { "Custom CSS Plugin" },
                    author = "User",
                    description = "Imported CSS style module",
                    css = raw,
                )
                name.endsWith(".js", ignoreCase = true) -> PluginSpec(
                    id = "user_${UUID.randomUUID().toString().take(8)}",
                    name = name.substringBeforeLast(".").ifBlank { "Custom Visual Script" },
                    author = "User",
                    description = "Imported WebGL / visual script plugin",
                    js = raw,
                )
                else -> {
                    val parsed = json.decodeFromString<PluginSpec>(raw)
                    parsed.copy(
                        id = parsed.id.ifBlank { "user_${UUID.randomUUID().toString().take(8)}" },
                        builtin = false,
                    )
                }
            }
            val dir = File(context.filesDir, "plugins").apply { mkdirs() }
            File(dir, "${spec.id}.json").writeText(json.encodeToString(spec))
            spec
        }
    }

    suspend fun removeplugin(id: String) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "plugins")
        File(dir, "$id.json").delete()
    }

    private fun displayname(uri: Uri): String {
        var result = ""
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) result = c.getString(0).orEmpty()
            }
        }
        return result.ifBlank { uri.lastPathSegment.orEmpty() }
    }
}
