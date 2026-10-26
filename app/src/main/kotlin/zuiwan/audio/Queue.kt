package zuiwan.audio

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zuiwan.model.Track
import zuiwan.model.artistline
import zuiwan.model.trackart
import java.util.concurrent.ConcurrentHashMap

class QueueBook {
    private val catalog = ConcurrentHashMap<String, Track>()

    private val queueTitleFlow = MutableStateFlow("Queue")
    val queueTitle: StateFlow<String> = queueTitleFlow.asStateFlow()

    @Volatile
    var continuation: String? = null

    @Volatile
    var seedVideoId: String? = null

    fun register(tracks: Iterable<Track>) {
        tracks.forEach { t -> catalog[t.id] = t }
    }

    fun register(track: Track) {
        catalog[track.id] = track
    }

    fun lookup(id: String): Track? = catalog[id]

    fun setcontext(title: String, nextToken: String? = null, seedId: String? = null) {
        queueTitleFlow.value = title
        continuation = nextToken
        seedVideoId = seedId
    }

    fun mediaitem(track: Track): MediaItem {
        val art = track.trackart()
        val enriched = if (track.artwork.isBlank() && art.isNotBlank()) track.copy(artwork = art) else track
        register(enriched)
        val meta = MediaMetadata.Builder()
            .setTitle(enriched.title)
            .setArtist(enriched.artistline())
            .setAlbumTitle(enriched.album?.name)
            .setArtworkUri(art.takeIf { it.isNotBlank() }?.toUri())
            .setIsPlayable(true)
            .build()

        val localSource = enriched.localUri?.takeIf { it.isNotBlank() }
        val builder = MediaItem.Builder()
            .setMediaId(enriched.id)
            .setUri(localSource ?: "https://music.youtube.com/watch?v=${enriched.id}")
            .apply {
                if (localSource == null) {
                    setCustomCacheKey(enriched.id)
                }
            }
            .setMediaMetadata(meta)
            .setTag(enriched)

        if (enriched.trimStartMs > 0L || enriched.trimEndMs > enriched.trimStartMs) {
            val clip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(enriched.trimStartMs.coerceAtLeast(0L))
                .apply {
                    if (enriched.trimEndMs > enriched.trimStartMs) {
                        setEndPositionMs(enriched.trimEndMs)
                    }
                }
                .build()
            builder.setClippingConfiguration(clip)
        }
        return builder.build()
    }
}
