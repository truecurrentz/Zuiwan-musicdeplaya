package zuiwan.audio

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zuiwan.model.Track
import zuiwan.model.artistline
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

    fun tomediaitem(track: Track): MediaItem {
        register(track)
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistline())
            .setAlbumTitle(track.album?.name)
            .setArtworkUri(track.artwork.takeIf { it.isNotBlank() }?.toUri())
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri("https://music.youtube.com/watch?v=${track.id}")
            .setCustomCacheKey(track.id)
            .setMediaMetadata(meta)
            .setTag(track)
            .build()
    }
}
