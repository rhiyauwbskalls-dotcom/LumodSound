package com.lumodsound.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpotifySettings(
    /** Hay una cookie sp_dc guardada (sesión de Spotify iniciada). */
    val connected: Boolean = false,
    /** Usar las letras sincronizadas de Spotify. */
    val lyrics: Boolean = true,
    /** Mostrar el Canvas (video corto en bucle) de Spotify en el reproductor. */
    val canvas: Boolean = true,
    /** Último resultado/error, para saber en Ajustes por qué algo no salió. */
    val lastResult: String? = null,
    /** Nombre de la cuenta de Spotify, leído con el token; null si aún no se ha podido verificar la sesión. */
    val userName: String? = null
)

/**
 * Sesión de Spotify y opciones. La cookie sp_dc y el token de acceso van cifrados (ver [TokenCipher]);
 * si el cifrado falla no se guarda nada.
 */
class SpotifyStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SpotifySettings> = _settings.asStateFlow()

    private fun load() = SpotifySettings(
        connected = !spDc().isNullOrBlank(),
        lyrics = prefs.getBoolean("lyrics", true),
        canvas = prefs.getBoolean("canvas", true),
        lastResult = prefs.getString("last_result", null),
        userName = prefs.getString("user_name", null)
    )

    fun spDc(): String? = prefs.getString("sp_dc_enc", null)?.let(TokenCipher::decrypt)?.takeIf { it.isNotBlank() }

    /**
     * Saca el valor de sp_dc de lo que se haya pegado: solo el valor, `sp_dc=valor`, o una cadena de cookies completa
     * (`sp_key=...; sp_dc=...`), con o sin comillas.
     */
    fun extractSpDc(raw: String): String {
        val text = raw.trim().removePrefix("Cookie:").removePrefix("cookie:").trim()
        val inList = Regex("(?:^|;\\s*)sp_dc=([^;\\s]+)").find(text)?.groupValues?.get(1)
        val value = inList ?: text.substringBefore(';')
        return value.trim().trim('"', '\'')
    }

    /** Guarda la cookie sp_dc (solo el valor). Devuelve false si no se pudo cifrar. */
    fun saveSpDc(value: String): Boolean {
        val clean = extractSpDc(value)
        if (clean.isBlank()) return false
        val enc = TokenCipher.encrypt(clean) ?: return false
        prefs.edit().putString("sp_dc_enc", enc).remove("token_enc").remove("token_exp").remove("user_name").apply()
        _settings.update { it.copy(connected = true, lastResult = null, userName = null) }
        prefs.edit().remove("last_result").apply()
        return true
    }

    fun disconnect() {
        prefs.edit().remove("sp_dc_enc").remove("token_enc").remove("token_exp").remove("last_result").remove("user_name").apply()
        _settings.update { it.copy(connected = false, lastResult = null, userName = null) }
    }

    fun setUserName(name: String?) {
        prefs.edit().apply { if (name == null) remove("user_name") else putString("user_name", name) }.apply()
        _settings.update { it.copy(userName = name) }
    }

    /** Token de acceso guardado y cuándo caduca (ms), o null si no hay o ya caducó. */
    fun cachedToken(): Pair<String, Long>? {
        val exp = prefs.getLong("token_exp", 0L)
        if (exp <= System.currentTimeMillis() + 60_000) return null
        val token = prefs.getString("token_enc", null)?.let(TokenCipher::decrypt)?.takeIf { it.isNotBlank() } ?: return null
        return token to exp
    }

    fun saveToken(token: String, expiresAtMs: Long) {
        val enc = TokenCipher.encrypt(token) ?: return
        prefs.edit().putString("token_enc", enc).putLong("token_exp", expiresAtMs).apply()
    }

    fun clearToken() {
        prefs.edit().remove("token_enc").remove("token_exp").apply()
    }

    fun setLyrics(v: Boolean) {
        prefs.edit().putBoolean("lyrics", v).apply()
        _settings.update { it.copy(lyrics = v) }
    }

    fun setCanvas(v: Boolean) {
        prefs.edit().putBoolean("canvas", v).apply()
        _settings.update { it.copy(canvas = v) }
    }

    fun setLastResult(text: String?) {
        prefs.edit().putString("last_result", text).apply()
        _settings.update { it.copy(lastResult = text) }
    }

    companion object {
        @Volatile private var instance: SpotifyStore? = null
        fun get(context: Context): SpotifyStore =
            instance ?: synchronized(this) { instance ?: SpotifyStore(context).also { instance = it } }
    }
}
