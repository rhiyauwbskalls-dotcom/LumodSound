package com.lumodsound.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Lee un archivo de YouTube en tramos de ~1 MB (una petición HTTP con rango por tramo) en vez de una
 * sola petición larga. YouTube frena (throttling) las peticiones largas hasta casi la velocidad de
 * reproducción, pero cada petición corta arranca a toda velocidad; es lo que hace yt-dlp con
 * `--http-chunk-size`. Para quien lo usa se comporta como una sola lectura continua.
 *
 * Si el servidor no respeta los rangos (no contesta con Content-Range), lee todo de golpe como antes.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: HttpDataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES
) : DataSource {

    private var base: DataSpec? = null
    private var position = 0L
    private var end = C.LENGTH_UNSET.toLong() // fin (exclusivo) de lo que se pidió, en posición absoluta
    private var chunkRemaining = 0L
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        base = dataSpec
        passthrough = false
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length
        else C.LENGTH_UNSET.toLong()

        val firstLength = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(chunkBytes, dataSpec.length)
        else chunkBytes
        val resolved = upstream.open(chunkSpec(dataSpec, position, firstLength))

        val total = totalLengthFrom(upstream.responseHeaders)
        if (total == null) {
            // Sin rangos: se vuelve a abrir completo y se lee de una vez
            upstream.close()
            passthrough = true
            return upstream.open(dataSpec)
        }

        chunkRemaining = if (resolved != C.LENGTH_UNSET.toLong()) resolved else firstLength
        if (end == C.LENGTH_UNSET.toLong() || end > total) end = total
        return end - dataSpec.position // lo que queda por leer, como si fuera una sola petición
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthrough) return upstream.read(buffer, offset, length)
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) openNextChunk()

        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val read = upstream.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            throw IOException(str(R.string.the_download_was_cut_off))
        }
        position += read
        chunkRemaining -= read
        return read
    }

    private fun openNextChunk() {
        upstream.close()
        val spec = base ?: throw IOException(str(R.string.data_source_closed))
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Lee un archivo de YouTube en tramos de ~1 MB (una petición HTTP con rango por tramo) en vez de una
 * sola petición larga. YouTube frena (throttling) las peticiones largas hasta casi la velocidad de
 * reproducción, pero cada petición corta arranca a toda velocidad; es lo que hace yt-dlp con
 * `--http-chunk-size`. Para quien lo usa se comporta como una sola lectura continua.
 *
 * Si el servidor no respeta los rangos (no contesta con Content-Range), lee todo de golpe como antes.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: HttpDataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES
) : DataSource {

    private var base: DataSpec? = null
    private var position = 0L
    private var end = C.LENGTH_UNSET.toLong() // fin (exclusivo) de lo que se pidió, en posición absoluta
    private var chunkRemaining = 0L
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        base = dataSpec
        passthrough = false
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length
        else C.LENGTH_UNSET.toLong()

        val firstLength = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(chunkBytes, dataSpec.length)
        else chunkBytes
        val resolved = upstream.open(chunkSpec(dataSpec, position, firstLength))

        val total = totalLengthFrom(upstream.responseHeaders)
        if (total == null) {
            // Sin rangos: se vuelve a abrir completo y se lee de una vez
            upstream.close()
            passthrough = true
            return upstream.open(dataSpec)
        }

        chunkRemaining = if (resolved != C.LENGTH_UNSET.toLong()) resolved else firstLength
        if (end == C.LENGTH_UNSET.toLong() || end > total) end = total
        return end - dataSpec.position // lo que queda por leer, como si fuera una sola petición
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthrough) return upstream.read(buffer, offset, length)
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) openNextChunk()

        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val read = upstream.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            throw IOException(str(R.string.the_download_was_cut_off))
        }
        position += read
        chunkRemaining -= read
        return read
    }

    private fun openNextChunk() {
        upstream.close()
        val spec = base ?: throw IOException(str(R.string.data_source_closed))
=======

/**
 * Lee un archivo de YouTube en tramos de ~1 MB (una petición HTTP con rango por tramo) en vez de una
 * sola petición larga. YouTube frena (throttling) las peticiones largas hasta casi la velocidad de
 * reproducción, pero cada petición corta arranca a toda velocidad; es lo que hace yt-dlp con
 * `--http-chunk-size`. Para quien lo usa se comporta como una sola lectura continua.
 *
 * Si el servidor no respeta los rangos (no contesta con Content-Range), lee todo de golpe como antes.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: HttpDataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES
) : DataSource {

    private var base: DataSpec? = null
    private var position = 0L
    private var end = C.LENGTH_UNSET.toLong() // fin (exclusivo) de lo que se pidió, en posición absoluta
    private var chunkRemaining = 0L
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        base = dataSpec
        passthrough = false
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length
        else C.LENGTH_UNSET.toLong()

        val firstLength = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(chunkBytes, dataSpec.length)
        else chunkBytes
        val resolved = upstream.open(chunkSpec(dataSpec, position, firstLength))

        val total = totalLengthFrom(upstream.responseHeaders)
        if (total == null) {
            // Sin rangos: se vuelve a abrir completo y se lee de una vez
            upstream.close()
            passthrough = true
            return upstream.open(dataSpec)
        }

        chunkRemaining = if (resolved != C.LENGTH_UNSET.toLong()) resolved else firstLength
        if (end == C.LENGTH_UNSET.toLong() || end > total) end = total
        return end - dataSpec.position // lo que queda por leer, como si fuera una sola petición
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthrough) return upstream.read(buffer, offset, length)
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) openNextChunk()

        val toRead = minOf(length.toLong(), chunkRemaining).toInt()
        val read = upstream.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            throw IOException("La descarga se cortó antes de tiempo")
        }
        position += read
        chunkRemaining -= read
        return read
    }

    private fun openNextChunk() {
        upstream.close()
        val spec = base ?: throw IOException("Fuente de datos cerrada")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
        val length = minOf(chunkBytes, end - position)
        val resolved = upstream.open(chunkSpec(spec, position, length))
        chunkRemaining = if (resolved != C.LENGTH_UNSET.toLong()) resolved else length
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        base = null
        chunkRemaining = 0L
        upstream.close()
    }

    private fun chunkSpec(spec: DataSpec, position: Long, length: Long): DataSpec =
        spec.buildUpon().setPosition(position).setLength(length).build()

    /** Tamaño total del archivo según "Content-Range: bytes 0-1048575/3532101"; null si no lo hay. */
    private fun totalLengthFrom(headers: Map<String, List<String>>): Long? {
        val value = headers.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull() ?: return null
        return value.substringAfterLast('/').trim().toLongOrNull()
    }

    companion object {
        const val DEFAULT_CHUNK_BYTES = 1_048_576L // 1 MB
    }
}
