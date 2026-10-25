package com.lumodsound.app.data

import android.content.Context
import org.json.JSONObject
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Datos (título, artista, portada...) de las canciones que el usuario descarga. Aparte de la lista
 * de canciones en caché: esta no se poda sola, porque las descargas son permanentes hasta que el
 * usuario las quite. Permite mostrar la tarjeta "Descargas" también sin internet.
 */
object DownloadedSongsStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("downloaded_songs", Context.MODE_PRIVATE)

    fun record(context: Context, song: SongUi) {
        val json = JSONObject()
            .put("t", song.title)
            .put("a", song.artist)
            .put("d", song.durationSec)
            .put("ts", System.currentTimeMillis())
        song.thumb?.let { json.put("th", it) }
        prefs(context).edit().putString(song.url, json.toString()).apply()
    }

    /** Canciones anotadas con la fecha en que se pidió la descarga. */
    fun all(context: Context): List<Pair<SongUi, Long>> =
        prefs(context).all.mapNotNull { (url, value) ->
            try {
                val j = JSONObject(value as? String ?: return@mapNotNull null)
                SongUi(
                    url = url,
                    title = j.optString("t", str(R.string.untitled)),
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Datos (título, artista, portada...) de las canciones que el usuario descarga. Aparte de la lista
 * de canciones en caché: esta no se poda sola, porque las descargas son permanentes hasta que el
 * usuario las quite. Permite mostrar la tarjeta "Descargas" también sin internet.
 */
object DownloadedSongsStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("downloaded_songs", Context.MODE_PRIVATE)

    fun record(context: Context, song: SongUi) {
        val json = JSONObject()
            .put("t", song.title)
            .put("a", song.artist)
            .put("d", song.durationSec)
            .put("ts", System.currentTimeMillis())
        song.thumb?.let { json.put("th", it) }
        prefs(context).edit().putString(song.url, json.toString()).apply()
    }

    /** Canciones anotadas con la fecha en que se pidió la descarga. */
    fun all(context: Context): List<Pair<SongUi, Long>> =
        prefs(context).all.mapNotNull { (url, value) ->
            try {
                val j = JSONObject(value as? String ?: return@mapNotNull null)
                SongUi(
                    url = url,
                    title = j.optString("t", str(R.string.untitled)),
=======

/**
 * Datos (título, artista, portada...) de las canciones que el usuario descarga. Aparte de la lista
 * de canciones en caché: esta no se poda sola, porque las descargas son permanentes hasta que el
 * usuario las quite. Permite mostrar la tarjeta "Descargas" también sin internet.
 */
object DownloadedSongsStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("downloaded_songs", Context.MODE_PRIVATE)

    fun record(context: Context, song: SongUi) {
        val json = JSONObject()
            .put("t", song.title)
            .put("a", song.artist)
            .put("d", song.durationSec)
            .put("ts", System.currentTimeMillis())
        song.thumb?.let { json.put("th", it) }
        prefs(context).edit().putString(song.url, json.toString()).apply()
    }

    /** Canciones anotadas con la fecha en que se pidió la descarga. */
    fun all(context: Context): List<Pair<SongUi, Long>> =
        prefs(context).all.mapNotNull { (url, value) ->
            try {
                val j = JSONObject(value as? String ?: return@mapNotNull null)
                SongUi(
                    url = url,
                    title = j.optString("t", "Sin título"),
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
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
