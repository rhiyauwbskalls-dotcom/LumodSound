package com.lumodsound.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Caché de letras en disco: una letra que ya se bajó vuelve a salir al instante y sin internet.
 *
 *  - Vive en `filesDir` (no en `cacheDir`) para que el sistema no la borre al liberar espacio.
 *  - Solo se guardan letras encontradas (sincronizadas, sin sincronizar o "instrumental"). "No encontrada"
 *    y los errores no se guardan, para que se vuelva a intentar la próxima vez.
 *  - Cada letra pesa unos pocos KB, así que no lleva límite de tamaño; se borra desde Ajustes.
 *  - Llamar siempre fuera del hilo principal.
 */
object LyricsCache {

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "lyrics_cache").also { it.mkdirs() }

    private fun fileFor(context: Context, key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(32)
        return File(dir(context), "$hash.json")
    }

    fun get(context: Context, key: String): LyricsState? {
        val f = fileFor(context, key)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText())
            when (json.optString("type")) {
                "synced" -> {
                    val arr = json.getJSONArray("lines")
                    val lines = List(arr.length()) { i ->
                        val o = arr.getJSONObject(i)
<<<<<<< HEAD
                        val w = o.optJSONArray("w")
                        val words = if (w == null) emptyList() else List(w.length()) { j ->
                            val wo = w.getJSONObject(j)
                            LyricWord(wo.getLong("b"), wo.getLong("e"), wo.getString("x"))
                        }
                        LyricLine(o.getLong("t"), o.getString("x"), words)
                    }
                    if (lines.isEmpty()) null else LyricsState.Synced(lines)
                }
                "plain" -> {
                    val arr = json.getJSONArray("lines")
                    val lines = List(arr.length()) { i -> arr.getString(i) }
                    if (lines.isEmpty()) null else LyricsState.Plain(lines)
                }
                "instrumental" -> LyricsState.Instrumental
                else -> null
            }
        } catch (e: Exception) {
            // archivo dañado: se descarta y la letra se vuelve a pedir
            f.delete()
            null
        }
    }

    fun put(context: Context, key: String, state: LyricsState) {
        val json = JSONObject()
        when (state) {
            is LyricsState.Synced -> {
                val arr = JSONArray()
                state.lines.forEach { line ->
                    val o = JSONObject().put("t", line.timeMs).put("x", line.text)
                    if (line.words.isNotEmpty()) {
                        val w = JSONArray()
                        line.words.forEach { w.put(JSONObject().put("b", it.beginMs).put("e", it.endMs).put("x", it.text)) }
                        o.put("w", w)
                    }
                    arr.put(o)
                }
=======
<<<<<<< HEAD
                        val w = o.optJSONArray("w")
                        val words = if (w == null) emptyList() else List(w.length()) { j ->
                            val wo = w.getJSONObject(j)
                            LyricWord(wo.getLong("b"), wo.getLong("e"), wo.getString("x"))
                        }
                        LyricLine(o.getLong("t"), o.getString("x"), words)
                    }
                    if (lines.isEmpty()) null else LyricsState.Synced(lines)
                }
                "plain" -> {
                    val arr = json.getJSONArray("lines")
                    val lines = List(arr.length()) { i -> arr.getString(i) }
                    if (lines.isEmpty()) null else LyricsState.Plain(lines)
                }
                "instrumental" -> LyricsState.Instrumental
                else -> null
            }
        } catch (e: Exception) {
            // archivo dañado: se descarta y la letra se vuelve a pedir
            f.delete()
            null
        }
    }

    fun put(context: Context, key: String, state: LyricsState) {
        val json = JSONObject()
        when (state) {
            is LyricsState.Synced -> {
                val arr = JSONArray()
                state.lines.forEach { line ->
                    val o = JSONObject().put("t", line.timeMs).put("x", line.text)
                    if (line.words.isNotEmpty()) {
                        val w = JSONArray()
                        line.words.forEach { w.put(JSONObject().put("b", it.beginMs).put("e", it.endMs).put("x", it.text)) }
                        o.put("w", w)
                    }
                    arr.put(o)
                }
=======
                        LyricLine(o.getLong("t"), o.getString("x"))
                    }
                    if (lines.isEmpty()) null else LyricsState.Synced(lines)
                }
                "plain" -> {
                    val arr = json.getJSONArray("lines")
                    val lines = List(arr.length()) { i -> arr.getString(i) }
                    if (lines.isEmpty()) null else LyricsState.Plain(lines)
                }
                "instrumental" -> LyricsState.Instrumental
                else -> null
            }
        } catch (e: Exception) {
            // archivo dañado: se descarta y la letra se vuelve a pedir
            f.delete()
            null
        }
    }

    fun put(context: Context, key: String, state: LyricsState) {
        val json = JSONObject()
        when (state) {
            is LyricsState.Synced -> {
                val arr = JSONArray()
                state.lines.forEach { arr.put(JSONObject().put("t", it.timeMs).put("x", it.text)) }
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
                json.put("type", "synced").put("lines", arr)
            }
            is LyricsState.Plain -> {
                val arr = JSONArray()
                state.lines.forEach { arr.put(it) }
                json.put("type", "plain").put("lines", arr)
            }
            is LyricsState.Instrumental -> json.put("type", "instrumental")
            else -> return // Loading, NotFound y Error no se guardan
        }
        try {
            val f = fileFor(context, key)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (e: Exception) {
            // sin espacio o sin permiso: simplemente no se guarda
        }
    }

    fun usedBytes(context: Context): Long =
        dir(context).listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
