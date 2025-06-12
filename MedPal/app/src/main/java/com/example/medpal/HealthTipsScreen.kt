package com.example.medpal

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HealthTipsScreen(
    url: String = "https://www.google.com",
    isLocal: Boolean = false
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            canGoBack = view.canGoBack()
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            canGoBack = view?.canGoBack() ?: false
                        }
                    }
                    settings.javaScriptEnabled = true
                    if (isLocal) {
                        loadUrl("file:///android_asset/index.html")
                    } else {
                        loadUrl(url)
                    }
                    webView = this
                }
            },
            modifier = Modifier.weight(1f),
            update = { wv ->
                webView = wv
                // Preserve WebView state between recompositions
                if (isLocal && wv.url?.startsWith("file:///android_asset") != true) {
                    wv.loadUrl("file:///android_asset/index.html")
                } else if (!isLocal && wv.url != url) {
                    wv.loadUrl(url)
                }
            }
        )
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}

