package com.sonaris.app.data

import android.content.Context

/** Punto único para traducir una letra: usa ML Kit o la IA según lo elegido en Ajustes → Letras traducción. */
object LyricsTranslation {
    suspend fun translate(context: Context, lines: List<String>, targetTag: String): TranslateResult {
        val s = TranslationStore.get(context).settings.value
        return if (s.engine == TranslateEngine.AI) {
            AiTranslator.translate(lines, targetTag, s)
        } else {
            LyricsTranslator.translate(lines, targetTag)
        }
    }
}
