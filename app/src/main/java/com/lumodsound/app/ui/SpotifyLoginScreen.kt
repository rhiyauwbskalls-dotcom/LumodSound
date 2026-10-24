package com.lumodsound.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

// Sin "/en/": es la URL que usa ArchiveTune y funciona en su WebView.
private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

// User-Agent de Chrome móvil normal (sin la marca "; wv" que delata a un WebView).
private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

/**
 * Inicio de sesión de Spotify en un WebView, como el de ArchiveTune: sesión limpia antes de empezar, ventanas
 * emergentes (Google/Facebook/captcha), y la cookie `sp_dc` se busca en todos los dominios de Spotify. Arriba se
 * muestra el estado de la carga para ver por qué una página no abre. La contraseña nunca la ve la app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(onSpDc: (String) -> Unit, onClose: () -> Unit) {
    val currentOnSpDc by rememberUpdatedState(onSpDc)
    var status by remember { mutableStateOf(str(R.string.preparing_sign_in)) }
    var active by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val w = active
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = str(R.string.close)) }
                Text(str(R.string.sign_in_to_spotify), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    val container = FrameLayout(ctx)
                    var delivered = false

                    fun capture(url: String?): Boolean {
                        if (delivered) return true
                        val spDc = readSpDc(url) ?: return false
                        delivered = true
                        CookieManager.getInstance().flush()
                        currentOnSpDc(spDc)
                        return true
                    }

                    fun newClient(): WebViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            shouldOverride(view, request.url?.toString(), ::capture)

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
                            shouldOverride(view, url, ::capture)

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            status = str(R.string.loading, hostOf(url))
                            capture(url)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            status = str(R.string.page_loaded, hostOf(url))
                            capture(url)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                status = str(R.string.couldnt_load_page, hostOf(request.url?.toString()), error.description, error.errorCode)
                            }
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) {
                                status = str(R.string.responded_http, hostOf(request.url?.toString()), errorResponse.statusCode)
                            }
                        }
                    }

                    val main = WebView(ctx)
                    fun configure(w: WebView) {
                        w.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = LOGIN_USER_AGENT
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                    }

                    fun closePopups() {
                        for (i in container.childCount - 1 downTo 0) {
                            val child = container.getChildAt(i) as? WebView ?: continue
                            if (child !== main) {
                                child.stopLoading()
                                container.removeView(child)
                                child.destroy()
                            }
                        }
                        active = main
                    }

                    configure(main)
                    main.webViewClient = newClient()
                    main.webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                            closePopups()
                            val popup = WebView(view.context)
                            configure(popup)
                            popup.webViewClient = newClient()
                            popup.webChromeClient = object : WebChromeClient() {
                                override fun onCloseWindow(window: WebView) {
                                    container.removeView(window)
                                    window.destroy()
                                    active = main
                                }
                            }
                            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                            container.addView(
                                popup,
                                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            )
                            popup.bringToFront()
                            popup.requestFocus()
                            active = popup
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onCloseWindow(window: WebView) {
                            closePopups()
                        }
                    }
                    container.addView(
                        main,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                    active = main
                    // Sesión limpia: cookies, almacenamiento y caché viejos pueden dejar la página de acceso en blanco
                    resetSession(ctx, main) {
                        status = str(R.string.opening_accounts_spotify_com)
=======

// Sin "/en/": es la URL que usa ArchiveTune y funciona en su WebView.
private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

// User-Agent de Chrome móvil normal (sin la marca "; wv" que delata a un WebView).
private const val LOGIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

/**
 * Inicio de sesión de Spotify en un WebView, como el de ArchiveTune: sesión limpia antes de empezar, ventanas
 * emergentes (Google/Facebook/captcha), y la cookie `sp_dc` se busca en todos los dominios de Spotify. Arriba se
 * muestra el estado de la carga para ver por qué una página no abre. La contraseña nunca la ve la app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(onSpDc: (String) -> Unit, onClose: () -> Unit) {
    val currentOnSpDc by rememberUpdatedState(onSpDc)
    var status by remember { mutableStateOf("Preparando el inicio de sesión…") }
    var active by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val w = active
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                Text("Iniciar sesión en Spotify", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    val container = FrameLayout(ctx)
                    var delivered = false

                    fun capture(url: String?): Boolean {
                        if (delivered) return true
                        val spDc = readSpDc(url) ?: return false
                        delivered = true
                        CookieManager.getInstance().flush()
                        currentOnSpDc(spDc)
                        return true
                    }

                    fun newClient(): WebViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            shouldOverride(view, request.url?.toString(), ::capture)

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
                            shouldOverride(view, url, ::capture)

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            status = "Cargando ${hostOf(url)}…"
                            capture(url)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            status = "Página cargada: ${hostOf(url)}"
                            capture(url)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                status = "No cargó ${hostOf(request.url?.toString())}: ${error.description} (${error.errorCode})"
                            }
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) {
                                status = "${hostOf(request.url?.toString())} respondió HTTP ${errorResponse.statusCode}"
                            }
                        }
                    }

                    val main = WebView(ctx)
                    fun configure(w: WebView) {
                        w.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = LOGIN_USER_AGENT
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                    }

                    fun closePopups() {
                        for (i in container.childCount - 1 downTo 0) {
                            val child = container.getChildAt(i) as? WebView ?: continue
                            if (child !== main) {
                                child.stopLoading()
                                container.removeView(child)
                                child.destroy()
                            }
                        }
                        active = main
                    }

                    configure(main)
                    main.webViewClient = newClient()
                    main.webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                            closePopups()
                            val popup = WebView(view.context)
                            configure(popup)
                            popup.webViewClient = newClient()
                            popup.webChromeClient = object : WebChromeClient() {
                                override fun onCloseWindow(window: WebView) {
                                    container.removeView(window)
                                    window.destroy()
                                    active = main
                                }
                            }
                            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                            container.addView(
                                popup,
                                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            )
                            popup.bringToFront()
                            popup.requestFocus()
                            active = popup
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onCloseWindow(window: WebView) {
                            closePopups()
                        }
                    }
                    container.addView(
                        main,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                    active = main
                    // Sesión limpia: cookies, almacenamiento y caché viejos pueden dejar la página de acceso en blanco
                    resetSession(ctx, main) {
                        status = "Abriendo accounts.spotify.com…"
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
                        main.loadUrl(SPOTIFY_LOGIN_URL)
                    }
                    container
                },
                onRelease = { container ->
                    for (i in container.childCount - 1 downTo 0) {
                        (container.getChildAt(i) as? WebView)?.let {
                            it.stopLoading()
                            it.destroy()
                        }
                    }
                    container.removeAllViews()
                }
            )
        }
    }
}

private fun hostOf(url: String?): String =
    runCatching { Uri.parse(url).host }.getOrNull() ?: url.orEmpty().take(40)

/** Borra cookies, almacenamiento y caché del WebView antes de empezar y luego llama a [onReady]. */
private fun resetSession(context: Context, webView: WebView, onReady: () -> Unit) {
    webView.stopLoading()
    webView.clearHistory()
    webView.clearFormData()
    webView.clearCache(true)
    WebStorage.getInstance().deleteAllData()
    val cm = CookieManager.getInstance()
    cm.setAcceptCookie(true)
    cm.setAcceptThirdPartyCookies(webView, true)
    cm.removeSessionCookies {
        cm.removeAllCookies {
            cm.flush()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            onReady()
        }
    }
}

/** Busca la cookie `sp_dc` en todos los dominios de Spotify (y en el de la página actual). */
private fun readSpDc(currentUrl: String?): String? {
    val cm = CookieManager.getInstance()
    cm.flush()
    val origins = linkedSetOf("https://open.spotify.com", "https://accounts.spotify.com", "https://spotify.com")
    val host = runCatching { Uri.parse(currentUrl).host?.lowercase() }.getOrNull()
    if (host != null && (host == "spotify.com" || host.endsWith(".spotify.com"))) origins.add("https://$host")
    for (origin in origins) {
        val spDc = cm.getCookie(origin)
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("sp_dc=") }
            ?.removePrefix("sp_dc=")
            ?.takeIf { it.isNotBlank() }
        if (spDc != null) return spDc
    }
    return null
}

private fun shouldOverride(view: WebView, url: String?, capture: (String?) -> Boolean): Boolean {
    if (capture(url)) return true
    val target = url?.takeIf { it.isNotBlank() } ?: return false
    val scheme = runCatching { Uri.parse(target).scheme?.lowercase() }.getOrNull()
    if (scheme == "http" || scheme == "https" || scheme == "javascript" || scheme == "data" || scheme == "blob") return false
    // Enlaces tipo intent://: se usa la dirección alternativa del navegador si la traen
    runCatching { Intent.parseUri(target, Intent.URI_INTENT_SCHEME) }
        .getOrNull()
        ?.getStringExtra("browser_fallback_url")
        ?.takeIf { it.startsWith("http") }
        ?.let { view.loadUrl(it) }
    return true
}
