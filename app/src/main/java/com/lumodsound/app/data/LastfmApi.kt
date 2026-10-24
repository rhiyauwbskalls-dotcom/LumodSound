package com.lumodsound.app.data

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Cliente mínimo de la API de Last.fm (ws.audioscrobbler.com/2.0). Todo va por POST con formulario, firmado
 * con `api_sig` (MD5), y las respuestas se leen como JSON suelto: Last.fm devuelve números como texto y un campo
 * es objeto con un elemento y lista con varios, así que no se usan clases fijas.
 */
object LastfmApi {

    private const val TAG = "Lastfm"
    private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Lo que Last.fm necesita saber de una canción. */
    class Track(
        val artist: String,
        val title: String,
        val album: String? = null,
        val durationSec: Int? = null
    )

    class Session(val username: String, val key: String)

    /** [session] si salió bien; si no, [error] explica por qué y [code] es el código de Last.fm (0 = sin respuesta). */
    class SessionResult(val session: Session?, val error: String?, val code: Int = 0)

    class TokenResult(val token: String?, val error: String?)

    /**
     * Resultado de una escritura. [Ignored] importa: Last.fm responde "ok" incluso cuando descarta el scrobble
     * (nombre de artista o canción que no pasa su filtro, fecha fuera de rango, límite diario...) y solo lo dice
     * en `ignoredMessage`.
     */
    sealed interface Outcome {
        object Ok : Outcome

        class Ignored(val code: Int, val message: String) : Outcome

        class Error(val code: Int, val message: String) : Outcome {
            /** 9 = la clave de sesión ya no vale: hay que iniciar sesión otra vez. */
            val needsReauth: Boolean get() = code == 9

            /** 11 servicio fuera de línea, 16 fallo temporal, 29 límite de peticiones, -1 sin red. */
            val retryable: Boolean get() = code == 11 || code == 16 || code == 29 || code == -1
        }
    }

    /**
     * `api_sig`: parámetros ordenados por nombre y pegados como `<nombre><valor>`, más el secreto, todo en MD5.
     * `format` no se firma (firmarlo es el motivo clásico del error 13 "Invalid method signature").
     */
    private fun sign(params: Map<String, String>, secret: String): String {
        val raw = params
            .filterKeys { it != "format" && it != "callback" }
            .entries
            .sortedBy { it.key }
            .joinToString(separator = "") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Firma [params], los envía y devuelve el JSON (también cuando el HTTP no es 2xx: los errores vienen en el cuerpo). */
    private fun call(params: Map<String, String>, secret: String): JSONObject? {
        return try {
            val signed = params + ("api_sig" to sign(params, secret)) + ("format" to "json")
            val form = FormBody.Builder().apply { signed.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(form)
                .header("User-Agent", "LumodSound/1.0")
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) null else JSONObject(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, str(R.string.request_failed, e.javaClass.simpleName, e.message))
            null
        }
    }

    private fun JSONObject.asError(): Outcome.Error? {
        if (!has("error")) return null
        return Outcome.Error(optInt("error", -1), optString("message").ifBlank { str(R.string.last_fm_error) })
    }

    private fun JSONObject.ignored(): Outcome.Ignored? {
        val info = optJSONObject("ignoredMessage") ?: return null
        val code = info.optInt("code", 0)
        if (code == 0) return null
        return Outcome.Ignored(code, info.optString("#text"))
    }

    /** Pide un token nuevo (vale 60 minutos). Hay que mandar al usuario a aprobarlo en last.fm antes de [getSession]. */
    fun getToken(apiKey: String, secret: String): TokenResult {
        val json = call(mapOf("method" to "auth.getToken", "api_key" to apiKey), secret)
            ?: return TokenResult(null, str(R.string.no_response))
        json.asError()?.let { return TokenResult(null, "${it.code}: ${it.message}") }
        val token = json.optString("token")
        return if (token.isBlank()) TokenResult(null, str(R.string.response_without_a_token)) else TokenResult(token, null)
    }

    /** Cambia el token aprobado por una clave de sesión (no caduca; el usuario la revoca desde last.fm). Consume el token. */
    fun getSession(apiKey: String, secret: String, token: String): SessionResult {
        val json = call(
            mapOf("method" to "auth.getSession", "api_key" to apiKey, "token" to token),
            secret
        ) ?: return SessionResult(null, str(R.string.no_response))
        json.asError()?.let { return SessionResult(null, "${it.code}: ${it.message}", it.code) }
        val session = json.optJSONObject("session") ?: return SessionResult(null, str(R.string.response_without_a_session))
        val name = session.optString("name")
        val key = session.optString("key")
        if (name.isBlank() || key.isBlank()) return SessionResult(null, "respuesta incompleta")
        return SessionResult(Session(name, key), null)
    }

    /** Avisa de lo que suena ahora. No afecta a las listas y no merece reintentos. */
    fun updateNowPlaying(apiKey: String, secret: String, sessionKey: String, track: Track): Outcome {
        val params = buildMap {
            put("method", "track.updateNowPlaying")
            put("api_key", apiKey)
            put("sk", sessionKey)
            put("artist", track.artist)
            put("track", track.title)
            track.album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            track.durationSec?.takeIf { it > 0 }?.let { put("duration", it.toString()) }
        }
        val json = call(params, secret) ?: return Outcome.Error(-1, str(R.string.no_connection))
        json.asError()?.let { return it }
        return json.optJSONObject("nowplaying")?.ignored() ?: Outcome.Ok
    }

    /** Registra una escucha. [startedAtSec] es el momento en que EMPEZÓ a sonar (lo que mandan todos los scrobblers). */
    fun scrobble(apiKey: String, secret: String, sessionKey: String, track: Track, startedAtSec: Long): Outcome {
        val params = buildMap {
            put("method", "track.scrobble")
            put("api_key", apiKey)
            put("sk", sessionKey)
            put("artist[0]", track.artist)
            put("track[0]", track.title)
            put("timestamp[0]", startedAtSec.toString())
            track.album?.takeIf { it.isNotBlank() }?.let { put("album[0]", it) }
            track.durationSec?.takeIf { it > 0 }?.let { put("duration[0]", it.toString()) }
            put("chosenByUser[0]", "1")
        }
        val json = call(params, secret) ?: return Outcome.Error(-1, str(R.string.no_connection))
=======

/**
 * Cliente mínimo de la API de Last.fm (ws.audioscrobbler.com/2.0). Todo va por POST con formulario, firmado
 * con `api_sig` (MD5), y las respuestas se leen como JSON suelto: Last.fm devuelve números como texto y un campo
 * es objeto con un elemento y lista con varios, así que no se usan clases fijas.
 */
object LastfmApi {

    private const val TAG = "Lastfm"
    private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Lo que Last.fm necesita saber de una canción. */
    class Track(
        val artist: String,
        val title: String,
        val album: String? = null,
        val durationSec: Int? = null
    )

    class Session(val username: String, val key: String)

    /** [session] si salió bien; si no, [error] explica por qué y [code] es el código de Last.fm (0 = sin respuesta). */
    class SessionResult(val session: Session?, val error: String?, val code: Int = 0)

    class TokenResult(val token: String?, val error: String?)

    /**
     * Resultado de una escritura. [Ignored] importa: Last.fm responde "ok" incluso cuando descarta el scrobble
     * (nombre de artista o canción que no pasa su filtro, fecha fuera de rango, límite diario...) y solo lo dice
     * en `ignoredMessage`.
     */
    sealed interface Outcome {
        object Ok : Outcome

        class Ignored(val code: Int, val message: String) : Outcome

        class Error(val code: Int, val message: String) : Outcome {
            /** 9 = la clave de sesión ya no vale: hay que iniciar sesión otra vez. */
            val needsReauth: Boolean get() = code == 9

            /** 11 servicio fuera de línea, 16 fallo temporal, 29 límite de peticiones, -1 sin red. */
            val retryable: Boolean get() = code == 11 || code == 16 || code == 29 || code == -1
        }
    }

    /**
     * `api_sig`: parámetros ordenados por nombre y pegados como `<nombre><valor>`, más el secreto, todo en MD5.
     * `format` no se firma (firmarlo es el motivo clásico del error 13 "Invalid method signature").
     */
    private fun sign(params: Map<String, String>, secret: String): String {
        val raw = params
            .filterKeys { it != "format" && it != "callback" }
            .entries
            .sortedBy { it.key }
            .joinToString(separator = "") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Firma [params], los envía y devuelve el JSON (también cuando el HTTP no es 2xx: los errores vienen en el cuerpo). */
    private fun call(params: Map<String, String>, secret: String): JSONObject? {
        return try {
            val signed = params + ("api_sig" to sign(params, secret)) + ("format" to "json")
            val form = FormBody.Builder().apply { signed.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(form)
                .header("User-Agent", "LumodSound/1.0")
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) null else JSONObject(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Petición fallida: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun JSONObject.asError(): Outcome.Error? {
        if (!has("error")) return null
        return Outcome.Error(optInt("error", -1), optString("message").ifBlank { "Error de Last.fm" })
    }

    private fun JSONObject.ignored(): Outcome.Ignored? {
        val info = optJSONObject("ignoredMessage") ?: return null
        val code = info.optInt("code", 0)
        if (code == 0) return null
        return Outcome.Ignored(code, info.optString("#text"))
    }

    /** Pide un token nuevo (vale 60 minutos). Hay que mandar al usuario a aprobarlo en last.fm antes de [getSession]. */
    fun getToken(apiKey: String, secret: String): TokenResult {
        val json = call(mapOf("method" to "auth.getToken", "api_key" to apiKey), secret)
            ?: return TokenResult(null, "sin respuesta")
        json.asError()?.let { return TokenResult(null, "${it.code}: ${it.message}") }
        val token = json.optString("token")
        return if (token.isBlank()) TokenResult(null, "respuesta sin token") else TokenResult(token, null)
    }

    /** Cambia el token aprobado por una clave de sesión (no caduca; el usuario la revoca desde last.fm). Consume el token. */
    fun getSession(apiKey: String, secret: String, token: String): SessionResult {
        val json = call(
            mapOf("method" to "auth.getSession", "api_key" to apiKey, "token" to token),
            secret
        ) ?: return SessionResult(null, "sin respuesta")
        json.asError()?.let { return SessionResult(null, "${it.code}: ${it.message}", it.code) }
        val session = json.optJSONObject("session") ?: return SessionResult(null, "respuesta sin sesión")
        val name = session.optString("name")
        val key = session.optString("key")
        if (name.isBlank() || key.isBlank()) return SessionResult(null, "respuesta incompleta")
        return SessionResult(Session(name, key), null)
    }

    /** Avisa de lo que suena ahora. No afecta a las listas y no merece reintentos. */
    fun updateNowPlaying(apiKey: String, secret: String, sessionKey: String, track: Track): Outcome {
        val params = buildMap {
            put("method", "track.updateNowPlaying")
            put("api_key", apiKey)
            put("sk", sessionKey)
            put("artist", track.artist)
            put("track", track.title)
            track.album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            track.durationSec?.takeIf { it > 0 }?.let { put("duration", it.toString()) }
        }
        val json = call(params, secret) ?: return Outcome.Error(-1, "Sin conexión")
        json.asError()?.let { return it }
        return json.optJSONObject("nowplaying")?.ignored() ?: Outcome.Ok
    }

    /** Registra una escucha. [startedAtSec] es el momento en que EMPEZÓ a sonar (lo que mandan todos los scrobblers). */
    fun scrobble(apiKey: String, secret: String, sessionKey: String, track: Track, startedAtSec: Long): Outcome {
        val params = buildMap {
            put("method", "track.scrobble")
            put("api_key", apiKey)
            put("sk", sessionKey)
            put("artist[0]", track.artist)
            put("track[0]", track.title)
            put("timestamp[0]", startedAtSec.toString())
            track.album?.takeIf { it.isNotBlank() }?.let { put("album[0]", it) }
            track.durationSec?.takeIf { it > 0 }?.let { put("duration[0]", it.toString()) }
            put("chosenByUser[0]", "1")
        }
        val json = call(params, secret) ?: return Outcome.Error(-1, "Sin conexión")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
        json.asError()?.let { return it }
        val scrobbles = json.optJSONObject("scrobbles") ?: return Outcome.Ok
        // Con una escucha llega un objeto; con varias, una lista: se lee lo que llegue.
        val first = scrobbles.optJSONObject("scrobble")
            ?: scrobbles.optJSONArray("scrobble")?.optJSONObject(0)
        return first?.ignored() ?: Outcome.Ok
    }
}
