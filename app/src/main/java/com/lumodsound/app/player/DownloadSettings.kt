package com.lumodsound.app.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ajustes de descargas. De momento uno: descargar solas las canciones que se guardan en Favoritos
 * (el corazón del reproductor clásico o la estrella del reproductor estilo Apple Music).
 */
object DownloadSettings {
    private const val PREFS = "settings"
    private const val KEY_AUTO_FAVORITES = "auto_download_favorites"

    var autoFavorites by mutableStateOf(false)
        private set

    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        autoFavorites = prefs(context).getBoolean(KEY_AUTO_FAVORITES, false)
        loaded = true
    }

    fun setAutoFavorites(context: Context, on: Boolean) {
        load(context)
        autoFavorites = on
        prefs(context).edit().putBoolean(KEY_AUTO_FAVORITES, on).apply()
    }
}
