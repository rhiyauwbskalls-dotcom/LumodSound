package com.lumodsound.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Llamadas mínimas a la API de Discord. Todas son bloqueantes: usarlas desde Dispatchers.IO. */
object DiscordApi {
    private const val BASE = "https://discord.com/api/v10"
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** `authorization` es el encabezado completo, por ejemplo "Bearer abc...". */
    fun fetchUsername(authorization: String): String? = try {
        val req = Request.Builder().url("$BASE/users/@me").header("Authorization", authorization).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val json = JSONObject(res.body?.string().orEmpty())
            json.optString("global_name").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("username").takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Discord solo muestra imágenes de su propio CDN: este endpoint recibe una URL externa
     * (la portada de la canción) y devuelve la ruta "mp:..." que se puede usar como imagen.
     */
    fun externalAsset(authorization: String, appId: String, imageUrl: String): String? = try {
        val body = JSONObject().put("urls", JSONArray().put(imageUrl)).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://discord.com/api/v9/applications/$appId/external-assets")
            .header("Authorization", authorization)
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val path = JSONArray(res.body?.string().orEmpty()).optJSONObject(0)?.optString("external_asset_path")
            path?.takeIf { it.isNotBlank() }?.let { "mp:$it" }
        }
    } catch (e: Exception) {
        null
    }
}
