package zuiwan.audio

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import zuiwan.data.MusicDao
import zuiwan.data.toentity
import zuiwan.data.tospec
import zuiwan.dsp.NormalizerProcessor
import zuiwan.model.AudioSpec
import zuiwan.model.QualityMode
import zuiwan.model.Track
import zuiwan.tube.ResolvedAudio
import zuiwan.tube.StreamEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@UnstableApi
class AudioVault(
    context: Context,
    private val dao: MusicDao,
    private val streamEngine: StreamEngine,
    private val qualityProvider: () -> QualityMode,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbProvider = StandaloneDatabaseProvider(appContext)

    val streamCache: SimpleCache = SimpleCache(
        File(appContext.filesDir, "streamcache"),
        LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L),
        dbProvider,
    )

    val downloadCache: SimpleCache = SimpleCache(
        File(appContext.filesDir, "downloads"),
        NoOpCacheEvictor(),
        dbProvider,
    )

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val networkFactory = OkHttpDataSource.Factory(okHttp)

    private val activeSpecFlow = MutableStateFlow<AudioSpec?>(null)
    val activeSpec: StateFlow<AudioSpec?> = activeSpecFlow.asStateFlow()

    private val progressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = progressMap.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    private fun chainedcache(enableWriteStream: Boolean): DataSource.Factory {
        val streamLayer = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(networkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .apply {
                if (!enableWriteStream) setCacheWriteDataSinkFactory(null)
            }

        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamLayer)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun playbacksource(normalizer: NormalizerProcessor): DataSource.Factory =
        ResolvingDataSource.Factory(chainedcache(enableWriteStream = true)) { spec ->
            val mediaId = spec.key ?: spec.uri.lastPathSegment ?: error("Missing track id")
            val stored = runBlocking(Dispatchers.IO) { dao.getformat(mediaId)?.tospec() }
            if (stored != null) {
                normalizer.setloudness(stored.loudnessDb, stored.perceptualDb)
                activeSpecFlow.value = stored
            }

            val knownLen = stored?.lengthBytes?.takeIf { it > 0L }
                ?: ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId)).takeIf { it > 0L }
            val probeLen = when {
                spec.length >= 0L -> spec.length
                knownLen != null -> (knownLen - spec.position).coerceAtLeast(1L)
                else -> 524_288L
            }

            if (downloadCache.isCached(mediaId, spec.position, probeLen) ||
                streamCache.isCached(mediaId, spec.position, probeLen)
            ) {
                return@Factory spec
            }

            val resolved = runBlocking(Dispatchers.IO) {
                val entity = dao.gettrack(mediaId)
                streamEngine.extractstream(
                    videoId = mediaId,
                    quality = qualityProvider(),
                    explicit = entity?.explicit ?: false,
                )
            }.getOrElse { err ->
                throw PlaybackException(
                    err.message ?: "Stream extraction failed",
                    err,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )
            }

            normalizer.setloudness(resolved.spec.loudnessDb, resolved.spec.perceptualDb)
            activeSpecFlow.value = resolved.spec
            scope.launch {
                dao.putformat(resolved.spec.toentity())
            }
            spec.withresolved(resolved)
        }

    fun downloadtrack(track: Track) {
        if (jobs.containsKey(track.id)) return
        val job = scope.launch {
            try {
                progressMap.update { it + (track.id to 0.01f) }
                if (dao.gettrack(track.id) == null) {
                    dao.puttrack(track.toentity())
                }
                val resolved = streamEngine.extractstream(
                    videoId = track.id,
                    quality = QualityMode.HIGH,
                    explicit = track.explicit,
                ).getOrThrow()

                dao.putformat(resolved.spec.toentity())

                val dlSourceFactory = ResolvingDataSource.Factory(
                    CacheDataSource.Factory()
                        .setCache(downloadCache)
                        .setUpstreamDataSourceFactory(networkFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
                ) { spec ->
                    spec.withresolved(resolved)
                }

                val cacheDs = (dlSourceFactory.createDataSource() as ResolvingDataSource)
                val innerCacheDs = CacheDataSource.Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(
                        ResolvingDataSource.Factory(networkFactory) { s -> s.withresolved(resolved) },
                    )
                    .createDataSource()

                val totalBytes = resolved.spec.lengthBytes.takeIf { it > 0L } ?: C.LENGTH_UNSET.toLong()
                val reqSpec = DataSpec.Builder()
                    .setUri("https://music.youtube.com/watch?v=${track.id}".toUri())
                    .setKey(track.id)
                    .setPosition(0L)
                    .setLength(totalBytes)
                    .build()

                val writer = CacheWriter(
                    innerCacheDs,
                    reqSpec,
                    ByteArray(64 * 1024),
                ) { requestLength, bytesCached, _ ->
                    val denom = requestLength.takeIf { it > 0L } ?: resolved.spec.lengthBytes
                    if (denom > 0L) {
                        val frac = (bytesCached.toFloat() / denom.toFloat()).coerceIn(0f, 0.99f)
                        progressMap.update { it + (track.id to frac) }
                    }
                }
                cacheDs.close()
                writer.cache()
                dao.setdownloaded(track.id, true, System.currentTimeMillis())
                progressMap.update { it - track.id }
            } catch (e: CancellationException) {
                downloadCache.removeResource(track.id)
                progressMap.update { it - track.id }
                throw e
            } catch (_: Exception) {
                downloadCache.removeResource(track.id)
                progressMap.update { it - track.id }
            } finally {
                jobs.remove(track.id)
            }
        }
        jobs[track.id] = job
    }

    fun canceldownload(trackId: String) {
        jobs.remove(trackId)?.cancel()
        progressMap.update { it - trackId }
    }

    fun removedownload(trackId: String) {
        canceldownload(trackId)
        scope.launch {
            runCatching { downloadCache.removeResource(trackId) }
            dao.setdownloaded(trackId, false, null)
        }
    }

    fun clearcache() {
        scope.launch {
            val keys = streamCache.keys.toList()
            keys.forEach { runCatching { streamCache.removeResource(it) } }
        }
    }

    fun cacheusage(): Pair<Long, Long> =
        streamCache.cacheSpace to downloadCache.cacheSpace
}

private fun DataSpec.withresolved(stream: ResolvedAudio): DataSpec {
    val resolved = withUri(stream.url.toUri())
        .withRequestHeaders(httpRequestHeaders + stream.headers)
    if ((!stream.requireBoundedRange && !stream.useRangeChunks) || stream.rangeChunkBytes <= 0L) {
        return resolved
    }
    val bounded = if (length == C.LENGTH_UNSET.toLong()) {
        stream.rangeChunkBytes
    } else {
        minOf(length, stream.rangeChunkBytes)
    }
    return resolved.subrange(0, bounded)
}
