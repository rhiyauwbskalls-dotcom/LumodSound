package com.lumodsound.app.player

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumodsound.app.R
import com.lumodsound.app.data.StreamQuality
import java.util.concurrent.CopyOnWriteArrayList

/** Calidad de audio que se elige en Ajustes. "Automática" decide según la red en cada canción. */
enum class AudioQuality(@StringRes val labelRes: Int, @StringRes val hintRes: Int) {
    AUTO(R.string.audio_quality_auto, R.string.audio_quality_auto_hint),
    HIGH(R.string.audio_quality_high, R.string.audio_quality_high_hint),
    MEDIUM(R.string.audio_quality_medium, R.string.audio_quality_medium_hint),
    LOW(R.string.audio_quality_low, R.string.audio_quality_low_hint)
}

/**
 * Ajustes de audio: normalizar volumen, calidad y omitir silencios.
 * Se guardan en las preferencias de la app y el servicio de reproducción se entera de los cambios al instante.
 */
object AudioSettings {
    private const val PREFS = "settings"
    private const val KEY_NORMALIZE = "audio_normalize"
    private const val KEY_SKIP_SILENCE = "audio_skip_silence"
    private const val KEY_QUALITY = "audio_quality"

    var normalize by mutableStateOf(false)
        private set
    var skipSilence by mutableStateOf(false)
        private set
    var quality by mutableStateOf(AudioQuality.HIGH)
        private set

    /** Copia que lee el hilo de audio sin pasar por el estado de Compose. */
    @Volatile
    internal var normalizeFlag = false
        private set

    private var loaded = false
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        normalize = p.getBoolean(KEY_NORMALIZE, false)
        normalizeFlag = normalize
        skipSilence = p.getBoolean(KEY_SKIP_SILENCE, false)
        quality = AudioQuality.values().firstOrNull { it.name == p.getString(KEY_QUALITY, null) } ?: AudioQuality.HIGH
        loaded = true
    }

    fun setNormalize(context: Context, on: Boolean) {
        load(context)
        normalize = on
        normalizeFlag = on
        prefs(context).edit().putBoolean(KEY_NORMALIZE, on).apply()
        notifyChanged()
    }

    fun setSkipSilence(context: Context, on: Boolean) {
        load(context)
        skipSilence = on
        prefs(context).edit().putBoolean(KEY_SKIP_SILENCE, on).apply()
        notifyChanged()
    }

    fun setQuality(context: Context, q: AudioQuality) {
        load(context)
        quality = q
        prefs(context).edit().putString(KEY_QUALITY, q.name).apply()
        notifyChanged()
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() = listeners.forEach { it() }

    /** Calidad concreta con la que se pide el audio ahora (en "Automática" depende de si la red es de datos móviles). */
    fun effectiveQuality(context: Context): StreamQuality {
        load(context)
        return when (quality) {
            AudioQuality.HIGH -> StreamQuality.HIGH
            AudioQuality.MEDIUM -> StreamQuality.MEDIUM
            AudioQuality.LOW -> StreamQuality.LOW
            AudioQuality.AUTO -> if (isMetered(context)) StreamQuality.MEDIUM else StreamQuality.HIGH
        }
    }

    private fun isMetered(context: Context): Boolean = try {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.isActiveNetworkMetered
    } catch (e: Exception) {
        false
    }
}
