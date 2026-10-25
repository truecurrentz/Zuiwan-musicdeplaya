package zuiwan.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import zuiwan.model.SessionData
import zuiwan.state.LocalHub
import zuiwan.theme.LocalGlass
import zuiwan.ui.ZIcons
import zuiwan.ui.iconbtn
import zuiwan.ui.topbar

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun viewlogin(
    onBack: () -> Unit,
) {
    val hub = LocalHub.current
    val glassTokens = LocalGlass.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var visitorData by remember { mutableStateOf("") }
    var dataSyncId by remember { mutableStateOf("") }
    var authUser by remember { mutableStateOf("0") }
    var finished by remember { mutableStateOf(false) }

    fun readcookies(): String {
        val cm = CookieManager.getInstance()
        runCatching { cm.flush() }
        val map = LinkedHashMap<String, String>()
        listOf("https://www.youtube.com", "https://music.youtube.com").forEach { domain ->
            val raw = cm.getCookie(domain).orEmpty()
            raw.split(";").forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val k = part.substring(0, idx).trim()
                    val v = part.substring(idx + 1).trim()
                    if (k.isNotEmpty()) map[k] = v
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun tryfinish(force: Boolean = false) {
        if (finished) return
        val merged = readcookies()
        val hasSap = merged.split(";").any {
            val k = it.substringBefore('=').trim()
            k == "SAPISID" || k == "__Secure-3PAPISID"
        }
        if (hasSap && (visitorData.isNotBlank() || force)) {
            finished = true
            hub.savesession(
                SessionData(
                    cookie = merged,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId.substringBefore("||"),
                    authUser = authUser.ifBlank { "0" },
                ),
            )
            onBack()
        } else if (force) {
            onBack()
        }
    }

    fun probecfg(view: WebView) {
        view.evaluateJavascript(
            "(function(){var c=(window.yt&&window.yt.config_)||(window.ytcfg&&window.ytcfg.data_)||{};return JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||'',u:String(c.SESSION_INDEX||'0')});})()",
        ) { raw ->
            val unquoted = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"").orEmpty()
            Regex("\"v\":\"([^\"]*)\"").find(unquoted)?.groupValues?.getOrNull(1)?.let {
                if (it.isNotBlank()) visitorData = it
            }
            Regex("\"d\":\"([^\"]*)\"").find(unquoted)?.groupValues?.getOrNull(1)?.let {
                if (it.isNotBlank()) dataSyncId = it.substringBefore("||")
            }
            Regex("\"u\":\"([^\"]*)\"").find(unquoted)?.groupValues?.getOrNull(1)?.let {
                if (it.isNotBlank()) authUser = it
            }
            tryfinish(force = false)
        }
    }

    LaunchedEffect(webView, finished) {
        var polls = 0
        while (!finished) {
            delay(950L)
            val wv = webView ?: continue
            val currentUrl = wv.url.orEmpty()
            if (currentUrl.contains("music.youtube.com") || currentUrl.contains("youtube.com")) {
                polls++
                probecfg(wv)
                if (polls >= 3) {
                    tryfinish(force = false)
                }
            }
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glassTokens.bg),
    ) {
        topbar(
            title = "YouTube Music Sign In",
            onBack = { tryfinish(force = true) },
            actions = {
                iconbtn(
                    icon = ZIcons.Check,
                    onClick = { tryfinish(force = true) },
                )
            },
        )

        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = glassTokens.accent,
                trackColor = glassTokens.glassFill,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null && url.contains("youtube.com")) {
                                    probecfg(view)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false
                        }
                        loadUrl(
                            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com",
                        )
                        webView = this
                    }
                },
            )
        }
    }
}
