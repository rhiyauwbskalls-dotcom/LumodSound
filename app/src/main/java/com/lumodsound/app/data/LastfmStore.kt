package com.lumodsound.app.data

import android.content.Context
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Credenciales de la API de Last.fm que usa la app (la pantalla de Last.fm no las pide). NO van en el código:
 * se leen de app/src/main/assets/lastfm.properties, un archivo que no se sube a Git (ver lastfm.properties.example).
 * Se crean gratis en https://www.last.fm/api/account/create. Si el archivo falta, Last.fm queda sin credenciales.
 */
private fun lastfmKeys(context: Context): Pair<String, String> = try {
    context.applicationContext.assets.open("lastfm.properties").use { stream ->
        val p = Properties().apply { load(stream) }
        p.getProperty("LASTFM_API_KEY").orEmpty().trim() to p.getProperty("LASTFM_SECRET").orEmpty().trim()
    }
} catch (e: Exception) {
    "" to ""
}

data class LastfmSettings(
    val apiKey: String = "",
    val secret: String = "",
    val sessionKey: String? = null,
    val username: String? = null,
    /** Mandar cada canción escuchada (scrobble). */
    val enabled: Boolean = true,
    /** Avisar de la canción que suena ahora. */
    val nowPlaying: Boolean = true,
    /** Resultado del último aviso, para mostrarlo en Ajustes. */
    val lastResult: String? = null
) {
    val hasCredentials: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank()
    val connected: Boolean get() = !sessionKey.isNullOrBlank()
}

/**
 * Cuenta de Last.fm y opciones. Es único para toda la app (Ajustes y el servicio de reproducción comparten el
 * mismo flujo). El secreto y la clave de sesión van cifrados (ver [TokenCipher]); si el cifrado falla no se guarda nada.
 */
class LastfmStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lastfm", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load(context))
    val settings: StateFlow<LastfmSettings> = _settings.asStateFlow()

    private fun load(context: Context): LastfmSettings {
        val (apiKey, secret) = lastfmKeys(context)
        return loadWith(apiKey, secret)
    }

    private fun loadWith(apiKey: String, secret: String) = LastfmSettings(
        apiKey = apiKey,
        secret = secret,
        sessionKey = prefs.getString("session_enc", null)?.let(TokenCipher::decrypt)?.takeIf { it.isNotBlank() },
        username = prefs.getString("username", null),
        enabled = prefs.getBoolean("enabled", true),
        nowPlaying = prefs.getBoolean("now_playing", true),
        lastResult = prefs.getString("last_result", null)
    )

    private fun edit(change: LastfmSettings.() -> LastfmSettings) {
        _settings.update { it.change() }
        val s = _settings.value
        prefs.edit()
            .putString("session_enc", s.sessionKey?.let(TokenCipher::encrypt))
            .putString("username", s.username)
            .putBoolean("enabled", s.enabled)
            .putBoolean("now_playing", s.nowPlaying)
            .putString("last_result", s.lastResult)
            .apply()
    }

    fun connect(sessionKey: String, username: String) = edit {
        copy(sessionKey = sessionKey, username = username, enabled = true, lastResult = null)
    }

    /** Cierra la sesión para poder volver a entrar con otra cuenta. */
    fun disconnect() = edit { copy(sessionKey = null, username = null, lastResult = null) }

    /** Token de un inicio de sesión a medias: se guarda por si el sistema cierra la app mientras el usuario está en el navegador. */
    fun savePendingToken(token: String) {
        prefs.edit().putString("pending_token", token).putLong("pending_at", System.currentTimeMillis()).apply()
    }

    fun pendingToken(): String? = prefs.getString("pending_token", null)?.takeIf { it.isNotBlank() }

    fun clearPendingToken() {
        prefs.edit().remove("pending_token").remove("pending_at").apply()
    }

    fun setEnabled(v: Boolean) = edit { copy(enabled = v) }
    fun setNowPlaying(v: Boolean) = edit { copy(nowPlaying = v) }
    fun setLastResult(text: String?) = edit { copy(lastResult = text) }

    companion object {
        @Volatile private var instance: LastfmStore? = null
        fun get(context: Context): LastfmStore =
            instance ?: synchronized(this) { instance ?: LastfmStore(context).also { instance = it } }
    }
}
