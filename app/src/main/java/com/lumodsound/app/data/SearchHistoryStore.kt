package com.lumodsound.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Historial de búsquedas (lo último primero), guardado en el teléfono. Se muestra al tocar la barra de búsqueda. */
class SearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    private fun load(): List<String> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, JSONArray(list).toString()).apply()
        _items.value = list
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        save((listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX))
    }

    fun remove(query: String) = save(_items.value.filterNot { it == query })

    fun clear() = save(emptyList())

    private companion object {
        const val KEY = "items"
        const val MAX = 50
    }
}
