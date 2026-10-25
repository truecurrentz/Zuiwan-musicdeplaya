package zuiwan.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zuiwan.data.MusicDao
import zuiwan.data.toentity
import zuiwan.model.AlbumRef
import zuiwan.model.ArtistRef
import zuiwan.model.FeedItem
import zuiwan.model.MatchResult
import zuiwan.model.MatchStage
import zuiwan.model.SearchTab
import zuiwan.model.ShazamReq
import zuiwan.model.ShazamRes
import zuiwan.model.Track
import zuiwan.model.upscaleart
import zuiwan.tube.TubeClient
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt
import kotlin.random.Random

class MusicRecognizer(
    private val context: Context,
    private val dao: MusicDao,
    private val tube: TubeClient,
) {
    private val sampleRate = 44_100
    private val channelCfg = AudioFormat.CHANNEL_IN_MONO
    private val pcmFormat = AudioFormat.ENCODING_PCM_16BIT
    private val earlyDurationMs = 5_000L
    private val fullDurationMs = 11_500L

    private val stageFlow = MutableStateFlow<MatchStage>(MatchStage.Ready)
    val stage: StateFlow<MatchStage> = stageFlow.asStateFlow()

    private val levelFlow = MutableStateFlow(0f)
    val level: StateFlow<Float> = levelFlow.asStateFlow()

    private var activeJob: Job? = null

    private val userAgents = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
        "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
        "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
        "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)",
    )

    private val zones = listOf(
        "America/New_York",
        "America/Los_Angeles",
        "Europe/London",
        "Europe/Paris",
        "Asia/Tokyo",
    )

    fun hasperm(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun stoplisten() {
        activeJob?.cancel()
        activeJob = null
        levelFlow.value = 0f
        stageFlow.value = MatchStage.Ready
    }

    fun resetmatch() {
        if (stageFlow.value !is MatchStage.Listening && stageFlow.value !is MatchStage.Processing) {
            levelFlow.value = 0f
            stageFlow.value = MatchStage.Ready
        }
    }

    @SuppressLint("MissingPermission")
    fun startlisten(scope: CoroutineScope) {
        if (!hasperm()) {
            stageFlow.value = MatchStage.Error("Microphone permission is required to recognize music")
            return
        }
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            stageFlow.value = MatchStage.Listening
            levelFlow.value = 0.08f
            try {
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelCfg, pcmFormat)
                    .coerceAtLeast(4096)
                val primaryRec = runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        channelCfg,
                        pcmFormat,
                        minBuf * 2,
                    )
                }.getOrNull()
                val recorder = if (primaryRec?.state == AudioRecord.STATE_INITIALIZED) {
                    primaryRec
                } else {
                    primaryRec?.release()
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelCfg,
                        pcmFormat,
                        minBuf * 2,
                    )
                }

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    stageFlow.value = MatchStage.Error("Unable to access microphone")
                    levelFlow.value = 0f
                    return@launch
                }

                val out = ByteArrayOutputStream(sampleRate * 2 * 12)
                val chunk = ByteArray(minBuf)
                val startedAt = System.currentTimeMillis()
                var earlyProbe: Deferred<MatchResult?>? = null
                var earlyHit: MatchResult? = null

                try {
                    recorder.startRecording()
                    while (isActive) {
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (elapsed >= fullDurationMs) break

                        val read = recorder.read(chunk, 0, chunk.size)
                        if (read > 0) {
                            out.write(chunk, 0, read)
                            levelFlow.value = computelevel(chunk, read)
                        }

                        if (earlyProbe == null && elapsed >= earlyDurationMs && out.size() > sampleRate * 6) {
                            val snap = out.toByteArray()
                            earlyProbe = async { probesample(snap).getOrNull() }
                        }

                        if (earlyProbe?.isCompleted == true) {
                            val hit = earlyProbe.await()
                            if (hit != null) {
                                earlyHit = hit
                                break
                            }
                            earlyProbe = null
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    runCatching { recorder.release() }
                    levelFlow.value = 0f
                }

                stageFlow.value = MatchStage.Processing

                val rawMatch = earlyHit ?: earlyProbe?.await() ?: run {
                    val fullBytes = out.toByteArray()
                    probesample(fullBytes).getOrElse { err ->
                        val msg = err.message.orEmpty()
                        stageFlow.value = if (msg.contains("No match", ignoreCase = true)) {
                            MatchStage.NoMatch("Could not identify this song. Hold closer and try again.")
                        } else {
                            MatchStage.Error(msg.ifBlank { "Recognition failed" })
                        }
                        return@launch
                    }
                }

                if (rawMatch == null) {
                    stageFlow.value = MatchStage.NoMatch("Could not identify this song. Hold closer and try again.")
                    return@launch
                }

                val enriched = resolvetrack(rawMatch)
                dao.putrecognized(enriched.toentity())
                enriched.matchedTrack?.let { t ->
                    if (dao.gettrack(t.id) == null) {
                        dao.puttrack(t.toentity())
                    }
                }
                stageFlow.value = MatchStage.Matched(enriched)
            } catch (c: CancellationException) {
                levelFlow.value = 0f
                throw c
            } catch (e: Exception) {
                levelFlow.value = 0f
                stageFlow.value = MatchStage.Error(e.message ?: "Recognition error")
            }
        }
    }

    private fun computelevel(buf: ByteArray, len: Int): Float {
        val shorts = len / 2
        if (shorts <= 0) return 0f
        val sb = ByteBuffer.wrap(buf, 0, shorts * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSq = 0.0
        for (i in 0 until shorts) {
            val v = sb.get(i).toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / shorts)
        return (rms / 6500.0).toFloat().coerceIn(0.05f, 1f)
    }

    private suspend fun probesample(raw44k: ByteArray): Result<MatchResult?> =
        runCatching {
            val resampled = AudioPrint.resamplepcm(raw44k, sampleRate, AudioPrint.targetRate)
            if (resampled.size < AudioPrint.targetRate * 2) return@runCatching null
            val sig = AudioPrint.makesig(resampled)
            val durationMs = (resampled.size / 2) * 1_000L / AudioPrint.targetRate
            queryshazam(sig, durationMs)
        }

    private suspend fun queryshazam(signature: String, sampleMs: Long): MatchResult? =
        withContext(Dispatchers.IO) {
            var lastErr: Exception? = null
            for (attempt in 0 until 2) {
                try {
                    val nowSec = System.currentTimeMillis() / 1_000L
                    val u1 = UUID.randomUUID().toString().uppercase()
                    val u2 = UUID.randomUUID().toString()
                    val body = ShazamReq(
                        geolocation = ShazamReq.Geo(
                            altitude = Random.nextDouble() * 300.0 + 100.0,
                            latitude = Random.nextDouble() * 140.0 - 70.0,
                            longitude = Random.nextDouble() * 320.0 - 160.0,
                        ),
                        signature = ShazamReq.Sig(
                            samplems = sampleMs,
                            timestamp = nowSec,
                            uri = signature,
                        ),
                        timestamp = nowSec,
                        timezone = zones.random(),
                    )
                    val res = tube.http.post("https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$u1/$u2") {
                        parameter("sync", "true")
                        parameter("webv3", "true")
                        parameter("sampling", "true")
                        parameter("connected", "")
                        parameter("shazamapiversion", "v3")
                        parameter("sharehub", "true")
                        parameter("video", "v3")
                        header("User-Agent", userAgents.random())
                        header("Content-Language", "en_US")
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                    if (!res.status.isSuccess()) {
                        val code = res.status.value
                        if (code == 429 && attempt == 0) {
                            delay(1_500L)
                            continue
                        }
                        if (code == 404) return@withContext null
                        error("Recognition server returned $code")
                    }
                    val parsed = res.body<ShazamRes>()
                    return@withContext parseshazam(parsed)
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt == 0) delay(900L)
                }
            }
            if (lastErr != null) throw lastErr
            null
        }

    private fun parseshazam(res: ShazamRes): MatchResult? {
        val tr = res.track ?: return null
        val title = tr.title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val artist = tr.subtitle?.trim().orEmpty().ifBlank { "Unknown Artist" }
        val songSec = tr.sections?.firstOrNull { it?.type == "SONG" }
        val meta = songSec?.metadata.orEmpty()
        val album = meta.firstOrNull { it?.title.equals("Album", ignoreCase = true) }?.text
        val released = meta.firstOrNull { it?.title.equals("Released", ignoreCase = true) }?.text
        val rawArt = tr.images?.coverarthq ?: tr.images?.coverart ?: tr.images?.background ?: ""
        val ytUri = tr.hub?.options
            ?.firstOrNull { it?.type?.contains("video", ignoreCase = true) == true }
            ?.actions?.firstOrNull()?.uri
        val ytId = ytUri?.let { u ->
            when {
                u.contains("v=") -> u.substringAfterLast("v=").substringBefore("&").takeIf { it.isNotBlank() }
                else -> u.substringAfterLast("/").substringBefore("?").takeIf { it.length == 11 }
            }
        }
        val key = tr.key?.takeIf { it.isNotBlank() }
            ?: res.tagid?.takeIf { it.isNotBlank() }
            ?: "${title.lowercase()}_${artist.lowercase()}"

        return MatchResult(
            id = key,
            title = title,
            artist = artist,
            album = album,
            artwork = upscaleart(rawArt, 1080),
            genre = tr.genres?.primary,
            releaseDate = released,
            videoId = ytId,
        )
    }

    suspend fun resolvetrack(match: MatchResult): MatchResult =
        withContext(Dispatchers.IO) {
            val query = "${match.title} ${match.artist}".trim()
            val searched = tube.searchmusic(query, SearchTab.SONGS).getOrNull()
                ?.items
                ?.firstNotNullOfOrNull { (it as? FeedItem.SongCard)?.track }

            val byVideo = if (searched == null && !match.videoId.isNullOrBlank()) {
                tube.loadnext(videoId = match.videoId).getOrNull()?.tracks?.firstOrNull()
            } else {
                null
            }

            val resolved = searched ?: byVideo ?: match.videoId?.let { vid ->
                Track(
                    id = vid,
                    title = match.title,
                    artists = listOf(ArtistRef(null, match.artist)),
                    album = match.album?.let { AlbumRef(null, it) },
                    artwork = match.artwork.ifBlank { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" },
                )
            }

            val finalTrack = resolved?.let { t ->
                t.copy(
                    artwork = match.artwork.ifBlank { t.artwork },
                )
            }

            match.copy(
                videoId = finalTrack?.id ?: match.videoId,
                artwork = match.artwork.ifBlank { finalTrack?.artwork.orEmpty() },
                matchedTrack = finalTrack,
            )
        }
}
