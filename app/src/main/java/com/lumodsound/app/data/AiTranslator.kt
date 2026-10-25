package com.lumodsound.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Traduce letras con una IA (Claude, ChatGPT, Gemini, OpenRouter, Pollinations o un endpoint personalizado)
 * usando la API key del usuario. Las líneas viajan numeradas ("1. texto") y la IA responde con la misma
 * numeración, así una línea que falte no descuadra el resto de la letra.
 */
object AiTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM =
        "You are a professional song lyrics translator. Translate faithfully, keeping the meaning, tone, " +
            "slang and emotion. Output only the translated lines, nothing else."

    private class ApiException(message: String) : Exception(message)

    suspend fun translate(lines: List<String>, targetTag: String, s: TranslationSettings): TranslateResult {
        val provider = s.provider
        val custom = provider == AiProvider.CUSTOM
        if (!custom && s.apiKey.isBlank()) {
            return TranslateResult.Error(str(R.string.missing_api_key_settings_lyrics))
        }
        if (custom && s.customEndpoint.isBlank()) {
            return TranslateResult.Error(str(R.string.missing_endpoint_settings_lyrics_translation))
        }
        if (s.model.isBlank()) return TranslateResult.Error(str(R.string.missing_model_name))

        val format = when (provider) {
            AiProvider.CLAUDE -> ApiFormat.ANTHROPIC
            AiProvider.GEMINI -> ApiFormat.GEMINI
            AiProvider.CUSTOM -> s.customFormat
            else -> ApiFormat.OPENAI
        }
        val url = buildUrl(s, format) ?: return TranslateResult.Error(str(R.string.the_endpoint_isn_t_a))

        val idx = lines.indices.filter { lines[it].isNotBlank() }
        if (idx.isEmpty()) return TranslateResult.Failure

        val langName = Locale.forLanguageTag(targetTag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "Spanish" }
        val numbered = idx.mapIndexed { n, i -> "${n + 1}. ${lines[i].trim()}" }.joinToString("\n")
        val user = "Translate each numbered line into $langName. Keep exactly the same numbering, one line " +
            "per number, with no commentary and no code fences. If a line is already in $langName, repeat it " +
            "unchanged.\n\n$numbered"

        val reply = try {
            call(s, format, url, user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            return TranslateResult.Error(e.message ?: str(R.string.api_error))
        } catch (e: IOException) {
            return TranslateResult.Error(str(R.string.can_t_connect_to, provider.label))
        } catch (e: Exception) {
            return TranslateResult.Error(str(R.string.unexpected_response_from, provider.label))
        }
        if (reply.isBlank()) return TranslateResult.Error(str(R.string.returned_no_text_blocked_response, provider.label))

        val out = MutableList(lines.size) { "" }
        val numbering = Regex("""^\s*(\d+)\s*[.)\-:]\s*(.*)$""")
        var parsed = 0
        for (raw in reply.lines()) {
            val m = numbering.find(raw) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n in 1..idx.size) {
                out[idx[n - 1]] = m.groupValues[2].trim()
                parsed++
            }
        }
        if (parsed == 0) return TranslateResult.Error(str(R.string.the_ai_didn_t_reply))
        if (idx.all { out[it].equals(lines[it].trim(), ignoreCase = true) }) return TranslateResult.SameLanguage
        return TranslateResult.Success(out)
    }

    /**
     * URL final. En "Personalizado" el usuario pega la URL completa; por comodidad, si termina en "/v1"
     * se le añade la ruta típica del formato elegido.
     */
    private fun buildUrl(s: TranslationSettings, format: ApiFormat): String? {
        val custom = s.provider == AiProvider.CUSTOM
        val base = if (custom) s.customEndpoint.trim().trimEnd('/') else s.provider.endpoint
        val model = s.model.removePrefix("models/")
        val url = when (format) {
            ApiFormat.GEMINI -> when {
                custom && base.contains(":generateContent") -> base
                base.endsWith("/models") -> "$base/$model:generateContent"
                base.endsWith("/v1beta") || base.endsWith("/v1") -> "$base/models/$model:generateContent"
                else -> "$base/$model:generateContent"
            }
            ApiFormat.OPENAI -> if (custom && base.endsWith("/v1")) "$base/chat/completions" else base
            ApiFormat.ANTHROPIC -> if (custom && base.endsWith("/v1")) "$base/messages" else base
        }
        return url.takeIf { it.toHttpUrlOrNull() != null }
    }

    private suspend fun call(s: TranslationSettings, format: ApiFormat, url: String, user: String): String {
        val key = s.apiKey
        val model = s.model
        return when (format) {
            ApiFormat.ANTHROPIC -> {
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4096)
                    .put("system", SYSTEM)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                val headers = buildMap {
                    if (key.isNotBlank()) put("x-api-key", key)
                    put("anthropic-version", "2023-06-01")
                }
                val json = post(url, headers, body)
                val content = JSONObject(json).optJSONArray("content") ?: JSONArray()
                buildString {
                    for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
                }
            }

            ApiFormat.GEMINI -> {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", user)))
                        )
                    )
                val headers = buildMap { if (key.isNotBlank()) put("x-goog-api-key", key) }
                val json = post(url, headers, body)
                val parts = JSONObject(json).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                buildString {
                    for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }

            // ChatGPT, OpenRouter, Pollinations y la mayoría de servidores propios: chat/completions
            ApiFormat.OPENAI -> {
                val body = JSONObject()
                    .put("model", model)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", SYSTEM))
                            .put(JSONObject().put("role", "user").put("content", user))
                    )
                val headers = buildMap {
                    if (key.isNotBlank()) put("Authorization", "Bearer $key")
                    if (s.provider == AiProvider.OPENROUTER) put("X-Title", "LumodSound")
                }
                val json = post(url, headers, body)
                JSONObject(json).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            }
        }
    }

    private suspend fun post(url: String, headers: Map<String, String>, body: JSONObject): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!cont.isActive) return
                        if (r.isSuccessful) cont.resume(text)
                        else cont.resumeWithException(ApiException(httpMessage(r.code, text)))
                    }
                }
            })
        }

    private fun httpMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }.take(140)
        val head = when (code) {
            401, 403 -> str(R.string.invalid_api_key_or_no, code)
            404 -> str(R.string.endpoint_or_model_not_found, code)
            429 -> str(R.string.usage_limit_reached_or_out, code)
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Traduce letras con una IA (Claude, ChatGPT, Gemini, OpenRouter, Pollinations o un endpoint personalizado)
 * usando la API key del usuario. Las líneas viajan numeradas ("1. texto") y la IA responde con la misma
 * numeración, así una línea que falte no descuadra el resto de la letra.
 */
object AiTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM =
        "You are a professional song lyrics translator. Translate faithfully, keeping the meaning, tone, " +
            "slang and emotion. Output only the translated lines, nothing else."

    private class ApiException(message: String) : Exception(message)

    suspend fun translate(lines: List<String>, targetTag: String, s: TranslationSettings): TranslateResult {
        val provider = s.provider
        val custom = provider == AiProvider.CUSTOM
        if (!custom && s.apiKey.isBlank()) {
            return TranslateResult.Error(str(R.string.missing_api_key_settings_lyrics))
        }
        if (custom && s.customEndpoint.isBlank()) {
            return TranslateResult.Error(str(R.string.missing_endpoint_settings_lyrics_translation))
        }
        if (s.model.isBlank()) return TranslateResult.Error(str(R.string.missing_model_name))

        val format = when (provider) {
            AiProvider.CLAUDE -> ApiFormat.ANTHROPIC
            AiProvider.GEMINI -> ApiFormat.GEMINI
            AiProvider.CUSTOM -> s.customFormat
            else -> ApiFormat.OPENAI
        }
        val url = buildUrl(s, format) ?: return TranslateResult.Error(str(R.string.the_endpoint_isn_t_a))

        val idx = lines.indices.filter { lines[it].isNotBlank() }
        if (idx.isEmpty()) return TranslateResult.Failure

        val langName = Locale.forLanguageTag(targetTag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "Spanish" }
        val numbered = idx.mapIndexed { n, i -> "${n + 1}. ${lines[i].trim()}" }.joinToString("\n")
        val user = "Translate each numbered line into $langName. Keep exactly the same numbering, one line " +
            "per number, with no commentary and no code fences. If a line is already in $langName, repeat it " +
            "unchanged.\n\n$numbered"

        val reply = try {
            call(s, format, url, user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            return TranslateResult.Error(e.message ?: str(R.string.api_error))
        } catch (e: IOException) {
            return TranslateResult.Error(str(R.string.can_t_connect_to, provider.label))
        } catch (e: Exception) {
            return TranslateResult.Error(str(R.string.unexpected_response_from, provider.label))
        }
        if (reply.isBlank()) return TranslateResult.Error(str(R.string.returned_no_text_blocked_response, provider.label))

        val out = MutableList(lines.size) { "" }
        val numbering = Regex("""^\s*(\d+)\s*[.)\-:]\s*(.*)$""")
        var parsed = 0
        for (raw in reply.lines()) {
            val m = numbering.find(raw) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n in 1..idx.size) {
                out[idx[n - 1]] = m.groupValues[2].trim()
                parsed++
            }
        }
        if (parsed == 0) return TranslateResult.Error(str(R.string.the_ai_didn_t_reply))
        if (idx.all { out[it].equals(lines[it].trim(), ignoreCase = true) }) return TranslateResult.SameLanguage
        return TranslateResult.Success(out)
    }

    /**
     * URL final. En "Personalizado" el usuario pega la URL completa; por comodidad, si termina en "/v1"
     * se le añade la ruta típica del formato elegido.
     */
    private fun buildUrl(s: TranslationSettings, format: ApiFormat): String? {
        val custom = s.provider == AiProvider.CUSTOM
        val base = if (custom) s.customEndpoint.trim().trimEnd('/') else s.provider.endpoint
        val model = s.model.removePrefix("models/")
        val url = when (format) {
            ApiFormat.GEMINI -> when {
                custom && base.contains(":generateContent") -> base
                base.endsWith("/models") -> "$base/$model:generateContent"
                base.endsWith("/v1beta") || base.endsWith("/v1") -> "$base/models/$model:generateContent"
                else -> "$base/$model:generateContent"
            }
            ApiFormat.OPENAI -> if (custom && base.endsWith("/v1")) "$base/chat/completions" else base
            ApiFormat.ANTHROPIC -> if (custom && base.endsWith("/v1")) "$base/messages" else base
        }
        return url.takeIf { it.toHttpUrlOrNull() != null }
    }

    private suspend fun call(s: TranslationSettings, format: ApiFormat, url: String, user: String): String {
        val key = s.apiKey
        val model = s.model
        return when (format) {
            ApiFormat.ANTHROPIC -> {
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4096)
                    .put("system", SYSTEM)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                val headers = buildMap {
                    if (key.isNotBlank()) put("x-api-key", key)
                    put("anthropic-version", "2023-06-01")
                }
                val json = post(url, headers, body)
                val content = JSONObject(json).optJSONArray("content") ?: JSONArray()
                buildString {
                    for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
                }
            }

            ApiFormat.GEMINI -> {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", user)))
                        )
                    )
                val headers = buildMap { if (key.isNotBlank()) put("x-goog-api-key", key) }
                val json = post(url, headers, body)
                val parts = JSONObject(json).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                buildString {
                    for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }

            // ChatGPT, OpenRouter, Pollinations y la mayoría de servidores propios: chat/completions
            ApiFormat.OPENAI -> {
                val body = JSONObject()
                    .put("model", model)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", SYSTEM))
                            .put(JSONObject().put("role", "user").put("content", user))
                    )
                val headers = buildMap {
                    if (key.isNotBlank()) put("Authorization", "Bearer $key")
                    if (s.provider == AiProvider.OPENROUTER) put("X-Title", "LumodSound")
                }
                val json = post(url, headers, body)
                JSONObject(json).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            }
        }
    }

    private suspend fun post(url: String, headers: Map<String, String>, body: JSONObject): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!cont.isActive) return
                        if (r.isSuccessful) cont.resume(text)
                        else cont.resumeWithException(ApiException(httpMessage(r.code, text)))
                    }
                }
            })
        }

    private fun httpMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }.take(140)
        val head = when (code) {
            401, 403 -> str(R.string.invalid_api_key_or_no, code)
            404 -> str(R.string.endpoint_or_model_not_found, code)
            429 -> str(R.string.usage_limit_reached_or_out, code)
=======

/**
 * Traduce letras con una IA (Claude, ChatGPT, Gemini, OpenRouter, Pollinations o un endpoint personalizado)
 * usando la API key del usuario. Las líneas viajan numeradas ("1. texto") y la IA responde con la misma
 * numeración, así una línea que falte no descuadra el resto de la letra.
 */
object AiTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM =
        "You are a professional song lyrics translator. Translate faithfully, keeping the meaning, tone, " +
            "slang and emotion. Output only the translated lines, nothing else."

    private class ApiException(message: String) : Exception(message)

    suspend fun translate(lines: List<String>, targetTag: String, s: TranslationSettings): TranslateResult {
        val provider = s.provider
        val custom = provider == AiProvider.CUSTOM
        if (!custom && s.apiKey.isBlank()) {
            return TranslateResult.Error("Falta la API key (Ajustes → Letras traducción)")
        }
        if (custom && s.customEndpoint.isBlank()) {
            return TranslateResult.Error("Falta el endpoint (Ajustes → Letras traducción)")
        }
        if (s.model.isBlank()) return TranslateResult.Error("Falta el nombre del modelo")

        val format = when (provider) {
            AiProvider.CLAUDE -> ApiFormat.ANTHROPIC
            AiProvider.GEMINI -> ApiFormat.GEMINI
            AiProvider.CUSTOM -> s.customFormat
            else -> ApiFormat.OPENAI
        }
        val url = buildUrl(s, format) ?: return TranslateResult.Error("El endpoint no es una URL válida")

        val idx = lines.indices.filter { lines[it].isNotBlank() }
        if (idx.isEmpty()) return TranslateResult.Failure

        val langName = Locale.forLanguageTag(targetTag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "Spanish" }
        val numbered = idx.mapIndexed { n, i -> "${n + 1}. ${lines[i].trim()}" }.joinToString("\n")
        val user = "Translate each numbered line into $langName. Keep exactly the same numbering, one line " +
            "per number, with no commentary and no code fences. If a line is already in $langName, repeat it " +
            "unchanged.\n\n$numbered"

        val reply = try {
            call(s, format, url, user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            return TranslateResult.Error(e.message ?: "Error de la API")
        } catch (e: IOException) {
            return TranslateResult.Error("Sin conexión con ${provider.label}")
        } catch (e: Exception) {
            return TranslateResult.Error("Respuesta inesperada de ${provider.label}")
        }
        if (reply.isBlank()) return TranslateResult.Error("${provider.label} no devolvió texto (¿respuesta bloqueada?)")

        val out = MutableList(lines.size) { "" }
        val numbering = Regex("""^\s*(\d+)\s*[.)\-:]\s*(.*)$""")
        var parsed = 0
        for (raw in reply.lines()) {
            val m = numbering.find(raw) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n in 1..idx.size) {
                out[idx[n - 1]] = m.groupValues[2].trim()
                parsed++
            }
        }
        if (parsed == 0) return TranslateResult.Error("La IA no respondió con el formato esperado")
        if (idx.all { out[it].equals(lines[it].trim(), ignoreCase = true) }) return TranslateResult.SameLanguage
        return TranslateResult.Success(out)
    }

    /**
     * URL final. En "Personalizado" el usuario pega la URL completa; por comodidad, si termina en "/v1"
     * se le añade la ruta típica del formato elegido.
     */
    private fun buildUrl(s: TranslationSettings, format: ApiFormat): String? {
        val custom = s.provider == AiProvider.CUSTOM
        val base = if (custom) s.customEndpoint.trim().trimEnd('/') else s.provider.endpoint
        val model = s.model.removePrefix("models/")
        val url = when (format) {
            ApiFormat.GEMINI -> when {
                custom && base.contains(":generateContent") -> base
                base.endsWith("/models") -> "$base/$model:generateContent"
                base.endsWith("/v1beta") || base.endsWith("/v1") -> "$base/models/$model:generateContent"
                else -> "$base/$model:generateContent"
            }
            ApiFormat.OPENAI -> if (custom && base.endsWith("/v1")) "$base/chat/completions" else base
            ApiFormat.ANTHROPIC -> if (custom && base.endsWith("/v1")) "$base/messages" else base
        }
        return url.takeIf { it.toHttpUrlOrNull() != null }
    }

    private suspend fun call(s: TranslationSettings, format: ApiFormat, url: String, user: String): String {
        val key = s.apiKey
        val model = s.model
        return when (format) {
            ApiFormat.ANTHROPIC -> {
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4096)
                    .put("system", SYSTEM)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                val headers = buildMap {
                    if (key.isNotBlank()) put("x-api-key", key)
                    put("anthropic-version", "2023-06-01")
                }
                val json = post(url, headers, body)
                val content = JSONObject(json).optJSONArray("content") ?: JSONArray()
                buildString {
                    for (i in 0 until content.length()) append(content.optJSONObject(i)?.optString("text").orEmpty())
                }
            }

            ApiFormat.GEMINI -> {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", user)))
                        )
                    )
                val headers = buildMap { if (key.isNotBlank()) put("x-goog-api-key", key) }
                val json = post(url, headers, body)
                val parts = JSONObject(json).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                buildString {
                    for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }

            // ChatGPT, OpenRouter, Pollinations y la mayoría de servidores propios: chat/completions
            ApiFormat.OPENAI -> {
                val body = JSONObject()
                    .put("model", model)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", SYSTEM))
                            .put(JSONObject().put("role", "user").put("content", user))
                    )
                val headers = buildMap {
                    if (key.isNotBlank()) put("Authorization", "Bearer $key")
                    if (s.provider == AiProvider.OPENROUTER) put("X-Title", "LumodSound")
                }
                val json = post(url, headers, body)
                JSONObject(json).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            }
        }
    }

    private suspend fun post(url: String, headers: Map<String, String>, body: JSONObject): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!cont.isActive) return
                        if (r.isSuccessful) cont.resume(text)
                        else cont.resumeWithException(ApiException(httpMessage(r.code, text)))
                    }
                }
            })
        }

    private fun httpMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }.take(140)
        val head = when (code) {
            401, 403 -> "API key inválida o sin permiso ($code)"
            404 -> "Endpoint o modelo no encontrado, revisa los datos ($code)"
            429 -> "Límite de uso o saldo agotado ($code)"
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
            else -> "Error $code"
        }
        return if (detail.isBlank()) head else "$head: $detail"
    }
}
