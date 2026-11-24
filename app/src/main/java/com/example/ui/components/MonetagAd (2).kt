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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

private enum class AdPhase { Checking, Loading, Shown, Failed }

/**
 * Remembers when ads could not be loaded because something blocks them (ad-blocker app, private
 * DNS with ad filtering...). While "blocked", no ad WebViews are created at all and ad slots take
 * no space. It tries again after a few minutes, in case the blocker was switched off.
 */
object AdStatus {
    private const val RETRY_AFTER_MS = 60_000L

    @Volatile
    private var blockedUntil = 0L

    @Volatile
    private var strikes = 0

    /** Last thing that happened to an ad, shown in Settings > About us to help find problems. */
    @Volatile
    var lastEvent: String = "no ad requested yet"
        private set

    private const val DNS_BLOCK_RETRY_MS = 10 * 60_000L
    private const val DNS_OK_CACHE_MS = 5 * 60_000L

    @Volatile
    private var dnsBlockedUntil = 0L

    @Volatile
    private var dnsOkUntil = 0L

    fun isBlocked(): Boolean {
        val now = System.currentTimeMillis()
        return now < blockedUntil || now < dnsBlockedUntil
    }

    fun isDnsKnownOk(): Boolean = System.currentTimeMillis() < dnsOkUntil

    /**
     * True when the phone's DNS (Private DNS, AdGuard-style DNS, a VPN blocker...) refuses to
     * resolve the ad server. Then no ad and no banner space is shown at all. The result is
     * remembered, so this lookup does not run for every ad. Call off the main thread.
     */
    fun checkDnsBlocked(context: Context): Boolean {
        if (isBlocked()) return true
        if (isDnsKnownOk()) return false
        if (!isOnline(context)) return false
        val host = Uri.parse(AppConstants.MONETAG_BASE_URL).host ?: return false
        val blocked = try {
            val addresses = InetAddress.getAllByName(host)
            // Ad-blocking DNS usually answers 0.0.0.0 / 127.0.0.1 instead of the real server.
            addresses.isNotEmpty() && addresses.all { it.isAnyLocalAddress || it.isLoopbackAddress }
        } catch (_: UnknownHostException) {
            // The ad server does not resolve. It is a blocker only if normal sites still do.
            try {
                InetAddress.getByName("www.google.com")
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
        val now = System.currentTimeMillis()
        if (blocked) {
            dnsBlockedUntil = now + DNS_BLOCK_RETRY_MS
            record("dns", "ad server blocked by DNS, ads and banners hidden")
        } else {
            dnsOkUntil = now + DNS_OK_CACHE_MS
        }
        return blocked
    }

    /** Two failed loads in a row = blocker. One failure alone can just be a slow network. */
    fun markBlocked() {
        strikes++
        if (strikes >= 2) blockedUntil = System.currentTimeMillis() + RETRY_AFTER_MS
    }

    fun markWorked() {
        strikes = 0
        blockedUntil = 0L
    }

    private val perZone = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun record(zoneId: String, text: String) {
        lastEvent = "zone $zoneId: $text"
        perZone[zoneId] = text
        android.util.Log.d("MonetagAd", lastEvent)
    }

    /** One line per zone that has reported something (for Settings > About us). */
    fun summary(): String =
        if (perZone.isEmpty()) "no ad requested yet"
        else perZone.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}: ${it.value}" }

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
@androidx.annotation.Keep
private class AdJsBridge(private val onResult: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @androidx.annotation.Keep
    @JavascriptInterface
    fun report(result: String) {
        main.post { onResult(result) }
    }
}

/**
 * Monetag ad slot.
 *  - The ad loads at its full size (some ad scripts only render in a visible, full-size view).
 *  - If the ad cannot load (blocked by an ad-blocker, offline, nothing after ~15 s) it is removed
 *    completely and the content below moves up, so no empty gap is left.
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
        mutableStateOf(
            when {
                AdStatus.isBlocked() -> AdPhase.Failed
                AdStatus.isDnsKnownOk() -> AdPhase.Loading
                else -> AdPhase.Checking
            }
        )
    }

    // Before any ad view is created, check that the phone's DNS lets the ad server through.
    // If it does not, the ad and its banner space never appear.
    LaunchedEffect(zoneId) {
        if (phase == AdPhase.Checking) {
            val blocked = withContext(Dispatchers.IO) { AdStatus.checkDnsBlocked(context) }
            phase = if (blocked) AdPhase.Failed else AdPhase.Loading
        }
    }
    if (phase == AdPhase.Failed || phase == AdPhase.Checking) return
    LaunchedEffect(zoneId) { AdStatus.record(zoneId, "requested, waiting for ad") }

    // Safety net: if the page never answers, give up so the slot does not stay half-alive.
    LaunchedEffect(zoneId) {
        delay(18_000L)
        if (phase == AdPhase.Loading) {
            AdStatus.record(zoneId, "no answer from page after 18 s, removed")
            phase = AdPhase.Failed
        }
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
            "setTimeout(function(){report('timeout');},15000);" +
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x11FFFFFF))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(height),
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
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    addJavascriptInterface(
                        AdJsBridge { result ->
                            when (result) {
                                "shown" -> {
                                    AdStatus.markWorked()
                                    AdStatus.record(zoneId, "shown")
                                    if (phase == AdPhase.Loading) phase = AdPhase.Shown
                                }
                                "blocked" -> {
                                    AdStatus.record(zoneId, "ad script blocked / failed to load")
                                    // Only call it a blocker when the phone is online.
                                    if (AdStatus.isOnline(ctx)) AdStatus.markBlocked()
                                    phase = AdPhase.Failed
                                }
                                else -> if (phase == AdPhase.Loading) {
                                    AdStatus.record(zoneId, "script loaded but nothing was drawn (15 s)")
                                    phase = AdPhase.Failed
                                }
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
                                AdStatus.record(zoneId, "network error: ${error.description}")
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
