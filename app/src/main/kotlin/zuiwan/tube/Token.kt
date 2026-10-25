package zuiwan.tube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.potoken.parseIntegrityTokenData
import com.metrolist.innertubex.extraction.potoken.stringToU8
import com.metrolist.innertubex.extraction.potoken.u8ToBase64
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@SuppressLint("SetJavaScriptEnabled")
class TokenBridge(
    context: Context,
    private val initCont: Continuation<TokenBridge>,
) {
    private val webView = WebView(context)
    private val scope = MainScope()
    private val initDone = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<String, Continuation<String>>()
    private val seq = AtomicLong(0L)

    @Volatile
    var dead = false
        private set

    @Volatile
    private var closed = false

    private var expiresAt: Instant = Instant.EPOCH

    val expired: Boolean
        get() = Instant.now().isAfter(expiresAt)

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = UA
        webView.settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, BRIDGE)
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                dead = true
                failinit(IllegalStateException("Renderer gone"))
                return true
            }
        }
    }

    fun boot() {
        scope.launch {
            runCatching {
                val html = withContext(Dispatchers.IO) {
                    webView.context.assets.open("potoken.html").bufferedReader().use { it.readText() }
                }
                val injected = html.replaceFirst("</script>", "\n$BRIDGE.startguard()</script>")
                webView.loadDataWithBaseURL("https://www.youtube.com", injected, "text/html", "utf-8", null)
            }.onFailure { failinit(it) }
        }
    }

    @JavascriptInterface
    fun startguard() {
        scope.launch {
            runCatching {
                val createBody = postguard("https://www.youtube.com/api/jnn/v1/Create", "[\"$REQ_KEY\"]")
                val challengeJson = parsechallenge(createBody)
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        """try {
                            var d = $challengeJson;
                            runBotGuard(d).then(function(r) {
                                window._wpo = r.webPoSignalOutput;
                                $BRIDGE.guardready(r.botguardResponse);
                            }, function(e) {
                                $BRIDGE.guarderror(String(e));
                            });
                        } catch(e) {
                            $BRIDGE.guarderror(String(e));
                        }""",
                        null,
                    )
                }
            }.onFailure { failinit(it) }
        }
    }

    @JavascriptInterface
    fun guardready(botguardResponse: String) {
        scope.launch {
            runCatching {
                val itBody = postguard(
                    "https://www.youtube.com/api/jnn/v1/GenerateIT",
                    "[\"$REQ_KEY\",\"$botguardResponse\"]",
                )
                val parsed = parseIntegrityTokenData(itBody)
                expiresAt = Instant.now().plusSeconds((parsed.lifetimeSeconds - 300L).coerceAtLeast(60L))
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        """createPoTokenMinter(window._wpo, ${parsed.tokenJavaScript}).then(function() {
                            $BRIDGE.minterready();
                        }, function(e) {
                            $BRIDGE.guarderror(String(e));
                        });""",
                        null,
                    )
                }
            }.onFailure { failinit(it) }
        }
    }

    @JavascriptInterface
    fun minterready() {
        if (initDone.compareAndSet(false, true)) {
            runCatching { initCont.resume(this) }
        }
    }

    @JavascriptInterface
    fun guarderror(msg: String) {
        failinit(IllegalStateException(msg))
    }

    suspend fun mint(identifier: String): String {
        val key = "$identifier#${seq.incrementAndGet()}"
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                pending[key] = cont
                val u8 = stringToU8(identifier)
                webView.evaluateJavascript(
                    """(function() {
                        var k = "$key";
                        obtainPoToken($u8).then(function(bytes) {
                            $BRIDGE.tokenok(k, bytes.join(","));
                        }).catch(function(e) {
                            $BRIDGE.tokenfail(k, String(e));
                        });
                    })()""",
                    null,
                )
            }
        }
    }

    @JavascriptInterface
    fun tokenok(key: String, csvBytes: String) {
        val cont = pending.remove(key) ?: return
        runCatching { u8ToBase64(csvBytes) }
            .onSuccess { cont.resume(it) }
            .onFailure { cont.resumeWithException(it) }
    }

    @JavascriptInterface
    fun tokenfail(key: String, error: String) {
        pending.remove(key)?.resumeWithException(IllegalStateException(error))
    }

    private fun failinit(err: Throwable) {
        dead = true
        teardown()
        if (initDone.compareAndSet(false, true)) {
            runCatching { initCont.resumeWithException(err) }
        }
        val active = pending.values.toList()
        pending.clear()
        active.forEach { runCatching { it.resumeWithException(err) } }
    }

    fun teardown() {
        if (closed) return
        closed = true
        scope.cancel()
        val drop = Runnable {
            runCatching {
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            drop.run()
        } else {
            Handler(Looper.getMainLooper()).post(drop)
        }
    }

    private suspend fun postguard(url: String, payload: String): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody())
                .headers(
                    mapOf(
                        "User-Agent" to UA,
                        "Accept" to "application/json",
                        "Content-Type" to "application/json+protobuf",
                        "x-goog-api-key" to API_KEY,
                        "x-user-agent" to "grpc-web-javascript/0.1",
                    ).toHeaders(),
                )
                .build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                check(res.code == 200 && text.isNotBlank()) { "BotGuard HTTP ${res.code}" }
                text
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parsechallenge(raw: String): String {
        val outer = Json.parseToJsonElement(raw).jsonArray
        val data = if (outer.size > 1 && outer[1].jsonPrimitive.isString) {
            val normalized = outer[1].jsonPrimitive.content
                .replace('-', '+')
                .replace('_', '/')
                .replace('.', '=')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val descrambled = Base64.Default.decode(padded).map { (it + 97).toByte() }.toByteArray().decodeToString()
            Json.parseToJsonElement(descrambled).jsonArray
        } else {
            outer[0].jsonArray
        }
        val scriptVal = data[1].takeIf { it !is JsonNull }?.jsonArray?.firstOrNull { it.jsonPrimitive.isString } ?: JsonNull
        val urlVal = data[2].takeIf { it !is JsonNull }?.jsonArray?.firstOrNull { it.jsonPrimitive.isString } ?: JsonNull
        val obj = JsonObject(
            mapOf(
                "messageId" to JsonPrimitive(data[0].jsonPrimitive.content),
                "interpreterJavascript" to JsonObject(
                    mapOf(
                        "privateDoNotAccessOrElseSafeScriptWrappedValue" to scriptVal,
                        "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to urlVal,
                    ),
                ),
                "interpreterHash" to JsonPrimitive(data[3].jsonPrimitive.content),
                "program" to JsonPrimitive(data[4].jsonPrimitive.content),
                "globalName" to JsonPrimitive(data[5].jsonPrimitive.content),
                "clientExperimentsStateBlob" to JsonPrimitive(data[7].jsonPrimitive.content),
            ),
        )
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        private const val BRIDGE = "TokenBridge"
        private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQ_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private val http = OkHttpClient()
    }
}

class TokenMint(context: Context) : TokenProvider {
    private val appContext = context.applicationContext
    private val lock = Mutex()
    private val supported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var bridge: TokenBridge? = null
    private var cachedSessionId: String? = null
    private var cachedStreamingToken: String? = null

    override val capabilities: TokenProviderCapabilities =
        TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true,
        )

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? {
        if (!supported) return null
        return try {
            withTimeoutOrNull(8_000L) {
                mintpair(videoId, visitorData)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun mintpair(videoId: String, sessionId: String): PoTokenResult =
        lock.withLock {
            val active = bridge
            if (active == null || active.dead || active.expired || cachedSessionId != sessionId || cachedStreamingToken == null) {
                active?.teardown()
                bridge = null
                cachedSessionId = null
                cachedStreamingToken = null

                val created = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val tb = TokenBridge(appContext, cont)
                        tb.boot()
                    }
                }
                val streamTok = created.mint(sessionId)
                bridge = created
                cachedSessionId = sessionId
                cachedStreamingToken = streamTok
            }
            val playerTok = bridge!!.mint(videoId)
            PoTokenResult(
                playerRequestToken = cachedStreamingToken!!,
                streamingDataToken = playerTok,
                visitorData = sessionId,
            )
        }

    override suspend fun close() {
        lock.withLock {
            bridge?.teardown()
            bridge = null
            cachedSessionId = null
            cachedStreamingToken = null
        }
    }
}
