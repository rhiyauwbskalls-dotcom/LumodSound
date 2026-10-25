package com.lumodsound.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException

/** De dónde puede salir una letra. Spotify solo se consulta si está conectado. */
enum class LyricsSource(val label: String) {
    SPOTIFY("Spotify"),
    BETTERLYRICS("BetterLyrics"),
    LRCLIB("LRCLIB")
}

enum class LyricsOutcome { FOUND, FOUND_PLAIN, INSTRUMENTAL, NOT_FOUND, ERROR }

/** Resultado de un proveedor en la última búsqueda: qué pasó y, si no hubo letra, por qué. */
data class LyricsAttempt(val outcome: LyricsOutcome, val reason: String?, val song: String)

/** Respuesta HTTP de error de un proveedor de letras (límite de uso, fallo del servidor...). */
class LyricsHttpException(val code: Int) : IOException("HTTP $code")

/** Último resultado de cada proveedor de letras; lo lee la tarjeta de letras de Ajustes. */
object LyricsProviderStatus {
    var attempts by mutableStateOf<Map<LyricsSource, LyricsAttempt>>(emptyMap())
        private set

    fun reset() {
        attempts = emptyMap()
    }

    fun record(source: LyricsSource, attempt: LyricsAttempt) {
        attempts = attempts + (source to attempt)
    }
}
