package com.lumodsound.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Inicio de sesión de Last.fm sin "Callback URL" (Last.fm no acepta direcciones con esquema propio como lumodsound://):
 * la app pide un token, abre la página de Last.fm con ese token, el usuario aprueba el acceso y, al volver a la app,
 * se cambia el token aprobado por una clave de sesión. La app nunca ve la contraseña.
 */
object LastfmAuth {
    const val CREATE_API_ACCOUNT_URL = "https://www.last.fm/api/account/create"

    /** Resultado de intentar terminar el inicio de sesión. [waiting]: Last.fm aún no tiene la aprobación del usuario. */
    class PendingResult(val connected: Boolean, val waiting: Boolean, val message: String?)

    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun authorizeUrl(apiKey: String, token: String): String =
        Uri.parse("https://www.last.fm/api/auth/").buildUpon()
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("token", token)
            .build()
            .toString()

    /** Pide un token y abre Last.fm para aprobarlo. Bloqueante. Devuelve un mensaje de error, o null si salió bien. */
    fun begin(context: Context): String? {
        val store = LastfmStore.get(context)
        val s = store.settings.value
        if (!s.hasCredentials) return str(R.string.the_last_fm_api_key)
        val result = LastfmApi.getToken(s.apiKey, s.secret)
        val token = result.token ?: return "Last.fm no dio un token (${result.error ?: "sin respuesta"})"
        store.savePendingToken(token)
        return try {
            openUrl(context, authorizeUrl(s.apiKey, token))
            null
        } catch (e: Exception) {
            "No se pudo abrir el navegador"
        }
    }

    /** Intenta terminar el inicio de sesión con el token pendiente. Bloqueante. */
    fun completePending(context: Context): PendingResult {
        val store = LastfmStore.get(context)
        val s = store.settings.value
        val token = store.pendingToken()
            ?: return PendingResult(false, false, str(R.string.there_s_no_sign_in))
        if (!s.hasCredentials) return PendingResult(false, false, str(R.string.the_last_fm_api_key))

        val result = LastfmApi.getSession(s.apiKey, s.secret, token)
        val session = result.session
        if (session != null) {
            store.clearPendingToken()
            store.connect(session.key, session.username)
            return PendingResult(true, false, null)
        }
        return when (result.code) {
            // 14: el token existe pero el usuario todavía no lo aprobó
            14 -> PendingResult(false, true, str(R.string.you_haven_t_approved_access))
            // 4 / 15: token inválido o caducado (vale 60 min): hay que empezar de nuevo
            4, 15 -> {
                store.clearPendingToken()
                PendingResult(false, false, str(R.string.the_link_expired_tap_sign))
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Inicio de sesión de Last.fm sin "Callback URL" (Last.fm no acepta direcciones con esquema propio como lumodsound://):
 * la app pide un token, abre la página de Last.fm con ese token, el usuario aprueba el acceso y, al volver a la app,
 * se cambia el token aprobado por una clave de sesión. La app nunca ve la contraseña.
 */
object LastfmAuth {
    const val CREATE_API_ACCOUNT_URL = "https://www.last.fm/api/account/create"

    /** Resultado de intentar terminar el inicio de sesión. [waiting]: Last.fm aún no tiene la aprobación del usuario. */
    class PendingResult(val connected: Boolean, val waiting: Boolean, val message: String?)

    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun authorizeUrl(apiKey: String, token: String): String =
        Uri.parse("https://www.last.fm/api/auth/").buildUpon()
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("token", token)
            .build()
            .toString()

    /** Pide un token y abre Last.fm para aprobarlo. Bloqueante. Devuelve un mensaje de error, o null si salió bien. */
    fun begin(context: Context): String? {
        val store = LastfmStore.get(context)
        val s = store.settings.value
        if (!s.hasCredentials) return str(R.string.the_last_fm_api_key)
        val result = LastfmApi.getToken(s.apiKey, s.secret)
        val token = result.token ?: return "Last.fm no dio un token (${result.error ?: "sin respuesta"})"
        store.savePendingToken(token)
        return try {
            openUrl(context, authorizeUrl(s.apiKey, token))
            null
        } catch (e: Exception) {
            "No se pudo abrir el navegador"
        }
    }

    /** Intenta terminar el inicio de sesión con el token pendiente. Bloqueante. */
    fun completePending(context: Context): PendingResult {
        val store = LastfmStore.get(context)
        val s = store.settings.value
        val token = store.pendingToken()
            ?: return PendingResult(false, false, str(R.string.there_s_no_sign_in))
        if (!s.hasCredentials) return PendingResult(false, false, str(R.string.the_last_fm_api_key))

        val result = LastfmApi.getSession(s.apiKey, s.secret, token)
        val session = result.session
        if (session != null) {
            store.clearPendingToken()
            store.connect(session.key, session.username)
            return PendingResult(true, false, null)
        }
        return when (result.code) {
            // 14: el token existe pero el usuario todavía no lo aprobó
            14 -> PendingResult(false, true, str(R.string.you_haven_t_approved_access))
            // 4 / 15: token inválido o caducado (vale 60 min): hay que empezar de nuevo
            4, 15 -> {
                store.clearPendingToken()
                PendingResult(false, false, str(R.string.the_link_expired_tap_sign))
=======

/**
 * Inicio de sesión de Last.fm sin "Callback URL" (Last.fm no acepta direcciones con esquema propio como lumodsound://):
 * la app pide un token, abre la página de Last.fm con ese token, el usuario aprueba el acceso y, al volver a la app,
 * se cambia el token aprobado por una clave de sesión. La app nunca ve la contraseña.
 */
object LastfmAuth {
    const val CREATE_API_ACCOUNT_URL = "https://www.last.fm/api/account/create"

    /** Resultado de intentar terminar el inicio de sesión. [waiting]: Last.fm aún no tiene la aprobación del usuario. */
    class PendingResult(val connected: Boolean, val waiting: Boolean, val message: String?)

    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun authorizeUrl(apiKey: String, token: String): String =
        Uri.parse("https://www.last.fm/api/auth/").buildUpon()
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("token", token)
            .build()
            .toString()

    /** Pide un token y abre Last.fm para aprobarlo. Bloqueante. Devuelve un mensaje de error, o null si salió bien. */
    fun begin(context: Context): String? {
        val store = LastfmStore.get(context)
        val s = store.settings.value
        if (!s.hasCredentials) return "Faltan la API key y el secreto de Last.fm"
        val result = LastfmApi.getToken(s.apiKey, s.secret)
        val token = result.token ?: return "Last.fm no dio un token (${result.error ?: "sin respuesta"})"
        store.savePendingToken(token)
        return try {
            openUrl(context, authorizeUrl(s.apiKey, token))
            null
        } catch (e: Exception) {
            "No se pudo abrir el navegador"
        }
    }

    /** Intenta terminar el inicio de sesión con el token pendiente. Bloqueante. */
    fun completePending(context: Context): PendingResult {
        val store = LastfmStore.get(context)
        val s = store.settings.value
        val token = store.pendingToken()
            ?: return PendingResult(false, false, "No hay un inicio de sesión en curso. Toca «Iniciar sesión» otra vez.")
        if (!s.hasCredentials) return PendingResult(false, false, "Faltan la API key y el secreto de Last.fm")

        val result = LastfmApi.getSession(s.apiKey, s.secret, token)
        val session = result.session
        if (session != null) {
            store.clearPendingToken()
            store.connect(session.key, session.username)
            return PendingResult(true, false, null)
        }
        return when (result.code) {
            // 14: el token existe pero el usuario todavía no lo aprobó
            14 -> PendingResult(false, true, "Todavía no aprobaste el acceso en Last.fm")
            // 4 / 15: token inválido o caducado (vale 60 min): hay que empezar de nuevo
            4, 15 -> {
                store.clearPendingToken()
                PendingResult(false, false, "El enlace caducó. Toca «Iniciar sesión» otra vez.")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
            }
            else -> PendingResult(false, true, "Last.fm no respondió (${result.error ?: "sin respuesta"})")
        }
    }
}
