package com.lumodsound.app.data

import android.util.Log
import com.lumodsound.app.R
import com.lumodsound.app.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.IOException
import java.io.InterruptedIOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Cliente de BetterLyrics (https://docs.betterlyrics.org): devuelve la letra en TTML con tiempo por sílaba.
 * Aquí se convierte en [LyricsState.Synced] con las palabras de cada línea en [LyricLine.words].
 *
 * Si la letra no está en la caché del servidor y no se manda clave, responde 401 y se trata como "no encontrada"
 * (el siguiente proveedor se encarga). Sin red o con error del servidor lanza IOException.
 */
class BetterLyricsClient {

    /** Por qué no salió letra en la última búsqueda (null si salió). La lee MainViewModel para el aviso y los Ajustes. */
    @Volatile
    var lastReason: String? = null
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * La API guarda la caché con título + artista + álbum + duración; si falta algo suele dar 401 (sin clave).
     * Por eso se manda el álbum cuando se conoce, y si no sale nada se reintenta con solo el primer artista
     * (YouTube junta varios artistas con comas o "&").
     */
    suspend fun fetch(rawTitle: String, rawArtist: String, durationSec: Long, album: String = ""): LyricsState =
        // runInterruptible: si se cancela la búsqueda (cambio de canción, otra petición), la llamada de red se corta
        // de verdad en vez de seguir en segundo plano y pisar el resultado/motivo de la búsqueda nueva
        runInterruptible(Dispatchers.IO) {
            val artist = cleanArtist(rawArtist)
            val title = cleanTitle(rawTitle, artist)
            lastReason = null
            val first = request(title, artist, album, durationSec)
            if (first !is LyricsState.NotFound) return@runInterruptible first
            val solo = artist.split(Regex("""\s*(?:,|&|;|\s[xX]\s)\s*""")).first().trim()
            if (solo.isNotBlank() && !solo.equals(artist, ignoreCase = true)) {
                request(title, solo, album, durationSec)
            } else {
                first
            }
        }

    private fun request(title: String, artist: String, album: String, durationSec: Long): LyricsState {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                if (album.isNotBlank()) addQueryParameter("al", album)
                if (durationSec > 0) addQueryParameter("d", durationSec.toString())
            }
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "LumodSound/1.0 (proyecto personal)")
            .build()
        val body = execute(req, title, artist, album, durationSec) ?: return LyricsState.NotFound
        val ttml = try {
            JSONObject(body).optString("ttml")
        } catch (e: Exception) {
            Log.w(TAG, "Respuesta no es JSON válido: ${e.message}")
            lastReason = str(R.string.lyr_reason_bad_response)
            return LyricsState.NotFound
        }
        if (ttml.isBlank()) {
            lastReason = str(R.string.lyr_reason_no_lyrics)
            return LyricsState.NotFound
        }
        val lines = try {
            parseTtml(ttml)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer el TTML: ${e.message}")
            lastReason = str(R.string.lyr_reason_ttml)
            return LyricsState.NotFound
        }
        if (lines.isEmpty()) {
            lastReason = str(R.string.lyr_reason_no_lyrics)
            return LyricsState.NotFound
        }
        return LyricsState.Synced(lines)
    }


    /**
     * Hace la llamada con un reintento ante fallos pasajeros (sin conexión momentánea, tiempo agotado, 429 o 5xx).
     * Tras el segundo fallo lanza la excepción: es un error de verdad, no "sin letra".
     * Devuelve null si el servidor responde con otro código (401, 404...) y deja el motivo en [lastReason].
     */
    private fun execute(req: Request, title: String, artist: String, album: String, durationSec: Long): String? {
        var attempt = 0
        while (true) {
            try {
                return client.newCall(req).execute().use { r ->
                    when {
                        r.isSuccessful -> r.body?.string()
                        r.code == 429 || r.code >= 500 -> throw LyricsHttpException(r.code)
                        else -> {
                            Log.w(TAG, "HTTP ${r.code} para \"$title\" - \"$artist\" (al=$album, d=$durationSec)")
                            lastReason = when (r.code) {
                                401 -> str(R.string.lyr_reason_bl_not_cached)
                                404 -> str(R.string.lyr_reason_no_lyrics)
                                else -> str(R.string.lyr_reason_http, r.code)
                            }
                            null
                        }
                    }
                }
            } catch (e: InterruptedIOException) {
                // un tiempo agotado también es InterruptedIOException (SocketTimeoutException); si el hilo fue
                // interrumpido de verdad (cancelación), no se reintenta
                if (Thread.currentThread().isInterrupted || e !is java.net.SocketTimeoutException) throw e
                if (attempt++ >= 1) throw e
                Log.w(TAG, "Tiempo agotado, reintentando: ${e.message}")
            } catch (e: IOException) {
                if (attempt++ >= 1) throw e
                Log.w(TAG, "Fallo de red, reintentando: ${e.message}")
            }
            Thread.sleep(700) // si se cancela durante la espera, lanza InterruptedException y runInterruptible la convierte
        }
    }

    private companion object {
        const val BASE = "https://api.betterlyrics.org/getLyrics"
        const val TAG = "BetterLyrics"
    }
}

private class WordBuf(val begin: Long, val end: Long, var text: String)

/**
 * TTML -> líneas con palabras. Los espacios entre sílabas vienen como texto suelto entre los <span>, por eso se
 * lee el DOM sin reformatear. Las voces de fondo (ttm:role="x-bg") se muestran entre paréntesis.
 */
internal fun parseTtml(ttml: String): List<LyricLine> {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
    val all = doc.getElementsByTagName("*")
    val out = ArrayList<LyricLine>()
    for (i in 0 until all.length) {
        val p = all.item(i) as? Element ?: continue
        if (p.tagName.substringAfter(':') != "p") continue
        val words = ArrayList<WordBuf>()
        val loose = StringBuilder()
        collectWords(p, words, loose)
        words.removeAll { it.text.isBlank() }
        if (words.isNotEmpty()) {
            words.first().text = words.first().text.trimStart()
            words.last().text = words.last().text.trimEnd()
        }
        val text = (if (words.isNotEmpty()) words.joinToString("") { it.text } else loose.toString())
            .replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) continue
        val begin = parseTtmlTime(p.getAttribute("begin")) ?: words.firstOrNull()?.begin ?: continue
        out += LyricLine(begin, text, words.map { LyricWord(it.begin, it.end, it.text) })
    }
    return out.sortedBy { it.timeMs }
}

private fun collectWords(node: Node, words: MutableList<WordBuf>, loose: StringBuilder) {
    val kids = node.childNodes
    for (k in 0 until kids.length) {
        val c = kids.item(k)
        when (c.nodeType) {
            Node.ELEMENT_NODE -> {
                val e = c as Element
                if (e.tagName.substringAfter(':') != "span") continue
                val begin = parseTtmlTime(e.getAttribute("begin"))
                if (begin != null) {
                    val end = parseTtmlTime(e.getAttribute("end")) ?: begin
                    words += WordBuf(begin, end, e.textContent ?: "")
                } else {
                    val isBg = e.getAttribute("ttm:role") == "x-bg"
                    val start = words.size
                    collectWords(e, words, loose)
                    if (isBg && words.size > start) {
                        val first = words[start]
                        first.text = "(" + first.text.trimStart()
                        val last = words.last()
                        val trailing = last.text.endsWith(" ")
                        last.text = last.text.trimEnd() + ")" + if (trailing) " " else ""
                    }
                }
            }
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                val t = c.nodeValue ?: ""
                if (t.isBlank()) {
                    // el espacio entre dos sílabas va fuera de los <span>: se pega a la palabra anterior
                    val last = words.lastOrNull()
                    if (last != null && last.text.isNotEmpty() && !last.text.last().isWhitespace()) last.text += " "
                } else {
                    loose.append(t)
                }
            }
        }
    }
}

/** "HH:MM:SS.mmm", "M:SS.mmm", "SS.mmm" o "12.5s" -> milisegundos. */
private fun parseTtmlTime(raw: String): Long? {
    val s = raw.trim().removeSuffix("s")
    if (s.isEmpty()) return null
    return try {
        val parts = s.split(":")
        val sec = when (parts.size) {
            3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
            2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
            else -> parts[0].toDouble()
        }
        (sec * 1000).roundToLong()
    } catch (e: NumberFormatException) {
        null
    }
}
