package dev.hailab.deeppilot.ui.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * One assistant message rendered by marked + KaTeX from bundled assets.
 *
 * The page is served from the app's assets through WebViewAssetLoader; every
 * other request is answered locally with a 403, so a reply can never make the
 * phone fetch anything. Links open in the system browser.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class MessageWebView(context: Context) : WebView(context) {
    /** Content height in CSS px (= dp), reported after every layout change. */
    var onHeight: (Int) -> Unit = {}

    private var ready = false
    private var pending: String? = null
    private var shown: String? = null

    private val assets = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
        }
        addJavascriptInterface(Host(), "DeepPilotHost")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                assets.shouldInterceptRequest(request.url) ?: blocked()

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme in setOf("http", "https", "mailto")) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                ready = true
                pending?.let(::push)
            }
        }
        loadUrl(PAGE_URL)
    }

    /** Shows [payload] (a JSON object for `DeepPilot.render`); repeated payloads are ignored. */
    fun show(payload: String) {
        if (payload == shown || payload == pending) return
        if (ready) push(payload) else pending = payload
    }

    private fun push(payload: String) {
        pending = null
        shown = payload
        evaluateJavascript("DeepPilot.render($payload)", null)
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private inner class Host {
        @JavascriptInterface
        fun onHeight(height: Int) {
            post { onHeight(height) }
        }
    }

    private companion object {
        const val PAGE_URL = "https://appassets.androidplatform.net/assets/markdown/page.html"
    }
}
