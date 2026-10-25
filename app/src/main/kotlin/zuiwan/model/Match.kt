package zuiwan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class MatchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String = "",
    val genre: String? = null,
    val releaseDate: String? = null,
    val videoId: String? = null,
    val matchedTrack: Track? = null,
    val recognizedAt: Long = System.currentTimeMillis(),
)

sealed interface MatchStage {
    data object Ready : MatchStage
    data object Listening : MatchStage
    data object Processing : MatchStage
    data object Cancelled : MatchStage
    data class Matched(val result: MatchResult) : MatchStage
    data class NoMatch(val message: String) : MatchStage
    data class Error(val message: String) : MatchStage
}

@Serializable
data class ShazamReq(
    @SerialName("geolocation")
    val geolocation: Geo,
    @SerialName("signature")
    val signature: Sig,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("timezone")
    val timezone: String,
) {
    @Serializable
    data class Geo(
        @SerialName("altitude")
        val altitude: Double,
        @SerialName("latitude")
        val latitude: Double,
        @SerialName("longitude")
        val longitude: Double,
    )

    @Serializable
    data class Sig(
        @SerialName("samplems")
        val samplems: Long,
        @SerialName("timestamp")
        val timestamp: Long,
        @SerialName("uri")
        val uri: String,
    )
}

@Serializable
data class ShazamRes(
    @SerialName("tagid")
    val tagid: String? = null,
    @SerialName("track")
    val track: HitTrack? = null,
) {
    @Serializable
    data class HitTrack(
        @SerialName("key")
        val key: String? = null,
        @SerialName("title")
        val title: String? = null,
        @SerialName("subtitle")
        val subtitle: String? = null,
        @SerialName("images")
        val images: HitImages? = null,
        @SerialName("hub")
        val hub: HitHub? = null,
        @SerialName("sections")
        val sections: List<HitSection?>? = null,
        @SerialName("genres")
        val genres: HitGenres? = null,
        @SerialName("isrc")
        val isrc: String? = null,
    )

    @Serializable
    data class HitImages(
        @SerialName("coverart")
        val coverart: String? = null,
        @SerialName("coverarthq")
        val coverarthq: String? = null,
        @SerialName("background")
        val background: String? = null,
    )

    @Serializable
    data class HitHub(
        @SerialName("options")
        val options: List<HitOption?>? = null,
    )

    @Serializable
    data class HitOption(
        @SerialName("type")
        val type: String? = null,
        @SerialName("actions")
        val actions: List<HitAction?>? = null,
    )

    @Serializable
    data class HitAction(
        @SerialName("uri")
        val uri: String? = null,
    )

    @Serializable
    data class HitSection(
        @SerialName("type")
        val type: String? = null,
        @SerialName("metadata")
        val metadata: List<HitMeta?>? = null,
    )

    @Serializable
    data class HitMeta(
        @SerialName("title")
        val title: String? = null,
        @SerialName("text")
        val text: String? = null,
    )

    @Serializable
    data class HitGenres(
        @SerialName("primary")
        val primary: String? = null,
    )
}
