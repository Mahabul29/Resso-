package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.util.AppConstants

/**
 * Monetag ad slot. Har placement ka apna WebView hota hai.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonetagAd(
    zoneId: String,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp
) {
    val html = remember(zoneId) {
        val tag = "<script>(function(s){s.dataset.zone='" + zoneId + "',s.src='" +
            AppConstants.MONETAG_TAG_SRC +
            "'})([document.documentElement, document.body].filter(Boolean).pop()" +
            ".appendChild(document.createElement('script')))</script>"
        "<html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden;}</style>" +
            "</head><body>" + tag + "</body></html>"
    }
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef[0]?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef[0] = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x11FFFFFF)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL(AppConstants.MONETAG_BASE_URL, html, "text/html", "utf-8", null)
                webViewRef[0] = this
            }
        }
    )
}
