package com.lumodsound.app.player

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ajustes del crossfade. Se guardan en SharedPreferences y el servicio los lee directamente, así que
 * funciona con la pantalla apagada o la app en segundo plano. Desactivado por defecto.
 */
object Crossfade {
    const val PREFS = "crossfade"
    const val MIN_SEC = 1
    const val MAX_SEC = 12
    const val DEFAULT_SEC = 5

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
    }

    /** Duración del cruce, en segundos. */
    fun seconds(context: Context): Int = prefs(context).getInt("seconds", DEFAULT_SEC).coerceIn(MIN_SEC, MAX_SEC)

    fun setSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt("seconds", seconds.coerceIn(MIN_SEC, MAX_SEC)).apply()
    }
}

/**
 * Crossfade real (las dos canciones suenan a la vez un momento). ExoPlayer no lo trae, así que se hace con un
 * segundo reproductor auxiliar, que solo existe durante el cruce:
 *
 *  1. Unos segundos antes de que se acabe la canción se prepara el auxiliar con ESA misma canción, en pausa y
 *     colocado en el punto exacto donde empezará el cruce (sale de la caché de disco, así que es inmediato).
 *  2. Traspaso (unos 250 ms): el auxiliar arranca en silencio la MISMA canción y se hace un pequeño fundido entre
 *     los dos (el de la sesión baja, el auxiliar sube). Así no hay corte ni salto aunque estén desfasados unos
 *     milisegundos: el oído lo toma como una sola canción.
 *  3. Cruce: el reproductor de la sesión salta a la siguiente con el volumen subiendo mientras el auxiliar sigue
 *     con la que sale, bajando. La notificación, la pantalla de reproducción, Last.fm y Discord cambian aquí.
 *  4. Al terminar, se libera el auxiliar y el volumen vuelve a 1.
 *
 * Las curvas son de potencia constante (coseno / seno): el volumen total se mantiene parejo durante el cruce.
 * Si algo no está listo a tiempo, o pausas, saltas o buscas en pleno cruce, se cancela y el cambio es el normal.
 * No actúa con "repetir una", con el temporizador de sueño activo ni cuando no hay canción siguiente.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val context: Context,
    private val player: ExoPlayer,
    private val mediaSourceFactory: MediaSource.Factory
) {
    private val handler = Handler(Looper.getMainLooper())
    private var outgoing: ExoPlayer? = null
    private var fading = false
    private var fadeMs = 0L
    private var released = false

    private enum class Phase { WAIT_AUX, HANDOVER, MAIN }
    private var phase = Phase.WAIT_AUX
    private var phaseStartMs = 0L

    // Número de la canción actual (sube en cada cambio) y la última en la que no se pudo hacer el cruce
    private var itemCounter = 0
    private var skippedItem = -1

    // Los eventos que provoca el propio cruce (el salto a la siguiente) se ignoran durante un momento
    private var ignoreEventsUntil = 0L

    private val tick = Runnable { evaluate() }
    private val ramp = Runnable { stepRamp() }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(Crossfade.PREFS, Context.MODE_PRIVATE)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { onSettingsChanged() }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (SystemClock.uptimeMillis() < ignoreEventsUntil) return
            itemCounter++
            if (fading) endFade() else discardOutgoing()
            evaluate()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (SystemClock.uptimeMillis() < ignoreEventsUntil) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                // El usuario movió la barra: lo preparado ya no sirve
                if (fading) endFade() else discardOutgoing()
                evaluate()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && fading) endFade() // pausa en pleno cruce: se termina de golpe y queda la nueva
            evaluate()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = evaluate()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                if (fading) endFade() else discardOutgoing()
            }
            evaluate()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = evaluate()

        override fun onRepeatModeChanged(repeatMode: Int) = evaluate()
    }

    fun attach() {
        player.addListener(listener)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        evaluate()
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        player.removeListener(listener)
        outgoing?.release()
        outgoing = null
        if (fading) player.volume = 1f
        fading = false
    }

    private fun onSettingsChanged() {
        if (released) return
        if (!Crossfade.isEnabled(context)) {
            if (fading) endFade() else discardOutgoing()
        }
        evaluate()
    }

    /** Decide qué toca hacer según lo que falte para que acabe la canción, y se vuelve a llamar cuando haga falta. */
    private fun evaluate() {
        if (released) return
        handler.removeCallbacks(tick)
        if (fading) return
        if (!Crossfade.isEnabled(context)) {
            discardOutgoing()
            return
        }
        // En pausa o cargando no hay nada que hacer; se vuelve a evaluar en el siguiente evento
        if (!player.isPlaying) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE ||
            SleepTimer.state.value.active ||
            player.nextMediaItemIndex == C.INDEX_UNSET ||
            player.isCurrentMediaItemLive ||
            skippedItem == itemCounter
        ) {
            discardOutgoing()
            return
        }
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        // El cruce nunca dura más de un tercio de la canción (así las muy cortas no se cruzan casi enteras)
        val fade = minOf(Crossfade.seconds(context) * 1000L, duration / 3)
        if (fade < 1000L) return

        val remaining = duration - player.currentPosition
        val lead = fade + HANDOVER_MS // el traspaso empieza un poco antes, para que el cruce dure lo que se pidió
        val prepareAt = lead + PREPARE_LEAD_MS
        when {
            remaining > prepareAt ->
                handler.postDelayed(tick, (remaining - prepareAt).coerceIn(250L, 5_000L))

            outgoing == null -> {
                // Si ya se está dentro de la zona del cruce (por ejemplo tras buscar cerca del final) no hay tiempo
                if (remaining < lead + 600L) {
                    skippedItem = itemCounter
                    return
                }
                prepareOutgoing(duration - lead)
                handler.postDelayed(tick, 250L)
            }

            remaining <= lead + 40L -> startFade(fade)

            else -> handler.postDelayed(tick, (remaining - lead).coerceIn(20L, 500L))
        }
    }

    private fun prepareOutgoing(positionMs: Long) {
        discardOutgoing()
        val item = player.currentMediaItem ?: return
        val aux = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false // el foco de audio lo gestiona el reproductor de la sesión
            )
            .build()
        aux.volume = 1f
        aux.playWhenReady = false
        aux.setMediaItem(item, positionMs)
        aux.prepare()
        outgoing = aux
    }

    private fun startFade(fade: Long) {
        val aux = outgoing
        if (aux == null || aux.playbackState != Player.STATE_READY) {
            // El auxiliar no llegó a estar listo: se deja la transición normal para esta canción
            skippedItem = itemCounter
            discardOutgoing()
            return
        }
        fadeMs = fade
        // El auxiliar arranca EN SILENCIO, un poco adelantado para compensar lo que tarda en empezar a sonar. El de la
        // sesión sigue sonando entero hasta que el auxiliar esté sonando de verdad (ver stepRamp).
        aux.volume = 0f
        aux.seekTo(player.currentPosition + SEEK_AHEAD_MS)
        aux.play()
        fading = true
        phase = Phase.WAIT_AUX
        phaseStartMs = SystemClock.uptimeMillis()
        handler.post(ramp)
    }

    private fun stepRamp() {
        if (!fading || released) return
        val aux = outgoing
        if (aux == null) {
            endFade()
            return
        }
        val now = SystemClock.uptimeMillis()
        when (phase) {
            Phase.WAIT_AUX -> {
                if (aux.isPlaying) {
                    phase = Phase.HANDOVER
                    phaseStartMs = now
                } else if (now - phaseStartMs > HANDOVER_WAIT_MAX_MS) {
                    // No arrancó a tiempo: se cancela sin que se note (el de la sesión nunca dejó de sonar)
                    skippedItem = itemCounter
                    endFade()
                    return
                }
            }

            Phase.HANDOVER -> {
                // Fundido corto entre las dos copias de la canción que sale: sin corte ni salto
                val th = ((now - phaseStartMs).toFloat() / HANDOVER_MS).coerceIn(0f, 1f)
                val angle = th * (PI / 2).toFloat()
                player.volume = cos(angle)
                aux.volume = sin(angle)
                if (th >= 1f) {
                    player.volume = 0f
                    aux.volume = 1f
                    ignoreEventsUntil = now + 800L
                    itemCounter++ // la canción de la sesión cambia ahora
                    player.seekToNextMediaItem()
                    phase = Phase.MAIN
                    phaseStartMs = now
                }
            }

            Phase.MAIN -> {
                val t = ((now - phaseStartMs).toFloat() / fadeMs).coerceIn(0f, 1f)
                val angle = t * (PI / 2).toFloat()
                aux.volume = cos(angle)    // la que sale baja
                player.volume = sin(angle) // la que entra sube
                if (t >= 1f) {
                    endFade()
                    return
                }
            }
        }
        handler.postDelayed(ramp, RAMP_STEP_MS)
    }

    /** Termina el cruce (porque acabó o porque se canceló): libera el auxiliar y deja el volumen normal. */
    private fun endFade() {
        handler.removeCallbacks(ramp)
        fading = false
        outgoing?.release()
        outgoing = null
        player.volume = 1f
        evaluate()
    }

    private fun discardOutgoing() {
        outgoing?.release()
        outgoing = null
    }

    private companion object {
        const val PREPARE_LEAD_MS = 6_000L // cuánto antes de empezar el cruce se prepara el auxiliar
        const val RAMP_STEP_MS = 20L
        const val HANDOVER_MS = 250L          // duración del fundido corto de traspaso entre las dos copias
        const val SEEK_AHEAD_MS = 100L        // cuánto se adelanta el auxiliar para compensar su arranque
        const val HANDOVER_WAIT_MAX_MS = 1_000L // si el auxiliar no suena en este tiempo, se cancela el cruce
    }
}
