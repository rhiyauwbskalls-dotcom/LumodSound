package com.lumodsound.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TranslateEngine { MLKIT, AI }

/** Formato de la API cuando el proveedor es "Personalizado". */
enum class ApiFormat(val label: String) {
    OPENAI("Compatible con OpenAI (la mayoría)"),
    ANTHROPIC("Anthropic (Claude)"),
    GEMINI("Gemini")
}

/** Proveedores de IA para traducir letras. El usuario pone su propia API key. */
enum class AiProvider(
    val label: String,
    val endpoint: String,
    val models: List<String>,
    val keyHint: String
) {
    CLAUDE(
        "Claude", "https://api.anthropic.com/v1/messages",
        listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"),
        "console.anthropic.com"
    ),
    OPENAI(
        "ChatGPT", "https://api.openai.com/v1/chat/completions",
        listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4o"),
        "platform.openai.com/api-keys"
    ),
    GEMINI(
        "Gemini", "https://generativelanguage.googleapis.com/v1beta/models",
        listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"),
        "aistudio.google.com/apikey"
    ),
    OPENROUTER(
        "OpenRouter", "https://openrouter.ai/api/v1/chat/completions",
        listOf("google/gemini-2.5-flash", "openai/gpt-4o-mini", "meta-llama/llama-3.3-70b-instruct"),
        "openrouter.ai/keys"
    ),
    POLLINATIONS(
        "Pollinations", "https://gen.pollinations.ai/v1/chat/completions",
        listOf("openai", "gemini", "mistral"),
        "enter.pollinations.ai"
    ),
    /** Endpoint, modelo y API key a gusto del usuario (cualquier servicio o servidor propio). */
    CUSTOM("Personalizado", "", emptyList(), "")
}

data class TranslationSettings(
    val engine: TranslateEngine = TranslateEngine.MLKIT,
    val provider: AiProvider = AiProvider.CLAUDE,
    val keys: Map<AiProvider, String> = emptyMap(),
    val models: Map<AiProvider, String> = emptyMap(),
    val customEndpoint: String = "",
    val customFormat: ApiFormat = ApiFormat.OPENAI
) {
    val apiKey: String get() = keys[provider].orEmpty()
    val model: String get() = modelFor(provider)
    fun modelFor(p: AiProvider): String = models[p]?.takeIf { it.isNotBlank() } ?: p.models.firstOrNull().orEmpty()
}

/**
 * Ajustes de "Letras traducción": motor (ML Kit o IA), proveedor, modelo y API key de cada proveedor.
 * Las API keys van cifradas (ver [TokenCipher]); si el cifrado falla no se guarda nada.
 */
class TranslationStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("translation", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TranslationSettings> = _settings.asStateFlow()

    private fun load(): TranslationSettings {
        val providers = AiProvider.values()
        return TranslationSettings(
            engine = runCatching { TranslateEngine.valueOf(prefs.getString("engine", null) ?: "") }
                .getOrDefault(TranslateEngine.MLKIT),
            provider = runCatching { AiProvider.valueOf(prefs.getString("provider", null) ?: "") }
                .getOrDefault(AiProvider.CLAUDE),
            keys = providers.associateWith { p ->
                prefs.getString("key_enc_${p.name}", null)?.let(TokenCipher::decrypt).orEmpty()
            },
            models = providers.associateWith { p -> prefs.getString("model_${p.name}", null).orEmpty() },
            customEndpoint = prefs.getString("custom_endpoint", null).orEmpty(),
            customFormat = runCatching { ApiFormat.valueOf(prefs.getString("custom_format", null) ?: "") }
                .getOrDefault(ApiFormat.OPENAI)
        )
    }

    fun setEngine(e: TranslateEngine) {
        prefs.edit().putString("engine", e.name).apply()
        _settings.update { it.copy(engine = e) }
    }

    fun setProvider(p: AiProvider) {
        prefs.edit().putString("provider", p.name).apply()
        _settings.update { it.copy(provider = p) }
    }

    fun setModel(p: AiProvider, model: String) {
        val m = model.trim()
        prefs.edit().apply { if (m.isEmpty()) remove("model_${p.name}") else putString("model_${p.name}", m) }.apply()
        _settings.update { it.copy(models = it.models + (p to m)) }
    }

    fun setCustomEndpoint(url: String) {
        val u = url.trim()
        prefs.edit().putString("custom_endpoint", u).apply()
        _settings.update { it.copy(customEndpoint = u) }
    }

    fun setCustomFormat(f: ApiFormat) {
        prefs.edit().putString("custom_format", f.name).apply()
        _settings.update { it.copy(customFormat = f) }
    }

    /** false si no se pudo cifrar (en ese caso no se guarda nada). Una clave vacía la borra. */
    fun setApiKey(p: AiProvider, key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) {
            prefs.edit().remove("key_enc_${p.name}").apply()
        } else {
            val enc = TokenCipher.encrypt(k) ?: return false
            prefs.edit().putString("key_enc_${p.name}", enc).apply()
        }
        _settings.update { it.copy(keys = it.keys + (p to k)) }
        return true
    }

    companion object {
        @Volatile private var instance: TranslationStore? = null
        fun get(context: Context): TranslationStore =
            instance ?: synchronized(this) { instance ?: TranslationStore(context).also { instance = it } }
    }
}
