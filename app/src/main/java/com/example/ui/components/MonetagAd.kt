package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AppConstants
import kotlinx.coroutines.delay

private enum class AdPhase { Loading, Shown, Failed }

/**
 * Remembers when ads could not be loaded because something blocks them (ad-blocker app, private
 * DNS with ad filtering...). While "blocked", no ad WebViews are created at all and ad slots take
 * no space. It tries again after a few minutes, in case the blocker was switched off.
 */
object AdStatus {
    private const val RETRY_AFTER_MS = 5 * 60_000L

    @Volatile
    private var blockedUntil = 0L

    fun isBlocked(): Boolean = System.currentTimeMillis() < blockedUntil

    fun markBlocked() {
        blockedUntil = System.currentTimeMillis() + RETRY_AFTER_MS
    }

    fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork != null
    } catch (_: Exception) {
        true
    }

    /**
     * Starts the WebView engine and the DNS lookup of the ad server early, so the first ad does not
     * have to wait for them. Call once at app start.
     */
    fun warmUp(context: Context) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                WebView(appContext).destroy()
            } catch (_: Exception) {
            }
        }, 1500L)
        Thread {
            try {
                java.net.InetAddress.getByName(Uri.parse(AppConstants.MONETAG_BASE_URL).host)
            } catch (_: Exception) {
            }
        }.start()
    }
}

/** The page tells the app whether the ad really appeared ("shown"), was blocked or timed out. */
private class AdJsBridge(private val onResult: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(result: String) {
        main.post { onResult(result) }
    }
}

/**
 * Monetag ad slot.
 *  - It takes NO space until the ad has really appeared, then grows smoothly to [height].
 *  - If the ad cannot load (blocked by an ad-blocker, offline, timeout) it is removed completely
 *    and the content below moves up, so no empty gap is left.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonetagAd(
    zoneId: String,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp
) {
    val context = LocalContext.current
    var phase by remember(zoneId) {
        mutableStateOf(if (AdStatus.isBlocked()) AdPhase.Failed else AdPhase.Loading)
    }
    if (phase == AdPhase.Failed) return

    // Safety net: if the page never answers, give up so the slot does not stay half-alive.
    LaunchedEffect(zoneId) {
        delay(12_000L)
        if (phase == AdPhase.Loading) phase = AdPhase.Failed
    }

    val html = remember(zoneId) {
        "<html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<link rel=\"preconnect\" href=\"" + AppConstants.MONETAG_BASE_URL + "\" crossorigin>" +
            "<link rel=\"dns-prefetch\" href=\"" + AppConstants.MONETAG_BASE_URL + "\">" +
            "<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden;}</style>" +
            "</head><body><script>(function(){" +
            "var done=false;" +
            "function report(r){if(done)return;done=true;try{AdBridge.report(r);}catch(e){}}" +
            "var s=document.createElement('script');" +
            "s.dataset.zone='" + zoneId + "';s.src='" + AppConstants.MONETAG_TAG_SRC + "';s.async=true;" +
            "s.onerror=function(){report('blocked');};" +
            "(document.body||document.documentElement).appendChild(s);" +
            "var mo=new MutationObserver(function(){" +
            "if(document.querySelectorAll('body *:not(script):not(style)').length>0){" +
            "report('shown');mo.disconnect();}});" +
            "mo.observe(document.documentElement,{childList:true,subtree:true});" +
            "setTimeout(function(){report('timeout');},9000);" +
            "})();</script></body></html>"
    }
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef[0]?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("AdBridge")
                destroy()
            }
            webViewRef[0] = null
        }
    }

    val shownHeight by animateDpAsState(
        targetValue = if (phase == AdPhase.Shown) height else 0.dp,
        label = "adHeight"
    )

    // Caller's padding/alignment only apply once the ad is visible, so nothing is left behind
    // while the ad is loading or when it fails.
    Box(
        modifier = (if (phase == AdPhase.Shown) modifier else Modifier)
            .fillMaxWidth()
            .height(shownHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x11FFFFFF))
    ) {
        AndroidView(
            // The ad page is laid out at its full size even while the slot is still collapsed.
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .height(height),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.setSupportMultipleWindows(false)
                    addJavascriptInterface(
                        AdJsBridge { result ->
                            when (result) {
                                "shown" -> if (phase == AdPhase.Loading) phase = AdPhase.Shown
                                "blocked" -> {
                                    // Only call it a blocker when the phone is online.
                                    if (AdStatus.isOnline(ctx)) AdStatus.markBlocked()
                                    phase = AdPhase.Failed
                                }
                                else -> if (phase == AdPhase.Loading) phase = AdPhase.Failed
                            }
                        },
                        "AdBridge"
                    )
                    webViewClient = object : WebViewClient() {
                        // Taps on the ad open the browser instead of replacing the ad slot.
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (!request.hasGesture()) return false
                            return try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, request.url)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            // The ad script itself could not be fetched (blocked / offline).
                            if (request.url.toString().startsWith(AppConstants.MONETAG_TAG_SRC)) {
                                if (AdStatus.isOnline(ctx)) AdStatus.markBlocked()
                                phase = AdPhase.Failed
                            }
                        }
                    }
                    loadDataWithBaseURL(AppConstants.MONETAG_BASE_URL, html, "text/html", "utf-8", null)
                    webViewRef[0] = this
                }
            }
        )
    }
}
