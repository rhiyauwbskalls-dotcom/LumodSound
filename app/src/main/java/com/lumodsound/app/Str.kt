package com.lumodsound.app

import android.content.Context
import androidx.annotation.StringRes

/** Contextos para pedir textos traducidos desde cualquier lugar (se asignan en LumodSoundApp). */
object AppText {
    /** Contexto de la aplicación, sin idioma forzado. */
    lateinit var base: Context

    /** El que usa str(): con el idioma elegido en Ajustes (o el del teléfono). */
    lateinit var context: Context

    fun refresh() {
        context = LanguageSettings.localized(base)
    }
}

/**
 * Texto traducido: inglés por defecto y español si el idioma elegido (o el del teléfono) es español
 * (res/values y res/values-es). Se pide al momento de mostrarlo.
 */
fun str(@StringRes id: Int, vararg args: Any?): String = AppText.context.getString(id, *args)
