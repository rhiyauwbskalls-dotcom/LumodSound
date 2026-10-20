package com.sonaris.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Inicio de sesión con OAuth2 + PKCE: se abre el navegador, Discord redirige a
 * [REDIRECT_URI] con un código y ese código se cambia por un token de acceso.
 * La app de Discord debe ser un "Public Client" y tener registrada esa URL de redirección.
 */
object DiscordAuth {
    const val REDIRECT_URI = "ytmusicdiscord://oauth2/callback"
    private const val SCOPES = "openid sdk.social_layer_presence"
    private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"

    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    /** Abre el navegador con la pantalla de autorización de Discord. */
    fun start(context: Context, clientId: String) {
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(16)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            URL_SAFE
        )
        DiscordStore.get(context).savePending(PendingAuth(clientId, verifier, state))

        val url = Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Cambia el código por los tokens y deja la cuenta conectada. Devuelve un mensaje de error, o null si salió bien. */
    fun complete(context: Context, code: String, state: String?): String? {
        val store = DiscordStore.get(context)
        val pending = store.takePending() ?: return "No había un inicio de sesión en curso"
        if (state != pending.state) return "Respuesta de Discord no válida"

        val (status, json) = post(
            pending.clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to pending.verifier
        )
        if (status !in 200..299 || json == null) {
            val reason = json?.optString("error_description")?.takeIf { it.isNotBlank() }
                ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "HTTP $status"
            return "Discord rechazó el inicio de sesión ($reason)"
        }
        val access = json.optString("access_token")
        if (access.isBlank()) return "Discord no devolvió el token"

        val username = DiscordApi.fetchUsername("Bearer $access") ?: "Discord"
        store.connect(access, json.optString("refresh_token").ifBlank { null }, json.optLong("expires_in", 604800), username)
        return null
    }

    /** Token de acceso vigente; lo renueva si le queda menos de una hora. Bloqueante. */
    fun validAccessToken(context: Context): String? {
        val store = DiscordStore.get(context)
        val s = store.settings.value
        val access = s.accessToken ?: return null
        val now = System.currentTimeMillis() / 1000
        if (s.expiresAtSec == 0L || s.expiresAtSec - now > 3600) return access

        val refresh = s.refreshToken
        val stillValid = if (s.expiresAtSec > now) access else null
        if (refresh == null || s.appId.isBlank()) return stillValid

        val (status, json) = post(s.appId, "grant_type" to "refresh_token", "refresh_token" to refresh)
        if (status in 200..299 && json != null) {
            val newAccess = json.optString("access_token")
            if (newAccess.isNotBlank()) {
                store.saveTokens(newAccess, json.optString("refresh_token").ifBlank { null }, json.optLong("expires_in", 604800))
                return newAccess
            }
        }
        // La renovación ya no es posible: hay que iniciar sesión de nuevo
        if (status == 400 && json?.optString("error") == "invalid_grant") store.disconnect()
        return stillValid
    }

    private fun post(clientId: String, vararg params: Pair<String, String>): Pair<Int, JSONObject?> = try {
        val body = FormBody.Builder().add("client_id", clientId).apply { params.forEach { (k, v) -> add(k, v) } }.build()
        client.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { res ->
            res.code to runCatching { JSONObject(res.body?.string().orEmpty()) }.getOrNull()
        }
    } catch (e: Exception) {
        0 to null
    }

    private const val URL_SAFE = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, URL_SAFE)
    }
}
