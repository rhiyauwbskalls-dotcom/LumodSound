package com.lumodsound.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.lumodsound.app.data.CachedSongsStore
import com.lumodsound.app.data.SongUi
import java.io.File
import java.util.TreeSet

/**
 * Caché de audio en disco: guarda lo que escuchas y los pedazos precargados de la siguiente
 * canción, así al volver a una canción suena al instante (y sin internet si ya está entera).
 *
 *  - Vive en `filesDir` (no en `cacheDir`) para que el sistema no la borre al liberar espacio.
 *  - El tamaño máximo lo elige el usuario en Ajustes (se borra lo más viejo al llegar al límite).
 *  - Debe haber una sola instancia por proceso.
 */
@OptIn(UnstableApi::class)
object MediaCache {

    const val UNLIMITED = -1
    const val DEFAULT_MB = 1024

    /** Tamaños que se ofrecen en Ajustes (en MB; [UNLIMITED] = sin límite). */
    val SIZE_OPTIONS_MB = listOf(512, 1024, 2048, 4096, UNLIMITED)

    private var cache: SimpleCache? = null
    private var evictor: ResizableLruEvictor? = null
    private var dbProvider: StandaloneDatabaseProvider? = null

    /** Base de datos que comparten la caché de reproducción, la de descargas y el gestor de descargas. */
    @Synchronized
    fun databaseProvider(context: Context): StandaloneDatabaseProvider =
        dbProvider ?: StandaloneDatabaseProvider(context.applicationContext).also { dbProvider = it }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("media_cache", Context.MODE_PRIVATE)

    /** Formato (itag) con el que se guardó cada canción: si cambia, los bytes viejos no sirven. */
    private fun formats(context: Context) =
        context.applicationContext.getSharedPreferences("media_cache_formats", Context.MODE_PRIVATE)

    private fun bytesFor(mb: Int): Long = if (mb == UNLIMITED) Long.MAX_VALUE else mb * 1024L * 1024L

    fun maxMb(context: Context): Int = prefs(context).getInt("max_mb", DEFAULT_MB)

    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: create(context.applicationContext)

    private fun create(app: Context): SimpleCache {
        val db = databaseProvider(app)

        // La caché de versiones anteriores (en cacheDir, 300 MB) ya no se usa: se borra
        val old = File(app.cacheDir, "media_cache")
        if (old.exists()) {
            try {
                SimpleCache.delete(old, db)
            } catch (e: Exception) {
                old.deleteRecursively()
            }
        }

        val lru = ResizableLruEvictor(bytesFor(maxMb(app)))
        return SimpleCache(File(app.filesDir, "media_cache"), lru, db).also {
            cache = it
            evictor = lru
        }
    }

    /** Cambia el límite al instante; si ahora es menor, borra lo más viejo hasta entrar. */
    fun setMaxMb(context: Context, mb: Int) {
        prefs(context).edit().putInt("max_mb", mb).apply()
        val c = synchronized(this) { cache }
        val e = synchronized(this) { evictor }
        if (c != null && e != null) {
            e.maxBytes = bytesFor(mb)
            synchronized(c) { e.trim(c) }
        }
    }

    /** Espacio que ocupa ahora la caché. */
    fun usedBytes(context: Context): Long = get(context).cacheSpace

    /**
     * Canciones que están enteras en disco (0 hasta el final), la última escuchada primero.
     * Ejecutar fuera del hilo principal.
     */
    fun fullyCachedSongs(context: Context): List<SongUi> {
        val c = get(context)
        val found = ArrayList<Pair<SongUi, Long>>()
        for ((song, lastPlayed) in CachedSongsStore.all(context)) {
            val total = ContentMetadata.getContentLength(c.getContentMetadata(song.url))
            if (total > 0 && c.isCached(song.url, 0, total)) {
                found += song to lastPlayed
            } else if (c.getCachedSpans(song.url).isEmpty()) {
                // ya no queda nada de esta canción (la caché la fue borrando): se olvida
                CachedSongsStore.remove(context, song.url)
            }
        }
        return found.sortedByDescending { it.second }.map { it.first }
    }

    /** Borra todo el audio guardado. */
    fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach {
            try {
                c.removeResource(it)
            } catch (e: Exception) {
                // un archivo en uso: se queda y el límite lo recogerá más adelante
            }
        }
        formats(context).edit().clear().apply()
        CachedSongsStore.clear(context)
    }

    /**
     * Anota el formato con el que se guarda [key]. Si ya había bytes de otro formato, se descartan:
     * mezclar dos formatos en un mismo archivo daría un audio corrupto.
     */
    fun checkFormat(context: Context, key: String, itag: Int) {
        val p = formats(context)
        val old = p.getInt(key, -1)
        if (old != -1 && old != itag) {
            try {
                get(context).removeResource(key)
            } catch (e: Exception) {
                // si no se puede borrar, el reproductor reintentará con la red
            }
        }
        if (old != itag) p.edit().putInt(key, itag).apply()
    }

    @Synchronized
    fun release() {
        cache?.release()
        cache = null
        evictor = null
    }
}

/**
 * Expulsión por "lo menos usado primero" (igual que LeastRecentlyUsedCacheEvictor de media3),
 * pero con el límite modificable en caliente para poder cambiarlo desde Ajustes sin reiniciar.
 */
@OptIn(UnstableApi::class)
private class ResizableLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan>(
        Comparator { a, b ->
            val diff = a.lastTouchTimestamp - b.lastTouchTimestamp
            if (diff == 0L) a.compareTo(b) else if (a.lastTouchTimestamp < b.lastTouchTimestamp) -1 else 1
        }
    )
    private var currentSize = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /** Aplica un límite nuevo ya mismo (se llama dentro del bloqueo de la caché). */
    fun trim(cache: Cache) = evict(cache, 0)

    private fun evict(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}
