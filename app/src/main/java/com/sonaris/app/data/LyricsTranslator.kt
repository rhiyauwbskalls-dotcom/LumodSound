package com.sonaris.app.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface TranslateResult {
    data class Success(val lines: List<String>) : TranslateResult
    data object SameLanguage : TranslateResult
    data object UnknownLanguage : TranslateResult
    data object Failure : TranslateResult
    /** Error con mensaje legible (p. ej. API key inválida al traducir con IA). */
    data class Error(val message: String) : TranslateResult
}

/**
 * Traduce la letra con ML Kit (en el dispositivo):
 * 1) detecta el idioma de la letra, 2) descarga el modelo si hace falta (una sola vez, ~30 MB),
 * 3) traduce línea por línea. El resultado conserva el mismo orden/tamaño que la lista original.
 */
object LyricsTranslator {

    suspend fun translate(
        lines: List<String>,
        targetTag: String = Locale.getDefault().language
    ): TranslateResult {
        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: TranslateLanguage.SPANISH

        val sample = lines.filter { it.isNotBlank() }.take(20).joinToString("\n")
        if (sample.isBlank()) return TranslateResult.Failure

        val identifier = LanguageIdentification.getClient()
        val detected = try {
            identifier.identifyLanguage(sample).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TranslateResult.Failure
        } finally {
            identifier.close()
        }
        if (detected == "und") return TranslateResult.UnknownLanguage
        val source = TranslateLanguage.fromLanguageTag(detected) ?: return TranslateResult.UnknownLanguage
        if (source == target) return TranslateResult.SameLanguage

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            TranslateResult.Success(
                lines.map { if (it.isBlank()) "" else translator.translate(it).await() }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TranslateResult.Failure
        } finally {
            translator.close()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
