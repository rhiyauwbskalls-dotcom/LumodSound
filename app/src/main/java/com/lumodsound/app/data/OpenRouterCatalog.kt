package com.lumodsound.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.lumodsound.app.R
import com.lumodsound.app.str

data class OpenRouterModel(val id: String, val name: String, val free: Boolean)

/** Catálogo público de OpenRouter (GET /api/v1/models, no necesita API key). Se guarda en memoria mientras la app esté abierta. */
object OpenRouterCatalog {
    private const val URL = "https://openrouter.ai/api/v1/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var cache: List<OpenRouterModel>? = null

    suspend fun fetch(force: Boolean = false): List<OpenRouterModel> {
        if (!force) cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(URL).header("User-Agent", "LumodSound/1.0 (proyecto personal)").build()
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
            val data = JSONObject(body).optJSONArray("data") ?: throw IOException(str(R.string.empty_response))
            val out = ArrayList<OpenRouterModel>(data.length())
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                // Solo modelos que devuelven texto (se descartan los de generación de imágenes, etc.)
                val outputs = o.optJSONObject("architecture")?.optJSONArray("output_modalities")
                if (outputs != null && outputs.length() > 0) {
                    var text = false
                    for (j in 0 until outputs.length()) if (outputs.optString(j) == "text") text = true
                    if (!text) continue
                }
                val pricing = o.optJSONObject("pricing")
                val free = pricing != null &&
                    pricing.optString("prompt").toDoubleOrNull() == 0.0 &&
                    pricing.optString("completion").toDoubleOrNull() == 0.0
                out.add(OpenRouterModel(id, o.optString("name").ifBlank { id }, free))
            }
            out.sortBy { it.name.lowercase() }
            out.also { cache = it }
        }
    }
}
