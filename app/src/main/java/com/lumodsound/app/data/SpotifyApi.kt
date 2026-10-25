package com.lumodsound.app.data

import android.content.Context
import com.lumodsound.app.R
import com.lumodsound.app.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/** La clave de sesión ya no vale (HTTP 401): hay que pedir otro token. */
private class SpotifyAuthException : Exception()

/**
 * Cliente de la API web NO oficial de Spotify (la que usa open.spotify.com con la cookie `sp_dc`):
 * token de acceso, búsqueda de la canción, letras sincronizadas y Canvas.
 *
 * Son endpoints internos que Spotify cambia sin avisar. La parte más frágil es el token: pide un código
 * TOTP cuyo secreto Spotify rota cada pocos días (ver [BUILTIN_SECRETS]).
 */
object SpotifyApi {

    /**
     * Secretos TOTP del reproductor web (versión -> cifra). Spotify los rota cada pocos días, así que además
     * de estos se baja la lista al día de [SECRETS_URL] y se prueban de la versión más nueva a la más vieja.
     */
    private val BUILTIN_SECRETS: Map<Int, IntArray> = mapOf(
        61 to intArrayOf(44, 55, 47, 42, 70, 40, 34, 114, 76, 74, 50, 111, 120, 97, 75, 76, 94, 102, 43, 69, 49, 120, 118, 80, 64, 78),
        60 to intArrayOf(79, 109, 69, 123, 90, 65, 46, 74, 94, 34, 58, 48, 70, 71, 92, 85, 122, 63, 91, 64, 87, 87),
        59 to intArrayOf(123, 105, 79, 70, 110, 59, 52, 125, 60, 49, 80, 70, 89, 75, 80, 86, 63, 53, 123, 37, 117, 49, 52, 93, 77, 62, 47, 86, 48, 104, 68, 72)
    )
    private const val SECRETS_URL =
        "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/main/secrets/secretDict.json"

    @Volatile private var remoteSecrets: Map<Int, IntArray>? = null

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Qué pasó en la última llamada (HTTP, sin resultados...), para mostrarlo en Ajustes. */
    @Volatile var lastNote: String? = null

    class TokenResult(val token: String?, val expiresAtMs: Long, val error: String?)

    /** Hasta cuándo no se consulta la búsqueda de Spotify porque respondió 429 (límite de peticiones). */
    @Volatile private var searchBlockedUntilMs = 0L

    /** Segundos que faltan para poder volver a buscar en Spotify (0 si no hay pausa). */
    fun searchCooldownSec(): Long = minOf(remainingSec(pathBlockedUntilMs), remainingSec(searchBlockedUntilMs))

    /** Pausa del buscador interno (pathfinder), independiente de la de `v1/search`. */
    @Volatile private var pathBlockedUntilMs = 0L

    private fun remainingSec(untilMs: Long): Long = ((untilMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)

    /** song.link retiró su API pública: tras el primer 401/403 no se vuelve a llamar en esta sesión. */
    @Volatile private var songLinkDisabled = false

    private class Resp(val code: Int, val bytes: ByteArray, val retryAfterSec: Long = 0L, val setCookies: List<String> = emptyList()) {
        val text: String get() = String(bytes, Charsets.UTF_8)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun send(request: Request): Resp? = try {
        client.newCall(request).execute().use {
            Resp(
                it.code, it.body?.bytes() ?: ByteArray(0),
                it.header("Retry-After")?.trim()?.toLongOrNull() ?: 0L,
                it.headers("Set-Cookie")
            )
        }
    } catch (e: Exception) {
        null
    }

    // ---------- Token ----------

    /** Código TOTP de 6 dígitos (HMAC-SHA1, paso de 30 s) con la clave derivada de [cipher]. */
    private fun totp(cipher: IntArray, nowMs: Long): String {
        val key = cipher.mapIndexed { i, b -> b xor ((i % 33) + 9) }.joinToString("").toByteArray(Charsets.UTF_8)
        val counter = nowMs / 1000 / 30
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }.doFinal(msg)
        val off = mac.last().toInt() and 0xF
        val bin = ((mac[off].toInt() and 0x7F) shl 24) or
            ((mac[off + 1].toInt() and 0xFF) shl 16) or
            ((mac[off + 2].toInt() and 0xFF) shl 8) or
            (mac[off + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    private fun loadRemoteSecrets(): Map<Int, IntArray> {
        val r = send(Request.Builder().url(SECRETS_URL).header("User-Agent", UA).build()) ?: return emptyMap()
        if (r.code != 200) return emptyMap()
        return try {
            val json = JSONObject(r.text)
            val out = HashMap<Int, IntArray>()
            for (key in json.keys()) {
                val version = key.toIntOrNull() ?: continue
                val arr = json.optJSONArray(key) ?: continue
                out[version] = IntArray(arr.length()) { arr.optInt(it) }
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Secretos a probar, de la versión más nueva a la más vieja. */
    private fun secrets(reload: Boolean): List<Pair<Int, IntArray>> {
        if (reload || remoteSecrets == null) remoteSecrets = loadRemoteSecrets()
        return (BUILTIN_SECRETS + (remoteSecrets ?: emptyMap())).entries
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }

    /** Hora del servidor de Spotify (ms); si no responde, la del teléfono. */
    private fun serverTimeMs(fallback: Long): Long {
        val r = send(
            Request.Builder().url("https://open.spotify.com/api/server-time")
                .header("User-Agent", UA).build()
        ) ?: return fallback
        if (r.code != 200) return fallback
        val secs = try { JSONObject(r.text).optLong("serverTime", 0L) } catch (e: Exception) { 0L }
        return if (secs > 0) secs * 1000 else fallback
    }

    // ---------- Token como lo pide ArchiveTune (secretos base32 del gist de la comunidad + TOTP estándar) ----------

    private const val NUANCES_URL =
        "https://gist.githubusercontent.com/sonic-liberation/22ed9c6ba463899e933427f7de1f0eef/raw/nuances.json"

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (c in input.uppercase().replace("=", "")) {
            val v = alphabet.indexOf(c)
            if (v < 0) continue
            buffer = ((buffer shl 5) or v) and 0xFFFF
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    /** TOTP estándar (RFC 6238, HMAC-SHA1, 30 s, 6 dígitos) con el secreto en base32 y la hora del servidor de Spotify. */
    private fun stdTotp(secretB32: String, serverTimeSec: Long): String? {
        val key = base32Decode(secretB32)
        if (key.isEmpty()) return null
        val msg = ByteBuffer.allocate(8).putLong(serverTimeSec / 30).array()
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }.doFinal(msg)
        val off = mac.last().toInt() and 0xF
        val bin = ((mac[off].toInt() and 0x7F) shl 24) or
            ((mac[off + 1].toInt() and 0xFF) shl 16) or
            ((mac[off + 2].toInt() and 0xFF) shl 8) or
            (mac[off + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    /** Devuelve el token, un error definitivo (cookie anónima) o null si no sirvió (el motivo queda en [attempts]). */
    private fun tokenViaNuances(spDc: String, attempts: MutableList<String>): TokenResult? {
        val nr = send(Request.Builder().url(NUANCES_URL).header("User-Agent", UA).build())
        if (nr == null || nr.code != 200) {
            attempts.add("gist:${nr?.code ?: "red"}")
            return null
        }
        val list: List<Pair<Int, String>> = try {
            val arr = JSONArray(nr.text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val sec = o.optString("s")
                val ver = o.optInt("v", 0)
                val valid = sec.isNotBlank() && sec.all { ch -> ch == '=' || ch.uppercaseChar() in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }
                if (ver > 0 && valid) ver to sec else null
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (list.isEmpty()) {
            attempts.add("gist:sin secretos")
            return null
        }
        val serverSec = serverTimeMs(System.currentTimeMillis()) / 1000
        for ((ver, sec) in list.sortedByDescending { it.first }.take(2)) {
            val code = stdTotp(sec, serverSec) ?: continue
            val url = "https://open.spotify.com/api/token?reason=transport&productType=web-player" +
                "&totp=$code&totpServer=$code&totpVer=$ver"
            val r = send(
                Request.Builder().url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en")
                    .header("Cookie", "sp_dc=$spDc")
                    .build()
            )
            if (r == null) {
                attempts.add("g$ver:red")
                continue
            }
            if (r.code != 200) {
                attempts.add("g$ver:${r.code}")
                continue
            }
            val json = try { JSONObject(r.text) } catch (e: Exception) { null } ?: continue
            val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: continue
            if (json.optBoolean("isAnonymous", false)) {
                return TokenResult(null, 0, "La cookie sp_dc no vale (caducó o está mal copiada)")
            }
            json.optString("clientId").takeIf { it.isNotBlank() }?.let { clientId = it }
            val exp = json.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 50 * 60_000L)
            return TokenResult(token, exp, null)
        }
        return null
    }

    // ---------- Nombre de usuario (para comprobar que el inicio de sesión es real) ----------

    /** Hash de la consulta "profileAttributes" (perfil de la sesión), el de ArchiveTune. */
    private const val PROFILE_HASH = "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced"

    /**
     * Nombre del usuario de la sesión: primero por pathfinder (`profileAttributes`), luego por `v1/me`.
     * Un token anónimo no tiene perfil, así que si sale un nombre es que la cookie sp_dc es de una cuenta real.
     * Si no se puede leer, devuelve null y el motivo queda en [lastNote].
     */
    fun fetchProfileName(token: String): String? {
        val body = JSONObject()
            .put("variables", JSONObject())
            .put("operationName", "profileAttributes")
            .put(
                "extensions", JSONObject()
                    .put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", PROFILE_HASH))
            ).toString()
        val r1 = send(
            Request.Builder().url("https://api-partner.spotify.com/pathfinder/v2/query")
                .post(body.toRequestBody("application/json;charset=UTF-8".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("App-Platform", "WebPlayer")
                .header("Accept", "application/json")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("User-Agent", UA)
                .build()
        )
        var note1 = "perfil interno: sin respuesta de red"
        if (r1 != null) {
            if (r1.code == 401) throw SpotifyAuthException()
            if (r1.code == 200) {
                val profile = try {
                    JSONObject(r1.text).optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile")
                } catch (e: Exception) { null }
                val name = profile?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: profile?.optString("uri")?.substringAfterLast(':')?.takeIf { it.isNotBlank() && it != "null" }
                if (name != null) return name
                note1 = "perfil interno: la respuesta no trae perfil (¿sesión anónima o hash viejo?)"
            } else {
                note1 = "perfil interno: HTTP ${r1.code}"
            }
        }
        val r2 = send(
            Request.Builder().url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $token")
                .header("App-Platform", "WebPlayer")
                .header("Accept", "application/json")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("User-Agent", UA)
                .build()
        )
        if (r2 == null) {
            lastNote = "$note1 · /v1/me: sin respuesta de red"
            return null
        }
        if (r2.code == 401) throw SpotifyAuthException()
        if (r2.code != 200) {
            lastNote = "$note1 · /v1/me: HTTP ${r2.code}"
            return null
        }
        val me = try { JSONObject(r2.text) } catch (e: Exception) { null }
        val name = me?.optString("display_name")?.takeIf { it.isNotBlank() && it != "null" }
            ?: me?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
        if (name == null) lastNote = "$note1 · /v1/me: sin nombre en la respuesta"
        return name
    }

    fun fetchToken(spDc: String): TokenResult {
        var lastError = "sin respuesta"
        val attempts = mutableListOf<String>()
        var errBody: String? = null
        // Primero, el método de ArchiveTune (otra fuente de secretos TOTP, reason=transport, mismas cabeceras)
        tokenViaNuances(spDc, attempts)?.let { return it }
        // Segunda pasada: si ningún secreto sirvió, se vuelve a bajar la lista por si Spotify los rotó
        for (pass in 0..1) {
            val now = System.currentTimeMillis()
            val server = serverTimeMs(now)
            for ((version, cipher) in secrets(reload = pass == 1)) {
                val url = "https://open.spotify.com/api/token?reason=init&productType=web-player" +
                    "&totp=${totp(cipher, now)}&totpServer=${totp(cipher, server)}&totpVer=$version&ts=$now"
                val r = send(
                    Request.Builder().url(url)
                        .header("User-Agent", UA)
                        .header("Accept", "application/json")
                        .header("App-Platform", "WebPlayer")
                        .header("Cookie", "sp_dc=$spDc")
                        .build()
                ) ?: continue
                if (r.code != 200) {
                    lastError = "HTTP ${r.code}, secreto v$version"
                    attempts.add("v$version:${r.code}")
                    if (errBody == null) errBody = r.text.take(120).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
                    continue
                }
                val json = try { JSONObject(r.text) } catch (e: Exception) { null } ?: continue
                val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: continue
                if (json.optBoolean("isAnonymous", false)) {
                    return TokenResult(null, 0, "La cookie sp_dc no vale (caducó o está mal copiada)")
                }
                json.optString("clientId").takeIf { it.isNotBlank() }?.let { clientId = it }
                val exp = json.optLong("accessTokenExpirationTimestampMs", now + 50 * 60_000L)
                return TokenResult(token, exp, null)
            }
        }
        val detail = attempts.takeLast(10).joinToString(" ").takeIf { it.isNotBlank() }
        return TokenResult(
            null, 0,
            "Spotify no dio el token ($lastError)" +
                (detail?.let { " · intentos: $it" } ?: "") +
                (errBody?.let { " · respuesta: $it" } ?: "")
        )
    }

    // ---------- Búsqueda de la canción ----------

    private fun norm(s: String): String = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Hash de la consulta "searchTracks" del buscador interno (pathfinder), el mismo que usa SimpMusic. Spotify lo cambia a veces. */
    private const val SEARCH_HASH = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"

    /** [usable] = el buscador respondió de verdad (con o sin coincidencia); si es false se prueba `v1/search`. */
    private class PathResult(val id: String?, val usable: Boolean)

    /** Hash de "searchDesktop" (POST a pathfinder/v2, la consulta que usa ArchiveTune). No necesita client-token. */
    private const val SEARCH_DESKTOP_HASH = "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49"

    private val idPattern = Regex("[A-Za-z0-9]{22}")

    /** [json] = respuesta válida; [hashRejected] = Spotify ya no conoce ese hash (PersistedQueryNotFound / 412). */
    private class PathHttp(val json: JSONObject?, val hashRejected: Boolean = false)

    /** Interpreta la respuesta de pathfinder: 401 -> renovar token; 429 -> pausa; hash caducado; JSON. */
    private fun readPathfinder(r: Resp?, label: String): PathHttp {
        if (r == null) {
            lastNote = "$label: sin respuesta de red"
            return PathHttp(null)
        }
        if (r.code == 401) throw SpotifyAuthException()
        if (r.code == 429) {
            val waitMs = if (r.retryAfterSec > 0) (r.retryAfterSec * 1000).coerceIn(30_000L, 15 * 60_000L) else 120_000L
            pathBlockedUntilMs = System.currentTimeMillis() + waitMs
            lastNote = "$label: HTTP 429; en pausa ${waitMs / 1000}s"
            return PathHttp(null)
        }
        val text = r.text
        if (r.code == 412 || text.contains("PersistedQueryNotFound", ignoreCase = true)) {
            lastNote = "$label: hash rechazado (PersistedQueryNotFound)"
            return PathHttp(null, hashRejected = true)
        }
        if (r.code != 200) {
            lastNote = "$label: HTTP ${r.code} ${text.take(120)}".trim()
            return PathHttp(null)
        }
        val json = try { JSONObject(text) } catch (e: Exception) { null }
        if (json == null) {
            lastNote = "$label: respuesta ilegible"
            return PathHttp(null)
        }
        val error = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }
        if (error != null && json.optJSONObject("data") == null) {
            lastNote = "$label: Spotify respondió «$error»"
            return PathHttp(null)
        }
        return PathHttp(json)
    }

    /** Consulta 1: `searchDesktop` por POST a pathfinder/v2, solo con el token (sin client-token). */
    private fun pathfinderSearchDesktop(token: String, term: String): PathHttp {
        val body = JSONObject()
            .put(
                "variables", JSONObject()
                    .put("searchTerm", term)
                    .put("offset", 0)
                    .put("limit", 10)
                    .put("numberOfTopResults", 5)
                    .put("includeAudiobooks", false)
                    .put("includeArtistHasConcertsField", false)
                    .put("includePreReleases", false)
                    .put("includeLocalConcertsField", false)
                    .put("includeAuthors", false)
            )
            .put("operationName", "searchDesktop")
            .put(
                "extensions", JSONObject()
                    .put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", SEARCH_DESKTOP_HASH))
            ).toString()
        val r = send(
            Request.Builder().url("https://api-partner.spotify.com/pathfinder/v2/query")
                .post(body.toRequestBody("application/json;charset=UTF-8".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("App-Platform", "WebPlayer")
                .header("Accept", "application/json")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("User-Agent", UA)
                .build()
        )
        return readPathfinder(r, "Búsqueda interna")
    }

    /** Consulta 2 (respaldo, la de SimpMusic): `searchTracks` por GET a pathfinder/v1; sí necesita client-token. */
    private fun pathfinderSearchTracks(token: String, term: String): PathHttp {
        val client = clientToken() ?: return PathHttp(null)
        val variables = JSONObject()
            .put("searchTerm", term)
            .put("offset", 0)
            .put("limit", 10)
            .put("numberOfTopResults", 10)
            .put("includeAudiobooks", true)
            .put("includePreReleases", false)
            .toString()
        val extensions = JSONObject()
            .put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", SEARCH_HASH))
            .toString()
        val url = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=searchTracks" +
            "&variables=${enc(variables).replace("+", "%20")}&extensions=${enc(extensions).replace("+", "%20")}"
        val r = send(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("client-token", client)
                .header("App-Platform", "WebPlayer")
                .header("Accept", "application/json")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("User-Agent", UA)
                .build()
        )
        return readPathfinder(r, "Búsqueda interna (v1)")
    }

    /**
     * Búsqueda por el buscador interno de Spotify (pathfinder), como ArchiveTune y SimpMusic. Limita mucho menos que
     * `api.spotify.com/v1/search` con el token del reproductor web.
     */
    private fun searchViaPathfinder(token: String, title: String, artist: String, durationSec: Long): PathResult {
        val term = "$title $artist".trim()
        val first = pathfinderSearchDesktop(token, term)
        var json = first.json
        if (json == null && first.hashRejected && remainingSec(pathBlockedUntilMs) == 0L) {
            val firstNote = lastNote
            json = pathfinderSearchTracks(token, term).json
            if (json == null) lastNote = "$firstNote · $lastNote"
        }
        if (json == null) return PathResult(null, false)
        val items = json.optJSONObject("data")?.optJSONObject("searchV2")?.optJSONObject("tracksV2")?.optJSONArray("items")
        if (items == null) {
            lastNote = "Búsqueda interna: respuesta sin resultados"
            return PathResult(null, false)
        }
        val wanted = norm(title)
        val wantedArtist = norm(artist)
        var best: Pair<String, Long>? = null // id, diferencia de duración
        var seen = 0
        var firstSeen: String? = null
        for (i in 0 until items.length()) {
            val wrapper = items.optJSONObject(i)?.optJSONObject("item") ?: continue
            val d = wrapper.optJSONObject("data") ?: continue
            val id = listOf(
                wrapper.optString("_uri"), wrapper.optString("uri"),
                d.optString("uri"), d.optString("_uri"), d.optString("id")
            ).map { it.substringAfterLast(':') }.firstOrNull { idPattern.matches(it) } ?: continue
            seen++
            val name = d.optString("name")
            var ms = d.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L
            if (ms <= 0L) ms = d.optLong("durationMs", 0L)
            val sec = ms / 1000
            if (firstSeen == null) firstSeen = "«$name» (${sec}s)"
            val n = norm(name)
            if (n.isBlank() || !(n.contains(wanted) || wanted.contains(n))) continue
            val diff: Long
            if (durationSec > 0 && sec > 0) {
                diff = abs(sec - durationSec)
                if (diff > 15) continue
            } else {
                // Sin duración en la respuesta: se exige que coincida también el artista
                val artists = d.optJSONObject("artists")?.optJSONArray("items")
                var artistOk = wantedArtist.isBlank()
                if (artists != null) {
                    for (k in 0 until artists.length()) {
                        val an = norm(artists.optJSONObject(k)?.optJSONObject("profile")?.optString("name") ?: "")
                        if (an.isNotBlank() && wantedArtist.isNotBlank() && (an.contains(wantedArtist) || wantedArtist.contains(an))) artistOk = true
                    }
                }
                if (!artistOk) continue
                diff = if (durationSec > 0) 10L else 0L
            }
            if (best == null || diff < best.second) best = id to diff
        }
        if (seen == 0) {
            lastNote = "Búsqueda interna: Spotify no devolvió canciones para «$title»"
            return PathResult(null, false) // sin resultados: se deja que pruebe v1/search
        }
        val pick = best
        if (pick != null) {
            lastNote = "Búsqueda: OK (interna)"
            return PathResult(pick.first, true)
        }
        lastNote = "Búsqueda: $seen resultados pero ninguno coincide con «$title» (${durationSec}s); el primero fue $firstSeen"
        return PathResult(null, true)
    }

    /**
     * Id de pista de Spotify: el resultado cuyo título coincide y cuya duración más se acerca. Primero se exige
     * ±6 s; si no hay, se acepta hasta ±15 s (YouTube y Spotify a veces difieren por silencios).
     */
    fun findTrackId(token: String, title: String, artist: String, durationSec: Long): String? {
        val cooldown = searchCooldownSec()
        if (cooldown > 0) {
            lastNote = "Búsqueda: en pausa ${cooldown}s por el límite de Spotify (429)"
            return null
        }
        // 1) Buscador interno (pathfinder); 2) si no respondió, api.spotify.com/v1/search (que suele dar 429)
        val pathNote: String
        if (remainingSec(pathBlockedUntilMs) == 0L) {
            val pr = searchViaPathfinder(token, title, artist, durationSec)
            if (pr.usable) return pr.id
            pathNote = lastNote ?: "Búsqueda interna: sin resultado"
        } else {
            pathNote = "Búsqueda interna: en pausa ${remainingSec(pathBlockedUntilMs)}s"
        }
        val v1Pause = remainingSec(searchBlockedUntilMs)
        if (v1Pause > 0) {
            lastNote = "$pathNote · v1 en pausa ${v1Pause}s"
            return null
        }
        val queries = listOf("track:$title artist:$artist", "$title $artist")
        var seen = 0
        var firstSeen: String? = null
        var lastCode = 0
        for (q in queries) {
            val r = send(
                Request.Builder()
                    .url("https://api.spotify.com/v1/search?type=track&limit=10&q=${enc(q)}")
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", UA)
                    .build()
            )
            if (r == null) {
                lastNote = "Búsqueda: sin respuesta de red"
                continue
            }
            lastCode = r.code
            if (r.code == 401) throw SpotifyAuthException()
            if (r.code == 429) {
                // Pausa global: Retry-After si viene (entre 30 s y 15 min); si no, 2 min. Y no se reintenta con la 2.ª consulta.
                val waitMs = if (r.retryAfterSec > 0) (r.retryAfterSec * 1000).coerceIn(30_000L, 15 * 60_000L) else 120_000L
                searchBlockedUntilMs = System.currentTimeMillis() + waitMs
                lastNote = "$pathNote · v1: HTTP 429 (Spotify limita esta API); en pausa ${waitMs / 1000}s"
                return null
            }
            if (r.code != 200) {
                lastNote = "Búsqueda: HTTP ${r.code}"
                continue
            }
            val items = try {
                JSONObject(r.text).optJSONObject("tracks")?.optJSONArray("items")
            } catch (e: Exception) { null }
            if (items == null) {
                lastNote = "Búsqueda: respuesta sin resultados"
                continue
            }
            val wanted = norm(title)
            var best: Pair<String, Long>? = null // id, diferencia de duración
            for (i in 0 until items.length()) {
                val t = items.optJSONObject(i) ?: continue
                val id = t.optString("id").takeIf { it.isNotBlank() } ?: continue
                seen++
                val name = norm(t.optString("name"))
                val diff = if (durationSec > 0) abs(t.optLong("duration_ms") / 1000 - durationSec) else 0L
                if (firstSeen == null) firstSeen = "«${t.optString("name")}» (${t.optLong("duration_ms") / 1000}s)"
                if (name.isBlank() || !(name.contains(wanted) || wanted.contains(name))) continue
                if (durationSec > 0 && diff > 15) continue
                if (best == null || diff < best.second) best = id to diff
            }
            val pick = best
            if (pick != null && (durationSec <= 0 || pick.second <= 15)) {
                lastNote = "Búsqueda: OK"
                return pick.first
            }
        }
        if (lastNote == null || seen > 0 || lastCode == 200) {
            lastNote = if (seen == 0) "Búsqueda: Spotify no devolvió canciones para «$title»"
            else "Búsqueda: $seen resultados pero ninguno coincide con «$title» (${durationSec}s); el primero fue $firstSeen"
        }
        return null
    }

    // ---------- Letras ----------

    /** Letra de la pista: [LyricsState.Synced] / [LyricsState.Plain], NotFound si no hay, null si falló. */
    fun lyrics(token: String, trackId: String): LyricsState? {
        val r = send(
            Request.Builder()
                .url("https://spclient.wg.spotify.com/color-lyrics/v2/track/$trackId?format=json&vocalRemoval=false&market=from_token")
                .header("Authorization", "Bearer $token")
                .header("App-Platform", "WebPlayer")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .apply { clientToken()?.let { header("client-token", it) } }
                .build()
        )
        if (r == null) {
            lastNote = "Letra: sin respuesta de red"
            return null
        }
        if (r.code == 401) throw SpotifyAuthException()
        if (r.code == 404) {
            lastNote = "Letra: Spotify no tiene letra para esta canción"
            return LyricsState.NotFound
        }
        if (r.code != 200) {
            lastNote = "Letra: HTTP ${r.code}"
            return null
        }
        val lyrics = try { JSONObject(r.text).optJSONObject("lyrics") } catch (e: Exception) { null }
            ?: return LyricsState.NotFound
        val arr = lyrics.optJSONArray("lines") ?: return LyricsState.NotFound
        val synced = lyrics.optString("syncType") == "LINE_SYNCED"
        val lines = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val words = o.optString("words").trim()
            if (words.isEmpty()) continue
            plain += words
            lines += LyricLine(o.optString("startTimeMs").toLongOrNull() ?: 0L, words)
        }
        lastNote = when {
            synced && lines.isNotEmpty() -> "Letra: OK (sincronizada)"
            plain.isNotEmpty() -> "Letra: OK (sin sincronizar)"
            else -> "Letra: vacía"
        }
        return when {
            synced && lines.isNotEmpty() -> LyricsState.Synced(lines.sortedBy { it.timeMs })
            plain.isNotEmpty() -> LyricsState.Plain(plain)
            else -> LyricsState.NotFound
        }
    }

    // ---------- Token de cliente (lo piden las consultas "pathfinder") ----------

    /** Id de cliente del reproductor web; se actualiza con el que devuelve el token de acceso. */
    @Volatile private var clientId = "d8a5ed958d274c2e8ee717e6a4b0971d"
    @Volatile private var clientTokenValue: String? = null
    @Volatile private var clientTokenExpMs = 0L

    private fun randomHex(n: Int): String {
        val chars = "0123456789abcdef"
        val rnd = java.security.SecureRandom()
        return buildString { repeat(n) { append(chars[rnd.nextInt(16)]) } }
    }

    private const val CLIENT_TOKEN_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"

    private const val WEB_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /**
     * Lee de la página de open.spotify.com la versión de cliente actual (`appServerConfig` en base64) y el id de
     * dispositivo de la cookie `sp_t`. Una versión fija caduca y Spotify responde 400 al pedir el client-token.
     */
    private fun fetchWebConfig(): Pair<String, String>? {
        val r = send(
            Request.Builder().url("https://open.spotify.com/")
                .header("User-Agent", WEB_MOBILE_UA)
                .header("Accept", "text/html")
                .build()
        )
        if (r == null || r.code != 200) return null
        val encoded = Regex("""<script[^>]*id="appServerConfig"[^>]*>([^<]+)</script>""")
            .find(r.text)?.groupValues?.get(1)?.trim() ?: return null
        val config = try {
            JSONObject(String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8))
        } catch (e: Exception) {
            return null
        }
        val version = config.optString("clientVersion").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val deviceId = r.setCookies
            .map { it.substringBefore(';') }
            .firstOrNull { it.startsWith("sp_t=") }
            ?.substringAfter('=')?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()
        return version to deviceId
    }

    private fun requestClientToken(body: String, userAgent: String, browserHeaders: Boolean): Resp? {
        val b = Request.Builder().url("https://clienttoken.spotify.com/v1/clienttoken")
            // Bytes, no String: con String OkHttp añade "; charset=utf-8" al Content-Type y clienttoken responde 400.
            // ArchiveTune también lo manda como bytes con "application/json" exacto.
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
        if (browserHeaders) b.header("Origin", "https://open.spotify.com").header("Referer", "https://open.spotify.com/")
        return send(b.build())
    }

    /** `client-token` del reproductor web (se pide a clienttoken.spotify.com y se reutiliza una hora). */
    private fun clientToken(): String? {
        val cached = clientTokenValue
        if (cached != null && System.currentTimeMillis() < clientTokenExpMs) return cached
        // Intento A: exactamente la petición que hace SimpMusic (la que Spotify acepta). Intento B: la anterior.
        val bodyA = JSONObject().put(
            "client_data", JSONObject()
                .put("client_version", "1.2.62.476.g2ad6e7f3")
                .put("client_id", "d8a5ed958d274c2e8ee717e6a4b0971d")
                .put(
                    "js_sdk_data", JSONObject()
                        .put("device_brand", "Apple")
                        .put("device_model", "unknown")
                        .put("os", "macos")
                        .put("os_version", "10.15.7")
                        .put("device_id", "4fd0c748-b282-4927-9658-6d51a24e58b7")
                        .put("device_type", "computer")
                )
        ).toString()
        // Intento 0 (el que usa ArchiveTune): versión de cliente REAL leída de open.spotify.com y device_id de la cookie sp_t.
        val tried = mutableListOf<String>()
        fun attempt(name: String, body: String, ua: String, browser: Boolean): Resp? {
            val x = requestClientToken(body, ua, browser)
            tried.add("$name:${x?.code ?: "red"}")
            return x
        }
        var r: Resp? = null
        val web = fetchWebConfig()
        if (web == null) tried.add("config-web:no") else tried.add("config-web:v${web.first}")
        if (web != null) {
            val bodyW = JSONObject().put(
                "client_data", JSONObject()
                    .put("client_version", web.first)
                    .put("client_id", clientId)
                    .put(
                        "js_sdk_data", JSONObject()
                            .put("device_brand", "unknown")
                            .put("device_model", "unknown")
                            .put("os", "android")
                            .put("os_version", "14")
                            .put("device_id", web.second)
                            .put("device_type", "smartphone")
                    )
            ).toString()
            r = attempt("web", bodyW, WEB_MOBILE_UA, false)
        }
        if (r == null || r.code != 200) {
            r = attempt("A", bodyA, CLIENT_TOKEN_UA, false) ?: r
        }
        if (r != null && r.code == 400) {
            val bodyB = JSONObject().put(
                "client_data", JSONObject()
                    .put("client_version", "1.2.74.435.g7f9d3b1a")
                    .put("client_id", clientId)
                    .put(
                        "js_sdk_data", JSONObject()
                            .put("device_brand", "unknown")
                            .put("device_model", "unknown")
                            .put("os", "windows")
                            .put("os_version", "NT 10.0")
                            .put("device_id", randomHex(32))
                            .put("device_type", "computer")
                    )
            ).toString()
            val rb = attempt("B", bodyB, UA, true)
            if (rb != null && rb.code == 200) r = rb
        }
        if (r != null && r.code == 400) {
            // El cuerpo de la respuesta dice qué campo rechaza Spotify
            lastNote = "Token de cliente: HTTP 400 · ${tried.joinToString(" ")} · client_id=${clientId.take(8)}… ${r.text.take(160)}".trim()
            return null
        }
        if (r == null || r.code != 200) {
            lastNote = "Token de cliente: ${if (r == null) "sin respuesta de red" else "HTTP ${r.code}"} · ${tried.joinToString(" ")}"
            return null
        }
        val token = try {
            JSONObject(r.text).optJSONObject("granted_token")?.optString("token")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
        if (token == null) {
            lastNote = "Token de cliente: Spotify no lo concedió"
            return null
        }
        clientTokenValue = token
        clientTokenExpMs = System.currentTimeMillis() + 60 * 60_000L
        return token
    }

    // ---------- Id de la canción a partir del enlace de YouTube (song.link) ----------

    /** Pide a song.link el id de Spotify de un video de YouTube (coincide la grabación exacta, sin buscar por texto). */
    fun trackIdFromYoutube(youtubeUrl: String): String? {
        if (songLinkDisabled) {
            lastNote = "song.link: desactivado (su API pública fue retirada)"
            return null
        }
        val r = send(
            Request.Builder()
                .url("https://api.song.link/v1-alpha.1/links?url=${enc(youtubeUrl)}&userCountry=US&songIfSingle=true")
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()
        )
        if (r == null) {
            lastNote = "song.link: sin respuesta de red"
            return null
        }
        if (r.code == 401 || r.code == 403) {
            songLinkDisabled = true
            lastNote = "song.link: HTTP ${r.code} (API pública retirada; se desactiva)"
            return null
        }
        if (r.code != 200) {
            lastNote = "song.link: HTTP ${r.code}"
            return null
        }
        val unique = try {
            JSONObject(r.text).optJSONObject("linksByPlatform")?.optJSONObject("spotify")?.optString("entityUniqueId")
        } catch (e: Exception) { null }
        val id = unique?.substringAfter("::", "")?.takeIf { it.isNotBlank() }
        if (id == null) lastNote = "song.link: no conoce esta canción en Spotify"
        else lastNote = "song.link: OK"
        return id
    }

    // ---------- Canvas ----------

    /** Hash de la consulta "canvas" del reproductor web (Spotify lo cambia a veces). */
    private const val CANVAS_HASH = "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"

    private fun findMp4(node: Any?): String? = when (node) {
        is String -> node.takeIf { it.startsWith("https://") && it.substringBefore('?').endsWith(".mp4") }
        is JSONObject -> node.keys().asSequence().firstNotNullOfOrNull { findMp4(node.opt(it)) }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findMp4(node.opt(it)) }
        else -> null
    }

    /** Respaldo: consulta GraphQL `canvas` del reproductor web (depende de un hash que Spotify cambia). */
    private fun canvasViaGraphql(token: String, client: String, trackId: String): String? {
        val body = JSONObject()
            .put("variables", JSONObject().put("trackUri", "spotify:track:$trackId"))
            .put("operationName", "canvas")
            .put(
                "extensions", JSONObject().put(
                    "persistedQuery", JSONObject().put("version", 1).put("sha256Hash", CANVAS_HASH)
                )
            ).toString()
        val r = send(
            Request.Builder()
                .url("https://api-partner.spotify.com/pathfinder/v2/query")
                .post(body.toRequestBody("application/json;charset=UTF-8".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("client-token", client)
                .header("App-Platform", "WebPlayer")
                .header("Accept", "application/json")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("User-Agent", UA)
                .build()
        )
        if (r == null) {
            lastNote = "Canvas: sin respuesta de red"
            return null
        }
        if (r.code == 401) throw SpotifyAuthException()
        if (r.code != 200) {
            lastNote = "Canvas: HTTP ${r.code}" + if (r.code == 429) " (límite de peticiones)" else ""
            return null
        }
        val json = try { JSONObject(r.text) } catch (e: Exception) { null }
        if (json == null) {
            lastNote = "Canvas: respuesta ilegible"
            return null
        }
        val error = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }
        val url = findMp4(json.opt("data"))
        lastNote = when {
            url != null -> "Canvas: OK"
            error != null -> "Canvas: Spotify respondió «$error» (puede haber cambiado el hash de la consulta)"
            else -> "Canvas: esta canción no tiene"
        }
        return url
    }
    // ---- Canvas por spclient (protobuf), como ArchiveTune: no depende de ningún hash ----

    private const val CANVAS_SPCLIENT_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val CANVAS_APP_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"

    /** [answered] = Spotify respondió bien (con o sin video); si es false se prueba el respaldo GraphQL. */
    private class CanvasRes(val url: String?, val answered: Boolean)

    private fun pbVarint(out: java.io.ByteArrayOutputStream, value: Int) {
        var v = value
        while (v >= 0x80) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    /** Petición protobuf: { campo 1: { campo 1: "spotify:track:ID" } }. */
    private fun canvasRequestBody(trackUri: String): ByteArray {
        val uri = trackUri.toByteArray(Charsets.UTF_8)
        val inner = java.io.ByteArrayOutputStream().apply { write(0x0A); pbVarint(this, uri.size); write(uri) }.toByteArray()
        return java.io.ByteArrayOutputStream().apply { write(0x0A); pbVarint(this, inner.size); write(inner) }.toByteArray()
    }

    private class PbReader(private val b: ByteArray) {
        private var p = 0
        fun atEnd() = p >= b.size
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (p >= b.size) throw IllegalStateException("fin inesperado")
                val x = b[p++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if ((x and 0x80) == 0) return result
                shift += 7
                if (shift > 63) throw IllegalStateException("varint demasiado largo")
            }
        }
        fun bytes(): ByteArray {
            val n = varint().toInt()
            if (n < 0 || p + n > b.size) throw IllegalStateException("longitud inválida")
            val r = b.copyOfRange(p, p + n)
            p += n
            return r
        }
        fun skip(wireType: Int) {
            when (wireType) {
                0 -> varint()
                1 -> p += 8
                2 -> bytes()
                5 -> p += 4
                else -> throw IllegalStateException("tipo de campo $wireType")
            }
        }
    }

    /** Respuesta protobuf: campo 1 repetido; en cada uno, campo 2 = url del video y campo 5 = uri de la pista. */
    private fun decodeCanvasUrls(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val top = PbReader(bytes)
        while (!top.atEnd()) {
            val tag = top.varint().toInt()
            if (tag == 10) {
                val canvas = PbReader(top.bytes())
                var uri: String? = null
                var url: String? = null
                while (!canvas.atEnd()) {
                    val field = canvas.varint().toInt()
                    when (field) {
                        18 -> url = String(canvas.bytes(), Charsets.UTF_8)
                        42 -> uri = String(canvas.bytes(), Charsets.UTF_8)
                        else -> canvas.skip(field and 7)
                    }
                }
                if (uri != null && url != null && url.startsWith("https://") && url.substringBefore('?').endsWith(".mp4")) {
                    result[uri] = url
                }
            } else {
                top.skip(tag and 7)
            }
        }
        return result
    }

    private fun canvasViaSpclient(token: String, client: String, trackId: String): CanvasRes {
        val trackUri = "spotify:track:$trackId"
        val r = send(
            Request.Builder().url(CANVAS_SPCLIENT_URL)
                .post(canvasRequestBody(trackUri).toRequestBody("application/protobuf".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Client-Token", client)
                .header("User-Agent", CANVAS_APP_UA)
                .header("Accept", "application/protobuf")
                .header("Accept-Language", "en")
                .build()
        )
        if (r == null) {
            lastNote = "Canvas (spclient): sin respuesta de red"
            return CanvasRes(null, false)
        }
        if (r.code == 401) throw SpotifyAuthException()
        if (r.code != 200) {
            lastNote = "Canvas (spclient): HTTP ${r.code}" + if (r.code == 429) " (límite de peticiones)" else ""
            return CanvasRes(null, false)
        }
        val url = try {
            decodeCanvasUrls(r.bytes)[trackUri]
        } catch (e: Exception) {
            lastNote = "Canvas (spclient): respuesta ilegible (${e.message})"
            return CanvasRes(null, false)
        }
        lastNote = if (url != null) "Canvas: OK" else "Canvas: esta canción no tiene"
        return CanvasRes(url, true)
    }

    /**
     * URL del video (.mp4) del Canvas de la pista, o null si no tiene. Primero por spclient (protobuf, como ArchiveTune);
     * si falla, por la consulta GraphQL del reproductor web.
     */
    fun canvasUrl(token: String, trackId: String): String? {
        val client = clientToken() ?: return null // el motivo queda en lastNote ("Token de cliente: ...")
        val proto = canvasViaSpclient(token, client, trackId)
        if (proto.answered) return proto.url
        val protoNote = lastNote
        val url = canvasViaGraphql(token, client, trackId)
        lastNote = "$protoNote · $lastNote"
        return url
    }
}

/** Une [SpotifyApi] con la sesión guardada: gestiona el token, lo renueva si caduca y no lanza excepciones. */
class SpotifyService(context: Context) {
    private val store = SpotifyStore.get(context)
    private val trackIds = HashMap<String, String?>()
    /** Canciones sin resultado: no se vuelven a pedir durante [FAIL_TTL_MS] (evita repetir peticiones). */
    private val failedAt = HashMap<String, Long>()
    private companion object { const val FAIL_TTL_MS = 10 * 60_000L }

    private fun token(forceRefresh: Boolean): String? {
        if (!forceRefresh) store.cachedToken()?.let { return it.first }
        val spDc = store.spDc() ?: return null
        val r = SpotifyApi.fetchToken(spDc)
        val token = r.token
        if (token == null) {
            store.clearToken()
            store.setLastResult(r.error)
            return null
        }
        store.saveToken(token, r.expiresAtMs)
        return token
    }

    /** Ejecuta [block] con token; si Spotify responde 401 renueva el token y reintenta una vez. */
    private fun <T> withToken(block: (String) -> T?): T? {
        for (attempt in 0..1) {
            val t = token(forceRefresh = attempt == 1) ?: return null
            try {
                return block(t)
            } catch (e: SpotifyAuthException) {
                store.clearToken()
            }
        }
        return null
    }

    // @Synchronized: letra y Canvas piden el id a la vez; la 2.ª llamada espera y encuentra el resultado en caché.
    @Synchronized
    private fun trackId(song: SongUi): String? {
        if (trackIds.containsKey(song.url)) return trackIds[song.url]
        val failed = failedAt[song.url]
        if (failed != null && System.currentTimeMillis() - failed < FAIL_TTL_MS) return null
        val pause = SpotifyApi.searchCooldownSec()
        if (pause > 0) {
            store.setLastResult("Búsqueda: en pausa ${pause}s por el límite de Spotify (429)")
            return null
        }
        // 1) song.link da la grabación exacta a partir del enlace de YouTube; 2) búsqueda de Spotify por texto
        var id: String? = SpotifyApi.trackIdFromYoutube(song.url)
        val linkNote = SpotifyApi.lastNote
        if (id == null) {
            id = withToken {
                SpotifyApi.findTrackId(it, cleanTitle(song.title, cleanArtist(song.artist)), cleanArtist(song.artist), song.durationSec)
            }
        }
        if (id != null) trackIds[song.url] = id
        else {
            // Solo se cachea el fallo "sin coincidencia"; con 429, red o sesión caída se reintenta más tarde.
            val note = SpotifyApi.lastNote.orEmpty()
            if (note.contains("ninguno coincide") || note.contains("no devolvió canciones")) {
                failedAt[song.url] = System.currentTimeMillis()
            }
            store.setLastResult("$linkNote · ${SpotifyApi.lastNote ?: "búsqueda sin resultado"}")
        }
        return id
    }

    /** Por qué no salió letra en la última búsqueda (null si salió). La lee MainViewModel para el aviso y los Ajustes. */
    @Volatile
    var lastLyricsReason: String? = null
        private set

    suspend fun lyrics(song: SongUi): LyricsState? = withContext(Dispatchers.IO) {
        lastLyricsReason = null
        try {
            SpotifyApi.lastNote = null
            val id = trackId(song)
            if (id == null) {
                lastLyricsReason = SpotifyApi.lastNote ?: str(R.string.lyr_reason_spotify_track)
                return@withContext null
            }
            val result = withToken { SpotifyApi.lyrics(it, id) }
            store.setLastResult(SpotifyApi.lastNote ?: "Letra: sin resultado")
            lastLyricsReason = when {
                result == null -> SpotifyApi.lastNote ?: str(R.string.lyr_reason_spotify_session)
                result is LyricsState.NotFound -> str(R.string.lyr_reason_no_lyrics)
                else -> null
            }
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            lastLyricsReason = e.message?.takeIf { it.isNotBlank() } ?: str(R.string.lyr_reason_unknown)
            null
        }
    }

    suspend fun canvas(song: SongUi): String? = withContext(Dispatchers.IO) {
        try {
            val id = trackId(song) ?: return@withContext null
            val url = withToken { SpotifyApi.canvasUrl(it, id) }
            store.setLastResult(SpotifyApi.lastNote ?: "Canvas: sin resultado")
            url
        } catch (e: Exception) {
            null
        }
    }

    /** Comprueba la sesión: pide token y lee el nombre de usuario (se guarda). Devuelve un resumen legible. */
    suspend fun verifySession(): String = withContext(Dispatchers.IO) {
        val spDc = store.spDc() ?: return@withContext "No hay sesión de Spotify"
        val tr = SpotifyApi.fetchToken(spDc)
        val token = tr.token
        if (token == null) {
            store.clearToken()
            store.setUserName(null)
            return@withContext "Token: ${tr.error}"
        }
        store.saveToken(token, tr.expiresAtMs)
        try {
            SpotifyApi.lastNote = null
            val name = SpotifyApi.fetchProfileName(token)
            store.setUserName(name)
            if (name != null) "Sesión iniciada como $name"
            else "Token OK, pero no se pudo leer el usuario (${SpotifyApi.lastNote ?: "sin detalle"})"
        } catch (e: SpotifyAuthException) {
            store.setUserName(null)
            "Spotify rechazó el token (401) al leer el usuario"
        }
    }

    /** Prueba de punta a punta con una canción conocida; devuelve un resumen paso a paso. */
    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        val spDc = store.spDc() ?: return@withContext "No hay sesión de Spotify"
        val sb = StringBuilder()
        val tr = SpotifyApi.fetchToken(spDc)
        val token = tr.token
        if (token == null) {
            store.clearToken()
            return@withContext "Token: ${tr.error}"
        }
        store.saveToken(token, tr.expiresAtMs)
        sb.appendLine("Token: OK")
        try {
            SpotifyApi.lastNote = null
            val name = SpotifyApi.fetchProfileName(token)
            store.setUserName(name)
            sb.appendLine(
                if (name != null) "Usuario: $name (sesión iniciada)"
                else "Usuario: no se pudo leer (${SpotifyApi.lastNote ?: "sin detalle"})"
            )
        } catch (e: SpotifyAuthException) {
            sb.appendLine("Usuario: Spotify rechazó el token (401)")
        }
        try {
            SpotifyApi.lastNote = null
            var id = SpotifyApi.trackIdFromYoutube("https://www.youtube.com/watch?v=4NRXx6U8ABQ")
            sb.appendLine(SpotifyApi.lastNote ?: "song.link: sin resultado")
            if (id == null) {
                id = SpotifyApi.findTrackId(token, "Blinding Lights", "The Weeknd", 200)
                sb.appendLine(SpotifyApi.lastNote ?: "Búsqueda: sin resultado")
            }
            if (id == null) return@withContext sb.toString().trim()
            SpotifyApi.lyrics(token, id)
            sb.appendLine(SpotifyApi.lastNote ?: "Letra: sin resultado")
            SpotifyApi.canvasUrl(token, id)
            sb.append(SpotifyApi.lastNote ?: "Canvas: sin resultado")
        } catch (e: SpotifyAuthException) {
            store.clearToken()
            sb.append("Spotify rechazó el token (401)")
        } catch (e: Exception) {
            sb.append("Error: ${e.message}")
        }
        sb.toString()
    }
}
