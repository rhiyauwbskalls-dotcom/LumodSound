package com.lumodsound.app

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Idioma de la app: el del teléfono o uno elegido en Ajustes. */
enum class AppLanguage(val code: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es")
}

/** Guarda y aplica el idioma elegido. Los textos se piden con str(), que usa [AppText.context]. */
object LanguageSettings {
    private const val PREFS = "settings"
    private const val KEY = "app_language"

    var current by mutableStateOf(AppLanguage.SYSTEM)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = AppLanguage.values().firstOrNull { it.name == saved } ?: AppLanguage.SYSTEM
        loaded = true
    }

    /** Cambia el idioma. Después hay que recrear la pantalla para que se vea (ver [AppText.refresh]). */
    fun set(context: Context, language: AppLanguage) {
        load(context)
        current = language
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language.name).apply()
        AppText.refresh()
    }

    /** Contexto con el idioma elegido (o el mismo si se sigue al teléfono). */
    fun localized(base: Context): Context {
        val code = current.code ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(code))
        return base.createConfigurationContext(config)
    }
}
