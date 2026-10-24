package zuiwan.tube

import app.zuiwan.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import zuiwan.model.Track
import java.security.MessageDigest

class ScrobbleClient(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun sign(params: Map<String, String>, secret: String): String {
        val joined = params.toSortedMap().entries.joinToString("") { "${it.key}${it.value}" } + secret
        return md5(joined)
    }

    suspend fun loginlastfm(username: String, password: String): Result<String> =
        runCatching {
            val apiKey = BuildConfig.LASTFM_KEY.ifBlank { error("Last.fm API key not configured") }
            val secret = BuildConfig.LASTFM_SECRET.ifBlank { error("Last.fm secret not configured") }
            val base = mapOf(
                "api_key" to apiKey,
                "method" to "auth.getMobileSession",
                "password" to password,
                "username" to username,
            )
            val sig = sign(base, secret)
            val res = http.post("https://ws.audioscrobbler.com/2.0/") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            base.forEach { (k, v) -> append(k, v) }
                            append("api_sig", sig)
                            append("format", "json")
                        },
                    ),
                )
            }
            val root = json.parseToJsonElement(res.bodyAsText()).obj()
            root.pathstr("session", "key") ?: error("Authentication failed")
        }

    suspend fun scrobbletrack(track: Track, sessionKey: String, startedSec: Long) {
        val apiKey = BuildConfig.LASTFM_KEY.takeIf { it.isNotBlank() } ?: return
        val secret = BuildConfig.LASTFM_SECRET.takeIf { it.isNotBlank() } ?: return
        if (sessionKey.isBlank()) return
        runCatching {
            val base = buildMap {
                put("api_key", apiKey)
                put("artist", track.artists.firstOrNull()?.name ?: "Unknown")
                put("method", "track.scrobble")
                put("sk", sessionKey)
                put("timestamp", startedSec.toString())
                put("track", track.title)
                track.album?.name?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            }
            val sig = sign(base, secret)
            http.post("https://ws.audioscrobbler.com/2.0/") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            base.forEach { (k, v) -> append(k, v) }
                            append("api_sig", sig)
                            append("format", "json")
                        },
                    ),
                )
            }
        }
    }
}
