package zuiwan.tube

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import zuiwan.model.AudioSpec
import zuiwan.model.MusicProvider
import zuiwan.model.QualityMode
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class TrackHint(
    val title: String = "",
    val artist: String = "",
    val durationSec: Int = 0,
    val explicit: Boolean = false,
)

class MirrorResolver {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val preferredHost = ConcurrentHashMap<MusicProvider, String>()

    suspend fun resolvepiped(
        videoId: String,
        quality: QualityMode,
    ): ResolvedAudio? =
        racehosts(MusicProvider.PIPED, PIPED_HOSTS) { host ->
            val raw = httpget("$host/streams/$videoId") ?: return@racehosts null
            val root = parseobj(raw) ?: return@racehosts null
            val streams = root["audioStreams"]?.aslist().orEmpty()
            if (streams.isEmpty()) return@racehosts null

            data class Candidate(
                val url: String,
                val mime: String,
                val codec: String,
                val bitrate: Int,
                val itag: Int,
                val clen: Long,
                val proxied: Boolean,
            )

            val candidates = streams.mapNotNull { el ->
                val obj = el.asobj() ?: return@mapNotNull null
                val url = obj.str("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                val mime = obj.str("mimeType")?.substringBefore(";").orEmpty().ifBlank { "audio/mp4" }
                if (!mime.startsWith("audio/")) return@mapNotNull null
                val bitrate = obj.num("bitrate")?.takeIf { it > 0 } ?: 128_000
                val codec = obj.str("codec").orEmpty()
                val itag = obj.num("itag") ?: 140
                val clen = obj.longnum("contentLength") ?: -1L
                val proxied = !url.contains("googlevideo.com", ignoreCase = true)
                Candidate(url, mime, codec, bitrate, itag, clen, proxied)
            }
            if (candidates.isEmpty()) return@racehosts null

            val picked = candidates.sortedWith(
                compareByDescending<Candidate> { it.proxied }
                    .thenBy { cand ->
                        when (quality) {
                            QualityMode.LOW -> cand.bitrate
                            QualityMode.HIGH, QualityMode.AUTO -> -cand.bitrate
                        }
                    },
            ).first()

            val now = System.currentTimeMillis()
            ResolvedAudio(
                videoId = videoId,
                url = picked.url,
                headers = mapOf("User-Agent" to BROWSER_UA),
                expiresAtMs = now + 900_000L,
                spec = AudioSpec(
                    id = videoId,
                    itag = picked.itag,
                    mime = picked.mime,
                    codecs = picked.codec,
                    bitrate = picked.bitrate,
                    sampleRate = 48_000,
                    lengthBytes = picked.clen,
                    client = "PIPED",
                    provider = MusicProvider.PIPED,
                ),
                requireBoundedRange = false,
                rangeChunkBytes = 0L,
                useRangeChunks = false,
            )
        }

    suspend fun resolveinvidious(
        videoId: String,
        quality: QualityMode,
    ): ResolvedAudio? =
        racehosts(MusicProvider.INVIDIOUS, INVIDIOUS_HOSTS) { host ->
            val raw = httpget("$host/api/v1/videos/$videoId?local=true") ?: return@racehosts null
            val root = parseobj(raw) ?: return@racehosts null
            val formats = root["adaptiveFormats"]?.aslist().orEmpty()
            if (formats.isEmpty()) return@racehosts null

            data class InvFormat(
                val itag: Int,
                val mime: String,
                val codec: String,
                val bitrate: Int,
                val clen: Long,
            )

            val parsed = formats.mapNotNull { el ->
                val obj = el.asobj() ?: return@mapNotNull null
                val typeStr = obj.str("type").orEmpty()
                if (!typeStr.startsWith("audio/")) return@mapNotNull null
                val itag = obj.num("itag") ?: obj.str("itag")?.toIntOrNull() ?: return@mapNotNull null
                val mime = typeStr.substringBefore(";").ifBlank { "audio/mp4" }
                val codec = typeStr.substringAfter("codecs=\"", "").substringBefore("\"")
                val bitrate = obj.num("bitrate") ?: obj.str("bitrate")?.toIntOrNull() ?: 128_000
                val clen = obj.longnum("clen") ?: obj.str("clen")?.toLongOrNull() ?: -1L
                InvFormat(itag, mime, codec, bitrate, clen)
            }
            if (parsed.isEmpty()) return@racehosts null

            val chosen = parsed.sortedBy { fmt ->
                when (quality) {
                    QualityMode.LOW -> fmt.bitrate
                    QualityMode.HIGH, QualityMode.AUTO -> -fmt.bitrate
                }
            }.first()

            val relayUrl = "$host/latest_version?id=$videoId&itag=${chosen.itag}&local=true"
            val now = System.currentTimeMillis()
            ResolvedAudio(
                videoId = videoId,
                url = relayUrl,
                headers = mapOf("User-Agent" to BROWSER_UA),
                expiresAtMs = now + 900_000L,
                spec = AudioSpec(
                    id = videoId,
                    itag = chosen.itag,
                    mime = chosen.mime,
                    codecs = chosen.codec,
                    bitrate = chosen.bitrate,
                    sampleRate = 48_000,
                    lengthBytes = chosen.clen,
                    client = "INVIDIOUS",
                    provider = MusicProvider.INVIDIOUS,
                ),
                requireBoundedRange = false,
                rangeChunkBytes = 0L,
                useRangeChunks = false,
            )
        }

    suspend fun resolvesaavn(
        videoId: String,
        hint: TrackHint,
        quality: QualityMode,
    ): ResolvedAudio? {
        val cleanTitle = cleanmeta(hint.title)
        if (cleanTitle.isBlank()) return null
        val cleanArtist = cleanmeta(hint.artist.substringBefore(","))
        val query = "$cleanTitle $cleanArtist".trim()
        val encoded = urlenc(query)

        return racehosts(MusicProvider.JIOSAAVN, SAAVN_HOSTS) { endpoint ->
            val raw = httpget("$endpoint?query=$encoded&limit=6") ?: return@racehosts null
            val root = parseobj(raw) ?: return@racehosts null
            val dataObj = root["data"]?.asobj() ?: root
            val results = (dataObj["results"]?.aslist() ?: root["results"]?.aslist()).orEmpty()
            if (results.isEmpty()) return@racehosts null

            var bestObj: JsonObject? = null
            var bestScore = -1

            for (el in results) {
                val item = el.asobj() ?: continue
                val name = decodehtml(item.str("name") ?: item.str("title").orEmpty())
                val primaryFromObj = item["artists"]?.asobj()
                    ?.get("primary")?.aslist()
                    ?.mapNotNull { it.asobj()?.str("name") }
                    ?.joinToString(" ")
                    .orEmpty()
                val artistStr = decodehtml(
                    primaryFromObj.ifBlank {
                        item.str("primaryArtists") ?: item.str("singers").orEmpty()
                    },
                )
                val dur = item.num("duration") ?: item.str("duration")?.toIntOrNull() ?: 0
                val score = matchscore(cleanTitle, cleanArtist, hint.durationSec, name, artistStr, dur)
                if (score > bestScore) {
                    bestScore = score
                    bestObj = item
                }
            }

            if (bestObj == null || bestScore < 42) return@racehosts null
            val dlList = bestObj["downloadUrl"]?.aslist().orEmpty()
            if (dlList.isEmpty()) return@racehosts null

            data class SaavnUrl(val kbps: Int, val url: String)
            val urls = dlList.mapNotNull { el ->
                val o = el.asobj() ?: return@mapNotNull null
                val u = (o.str("url") ?: o.str("link"))?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                val qStr = o.str("quality").orEmpty()
                val kbps = qStr.filter { it.isDigit() }.toIntOrNull() ?: 160
                SaavnUrl(kbps, u)
            }
            if (urls.isEmpty()) return@racehosts null

            val picked = when (quality) {
                QualityMode.LOW -> urls.minByOrNull { abs(it.kbps - 96) } ?: urls.first()
                QualityMode.AUTO -> urls.maxByOrNull { it.kbps } ?: urls.last()
                QualityMode.HIGH -> urls.maxByOrNull { it.kbps } ?: urls.last()
            }

            val now = System.currentTimeMillis()
            ResolvedAudio(
                videoId = videoId,
                url = picked.url,
                headers = mapOf("User-Agent" to BROWSER_UA),
                expiresAtMs = now + 3_600_000L,
                spec = AudioSpec(
                    id = videoId,
                    itag = picked.kbps,
                    mime = "audio/mp4",
                    codecs = "mp4a.40.2",
                    bitrate = picked.kbps * 1_000,
                    sampleRate = 44_100,
                    lengthBytes = -1L,
                    client = "JIOSAAVN",
                    provider = MusicProvider.JIOSAAVN,
                ),
                requireBoundedRange = false,
                rangeChunkBytes = 0L,
                useRangeChunks = false,
            )
        }
    }

    suspend fun resolvemono(
        videoId: String,
        hint: TrackHint,
        quality: QualityMode,
    ): ResolvedAudio? {
        val cleanTitle = cleanmeta(hint.title)
        if (cleanTitle.isBlank()) return null
        val cleanArtist = cleanmeta(hint.artist.substringBefore(","))
        val encoded = urlenc("$cleanTitle $cleanArtist".trim())
        val tier = if (quality == QualityMode.LOW) "LOW" else "HIGH"

        return racehosts(MusicProvider.MONOCHROME, MONO_HOSTS) { host ->
            val searchRaw = httpget("$host/search/?s=$encoded") ?: return@racehosts null
            val root = parseobj(searchRaw) ?: return@racehosts null
            val dataNode = root["data"]?.asobj() ?: root
            val items = (dataNode["items"]?.aslist()
                ?: dataNode["tracks"]?.asobj()?.get("items")?.aslist()
                ?: root["items"]?.aslist()).orEmpty()
            if (items.isEmpty()) return@racehosts null

            var bestId: String? = null
            var bestScore = -1

            for (el in items) {
                val item = el.asobj() ?: continue
                val idVal = item.str("id") ?: item.longnum("id")?.toString() ?: continue
                val titleStr = item.str("title").orEmpty()
                val artistSingle = item["artist"]?.asobj()?.str("name").orEmpty()
                val artistMulti = item["artists"]?.aslist()
                    ?.mapNotNull { it.asobj()?.str("name") }
                    ?.joinToString(" ")
                    .orEmpty()
                val dur = item.num("duration") ?: 0
                val score = matchscore(
                    cleanTitle,
                    cleanArtist,
                    hint.durationSec,
                    titleStr,
                    artistMulti.ifBlank { artistSingle },
                    dur,
                )
                if (score > bestScore) {
                    bestScore = score
                    bestId = idVal
                }
            }

            if (bestId == null || bestScore < 44) return@racehosts null
            val trackRaw = httpget("$host/track/?id=$bestId&quality=$tier") ?: return@racehosts null
            val trackNode = parseobj(trackRaw) ?: return@racehosts null
            val inner = trackNode["data"]?.asobj() ?: trackNode

            val directUrl = inner.str("OriginalTrackUrl")
                ?: trackNode.str("OriginalTrackUrl")
                ?: inner.str("url")
            val manifestB64 = inner.str("manifest") ?: trackNode.str("manifest")
            val parsedManifest = manifestB64?.let { parseb64url(it) }

            val streamUrl = directUrl?.takeIf { it.startsWith("http") }
                ?: parsedManifest?.first
                ?: return@racehosts null
            val mime = parsedManifest?.second ?: "audio/mp4"
            val kbps = if (quality == QualityMode.LOW) 96_000 else 320_000

            val now = System.currentTimeMillis()
            ResolvedAudio(
                videoId = videoId,
                url = streamUrl,
                headers = mapOf("User-Agent" to BROWSER_UA),
                expiresAtMs = now + 1_800_000L,
                spec = AudioSpec(
                    id = videoId,
                    itag = 320,
                    mime = mime,
                    codecs = if (mime.contains("flac")) "flac" else "mp4a.40.2",
                    bitrate = kbps,
                    sampleRate = 44_100,
                    lengthBytes = -1L,
                    client = "MONOCHROME",
                    provider = MusicProvider.MONOCHROME,
                ),
                requireBoundedRange = false,
                rangeChunkBytes = 0L,
                useRangeChunks = false,
            )
        }
    }

    suspend fun resolveaudius(
        videoId: String,
        hint: TrackHint,
    ): ResolvedAudio? {
        val cleanTitle = cleanmeta(hint.title)
        if (cleanTitle.isBlank()) return null
        val cleanArtist = cleanmeta(hint.artist.substringBefore(","))
        val encoded = urlenc("$cleanTitle $cleanArtist".trim())

        return racehosts(MusicProvider.AUDIUS, AUDIUS_HOSTS) { host ->
            val raw = httpget("$host/v1/tracks/search?query=$encoded&app_name=Zuiwan") ?: return@racehosts null
            val root = parseobj(raw) ?: return@racehosts null
            val items = root["data"]?.aslist().orEmpty()
            if (items.isEmpty()) return@racehosts null

            var bestId: String? = null
            var bestScore = -1

            for (el in items) {
                val item = el.asobj() ?: continue
                if (item["is_streamable"]?.jsonPrimitive?.booleanOrNull == false) continue
                val idVal = item.str("id") ?: continue
                val titleStr = item.str("title").orEmpty()
                val userStr = item["user"]?.asobj()?.str("name").orEmpty()
                val dur = item.num("duration") ?: 0
                val score = matchscore(cleanTitle, cleanArtist, hint.durationSec, titleStr, userStr, dur)
                if (score > bestScore) {
                    bestScore = score
                    bestId = idVal
                }
            }

            if (bestId == null || bestScore < 46) return@racehosts null
            val streamUrl = "$host/v1/tracks/$bestId/stream?app_name=Zuiwan"
            val now = System.currentTimeMillis()
            ResolvedAudio(
                videoId = videoId,
                url = streamUrl,
                headers = mapOf("User-Agent" to BROWSER_UA),
                expiresAtMs = now + 1_800_000L,
                spec = AudioSpec(
                    id = videoId,
                    itag = 320,
                    mime = "audio/mpeg",
                    codecs = "mp3",
                    bitrate = 320_000,
                    sampleRate = 48_000,
                    lengthBytes = -1L,
                    client = "AUDIUS",
                    provider = MusicProvider.AUDIUS,
                ),
                requireBoundedRange = false,
                rangeChunkBytes = 0L,
                useRangeChunks = false,
            )
        }
    }

    private suspend fun racehosts(
        provider: MusicProvider,
        hosts: List<String>,
        block: suspend (String) -> ResolvedAudio?,
    ): ResolvedAudio? = withTimeoutOrNull(9_500L) {
        val fav = preferredHost[provider]
        val ordered = if (fav != null && fav in hosts) {
            listOf(fav) + hosts.filterNot { it == fav }
        } else {
            hosts
        }
        coroutineScope {
            val resultChan = Channel<Pair<String, ResolvedAudio>?>(ordered.size)
            val childJob = SupervisorJob(coroutineContext[Job])
            ordered.forEachIndexed { idx, host ->
                launch(Dispatchers.IO + childJob) {
                    var sent = false
                    try {
                        if (idx > 0) delay(idx * 240L)
                        val res = try {
                            block(host)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        resultChan.trySend(res?.let { host to it })
                        sent = true
                    } finally {
                        if (!sent) {
                            resultChan.trySend(null)
                        }
                    }
                }
            }

            var winner: ResolvedAudio? = null
            repeat(ordered.size) {
                val msg = resultChan.receive()
                if (msg != null) {
                    preferredHost[provider] = msg.first
                    winner = msg.second
                    childJob.cancelChildren()
                    childJob.cancel()
                    return@coroutineScope winner
                }
            }
            childJob.cancel()
            winner
        }
    }

    private suspend fun httpget(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        }
    }

    private fun parseb64url(encoded: String): Pair<String, String>? =
        runCatching {
            val decoded = String(Base64.decode(encoded.trim(), Base64.DEFAULT), Charsets.UTF_8)
            val obj = parseobj(decoded) ?: return null
            val firstUrl = obj["urls"]?.aslist()
                ?.firstNotNullOfOrNull { it.jsonPrimitive.contentOrNull }
                ?.takeIf { it.startsWith("http") }
                ?: return null
            val mime = obj.str("mimeType")?.substringBefore(";").orEmpty().ifBlank { "audio/mp4" }
            firstUrl to mime
        }.getOrNull()

    private fun cleanmeta(raw: String): String =
        raw.replace(PAREN_NOISE, " ")
            .replace(BRACKET_NOISE, " ")
            .replace(NON_WORD, " ")
            .replace(MULTI_SPACE, " ")
            .trim()

    private fun matchscore(
        wantTitle: String,
        wantArtist: String,
        wantDurSec: Int,
        candTitleRaw: String,
        candArtistRaw: String,
        candDurSec: Int,
    ): Int {
        if (wantDurSec > 20 && candDurSec > 0 && abs(wantDurSec - candDurSec) > 20) {
            return -100
        }
        val wt = cleanmeta(wantTitle).lowercase()
        val ct = cleanmeta(candTitleRaw).lowercase()
        if (wt.isBlank() || ct.isBlank()) return -100

        var score = 0
        if (wt == ct) {
            score += 55
        } else if (ct.contains(wt) || wt.contains(ct)) {
            score += 40
        } else {
            val wtWords = wt.split(" ").filter { it.length >= 2 }.toSet()
            val ctWords = ct.split(" ").filter { it.length >= 2 }.toSet()
            if (wtWords.isNotEmpty() && ctWords.isNotEmpty()) {
                val overlap = wtWords.intersect(ctWords).size
                score += (overlap * 42) / wtWords.size
            }
        }

        val wa = cleanmeta(wantArtist).lowercase()
        val ca = cleanmeta(candArtistRaw).lowercase()
        if (wa.isNotBlank() && ca.isNotBlank()) {
            if (ca.contains(wa) || wa.contains(ca)) {
                score += 30
            } else {
                val waWords = wa.split(" ").filter { it.length >= 2 }.toSet()
                val caWords = ca.split(" ").filter { it.length >= 2 }.toSet()
                if (waWords.intersect(caWords).isNotEmpty()) {
                    score += 18
                } else {
                    score -= 12
                }
            }
        }

        if (wantDurSec > 0 && candDurSec > 0) {
            val diff = abs(wantDurSec - candDurSec)
            score += when {
                diff <= 3 -> 20
                diff <= 8 -> 12
                diff <= 15 -> 5
                else -> -10
            }
        }
        return score
    }

    private fun decodehtml(s: String): String =
        s.replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    private fun urlenc(s: String): String =
        URLEncoder.encode(s, "UTF-8")

    private fun parseobj(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()

    private fun JsonElement.asobj(): JsonObject? =
        runCatching { jsonObject }.getOrNull()

    private fun JsonElement.aslist(): JsonArray? =
        runCatching { jsonArray }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun JsonObject.num(key: String): Int? =
        this[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }

    private fun JsonObject.longnum(key: String): Long? =
        this[key]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }

    private companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"

        val PAREN_NOISE =
            Regex("\\((?i:official|lyric|lyrics|audio|video|visualizer|hd|hq|4k|mv|music video)[^)]*\\)")
        val BRACKET_NOISE =
            Regex("\\[(?i:official|lyric|lyrics|audio|video|visualizer|hd|hq|4k|mv|music video)[^]]*\\]")
        val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")
        val MULTI_SPACE = Regex("\\s+")

        val PIPED_HOSTS = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.nosebs.ru",
            "https://pipedapi.adminforge.de",
        )

        val INVIDIOUS_HOSTS = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.privacyredirect.com",
            "https://yewtu.be",
        )

        val SAAVN_HOSTS = listOf(
            "https://saavn.dev/api/search/songs",
            "https://saavn.sumit.co/api/search/songs",
            "https://jiosaavn-api-privatecvc2.vercel.app/search/songs",
        )

        val MONO_HOSTS = listOf(
            "https://triton.squid.wtf",
            "https://wolf.qqdl.site",
            "https://maus.qqdl.site",
            "https://hund.qqdl.site",
        )

        val AUDIUS_HOSTS = listOf(
            "https://discoveryprovider.audius.co",
            "https://discoveryprovider2.audius.co",
        )
    }
}
