package com.sonaris.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Dirección guardada del Canvas de una canción y cuándo se guardó. */
data class SavedCanvas(val url: String, val savedAt: Long) {
    /** Reciente: se usa sin volver a preguntar a Spotify. */
    val fresh: Boolean get() = System.currentTimeMillis() - savedAt < FRESH_MS

    /** Vieja pero aprovechable si Spotify no responde. */
    val usable: Boolean get() = System.currentTimeMillis() - savedAt < MAX_STALE_MS

    private companion object {
        const val FRESH_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_STALE_MS = 60L * 24 * 60 * 60 * 1000
    }
}

/**
 * Caché del Canvas de Spotify, en dos partes:
 *  1. la dirección del video de cada canción (así no hace falta pedirla otra vez a Spotify), y
 *  2. los bytes del video, que se guardan al reproducirlo (así vuelve a verse sin gastar datos).
 *
 * Vive en `filesDir` y el video tiene un tope de [MAX_BYTES] (se borra lo más viejo al llegar).
 * Debe haber una sola instancia de [SimpleCache] por proceso.
 */
@OptIn(UnstableApi::class)
object CanvasCache {

    private const val MAX_BYTES = 256L * 1024 * 1024

    private var cache: SimpleCache? = null

    private fun urls(context: Context) =
        context.applicationContext.getSharedPreferences("canvas_urls", Context.MODE_PRIVATE)

    @Synchronized
    private fun get(context: Context): SimpleCache {
        cache?.let { return it }
        val app = context.applicationContext
        return SimpleCache(
            File(app.filesDir, "canvas_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            MediaCache.databaseProvider(app)
        ).also { cache = it }
    }

    /** Fuente de datos para el reproductor del Canvas: lee de disco si ya está y, si no, baja y guarda. */
    fun dataSourceFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun getSaved(context: Context, songKey: String): SavedCanvas? {
        val raw = urls(context).getString(songKey, null) ?: return null
        val sep = raw.indexOf('|')
        if (sep <= 0) return null
        val at = raw.substring(0, sep).toLongOrNull() ?: return null
        val url = raw.substring(sep + 1)
        return if (url.isBlank()) null else SavedCanvas(url, at)
    }

    fun saveUrl(context: Context, songKey: String, url: String) {
        urls(context).edit().putString(songKey, "${System.currentTimeMillis()}|$url").apply()
    }

    fun forgetUrl(context: Context, songKey: String) {
        urls(context).edit().remove(songKey).apply()
    }

    /** Espacio que ocupan los videos guardados. */
    fun usedBytes(context: Context): Long = get(context).cacheSpace

    /** Hay algo guardado (direcciones o videos): sirve para decidir si se muestra la tarjeta en Ajustes. */
    fun hasData(context: Context): Boolean = urls(context).all.isNotEmpty() || usedBytes(context) > 0

    fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach {
            try {
                c.removeResource(it)
            } catch (e: Exception) {
                // un archivo en uso: se queda y el límite lo recogerá más adelante
            }
        }
        urls(context).edit().clear().apply()
    }
}
