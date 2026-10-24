package zuiwan.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

@Serializable
private data class TtmlWrapper(val ttml: String? = null)

class BetterSource(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchbetter(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): Result<String> = runCatching {
        val res = http.get("https://lyrics-api.boidu.dev/getLyrics") {
            parameter("s", title)
            parameter("a", artist)
            if (durationSec > 0) parameter("d", durationSec)
            if (!album.isNullOrBlank()) parameter("al", album)
        }
        check(res.status.isSuccess()) { "BetterLyrics HTTP ${res.status.value}" }
        val ttml = json.decodeFromString<TtmlWrapper>(res.bodyAsText()).ttml
            ?.takeIf { it.isNotBlank() }
            ?: error("Empty TTML")
        parsettml(ttml).takeIf { it.isNotBlank() } ?: error("No lines in TTML")
    }

    private fun parsettml(ttml: String): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val paragraphs = doc.getElementsByTagName("p")
        val lines = mutableListOf<String>()

        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue
            val beginStr = p.getAttribute("begin").takeIf { it.isNotBlank() } ?: continue
            val lineStartSec = parsesec(beginStr)

            val wordEntries = mutableListOf<Pair<Double, String>>()
            val plainBuilder = StringBuilder()
            val children = p.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeType == Node.TEXT_NODE) {
                    plainBuilder.append(child.textContent)
                } else if (child.nodeType == Node.ELEMENT_NODE) {
                    val span = child as Element
                    if (span.localName.equals("span", ignoreCase = true) || span.tagName.endsWith("span")) {
                        val role = span.getAttribute("ttm:role").ifBlank { span.getAttribute("role") }
                        if (role == "x-bg" || role == "x-translation" || role == "x-roman") continue
                        val txt = span.textContent.orEmpty()
                        val wBegin = span.getAttribute("begin")
                        if (wBegin.isNotBlank() && txt.isNotBlank()) {
                            val trail = if (j + 1 < children.length && children.item(j + 1).nodeType == Node.TEXT_NODE && children.item(j + 1).textContent.contains(' ')) " " else ""
                            wordEntries += parsesec(wBegin) to (txt.trim() + trail)
                        }
                        plainBuilder.append(txt)
                    }
                }
            }

            val lineTag = formattag(lineStartSec, '[', ']')
            if (wordEntries.size >= 2) {
                val wordString = wordEntries.joinToString("") { (sec, w) ->
                    "${formattag(sec, '<', '>')}$w"
                }
                lines += "$lineTag$wordString"
            } else {
                val clean = plainBuilder.toString().replace(Regex("""\s+"""), " ").trim()
                if (clean.isNotBlank()) {
                    lines += "$lineTag$clean"
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun parsesec(raw: String): Double {
        val clean = raw.trim().removeSuffix("s")
        if (clean.endsWith("ms")) {
            return (clean.removeSuffix("ms").toDoubleOrNull() ?: 0.0) / 1_000.0
        }
        val parts = clean.split(":")
        return when (parts.size) {
            3 -> (parts[0].toDoubleOrNull() ?: 0.0) * 3600.0 +
                (parts[1].toDoubleOrNull() ?: 0.0) * 60.0 +
                (parts[2].toDoubleOrNull() ?: 0.0)
            2 -> (parts[0].toDoubleOrNull() ?: 0.0) * 60.0 +
                (parts[1].toDoubleOrNull() ?: 0.0)
            else -> clean.toDoubleOrNull() ?: 0.0
        }
    }

    private fun formattag(seconds: Double, open: Char, close: Char): String {
        val totalMs = (seconds * 1_000.0).toLong().coerceAtLeast(0L)
        val m = totalMs / 60_000L
        val s = (totalMs / 1_000L) % 60L
        val ms = totalMs % 1_000L
        return "%c%02d:%02d.%03d%c".format(open, m, s, ms, close)
    }
}
