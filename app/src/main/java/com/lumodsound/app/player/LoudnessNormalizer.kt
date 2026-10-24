package com.lumodsound.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Normalizar audio: iguala el volumen entre canciones para que no haya que tocar el volumen en cada cambio.
 *
 * Mide cuánto suena la música (nivel RMS) sobre la marcha y mueve una ganancia despacio hacia un nivel objetivo:
 * baja las canciones muy fuertes y sube un poco las muy suaves. Un limitador suave evita que se sature al subir.
 * No necesita datos de YouTube ni analizar la canción antes, pero por eso tarda unos segundos en ajustarse al
 * empezar una canción nueva. Solo trabaja con audio PCM de 16 bits (el normal); con otro formato no hace nada.
 */
@UnstableApi
class LoudnessNormalizer : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channels = 2
    private var gain = 1.0
    private var levelDb = Double.NaN
    private var warmSec = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        // Sigue desde donde iba el reproductor anterior (p. ej. el auxiliar del crossfade) para que no haya saltos de volumen
        gain = sharedGain
        levelDb = sharedLevelDb
        warmSec = if (levelDb.isNaN()) 0.0 else WARM_SEC
        return inputAudioFormat
    }

    override fun onFlush() {
        // Al buscar en la canción se vuelve a medir rápido, sin mover la ganancia de golpe
        warmSec = 0.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)

        val frames = size / (2 * channels)
        if (!AudioSettings.normalizeFlag || frames == 0) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)
        val start = inputBuffer.position()
        val samples = frames * channels

        // 1) ¿Cuánto suena este trozo?
        var sumSq = 0.0
        for (i in 0 until samples) {
            val s = inputBuffer.getShort(start + i * 2).toInt()
            sumSq += s.toDouble() * s
        }
        val rms = sqrt(sumSq / samples) / 32768.0
        val bufferDb = if (rms > 1e-9) 20.0 * log10(rms) else -120.0
        val bufferSec = frames.toDouble() / sampleRate
        if (bufferDb > SILENCE_DB) {
            levelDb = if (levelDb.isNaN()) {
                bufferDb
            } else {
                val tau = if (warmSec < WARM_SEC) FAST_TAU else SLOW_TAU
                levelDb + (1.0 - exp(-bufferSec / tau)) * (bufferDb - levelDb)
            }
            warmSec += bufferSec
        }

        // 2) Ganancia deseada y cuánto puede moverse en este trozo (más rápido hacia abajo, para no saturar)
        val wantDb = if (levelDb.isNaN()) 0.0 else (TARGET_DB - levelDb).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        val currentDb = 20.0 * log10(gain)
        val maxStep = (if (wantDb < currentDb) SLEW_DOWN_DB_S else SLEW_UP_DB_S) * bufferSec
        val newDb = currentDb + (wantDb - currentDb).coerceIn(-maxStep, maxStep)
        val startGain = gain
        val endGain = 10.0.pow(newDb / 20.0)

        // 3) Aplicarla con una rampa suave dentro del trozo
        for (f in 0 until frames) {
            val g = startGain + (endGain - startGain) * f / frames
            for (c in 0 until channels) {
                val s = inputBuffer.getShort(start + (f * channels + c) * 2) * g
                out.putShort(softLimit(s).toInt().toShort())
            }
        }
        inputBuffer.position(start + samples * 2)
        if (inputBuffer.hasRemaining()) out.put(inputBuffer) // por si sobran bytes sueltos
        out.flip()

        gain = endGain
        sharedGain = endGain
        sharedLevelDb = levelDb
    }

    /** Cerca del tope la señal se curva suavemente en vez de cortarse de golpe. */
    private fun softLimit(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val room = 32767.0 - KNEE
        val y = KNEE + room * tanh((a - KNEE) / room)
        return if (x < 0) -y else y
    }

    private companion object {
        const val TARGET_DB = -16.0      // nivel RMS al que se acercan todas las canciones
        const val MIN_GAIN_DB = -9.0
        const val MAX_GAIN_DB = 6.0
        const val SILENCE_DB = -50.0     // por debajo de esto no se mide (silencios)
        const val FAST_TAU = 0.8         // segundos: medición rápida al empezar
        const val SLOW_TAU = 5.0         // segundos: medición estable después
        const val WARM_SEC = 3.0
        const val SLEW_UP_DB_S = 1.0
        const val SLEW_DOWN_DB_S = 3.0
        const val KNEE = 0.8 * 32767.0

        @Volatile var sharedGain = 1.0
        @Volatile var sharedLevelDb = Double.NaN
    }
}

/** Reproductores con el normalizador de volumen en la cadena de audio (el principal y el auxiliar del crossfade). */
@UnstableApi
class NormalizingRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(LoudnessNormalizer()))
            .build()
}
