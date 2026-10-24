package com.lumodsound.app.data

import android.content.Context
import org.json.JSONObject
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Datos de las canciones que han sonado (título, artista, portada...). La caché de audio solo guarda
 * bytes, así que esto permite mostrar la tarjeta "Caché" de la Biblioteca con las canciones que están
 * descargadas al 100%. Se muestra solo lo que además siga entero en disco (ver MediaCache.fullyCachedSongs).
 */
object CachedSongsStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("cached_songs", Context.MODE_PRIVATE)

    /** Anota o actualiza una canción; la fecha sirve para ordenar (la última escuchada primero). */
    fun record(context: Context, song: SongUi) {
        val json = JSONObject()
            .put("t", song.title)
            .put("a", song.artist)
            .put("d", song.durationSec)
            .put("ts", System.currentTimeMillis())
        song.thumb?.let { json.put("th", it) }
        prefs(context).edit().putString(song.url, json.toString()).apply()
    }

    /** Todas las canciones anotadas con la fecha en que sonaron por última vez. */
    fun all(context: Context): List<Pair<SongUi, Long>> =
        prefs(context).all.mapNotNull { (url, value) ->
            try {
                val j = JSONObject(value as? String ?: return@mapNotNull null)
                SongUi(
                    url = url,
                    title = j.optString("t", str(R.string.untitled)),
=======

/**
 * Datos de las canciones que han sonado (título, artista, portada...). La caché de audio solo guarda
 * bytes, así que esto permite mostrar la tarjeta "Caché" de la Biblioteca con las canciones que están
 * descargadas al 100%. Se muestra solo lo que además siga entero en disco (ver MediaCache.fullyCachedSongs).
 */
object CachedSongsStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("cached_songs", Context.MODE_PRIVATE)

    /** Anota o actualiza una canción; la fecha sirve para ordenar (la última escuchada primero). */
    fun record(context: Context, song: SongUi) {
        val json = JSONObject()
            .put("t", song.title)
            .put("a", song.artist)
            .put("d", song.durationSec)
            .put("ts", System.currentTimeMillis())
        song.thumb?.let { json.put("th", it) }
        prefs(context).edit().putString(song.url, json.toString()).apply()
    }

    /** Todas las canciones anotadas con la fecha en que sonaron por última vez. */
    fun all(context: Context): List<Pair<SongUi, Long>> =
        prefs(context).all.mapNotNull { (url, value) ->
            try {
                val j = JSONObject(value as? String ?: return@mapNotNull null)
                SongUi(
                    url = url,
                    title = j.optString("t", "Sin título"),
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
                    artist = j.optString("a", ""),
                    durationSec = j.optLong("d", 0L),
                    thumb = if (j.has("th")) j.optString("th") else null
                ) to j.optLong("ts", 0L)
            } catch (e: Exception) {
                null
            }
        }

    fun remove(context: Context, url: String) {
        prefs(context).edit().remove(url).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
