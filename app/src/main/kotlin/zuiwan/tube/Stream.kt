package zuiwan.tube

import android.content.Context
import android.net.ConnectivityManager
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zuiwan.model.AudioSpec
import zuiwan.model.QualityMode
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime

data class ResolvedAudio(
    val videoId: String,
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val spec: AudioSpec,
    val requireBoundedRange: Boolean,
    val rangeChunkBytes: Long,
    val useRangeChunks: Boolean,
)

class StreamEngine(
    private val context: Context,
    private val innerTubeProvider: () -> InnerTube,
) {
    private val lock = Mutex()
    private val tokenMint = TokenMint(context)
    private val failedClients = ConcurrentHashMap<String, Pair<Set<String>, Long>>()
    private val streamCache = ConcurrentHashMap<String, ResolvedAudio>()

    @Volatile
    private var cachedGen = -1L

    @Volatile
    private var cachedExtractor: InnerTubeExtractor? = null

    @Volatile
    private var cachedCipher: YouTubeCipherService? = null

    private val configRepo = object : PlayerConfigRepository {
        private val prefs = context.getSharedPreferences("zuiwan_cipher", Context.MODE_PRIVATE)
        override val enabled: Boolean = true
        override val sourceUrl: String = CONFIG_URL
        override val defaultSourceUrl: String = CONFIG_URL
        override var cachedJson: String
            get() = prefs.getString("json", "").orEmpty()
            set(value) = prefs.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = prefs.getLong("at", 0L)
            set(value) = prefs.edit().putLong("at", value).apply()
        override var cachedSourceUrl: String
            get() = prefs.getString("src", "").orEmpty()
            set(value) = prefs.edit().putString("src", value).apply()
        override var cachedEtag: String
            get() = prefs.getString("etag", "").orEmpty()
            set(value) = prefs.edit().putString("etag", value).apply()
    }

    fun peekstream(videoId: String): ResolvedAudio? {
        val hit = streamCache[videoId] ?: return null
        if (System.currentTimeMillis() + 15_000L >= hit.expiresAtMs) {
            streamCache.remove(videoId)
            return null
        }
        return hit
    }

    fun dropstream(videoId: String) {
        streamCache.remove(videoId)
    }

    fun markfailed(videoId: String, clientName: String) {
        dropstream(videoId)
        val now = System.currentTimeMillis()
        failedClients.compute(videoId) { _, existing ->
            val prev = existing?.takeIf { now - it.second < 300_000L }?.first.orEmpty()
            (prev + clientName) to now
        }
    }

    suspend fun prewarm() {
        runCatching { extractor().prewarm() }
    }

    suspend fun extractstream(
        videoId: String,
        quality: QualityMode,
        explicit: Boolean = false,
    ): Result<ResolvedAudio> {
        peekstream(videoId)?.let { return Result.success(it) }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val mappedQuality = when (quality) {
                QualityMode.HIGH -> AudioQuality.HIGH
                QualityMode.LOW -> AudioQuality.LOW
                QualityMode.AUTO -> if (cm?.isActiveNetworkMetered == true) AudioQuality.LOW else AudioQuality.AUTO
            }
            val hints = ContentHints(isExplicit = explicit, wantVideo = false)
                .withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    allowBoundedRange = true,
                )
            val now = System.currentTimeMillis()
            val excluded = failedClients[videoId]
                ?.takeIf { now - it.second < 300_000L }
                ?.first
                .orEmpty()

            val extracted = extractor().extract(
                videoId = videoId,
                hints = hints,
                excludedClients = excluded,
                audioQuality = mappedQuality,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            ) ?: error("No playable stream resolved for $videoId")

            val resolved = extracted.toresolved()
            streamCache[videoId] = resolved
            Result.success(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractor(): InnerTubeExtractor {
        val tube = innerTubeProvider()
        val gen = tube.sessionSnapshot().generation
        val current = cachedExtractor
        if (current != null && cachedGen == gen) return current

        return lock.withLock {
            val latest = cachedExtractor
            if (latest != null && cachedGen == gen) return@withLock latest

            val store = RemotePlayerConfigStore(tube.httpClient, configRepo)
            val cipher = YouTubeCipherService(tube.httpClient, store)
            val parser = YtConfigParserImpl(
                httpClient = tube.httpClient,
                innerTube = tube,
                remotePlayerConfigStore = store,
                cipherService = cipher,
            ).withfallback()

            InnerTubeExtractor(
                configParser = parser,
                cipherService = cipher,
                innerTube = tube,
                tokenProvider = tokenMint,
            ).also {
                cachedCipher = cipher
                cachedExtractor = it
                cachedGen = gen
            }
        }
    }

    private fun YtConfigParser.withfallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
                try {
                    this@withfallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withfallback.fetchEmbeddedConfig(videoId, false)
                }
        }

    @OptIn(ExperimentalTime::class)
    private fun ExtractedStream.toresolved(): ResolvedAudio {
        val nowMs = System.currentTimeMillis()
        val expMs = expiresAt?.toEpochMilliseconds() ?: (nowMs + 300_000L)
        val spec = AudioSpec(
            id = videoId,
            itag = itag,
            mime = mimeType?.substringBefore(";").orEmpty().ifBlank { "audio/webm" },
            codecs = codecs.orEmpty(),
            bitrate = bitrate ?: 128_000,
            sampleRate = sampleRate ?: 48_000,
            lengthBytes = contentLengthBytes ?: -1L,
            loudnessDb = loudnessDb,
            perceptualDb = perceptualLoudnessDb,
            client = clientName,
        )
        return ResolvedAudio(
            videoId = videoId,
            url = audioUrl,
            headers = headers,
            expiresAtMs = expMs.coerceAtLeast(nowMs + 30_000L),
            spec = spec,
            requireBoundedRange = requireBoundedRange,
            rangeChunkBytes = rangeChunkSizeBytes.takeIf { it > 0L } ?: 1_048_576L,
            useRangeChunks = useRangeChunks,
        )
    }

    private companion object {
        const val CONFIG_URL =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    }
}
