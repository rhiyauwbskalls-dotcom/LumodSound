package com.lumodsound.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    }
}
