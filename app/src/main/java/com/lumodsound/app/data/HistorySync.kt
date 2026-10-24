package com.lumodsound.app.data

import android.content.Context
import android.util.Log
import java.util.Locale
import kotlin.random.Random
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Envío opcional del historial de escucha a la cuenta de YouTube Music (para que mejoren las
 * recomendaciones de la cuenta). Desactivado por defecto y solo funciona con sesión iniciada.
 */
object HistorySync {

    private const val TAG = "HistorySync"

    /** Tiempo de escucha a partir del cual una canción cuenta como escuchada. */
    const val THRESHOLD_MS = 30_000L

    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("history_sync", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
    }

    /** Resultado del último envío ("correcto", "falló (HTTP 403/-)"...), para mostrarlo en Ajustes. */
    fun lastResult(context: Context): String? = prefs(context).getString("last_result", null)

    private fun record(context: Context, result: String) {
        Log.i(TAG, str(R.string.last_sent_2, result))
        prefs(context).edit().putString("last_result", result).apply()
    }

    /**
     * Bloqueante (llamar fuera del hilo principal). Manda los dos avisos que usa YouTube: el de reproducción
     * y el de tiempo visto (este último es el que alimenta el historial), con el mismo identificador.
     */
    fun report(context: Context, videoUrl: String, listenedSec: Long): Boolean {
        val videoId = videoUrl.substringAfter("v=", "").substringBefore('&')
        if (videoId.isBlank()) return false

        val found = YtMusicApi.playbackTracking(videoId)
        val tracking = found.tracking
        if (tracking == null || (tracking.playbackUrl == null && tracking.watchtimeUrl == null)) {
            Log.w(TAG, str(R.string.no_tracking_url, found.log))
            record(context, str(R.string.failed_without_a_tracking_url, found.log))
            return false
        }

        val cpn = buildString { repeat(16) { append(CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)]) } }
        val seconds = String.format(Locale.US, "%.1f", listenedSec.toDouble())
        val common = mapOf("ver" to "2", "cpn" to cpn, "cmt" to seconds, "el" to "detailpage")

        val playbackCode = tracking.playbackUrl?.let { YtMusicApi.ping(it, common, tracking.client) }
        val watchtimeCode = tracking.watchtimeUrl?.let {
            YtMusicApi.ping(it, common + mapOf("st" to "0", "et" to seconds), tracking.client)
        }
        Log.i(TAG, str(R.string.ping_for_playback_watch_time, videoId, playbackCode, watchtimeCode))
=======

/**
 * Envío opcional del historial de escucha a la cuenta de YouTube Music (para que mejoren las
 * recomendaciones de la cuenta). Desactivado por defecto y solo funciona con sesión iniciada.
 */
object HistorySync {

    private const val TAG = "HistorySync"

    /** Tiempo de escucha a partir del cual una canción cuenta como escuchada. */
    const val THRESHOLD_MS = 30_000L

    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("history_sync", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
    }

    /** Resultado del último envío ("correcto", "falló (HTTP 403/-)"...), para mostrarlo en Ajustes. */
    fun lastResult(context: Context): String? = prefs(context).getString("last_result", null)

    private fun record(context: Context, result: String) {
        Log.i(TAG, "Último envío: $result")
        prefs(context).edit().putString("last_result", result).apply()
    }

    /**
     * Bloqueante (llamar fuera del hilo principal). Manda los dos avisos que usa YouTube: el de reproducción
     * y el de tiempo visto (este último es el que alimenta el historial), con el mismo identificador.
     */
    fun report(context: Context, videoUrl: String, listenedSec: Long): Boolean {
        val videoId = videoUrl.substringAfter("v=", "").substringBefore('&')
        if (videoId.isBlank()) return false

        val found = YtMusicApi.playbackTracking(videoId)
        val tracking = found.tracking
        if (tracking == null || (tracking.playbackUrl == null && tracking.watchtimeUrl == null)) {
            Log.w(TAG, "Sin URL de seguimiento: ${found.log}")
            record(context, "falló sin URL de seguimiento (${found.log})")
            return false
        }

        val cpn = buildString { repeat(16) { append(CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)]) } }
        val seconds = String.format(Locale.US, "%.1f", listenedSec.toDouble())
        val common = mapOf("ver" to "2", "cpn" to cpn, "cmt" to seconds, "el" to "detailpage")

        val playbackCode = tracking.playbackUrl?.let { YtMusicApi.ping(it, common, tracking.client) }
        val watchtimeCode = tracking.watchtimeUrl?.let {
            YtMusicApi.ping(it, common + mapOf("st" to "0", "et" to seconds), tracking.client)
        }
        Log.i(TAG, "Aviso de $videoId: reproducción=$playbackCode, tiempo visto=$watchtimeCode")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound

        fun ok(code: Int?) = code == null || code in 200..299
        val success = ok(playbackCode) && ok(watchtimeCode) && (playbackCode != null || watchtimeCode != null)
        record(
            context,
            "${if (success) "correcto" else "falló"} vía ${tracking.client.ifBlank { "?" }} " +
                "(HTTP ${playbackCode ?: "-"}/${watchtimeCode ?: "-"})"
        )
        return success
    }
}
