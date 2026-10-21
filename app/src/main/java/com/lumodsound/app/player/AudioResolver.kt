package com.lumodsound.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import com.lumodsound.app.data.YtRepository
import java.io.IOException

/**
 * Prepara las peticiones de audio, tanto al reproducir como al descargar. Si la canción ya está ENTERA
 * en disco (descargada o en la caché) no se le pide nada a YouTube: arranca al instante y sin internet.
 * Si no, se resuelve la URL real (caduca) y se lee/guarda con una clave estable: la propia canción.
 */
@OptIn(UnstableApi::class)
object AudioResolver {

    /** ¿Está en disco todo lo que falta por leer desde [position]? (descargas o caché de reproducción) */
    fun isFullyCached(context: Context, key: String, position: Long, length: Long): Boolean =
        fullyIn(Downloads.cache(context), key, position, length) ||
            fullyIn(MediaCache.get(context), key, position, length)

    private fun fullyIn(cache: Cache, key: String, position: Long, length: Long): Boolean {
        val total = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (total <= 0 || position >= total) return false
        val needed = if (length > 0) length else total - position
        return cache.isCached(key, position, needed)
    }

    fun resolve(context: Context, repo: YtRepository, dataSpec: DataSpec): DataSpec {
        val host = dataSpec.uri.host.orEmpty()
        if (!(host.endsWith("youtube.com") || host == "youtu.be")) return dataSpec

        val key = dataSpec.uri.toString()
        if (isFullyCached(context, key, dataSpec.position, dataSpec.length)) {
            return dataSpec.buildUpon().setKey(key).build()
        }

        val audio = try {
            repo.resolveAudioBlocking(key)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("No se pudo obtener el audio: ${e.message}", e)
        }
        MediaCache.checkFormat(context, audio.key, audio.itag)
        return dataSpec.buildUpon().setUri(Uri.parse(audio.url)).setKey(audio.key).build()
    }
}
