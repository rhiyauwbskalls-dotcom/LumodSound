package com.lumodsound.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<SongUi> = emptyList()
)

/** Favoritos y playlists guardados en el teléfono (SharedPreferences + JSON). */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun loadFavorites(): List<SongUi> {
        val raw = prefs.getString(KEY_FAV, null) ?: return emptyList()
        return try {
            songsFromJson(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFavorites(list: List<SongUi>) {
        prefs.edit().putString(KEY_FAV, songsToJson(list).toString()).apply()
    }

    fun loadPlaylists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    songs = songsFromJson(o.getJSONArray("songs"))
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", songsToJson(p.songs))
            )
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    private fun songsToJson(list: List<SongUi>): JSONArray {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("url", s.url)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("dur", s.durationSec)
                    .put("thumb", s.thumb ?: "")
            )
        }
        return arr
    }

    private fun songsFromJson(arr: JSONArray): List<SongUi> =
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SongUi(
                url = o.getString("url"),
                title = o.getString("title"),
                artist = o.getString("artist"),
                durationSec = o.getLong("dur"),
                thumb = o.optString("thumb").ifEmpty { null }
            )
        }

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_PLAYLISTS = "playlists"
    }
}
