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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zuiwan.model.AudioSpec
import zuiwan.model.MusicProvider
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
    private val mirrors = MirrorResolver()
    private val failedClients = ConcurrentHashMap<String, Pair<Set<String>, Long>>()
    private val failedProviders = ConcurrentHashMap<String, Pair<Set<MusicProvider>, Long>>()
    private val providerPenalty = ConcurrentHashMap<MusicProvider, Pair<Int, Long>>()
    private val streamCache = ConcurrentHashMap<String, ResolvedAudio>()

    private val activeProviderFlow = MutableStateFlow(MusicProvider.YOUTUBE_FAST)
    val activeProvider: StateFlow<MusicProvider> = activeProviderFlow.asStateFlow()

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

    fun peekstream(
        videoId: String,
        preferred: MusicProvider = MusicProvider.AUTO,
    ): ResolvedAudio? {
        val hit = streamCache[videoId] ?: return null
        val now = System.currentTimeMillis()
        if (now + 15_000L >= hit.expiresAtMs) {
            streamCache.remove(videoId)
            return null
        }
        val blocked = excludedproviders(videoId, now)
        if (hit.spec.provider in blocked) {
            streamCache.remove(videoId)
            return null
        }
        if (preferred != MusicProvider.AUTO && hit.spec.provider != preferred && preferred !in blocked) {
            return null
        }
        return hit
    }

    fun dropstream(videoId: String) {
        streamCache.remove(videoId)
    }

    fun clearcached() {
        streamCache.clear()
        failedProviders.clear()
        failedClients.clear()
    }

    fun markfailed(videoId: String, clientName: String) {
        markstalled(videoId, streamCache[videoId]?.spec?.provider, clientName)
    }

    fun markstalled(
        videoId: String,
        provider: MusicProvider?,
        clientName: String?,
    ) {
        dropstream(videoId)
        val now = System.currentTimeMillis()
        if (!clientName.isNullOrBlank()) {
            failedClients.compute(videoId) { _, existing ->
                val prev = existing?.takeIf { now - it.second < 300_000L }?.first.orEmpty()
                (prev + clientName) to now
            }
        }
        if (provider != null && provider != MusicProvider.AUTO) {
            failedProviders.compute(videoId) { _, existing ->
                val prev = existing?.takeIf { now - it.second < 300_000L }?.first.orEmpty()
                (prev + provider) to now
            }
            providerPenalty.compute(provider) { _, existing ->
                val prev = existing?.takeIf { now - it.second < 600_000L }?.first ?: 0
                (prev + 2).coerceAtMost(20) to now
            }
        }
    }

    fun markhealthy(provider: MusicProvider) {
        if (provider == MusicProvider.AUTO) return
        val now = System.currentTimeMillis()
        providerPenalty.computeIfPresent(provider) { _, existing ->
            val next = (existing.first - 1).coerceAtLeast(0)
            if (next == 0) null else next to now
        }
    }

    suspend fun prewarm() {
        runCatching { extractor().prewarm() }
    }

    suspend fun extractstream(
        videoId: String,
        quality: QualityMode,
        explicit: Boolean = false,
        preferred: MusicProvider = MusicProvider.AUTO,
        allowFallback: Boolean = true,
        hint: TrackHint = TrackHint(explicit = explicit),
    ): Result<ResolvedAudio> {
        peekstream(videoId, preferred)?.let {
            activeProviderFlow.value = it.spec.provider
            return Result.success(it)
        }
        return try {
            val now = System.currentTimeMillis()
            val chain = providerchain(
                preferred = preferred,
                allowFallback = allowFallback,
                videoId = videoId,
                hasMeta = hint.title.isNotBlank(),
                now = now,
            )

            var lastErr: Throwable? = null
            for (candidate in chain) {
                val resolved = try {
                    resolveone(
                        provider = candidate,
                        videoId = videoId,
                        quality = quality,
                        hint = hint.copy(explicit = explicit || hint.explicit),
                        now = now,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastErr = e
                    null
                }
                if (resolved != null) {
                    streamCache[videoId] = resolved
                    activeProviderFlow.value = resolved.spec.provider
                    return Result.success(resolved)
                } else {
                    providerPenalty.compute(candidate) { _, existing ->
                        val prev = existing?.takeIf { now - it.second < 600_000L }?.first ?: 0
                        (prev + 1).coerceAtMost(20) to now
                    }
                }
            }

            error(lastErr?.message ?: "No playable stream resolved across music providers for $videoId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun providerchain(
        preferred: MusicProvider,
        allowFallback: Boolean,
        videoId: String,
        hasMeta: Boolean,
        now: Long,
    ): List<MusicProvider> {
        val blocked = excludedproviders(videoId, now)
        val baseOrder = listOf(
            MusicProvider.YOUTUBE_FAST,
            MusicProvider.PIPED,
            MusicProvider.JIOSAAVN,
            MusicProvider.INVIDIOUS,
            MusicProvider.MONOCHROME,
            MusicProvider.YOUTUBE_REMIX,
            MusicProvider.AUDIUS,
        )

        val eligible = baseOrder.filter { p ->
            hasMeta || p !in META_PROVIDERS || p == preferred
        }

        val ranked = eligible.sortedWith(
            compareBy<MusicProvider> { it in blocked }
                .thenBy { penaltyscore(it, now) },
        )

        if (preferred == MusicProvider.AUTO) {
            return if (allowFallback) ranked else listOfNotNull(ranked.firstOrNull())
        }

        if (!allowFallback) {
            return listOf(preferred)
        }

        val others = ranked.filterNot { it == preferred }
        return if (preferred in blocked) {
            others + preferred
        } else {
            listOf(preferred) + others
        }
    }

    private suspend fun resolveone(
        provider: MusicProvider,
        videoId: String,
        quality: QualityMode,
        hint: TrackHint,
        now: Long,
    ): ResolvedAudio? =
        when (provider) {
            MusicProvider.AUTO,
            MusicProvider.YOUTUBE_FAST,
            -> resolveyoutube(
                videoId = videoId,
                quality = quality,
                explicit = hint.explicit,
                providerTag = MusicProvider.YOUTUBE_FAST,
                overrideIds = emptyList(),
                extraExcluded = setOf("WEB_REMIX"),
                now = now,
            )

            MusicProvider.YOUTUBE_REMIX -> resolveyoutube(
                videoId = videoId,
                quality = quality,
                explicit = hint.explicit,
                providerTag = MusicProvider.YOUTUBE_REMIX,
                overrideIds = listOf("WEB_REMIX", "TVHTML5_SIMPLY", "MWEB"),
                extraExcluded = emptySet(),
                now = now,
            )

            MusicProvider.PIPED -> mirrors.resolvepiped(videoId, quality)
            MusicProvider.INVIDIOUS -> mirrors.resolveinvidious(videoId, quality)
            MusicProvider.JIOSAAVN -> mirrors.resolvesaavn(videoId, hint, quality)
            MusicProvider.MONOCHROME -> mirrors.resolvemono(videoId, hint, quality)
            MusicProvider.AUDIUS -> mirrors.resolveaudius(videoId, hint)
        }

    private suspend fun resolveyoutube(
        videoId: String,
        quality: QualityMode,
        explicit: Boolean,
        providerTag: MusicProvider,
        overrideIds: List<String>,
        extraExcluded: Set<String>,
        now: Long,
    ): ResolvedAudio? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val mappedQuality = when (quality) {
            QualityMode.HIGH -> AudioQuality.HIGH
            QualityMode.LOW -> AudioQuality.LOW
            QualityMode.AUTO -> if (cm?.isActiveNetworkMetered == true) AudioQuality.LOW else AudioQuality.AUTO
        }
        val excluded = failedClients[videoId]
            ?.takeIf { now - it.second < 300_000L }
            ?.first
            .orEmpty() + extraExcluded

        val ext = extractor()
        for (overrideId in overrideIds) {
            if (overrideId in excluded) continue
            val overrideHints = ContentHints(
                isExplicit = explicit,
                wantVideo = false,
                playbackClientOverrideId = overrideId,
            ).withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = true,
            )
            val hit = runCatching {
                ext.extract(
                    videoId = videoId,
                    hints = overrideHints,
                    excludedClients = excluded,
                    audioQuality = mappedQuality,
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                )
            }.getOrNull()
            if (hit != null) {
                return hit.toresolved(providerTag)
            }
        }

        val hints = ContentHints(isExplicit = explicit, wantVideo = false)
            .withStreamCapabilities(
                allowHls = false,
                allowSabr = false,
                allowBoundedRange = true,
            )
        val extracted = ext.extract(
            videoId = videoId,
            hints = hints,
            excludedClients = excluded,
            audioQuality = mappedQuality,
            clientPlaybackNonce = generateClientPlaybackNonce(),
        ) ?: return null
        return extracted.toresolved(providerTag)
    }

    private fun excludedproviders(videoId: String, now: Long): Set<MusicProvider> =
        failedProviders[videoId]
            ?.takeIf { now - it.second < 300_000L }
            ?.first
            .orEmpty()

    private fun penaltyscore(provider: MusicProvider, now: Long): Int =
        providerPenalty[provider]
            ?.takeIf { now - it.second < 600_000L }
            ?.first
            ?: 0

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
    private fun ExtractedStream.toresolved(providerTag: MusicProvider): ResolvedAudio {
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
            provider = providerTag,
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

        val META_PROVIDERS = setOf(
            MusicProvider.JIOSAAVN,
            MusicProvider.MONOCHROME,
            MusicProvider.AUDIUS,
        )
    }
}
