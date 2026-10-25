package com.lumodsound.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lumodsound.app.data.AppDownloader
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

class LumodSoundApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
<<<<<<< HEAD
        AppText.base = this
        LanguageSettings.load(this)
        AppText.refresh()
=======
<<<<<<< HEAD
        AppText.base = this
        LanguageSettings.load(this)
        AppText.refresh()
=======
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
        NewPipe.init(AppDownloader())
    }

    /**
     * Portadas: máximo 6 descargas a la vez por servidor (en vez de decenas de golpe),
     * caché en memoria y en disco para no volver a bajarlas, y aparición suave.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .dispatcher(Dispatcher().apply {
                        maxRequests = 16
                        maxRequestsPerHost = 6
                    })
                    .build()
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
