package com.lumodsound.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lumodsound.app.data.DownloadedSongsStore
import com.lumodsound.app.data.SongUi
import com.lumodsound.app.data.YtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Descargas de canciones (como Metrolist): van a una caché propia que NUNCA se limpia sola; solo se
 * borran cuando el usuario quita la descarga o las borra todas en Ajustes. Se descargan con el
 * DownloadManager de media3 y un servicio en primer plano con notificación de progreso.
 */
@OptIn(UnstableApi::class)
object Downloads {

    /** Estado simplificado de una canción para la interfaz. */
    const val NONE = 0
    const val IN_PROGRESS = 1
    const val DONE = 2

    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null
    private val repo by lazy { YtRepository() }
    private val io = Executors.newSingleThreadExecutor()

    private val _states = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** URL de la canción -> [IN_PROGRESS] o [DONE] (las demás no aparecen). */
    val states: StateFlow<Map<String, Int>> = _states.asStateFlow()

    @Synchronized
    fun cache(context: Context): SimpleCache {
        cache?.let { return it }
        val app = context.applicationContext
        return SimpleCache(
            File(app.filesDir, "downloads"),
            NoOpCacheEvictor(), // nada se borra automáticamente
            MediaCache.databaseProvider(app)
        ).also { cache = it }
    }

    /** Debe pedirse desde el hilo principal (así lo exige DownloadManager). */
    @Synchronized
    fun manager(context: Context): DownloadManager {
        manager?.let { return it }
        val app = context.applicationContext

        // Lo que ya se escuchó (caché de reproducción) se reutiliza sin volver a bajarlo
        val upstream = ResolvingDataSource.Factory(
            CacheDataSource.Factory()
                .setCache(MediaCache.get(app))
                .setCacheWriteDataSinkFactory(null)
                .setUpstreamDataSourceFactory(
                    // Descarga por tramos de 1 MB: evita el límite de velocidad que YouTube pone
                    // a las peticiones largas (ver ChunkedDataSource)
                    DataSource.Factory {
                        ChunkedDataSource(
                            DefaultHttpDataSource.Factory()
                                .setAllowCrossProtocolRedirects(true)
                                .createDataSource()
                        )
                    }
                ),
            ResolvingDataSource.Resolver { spec -> AudioResolver.resolve(app, repo, spec) }
        )

        val created = DownloadManager(
            app,
            MediaCache.databaseProvider(app),
            cache(app),
            upstream,
            Executor { it.run() } // cada descarga en su propio hilo
        )
        created.maxParallelDownloads = 3

        val initial = HashMap<String, Int>()
        created.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val d = cursor.download
                toUi(d.state).takeIf { it != NONE }?.let { initial[d.request.id] = it }
            }
        }
        _states.value = initial

        created.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                val id = download.request.id
                val ui = toUi(download.state)
                _states.update { if (ui == NONE) it - id else it + (id to ui) }
                if (download.state == Download.STATE_COMPLETED) {
                    // Ya está en la caché de descargas: se quita la copia temporal para no duplicar
                    io.execute {
                        try {
                            MediaCache.get(app).removeResource(id)
                        } catch (e: Exception) {
                            // si no se puede borrar, la caché de reproducción la recogerá por antigüedad
                        }
                    }
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                val id = download.request.id
                _states.update { it - id }
                DownloadedSongsStore.remove(app, id)
            }
        })
        manager = created
        return created
    }

    private fun toUi(state: Int): Int = when (state) {
        Download.STATE_COMPLETED -> DONE
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> IN_PROGRESS
        else -> NONE
    }

    /** Crea el gestor de descargas (desde el hilo principal) para conocer lo que ya está descargado. */
    fun ensureReady(context: Context) {
        manager(context)
    }

    /** Pide la descarga de [song] (permanente hasta que se quite). */
    fun download(context: Context, song: SongUi) {
        val app = context.applicationContext
        manager(app) // asegura que el gestor existe y escucha los cambios
        DownloadedSongsStore.record(app, song)
        val request = DownloadRequest.Builder(song.url, Uri.parse(song.url))
            .setCustomCacheKey(song.url)
            .setData(song.title.toByteArray())
            .build()
        DownloadService.sendAddDownload(app, LumodSoundDownloadService::class.java, request, false)

        // La portada también se guarda para verla sin internet
        song.thumb?.let { thumb ->
            app.imageLoader.enqueue(
                ImageRequest.Builder(app)
                    .data(thumb)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    /** Quita la descarga (o la cancela si está en curso) y libera su espacio. */
    fun remove(context: Context, url: String) {
        val app = context.applicationContext
        manager(app)
        DownloadService.sendRemoveDownload(app, LumodSoundDownloadService::class.java, url, false)
    }

    /** Borra todas las descargas. */
    fun removeAll(context: Context) {
        val app = context.applicationContext
        manager(app)
        DownloadService.sendRemoveAllDownloads(app, LumodSoundDownloadService::class.java, false)
        DownloadedSongsStore.clear(app)
    }

    /** Canciones descargadas del todo, la más reciente primero. */
    fun completedSongs(context: Context): List<SongUi> {
        val done = _states.value
        return DownloadedSongsStore.all(context)
            .filter { done[it.first.url] == DONE }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** Espacio que ocupan las descargas. */
    fun usedBytes(context: Context): Long = cache(context).cacheSpace
}
