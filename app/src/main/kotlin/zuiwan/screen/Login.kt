package zuiwan.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import zuiwan.model.SessionData
import zuiwan.state.LocalHub
import zuiwan.ui.GlassIconButton
import zuiwan.ui.GlassTopBar
import zuiwan.ui.ZIcons

private class SessionCapture(
    val onCaptured: (visitorData: String, dataSyncId: String) -> Unit,
) {
    @JavascriptInterface
    fun report(visitorData: String?, dataSyncId: String?) {
        onCaptured(
            visitorData.orEmpty().trim('"'),
            dataSyncId.orEmpty().trim('"').substringBefore("||"),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun viewlogin(
    onBack: () -> Unit,
) {
    val hub = LocalHub.current
    var visitorData by remember { mutableStateOf("") }
    var dataSyncId by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun finishlogin() {
        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
        if (cookie.contains("SAPISID")) {
            hub.savesession(
                SessionData(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    lastFmUser = hub.session.value.lastFmUser,
                    lastFmSession = hub.session.value.lastFmSession,
                    scrobbleEnabled = hub.session.value.scrobbleEnabled,
                ),
            )
        }
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassTopBar(title = "Sign In", onBack = ::finishlogin) {
            GlassIconButton(
                icon = ZIcons.Check,
                solidAccent = true,
                onClick = ::finishlogin,
            )
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(
                        SessionCapture { vd, ds ->
                            if (vd.isNotBlank()) visitorData = vd
                            if (ds.isNotBlank()) dataSyncId = ds
                        },
                        "ZuiwanAuth",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (url.contains("music.youtube.com")) {
                                view.evaluateJavascript(
                                    """(function() {
                                        try {
                                            var vd = window.ytcfg ? window.ytcfg.get('VISITOR_DATA') : '';
                                            var ds = window.ytcfg ? window.ytcfg.get('DATASYNC_ID') : '';
                                            ZuiwanAuth.report(String(vd || ''), String(ds || ''));
                                        } catch(e) {}
                                    })()""",
                                    null,
                                )
                            }
                        }
                    }
                    loadUrl(
                        "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com",
                    )
                    webViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
