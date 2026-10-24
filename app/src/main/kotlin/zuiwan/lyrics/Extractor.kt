package zuiwan.lyrics

import zuiwan.data.MusicDao
import zuiwan.data.toentity
import zuiwan.data.topayload
import zuiwan.model.LyricPayload
import zuiwan.model.Track
import zuiwan.tube.TubeClient

class LyricsExtractor(
    private val dao: MusicDao,
    private val tube: TubeClient,
) {
    private val lrclib = LrclibSource(tube.http)
    private val better = BetterSource(tube.http)
    private val kugou = KugouSource(tube.http)

    suspend fun extractlyrics(
        track: Track,
        romanize: Boolean = true,
        forceRefresh: Boolean = false,
    ): LyricPayload {
        if (!forceRefresh) {
            val cached = dao.getlyrics(track.id)?.topayload()
            if (cached != null && cached.raw.isNotBlank()) {
                return LyricParser.parselyrics(cached.raw, cached.source, romanize)
            }
        }

        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val album = track.album?.name
        val dur = track.duration

        better.fetchbetter(track.title, artist, album, dur).getOrNull()?.let { raw ->
            val parsed = LyricParser.parselyrics(raw, "BetterLyrics", romanize)
            if (parsed.lines.isNotEmpty()) {
                persist(track.id, parsed)
                return parsed
            }
        }

        lrclib.fetchlrclib(track.title, artist, album, dur).getOrNull()?.let { raw ->
            val parsed = LyricParser.parselyrics(raw, "LrcLib", romanize)
            if (parsed.lines.isNotEmpty()) {
                persist(track.id, parsed)
                return parsed
            }
        }

        kugou.fetchkugou(track.title, artist, dur).getOrNull()?.let { raw ->
            val parsed = LyricParser.parselyrics(raw, "KuGou", romanize)
            if (parsed.lines.isNotEmpty()) {
                persist(track.id, parsed)
                return parsed
            }
        }

        tube.tubelyrics(track.id).getOrNull()?.let { (raw, _) ->
            val parsed = LyricParser.parselyrics(raw, "YouTube Music", romanize)
            if (parsed.lines.isNotEmpty()) {
                persist(track.id, parsed)
                return parsed
            }
        }

        return LyricPayload(source = "None")
    }

    suspend fun searchlyrics(query: String, romanize: Boolean = true): List<LyricPayload> {
        val entries = lrclib.searchlrclib(query).getOrDefault(emptyList())
        return entries.take(10).mapNotNull { entry ->
            val raw = entry.syncedLyrics?.takeIf { it.isNotBlank() }
                ?: entry.plainLyrics?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val label = listOfNotNull(entry.trackName, entry.artistName).joinToString(" - ").ifBlank { "LrcLib" }
            LyricParser.parselyrics(raw, label, romanize)
        }
    }

    suspend fun applylyrics(trackId: String, payload: LyricPayload) {
        persist(trackId, payload)
    }

    private suspend fun persist(trackId: String, payload: LyricPayload) {
        dao.putlyrics(payload.toentity(trackId))
    }
}
