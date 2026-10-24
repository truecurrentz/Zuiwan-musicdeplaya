package zuiwan.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import zuiwan.tube.arr
import zuiwan.tube.num
import zuiwan.tube.obj
import zuiwan.tube.patharr
import zuiwan.tube.str
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

class KugouSource(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchkugou(
        title: String,
        artist: String,
        durationSec: Int,
    ): Result<String> = runCatching {
        val keyword = "$title - $artist"
        val searchRes = http.get("https://mobileservice.kugou.com/api/v3/search/song") {
            parameter("version", 9108)
            parameter("plat", 0)
            parameter("pagesize", 8)
            parameter("showtype", 0)
            parameter("keyword", keyword)
        }
        val root = json.parseToJsonElement(searchRes.bodyAsText()).obj()
        val infos = root.patharr("data", "info").orEmpty()
        val chosen = infos
            .mapNotNull { it.obj() }
            .firstOrNull { info ->
                val dur = info["duration"].num() ?: 0
                durationSec <= 0 || abs(dur - durationSec) <= 8
            } ?: infos.firstOrNull().obj() ?: error("No KuGou song match")

        val hash = chosen["hash"].str() ?: error("Missing KuGou hash")
        val candRes = http.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter("keyword", keyword)
            parameter("hash", hash)
        }
        val candObj = json.parseToJsonElement(candRes.bodyAsText()).obj()
        val cand = candObj?.get("candidates").arr()?.firstOrNull().obj() ?: error("No KuGou lyric candidate")
        val id = cand["id"].str() ?: error("Missing candidate id")
        val accesskey = cand["accesskey"].str() ?: error("Missing accesskey")

        val dlRes = http.get("https://lyrics.kugou.com/download") {
            parameter("fmt", "lrc")
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", id)
            parameter("accesskey", accesskey)
        }
        val dlObj = json.parseToJsonElement(dlRes.bodyAsText()).obj()
        val b64 = dlObj?.get("content").str()?.takeIf { it.isNotBlank() } ?: error("Empty KuGou lyric body")
        Base64.Default.decode(b64).decodeToString().trim()
    }
}
