package com.lumodsound.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Proveedores de letras que se pueden elegir en Ajustes. Spotify no está aquí: si está conectado se consulta justo después del elegido. */
enum class LyricsProvider(val id: String, val label: String) {
    BETTERLYRICS("bl", "BetterLyrics"),
    LRCLIB("lrc", "LRCLIB")
}

/** Proveedor principal de letras (va primero); luego Spotify si está conectado y al final el otro de respaldo. */
object LyricsProviderSettings {
    private const val PREFS = "settings"
    private const val KEY = "lyrics_provider"

    var main by mutableStateOf(LyricsProvider.LRCLIB)
        private set

    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        val id = prefs(context).getString(KEY, null)
        main = LyricsProvider.values().firstOrNull { it.id == id } ?: LyricsProvider.LRCLIB
        loaded = true
    }

    fun set(context: Context, value: LyricsProvider) {
        load(context)
        main = value
        prefs(context).edit().putString(KEY, value.id).apply()
    }
}
