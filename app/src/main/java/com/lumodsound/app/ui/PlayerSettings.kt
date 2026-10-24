package com.lumodsound.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

<<<<<<< HEAD
/** Estilo del reproductor grande: el de siempre o el de portada a pantalla completa. */
enum class PlayerStyle { CLASSIC, APPLE }

/**
 * Opciones del reproductor grande que se eligen en Ajustes.
 * "Fondo difuminado" usa la portada difuminada y oscurecida de fondo (apagado por defecto: se ve el degradado de color).
 * "Estilo" cambia entre el reproductor normal y el de portada a pantalla completa.
 */
object PlayerSettings {
    private const val PREFS = "settings"
    private const val KEY_BLUR = "player_blur_background"
    private const val KEY_STYLE = "player_style"

    var blurBackground by mutableStateOf(false)
        private set

    var style by mutableStateOf(PlayerStyle.CLASSIC)
        private set

    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        blurBackground = p.getBoolean(KEY_BLUR, false)
        style = runCatching { PlayerStyle.valueOf(p.getString(KEY_STYLE, null) ?: "CLASSIC") }
            .getOrDefault(PlayerStyle.CLASSIC)
        loaded = true
    }

    fun setBlurBackground(context: Context, on: Boolean) {
        load(context)
        blurBackground = on
        prefs(context).edit().putBoolean(KEY_BLUR, on).apply()
    }

    fun setStyle(context: Context, value: PlayerStyle) {
        load(context)
        style = value
        prefs(context).edit().putString(KEY_STYLE, value.name).apply()
=======
/**
 * Opciones del reproductor grande que se eligen en Ajustes.
 * "Fondo difuminado" usa la portada difuminada y oscurecida de fondo (apagado por defecto: se ve el degradado de color).
 */
object PlayerSettings {
    private const val PREFS = "settings"
    private const val KEY_BLUR = "player_blur_background"

    var blurBackground by mutableStateOf(false)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        blurBackground = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLUR, false)
        loaded = true
    }

    fun setBlurBackground(context: Context, on: Boolean) {
        blurBackground = on
        loaded = true
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLUR, on).apply()
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
    }
}
