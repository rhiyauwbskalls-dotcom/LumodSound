package com.lumodsound.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Historial local de lo último que escuchaste (SharedPreferences, sin dependencias extra). */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<SongUi> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<SongUi>) {
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
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "recents"
    }
}
