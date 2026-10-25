package com.lumodsound.app.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Enlace "ver todo" de un carrusel: se pide con browseId + params. */
class MoreRef(val browseId: String, val params: String?)

/** Lo que trae la página real del artista en YouTube Music. */
class ParsedArtist(
    val subscribers: Long,
    val description: String?,
    /** Las ~5 canciones top, en el orden real de YouTube Music. */
    val topSongs: List<SongUi>,
    /** Lista completa de canciones del artista (ordenada por popularidad), si existe. */
    val songsPlaylistUrl: String?,
    val albums: List<AlbumUi>,
    val albumsMore: MoreRef?,
    val singles: List<AlbumUi>,
    val singlesMore: MoreRef?
)

/**
 * Cliente mínimo de la API interna de YouTube Music (youtubei/v1/browse, cliente WEB_REMIX).
 * Se pide siempre en inglés (hl=en) para que los números ("45.3M") y los títulos de sección
 * ("Albums", "Singles & EPs") tengan un formato predecible; la UI sigue en español.
 */
object YtMusicApi {

    /** Álbum/lista del Inicio que NewPipe no puede abrir por URL: se cargan con la API de YouTube Music. */
    private const val TAG = "YTHome"

    const val YTM_ALBUM = "ytm-album:"
    const val YTM_PLAYLIST = "ytm-playlist:"

    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val ANDROID_UA = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
    private const val ANDROID_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"

    private fun baseBody(hl: String = "en", gl: String = "US", android: Boolean = false): JSONObject {
        val clientCtx = JSONObject()
        if (android) {
            clientCtx.put("clientName", "ANDROID_MUSIC")
                .put("clientVersion", "7.27.52")
                .put("androidSdkVersion", 30)
                .put("osName", "Android")
                .put("osVersion", "11")
        } else {
            clientCtx.put("clientName", "WEB_REMIX")
                .put("clientVersion", "1.20250310.01.00")
        }
        clientCtx.put("hl", hl).put("gl", gl)
        return JSONObject().put("context", JSONObject().put("client", clientCtx))
    }

    /** Cookies de la sesión de Google (null = sin iniciar sesión). Las pone el ViewModel. */
    @Volatile
    var cookie: String? = null

    private class HttpException(val code: Int) : IOException("YT Music HTTP $code")

    /** Cabecera Authorization que pide YouTube Music con una sesión: SAPISIDHASH <hora>_<sha1(hora SAPISID origen)>. */
    private fun authHeader(cookie: String, origin: String = "https://music.youtube.com"): String? {
        val values = cookie.split(";").mapNotNull { part ->
            val t = part.trim()
            val i = t.indexOf('=')
            if (i > 0) t.substring(0, i) to t.substring(i + 1) else null
        }.toMap()
        val sapisid = values["SAPISID"] ?: values["__Secure-3PAPISID"] ?: return null
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$ts $sapisid $origin".toByteArray())
        return "SAPISIDHASH ${ts}_" + digest.joinToString("") { "%02x".format(it) }
    }

    /** Bloqueante: llamar desde Dispatchers.IO. Con sesión iniciada va autenticada; si YouTube la rechaza, reintenta sin ella. */
    private fun post(path: String, body: JSONObject, query: String = "", android: Boolean = false): JSONObject {
        val ck = cookie
        if (!android && ck != null) {
            try {
                return postOnce(path, body, query, false, ck)
            } catch (e: HttpException) {
                if (e.code != 401 && e.code != 403) throw e
                Log.w(TAG, "Sesión rechazada (HTTP ${e.code}); se reintenta sin sesión")
            }
        }
        return postOnce(path, body, query, android, null)
    }

    private fun postOnce(path: String, body: JSONObject, query: String, android: Boolean, ck: String?): JSONObject {
        val key = if (android) "&key=$ANDROID_KEY" else ""
        val builder = Request.Builder()
            .url("$BASE$path?prettyPrint=false$key$query")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (android) {
            builder.header("User-Agent", ANDROID_UA)
        } else {
            builder.header("User-Agent", UA)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
            if (ck != null) {
                builder.header("Cookie", ck)
                    .header("X-Goog-AuthUser", "0")
                    .header("X-Origin", "https://music.youtube.com")
                authHeader(ck)?.let { builder.header("Authorization", it) }
            } else {
                builder.header("Cookie", "SOCS=CAI")
            }
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            val text = resp.body?.string() ?: throw IOException(str(R.string.empty_response))
            return JSONObject(text)
        }
    }

    /** URLs con las que YouTube anota una reproducción (las trae la respuesta de "player"). */
    class PlaybackTracking(val playbackUrl: String?, val watchtimeUrl: String?, val client: String = "")

    /** Resultado de buscar las URLs: [tracking] si hubo, y en [log] qué pasó con cada cliente probado. */
    class TrackingResult(val tracking: PlaybackTracking?, val log: String)

    private const val VR_VERSION = "1.60.19"
    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/$VR_VERSION (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private fun playerBody(
        client: JSONObject,
        videoId: String,
        sts: Int? = null,
        embedUrl: String? = null
    ): JSONObject {
        val context = JSONObject().put("client", client)
        if (embedUrl != null) context.put("thirdParty", JSONObject().put("embedUrl", embedUrl))
        val playback = JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
        if (sts != null) playback.put("signatureTimestamp", sts)
        return JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", playback))
    }

    /**
     * Datos que YouTube espera en un "player" de web: versión real del cliente, visitorData (identifica la
     * visita; sin él responde "bot" o "Video unavailable") y signatureTimestamp. Se leen de la página de
     * YouTube Music con la sesión y se guardan unas horas.
     */
    private class WebSession(
        val cookieKey: Int,
        val clientVersion: String,
        val visitorData: String?,
        val sts: Int?,
        val fetchedAt: Long,
        val error: String? = null
    ) {
        fun summary() = "visitor=${if (visitorData != null) "sí" else "no"}, sts=${sts ?: "no"}" +
            (error?.let { ", fallo=$it" } ?: "")
    }

    @Volatile
    private var webSession: WebSession? = null
    private const val SESSION_TTL_MS = 6 * 60 * 60 * 1000L
    private const val FALLBACK_WEB_VERSION = "1.20250310.01.00"

    private fun httpGetText(url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            return resp.body?.string() ?: throw IOException(str(R.string.empty_response))
        }
    }

    private fun firstGroup(text: String, vararg patterns: String): String? {
        for (p in patterns) {
            val v = Regex(p).find(text)?.groupValues?.getOrNull(1)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun loadWebSession(ck: String): WebSession {
        val key = ck.hashCode()
        val cached = webSession
        if (cached != null && cached.cookieKey == key &&
            System.currentTimeMillis() - cached.fetchedAt < SESSION_TTL_MS
        ) return cached

        var version = FALLBACK_WEB_VERSION
        var visitor: String? = null
        var sts: Int? = null
        val errors = StringBuilder()
        fun fail(what: String, e: Exception) {
            val code = (e as? HttpException)?.code?.let { "HTTP $it" } ?: e.javaClass.simpleName
            if (errors.isNotEmpty()) errors.append(" / ")
            errors.append(what).append(' ').append(code)
            Log.w(TAG, "$what: $code")
        }
        try {
            val html = httpGetText(
                "https://music.youtube.com/",
                mapOf("User-Agent" to UA, "Cookie" to ck, "Accept-Language" to "en-US,en;q=0.9")
            )
            firstGroup(
                html,
                "\"INNERTUBE_CONTEXT_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"",
                "\"clientVersion\"\\s*:\\s*\"(1\\.[^\"]+)\""
            )?.let { version = it }
            visitor = firstGroup(
                html,
                "\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"",
                "\"visitorData\"\\s*:\\s*\"([^\"]+)\""
            )?.replace("\\u003d", "=")
            sts = firstGroup(html, "\"STS\"\\s*:\\s*(\\d+)")?.toIntOrNull()
            if (sts == null) {
                val jsPath = firstGroup(
                    html,
                    "\"PLAYER_JS_URL\"\\s*:\\s*\"([^\"]+)\"",
                    "\"jsUrl\"\\s*:\\s*\"([^\"]+)\""
                )?.replace("\\/", "/")
                if (jsPath != null) {
                    val jsUrl = if (jsPath.startsWith("http")) jsPath else "https://music.youtube.com$jsPath"
                    val js = httpGetText(jsUrl, mapOf("User-Agent" to UA))
                    sts = firstGroup(js, "signatureTimestamp[:=](\\d+)", "\\bsts\\s*:\\s*(\\d{5})")?.toIntOrNull()
                }
            }
        } catch (e: Exception) {
            fail("página", e)
        }

        // Respaldo 1: toda respuesta de youtubei trae responseContext.visitorData (no depende del HTML).
        if (visitor == null) {
            try {
                val r = postOnce("browse", baseBody().put("browseId", "FEmusic_home"), "", false, ck)
                visitor = r.optJSONObject("responseContext")
                    ?.optString("visitorData")?.takeIf { it.isNotBlank() }
                if (visitor == null) errors.append(if (errors.isEmpty()) "" else " / ").append("browse sin visitorData")
            } catch (e: Exception) {
                fail("browse", e)
            }
        }

        // Respaldo 2: signatureTimestamp desde el base.js del reproductor (como yt-dlp / NewPipe).
        if (sts == null) {
            try {
                val ia = httpGetText("https://www.youtube.com/iframe_api", mapOf("User-Agent" to UA))
                val hash = firstGroup(ia, "player\\\\?/([0-9a-fA-F]{8})\\\\?/")
                if (hash != null) {
                    val js = httpGetText(
                        "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_US/base.js",
                        mapOf("User-Agent" to UA)
                    )
                    sts = firstGroup(js, "signatureTimestamp[:=](\\d+)")?.toIntOrNull()
                }
                if (sts == null) errors.append(if (errors.isEmpty()) "" else " / ").append("sts no encontrado")
            } catch (e: Exception) {
                fail("sts", e)
            }
        }
        return WebSession(
            key, version, visitor, sts, System.currentTimeMillis(),
            errors.toString().takeIf { it.isNotEmpty() }
        ).also {
            // Solo se guarda si se consiguió lo importante; si no, se vuelve a intentar en el siguiente envío.
            if (it.visitorData != null) webSession = it
        }
    }

    /** Petición "player" a una dirección y cabeceras concretas (cada cliente de YouTube pide lo suyo). */
    private fun playerRequest(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            return JSONObject(resp.body?.string() ?: throw IOException(str(R.string.empty_response)))
        }
    }

    /** Extrae las URLs de seguimiento; [why] recibe el motivo si no las hay (para diagnosticar). */
    private fun parseTracking(root: JSONObject, why: StringBuilder): PlaybackTracking? {
        val t = root.optJSONObject("playbackTracking")
        if (t == null) {
            val status = root.optJSONObject("playabilityStatus")
            why.append("sin playbackTracking (${status?.optString("status") ?: "sin estado"}")
            status?.optString("reason")?.takeIf { it.isNotBlank() }?.let { why.append(": ").append(it.take(60)) }
            why.append(")")
            return null
        }
        fun url(key: String) = t.optJSONObject(key)?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val result = PlaybackTracking(url("videostatsPlaybackUrl"), url("videostatsWatchtimeUrl"))
        if (result.playbackUrl == null && result.watchtimeUrl == null) why.append("playbackTracking vacío")
        return result
    }

    /**
     * Pide a YouTube las URLs de seguimiento de un video. Solo con sesión iniciada. YouTube rechaza "player" si la
     * petición no parece un cliente real (sin visitorData / signatureTimestamp responde "bot" o "Video unavailable"),
     * así que primero se leen esos datos de la página de YouTube Music y se mandan en todos los intentos:
     * web con sesión, Android VR y Android Music (el cliente TV embebido ya no lo admite YouTube). La URL sirve igual venga de donde venga:
     * la sesión se envía al avisar (como yt-dlp).
     */
    fun playbackTracking(videoId: String): TrackingResult {
        val ck = cookie ?: return TrackingResult(null, "sin sesión")
        val log = StringBuilder()
        val web = loadWebSession(ck)
        log.append("datos: ").append(web.summary()).append("; ")
        val visitor = web.visitorData

        fun attempt(name: String, block: (StringBuilder) -> PlaybackTracking?): PlaybackTracking? {
            val why = StringBuilder()
            val found = try {
                block(why)
            } catch (e: HttpException) {
                why.append("HTTP ${e.code}")
                null
            } catch (e: Exception) {
                why.append(e.javaClass.simpleName)
                null
            }
            val ok = found != null && (found.playbackUrl != null || found.watchtimeUrl != null)
            if (!log.endsWith("; ")) log.append("; ")
            log.append(name).append('=').append(if (ok) "ok" else why.toString())
            return if (ok) PlaybackTracking(found!!.playbackUrl, found.watchtimeUrl, name) else null
        }

        fun JSONObject.withVisitor(): JSONObject = apply { visitor?.let { put("visitorData", it) } }
        fun MutableMap<String, String>.withVisitor(): MutableMap<String, String> =
            apply { visitor?.let { put("X-Goog-Visitor-Id", it) } }

        // 1) Web (música) con la sesión
        attempt("web") { why ->
            val ctx = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", web.clientVersion)
                .put("hl", "en").put("gl", "US").withVisitor()
            val headers = mutableMapOf(
                "User-Agent" to UA,
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "Cookie" to ck,
                "X-Goog-AuthUser" to "0",
                "X-Origin" to "https://music.youtube.com",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to web.clientVersion
            ).withVisitor()
            authHeader(ck)?.let { headers["Authorization"] = it }
            parseTracking(
                playerRequest("${BASE}player?prettyPrint=false", playerBody(ctx, videoId, web.sts), headers),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        // 2) Android VR (www.youtube.com)
        attempt("vr") { why ->
            val ctx = JSONObject().put("clientName", "ANDROID_VR").put("clientVersion", VR_VERSION)
                .put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
                .put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12L")
                .put("hl", "en").put("gl", "US").withVisitor()
            val headers = mutableMapOf(
                "User-Agent" to VR_UA,
                "X-YouTube-Client-Name" to "28",
                "X-YouTube-Client-Version" to VR_VERSION
            ).withVisitor()
            parseTracking(
                playerRequest("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", playerBody(ctx, videoId), headers),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        // 3) Android Music
        attempt("android") { why ->
            val ctx = JSONObject().put("clientName", "ANDROID_MUSIC").put("clientVersion", "7.27.52")
                .put("androidSdkVersion", 30).put("osName", "Android").put("osVersion", "11")
                .put("hl", "en").put("gl", "US").withVisitor()
            parseTracking(
                playerRequest(
                    "${BASE}player?prettyPrint=false&key=$ANDROID_KEY",
                    playerBody(ctx, videoId),
                    mutableMapOf("User-Agent" to ANDROID_UA).withVisitor()
                ),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        return TrackingResult(null, log.toString())
    }

    /** Valor del parámetro "c" que YouTube espera según el cliente que dio la URL. */
    private fun clientParam(source: String): String? = when (source) {
        "web" -> "WEB_REMIX"
        "vr" -> "ANDROID_VR"
        "android" -> "ANDROID_MUSIC"
        else -> null
    }

    /**
     * GET con la sesión a una URL de seguimiento, añadiendo [params]. Va con las mismas cabeceras de sesión que usa
     * la web (Authorization, X-Goog-AuthUser, Origin, visitorData) para que YouTube lo asocie a la cuenta, y con "c"
     * = cliente si la URL no lo trae. Devuelve el código HTTP (o -1 si no se pudo).
     */
    fun ping(baseUrl: String, params: Map<String, String>, source: String = ""): Int {
        val ck = cookie ?: return -1
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder() ?: return -1
        params.forEach { (k, v) -> builder.setQueryParameter(k, v) }
        val url = builder.build()
        if (url.queryParameter("c") == null) {
            clientParam(source)?.let { builder.setQueryParameter("c", it) }
        }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("User-Agent", UA)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("Cookie", ck)
            .header("X-Goog-AuthUser", "0")
        authHeader(ck)?.let { request.header("Authorization", it) }
        webSession?.visitorData?.let { request.header("X-Goog-Visitor-Id", it) }
        return client.newCall(request.build()).execute().use { it.code }
    }

    fun browse(browseId: String, params: String? = null): JSONObject {
        val body = baseBody().put("browseId", browseId)
        if (params != null) body.put("params", params)
        return post("browse", body)
    }

    /** Cola de reproducción de un video; su primer elemento trae artista(s) y álbum de la canción. */
    fun next(videoId: String): JSONObject =
        post("next", baseBody().put("videoId", videoId).put("isAudioOnly", true))

    // ---------- Álbum de una canción ----------

    class AlbumRef(val browseId: String, val name: String)

    /** Álbum (MPREb...) al que pertenece la canción, si la cola lo indica. */
    fun parseAlbumRef(nextRoot: JSONObject): AlbumRef? {
        val item = findFirst(nextRoot, "playlistPanelVideoRenderer") ?: return null
        val runs = item.obj("longBylineText")?.optJSONArray("runs") ?: return null
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val e = run.obj("navigationEndpoint", "browseEndpoint") ?: continue
            val id = e.optString("browseId")
            val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            val name = run.optString("text")
            if ((type == "MUSIC_PAGE_TYPE_ALBUM" || id.startsWith("MPREb")) && name.isNotBlank()) {
                return AlbumRef(id, name)
            }
        }
        return null
    }

    class SongMeta(val albumRef: AlbumRef?, val year: String?, val durationSec: Long)

    /** Álbum, año y duración de una canción a partir de la respuesta de "next". */
    fun parseSongMeta(nextRoot: JSONObject): SongMeta {
        val item = findFirst(nextRoot, "playlistPanelVideoRenderer")
        var year: String? = null
        val runs = item?.obj("longBylineText")?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
                if (Regex("""^(19|20)\d{2}$""").matches(t)) year = t
            }
        }
        return SongMeta(
            albumRef = parseAlbumRef(nextRoot),
            year = year,
            durationSec = parseDuration(item?.obj("lengthText").runsText())
        )
    }

    /** Lista de reproducción (OLAK5uy_...) de la página de un álbum; es la que entiende NewPipe. */
    fun parseAlbumPlaylistId(albumRoot: JSONObject): String? {
        val eps = ArrayList<JSONObject>()
        collect(albumRoot, "watchEndpoint", eps)
        collect(albumRoot, "watchPlaylistEndpoint", eps)
        eps.map { it.optString("playlistId") }.firstOrNull { it.startsWith("OLAK5uy_") }?.let { return it }
        return Regex("""OLAK5uy_[A-Za-z0-9_-]+""").find(albumRoot.toString())?.value
    }

    // ---------- Inicio (FEmusic_home) ----------

    /** Un estante del Inicio de YouTube Music: canciones y/o álbumes y listas. */
    class FeedShelf(
        val title: String,
        val songs: List<SongUi>,
        val albums: List<AlbumUi>,
        /** true si YouTube lo manda como filas de canciones (Selecciones rápidas): se muestra en columnas de 3. */
        val compact: Boolean = false,
        /** Nombre de la cuenta que YouTube pone sobre el título en estantes personalizados (p. ej. "Accesos directos"). */
        val strapline: String? = null,
        /** true si es el estante "Accesos directos" (Speed dial): se muestra en páginas de 3 x 3 portadas. */
        val speedDial: Boolean = false
    )

    /** Categoría de "Estados de ánimo y géneros" de YouTube Music (Los 70, Pop latino, Hip hop...). */
    class ExploreCategory(val title: String, val browseId: String, val params: String, val color: Long?)

    /** Todas las categorías de "Explorar" (estados de ánimo y géneros), con el color que les da YouTube. */
    fun exploreCategories(hl: String, gl: String): List<ExploreCategory> {
        val root = post("browse", baseBody(hl, gl, false).put("browseId", "FEmusic_moods_and_genres"))
        val buttons = ArrayList<JSONObject>()
        collect(root, "musicNavigationButtonRenderer", buttons)
        return buttons.mapNotNull { b ->
            val e = b.obj("clickCommand", "browseEndpoint") ?: return@mapNotNull null
            val params = e.optString("params")
            val title = b.obj("buttonText").runsText().trim()
            if (params.isBlank() || title.isBlank()) return@mapNotNull null
            val color = b.optJSONObject("solid")?.optLong("leftStripeColor", 0L)?.takeIf { it != 0L }
            ExploreCategory(title, e.optString("browseId").ifBlank { "FEmusic_moods_and_genres_category" }, params, color)
        }.distinctBy { it.params }
    }

    /** Listas y estantes de una categoría de Explorar. */
    fun categoryFeed(cat: ExploreCategory, hl: String, gl: String): List<FeedShelf> {
        val root = post(
            "browse",
            baseBody(hl, gl, false).put("browseId", cat.browseId).put("params", cat.params)
        )
        return parseFeed(root, includeGrids = true).distinctBy { it.title }
    }

    /** Chip de la parte de arriba del Inicio de YouTube Music (Relajar, Energizar, Entrenar...). */
    class FeedChip(val label: String, val browseId: String, val params: String)

    private fun parseChips(root: JSONObject): List<FeedChip> {
        val found = ArrayList<JSONObject>()
        collect(root, "chipCloudChipRenderer", found)
        return found.mapNotNull { chip ->
            val e = chip.obj("navigationEndpoint", "browseEndpoint") ?: return@mapNotNull null
            val params = e.optString("params")
            val label = chip.obj("text").runsText().trim()
            if (params.isBlank() || label.isBlank()) return@mapNotNull null
            FeedChip(label, e.optString("browseId").ifBlank { "FEmusic_home" }, params)
        }.distinctBy { it.params }
    }

    /** Estantes que YouTube Music muestra al tocar un chip del Inicio. */
    fun chipFeed(chip: FeedChip, hl: String, gl: String, android: Boolean = false): List<FeedShelf> {
        val root = post(
            "browse",
            baseBody(hl, gl, android).put("browseId", chip.browseId).put("params", chip.params),
            android = android
        )
        return parseFeed(root).distinctBy { it.title }
    }

    /**
     * Inicio de YouTube Music (FEmusic_home): los estantes que arma el algoritmo de YouTube.
     * Sin cuenta iniciada no es personal: depende de la región ([gl]) y del idioma ([hl]).
     * [android] usa el cliente ANDROID_MUSIC en lugar de WEB_REMIX.
     */
    fun home(
        hl: String,
        gl: String,
        android: Boolean = false,
        /** Se llama con todos los estantes acumulados cada vez que llega una página nueva. */
        onProgress: (List<FeedShelf>) -> Unit = {},
        /** Se llama una vez con los chips del Inicio (Relajar, Energizar...), si YouTube los manda. */
        onChips: (List<FeedChip>) -> Unit = {}
    ): List<FeedShelf> {
        val out = ArrayList<FeedShelf>()
        val first = post("browse", baseBody(hl, gl, android).put("browseId", "FEmusic_home"), android = android)
        parseChips(first).takeIf { it.isNotEmpty() }?.let(onChips)
        out += parseFeed(first)
        if (out.isNotEmpty()) onProgress(out.distinctBy { it.title })
        Log.d(TAG, "página 1 (android=$android): ${out.size} estantes, token=${continuationToken(first)?.length}")

        // Siguientes páginas del Inicio
        var token = continuationToken(first)
        var pages = 1
        while (!token.isNullOrBlank() && pages < 6 && out.size < 20) {
            val next = fetchContinuation(token, hl, gl, android) ?: break
            val shelves = parseFeed(next)
            out += shelves
            onProgress(out.distinctBy { it.title })
            token = continuationToken(next)
            pages++
            Log.d(TAG, "página $pages: +${shelves.size} estantes, token=${token?.length}")
        }

        // Si aun así hay pocos, los estantes de cada chip de ánimo (Energizar, Relajar...) que YouTube ofrece
        if (out.size < 8) {
            val chips = ArrayList<JSONObject>()
            collect(first, "chipCloudChipRenderer", chips)
            Log.d(TAG, "chips encontrados: ${chips.size}")
            var used = 0
            for (chip in chips) {
                if (used >= 4) break
                val e = chip.obj("navigationEndpoint", "browseEndpoint") ?: continue
                val params = e.optString("params")
                if (params.isBlank()) continue
                val id = e.optString("browseId").ifBlank { "FEmusic_home" }
                try {
                    val root = post(
                        "browse",
                        baseBody(hl, gl, android).put("browseId", id).put("params", params),
                        android = android
                    )
                    val shelves = parseFeed(root)
                    Log.d(TAG, "chip '${chip.obj("text").runsText()}': ${shelves.size} estantes")
                    out += shelves
                    onProgress(out.distinctBy { it.title })
                    used++
                } catch (ex: IOException) {
                    Log.w(TAG, "chip falló: ${ex.message}")
                }
            }
        }
        return out.distinctBy { it.title }
    }

    private fun continuationToken(root: JSONObject): String? =
        findFirst(root, "nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
            ?: findFirst(root, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }

    /**
     * Pide la siguiente página probando las formas que usan YouTube Music y ytmusicapi.
     * El token ya viene codificado por YouTube (termina en %3D...), no se vuelve a codificar.
     */
    private fun fetchContinuation(token: String, hl: String, gl: String, android: Boolean): JSONObject? {
        val attempts: List<() -> JSONObject> = listOf(
            {
                post(
                    "browse", baseBody(hl, gl, android).put("browseId", "FEmusic_home"),
                    query = "&ctoken=$token&continuation=$token", android = android
                )
            },
            {
                post(
                    "browse", baseBody(hl, gl, android),
                    query = "&ctoken=$token&continuation=$token&type=next", android = android
                )
            },
            { post("browse", baseBody(hl, gl, android).put("continuation", token), android = android) }
        )
        for ((i, attempt) in attempts.withIndex()) {
            try {
                val root = attempt()
                if (parseFeed(root).isNotEmpty()) return root
                Log.w(TAG, "continuación intento ${i + 1}: respuesta sin estantes")
            } catch (e: IOException) {
                Log.w(TAG, "continuación intento ${i + 1} falló: ${e.message}")
            }
        }
        return null
    }

    private fun parseFeed(root: JSONObject, includeGrids: Boolean = false): List<FeedShelf> {
        val carousels = ArrayList<JSONObject>()
        collect(root, "musicCarouselShelfRenderer", carousels)
        val blocks = ArrayList<Triple<String, org.json.JSONArray, String>>()
        for (car in carousels) {
            val title = car.obj("header", "musicCarouselShelfBasicHeaderRenderer", "title").runsText()
            if (title.isBlank()) continue
            val contents = car.optJSONArray("contents") ?: continue
            val strap = car.obj("header", "musicCarouselShelfBasicHeaderRenderer", "strapline").runsText().trim()
            blocks.add(Triple(title, contents, strap))
        }
        if (includeGrids) {
            // Las páginas de categoría (Estados de ánimo y géneros) también traen listas en cuadrículas
            val grids = ArrayList<JSONObject>()
            collect(root, "gridRenderer", grids)
            for (g in grids) {
                val title = g.obj("header", "gridHeaderRenderer", "title").runsText().ifBlank { "Listas" }
                val items = g.optJSONArray("items") ?: continue
                blocks.add(Triple(title, items, ""))
            }
        }
        val shelves = ArrayList<FeedShelf>()
        for ((title, contents, strap) in blocks) {

            val songs = ArrayList<SongUi>()
            val albums = ArrayList<AlbumUi>()
            var fromRows = false
            for (i in 0 until contents.length()) {
                val c = contents.optJSONObject(i) ?: continue
                c.optJSONObject("musicResponsiveListItemRenderer")?.let { row ->
                    val song = parseFeedSongRow(row)
                    if (song != null) {
                        songs.add(song)
                        fromRows = true
                    }
                }
                c.optJSONObject("musicTwoRowItemRenderer")?.let { t ->
                    val song = parseFeedTwoRowSong(t)
                    if (song != null) {
                        songs.add(song)
                    } else {
                        val album = parseFeedAlbum(t)
                        if (album != null) albums.add(album)
                    }
                }
            }
            val s = songs.distinctBy { it.url }
            val a = albums.distinctBy { it.url }
            val low = title.lowercase()
            val dial = low.contains("accesos directos") || low.contains("speed dial")
            if (s.size + a.size >= 2) {
                shelves.add(
                    FeedShelf(
                        title, s, a,
                        compact = fromRows && a.isEmpty(),
                        strapline = strap.ifBlank { null },
                        speedDial = dial
                    )
                )
            }
        }
        Log.d(TAG, "estantes del feed: " + shelves.joinToString(" | ") { it.title + if (it.speedDial) " [accesos directos]" else "" })
        return shelves
    }

    private val TYPE_WORDS = setOf(
        "song", "video", "canción", "cancion", "vídeo", "single", "sencillo",
        "album", "álbum", "ep", "playlist", "lista", "lista de reproducción"
    )

    /** Artistas de una línea de subtítulo: los textos que enlazan a un artista; si no, el primer texto útil. */
    private fun artistFromRuns(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return ""
        val names = ArrayList<String>()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val e = r.obj("navigationEndpoint", "browseEndpoint") ?: continue
            val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            if (type == "MUSIC_PAGE_TYPE_ARTIST" || type == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                e.optString("browseId").startsWith("UC")
            ) names.add(r.optString("text"))
        }
        if (names.isNotEmpty()) return names.joinToString(", ")
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
            if (t.isNotEmpty() && t != "•" && t.lowercase() !in TYPE_WORDS && !t.any { it.isDigit() }) return t
        }
        return ""
    }

    private fun badMusicType(item: JSONObject): Boolean {
        val type = findFirst(item, "watchEndpointMusicConfig")?.optString("musicVideoType").orEmpty()
        return type == "MUSIC_VIDEO_TYPE_UGC" || type.contains("PODCAST")
    }

    /** Sugerencias al escribir: consultas sugeridas y resultados directos (canciones, álbumes y artistas). */
    data class SearchSuggestions(
        val queries: List<String>,
        val songs: List<SongUi>,
        val albums: List<AlbumUi>,
        val artists: List<ArtistUi>
    )

    /** Bloqueante: llamar desde Dispatchers.IO. Mismo endpoint que usa la app oficial al escribir en la barra. */
    fun searchSuggestions(query: String): SearchSuggestions {
        val root = post("music/get_search_suggestions", baseBody().put("input", query))
        val sections = root.optJSONArray("contents")
        val queries = ArrayList<String>()
        val songs = ArrayList<SongUi>()
        val albums = ArrayList<AlbumUi>()
        val artists = ArrayList<ArtistUi>()
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val items = sections.optJSONObject(i)
                    ?.optJSONObject("searchSuggestionsSectionRenderer")?.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    item.optJSONObject("searchSuggestionRenderer")?.let { s ->
                        val text = s.obj("suggestion").runsText()
                            .ifBlank { s.obj("navigationEndpoint", "searchEndpoint")?.optString("query").orEmpty() }
                        if (text.isNotBlank()) queries.add(text)
                    }
                    item.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                        parseSuggestionItem(r, songs, albums, artists)
                    }
                }
            }
        }
        return SearchSuggestions(
            queries.distinct(), songs.distinctBy { it.url }, albums.distinctBy { it.url }, artists.distinctBy { it.url }
        )
    }

    private fun parseSuggestionItem(
        r: JSONObject,
        songs: MutableList<SongUi>,
        albums: MutableList<AlbumUi>,
        artists: MutableList<ArtistUi>
    ) {
        val e = r.obj("navigationEndpoint", "browseEndpoint")
        val browseId = e?.optString("browseId").orEmpty()
        val type = e?.let { findFirst(it, "browseEndpointContextMusicConfig")?.optString("pageType") }.orEmpty()
        val title = r.obj("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
        if (title.isBlank()) return
        val sub = r.obj("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text")
        val thumb = bestThumb(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))

        when {
            type == "MUSIC_PAGE_TYPE_ARTIST" -> {
                if (browseId.startsWith("UC")) {
                    artists.add(ArtistUi("https://www.youtube.com/channel/$browseId", title, thumb, 0L))
                }
            }
            e != null && (type == "MUSIC_PAGE_TYPE_ALBUM" || type == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                browseId.startsWith("MPREb") || browseId.startsWith("VL")) -> {
                val eps = ArrayList<JSONObject>()
                collect(r, "watchPlaylistEndpoint", eps)
                collect(r, "watchEndpoint", eps)
                val ids = eps.map { it.optString("playlistId") }
                val url: String? = when {
                    type == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") -> {
                        val olak = ids.firstOrNull { it.startsWith("OLAK5uy_") }
                        if (olak != null) "https://www.youtube.com/playlist?list=$olak" else "$YTM_ALBUM$browseId"
                    }
                    browseId.startsWith("VLPL") || browseId.startsWith("VLOLAK5uy_") ->
                        "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}"
                    browseId.startsWith("VL") -> "$YTM_PLAYLIST${browseId.removePrefix("VL")}"
                    else -> null
                }
                if (url != null) {
                    albums.add(
                        AlbumUi(
                            url = url, name = title, artist = artistFromRuns(sub), artistUrl = null, thumb = thumb,
                            subtitle = sub.runsText().ifBlank { null }
                        )
                    )
                }
            }
            else -> parseFeedSongRow(r)?.let { songs.add(it) }
        }
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Enlace "ver todo" de un carrusel: se pide con browseId + params. */
class MoreRef(val browseId: String, val params: String?)

/** Lo que trae la página real del artista en YouTube Music. */
class ParsedArtist(
    val subscribers: Long,
    val description: String?,
    /** Las ~5 canciones top, en el orden real de YouTube Music. */
    val topSongs: List<SongUi>,
    /** Lista completa de canciones del artista (ordenada por popularidad), si existe. */
    val songsPlaylistUrl: String?,
    val albums: List<AlbumUi>,
    val albumsMore: MoreRef?,
    val singles: List<AlbumUi>,
    val singlesMore: MoreRef?
)

/**
 * Cliente mínimo de la API interna de YouTube Music (youtubei/v1/browse, cliente WEB_REMIX).
 * Se pide siempre en inglés (hl=en) para que los números ("45.3M") y los títulos de sección
 * ("Albums", "Singles & EPs") tengan un formato predecible; la UI sigue en español.
 */
object YtMusicApi {

    /** Álbum/lista del Inicio que NewPipe no puede abrir por URL: se cargan con la API de YouTube Music. */
    private const val TAG = "YTHome"

    const val YTM_ALBUM = "ytm-album:"
    const val YTM_PLAYLIST = "ytm-playlist:"

    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val ANDROID_UA = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
    private const val ANDROID_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"

    private fun baseBody(hl: String = "en", gl: String = "US", android: Boolean = false): JSONObject {
        val clientCtx = JSONObject()
        if (android) {
            clientCtx.put("clientName", "ANDROID_MUSIC")
                .put("clientVersion", "7.27.52")
                .put("androidSdkVersion", 30)
                .put("osName", "Android")
                .put("osVersion", "11")
        } else {
            clientCtx.put("clientName", "WEB_REMIX")
                .put("clientVersion", "1.20250310.01.00")
        }
        clientCtx.put("hl", hl).put("gl", gl)
        return JSONObject().put("context", JSONObject().put("client", clientCtx))
    }

    /** Cookies de la sesión de Google (null = sin iniciar sesión). Las pone el ViewModel. */
    @Volatile
    var cookie: String? = null

    private class HttpException(val code: Int) : IOException("YT Music HTTP $code")

    /** Cabecera Authorization que pide YouTube Music con una sesión: SAPISIDHASH <hora>_<sha1(hora SAPISID origen)>. */
    private fun authHeader(cookie: String, origin: String = "https://music.youtube.com"): String? {
        val values = cookie.split(";").mapNotNull { part ->
            val t = part.trim()
            val i = t.indexOf('=')
            if (i > 0) t.substring(0, i) to t.substring(i + 1) else null
        }.toMap()
        val sapisid = values["SAPISID"] ?: values["__Secure-3PAPISID"] ?: return null
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$ts $sapisid $origin".toByteArray())
        return "SAPISIDHASH ${ts}_" + digest.joinToString("") { "%02x".format(it) }
    }

    /** Bloqueante: llamar desde Dispatchers.IO. Con sesión iniciada va autenticada; si YouTube la rechaza, reintenta sin ella. */
    private fun post(path: String, body: JSONObject, query: String = "", android: Boolean = false): JSONObject {
        val ck = cookie
        if (!android && ck != null) {
            try {
                return postOnce(path, body, query, false, ck)
            } catch (e: HttpException) {
                if (e.code != 401 && e.code != 403) throw e
                Log.w(TAG, "Sesión rechazada (HTTP ${e.code}); se reintenta sin sesión")
            }
        }
        return postOnce(path, body, query, android, null)
    }

    private fun postOnce(path: String, body: JSONObject, query: String, android: Boolean, ck: String?): JSONObject {
        val key = if (android) "&key=$ANDROID_KEY" else ""
        val builder = Request.Builder()
            .url("$BASE$path?prettyPrint=false$key$query")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (android) {
            builder.header("User-Agent", ANDROID_UA)
        } else {
            builder.header("User-Agent", UA)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
            if (ck != null) {
                builder.header("Cookie", ck)
                    .header("X-Goog-AuthUser", "0")
                    .header("X-Origin", "https://music.youtube.com")
                authHeader(ck)?.let { builder.header("Authorization", it) }
            } else {
                builder.header("Cookie", "SOCS=CAI")
            }
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            val text = resp.body?.string() ?: throw IOException(str(R.string.empty_response))
            return JSONObject(text)
        }
    }

    /** URLs con las que YouTube anota una reproducción (las trae la respuesta de "player"). */
    class PlaybackTracking(val playbackUrl: String?, val watchtimeUrl: String?, val client: String = "")

    /** Resultado de buscar las URLs: [tracking] si hubo, y en [log] qué pasó con cada cliente probado. */
    class TrackingResult(val tracking: PlaybackTracking?, val log: String)

    private const val VR_VERSION = "1.60.19"
    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/$VR_VERSION (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private fun playerBody(
        client: JSONObject,
        videoId: String,
        sts: Int? = null,
        embedUrl: String? = null
    ): JSONObject {
        val context = JSONObject().put("client", client)
        if (embedUrl != null) context.put("thirdParty", JSONObject().put("embedUrl", embedUrl))
        val playback = JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
        if (sts != null) playback.put("signatureTimestamp", sts)
        return JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", playback))
    }

    /**
     * Datos que YouTube espera en un "player" de web: versión real del cliente, visitorData (identifica la
     * visita; sin él responde "bot" o "Video unavailable") y signatureTimestamp. Se leen de la página de
     * YouTube Music con la sesión y se guardan unas horas.
     */
    private class WebSession(
        val cookieKey: Int,
        val clientVersion: String,
        val visitorData: String?,
        val sts: Int?,
        val fetchedAt: Long,
        val error: String? = null
    ) {
        fun summary() = "visitor=${if (visitorData != null) "sí" else "no"}, sts=${sts ?: "no"}" +
            (error?.let { ", fallo=$it" } ?: "")
    }

    @Volatile
    private var webSession: WebSession? = null
    private const val SESSION_TTL_MS = 6 * 60 * 60 * 1000L
    private const val FALLBACK_WEB_VERSION = "1.20250310.01.00"

    private fun httpGetText(url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            return resp.body?.string() ?: throw IOException(str(R.string.empty_response))
        }
    }

    private fun firstGroup(text: String, vararg patterns: String): String? {
        for (p in patterns) {
            val v = Regex(p).find(text)?.groupValues?.getOrNull(1)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun loadWebSession(ck: String): WebSession {
        val key = ck.hashCode()
        val cached = webSession
        if (cached != null && cached.cookieKey == key &&
            System.currentTimeMillis() - cached.fetchedAt < SESSION_TTL_MS
        ) return cached

        var version = FALLBACK_WEB_VERSION
        var visitor: String? = null
        var sts: Int? = null
        val errors = StringBuilder()
        fun fail(what: String, e: Exception) {
            val code = (e as? HttpException)?.code?.let { "HTTP $it" } ?: e.javaClass.simpleName
            if (errors.isNotEmpty()) errors.append(" / ")
            errors.append(what).append(' ').append(code)
            Log.w(TAG, "$what: $code")
        }
        try {
            val html = httpGetText(
                "https://music.youtube.com/",
                mapOf("User-Agent" to UA, "Cookie" to ck, "Accept-Language" to "en-US,en;q=0.9")
            )
            firstGroup(
                html,
                "\"INNERTUBE_CONTEXT_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"",
                "\"clientVersion\"\\s*:\\s*\"(1\\.[^\"]+)\""
            )?.let { version = it }
            visitor = firstGroup(
                html,
                "\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"",
                "\"visitorData\"\\s*:\\s*\"([^\"]+)\""
            )?.replace("\\u003d", "=")
            sts = firstGroup(html, "\"STS\"\\s*:\\s*(\\d+)")?.toIntOrNull()
            if (sts == null) {
                val jsPath = firstGroup(
                    html,
                    "\"PLAYER_JS_URL\"\\s*:\\s*\"([^\"]+)\"",
                    "\"jsUrl\"\\s*:\\s*\"([^\"]+)\""
                )?.replace("\\/", "/")
                if (jsPath != null) {
                    val jsUrl = if (jsPath.startsWith("http")) jsPath else "https://music.youtube.com$jsPath"
                    val js = httpGetText(jsUrl, mapOf("User-Agent" to UA))
                    sts = firstGroup(js, "signatureTimestamp[:=](\\d+)", "\\bsts\\s*:\\s*(\\d{5})")?.toIntOrNull()
                }
            }
        } catch (e: Exception) {
            fail("página", e)
        }

        // Respaldo 1: toda respuesta de youtubei trae responseContext.visitorData (no depende del HTML).
        if (visitor == null) {
            try {
                val r = postOnce("browse", baseBody().put("browseId", "FEmusic_home"), "", false, ck)
                visitor = r.optJSONObject("responseContext")
                    ?.optString("visitorData")?.takeIf { it.isNotBlank() }
                if (visitor == null) errors.append(if (errors.isEmpty()) "" else " / ").append("browse sin visitorData")
            } catch (e: Exception) {
                fail("browse", e)
            }
        }

        // Respaldo 2: signatureTimestamp desde el base.js del reproductor (como yt-dlp / NewPipe).
        if (sts == null) {
            try {
                val ia = httpGetText("https://www.youtube.com/iframe_api", mapOf("User-Agent" to UA))
                val hash = firstGroup(ia, "player\\\\?/([0-9a-fA-F]{8})\\\\?/")
                if (hash != null) {
                    val js = httpGetText(
                        "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_US/base.js",
                        mapOf("User-Agent" to UA)
                    )
                    sts = firstGroup(js, "signatureTimestamp[:=](\\d+)")?.toIntOrNull()
                }
                if (sts == null) errors.append(if (errors.isEmpty()) "" else " / ").append("sts no encontrado")
            } catch (e: Exception) {
                fail("sts", e)
            }
        }
        return WebSession(
            key, version, visitor, sts, System.currentTimeMillis(),
            errors.toString().takeIf { it.isNotEmpty() }
        ).also {
            // Solo se guarda si se consiguió lo importante; si no, se vuelve a intentar en el siguiente envío.
            if (it.visitorData != null) webSession = it
        }
    }

    /** Petición "player" a una dirección y cabeceras concretas (cada cliente de YouTube pide lo suyo). */
    private fun playerRequest(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            return JSONObject(resp.body?.string() ?: throw IOException(str(R.string.empty_response)))
        }
    }

    /** Extrae las URLs de seguimiento; [why] recibe el motivo si no las hay (para diagnosticar). */
    private fun parseTracking(root: JSONObject, why: StringBuilder): PlaybackTracking? {
        val t = root.optJSONObject("playbackTracking")
        if (t == null) {
            val status = root.optJSONObject("playabilityStatus")
            why.append("sin playbackTracking (${status?.optString("status") ?: "sin estado"}")
            status?.optString("reason")?.takeIf { it.isNotBlank() }?.let { why.append(": ").append(it.take(60)) }
            why.append(")")
            return null
        }
        fun url(key: String) = t.optJSONObject(key)?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val result = PlaybackTracking(url("videostatsPlaybackUrl"), url("videostatsWatchtimeUrl"))
        if (result.playbackUrl == null && result.watchtimeUrl == null) why.append("playbackTracking vacío")
        return result
    }

    /**
     * Pide a YouTube las URLs de seguimiento de un video. Solo con sesión iniciada. YouTube rechaza "player" si la
     * petición no parece un cliente real (sin visitorData / signatureTimestamp responde "bot" o "Video unavailable"),
     * así que primero se leen esos datos de la página de YouTube Music y se mandan en todos los intentos:
     * web con sesión, Android VR y Android Music (el cliente TV embebido ya no lo admite YouTube). La URL sirve igual venga de donde venga:
     * la sesión se envía al avisar (como yt-dlp).
     */
    fun playbackTracking(videoId: String): TrackingResult {
        val ck = cookie ?: return TrackingResult(null, "sin sesión")
        val log = StringBuilder()
        val web = loadWebSession(ck)
        log.append("datos: ").append(web.summary()).append("; ")
        val visitor = web.visitorData

        fun attempt(name: String, block: (StringBuilder) -> PlaybackTracking?): PlaybackTracking? {
            val why = StringBuilder()
            val found = try {
                block(why)
            } catch (e: HttpException) {
                why.append("HTTP ${e.code}")
                null
            } catch (e: Exception) {
                why.append(e.javaClass.simpleName)
                null
            }
            val ok = found != null && (found.playbackUrl != null || found.watchtimeUrl != null)
            if (!log.endsWith("; ")) log.append("; ")
            log.append(name).append('=').append(if (ok) "ok" else why.toString())
            return if (ok) PlaybackTracking(found!!.playbackUrl, found.watchtimeUrl, name) else null
        }

        fun JSONObject.withVisitor(): JSONObject = apply { visitor?.let { put("visitorData", it) } }
        fun MutableMap<String, String>.withVisitor(): MutableMap<String, String> =
            apply { visitor?.let { put("X-Goog-Visitor-Id", it) } }

        // 1) Web (música) con la sesión
        attempt("web") { why ->
            val ctx = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", web.clientVersion)
                .put("hl", "en").put("gl", "US").withVisitor()
            val headers = mutableMapOf(
                "User-Agent" to UA,
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "Cookie" to ck,
                "X-Goog-AuthUser" to "0",
                "X-Origin" to "https://music.youtube.com",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to web.clientVersion
            ).withVisitor()
            authHeader(ck)?.let { headers["Authorization"] = it }
            parseTracking(
                playerRequest("${BASE}player?prettyPrint=false", playerBody(ctx, videoId, web.sts), headers),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        // 2) Android VR (www.youtube.com)
        attempt("vr") { why ->
            val ctx = JSONObject().put("clientName", "ANDROID_VR").put("clientVersion", VR_VERSION)
                .put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
                .put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12L")
                .put("hl", "en").put("gl", "US").withVisitor()
            val headers = mutableMapOf(
                "User-Agent" to VR_UA,
                "X-YouTube-Client-Name" to "28",
                "X-YouTube-Client-Version" to VR_VERSION
            ).withVisitor()
            parseTracking(
                playerRequest("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", playerBody(ctx, videoId), headers),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        // 3) Android Music
        attempt("android") { why ->
            val ctx = JSONObject().put("clientName", "ANDROID_MUSIC").put("clientVersion", "7.27.52")
                .put("androidSdkVersion", 30).put("osName", "Android").put("osVersion", "11")
                .put("hl", "en").put("gl", "US").withVisitor()
            parseTracking(
                playerRequest(
                    "${BASE}player?prettyPrint=false&key=$ANDROID_KEY",
                    playerBody(ctx, videoId),
                    mutableMapOf("User-Agent" to ANDROID_UA).withVisitor()
                ),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        return TrackingResult(null, log.toString())
    }

    /** Valor del parámetro "c" que YouTube espera según el cliente que dio la URL. */
    private fun clientParam(source: String): String? = when (source) {
        "web" -> "WEB_REMIX"
        "vr" -> "ANDROID_VR"
        "android" -> "ANDROID_MUSIC"
        else -> null
    }

    /**
     * GET con la sesión a una URL de seguimiento, añadiendo [params]. Va con las mismas cabeceras de sesión que usa
     * la web (Authorization, X-Goog-AuthUser, Origin, visitorData) para que YouTube lo asocie a la cuenta, y con "c"
     * = cliente si la URL no lo trae. Devuelve el código HTTP (o -1 si no se pudo).
     */
    fun ping(baseUrl: String, params: Map<String, String>, source: String = ""): Int {
        val ck = cookie ?: return -1
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder() ?: return -1
        params.forEach { (k, v) -> builder.setQueryParameter(k, v) }
        val url = builder.build()
        if (url.queryParameter("c") == null) {
            clientParam(source)?.let { builder.setQueryParameter("c", it) }
        }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("User-Agent", UA)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("Cookie", ck)
            .header("X-Goog-AuthUser", "0")
        authHeader(ck)?.let { request.header("Authorization", it) }
        webSession?.visitorData?.let { request.header("X-Goog-Visitor-Id", it) }
        return client.newCall(request.build()).execute().use { it.code }
    }

    fun browse(browseId: String, params: String? = null): JSONObject {
        val body = baseBody().put("browseId", browseId)
        if (params != null) body.put("params", params)
        return post("browse", body)
    }

    /** Cola de reproducción de un video; su primer elemento trae artista(s) y álbum de la canción. */
    fun next(videoId: String): JSONObject =
        post("next", baseBody().put("videoId", videoId).put("isAudioOnly", true))

    // ---------- Álbum de una canción ----------

    class AlbumRef(val browseId: String, val name: String)

    /** Álbum (MPREb...) al que pertenece la canción, si la cola lo indica. */
    fun parseAlbumRef(nextRoot: JSONObject): AlbumRef? {
        val item = findFirst(nextRoot, "playlistPanelVideoRenderer") ?: return null
        val runs = item.obj("longBylineText")?.optJSONArray("runs") ?: return null
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val e = run.obj("navigationEndpoint", "browseEndpoint") ?: continue
            val id = e.optString("browseId")
            val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            val name = run.optString("text")
            if ((type == "MUSIC_PAGE_TYPE_ALBUM" || id.startsWith("MPREb")) && name.isNotBlank()) {
                return AlbumRef(id, name)
            }
        }
        return null
    }

    class SongMeta(val albumRef: AlbumRef?, val year: String?, val durationSec: Long)

    /** Álbum, año y duración de una canción a partir de la respuesta de "next". */
    fun parseSongMeta(nextRoot: JSONObject): SongMeta {
        val item = findFirst(nextRoot, "playlistPanelVideoRenderer")
        var year: String? = null
        val runs = item?.obj("longBylineText")?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
                if (Regex("""^(19|20)\d{2}$""").matches(t)) year = t
            }
        }
        return SongMeta(
            albumRef = parseAlbumRef(nextRoot),
            year = year,
            durationSec = parseDuration(item?.obj("lengthText").runsText())
        )
    }

    /** Lista de reproducción (OLAK5uy_...) de la página de un álbum; es la que entiende NewPipe. */
    fun parseAlbumPlaylistId(albumRoot: JSONObject): String? {
        val eps = ArrayList<JSONObject>()
        collect(albumRoot, "watchEndpoint", eps)
        collect(albumRoot, "watchPlaylistEndpoint", eps)
        eps.map { it.optString("playlistId") }.firstOrNull { it.startsWith("OLAK5uy_") }?.let { return it }
        return Regex("""OLAK5uy_[A-Za-z0-9_-]+""").find(albumRoot.toString())?.value
    }

    // ---------- Inicio (FEmusic_home) ----------

    /** Un estante del Inicio de YouTube Music: canciones y/o álbumes y listas. */
    class FeedShelf(
        val title: String,
        val songs: List<SongUi>,
        val albums: List<AlbumUi>,
        /** true si YouTube lo manda como filas de canciones (Selecciones rápidas): se muestra en columnas de 3. */
        val compact: Boolean = false,
        /** Nombre de la cuenta que YouTube pone sobre el título en estantes personalizados (p. ej. "Accesos directos"). */
        val strapline: String? = null,
        /** true si es el estante "Accesos directos" (Speed dial): se muestra en páginas de 3 x 3 portadas. */
        val speedDial: Boolean = false
    )

    /** Categoría de "Estados de ánimo y géneros" de YouTube Music (Los 70, Pop latino, Hip hop...). */
    class ExploreCategory(val title: String, val browseId: String, val params: String, val color: Long?)

    /** Todas las categorías de "Explorar" (estados de ánimo y géneros), con el color que les da YouTube. */
    fun exploreCategories(hl: String, gl: String): List<ExploreCategory> {
        val root = post("browse", baseBody(hl, gl, false).put("browseId", "FEmusic_moods_and_genres"))
        val buttons = ArrayList<JSONObject>()
        collect(root, "musicNavigationButtonRenderer", buttons)
        return buttons.mapNotNull { b ->
            val e = b.obj("clickCommand", "browseEndpoint") ?: return@mapNotNull null
            val params = e.optString("params")
            val title = b.obj("buttonText").runsText().trim()
            if (params.isBlank() || title.isBlank()) return@mapNotNull null
            val color = b.optJSONObject("solid")?.optLong("leftStripeColor", 0L)?.takeIf { it != 0L }
            ExploreCategory(title, e.optString("browseId").ifBlank { "FEmusic_moods_and_genres_category" }, params, color)
        }.distinctBy { it.params }
    }

    /** Listas y estantes de una categoría de Explorar. */
    fun categoryFeed(cat: ExploreCategory, hl: String, gl: String): List<FeedShelf> {
        val root = post(
            "browse",
            baseBody(hl, gl, false).put("browseId", cat.browseId).put("params", cat.params)
        )
        return parseFeed(root, includeGrids = true).distinctBy { it.title }
    }

    /** Chip de la parte de arriba del Inicio de YouTube Music (Relajar, Energizar, Entrenar...). */
    class FeedChip(val label: String, val browseId: String, val params: String)

    private fun parseChips(root: JSONObject): List<FeedChip> {
        val found = ArrayList<JSONObject>()
        collect(root, "chipCloudChipRenderer", found)
        return found.mapNotNull { chip ->
            val e = chip.obj("navigationEndpoint", "browseEndpoint") ?: return@mapNotNull null
            val params = e.optString("params")
            val label = chip.obj("text").runsText().trim()
            if (params.isBlank() || label.isBlank()) return@mapNotNull null
            FeedChip(label, e.optString("browseId").ifBlank { "FEmusic_home" }, params)
        }.distinctBy { it.params }
    }

    /** Estantes que YouTube Music muestra al tocar un chip del Inicio. */
    fun chipFeed(chip: FeedChip, hl: String, gl: String, android: Boolean = false): List<FeedShelf> {
        val root = post(
            "browse",
            baseBody(hl, gl, android).put("browseId", chip.browseId).put("params", chip.params),
            android = android
        )
        return parseFeed(root).distinctBy { it.title }
    }

    /**
     * Inicio de YouTube Music (FEmusic_home): los estantes que arma el algoritmo de YouTube.
     * Sin cuenta iniciada no es personal: depende de la región ([gl]) y del idioma ([hl]).
     * [android] usa el cliente ANDROID_MUSIC en lugar de WEB_REMIX.
     */
    fun home(
        hl: String,
        gl: String,
        android: Boolean = false,
        /** Se llama con todos los estantes acumulados cada vez que llega una página nueva. */
        onProgress: (List<FeedShelf>) -> Unit = {},
        /** Se llama una vez con los chips del Inicio (Relajar, Energizar...), si YouTube los manda. */
        onChips: (List<FeedChip>) -> Unit = {}
    ): List<FeedShelf> {
        val out = ArrayList<FeedShelf>()
        val first = post("browse", baseBody(hl, gl, android).put("browseId", "FEmusic_home"), android = android)
        parseChips(first).takeIf { it.isNotEmpty() }?.let(onChips)
        out += parseFeed(first)
        if (out.isNotEmpty()) onProgress(out.distinctBy { it.title })
        Log.d(TAG, "página 1 (android=$android): ${out.size} estantes, token=${continuationToken(first)?.length}")

        // Siguientes páginas del Inicio
        var token = continuationToken(first)
        var pages = 1
        while (!token.isNullOrBlank() && pages < 6 && out.size < 20) {
            val next = fetchContinuation(token, hl, gl, android) ?: break
            val shelves = parseFeed(next)
            out += shelves
            onProgress(out.distinctBy { it.title })
            token = continuationToken(next)
            pages++
            Log.d(TAG, "página $pages: +${shelves.size} estantes, token=${token?.length}")
        }

        // Si aun así hay pocos, los estantes de cada chip de ánimo (Energizar, Relajar...) que YouTube ofrece
        if (out.size < 8) {
            val chips = ArrayList<JSONObject>()
            collect(first, "chipCloudChipRenderer", chips)
            Log.d(TAG, "chips encontrados: ${chips.size}")
            var used = 0
            for (chip in chips) {
                if (used >= 4) break
                val e = chip.obj("navigationEndpoint", "browseEndpoint") ?: continue
                val params = e.optString("params")
                if (params.isBlank()) continue
                val id = e.optString("browseId").ifBlank { "FEmusic_home" }
                try {
                    val root = post(
                        "browse",
                        baseBody(hl, gl, android).put("browseId", id).put("params", params),
                        android = android
                    )
                    val shelves = parseFeed(root)
                    Log.d(TAG, "chip '${chip.obj("text").runsText()}': ${shelves.size} estantes")
                    out += shelves
                    onProgress(out.distinctBy { it.title })
                    used++
                } catch (ex: IOException) {
                    Log.w(TAG, "chip falló: ${ex.message}")
                }
            }
        }
        return out.distinctBy { it.title }
    }

    private fun continuationToken(root: JSONObject): String? =
        findFirst(root, "nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
            ?: findFirst(root, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }

    /**
     * Pide la siguiente página probando las formas que usan YouTube Music y ytmusicapi.
     * El token ya viene codificado por YouTube (termina en %3D...), no se vuelve a codificar.
     */
    private fun fetchContinuation(token: String, hl: String, gl: String, android: Boolean): JSONObject? {
        val attempts: List<() -> JSONObject> = listOf(
            {
                post(
                    "browse", baseBody(hl, gl, android).put("browseId", "FEmusic_home"),
                    query = "&ctoken=$token&continuation=$token", android = android
                )
            },
            {
                post(
                    "browse", baseBody(hl, gl, android),
                    query = "&ctoken=$token&continuation=$token&type=next", android = android
                )
            },
            { post("browse", baseBody(hl, gl, android).put("continuation", token), android = android) }
        )
        for ((i, attempt) in attempts.withIndex()) {
            try {
                val root = attempt()
                if (parseFeed(root).isNotEmpty()) return root
                Log.w(TAG, "continuación intento ${i + 1}: respuesta sin estantes")
            } catch (e: IOException) {
                Log.w(TAG, "continuación intento ${i + 1} falló: ${e.message}")
            }
        }
        return null
    }

    private fun parseFeed(root: JSONObject, includeGrids: Boolean = false): List<FeedShelf> {
        val carousels = ArrayList<JSONObject>()
        collect(root, "musicCarouselShelfRenderer", carousels)
        val blocks = ArrayList<Triple<String, org.json.JSONArray, String>>()
        for (car in carousels) {
            val title = car.obj("header", "musicCarouselShelfBasicHeaderRenderer", "title").runsText()
            if (title.isBlank()) continue
            val contents = car.optJSONArray("contents") ?: continue
            val strap = car.obj("header", "musicCarouselShelfBasicHeaderRenderer", "strapline").runsText().trim()
            blocks.add(Triple(title, contents, strap))
        }
        if (includeGrids) {
            // Las páginas de categoría (Estados de ánimo y géneros) también traen listas en cuadrículas
            val grids = ArrayList<JSONObject>()
            collect(root, "gridRenderer", grids)
            for (g in grids) {
                val title = g.obj("header", "gridHeaderRenderer", "title").runsText().ifBlank { "Listas" }
                val items = g.optJSONArray("items") ?: continue
                blocks.add(Triple(title, items, ""))
            }
        }
        val shelves = ArrayList<FeedShelf>()
        for ((title, contents, strap) in blocks) {

            val songs = ArrayList<SongUi>()
            val albums = ArrayList<AlbumUi>()
            var fromRows = false
            for (i in 0 until contents.length()) {
                val c = contents.optJSONObject(i) ?: continue
                c.optJSONObject("musicResponsiveListItemRenderer")?.let { row ->
                    val song = parseFeedSongRow(row)
                    if (song != null) {
                        songs.add(song)
                        fromRows = true
                    }
                }
                c.optJSONObject("musicTwoRowItemRenderer")?.let { t ->
                    val song = parseFeedTwoRowSong(t)
                    if (song != null) {
                        songs.add(song)
                    } else {
                        val album = parseFeedAlbum(t)
                        if (album != null) albums.add(album)
                    }
                }
            }
            val s = songs.distinctBy { it.url }
            val a = albums.distinctBy { it.url }
            val low = title.lowercase()
            val dial = low.contains("accesos directos") || low.contains("speed dial")
            if (s.size + a.size >= 2) {
                shelves.add(
                    FeedShelf(
                        title, s, a,
                        compact = fromRows && a.isEmpty(),
                        strapline = strap.ifBlank { null },
                        speedDial = dial
                    )
                )
            }
        }
        Log.d(TAG, "estantes del feed: " + shelves.joinToString(" | ") { it.title + if (it.speedDial) " [accesos directos]" else "" })
        return shelves
    }

    private val TYPE_WORDS = setOf(
        "song", "video", "canción", "cancion", "vídeo", "single", "sencillo",
        "album", "álbum", "ep", "playlist", "lista", "lista de reproducción"
    )

    /** Artistas de una línea de subtítulo: los textos que enlazan a un artista; si no, el primer texto útil. */
    private fun artistFromRuns(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return ""
        val names = ArrayList<String>()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val e = r.obj("navigationEndpoint", "browseEndpoint") ?: continue
            val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            if (type == "MUSIC_PAGE_TYPE_ARTIST" || type == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                e.optString("browseId").startsWith("UC")
            ) names.add(r.optString("text"))
        }
        if (names.isNotEmpty()) return names.joinToString(", ")
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
            if (t.isNotEmpty() && t != "•" && t.lowercase() !in TYPE_WORDS && !t.any { it.isDigit() }) return t
        }
        return ""
    }

    private fun badMusicType(item: JSONObject): Boolean {
        val type = findFirst(item, "watchEndpointMusicConfig")?.optString("musicVideoType").orEmpty()
        return type == "MUSIC_VIDEO_TYPE_UGC" || type.contains("PODCAST")
    }

    /** Sugerencias al escribir: consultas sugeridas y resultados directos (canciones, álbumes y artistas). */
    data class SearchSuggestions(
        val queries: List<String>,
        val songs: List<SongUi>,
        val albums: List<AlbumUi>,
        val artists: List<ArtistUi>
    )

    /** Bloqueante: llamar desde Dispatchers.IO. Mismo endpoint que usa la app oficial al escribir en la barra. */
    fun searchSuggestions(query: String): SearchSuggestions {
        val root = post("music/get_search_suggestions", baseBody().put("input", query))
        val sections = root.optJSONArray("contents")
        val queries = ArrayList<String>()
        val songs = ArrayList<SongUi>()
        val albums = ArrayList<AlbumUi>()
        val artists = ArrayList<ArtistUi>()
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val items = sections.optJSONObject(i)
                    ?.optJSONObject("searchSuggestionsSectionRenderer")?.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    item.optJSONObject("searchSuggestionRenderer")?.let { s ->
                        val text = s.obj("suggestion").runsText()
                            .ifBlank { s.obj("navigationEndpoint", "searchEndpoint")?.optString("query").orEmpty() }
                        if (text.isNotBlank()) queries.add(text)
                    }
                    item.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                        parseSuggestionItem(r, songs, albums, artists)
                    }
                }
            }
        }
        return SearchSuggestions(
            queries.distinct(), songs.distinctBy { it.url }, albums.distinctBy { it.url }, artists.distinctBy { it.url }
        )
    }

    private fun parseSuggestionItem(
        r: JSONObject,
        songs: MutableList<SongUi>,
        albums: MutableList<AlbumUi>,
        artists: MutableList<ArtistUi>
    ) {
        val e = r.obj("navigationEndpoint", "browseEndpoint")
        val browseId = e?.optString("browseId").orEmpty()
        val type = e?.let { findFirst(it, "browseEndpointContextMusicConfig")?.optString("pageType") }.orEmpty()
        val title = r.obj("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
        if (title.isBlank()) return
        val sub = r.obj("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text")
        val thumb = bestThumb(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))

        when {
            type == "MUSIC_PAGE_TYPE_ARTIST" -> {
                if (browseId.startsWith("UC")) {
                    artists.add(ArtistUi("https://www.youtube.com/channel/$browseId", title, thumb, 0L))
                }
            }
            e != null && (type == "MUSIC_PAGE_TYPE_ALBUM" || type == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                browseId.startsWith("MPREb") || browseId.startsWith("VL")) -> {
                val eps = ArrayList<JSONObject>()
                collect(r, "watchPlaylistEndpoint", eps)
                collect(r, "watchEndpoint", eps)
                val ids = eps.map { it.optString("playlistId") }
                val url: String? = when {
                    type == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") -> {
                        val olak = ids.firstOrNull { it.startsWith("OLAK5uy_") }
                        if (olak != null) "https://www.youtube.com/playlist?list=$olak" else "$YTM_ALBUM$browseId"
                    }
                    browseId.startsWith("VLPL") || browseId.startsWith("VLOLAK5uy_") ->
                        "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}"
                    browseId.startsWith("VL") -> "$YTM_PLAYLIST${browseId.removePrefix("VL")}"
                    else -> null
                }
                if (url != null) {
                    albums.add(
                        AlbumUi(
                            url = url, name = title, artist = artistFromRuns(sub), artistUrl = null, thumb = thumb,
                            subtitle = sub.runsText().ifBlank { null }
                        )
                    )
                }
            }
            else -> parseFeedSongRow(r)?.let { songs.add(it) }
        }
=======

/** Enlace "ver todo" de un carrusel: se pide con browseId + params. */
class MoreRef(val browseId: String, val params: String?)

/** Lo que trae la página real del artista en YouTube Music. */
class ParsedArtist(
    val subscribers: Long,
    val description: String?,
    /** Las ~5 canciones top, en el orden real de YouTube Music. */
    val topSongs: List<SongUi>,
    /** Lista completa de canciones del artista (ordenada por popularidad), si existe. */
    val songsPlaylistUrl: String?,
    val albums: List<AlbumUi>,
    val albumsMore: MoreRef?,
    val singles: List<AlbumUi>,
    val singlesMore: MoreRef?
)

/**
 * Cliente mínimo de la API interna de YouTube Music (youtubei/v1/browse, cliente WEB_REMIX).
 * Se pide siempre en inglés (hl=en) para que los números ("45.3M") y los títulos de sección
 * ("Albums", "Singles & EPs") tengan un formato predecible; la UI sigue en español.
 */
object YtMusicApi {

    /** Álbum/lista del Inicio que NewPipe no puede abrir por URL: se cargan con la API de YouTube Music. */
    private const val TAG = "YTHome"

    const val YTM_ALBUM = "ytm-album:"
    const val YTM_PLAYLIST = "ytm-playlist:"

    private const val BASE = "https://music.youtube.com/youtubei/v1/"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val ANDROID_UA = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
    private const val ANDROID_KEY = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"

    private fun baseBody(hl: String = "en", gl: String = "US", android: Boolean = false): JSONObject {
        val clientCtx = JSONObject()
        if (android) {
            clientCtx.put("clientName", "ANDROID_MUSIC")
                .put("clientVersion", "7.27.52")
                .put("androidSdkVersion", 30)
                .put("osName", "Android")
                .put("osVersion", "11")
        } else {
            clientCtx.put("clientName", "WEB_REMIX")
                .put("clientVersion", "1.20250310.01.00")
        }
        clientCtx.put("hl", hl).put("gl", gl)
        return JSONObject().put("context", JSONObject().put("client", clientCtx))
    }

    /** Cookies de la sesión de Google (null = sin iniciar sesión). Las pone el ViewModel. */
    @Volatile
    var cookie: String? = null

    private class HttpException(val code: Int) : IOException("YT Music HTTP $code")

    /** Cabecera Authorization que pide YouTube Music con una sesión: SAPISIDHASH <hora>_<sha1(hora SAPISID origen)>. */
    private fun authHeader(cookie: String, origin: String = "https://music.youtube.com"): String? {
        val values = cookie.split(";").mapNotNull { part ->
            val t = part.trim()
            val i = t.indexOf('=')
            if (i > 0) t.substring(0, i) to t.substring(i + 1) else null
        }.toMap()
        val sapisid = values["SAPISID"] ?: values["__Secure-3PAPISID"] ?: return null
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$ts $sapisid $origin".toByteArray())
        return "SAPISIDHASH ${ts}_" + digest.joinToString("") { "%02x".format(it) }
    }

    /** Bloqueante: llamar desde Dispatchers.IO. Con sesión iniciada va autenticada; si YouTube la rechaza, reintenta sin ella. */
    private fun post(path: String, body: JSONObject, query: String = "", android: Boolean = false): JSONObject {
        val ck = cookie
        if (!android && ck != null) {
            try {
                return postOnce(path, body, query, false, ck)
            } catch (e: HttpException) {
                if (e.code != 401 && e.code != 403) throw e
                Log.w(TAG, "Sesión rechazada (HTTP ${e.code}); se reintenta sin sesión")
            }
        }
        return postOnce(path, body, query, android, null)
    }

    private fun postOnce(path: String, body: JSONObject, query: String, android: Boolean, ck: String?): JSONObject {
        val key = if (android) "&key=$ANDROID_KEY" else ""
        val builder = Request.Builder()
            .url("$BASE$path?prettyPrint=false$key$query")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (android) {
            builder.header("User-Agent", ANDROID_UA)
        } else {
            builder.header("User-Agent", UA)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
            if (ck != null) {
                builder.header("Cookie", ck)
                    .header("X-Goog-AuthUser", "0")
                    .header("X-Origin", "https://music.youtube.com")
                authHeader(ck)?.let { builder.header("Authorization", it) }
            } else {
                builder.header("Cookie", "SOCS=CAI")
            }
        }

        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            val text = resp.body?.string() ?: throw IOException("Respuesta vacía")
            return JSONObject(text)
        }
    }

    /** URLs con las que YouTube anota una reproducción (las trae la respuesta de "player"). */
    class PlaybackTracking(val playbackUrl: String?, val watchtimeUrl: String?, val client: String = "")

    /** Resultado de buscar las URLs: [tracking] si hubo, y en [log] qué pasó con cada cliente probado. */
    class TrackingResult(val tracking: PlaybackTracking?, val log: String)

    private const val VR_VERSION = "1.60.19"
    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/$VR_VERSION (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private fun playerBody(
        client: JSONObject,
        videoId: String,
        sts: Int? = null,
        embedUrl: String? = null
    ): JSONObject {
        val context = JSONObject().put("client", client)
        if (embedUrl != null) context.put("thirdParty", JSONObject().put("embedUrl", embedUrl))
        val playback = JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
        if (sts != null) playback.put("signatureTimestamp", sts)
        return JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", playback))
    }

    /**
     * Datos que YouTube espera en un "player" de web: versión real del cliente, visitorData (identifica la
     * visita; sin él responde "bot" o "Video unavailable") y signatureTimestamp. Se leen de la página de
     * YouTube Music con la sesión y se guardan unas horas.
     */
    private class WebSession(
        val cookieKey: Int,
        val clientVersion: String,
        val visitorData: String?,
        val sts: Int?,
        val fetchedAt: Long,
        val error: String? = null
    ) {
        fun summary() = "visitor=${if (visitorData != null) "sí" else "no"}, sts=${sts ?: "no"}" +
            (error?.let { ", fallo=$it" } ?: "")
    }

    @Volatile
    private var webSession: WebSession? = null
    private const val SESSION_TTL_MS = 6 * 60 * 60 * 1000L
    private const val FALLBACK_WEB_VERSION = "1.20250310.01.00"

    private fun httpGetText(url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            return resp.body?.string() ?: throw IOException("Respuesta vacía")
        }
    }

    private fun firstGroup(text: String, vararg patterns: String): String? {
        for (p in patterns) {
            val v = Regex(p).find(text)?.groupValues?.getOrNull(1)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun loadWebSession(ck: String): WebSession {
        val key = ck.hashCode()
        val cached = webSession
        if (cached != null && cached.cookieKey == key &&
            System.currentTimeMillis() - cached.fetchedAt < SESSION_TTL_MS
        ) return cached

        var version = FALLBACK_WEB_VERSION
        var visitor: String? = null
        var sts: Int? = null
        val errors = StringBuilder()
        fun fail(what: String, e: Exception) {
            val code = (e as? HttpException)?.code?.let { "HTTP $it" } ?: e.javaClass.simpleName
            if (errors.isNotEmpty()) errors.append(" / ")
            errors.append(what).append(' ').append(code)
            Log.w(TAG, "$what: $code")
        }
        try {
            val html = httpGetText(
                "https://music.youtube.com/",
                mapOf("User-Agent" to UA, "Cookie" to ck, "Accept-Language" to "en-US,en;q=0.9")
            )
            firstGroup(
                html,
                "\"INNERTUBE_CONTEXT_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"",
                "\"clientVersion\"\\s*:\\s*\"(1\\.[^\"]+)\""
            )?.let { version = it }
            visitor = firstGroup(
                html,
                "\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"",
                "\"visitorData\"\\s*:\\s*\"([^\"]+)\""
            )?.replace("\\u003d", "=")
            sts = firstGroup(html, "\"STS\"\\s*:\\s*(\\d+)")?.toIntOrNull()
            if (sts == null) {
                val jsPath = firstGroup(
                    html,
                    "\"PLAYER_JS_URL\"\\s*:\\s*\"([^\"]+)\"",
                    "\"jsUrl\"\\s*:\\s*\"([^\"]+)\""
                )?.replace("\\/", "/")
                if (jsPath != null) {
                    val jsUrl = if (jsPath.startsWith("http")) jsPath else "https://music.youtube.com$jsPath"
                    val js = httpGetText(jsUrl, mapOf("User-Agent" to UA))
                    sts = firstGroup(js, "signatureTimestamp[:=](\\d+)", "\\bsts\\s*:\\s*(\\d{5})")?.toIntOrNull()
                }
            }
        } catch (e: Exception) {
            fail("página", e)
        }

        // Respaldo 1: toda respuesta de youtubei trae responseContext.visitorData (no depende del HTML).
        if (visitor == null) {
            try {
                val r = postOnce("browse", baseBody().put("browseId", "FEmusic_home"), "", false, ck)
                visitor = r.optJSONObject("responseContext")
                    ?.optString("visitorData")?.takeIf { it.isNotBlank() }
                if (visitor == null) errors.append(if (errors.isEmpty()) "" else " / ").append("browse sin visitorData")
            } catch (e: Exception) {
                fail("browse", e)
            }
        }

        // Respaldo 2: signatureTimestamp desde el base.js del reproductor (como yt-dlp / NewPipe).
        if (sts == null) {
            try {
                val ia = httpGetText("https://www.youtube.com/iframe_api", mapOf("User-Agent" to UA))
                val hash = firstGroup(ia, "player\\\\?/([0-9a-fA-F]{8})\\\\?/")
                if (hash != null) {
                    val js = httpGetText(
                        "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_US/base.js",
                        mapOf("User-Agent" to UA)
                    )
                    sts = firstGroup(js, "signatureTimestamp[:=](\\d+)")?.toIntOrNull()
                }
                if (sts == null) errors.append(if (errors.isEmpty()) "" else " / ").append("sts no encontrado")
            } catch (e: Exception) {
                fail("sts", e)
            }
        }
        return WebSession(
            key, version, visitor, sts, System.currentTimeMillis(),
            errors.toString().takeIf { it.isNotEmpty() }
        ).also {
            // Solo se guarda si se consiguió lo importante; si no, se vuelve a intentar en el siguiente envío.
            if (it.visitorData != null) webSession = it
        }
    }

    /** Petición "player" a una dirección y cabeceras concretas (cada cliente de YouTube pide lo suyo). */
    private fun playerRequest(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code)
            return JSONObject(resp.body?.string() ?: throw IOException("Respuesta vacía"))
        }
    }

    /** Extrae las URLs de seguimiento; [why] recibe el motivo si no las hay (para diagnosticar). */
    private fun parseTracking(root: JSONObject, why: StringBuilder): PlaybackTracking? {
        val t = root.optJSONObject("playbackTracking")
        if (t == null) {
            val status = root.optJSONObject("playabilityStatus")
            why.append("sin playbackTracking (${status?.optString("status") ?: "sin estado"}")
            status?.optString("reason")?.takeIf { it.isNotBlank() }?.let { why.append(": ").append(it.take(60)) }
            why.append(")")
            return null
        }
        fun url(key: String) = t.optJSONObject(key)?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val result = PlaybackTracking(url("videostatsPlaybackUrl"), url("videostatsWatchtimeUrl"))
        if (result.playbackUrl == null && result.watchtimeUrl == null) why.append("playbackTracking vacío")
        return result
    }

    /**
     * Pide a YouTube las URLs de seguimiento de un video. Solo con sesión iniciada. YouTube rechaza "player" si la
     * petición no parece un cliente real (sin visitorData / signatureTimestamp responde "bot" o "Video unavailable"),
     * así que primero se leen esos datos de la página de YouTube Music y se mandan en todos los intentos:
     * web con sesión, Android VR y Android Music (el cliente TV embebido ya no lo admite YouTube). La URL sirve igual venga de donde venga:
     * la sesión se envía al avisar (como yt-dlp).
     */
    fun playbackTracking(videoId: String): TrackingResult {
        val ck = cookie ?: return TrackingResult(null, "sin sesión")
        val log = StringBuilder()
        val web = loadWebSession(ck)
        log.append("datos: ").append(web.summary()).append("; ")
        val visitor = web.visitorData

        fun attempt(name: String, block: (StringBuilder) -> PlaybackTracking?): PlaybackTracking? {
            val why = StringBuilder()
            val found = try {
                block(why)
            } catch (e: HttpException) {
                why.append("HTTP ${e.code}")
                null
            } catch (e: Exception) {
                why.append(e.javaClass.simpleName)
                null
            }
            val ok = found != null && (found.playbackUrl != null || found.watchtimeUrl != null)
            if (!log.endsWith("; ")) log.append("; ")
            log.append(name).append('=').append(if (ok) "ok" else why.toString())
            return if (ok) PlaybackTracking(found!!.playbackUrl, found.watchtimeUrl, name) else null
        }

        fun JSONObject.withVisitor(): JSONObject = apply { visitor?.let { put("visitorData", it) } }
        fun MutableMap<String, String>.withVisitor(): MutableMap<String, String> =
            apply { visitor?.let { put("X-Goog-Visitor-Id", it) } }

        // 1) Web (música) con la sesión
        attempt("web") { why ->
            val ctx = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", web.clientVersion)
                .put("hl", "en").put("gl", "US").withVisitor()
            val headers = mutableMapOf(
                "User-Agent" to UA,
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "Cookie" to ck,
                "X-Goog-AuthUser" to "0",
                "X-Origin" to "https://music.youtube.com",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to web.clientVersion
            ).withVisitor()
            authHeader(ck)?.let { headers["Authorization"] = it }
            parseTracking(
                playerRequest("${BASE}player?prettyPrint=false", playerBody(ctx, videoId, web.sts), headers),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        // 2) Android VR (www.youtube.com)
        attempt("vr") { why ->
            val ctx = JSONObject().put("clientName", "ANDROID_VR").put("clientVersion", VR_VERSION)
                .put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
                .put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12L")
                .put("hl", "en").put("gl", "US").withVisitor()
            val headers = mutableMapOf(
                "User-Agent" to VR_UA,
                "X-YouTube-Client-Name" to "28",
                "X-YouTube-Client-Version" to VR_VERSION
            ).withVisitor()
            parseTracking(
                playerRequest("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", playerBody(ctx, videoId), headers),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        // 3) Android Music
        attempt("android") { why ->
            val ctx = JSONObject().put("clientName", "ANDROID_MUSIC").put("clientVersion", "7.27.52")
                .put("androidSdkVersion", 30).put("osName", "Android").put("osVersion", "11")
                .put("hl", "en").put("gl", "US").withVisitor()
            parseTracking(
                playerRequest(
                    "${BASE}player?prettyPrint=false&key=$ANDROID_KEY",
                    playerBody(ctx, videoId),
                    mutableMapOf("User-Agent" to ANDROID_UA).withVisitor()
                ),
                why
            )
        }?.let { return TrackingResult(it, log.toString()) }

        return TrackingResult(null, log.toString())
    }

    /** Valor del parámetro "c" que YouTube espera según el cliente que dio la URL. */
    private fun clientParam(source: String): String? = when (source) {
        "web" -> "WEB_REMIX"
        "vr" -> "ANDROID_VR"
        "android" -> "ANDROID_MUSIC"
        else -> null
    }

    /**
     * GET con la sesión a una URL de seguimiento, añadiendo [params]. Va con las mismas cabeceras de sesión que usa
     * la web (Authorization, X-Goog-AuthUser, Origin, visitorData) para que YouTube lo asocie a la cuenta, y con "c"
     * = cliente si la URL no lo trae. Devuelve el código HTTP (o -1 si no se pudo).
     */
    fun ping(baseUrl: String, params: Map<String, String>, source: String = ""): Int {
        val ck = cookie ?: return -1
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder() ?: return -1
        params.forEach { (k, v) -> builder.setQueryParameter(k, v) }
        val url = builder.build()
        if (url.queryParameter("c") == null) {
            clientParam(source)?.let { builder.setQueryParameter("c", it) }
        }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("User-Agent", UA)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("Cookie", ck)
            .header("X-Goog-AuthUser", "0")
        authHeader(ck)?.let { request.header("Authorization", it) }
        webSession?.visitorData?.let { request.header("X-Goog-Visitor-Id", it) }
        return client.newCall(request.build()).execute().use { it.code }
    }

    fun browse(browseId: String, params: String? = null): JSONObject {
        val body = baseBody().put("browseId", browseId)
        if (params != null) body.put("params", params)
        return post("browse", body)
    }

    /** Cola de reproducción de un video; su primer elemento trae artista(s) y álbum de la canción. */
    fun next(videoId: String): JSONObject =
        post("next", baseBody().put("videoId", videoId).put("isAudioOnly", true))

    // ---------- Álbum de una canción ----------

    class AlbumRef(val browseId: String, val name: String)

    /** Álbum (MPREb...) al que pertenece la canción, si la cola lo indica. */
    fun parseAlbumRef(nextRoot: JSONObject): AlbumRef? {
        val item = findFirst(nextRoot, "playlistPanelVideoRenderer") ?: return null
        val runs = item.obj("longBylineText")?.optJSONArray("runs") ?: return null
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val e = run.obj("navigationEndpoint", "browseEndpoint") ?: continue
            val id = e.optString("browseId")
            val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            val name = run.optString("text")
            if ((type == "MUSIC_PAGE_TYPE_ALBUM" || id.startsWith("MPREb")) && name.isNotBlank()) {
                return AlbumRef(id, name)
            }
        }
        return null
    }

    class SongMeta(val albumRef: AlbumRef?, val year: String?, val durationSec: Long)

    /** Álbum, año y duración de una canción a partir de la respuesta de "next". */
    fun parseSongMeta(nextRoot: JSONObject): SongMeta {
        val item = findFirst(nextRoot, "playlistPanelVideoRenderer")
        var year: String? = null
        val runs = item?.obj("longBylineText")?.optJSONArray("runs")
        if (runs != null) {
            for (i in 0 until runs.length()) {
                val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
                if (Regex("""^(19|20)\d{2}$""").matches(t)) year = t
            }
        }
        return SongMeta(
            albumRef = parseAlbumRef(nextRoot),
            year = year,
            durationSec = parseDuration(item?.obj("lengthText").runsText())
        )
    }

    /** Lista de reproducción (OLAK5uy_...) de la página de un álbum; es la que entiende NewPipe. */
    fun parseAlbumPlaylistId(albumRoot: JSONObject): String? {
        val eps = ArrayList<JSONObject>()
        collect(albumRoot, "watchEndpoint", eps)
        collect(albumRoot, "watchPlaylistEndpoint", eps)
        eps.map { it.optString("playlistId") }.firstOrNull { it.startsWith("OLAK5uy_") }?.let { return it }
        return Regex("""OLAK5uy_[A-Za-z0-9_-]+""").find(albumRoot.toString())?.value
    }

    // ---------- Inicio (FEmusic_home) ----------

    /** Un estante del Inicio de YouTube Music: canciones y/o álbumes y listas. */
    class FeedShelf(
        val title: String,
        val songs: List<SongUi>,
        val albums: List<AlbumUi>,
        /** true si YouTube lo manda como filas de canciones (Selecciones rápidas): se muestra en columnas de 3. */
        val compact: Boolean = false,
        /** Nombre de la cuenta que YouTube pone sobre el título en estantes personalizados (p. ej. "Accesos directos"). */
        val strapline: String? = null,
        /** true si es el estante "Accesos directos" (Speed dial): se muestra en páginas de 3 x 3 portadas. */
        val speedDial: Boolean = false
    )

    /** Categoría de "Estados de ánimo y géneros" de YouTube Music (Los 70, Pop latino, Hip hop...). */
    class ExploreCategory(val title: String, val browseId: String, val params: String, val color: Long?)

    /** Todas las categorías de "Explorar" (estados de ánimo y géneros), con el color que les da YouTube. */
    fun exploreCategories(hl: String, gl: String): List<ExploreCategory> {
        val root = post("browse", baseBody(hl, gl, false).put("browseId", "FEmusic_moods_and_genres"))
        val buttons = ArrayList<JSONObject>()
        collect(root, "musicNavigationButtonRenderer", buttons)
        return buttons.mapNotNull { b ->
            val e = b.obj("clickCommand", "browseEndpoint") ?: return@mapNotNull null
            val params = e.optString("params")
            val title = b.obj("buttonText").runsText().trim()
            if (params.isBlank() || title.isBlank()) return@mapNotNull null
            val color = b.optJSONObject("solid")?.optLong("leftStripeColor", 0L)?.takeIf { it != 0L }
            ExploreCategory(title, e.optString("browseId").ifBlank { "FEmusic_moods_and_genres_category" }, params, color)
        }.distinctBy { it.params }
    }

    /** Listas y estantes de una categoría de Explorar. */
    fun categoryFeed(cat: ExploreCategory, hl: String, gl: String): List<FeedShelf> {
        val root = post(
            "browse",
            baseBody(hl, gl, false).put("browseId", cat.browseId).put("params", cat.params)
        )
        return parseFeed(root, includeGrids = true).distinctBy { it.title }
    }

    /** Chip de la parte de arriba del Inicio de YouTube Music (Relajar, Energizar, Entrenar...). */
    class FeedChip(val label: String, val browseId: String, val params: String)

    private fun parseChips(root: JSONObject): List<FeedChip> {
        val found = ArrayList<JSONObject>()
        collect(root, "chipCloudChipRenderer", found)
        return found.mapNotNull { chip ->
            val e = chip.obj("navigationEndpoint", "browseEndpoint") ?: return@mapNotNull null
            val params = e.optString("params")
            val label = chip.obj("text").runsText().trim()
            if (params.isBlank() || label.isBlank()) return@mapNotNull null
            FeedChip(label, e.optString("browseId").ifBlank { "FEmusic_home" }, params)
        }.distinctBy { it.params }
    }

    /** Estantes que YouTube Music muestra al tocar un chip del Inicio. */
    fun chipFeed(chip: FeedChip, hl: String, gl: String, android: Boolean = false): List<FeedShelf> {
        val root = post(
            "browse",
            baseBody(hl, gl, android).put("browseId", chip.browseId).put("params", chip.params),
            android = android
        )
        return parseFeed(root).distinctBy { it.title }
    }

    /**
     * Inicio de YouTube Music (FEmusic_home): los estantes que arma el algoritmo de YouTube.
     * Sin cuenta iniciada no es personal: depende de la región ([gl]) y del idioma ([hl]).
     * [android] usa el cliente ANDROID_MUSIC en lugar de WEB_REMIX.
     */
    fun home(
        hl: String,
        gl: String,
        android: Boolean = false,
        /** Se llama con todos los estantes acumulados cada vez que llega una página nueva. */
        onProgress: (List<FeedShelf>) -> Unit = {},
        /** Se llama una vez con los chips del Inicio (Relajar, Energizar...), si YouTube los manda. */
        onChips: (List<FeedChip>) -> Unit = {}
    ): List<FeedShelf> {
        val out = ArrayList<FeedShelf>()
        val first = post("browse", baseBody(hl, gl, android).put("browseId", "FEmusic_home"), android = android)
        parseChips(first).takeIf { it.isNotEmpty() }?.let(onChips)
        out += parseFeed(first)
        if (out.isNotEmpty()) onProgress(out.distinctBy { it.title })
        Log.d(TAG, "página 1 (android=$android): ${out.size} estantes, token=${continuationToken(first)?.length}")

        // Siguientes páginas del Inicio
        var token = continuationToken(first)
        var pages = 1
        while (!token.isNullOrBlank() && pages < 6 && out.size < 20) {
            val next = fetchContinuation(token, hl, gl, android) ?: break
            val shelves = parseFeed(next)
            out += shelves
            onProgress(out.distinctBy { it.title })
            token = continuationToken(next)
            pages++
            Log.d(TAG, "página $pages: +${shelves.size} estantes, token=${token?.length}")
        }

        // Si aun así hay pocos, los estantes de cada chip de ánimo (Energizar, Relajar...) que YouTube ofrece
        if (out.size < 8) {
            val chips = ArrayList<JSONObject>()
            collect(first, "chipCloudChipRenderer", chips)
            Log.d(TAG, "chips encontrados: ${chips.size}")
            var used = 0
            for (chip in chips) {
                if (used >= 4) break
                val e = chip.obj("navigationEndpoint", "browseEndpoint") ?: continue
                val params = e.optString("params")
                if (params.isBlank()) continue
                val id = e.optString("browseId").ifBlank { "FEmusic_home" }
                try {
                    val root = post(
                        "browse",
                        baseBody(hl, gl, android).put("browseId", id).put("params", params),
                        android = android
                    )
                    val shelves = parseFeed(root)
                    Log.d(TAG, "chip '${chip.obj("text").runsText()}': ${shelves.size} estantes")
                    out += shelves
                    onProgress(out.distinctBy { it.title })
                    used++
                } catch (ex: IOException) {
                    Log.w(TAG, "chip falló: ${ex.message}")
                }
            }
        }
        return out.distinctBy { it.title }
    }

    private fun continuationToken(root: JSONObject): String? =
        findFirst(root, "nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
            ?: findFirst(root, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }

    /**
     * Pide la siguiente página probando las formas que usan YouTube Music y ytmusicapi.
     * El token ya viene codificado por YouTube (termina en %3D...), no se vuelve a codificar.
     */
    private fun fetchContinuation(token: String, hl: String, gl: String, android: Boolean): JSONObject? {
        val attempts: List<() -> JSONObject> = listOf(
            {
                post(
                    "browse", baseBody(hl, gl, android).put("browseId", "FEmusic_home"),
                    query = "&ctoken=$token&continuation=$token", android = android
                )
            },
            {
                post(
                    "browse", baseBody(hl, gl, android),
                    query = "&ctoken=$token&continuation=$token&type=next", android = android
                )
            },
            { post("browse", baseBody(hl, gl, android).put("continuation", token), android = android) }
        )
        for ((i, attempt) in attempts.withIndex()) {
            try {
                val root = attempt()
                if (parseFeed(root).isNotEmpty()) return root
                Log.w(TAG, "continuación intento ${i + 1}: respuesta sin estantes")
            } catch (e: IOException) {
                Log.w(TAG, "continuación intento ${i + 1} falló: ${e.message}")
            }
        }
        return null
    }

    private fun parseFeed(root: JSONObject, includeGrids: Boolean = false): List<FeedShelf> {
        val carousels = ArrayList<JSONObject>()
        collect(root, "musicCarouselShelfRenderer", carousels)
        val blocks = ArrayList<Triple<String, org.json.JSONArray, String>>()
        for (car in carousels) {
            val title = car.obj("header", "musicCarouselShelfBasicHeaderRenderer", "title").runsText()
            if (title.isBlank()) continue
            val contents = car.optJSONArray("contents") ?: continue
            val strap = car.obj("header", "musicCarouselShelfBasicHeaderRenderer", "strapline").runsText().trim()
            blocks.add(Triple(title, contents, strap))
        }
        if (includeGrids) {
            // Las páginas de categoría (Estados de ánimo y géneros) también traen listas en cuadrículas
            val grids = ArrayList<JSONObject>()
            collect(root, "gridRenderer", grids)
            for (g in grids) {
                val title = g.obj("header", "gridHeaderRenderer", "title").runsText().ifBlank { "Listas" }
                val items = g.optJSONArray("items") ?: continue
                blocks.add(Triple(title, items, ""))
            }
        }
        val shelves = ArrayList<FeedShelf>()
        for ((title, contents, strap) in blocks) {

            val songs = ArrayList<SongUi>()
            val albums = ArrayList<AlbumUi>()
            var fromRows = false
            for (i in 0 until contents.length()) {
                val c = contents.optJSONObject(i) ?: continue
                c.optJSONObject("musicResponsiveListItemRenderer")?.let { row ->
                    val song = parseFeedSongRow(row)
                    if (song != null) {
                        songs.add(song)
                        fromRows = true
                    }
                }
                c.optJSONObject("musicTwoRowItemRenderer")?.let { t ->
                    val song = parseFeedTwoRowSong(t)
                    if (song != null) {
                        songs.add(song)
                    } else {
                        val album = parseFeedAlbum(t)
                        if (album != null) albums.add(album)
                    }
                }
            }
            val s = songs.distinctBy { it.url }
            val a = albums.distinctBy { it.url }
            val low = title.lowercase()
            val dial = low.contains("accesos directos") || low.contains("speed dial")
            if (s.size + a.size >= 2) {
                shelves.add(
                    FeedShelf(
                        title, s, a,
                        compact = fromRows && a.isEmpty(),
                        strapline = strap.ifBlank { null },
                        speedDial = dial
                    )
                )
            }
        }
        Log.d(TAG, "estantes del feed: " + shelves.joinToString(" | ") { it.title + if (it.speedDial) " [accesos directos]" else "" })
        return shelves
    }

    private val TYPE_WORDS = setOf(
        "song", "video", "canción", "cancion", "vídeo", "single", "sencillo",
        "album", "álbum", "ep", "playlist", "lista", "lista de reproducción"
    )

    /** Artistas de una línea de subtítulo: los textos que enlazan a un artista; si no, el primer texto útil. */
    private fun artistFromRuns(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return ""
        val names = ArrayList<String>()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val e = r.obj("navigationEndpoint", "browseEndpoint") ?: continue
            val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            if (type == "MUSIC_PAGE_TYPE_ARTIST" || type == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                e.optString("browseId").startsWith("UC")
            ) names.add(r.optString("text"))
        }
        if (names.isNotEmpty()) return names.joinToString(", ")
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
            if (t.isNotEmpty() && t != "•" && t.lowercase() !in TYPE_WORDS && !t.any { it.isDigit() }) return t
        }
        return ""
    }

    private fun badMusicType(item: JSONObject): Boolean {
        val type = findFirst(item, "watchEndpointMusicConfig")?.optString("musicVideoType").orEmpty()
        return type == "MUSIC_VIDEO_TYPE_UGC" || type.contains("PODCAST")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
    }

    private fun parseFeedSongRow(r: JSONObject): SongUi? {
        if (badMusicType(r)) return null
        val vid = (r.obj(
            "overlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint"
        ) ?: findFirst(r, "watchEndpoint"))?.optString("videoId").orEmpty()
        val title = r.obj("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
        if (vid.isBlank() || title.isBlank()) return null
        return SongUi(
            url = "https://www.youtube.com/watch?v=$vid",
            title = title,
            artist = artistFromRuns(r.obj("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text"))
                .ifBlank { "Desconocido" },
            durationSec = 0,
            thumb = bestThumb(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        )
    }

    private fun parseFeedTwoRowSong(t: JSONObject): SongUi? {
        val vid = t.obj("navigationEndpoint", "watchEndpoint")?.optString("videoId").orEmpty()
        if (vid.isBlank() || badMusicType(t)) return null
        val title = t.obj("title").runsText()
        if (title.isBlank()) return null
        return SongUi(
            url = "https://www.youtube.com/watch?v=$vid",
            title = title,
            artist = artistFromRuns(t.obj("subtitle")).ifBlank { "Desconocido" },
            durationSec = 0,
            thumb = bestThumb(t.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        )
    }

    /**
     * Álbumes y listas de reproducción del Inicio (incluidas las listas de YouTube Music tipo RDCLAK...,
     * que NewPipe no abre): se muestran con la misma pantalla de álbum.
     */
    private fun parseFeedAlbum(t: JSONObject): AlbumUi? {
        val e = t.obj("navigationEndpoint", "browseEndpoint") ?: return null
        val browseId = e.optString("browseId")
        val type = findFirst(e, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()

        val eps = ArrayList<JSONObject>()
        collect(t, "watchPlaylistEndpoint", eps)
        collect(t, "watchEndpoint", eps)
        val ids = eps.map { it.optString("playlistId") }

        val url: String = when {
            type == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") -> {
                val olak = ids.firstOrNull { it.startsWith("OLAK5uy_") }
                if (olak != null) "https://www.youtube.com/playlist?list=$olak" else "$YTM_ALBUM$browseId"
            }
            browseId.startsWith("VLPL") || browseId.startsWith("VLOLAK5uy_") ->
                "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}"
            browseId.startsWith("VL") -> "$YTM_PLAYLIST${browseId.removePrefix("VL")}"
            else -> return null // artistas, géneros, etc.
        }

        val name = t.obj("title").runsText()
        if (name.isBlank()) return null
        return AlbumUi(
            url = url,
            name = name,
            artist = artistFromRuns(t.obj("subtitle")),
            artistUrl = null,
            thumb = bestThumb(t.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            subtitle = t.obj("subtitle").runsText().ifBlank { null }
        )
    }

    /** Canciones de una página de álbum o lista de YouTube Music (respuesta de browse MPREb... / VL...). */
    fun parsePageSongs(root: JSONObject): List<SongUi> {
        val header = findFirst(root, "musicResponsiveHeaderRenderer")
            ?: findFirst(root, "musicDetailHeaderRenderer")
            ?: findFirst(root, "musicEditablePlaylistDetailHeaderRenderer")
        val cover = header?.let { h ->
            bestThumb(findFirst(h, "musicThumbnailRenderer")?.arr("thumbnail", "thumbnails"))
        }
        val headArtist = artistFromRuns(header?.obj("straplineTextOne"))
            .ifBlank { artistFromRuns(header?.obj("subtitle")) }

        val shelf = findFirst(root, "musicPlaylistShelfRenderer") ?: findFirst(root, "musicShelfRenderer")
            ?: return emptyList()
        val rows = ArrayList<JSONObject>()
        collect(shelf, "musicResponsiveListItemRenderer", rows)

        val out = ArrayList<SongUi>()
        for (r in rows) {
            val vid = (r.obj(
                "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint"
            ) ?: findFirst(r, "watchEndpoint"))?.optString("videoId").orEmpty()
            val title = r.obj("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
            if (vid.isBlank() || title.isBlank()) continue
            out.add(
                SongUi(
                    url = "https://www.youtube.com/watch?v=$vid",
                    title = title,
                    artist = artistFromRuns(r.obj("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text"))
                        .ifBlank { headArtist }.ifBlank { "Desconocido" },
                    durationSec = parseDuration(
                        r.obj("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text").runsText()
                    ),
                    thumb = bestThumb(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")) ?: cover
                )
            )
        }
        return out.distinctBy { it.url }
    }

    // ---------- Radio (canciones parecidas) ----------

    /** Un elemento de la radio. musicVideoType: ATV = pista de audio oficial, OMV = video musical oficial, UGC = subido por usuarios. */
    class RadioItem(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationSec: Long,
        val thumb: String?,
        val musicVideoType: String
    )

    /** Radio automática de YouTube Music para un video (la misma que usa su app al "iniciar radio"). */
    fun radio(
        videoId: String,
        continuation: String? = null,
        playlistId: String = "RDAMVM$videoId",
        params: String? = "wAEB"
    ): JSONObject {
        val body = baseBody()
            .put("videoId", videoId)
            .put("playlistId", playlistId)
            .apply { if (params != null) put("params", params) }
            .put("isAudioOnly", true)
            .put("enablePersistentPlaylistPanel", true)
            .put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        if (continuation != null) body.put("continuation", continuation)
        return post("next", body)
    }

    /** Token para pedir la siguiente página de la radio (como hace Metrolist con `continuation`). */
    fun radioContinuation(root: JSONObject): String? {
        val panel = findFirst(root, "playlistPanelContinuation")
            ?: findFirst(root, "playlistPanelRenderer")
            ?: return null
        val data = findFirst(panel, "nextRadioContinuationData")
            ?: findFirst(panel, "nextContinuationData")
        return data?.optString("continuation")?.takeIf { it.isNotBlank() }
    }

    /** Destino del "automix": lo que YouTube sugiere seguir cuando la radio se acaba (como Metrolist). */
    class RadioAutomix(val videoId: String?, val playlistId: String, val params: String?)

    fun radioAutomix(root: JSONObject): RadioAutomix? {
        val node = findFirst(root, "automixPreviewVideoRenderer") ?: return null
        val ep = findFirst(node, "watchPlaylistEndpoint") ?: return null
        val pid = ep.optString("playlistId").takeIf { it.isNotBlank() } ?: return null
        return RadioAutomix(
            videoId = ep.optString("videoId").takeIf { it.isNotBlank() },
            playlistId = pid,
            params = ep.optString("params").takeIf { it.isNotBlank() }
        )
    }

    fun parseRadio(root: JSONObject): List<RadioItem> {
        val items = ArrayList<JSONObject>()
        collect(root, "playlistPanelVideoRenderer", items)
        val out = ArrayList<RadioItem>()
        for (it in items) {
            val vid = it.optString("videoId")
            val title = it.obj("title").runsText()
            if (vid.isBlank() || title.isBlank()) continue

            // longBylineText: "Artista(s) • Álbum • Año" -> solo lo anterior al primer separador
            val runs = it.obj("longBylineText")?.optJSONArray("runs")
            val artist = StringBuilder()
            if (runs != null) {
                for (i in 0 until runs.length()) {
                    val t = runs.optJSONObject(i)?.optString("text").orEmpty()
                    if (t.trim() == "•") break
                    artist.append(t)
                }
            }
            out.add(
                RadioItem(
                    videoId = vid,
                    title = title,
                    artist = artist.toString().trim(),
                    durationSec = parseDuration(it.obj("lengthText").runsText()),
                    thumb = bestThumb(it.arr("thumbnail", "thumbnails")),
                    musicVideoType = findFirst(it, "watchEndpointMusicConfig")
                        ?.optString("musicVideoType").orEmpty()
                )
            )
        }
        return out
    }

    /** "3:45" -> 225; "1:02:03" -> 3723; vacío -> 0. */
    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 0
        return text.trim().split(":").fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: return 0) }
    }

    // ---------- Artista ----------

    fun parseArtist(root: JSONObject, artist: ArtistUi): ParsedArtist {
        val sections = root.arr(
            "contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer",
            "content", "sectionListRenderer", "contents"
        ) ?: JSONArray()

        var topSongs: List<SongUi> = emptyList()
        var songsUrl: String? = null
        var albums: List<AlbumUi> = emptyList()
        var albumsMore: MoreRef? = null
        var singles: List<AlbumUi> = emptyList()
        var singlesMore: MoreRef? = null

        for (i in 0 until sections.length()) {
            val sec = sections.optJSONObject(i) ?: continue

            val shelf = sec.optJSONObject("musicShelfRenderer")
            if (shelf != null) {
                if (topSongs.isEmpty()) {
                    topSongs = parseSongShelf(shelf, artist)
                    val bid = findFirst(shelf, "bottomEndpoint")
                        ?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                    if (bid.startsWith("VL")) {
                        songsUrl = "https://www.youtube.com/playlist?list=${bid.removePrefix("VL")}"
                    }
                }
                continue
            }

            val carousel = sec.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val header = carousel.obj("header", "musicCarouselShelfBasicHeaderRenderer")
            val title = header?.obj("title").runsText().lowercase()
            val isSingles = title.contains("single")
            val isAlbums = !isSingles && title.contains("album")
            if (!isSingles && !isAlbums) continue // videos, "te puede gustar", etc.

            val items = ArrayList<JSONObject>()
            collect(carousel.optJSONArray("contents"), "musicTwoRowItemRenderer", items)
            val releases = items.mapNotNull { parseRelease(it, artist) }.distinctBy { it.url }

            val more = header?.obj("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint")
                ?.let { e ->
                    val id = e.optString("browseId")
                    if (id.isBlank()) null else MoreRef(id, e.optString("params").ifBlank { null })
                }

            if (isSingles) {
                singles = releases; singlesMore = more
            } else {
                albums = releases; albumsMore = more
            }
        }

        // Suscriptores de la ficha de YouTube Music
        val subsText = findFirst(root, "subscribeButtonRenderer")?.let { b ->
            b.obj("subscriberCountText").runsText()
                .ifBlank { b.obj("longSubscriberCountText").runsText() }
        }.orEmpty()

        val description = findFirst(root, "musicImmersiveHeaderRenderer")
            ?.obj("description").runsText().ifBlank { null }

        return ParsedArtist(
            subscribers = if (subsText.isBlank()) -1L else parseCount(subsText),
            description = description,
            topSongs = topSongs,
            songsPlaylistUrl = songsUrl,
            albums = albums,
            albumsMore = albumsMore,
            singles = singles,
            singlesMore = singlesMore
        )
    }

    /** Discografía completa ("ver todo" del carrusel); si no hay enlace o no mejora, deja la lista corta. */
    fun fullReleases(more: MoreRef?, artist: ArtistUi, fallback: List<AlbumUi>): List<AlbumUi> {
        if (more == null) return fallback
        val root = browse(more.browseId, more.params)
        val items = ArrayList<JSONObject>()
        collect(root, "musicTwoRowItemRenderer", items)
        val list = items.mapNotNull { parseRelease(it, artist) }.distinctBy { it.url }
        return if (list.size > fallback.size) list else fallback
    }

    private fun parseSongShelf(shelf: JSONObject, artist: ArtistUi): List<SongUi> {
        val contents = shelf.optJSONArray("contents") ?: return emptyList()
        val out = ArrayList<SongUi>()
        for (i in 0 until contents.length()) {
            val r = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val vid = (r.obj(
                "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint"
            ) ?: findFirst(r, "watchEndpoint"))?.optString("videoId").orEmpty()
            if (vid.isBlank()) continue

            val title = r.obj("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
            if (title.isBlank()) continue
            val by = r.obj("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()

            out.add(
                SongUi(
                    url = "https://www.youtube.com/watch?v=$vid",
                    title = title,
                    artist = by.ifBlank { artist.name },
                    durationSec = 0,
                    thumb = bestThumb(r.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
                )
            )
        }
        return out
    }

    private fun parseRelease(item: JSONObject, artist: ArtistUi): AlbumUi? {
        val type = findFirst(item, "browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
        if (type.isNotEmpty() && type != "MUSIC_PAGE_TYPE_ALBUM") return null

        // Ojo: el menú trae listas de "radio" (RDAMPL...). La lista del álbum es la OLAK5uy_...
        val endpoints = ArrayList<JSONObject>()
        collect(item, "watchPlaylistEndpoint", endpoints)
        val pid = endpoints.map { it.optString("playlistId") }.firstOrNull { it.startsWith("OLAK5uy_") }
            ?: return null

        val name = item.obj("title").runsText()
        if (name.isBlank()) return null

        return AlbumUi(
            url = "https://www.youtube.com/playlist?list=$pid",
            name = name,
            artist = artist.name,
            artistUrl = artist.url,
            thumb = bestThumb(item.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        )
    }

    // ---------- Utilidades de JSON ----------

    /** "45.3M subscribers" -> 45_300_000; "1,234" -> 1234; "820K" -> 820_000. */
    internal fun parseCount(text: String): Long {
        val m = Regex("""([0-9][0-9.,]*)\s*([KMB])?""", RegexOption.IGNORE_CASE).find(text) ?: return -1L
        val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return -1L
        val mult = when (m.groupValues[2].uppercase()) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
        return (value * mult).toLong()
    }

    private fun bestThumb(arr: JSONArray?, target: Int = 400): String? {
        if (arr == null || arr.length() == 0) return null
        val list = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("url").isNotBlank() }
        if (list.isEmpty()) return null
        val sorted = list.sortedBy { it.optInt("height") }
        return (sorted.firstOrNull { it.optInt("height") >= target } ?: sorted.last()).optString("url")
    }

    private fun JSONObject.at(vararg path: Any): Any? {
        var cur: Any? = this
        for (k in path) {
            cur = when (k) {
                is String -> (cur as? JSONObject)?.opt(k)
                is Int -> (cur as? JSONArray)?.opt(k)
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }

    private fun JSONObject.obj(vararg path: Any): JSONObject? = at(*path) as? JSONObject
    private fun JSONObject.arr(vararg path: Any): JSONArray? = at(*path) as? JSONArray

    private fun JSONObject?.runsText(): String {
        val runs = this?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    /** Primer objeto que cuelga de la clave [key], buscando en profundidad. */
    private fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findFirst(node.opt(keys.next()), key)?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                findFirst(node.opt(i), key)?.let { return it }
            }
        }
        return null
    }

    /** Todos los objetos que cuelgan de la clave [key], en orden. */
    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { out.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k != key) collect(node.opt(k), key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }
}
