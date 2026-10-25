package zuiwan.tube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import zuiwan.model.ArtistRef

data class RunToken(
    val text: String,
    val browseId: String? = null,
    val pageType: String? = null,
    val videoId: String? = null,
    val playlistId: String? = null,
)

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.arr(): JsonArray? = this as? JsonArray

fun JsonElement?.prim(): JsonPrimitive? =
    if (this == null || this is JsonNull) null else this as? JsonPrimitive

fun JsonElement?.str(): String? = prim()?.contentOrNull

fun JsonElement?.num(): Int? = prim()?.intOrNull

fun JsonElement?.longnum(): Long? = prim()?.longOrNull

fun JsonElement?.flag(): Boolean? = prim()?.booleanOrNull

fun JsonObject?.path(vararg keys: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = (cur as? JsonObject)?.get(k) ?: return null
    }
    return cur
}

fun JsonObject?.pathobj(vararg keys: String): JsonObject? = path(*keys).obj()

fun JsonObject?.patharr(vararg keys: String): JsonArray? = path(*keys).arr()

fun JsonObject?.pathstr(vararg keys: String): String? = path(*keys).str()

fun JsonElement?.runs(): List<RunToken> {
    val list = this.obj()?.get("runs").arr() ?: this.arr() ?: return emptyList()
    return list.mapNotNull { item ->
        val o = item.obj() ?: return@mapNotNull null
        val text = o["text"].str() ?: return@mapNotNull null
        val nav = o["navigationEndpoint"].obj()
        val browse = nav.pathobj("browseEndpoint")
        val watch = nav.pathobj("watchEndpoint")
        val pageType = browse.pathstr(
            "browseEndpointContextSupportedConfigs",
            "browseEndpointContextMusicConfig",
            "pageType",
        )
        RunToken(
            text = text,
            browseId = browse?.get("browseId").str(),
            pageType = pageType,
            videoId = watch?.get("videoId").str(),
            playlistId = watch?.get("playlistId").str(),
        )
    }
}

fun JsonElement?.runtext(): String =
    runs().joinToString("") { it.text }.trim()

fun List<RunToken>.splitgroups(): List<List<RunToken>> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<RunToken>>()
    var current = mutableListOf<RunToken>()
    for (token in this) {
        if (token.text.trim() in setOf("\u2022", "\u00B7", "|")) {
            if (current.isNotEmpty()) {
                groups += current
                current = mutableListOf()
            }
        } else {
            current += token
        }
    }
    if (current.isNotEmpty()) groups += current
    return groups
}

fun List<RunToken>.extractartists(): List<ArtistRef> {
    val linked = filter { token ->
        val id = token.browseId
        val type = token.pageType
        id != null && (
            id.startsWith("UC") ||
                type == "MUSIC_PAGE_TYPE_ARTIST" ||
                type == "MUSIC_PAGE_TYPE_USER_CHANNEL"
            )
    }.map { ArtistRef(it.browseId, it.text.trim()) }
    if (linked.isNotEmpty()) return linked

    val firstGroup = splitgroups().firstOrNull().orEmpty()
    val candidate = firstGroup.joinToString("") { it.text }.trim()
    if (candidate.isNotBlank() && parsetime(candidate) == null && candidate.toIntOrNull() == null) {
        val ignored = setOf(
            "Song", "Video", "Album", "Single", "EP", "Artist", "Playlist", "Podcast", "Episode",
        )
        if (candidate !in ignored) {
            return listOf(ArtistRef(null, candidate))
        }
    }
    return emptyList()
}

fun JsonElement?.lastthumb(depth: Int = 0): String {
    if (this == null || depth > 7) return ""
    if (this is JsonArray) {
        val directUrl = this.lastOrNull().obj()?.get("url").str()
        if (!directUrl.isNullOrBlank()) {
            return if (directUrl.startsWith("//")) "https:$directUrl" else directUrl
        }
        for (i in this.lastIndex downTo 0) {
            val sub = this[i].lastthumb(depth + 1)
            if (sub.isNotBlank()) return sub
        }
        return ""
    }
    val o = this.obj() ?: return ""
    val direct = o["thumbnails"].arr() ?: o["sources"].arr()
    if (direct != null) {
        val url = direct.lastOrNull().obj()?.get("url").str()
        if (!url.isNullOrBlank()) {
            return if (url.startsWith("//")) "https:$url" else url
        }
    }
    val keys = listOf(
        "musicThumbnailRenderer",
        "croppedSquareThumbnailRenderer",
        "thumbnailRenderer",
        "musicAnimatedThumbnailRenderer",
        "backupRenderer",
        "foregroundThumbnail",
        "avatar",
        "thumbnail",
        "image",
    )
    for (k in keys) {
        val hit = o[k]?.lastthumb(depth + 1).orEmpty()
        if (hit.isNotBlank()) return hit
    }
    return ""
}

fun JsonElement?.findcontinuation(): String? {
    when (this) {
        is JsonObject -> {
            pathstr("nextContinuationData", "continuation")?.let { return it }
            pathstr("nextRadioContinuationData", "continuation")?.let { return it }
            pathstr("continuationEndpoint", "continuationCommand", "token")?.let { return it }
            this["continuations"].findcontinuation()?.let { return it }
            for ((k, v) in this) {
                if (k.contains("ontinuation", ignoreCase = true)) {
                    v.findcontinuation()?.let { return it }
                }
            }
        }
        is JsonArray -> {
            for (el in this) {
                el.findcontinuation()?.let { return it }
            }
        }
        else -> {}
    }
    return null
}

fun parsetime(raw: String?): Int? {
    val clean = raw?.trim()?.takeIf { it.contains(":") } ?: return null
    val parts = clean.split(":")
    if (parts.size !in 2..3 || parts.any { it.toIntOrNull() == null }) return null
    val nums = parts.map { it.toInt() }
    return if (nums.size == 2) {
        nums[0] * 60 + nums[1]
    } else {
        nums[0] * 3600 + nums[1] * 60 + nums[2]
    }
}

fun JsonArray?.hasexplicit(): Boolean {
    if (this == null) return false
    return any { badge ->
        badge.obj().pathstr("musicInlineBadgeRenderer", "icon", "iconType") == "MUSIC_EXPLICIT_BADGE"
    }
}
