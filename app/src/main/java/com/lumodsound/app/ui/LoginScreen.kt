package com.lumodsound.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// El destino ("continue") va codificado entero; así Google termina en music.youtube.com y no en YouTube normal.
private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
        "%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

private const val MUSIC_URL = "https://music.youtube.com/"

/**
 * Inicio de sesión de Google en un WebView. Al terminar de cargar music.youtube.com con la sesión
 * abierta, se leen sus cookies y se entregan a la app. No hace falta registrar la app en Google Cloud.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onCookie: (String) -> Unit, onClose: () -> Unit) {
    val currentOnCookie by rememberUpdatedState(onCookie)
    BackHandler(onBack = onClose)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } } // que los toques no pasen a la pantalla de abajo
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            Text("Iniciar sesión con Google", style = MaterialTheme.typography.titleMedium)
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    var delivered = false
                    var sentToMusic = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (delivered || url == null) return
                            val cm = CookieManager.getInstance()
                            when {
                                url.startsWith("https://music.youtube.com") -> {
                                    val cookie = cm.getCookie(url)
                                    if (cookie != null && cookie.contains("SAPISID")) {
                                        delivered = true
                                        currentOnCookie(cookie)
                                    }
                                }
                                // Si la sesión ya quedó abierta pero Google nos dejó en YouTube normal, ir a YouTube Music
                                !sentToMusic && (url.startsWith("https://www.youtube.com") ||
                                    url.startsWith("https://m.youtube.com")) -> {
                                    val cookie = cm.getCookie(url)
                                    if (cookie != null && cookie.contains("SAPISID")) {
                                        sentToMusic = true
                                        view.loadUrl(MUSIC_URL)
                                    }
                                }
                            }
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
            onRelease = { it.destroy() }
        )
    }
    }
}
