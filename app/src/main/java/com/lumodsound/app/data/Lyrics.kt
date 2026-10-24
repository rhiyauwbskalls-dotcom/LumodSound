package com.lumodsound.app.data

import com.lumodsound.app.R
import com.lumodsound.app.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Una palabra (o sílaba) con su tiempo: solo la traen los proveedores con sincronía por palabra (BetterLyrics). */
data class LyricWord(val beginMs: Long, val endMs: Long, val text: String)

data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Synced(val lines: List<LyricLine>) : LyricsState
    data class Plain(val lines: List<String>) : LyricsState
    data object NotFound : LyricsState
    data object Instrumental : LyricsState
    data object Error : LyricsState
}

/** Cliente de https://lrclib.net (letras sincronizadas en formato LRC). */
class LrcLibClient {

    /** Por qué no salió letra en la última búsqueda (null si salió). La lee MainViewModel para el aviso y los Ajustes. */
    @Volatile
    var lastReason: String? = null
        private set

    /** Último código HTTP de error que no fue un simple 404 (que significa "sin letra"). */
    @Volatile
    private var lastErrorCode: Int? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(rawTitle: String, rawArtist: String, durationSec: Long): LyricsState =
        withContext(Dispatchers.IO) {
            val artist = cleanArtist(rawArtist)
            val title = cleanTitle(rawTitle, artist)
            lastReason = null
            lastErrorCode = null
            val result = lookup(title, artist, durationSec)
            if (result == null) {
                lastReason = lastErrorCode?.let { str(R.string.lyr_reason_http, it) }
                    ?: str(R.string.lyr_reason_no_lyrics)
            }
            result ?: LyricsState.NotFound
        }

    private fun lookup(title: String, artist: String, durationSec: Long): LyricsState? {
        // 1) coincidencia exacta
        val exactUrl = buildString {
            append("$BASE/get?track_name=${enc(title)}&artist_name=${enc(artist)}")
            if (durationSec > 0) append("&duration=$durationSec")
        }
        httpGet(exactUrl)?.let { body ->
            parseObject(body)?.toState()?.let { return it }
        }
        // 2) búsqueda por campos
        searchBest("$BASE/search?track_name=${enc(title)}&artist_name=${enc(artist)}", durationSec)
            ?.let { return it }
        // 3) búsqueda libre
        searchBest("$BASE/search?q=${enc("$title $artist")}", durationSec)
            ?.let { return it }
        return null
    }

    /** Del listado elige la mejor: primero sincronizada, y la de duración más cercana. */
    private fun searchBest(url: String, durationSec: Long): LyricsState? {
        val body = httpGet(url) ?: return null
        val arr = try {
            JSONArray(body)
        } catch (e: Exception) {
            return null
        }
        var best: LyricsState? = null
        var bestRank = Int.MAX_VALUE
        var bestDiff = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val diff = if (durationSec > 0) abs(o.optDouble("duration", 0.0) - durationSec) else 0.0
            if (durationSec > 0 && diff > 10.0) continue
            val st = o.toState() ?: continue
            val rank = when (st) {
                is LyricsState.Synced -> 0
                is LyricsState.Plain -> 1
                else -> 2
            }
            if (rank < bestRank || (rank == bestRank && diff < bestDiff)) {
                best = st
                bestRank = rank
                bestDiff = diff
            }
        }
        return best
    }

    private fun JSONObject.str(name: String): String =
        if (isNull(name)) "" else optString(name, "")

    private fun JSONObject.toState(): LyricsState? {
        if (optBoolean("instrumental", false)) return LyricsState.Instrumental
        val synced = parseLrc(str("syncedLyrics"))
        if (synced.isNotEmpty()) return LyricsState.Synced(synced)
        val plain = str("plainLyrics").lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (plain.isNotEmpty()) return LyricsState.Plain(plain)
        return null
    }

    private fun parseObject(body: String): JSONObject? =
        try {
            JSONObject(body)
        } catch (e: Exception) {
            null
        }

    /** null si el servidor responde 404 u otro código de error; lanza IOException si no hay red. */
    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "LumodSound/1.0 (proyecto personal)")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                if (r.code != 404) lastErrorCode = r.code
                return null
            }
            return r.body?.string()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val BASE = "https://lrclib.net/api"
    }
}

/** Quita "- Topic", VEVO, etc. del nombre del artista. */
internal fun cleanArtist(artist: String): String =
    artist.replace(Regex("""(?i)\s*-\s*topic$"""), "")
        .replace(Regex("""(?i)vevo$"""), "")
        .trim()

/** Limpia títulos tipo "Artista - Canción (Official Video) [HD]" -> "Canción". */
internal fun cleanTitle(title: String, artist: String): String {
    var t = title
        .replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), " ")
        .replace(
            Regex("""(?i)\b(official\s+(music\s+)?(video|audio|lyric\s+video)|lyrics?|letra|video\s+oficial)\b"""),
            " "
        )
        .replace(Regex("""(?i)\s+(ft|feat)\.?\s+.*$"""), "")
    if (t.contains(" - ")) {
        val parts = t.split(" - ", limit = 2)
        val left = parts[0].trim()
        val sameArtist = artist.isNotBlank() && left.isNotBlank() &&
            (left.equals(artist, true) || artist.contains(left, true) || left.contains(artist, true))
        if (sameArtist) t = parts[1]
    }
    return t.replace(Regex("""\s+"""), " ").trim().ifEmpty { title.trim() }
}

/** Convierte texto LRC ("[01:23.45] letra") en líneas con su tiempo en ms. */
internal fun parseLrc(lrc: String): List<LyricLine> {
    if (lrc.isBlank()) return emptyList()
    val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    val out = ArrayList<LyricLine>()
    for (raw in lrc.lines()) {
        val matches = stamp.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val text = raw.substring(matches.last().range.last + 1).trim()
        for (m in matches) {
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val ms = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            out.add(LyricLine(min * 60_000 + sec * 1_000 + ms, text))
        }
    }
    return out.sortedBy { it.timeMs }
}
