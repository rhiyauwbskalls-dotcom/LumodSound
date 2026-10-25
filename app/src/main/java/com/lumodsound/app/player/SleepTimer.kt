package com.lumodsound.app.player

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Temporizador de sueño: pausa la música pasado un tiempo (bajando el volumen poco a poco)
 * o cuando termina la canción actual. Vive en el proceso del servicio de reproducción, así que
 * sigue funcionando con la pantalla apagada o la app en segundo plano.
 */
object SleepTimer {

    data class State(
        /** Momento (SystemClock.elapsedRealtime) en que se detendrá; 0 si no hay cuenta atrás. */
        val endAtElapsedMs: Long = 0L,
        /** Se detendrá al terminar la canción actual. */
        val untilSongEnd: Boolean = false
    ) {
        val active: Boolean get() = endAtElapsedMs > 0L || untilSongEnd
    }

    /** Duración del fundido final, en milisegundos. */
    private const val FADE_MS = 8_000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // "Al terminar la canción": ExoPlayer pausa solo al final del elemento actual
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && _state.value.untilSongEnd) {
                player?.pauseAtEndOfMediaItems = false
                _state.value = State()
            }
        }
    }

    /** El servicio entrega su reproductor al crearse. */
    fun attach(exo: ExoPlayer) {
        player = exo
        exo.addListener(listener)
    }

    /** El servicio lo suelta al destruirse (cancela cualquier temporizador). */
    fun detach() {
        cancel()
        player?.removeListener(listener)
        player = null
    }

    /** Detiene la música dentro de [minutes] minutos. */
    fun startMinutes(minutes: Int) {
        cancel()
        val totalMs = minutes * 60_000L
        _state.value = State(endAtElapsedMs = SystemClock.elapsedRealtime() + totalMs)
        job = scope.launch {
            val fadeMs = minOf(FADE_MS, totalMs)
            delay(totalMs - fadeMs)
            player?.let { p ->
                val steps = (fadeMs / 200L).toInt().coerceAtLeast(1)
                for (i in 1..steps) {
                    p.volume = 1f - i / steps.toFloat()
                    delay(200L)
                }
                p.pause()
                p.volume = 1f
            }
            _state.value = State()
        }
    }

    /** Detiene la música cuando termine la canción que suena ahora. */
    fun startAtSongEnd() {
        cancel()
        _state.value = State(untilSongEnd = true)
        player?.pauseAtEndOfMediaItems = true
    }

    fun cancel() {
        job?.cancel()
        job = null
        player?.let {
            it.pauseAtEndOfMediaItems = false
            it.volume = 1f // por si se cancela en pleno fundido
        }
        _state.value = State()
    }
}
