package com.lumodsound.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingMusicLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private const val INFO_TTL_MS = 90L * 60 * 1000
private const val INFO_MAX_ENTRIES = 20
private val infoCache = ConcurrentHashMap<String, Pair<StreamInfo, Long>>()
private val infoLocks = ConcurrentHashMap<String, Any>()

/** Elige la portada más pequeña que aún se ve bien (>= 400 px): menos datos, menos CPU, menos batería. */
private fun List<Image>.bestThumb(target: Int = 400): String? {
    if (isEmpty()) return null
    val sorted = sortedBy { it.height }
    return (sorted.firstOrNull { it.height >= target } ?: sorted.last()).url
}

/** Audio ya resuelto: URL directa (caduca) + clave estable para la caché de disco. */
private val albumSongsCache = ConcurrentHashMap<String, List<SongUi>>()
/** Álbum de cada canción ya consultada; lista vacía = la canción no tiene álbum. */
private val songAlbumCache = ConcurrentHashMap<String, List<AlbumUi>>()

private inline fun <T> safe(default: T, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        default
    }

/** Marcas de versiones que no son la canción original (remix, karaoke, en vivo, covers, promos...). */
private val BAD_TITLE = Regex(
    """\b(remix|rmx|mashup|karaoke|nightcore|sped up|slowed|reverb|8d audio|bass boosted|type beat|""" +
        """official video|video oficial|lyric video|lyrics video|trailer|commercial|comercial|teaser|""" +
        """reaction|tutorial|full album|unplugged|en vivo)\b""" +
        """|\blive (at|from|in|on|session|version|performance)\b""" +
        """|[(\[][^)\]]*\b(live|cover|tribute|instrumental|soundtrack|ost)\b""",
    RegexOption.IGNORE_CASE
)

private fun badTags(text: String): Set<String> =
    BAD_TITLE.findAll(text).map { it.value.lowercase() }.toSet()

/** Un título pasa si no trae marcas de versión, o si la canción de origen ya las traía. */
private fun allowedByTitle(title: String, seedTitle: String?): Boolean {
    val tags = badTags(title)
    if (tags.isEmpty()) return true
    val seedTags = seedTitle?.let { badTags(it) }.orEmpty()
    return seedTags.containsAll(tags)
}

/** Para comparar nombres de artista sin importar mayúsculas, acentos sueltos o signos. */
private fun String.normalizeName(): String =
    lowercase()
        .replace(Regex("""\s*-\s*topic$"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

data class ResolvedAudio(val url: String, val key: String, val itag: Int)

/** Calidad concreta con la que se pide el audio (el ajuste "Automática" la resuelve según la red). */
enum class StreamQuality { HIGH, MEDIUM, LOW }

data class SongUi(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumb: String?
)

class SearchCursor(val handler: SearchQueryHandler, val nextPage: Page)

data class SearchPage(val items: List<SongUi>, val cursor: SearchCursor?)

class YtRepository {

    private val service = ServiceList.YouTube

    /** Busca canciones (filtro "YouTube Music: canciones"). */
    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        val info = SearchInfo.getInfo(service, handler)
        val next = info.nextPage
        SearchPage(
            items = info.relatedItems.toSongs(),
            cursor = if (next != null) SearchCursor(handler, next) else null
        )
    }

    /** Siguiente página de resultados. */
    suspend fun more(cursor: SearchCursor): SearchPage = withContext(Dispatchers.IO) {
        val page = SearchInfo.getMoreItems(service, cursor.handler, cursor.nextPage)
        val next = page.nextPage
        SearchPage(
            items = page.items.toSongs(),
            cursor = if (next != null) SearchCursor(cursor.handler, next) else null
        )
    }

    private fun freshInfo(videoUrl: String): StreamInfo? {
        val cached = infoCache[videoUrl] ?: return null
        return if (System.currentTimeMillis() - cached.second < INFO_TTL_MS) cached.first else null
    }

    /**
     * StreamInfo de un video con caché de 90 min. El bloqueo es POR VIDEO: el reproductor (audio)
     * y la radio (relacionados) de la misma canción comparten una sola petición, pero cargar
     * recomendaciones del Inicio ya no hace esperar a la canción que quieres reproducir.
     */
    private fun streamInfo(videoUrl: String): StreamInfo {
        freshInfo(videoUrl)?.let { return it }
        val lock = infoLocks.computeIfAbsent(videoUrl) { Any() }
        synchronized(lock) {
            freshInfo(videoUrl)?.let { return it }
            val info = StreamInfo.getInfo(service, videoUrl)
            infoCache[videoUrl] = info to System.currentTimeMillis()
            if (infoCache.size > INFO_MAX_ENTRIES) {
                infoCache.entries.minByOrNull { it.value.second }?.let { infoCache.remove(it.key) }
            }
            return info
        }
    }

    /** Flujos de audio utilizables: de descarga directa, de la pista original (no doblada). */
    private fun audioCandidates(info: StreamInfo): List<AudioStream> {
        val all = info.audioStreams.filter { it.isUrl }
        val progressive = all.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty { all }
        // Evita pistas de audio doblado automáticamente
        return pool.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { pool }
    }

    /** Mejor audio disponible: M4A primero y, dentro de eso, el de mayor calidad. */
    private fun pickBestAudio(info: StreamInfo): AudioStream? =
        audioCandidates(info).sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate }
        ).firstOrNull()

    /** El audio que pide el ajuste de calidad: la mejor, o la más cercana a ~128 kbps (media) o la más ligera (baja). */
    private fun pickAudio(info: StreamInfo, quality: StreamQuality): AudioStream? {
        val best = pickBestAudio(info)
        if (quality == StreamQuality.HIGH || best == null) return best
        val withRate = audioCandidates(info).filter { it.averageBitrate > 0 }
        val pool = withRate.filter { it.format == MediaFormat.M4A }.ifEmpty { withRate }
        if (pool.isEmpty()) return best
        return when (quality) {
            StreamQuality.LOW -> pool.minByOrNull { it.averageBitrate }
            StreamQuality.MEDIUM -> pool.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
            StreamQuality.HIGH -> best
        } ?: best
    }

    /** Información de una canción: álbum, año, duración, calidad del audio y datos del video. */
    suspend fun songInfo(song: SongUi): SongInfo = coroutineScope {
        val vid = song.url.substringAfter("v=", "").substringBefore('&')
        val metaD = async(Dispatchers.IO) {
            safe<YtMusicApi.SongMeta?>(null) {
                if (vid.isBlank()) null else YtMusicApi.parseSongMeta(YtMusicApi.next(vid))
            }
        }
        val albumD = async { safe<AlbumUi?>(null) { albumOfSong(song) } }
        val streamD = async(Dispatchers.IO) { safe<StreamInfo?>(null) { streamInfo(song.url) } }

        val meta = metaD.await()
        val album = albumD.await()
        val stream = streamD.await()
        val audio = stream?.let { pickBestAudio(it) }

        val format = when (audio?.format) {
            null -> null
            MediaFormat.M4A -> "M4A (AAC)"
            MediaFormat.WEBMA_OPUS -> "WebM (Opus)"
            MediaFormat.WEBMA -> "WebM (Vorbis)"
            else -> audio.format?.suffix?.uppercase()
        }
        var kbps = audio?.averageBitrate ?: 0
        if (kbps > 1000) kbps /= 1000 // por si viene en bits por segundo

        SongInfo(
            title = song.title,
            artist = song.artist,
            album = album,
            albumName = album?.name ?: meta?.albumRef?.name,
            year = meta?.year,
            durationSec = when {
                song.durationSec > 0 -> song.durationSec
                (stream?.duration ?: 0L) > 0 -> stream!!.duration
                else -> meta?.durationSec ?: 0L
            },
            audioFormat = format,
            audioCodec = audio?.codec?.takeIf { it.isNotBlank() },
            bitrateKbps = kbps,
            channel = stream?.uploaderName?.takeIf { it.isNotBlank() },
            views = stream?.viewCount ?: -1L,
            videoId = vid,
            url = song.url
        )
    }

    /** Audio de un video (bloqueante: lo usa el hilo de carga del reproductor y la precarga). */
    fun resolveAudioBlocking(videoUrl: String, quality: StreamQuality = StreamQuality.HIGH): ResolvedAudio {
        val info = streamInfo(videoUrl)
        val best = pickAudio(info, quality) ?: throw IllegalStateException(str(R.string.this_video_has_no_audio))

        // La clave es solo la canción (como Metrolist): así se puede saber si está en disco sin resolver
        // nada. El formato (itag) se anota aparte para no mezclar bytes de formatos distintos.
        return ResolvedAudio(best.content, videoUrl, best.itag)
    }

    // ---------- Artistas y álbumes ----------

    /** [pages] páginas de resultados (la primera trae ~20; para álbumes conviene pedir 2). */
    private fun searchRaw(query: String, filter: String, pages: Int = 1): List<InfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        val items = ArrayList<InfoItem>(info.relatedItems)
        var next = info.nextPage
        var page = 1
        while (page < pages && next != null) {
            val more = try {
                SearchInfo.getMoreItems(service, handler, next)
            } catch (e: Exception) {
                break // con lo que ya tenemos basta
            }
            items.addAll(more.items)
            next = more.nextPage
            page++
        }
        return items
    }

    /** Reintenta una vez si falla; devuelve null si no se pudo, para distinguir "falló" de "no hay nada". */
    private suspend fun searchWithRetry(query: String, filter: String, pages: Int = 1): List<InfoItem>? {
        repeat(2) { attempt ->
            try {
                return withContext(Dispatchers.IO) { searchRaw(query, filter, pages) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0) delay(700)
            }
        }
        return null
    }

    /** Álbumes, sencillos y EPs que coinciden con la búsqueda (YouTube Music: filtro "Álbumes"). */
    suspend fun searchAlbums(query: String): List<AlbumUi> = withContext(Dispatchers.IO) {
        searchRaw(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, pages = 2)
            .filterIsInstance<PlaylistInfoItem>()
            .map { item ->
                AlbumUi(
                    url = item.url,
                    name = item.name ?: "",
                    artist = (item.uploaderName ?: "").removeSuffix(" - Topic"),
                    artistUrl = item.uploaderUrl,
                    thumb = item.thumbnails.bestThumb(),
                    trackCount = albumSongsCache[item.url]?.size
                        ?: item.streamCount.toInt().takeIf { it > 0 }
                )
            }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url }
            .take(30)
    }

    /** Artistas que coinciden con la búsqueda (YouTube Music: filtro "Artistas"). */
    /** Sugerencias al escribir en la barra de búsqueda (consultas y resultados directos). */
    suspend fun searchSuggestions(query: String): YtMusicApi.SearchSuggestions = withContext(Dispatchers.IO) {
        YtMusicApi.searchSuggestions(query)
    }

    suspend fun searchArtists(query: String): List<ArtistUi> = withContext(Dispatchers.IO) {
        searchRaw(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS)
            .filterIsInstance<ChannelInfoItem>()
            .map { ArtistUi(it.url, it.name ?: "", it.thumbnails.bestThumb(), it.subscriberCount) }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .take(6)
    }

    /**
     * Pantalla de artista con los datos reales de YouTube Music (canciones top, discografía completa
     * separada en álbumes y sencillos, suscriptores de la ficha). Si esa API falla o cambia de formato,
     * cae al método anterior basado en búsquedas.
     */
    suspend fun artistPage(artist: ArtistUi): ArtistPage =
        try {
            musicArtistPage(artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            legacyArtistPage(artist)
        }

    private suspend fun musicArtistPage(artist: ArtistUi): ArtistPage = coroutineScope {
        val id = artist.url.substringAfter("/channel/", "").substringBefore('?').substringBefore('/')
        if (id.isBlank()) throw IllegalStateException(str(R.string.artist_without_browseid))

        val root = withContext(Dispatchers.IO) { YtMusicApi.browse(id) }
        val parsed = YtMusicApi.parseArtist(root, artist)
        if (parsed.topSongs.isEmpty() && parsed.albums.isEmpty() && parsed.singles.isEmpty()) {
            throw IllegalStateException(str(R.string.empty_artist_page))
        }

        // Lista completa de canciones y discografía completa, en paralelo; si algo falla se usa lo corto
        val songsD = async(Dispatchers.IO) {
            safe<List<SongUi>?>(null) {
                parsed.songsPlaylistUrl?.let { PlaylistInfo.getInfo(service, it).relatedItems.toSongs() }
            }
        }
        val albumsD = async(Dispatchers.IO) {
            safe(parsed.albums) { YtMusicApi.fullReleases(parsed.albumsMore, artist, parsed.albums) }
        }
        val singlesD = async(Dispatchers.IO) {
            safe(parsed.singles) { YtMusicApi.fullReleases(parsed.singlesMore, artist, parsed.singles) }
        }

        val full = songsD.await().orEmpty()
        val byUrl = full.associateBy { it.url }
        // Las 5 top van primero (orden real), con duración si la lista completa la trae
        val popular = (parsed.topSongs.map { byUrl[it.url] ?: it } + full)
            .distinctBy { it.url }
            .take(20)

        val albums = albumsD.await()
        val singles = singlesD.await()
        val photo = artist.thumb

        ArtistPage(
            artist = artist.copy(thumb = photo, subscribers = parsed.subscribers),
            hero = photo,
            description = parsed.description,
            subscribers = parsed.subscribers,
            popular = popular,
            releases = emptyList(),
            popularFailed = false,
            releasesFailed = false,
            albums = albums,
            singles = singles
        )
    }

    /**
     * Datos de la pantalla de artista, pedidos en paralelo: ficha del canal (foto, banner, suscriptores),
     * canciones populares (búsqueda de canciones filtrada por artista) y lanzamientos (búsqueda de álbumes
     * filtrada por el artista).
     */
    private suspend fun legacyArtistPage(artist: ArtistUi): ArtistPage = coroutineScope {
        val infoD = async(Dispatchers.IO) {
            safe<ChannelInfo?>(null) { ChannelInfo.getInfo(service, artist.url) }
        }
        val songsD = async { searchWithRetry(artist.name, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
        val albumsD = async { searchWithRetry(artist.name, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, pages = 2) }

        val info = infoD.await()
        val songsRaw = songsD.await()
        val albumsRaw = albumsD.await()
        val key = artist.name.normalizeName()

        val popular = (songsRaw ?: emptyList())
            .filterIsInstance<StreamInfoItem>()
            .map { it.toSong() }
            .filter { it.artist.normalizeName().contains(key) }
            .distinctBy { it.url }
            .take(15)

        val releases = (albumsRaw ?: emptyList())
            .filterIsInstance<PlaylistInfoItem>()
            .filter { item ->
                val byUrl = item.uploaderUrl != null && item.uploaderUrl == artist.url
                byUrl || (item.uploaderName ?: "").normalizeName().contains(key)
            }
            .map { item ->
                AlbumUi(
                    url = item.url,
                    name = item.name ?: str(R.string.untitled),
                    artist = item.uploaderName ?: artist.name,
                    artistUrl = item.uploaderUrl,
                    thumb = item.thumbnails.bestThumb(),
                    trackCount = albumSongsCache[item.url]?.size
                )
            }
            .distinctBy { it.url }
            .take(40)

        // La foto del artista (no el banner del canal, que suele ser un diseño genérico)
        val avatar = info?.avatars?.maxByOrNull { it.height }?.url
        val photo = artist.thumb ?: avatar
        ArtistPage(
            artist = artist.copy(thumb = photo),
            hero = photo,
            description = info?.description,
            subscribers = info?.subscriberCount ?: artist.subscribers,
            popular = popular,
            releases = releases,
            popularFailed = songsRaw == null,
            releasesFailed = albumsRaw == null
        )
    }

    /** Álbum al que pertenece una canción, o null si no tiene (videos sueltos, etc.). */
    suspend fun albumOfSong(song: SongUi): AlbumUi? = withContext(Dispatchers.IO) {
        songAlbumCache[song.url]?.let { return@withContext it.firstOrNull() }
        val vid = song.url.substringAfter("v=", "").substringBefore('&')
        if (vid.isBlank()) return@withContext null

        val ref = YtMusicApi.parseAlbumRef(YtMusicApi.next(vid))
        val pid = ref?.let { YtMusicApi.parseAlbumPlaylistId(YtMusicApi.browse(it.browseId)) }
        val album = if (ref != null && pid != null) {
            AlbumUi(
                url = "https://www.youtube.com/playlist?list=$pid",
                name = ref.name,
                artist = song.artist,
                artistUrl = null,
                thumb = song.thumb
            )
        } else null
        if (songAlbumCache.size > 200) songAlbumCache.keys.firstOrNull()?.let { songAlbumCache.remove(it) }
        songAlbumCache[song.url] = listOfNotNull(album)
        album
    }

    /** Canciones de un álbum, sencillo o EP (con caché: al abrirlo después de clasificarlo es instantáneo). */
    suspend fun albumTracks(albumUrl: String): List<SongUi> = withContext(Dispatchers.IO) {
        albumSongsCache[albumUrl]?.let { return@withContext it }
        val songs = when {
            albumUrl.startsWith(YtMusicApi.YTM_PLAYLIST) ->
                YtMusicApi.parsePageSongs(YtMusicApi.browse("VL" + albumUrl.removePrefix(YtMusicApi.YTM_PLAYLIST)))
            albumUrl.startsWith(YtMusicApi.YTM_ALBUM) ->
                YtMusicApi.parsePageSongs(YtMusicApi.browse(albumUrl.removePrefix(YtMusicApi.YTM_ALBUM)))
            else -> PlaylistInfo.getInfo(service, albumUrl).relatedItems.toSongs()
        }
        if (songs.isEmpty()) throw IllegalStateException(str(R.string.list_without_songs))
        if (albumSongsCache.size > 40) albumSongsCache.keys.firstOrNull()?.let { albumSongsCache.remove(it) }
        albumSongsCache[albumUrl] = songs
        songs
    }

    /**
     * Estantes del Inicio de YouTube Music (los recomendados por YouTube). Prueba WEB_REMIX y, si no
     * trae nada, ANDROID_MUSIC. Idioma español y región del teléfono.
     */
    suspend fun homeFeed(
        onProgress: (List<YtMusicApi.FeedShelf>) -> Unit = {},
        onChips: (List<YtMusicApi.FeedChip>) -> Unit = {}
    ): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        val web = safe<List<YtMusicApi.FeedShelf>>(emptyList()) {
            YtMusicApi.home("es", gl, android = false, onProgress = onProgress, onChips = onChips)
        }
        if (web.isNotEmpty()) web
        else safe(emptyList()) {
            YtMusicApi.home("es", gl, android = true, onProgress = onProgress, onChips = onChips)
        }
    }

    /** Categorías de Explorar (estados de ánimo y géneros). Vacía si falla la red. */
    suspend fun exploreCategories(): List<YtMusicApi.ExploreCategory> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        safe(emptyList()) { YtMusicApi.exploreCategories("es", gl) }
    }

    suspend fun categoryFeed(cat: YtMusicApi.ExploreCategory): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        safe(emptyList()) { YtMusicApi.categoryFeed(cat, "es", gl) }
    }

    /** Estantes de un chip del Inicio de YouTube Music (WEB_REMIX y, si no trae nada, ANDROID_MUSIC). */
    suspend fun homeChipFeed(chip: YtMusicApi.FeedChip): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        val web = safe<List<YtMusicApi.FeedShelf>>(emptyList()) { YtMusicApi.chipFeed(chip, "es", gl, android = false) }
        if (web.isNotEmpty()) web
        else safe(emptyList()) { YtMusicApi.chipFeed(chip, "es", gl, android = true) }
    }

    /** Tendencias musicales de YouTube; si el kiosco falla, cae a una búsqueda. */
    suspend fun trendingMusic(): List<SongUi> = withContext(Dispatchers.IO) {
        try {
            val extractor = service.kioskList.getExtractorById(
                YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID, null
            )
            extractor.fetchPage()
            val items = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toSong() }
            if (items.isEmpty()) throw IllegalStateException("Sin resultados")
            items
        } catch (e: Exception) {
            search("éxitos del momento").items
        }
    }

    /**
     * Sesión de radio como la de Metrolist: se pide la radio de una canción y cada página nueva
     * continúa la anterior (continuación) o sigue el "automix" de YouTube, sin filtros agresivos.
     */
    class RadioSession(
        var videoId: String,
        var playlistId: String = "RDAMVM$videoId",
        var params: String? = "wAEB",
        var continuation: String? = null,
        var exhausted: Boolean = false
    )

    /** Siguiente página de la sesión. Vacía si ya no hay más (y la sesión queda agotada). */
    suspend fun radioMore(session: RadioSession): List<SongUi> = withContext(Dispatchers.IO) {
        if (session.exhausted) return@withContext emptyList()
        val root = YtMusicApi.radio(
            session.videoId, session.continuation, session.playlistId, session.params
        )
        val items = YtMusicApi.parseRadio(root)

        session.continuation = YtMusicApi.radioContinuation(root)
        if (session.continuation == null) {
            val auto = YtMusicApi.radioAutomix(root)
            if (auto != null && auto.playlistId != session.playlistId) {
                session.videoId = auto.videoId ?: session.videoId
                session.playlistId = auto.playlistId
                session.params = auto.params
            } else {
                session.exhausted = true
            }
        }
        items
            .filter { it.durationSec == 0L || it.durationSec in 30..1200 }
            .map {
                SongUi(
                    url = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = it.artist.ifBlank { "Desconocido" },
                    durationSec = it.durationSec,
                    thumb = it.thumb
                )
            }
    }

    /**
     * Canciones parecidas a un video (base de la radio infinita, "Para ti" y "Parecido a...").
     * Usa la radio de YouTube Music, que ya agrupa por estilo, y se queda solo con canciones:
     * sin remixes, en vivo, covers, karaokes, videos promocionales ni subidas de usuarios.
     * [seedTitle]: si la canción de origen ya es, por ejemplo, un remix, no se descartan los remixes.
     */
    suspend fun related(videoUrl: String, seedTitle: String? = null): List<SongUi> = withContext(Dispatchers.IO) {
        val viaMusic = safe<List<SongUi>>(emptyList()) { musicRadio(videoUrl, seedTitle) }
        if (viaMusic.size >= 5) return@withContext viaMusic

        // Respaldo: videos relacionados de YouTube, con los mismos filtros de título
        val viaVideos = safe<List<SongUi>>(emptyList()) {
            streamInfo(videoUrl).relatedItems
                .toSongs()
                .filter { it.durationSec in 60..720 && allowedByTitle(it.title, seedTitle) }
        }
        (viaMusic + viaVideos).distinctBy { it.url }
    }

    private fun musicRadio(videoUrl: String, seedTitle: String?): List<SongUi> {
        val vid = videoUrl.substringAfter("v=", "").substringBefore('&')
        if (vid.isBlank()) return emptyList()

        // Varias páginas de la radio (como Metrolist): la primera trae ~25, las continuaciones el resto
        val all = ArrayList<YtMusicApi.RadioItem>()
        var root = YtMusicApi.radio(vid)
        all += YtMusicApi.parseRadio(root)
        var token = YtMusicApi.radioContinuation(root)
        var pages = 0
        while (token != null && all.size < 150 && pages++ < 4) {
            root = try {
                YtMusicApi.radio(vid, token)
            } catch (e: Exception) {
                break
            }
            all += YtMusicApi.parseRadio(root)
            token = YtMusicApi.radioContinuation(root)
        }
        val usable = all.filter {
            it.videoId != vid && it.durationSec in 60..720 && allowedByTitle(it.title, seedTitle)
        }
        // Primero las pistas de audio oficiales; los videos musicales oficiales solo si faltan canciones
        val tracks = usable.filter { it.musicVideoType == "MUSIC_VIDEO_TYPE_ATV" }
        val pool = if (tracks.size >= 6) tracks
        else tracks + usable.filter {
            it.musicVideoType == "MUSIC_VIDEO_TYPE_OMV" || it.musicVideoType.isEmpty()
        }

        val perArtist = HashMap<String, Int>()
        val seenSongs = HashSet<String>()
        return pool
            .filter { seenSongs.add((it.title + "|" + it.artist).normalizeName()) }
            .filter { perArtist.merge(it.artist.normalizeName(), 1, Int::plus)!! <= 3 } // variedad
            .take(80)
            .map {
                SongUi(
                    url = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = it.artist.ifBlank { "Desconocido" },
                    durationSec = it.durationSec,
                    thumb = it.thumb
                )
            }
    }

    private fun List<InfoItem>.toSongs(): List<SongUi> =
        filterIsInstance<StreamInfoItem>().map { it.toSong() }

    private fun StreamInfoItem.toSong() = SongUi(
        url = url,
        title = name ?: str(R.string.untitled),
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private const val INFO_TTL_MS = 90L * 60 * 1000
private const val INFO_MAX_ENTRIES = 20
private val infoCache = ConcurrentHashMap<String, Pair<StreamInfo, Long>>()
private val infoLocks = ConcurrentHashMap<String, Any>()

/** Elige la portada más pequeña que aún se ve bien (>= 400 px): menos datos, menos CPU, menos batería. */
private fun List<Image>.bestThumb(target: Int = 400): String? {
    if (isEmpty()) return null
    val sorted = sortedBy { it.height }
    return (sorted.firstOrNull { it.height >= target } ?: sorted.last()).url
}

/** Audio ya resuelto: URL directa (caduca) + clave estable para la caché de disco. */
private val albumSongsCache = ConcurrentHashMap<String, List<SongUi>>()
/** Álbum de cada canción ya consultada; lista vacía = la canción no tiene álbum. */
private val songAlbumCache = ConcurrentHashMap<String, List<AlbumUi>>()

private inline fun <T> safe(default: T, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        default
    }

/** Marcas de versiones que no son la canción original (remix, karaoke, en vivo, covers, promos...). */
private val BAD_TITLE = Regex(
    """\b(remix|rmx|mashup|karaoke|nightcore|sped up|slowed|reverb|8d audio|bass boosted|type beat|""" +
        """official video|video oficial|lyric video|lyrics video|trailer|commercial|comercial|teaser|""" +
        """reaction|tutorial|full album|unplugged|en vivo)\b""" +
        """|\blive (at|from|in|on|session|version|performance)\b""" +
        """|[(\[][^)\]]*\b(live|cover|tribute|instrumental|soundtrack|ost)\b""",
    RegexOption.IGNORE_CASE
)

private fun badTags(text: String): Set<String> =
    BAD_TITLE.findAll(text).map { it.value.lowercase() }.toSet()

/** Un título pasa si no trae marcas de versión, o si la canción de origen ya las traía. */
private fun allowedByTitle(title: String, seedTitle: String?): Boolean {
    val tags = badTags(title)
    if (tags.isEmpty()) return true
    val seedTags = seedTitle?.let { badTags(it) }.orEmpty()
    return seedTags.containsAll(tags)
}

/** Para comparar nombres de artista sin importar mayúsculas, acentos sueltos o signos. */
private fun String.normalizeName(): String =
    lowercase()
        .replace(Regex("""\s*-\s*topic$"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

data class ResolvedAudio(val url: String, val key: String, val itag: Int)

/** Calidad concreta con la que se pide el audio (el ajuste "Automática" la resuelve según la red). */
enum class StreamQuality { HIGH, MEDIUM, LOW }

data class SongUi(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumb: String?
)

class SearchCursor(val handler: SearchQueryHandler, val nextPage: Page)

data class SearchPage(val items: List<SongUi>, val cursor: SearchCursor?)

class YtRepository {

    private val service = ServiceList.YouTube

    /** Busca canciones (filtro "YouTube Music: canciones"). */
    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        val info = SearchInfo.getInfo(service, handler)
        val next = info.nextPage
        SearchPage(
            items = info.relatedItems.toSongs(),
            cursor = if (next != null) SearchCursor(handler, next) else null
        )
    }

    /** Siguiente página de resultados. */
    suspend fun more(cursor: SearchCursor): SearchPage = withContext(Dispatchers.IO) {
        val page = SearchInfo.getMoreItems(service, cursor.handler, cursor.nextPage)
        val next = page.nextPage
        SearchPage(
            items = page.items.toSongs(),
            cursor = if (next != null) SearchCursor(cursor.handler, next) else null
        )
    }

    private fun freshInfo(videoUrl: String): StreamInfo? {
        val cached = infoCache[videoUrl] ?: return null
        return if (System.currentTimeMillis() - cached.second < INFO_TTL_MS) cached.first else null
    }

    /**
     * StreamInfo de un video con caché de 90 min. El bloqueo es POR VIDEO: el reproductor (audio)
     * y la radio (relacionados) de la misma canción comparten una sola petición, pero cargar
     * recomendaciones del Inicio ya no hace esperar a la canción que quieres reproducir.
     */
    private fun streamInfo(videoUrl: String): StreamInfo {
        freshInfo(videoUrl)?.let { return it }
        val lock = infoLocks.computeIfAbsent(videoUrl) { Any() }
        synchronized(lock) {
            freshInfo(videoUrl)?.let { return it }
            val info = StreamInfo.getInfo(service, videoUrl)
            infoCache[videoUrl] = info to System.currentTimeMillis()
            if (infoCache.size > INFO_MAX_ENTRIES) {
                infoCache.entries.minByOrNull { it.value.second }?.let { infoCache.remove(it.key) }
            }
            return info
        }
    }

    /** Flujos de audio utilizables: de descarga directa, de la pista original (no doblada). */
    private fun audioCandidates(info: StreamInfo): List<AudioStream> {
        val all = info.audioStreams.filter { it.isUrl }
        val progressive = all.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty { all }
        // Evita pistas de audio doblado automáticamente
        return pool.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { pool }
    }

    /** Mejor audio disponible: M4A primero y, dentro de eso, el de mayor calidad. */
    private fun pickBestAudio(info: StreamInfo): AudioStream? =
        audioCandidates(info).sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate }
        ).firstOrNull()

    /** El audio que pide el ajuste de calidad: la mejor, o la más cercana a ~128 kbps (media) o la más ligera (baja). */
    private fun pickAudio(info: StreamInfo, quality: StreamQuality): AudioStream? {
        val best = pickBestAudio(info)
        if (quality == StreamQuality.HIGH || best == null) return best
        val withRate = audioCandidates(info).filter { it.averageBitrate > 0 }
        val pool = withRate.filter { it.format == MediaFormat.M4A }.ifEmpty { withRate }
        if (pool.isEmpty()) return best
        return when (quality) {
            StreamQuality.LOW -> pool.minByOrNull { it.averageBitrate }
            StreamQuality.MEDIUM -> pool.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
            StreamQuality.HIGH -> best
        } ?: best
    }

    /** Información de una canción: álbum, año, duración, calidad del audio y datos del video. */
    suspend fun songInfo(song: SongUi): SongInfo = coroutineScope {
        val vid = song.url.substringAfter("v=", "").substringBefore('&')
        val metaD = async(Dispatchers.IO) {
            safe<YtMusicApi.SongMeta?>(null) {
                if (vid.isBlank()) null else YtMusicApi.parseSongMeta(YtMusicApi.next(vid))
            }
        }
        val albumD = async { safe<AlbumUi?>(null) { albumOfSong(song) } }
        val streamD = async(Dispatchers.IO) { safe<StreamInfo?>(null) { streamInfo(song.url) } }

        val meta = metaD.await()
        val album = albumD.await()
        val stream = streamD.await()
        val audio = stream?.let { pickBestAudio(it) }

        val format = when (audio?.format) {
            null -> null
            MediaFormat.M4A -> "M4A (AAC)"
            MediaFormat.WEBMA_OPUS -> "WebM (Opus)"
            MediaFormat.WEBMA -> "WebM (Vorbis)"
            else -> audio.format?.suffix?.uppercase()
        }
        var kbps = audio?.averageBitrate ?: 0
        if (kbps > 1000) kbps /= 1000 // por si viene en bits por segundo

        SongInfo(
            title = song.title,
            artist = song.artist,
            album = album,
            albumName = album?.name ?: meta?.albumRef?.name,
            year = meta?.year,
            durationSec = when {
                song.durationSec > 0 -> song.durationSec
                (stream?.duration ?: 0L) > 0 -> stream!!.duration
                else -> meta?.durationSec ?: 0L
            },
            audioFormat = format,
            audioCodec = audio?.codec?.takeIf { it.isNotBlank() },
            bitrateKbps = kbps,
            channel = stream?.uploaderName?.takeIf { it.isNotBlank() },
            views = stream?.viewCount ?: -1L,
            videoId = vid,
            url = song.url
        )
    }

    /** Audio de un video (bloqueante: lo usa el hilo de carga del reproductor y la precarga). */
    fun resolveAudioBlocking(videoUrl: String, quality: StreamQuality = StreamQuality.HIGH): ResolvedAudio {
        val info = streamInfo(videoUrl)
        val best = pickAudio(info, quality) ?: throw IllegalStateException(str(R.string.this_video_has_no_audio))

        // La clave es solo la canción (como Metrolist): así se puede saber si está en disco sin resolver
        // nada. El formato (itag) se anota aparte para no mezclar bytes de formatos distintos.
        return ResolvedAudio(best.content, videoUrl, best.itag)
    }

    // ---------- Artistas y álbumes ----------

    /** [pages] páginas de resultados (la primera trae ~20; para álbumes conviene pedir 2). */
    private fun searchRaw(query: String, filter: String, pages: Int = 1): List<InfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        val items = ArrayList<InfoItem>(info.relatedItems)
        var next = info.nextPage
        var page = 1
        while (page < pages && next != null) {
            val more = try {
                SearchInfo.getMoreItems(service, handler, next)
            } catch (e: Exception) {
                break // con lo que ya tenemos basta
            }
            items.addAll(more.items)
            next = more.nextPage
            page++
        }
        return items
    }

    /** Reintenta una vez si falla; devuelve null si no se pudo, para distinguir "falló" de "no hay nada". */
    private suspend fun searchWithRetry(query: String, filter: String, pages: Int = 1): List<InfoItem>? {
        repeat(2) { attempt ->
            try {
                return withContext(Dispatchers.IO) { searchRaw(query, filter, pages) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0) delay(700)
            }
        }
        return null
    }

    /** Álbumes, sencillos y EPs que coinciden con la búsqueda (YouTube Music: filtro "Álbumes"). */
    suspend fun searchAlbums(query: String): List<AlbumUi> = withContext(Dispatchers.IO) {
        searchRaw(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, pages = 2)
            .filterIsInstance<PlaylistInfoItem>()
            .map { item ->
                AlbumUi(
                    url = item.url,
                    name = item.name ?: "",
                    artist = (item.uploaderName ?: "").removeSuffix(" - Topic"),
                    artistUrl = item.uploaderUrl,
                    thumb = item.thumbnails.bestThumb(),
                    trackCount = albumSongsCache[item.url]?.size
                        ?: item.streamCount.toInt().takeIf { it > 0 }
                )
            }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url }
            .take(30)
    }

    /** Artistas que coinciden con la búsqueda (YouTube Music: filtro "Artistas"). */
    /** Sugerencias al escribir en la barra de búsqueda (consultas y resultados directos). */
    suspend fun searchSuggestions(query: String): YtMusicApi.SearchSuggestions = withContext(Dispatchers.IO) {
        YtMusicApi.searchSuggestions(query)
    }

    suspend fun searchArtists(query: String): List<ArtistUi> = withContext(Dispatchers.IO) {
        searchRaw(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS)
            .filterIsInstance<ChannelInfoItem>()
            .map { ArtistUi(it.url, it.name ?: "", it.thumbnails.bestThumb(), it.subscriberCount) }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .take(6)
    }

    /**
     * Pantalla de artista con los datos reales de YouTube Music (canciones top, discografía completa
     * separada en álbumes y sencillos, suscriptores de la ficha). Si esa API falla o cambia de formato,
     * cae al método anterior basado en búsquedas.
     */
    suspend fun artistPage(artist: ArtistUi): ArtistPage =
        try {
            musicArtistPage(artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            legacyArtistPage(artist)
        }

    private suspend fun musicArtistPage(artist: ArtistUi): ArtistPage = coroutineScope {
        val id = artist.url.substringAfter("/channel/", "").substringBefore('?').substringBefore('/')
        if (id.isBlank()) throw IllegalStateException(str(R.string.artist_without_browseid))

        val root = withContext(Dispatchers.IO) { YtMusicApi.browse(id) }
        val parsed = YtMusicApi.parseArtist(root, artist)
        if (parsed.topSongs.isEmpty() && parsed.albums.isEmpty() && parsed.singles.isEmpty()) {
            throw IllegalStateException(str(R.string.empty_artist_page))
        }

        // Lista completa de canciones y discografía completa, en paralelo; si algo falla se usa lo corto
        val songsD = async(Dispatchers.IO) {
            safe<List<SongUi>?>(null) {
                parsed.songsPlaylistUrl?.let { PlaylistInfo.getInfo(service, it).relatedItems.toSongs() }
            }
        }
        val albumsD = async(Dispatchers.IO) {
            safe(parsed.albums) { YtMusicApi.fullReleases(parsed.albumsMore, artist, parsed.albums) }
        }
        val singlesD = async(Dispatchers.IO) {
            safe(parsed.singles) { YtMusicApi.fullReleases(parsed.singlesMore, artist, parsed.singles) }
        }

        val full = songsD.await().orEmpty()
        val byUrl = full.associateBy { it.url }
        // Las 5 top van primero (orden real), con duración si la lista completa la trae
        val popular = (parsed.topSongs.map { byUrl[it.url] ?: it } + full)
            .distinctBy { it.url }
            .take(20)

        val albums = albumsD.await()
        val singles = singlesD.await()
        val photo = artist.thumb

        ArtistPage(
            artist = artist.copy(thumb = photo, subscribers = parsed.subscribers),
            hero = photo,
            description = parsed.description,
            subscribers = parsed.subscribers,
            popular = popular,
            releases = emptyList(),
            popularFailed = false,
            releasesFailed = false,
            albums = albums,
            singles = singles
        )
    }

    /**
     * Datos de la pantalla de artista, pedidos en paralelo: ficha del canal (foto, banner, suscriptores),
     * canciones populares (búsqueda de canciones filtrada por artista) y lanzamientos (búsqueda de álbumes
     * filtrada por el artista).
     */
    private suspend fun legacyArtistPage(artist: ArtistUi): ArtistPage = coroutineScope {
        val infoD = async(Dispatchers.IO) {
            safe<ChannelInfo?>(null) { ChannelInfo.getInfo(service, artist.url) }
        }
        val songsD = async { searchWithRetry(artist.name, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
        val albumsD = async { searchWithRetry(artist.name, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, pages = 2) }

        val info = infoD.await()
        val songsRaw = songsD.await()
        val albumsRaw = albumsD.await()
        val key = artist.name.normalizeName()

        val popular = (songsRaw ?: emptyList())
            .filterIsInstance<StreamInfoItem>()
            .map { it.toSong() }
            .filter { it.artist.normalizeName().contains(key) }
            .distinctBy { it.url }
            .take(15)

        val releases = (albumsRaw ?: emptyList())
            .filterIsInstance<PlaylistInfoItem>()
            .filter { item ->
                val byUrl = item.uploaderUrl != null && item.uploaderUrl == artist.url
                byUrl || (item.uploaderName ?: "").normalizeName().contains(key)
            }
            .map { item ->
                AlbumUi(
                    url = item.url,
                    name = item.name ?: str(R.string.untitled),
                    artist = item.uploaderName ?: artist.name,
                    artistUrl = item.uploaderUrl,
                    thumb = item.thumbnails.bestThumb(),
                    trackCount = albumSongsCache[item.url]?.size
                )
            }
            .distinctBy { it.url }
            .take(40)

        // La foto del artista (no el banner del canal, que suele ser un diseño genérico)
        val avatar = info?.avatars?.maxByOrNull { it.height }?.url
        val photo = artist.thumb ?: avatar
        ArtistPage(
            artist = artist.copy(thumb = photo),
            hero = photo,
            description = info?.description,
            subscribers = info?.subscriberCount ?: artist.subscribers,
            popular = popular,
            releases = releases,
            popularFailed = songsRaw == null,
            releasesFailed = albumsRaw == null
        )
    }

    /** Álbum al que pertenece una canción, o null si no tiene (videos sueltos, etc.). */
    suspend fun albumOfSong(song: SongUi): AlbumUi? = withContext(Dispatchers.IO) {
        songAlbumCache[song.url]?.let { return@withContext it.firstOrNull() }
        val vid = song.url.substringAfter("v=", "").substringBefore('&')
        if (vid.isBlank()) return@withContext null

        val ref = YtMusicApi.parseAlbumRef(YtMusicApi.next(vid))
        val pid = ref?.let { YtMusicApi.parseAlbumPlaylistId(YtMusicApi.browse(it.browseId)) }
        val album = if (ref != null && pid != null) {
            AlbumUi(
                url = "https://www.youtube.com/playlist?list=$pid",
                name = ref.name,
                artist = song.artist,
                artistUrl = null,
                thumb = song.thumb
            )
        } else null
        if (songAlbumCache.size > 200) songAlbumCache.keys.firstOrNull()?.let { songAlbumCache.remove(it) }
        songAlbumCache[song.url] = listOfNotNull(album)
        album
    }

    /** Canciones de un álbum, sencillo o EP (con caché: al abrirlo después de clasificarlo es instantáneo). */
    suspend fun albumTracks(albumUrl: String): List<SongUi> = withContext(Dispatchers.IO) {
        albumSongsCache[albumUrl]?.let { return@withContext it }
        val songs = when {
            albumUrl.startsWith(YtMusicApi.YTM_PLAYLIST) ->
                YtMusicApi.parsePageSongs(YtMusicApi.browse("VL" + albumUrl.removePrefix(YtMusicApi.YTM_PLAYLIST)))
            albumUrl.startsWith(YtMusicApi.YTM_ALBUM) ->
                YtMusicApi.parsePageSongs(YtMusicApi.browse(albumUrl.removePrefix(YtMusicApi.YTM_ALBUM)))
            else -> PlaylistInfo.getInfo(service, albumUrl).relatedItems.toSongs()
        }
        if (songs.isEmpty()) throw IllegalStateException(str(R.string.list_without_songs))
        if (albumSongsCache.size > 40) albumSongsCache.keys.firstOrNull()?.let { albumSongsCache.remove(it) }
        albumSongsCache[albumUrl] = songs
        songs
    }

    /**
     * Estantes del Inicio de YouTube Music (los recomendados por YouTube). Prueba WEB_REMIX y, si no
     * trae nada, ANDROID_MUSIC. Idioma español y región del teléfono.
     */
    suspend fun homeFeed(
        onProgress: (List<YtMusicApi.FeedShelf>) -> Unit = {},
        onChips: (List<YtMusicApi.FeedChip>) -> Unit = {}
    ): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        val web = safe<List<YtMusicApi.FeedShelf>>(emptyList()) {
            YtMusicApi.home("es", gl, android = false, onProgress = onProgress, onChips = onChips)
        }
        if (web.isNotEmpty()) web
        else safe(emptyList()) {
            YtMusicApi.home("es", gl, android = true, onProgress = onProgress, onChips = onChips)
        }
    }

    /** Categorías de Explorar (estados de ánimo y géneros). Vacía si falla la red. */
    suspend fun exploreCategories(): List<YtMusicApi.ExploreCategory> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        safe(emptyList()) { YtMusicApi.exploreCategories("es", gl) }
    }

    suspend fun categoryFeed(cat: YtMusicApi.ExploreCategory): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        safe(emptyList()) { YtMusicApi.categoryFeed(cat, "es", gl) }
    }

    /** Estantes de un chip del Inicio de YouTube Music (WEB_REMIX y, si no trae nada, ANDROID_MUSIC). */
    suspend fun homeChipFeed(chip: YtMusicApi.FeedChip): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        val web = safe<List<YtMusicApi.FeedShelf>>(emptyList()) { YtMusicApi.chipFeed(chip, "es", gl, android = false) }
        if (web.isNotEmpty()) web
        else safe(emptyList()) { YtMusicApi.chipFeed(chip, "es", gl, android = true) }
    }

    /** Tendencias musicales de YouTube; si el kiosco falla, cae a una búsqueda. */
    suspend fun trendingMusic(): List<SongUi> = withContext(Dispatchers.IO) {
        try {
            val extractor = service.kioskList.getExtractorById(
                YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID, null
            )
            extractor.fetchPage()
            val items = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toSong() }
            if (items.isEmpty()) throw IllegalStateException("Sin resultados")
            items
        } catch (e: Exception) {
            search("éxitos del momento").items
        }
    }

    /**
     * Sesión de radio como la de Metrolist: se pide la radio de una canción y cada página nueva
     * continúa la anterior (continuación) o sigue el "automix" de YouTube, sin filtros agresivos.
     */
    class RadioSession(
        var videoId: String,
        var playlistId: String = "RDAMVM$videoId",
        var params: String? = "wAEB",
        var continuation: String? = null,
        var exhausted: Boolean = false
    )

    /** Siguiente página de la sesión. Vacía si ya no hay más (y la sesión queda agotada). */
    suspend fun radioMore(session: RadioSession): List<SongUi> = withContext(Dispatchers.IO) {
        if (session.exhausted) return@withContext emptyList()
        val root = YtMusicApi.radio(
            session.videoId, session.continuation, session.playlistId, session.params
        )
        val items = YtMusicApi.parseRadio(root)

        session.continuation = YtMusicApi.radioContinuation(root)
        if (session.continuation == null) {
            val auto = YtMusicApi.radioAutomix(root)
            if (auto != null && auto.playlistId != session.playlistId) {
                session.videoId = auto.videoId ?: session.videoId
                session.playlistId = auto.playlistId
                session.params = auto.params
            } else {
                session.exhausted = true
            }
        }
        items
            .filter { it.durationSec == 0L || it.durationSec in 30..1200 }
            .map {
                SongUi(
                    url = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = it.artist.ifBlank { "Desconocido" },
                    durationSec = it.durationSec,
                    thumb = it.thumb
                )
            }
    }

    /**
     * Canciones parecidas a un video (base de la radio infinita, "Para ti" y "Parecido a...").
     * Usa la radio de YouTube Music, que ya agrupa por estilo, y se queda solo con canciones:
     * sin remixes, en vivo, covers, karaokes, videos promocionales ni subidas de usuarios.
     * [seedTitle]: si la canción de origen ya es, por ejemplo, un remix, no se descartan los remixes.
     */
    suspend fun related(videoUrl: String, seedTitle: String? = null): List<SongUi> = withContext(Dispatchers.IO) {
        val viaMusic = safe<List<SongUi>>(emptyList()) { musicRadio(videoUrl, seedTitle) }
        if (viaMusic.size >= 5) return@withContext viaMusic

        // Respaldo: videos relacionados de YouTube, con los mismos filtros de título
        val viaVideos = safe<List<SongUi>>(emptyList()) {
            streamInfo(videoUrl).relatedItems
                .toSongs()
                .filter { it.durationSec in 60..720 && allowedByTitle(it.title, seedTitle) }
        }
        (viaMusic + viaVideos).distinctBy { it.url }
    }

    private fun musicRadio(videoUrl: String, seedTitle: String?): List<SongUi> {
        val vid = videoUrl.substringAfter("v=", "").substringBefore('&')
        if (vid.isBlank()) return emptyList()

        // Varias páginas de la radio (como Metrolist): la primera trae ~25, las continuaciones el resto
        val all = ArrayList<YtMusicApi.RadioItem>()
        var root = YtMusicApi.radio(vid)
        all += YtMusicApi.parseRadio(root)
        var token = YtMusicApi.radioContinuation(root)
        var pages = 0
        while (token != null && all.size < 150 && pages++ < 4) {
            root = try {
                YtMusicApi.radio(vid, token)
            } catch (e: Exception) {
                break
            }
            all += YtMusicApi.parseRadio(root)
            token = YtMusicApi.radioContinuation(root)
        }
        val usable = all.filter {
            it.videoId != vid && it.durationSec in 60..720 && allowedByTitle(it.title, seedTitle)
        }
        // Primero las pistas de audio oficiales; los videos musicales oficiales solo si faltan canciones
        val tracks = usable.filter { it.musicVideoType == "MUSIC_VIDEO_TYPE_ATV" }
        val pool = if (tracks.size >= 6) tracks
        else tracks + usable.filter {
            it.musicVideoType == "MUSIC_VIDEO_TYPE_OMV" || it.musicVideoType.isEmpty()
        }

        val perArtist = HashMap<String, Int>()
        val seenSongs = HashSet<String>()
        return pool
            .filter { seenSongs.add((it.title + "|" + it.artist).normalizeName()) }
            .filter { perArtist.merge(it.artist.normalizeName(), 1, Int::plus)!! <= 3 } // variedad
            .take(80)
            .map {
                SongUi(
                    url = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = it.artist.ifBlank { "Desconocido" },
                    durationSec = it.durationSec,
                    thumb = it.thumb
                )
            }
    }

    private fun List<InfoItem>.toSongs(): List<SongUi> =
        filterIsInstance<StreamInfoItem>().map { it.toSong() }

    private fun StreamInfoItem.toSong() = SongUi(
        url = url,
        title = name ?: str(R.string.untitled),
=======

private const val INFO_TTL_MS = 90L * 60 * 1000
private const val INFO_MAX_ENTRIES = 20
private val infoCache = ConcurrentHashMap<String, Pair<StreamInfo, Long>>()
private val infoLocks = ConcurrentHashMap<String, Any>()

/** Elige la portada más pequeña que aún se ve bien (>= 400 px): menos datos, menos CPU, menos batería. */
private fun List<Image>.bestThumb(target: Int = 400): String? {
    if (isEmpty()) return null
    val sorted = sortedBy { it.height }
    return (sorted.firstOrNull { it.height >= target } ?: sorted.last()).url
}

/** Audio ya resuelto: URL directa (caduca) + clave estable para la caché de disco. */
private val albumSongsCache = ConcurrentHashMap<String, List<SongUi>>()
/** Álbum de cada canción ya consultada; lista vacía = la canción no tiene álbum. */
private val songAlbumCache = ConcurrentHashMap<String, List<AlbumUi>>()

private inline fun <T> safe(default: T, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        default
    }

/** Marcas de versiones que no son la canción original (remix, karaoke, en vivo, covers, promos...). */
private val BAD_TITLE = Regex(
    """\b(remix|rmx|mashup|karaoke|nightcore|sped up|slowed|reverb|8d audio|bass boosted|type beat|""" +
        """official video|video oficial|lyric video|lyrics video|trailer|commercial|comercial|teaser|""" +
        """reaction|tutorial|full album|unplugged|en vivo)\b""" +
        """|\blive (at|from|in|on|session|version|performance)\b""" +
        """|[(\[][^)\]]*\b(live|cover|tribute|instrumental|soundtrack|ost)\b""",
    RegexOption.IGNORE_CASE
)

private fun badTags(text: String): Set<String> =
    BAD_TITLE.findAll(text).map { it.value.lowercase() }.toSet()

/** Un título pasa si no trae marcas de versión, o si la canción de origen ya las traía. */
private fun allowedByTitle(title: String, seedTitle: String?): Boolean {
    val tags = badTags(title)
    if (tags.isEmpty()) return true
    val seedTags = seedTitle?.let { badTags(it) }.orEmpty()
    return seedTags.containsAll(tags)
}

/** Para comparar nombres de artista sin importar mayúsculas, acentos sueltos o signos. */
private fun String.normalizeName(): String =
    lowercase()
        .replace(Regex("""\s*-\s*topic$"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

data class ResolvedAudio(val url: String, val key: String, val itag: Int)

data class SongUi(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumb: String?
)

class SearchCursor(val handler: SearchQueryHandler, val nextPage: Page)

data class SearchPage(val items: List<SongUi>, val cursor: SearchCursor?)

class YtRepository {

    private val service = ServiceList.YouTube

    /** Busca canciones (filtro "YouTube Music: canciones"). */
    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        val info = SearchInfo.getInfo(service, handler)
        val next = info.nextPage
        SearchPage(
            items = info.relatedItems.toSongs(),
            cursor = if (next != null) SearchCursor(handler, next) else null
        )
    }

    /** Siguiente página de resultados. */
    suspend fun more(cursor: SearchCursor): SearchPage = withContext(Dispatchers.IO) {
        val page = SearchInfo.getMoreItems(service, cursor.handler, cursor.nextPage)
        val next = page.nextPage
        SearchPage(
            items = page.items.toSongs(),
            cursor = if (next != null) SearchCursor(cursor.handler, next) else null
        )
    }

    private fun freshInfo(videoUrl: String): StreamInfo? {
        val cached = infoCache[videoUrl] ?: return null
        return if (System.currentTimeMillis() - cached.second < INFO_TTL_MS) cached.first else null
    }

    /**
     * StreamInfo de un video con caché de 90 min. El bloqueo es POR VIDEO: el reproductor (audio)
     * y la radio (relacionados) de la misma canción comparten una sola petición, pero cargar
     * recomendaciones del Inicio ya no hace esperar a la canción que quieres reproducir.
     */
    private fun streamInfo(videoUrl: String): StreamInfo {
        freshInfo(videoUrl)?.let { return it }
        val lock = infoLocks.computeIfAbsent(videoUrl) { Any() }
        synchronized(lock) {
            freshInfo(videoUrl)?.let { return it }
            val info = StreamInfo.getInfo(service, videoUrl)
            infoCache[videoUrl] = info to System.currentTimeMillis()
            if (infoCache.size > INFO_MAX_ENTRIES) {
                infoCache.entries.minByOrNull { it.value.second }?.let { infoCache.remove(it.key) }
            }
            return info
        }
    }

    /** Mejor audio disponible: M4A primero y, dentro de eso, el de mayor calidad. */
    private fun pickBestAudio(info: StreamInfo): AudioStream? {
        val all = info.audioStreams.filter { it.isUrl }
        val progressive = all.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty { all }
        // Evita pistas de audio doblado automáticamente
        val original = pool.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { pool }

        return original.sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate }
        ).firstOrNull()
    }

    /** Información de una canción: álbum, año, duración, calidad del audio y datos del video. */
    suspend fun songInfo(song: SongUi): SongInfo = coroutineScope {
        val vid = song.url.substringAfter("v=", "").substringBefore('&')
        val metaD = async(Dispatchers.IO) {
            safe<YtMusicApi.SongMeta?>(null) {
                if (vid.isBlank()) null else YtMusicApi.parseSongMeta(YtMusicApi.next(vid))
            }
        }
        val albumD = async { safe<AlbumUi?>(null) { albumOfSong(song) } }
        val streamD = async(Dispatchers.IO) { safe<StreamInfo?>(null) { streamInfo(song.url) } }

        val meta = metaD.await()
        val album = albumD.await()
        val stream = streamD.await()
        val audio = stream?.let { pickBestAudio(it) }

        val format = when (audio?.format) {
            null -> null
            MediaFormat.M4A -> "M4A (AAC)"
            MediaFormat.WEBMA_OPUS -> "WebM (Opus)"
            MediaFormat.WEBMA -> "WebM (Vorbis)"
            else -> audio.format?.suffix?.uppercase()
        }
        var kbps = audio?.averageBitrate ?: 0
        if (kbps > 1000) kbps /= 1000 // por si viene en bits por segundo

        SongInfo(
            title = song.title,
            artist = song.artist,
            album = album,
            albumName = album?.name ?: meta?.albumRef?.name,
            year = meta?.year,
            durationSec = when {
                song.durationSec > 0 -> song.durationSec
                (stream?.duration ?: 0L) > 0 -> stream!!.duration
                else -> meta?.durationSec ?: 0L
            },
            audioFormat = format,
            audioCodec = audio?.codec?.takeIf { it.isNotBlank() },
            bitrateKbps = kbps,
            channel = stream?.uploaderName?.takeIf { it.isNotBlank() },
            views = stream?.viewCount ?: -1L,
            videoId = vid,
            url = song.url
        )
    }

    /** Audio de un video (bloqueante: lo usa el hilo de carga del reproductor y la precarga). */
    fun resolveAudioBlocking(videoUrl: String): ResolvedAudio {
        val info = streamInfo(videoUrl)
        val best = pickBestAudio(info) ?: throw IllegalStateException("Este video no tiene audio disponible")

        // La clave es solo la canción (como Metrolist): así se puede saber si está en disco sin resolver
        // nada. El formato (itag) se anota aparte para no mezclar bytes de formatos distintos.
        return ResolvedAudio(best.content, videoUrl, best.itag)
    }

    // ---------- Artistas y álbumes ----------

    /** [pages] páginas de resultados (la primera trae ~20; para álbumes conviene pedir 2). */
    private fun searchRaw(query: String, filter: String, pages: Int = 1): List<InfoItem> {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        val items = ArrayList<InfoItem>(info.relatedItems)
        var next = info.nextPage
        var page = 1
        while (page < pages && next != null) {
            val more = try {
                SearchInfo.getMoreItems(service, handler, next)
            } catch (e: Exception) {
                break // con lo que ya tenemos basta
            }
            items.addAll(more.items)
            next = more.nextPage
            page++
        }
        return items
    }

    /** Reintenta una vez si falla; devuelve null si no se pudo, para distinguir "falló" de "no hay nada". */
    private suspend fun searchWithRetry(query: String, filter: String, pages: Int = 1): List<InfoItem>? {
        repeat(2) { attempt ->
            try {
                return withContext(Dispatchers.IO) { searchRaw(query, filter, pages) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0) delay(700)
            }
        }
        return null
    }

    /** Álbumes, sencillos y EPs que coinciden con la búsqueda (YouTube Music: filtro "Álbumes"). */
    suspend fun searchAlbums(query: String): List<AlbumUi> = withContext(Dispatchers.IO) {
        searchRaw(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, pages = 2)
            .filterIsInstance<PlaylistInfoItem>()
            .map { item ->
                AlbumUi(
                    url = item.url,
                    name = item.name ?: "",
                    artist = (item.uploaderName ?: "").removeSuffix(" - Topic"),
                    artistUrl = item.uploaderUrl,
                    thumb = item.thumbnails.bestThumb(),
                    trackCount = albumSongsCache[item.url]?.size
                        ?: item.streamCount.toInt().takeIf { it > 0 }
                )
            }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url }
            .take(30)
    }

    /** Artistas que coinciden con la búsqueda (YouTube Music: filtro "Artistas"). */
    suspend fun searchArtists(query: String): List<ArtistUi> = withContext(Dispatchers.IO) {
        searchRaw(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS)
            .filterIsInstance<ChannelInfoItem>()
            .map { ArtistUi(it.url, it.name ?: "", it.thumbnails.bestThumb(), it.subscriberCount) }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .take(6)
    }

    /**
     * Pantalla de artista con los datos reales de YouTube Music (canciones top, discografía completa
     * separada en álbumes y sencillos, suscriptores de la ficha). Si esa API falla o cambia de formato,
     * cae al método anterior basado en búsquedas.
     */
    suspend fun artistPage(artist: ArtistUi): ArtistPage =
        try {
            musicArtistPage(artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            legacyArtistPage(artist)
        }

    private suspend fun musicArtistPage(artist: ArtistUi): ArtistPage = coroutineScope {
        val id = artist.url.substringAfter("/channel/", "").substringBefore('?').substringBefore('/')
        if (id.isBlank()) throw IllegalStateException("Artista sin browseId")

        val root = withContext(Dispatchers.IO) { YtMusicApi.browse(id) }
        val parsed = YtMusicApi.parseArtist(root, artist)
        if (parsed.topSongs.isEmpty() && parsed.albums.isEmpty() && parsed.singles.isEmpty()) {
            throw IllegalStateException("Página de artista vacía")
        }

        // Lista completa de canciones y discografía completa, en paralelo; si algo falla se usa lo corto
        val songsD = async(Dispatchers.IO) {
            safe<List<SongUi>?>(null) {
                parsed.songsPlaylistUrl?.let { PlaylistInfo.getInfo(service, it).relatedItems.toSongs() }
            }
        }
        val albumsD = async(Dispatchers.IO) {
            safe(parsed.albums) { YtMusicApi.fullReleases(parsed.albumsMore, artist, parsed.albums) }
        }
        val singlesD = async(Dispatchers.IO) {
            safe(parsed.singles) { YtMusicApi.fullReleases(parsed.singlesMore, artist, parsed.singles) }
        }

        val full = songsD.await().orEmpty()
        val byUrl = full.associateBy { it.url }
        // Las 5 top van primero (orden real), con duración si la lista completa la trae
        val popular = (parsed.topSongs.map { byUrl[it.url] ?: it } + full)
            .distinctBy { it.url }
            .take(20)

        val albums = albumsD.await()
        val singles = singlesD.await()
        val photo = artist.thumb

        ArtistPage(
            artist = artist.copy(thumb = photo, subscribers = parsed.subscribers),
            hero = photo,
            description = parsed.description,
            subscribers = parsed.subscribers,
            popular = popular,
            releases = emptyList(),
            popularFailed = false,
            releasesFailed = false,
            albums = albums,
            singles = singles
        )
    }

    /**
     * Datos de la pantalla de artista, pedidos en paralelo: ficha del canal (foto, banner, suscriptores),
     * canciones populares (búsqueda de canciones filtrada por artista) y lanzamientos (búsqueda de álbumes
     * filtrada por el artista).
     */
    private suspend fun legacyArtistPage(artist: ArtistUi): ArtistPage = coroutineScope {
        val infoD = async(Dispatchers.IO) {
            safe<ChannelInfo?>(null) { ChannelInfo.getInfo(service, artist.url) }
        }
        val songsD = async { searchWithRetry(artist.name, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }
        val albumsD = async { searchWithRetry(artist.name, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, pages = 2) }

        val info = infoD.await()
        val songsRaw = songsD.await()
        val albumsRaw = albumsD.await()
        val key = artist.name.normalizeName()

        val popular = (songsRaw ?: emptyList())
            .filterIsInstance<StreamInfoItem>()
            .map { it.toSong() }
            .filter { it.artist.normalizeName().contains(key) }
            .distinctBy { it.url }
            .take(15)

        val releases = (albumsRaw ?: emptyList())
            .filterIsInstance<PlaylistInfoItem>()
            .filter { item ->
                val byUrl = item.uploaderUrl != null && item.uploaderUrl == artist.url
                byUrl || (item.uploaderName ?: "").normalizeName().contains(key)
            }
            .map { item ->
                AlbumUi(
                    url = item.url,
                    name = item.name ?: "Sin título",
                    artist = item.uploaderName ?: artist.name,
                    artistUrl = item.uploaderUrl,
                    thumb = item.thumbnails.bestThumb(),
                    trackCount = albumSongsCache[item.url]?.size
                )
            }
            .distinctBy { it.url }
            .take(40)

        // La foto del artista (no el banner del canal, que suele ser un diseño genérico)
        val avatar = info?.avatars?.maxByOrNull { it.height }?.url
        val photo = artist.thumb ?: avatar
        ArtistPage(
            artist = artist.copy(thumb = photo),
            hero = photo,
            description = info?.description,
            subscribers = info?.subscriberCount ?: artist.subscribers,
            popular = popular,
            releases = releases,
            popularFailed = songsRaw == null,
            releasesFailed = albumsRaw == null
        )
    }

    /** Álbum al que pertenece una canción, o null si no tiene (videos sueltos, etc.). */
    suspend fun albumOfSong(song: SongUi): AlbumUi? = withContext(Dispatchers.IO) {
        songAlbumCache[song.url]?.let { return@withContext it.firstOrNull() }
        val vid = song.url.substringAfter("v=", "").substringBefore('&')
        if (vid.isBlank()) return@withContext null

        val ref = YtMusicApi.parseAlbumRef(YtMusicApi.next(vid))
        val pid = ref?.let { YtMusicApi.parseAlbumPlaylistId(YtMusicApi.browse(it.browseId)) }
        val album = if (ref != null && pid != null) {
            AlbumUi(
                url = "https://www.youtube.com/playlist?list=$pid",
                name = ref.name,
                artist = song.artist,
                artistUrl = null,
                thumb = song.thumb
            )
        } else null
        if (songAlbumCache.size > 200) songAlbumCache.keys.firstOrNull()?.let { songAlbumCache.remove(it) }
        songAlbumCache[song.url] = listOfNotNull(album)
        album
    }

    /** Canciones de un álbum, sencillo o EP (con caché: al abrirlo después de clasificarlo es instantáneo). */
    suspend fun albumTracks(albumUrl: String): List<SongUi> = withContext(Dispatchers.IO) {
        albumSongsCache[albumUrl]?.let { return@withContext it }
        val songs = when {
            albumUrl.startsWith(YtMusicApi.YTM_PLAYLIST) ->
                YtMusicApi.parsePageSongs(YtMusicApi.browse("VL" + albumUrl.removePrefix(YtMusicApi.YTM_PLAYLIST)))
            albumUrl.startsWith(YtMusicApi.YTM_ALBUM) ->
                YtMusicApi.parsePageSongs(YtMusicApi.browse(albumUrl.removePrefix(YtMusicApi.YTM_ALBUM)))
            else -> PlaylistInfo.getInfo(service, albumUrl).relatedItems.toSongs()
        }
        if (songs.isEmpty()) throw IllegalStateException("Lista sin canciones")
        if (albumSongsCache.size > 40) albumSongsCache.keys.firstOrNull()?.let { albumSongsCache.remove(it) }
        albumSongsCache[albumUrl] = songs
        songs
    }

    /**
     * Estantes del Inicio de YouTube Music (los recomendados por YouTube). Prueba WEB_REMIX y, si no
     * trae nada, ANDROID_MUSIC. Idioma español y región del teléfono.
     */
    suspend fun homeFeed(
        onProgress: (List<YtMusicApi.FeedShelf>) -> Unit = {},
        onChips: (List<YtMusicApi.FeedChip>) -> Unit = {}
    ): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        val web = safe<List<YtMusicApi.FeedShelf>>(emptyList()) {
            YtMusicApi.home("es", gl, android = false, onProgress = onProgress, onChips = onChips)
        }
        if (web.isNotEmpty()) web
        else safe(emptyList()) {
            YtMusicApi.home("es", gl, android = true, onProgress = onProgress, onChips = onChips)
        }
    }

    /** Categorías de Explorar (estados de ánimo y géneros). Vacía si falla la red. */
    suspend fun exploreCategories(): List<YtMusicApi.ExploreCategory> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        safe(emptyList()) { YtMusicApi.exploreCategories("es", gl) }
    }

    suspend fun categoryFeed(cat: YtMusicApi.ExploreCategory): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        safe(emptyList()) { YtMusicApi.categoryFeed(cat, "es", gl) }
    }

    /** Estantes de un chip del Inicio de YouTube Music (WEB_REMIX y, si no trae nada, ANDROID_MUSIC). */
    suspend fun homeChipFeed(chip: YtMusicApi.FeedChip): List<YtMusicApi.FeedShelf> = withContext(Dispatchers.IO) {
        val gl = Locale.getDefault().country.ifBlank { "US" }
        val web = safe<List<YtMusicApi.FeedShelf>>(emptyList()) { YtMusicApi.chipFeed(chip, "es", gl, android = false) }
        if (web.isNotEmpty()) web
        else safe(emptyList()) { YtMusicApi.chipFeed(chip, "es", gl, android = true) }
    }

    /** Tendencias musicales de YouTube; si el kiosco falla, cae a una búsqueda. */
    suspend fun trendingMusic(): List<SongUi> = withContext(Dispatchers.IO) {
        try {
            val extractor = service.kioskList.getExtractorById(
                YoutubeTrendingMusicLinkHandlerFactory.KIOSK_ID, null
            )
            extractor.fetchPage()
            val items = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toSong() }
            if (items.isEmpty()) throw IllegalStateException("Sin resultados")
            items
        } catch (e: Exception) {
            search("éxitos del momento").items
        }
    }

    /**
     * Sesión de radio como la de Metrolist: se pide la radio de una canción y cada página nueva
     * continúa la anterior (continuación) o sigue el "automix" de YouTube, sin filtros agresivos.
     */
    class RadioSession(
        var videoId: String,
        var playlistId: String = "RDAMVM$videoId",
        var params: String? = "wAEB",
        var continuation: String? = null,
        var exhausted: Boolean = false
    )

    /** Siguiente página de la sesión. Vacía si ya no hay más (y la sesión queda agotada). */
    suspend fun radioMore(session: RadioSession): List<SongUi> = withContext(Dispatchers.IO) {
        if (session.exhausted) return@withContext emptyList()
        val root = YtMusicApi.radio(
            session.videoId, session.continuation, session.playlistId, session.params
        )
        val items = YtMusicApi.parseRadio(root)

        session.continuation = YtMusicApi.radioContinuation(root)
        if (session.continuation == null) {
            val auto = YtMusicApi.radioAutomix(root)
            if (auto != null && auto.playlistId != session.playlistId) {
                session.videoId = auto.videoId ?: session.videoId
                session.playlistId = auto.playlistId
                session.params = auto.params
            } else {
                session.exhausted = true
            }
        }
        items
            .filter { it.durationSec == 0L || it.durationSec in 30..1200 }
            .map {
                SongUi(
                    url = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = it.artist.ifBlank { "Desconocido" },
                    durationSec = it.durationSec,
                    thumb = it.thumb
                )
            }
    }

    /**
     * Canciones parecidas a un video (base de la radio infinita, "Para ti" y "Parecido a...").
     * Usa la radio de YouTube Music, que ya agrupa por estilo, y se queda solo con canciones:
     * sin remixes, en vivo, covers, karaokes, videos promocionales ni subidas de usuarios.
     * [seedTitle]: si la canción de origen ya es, por ejemplo, un remix, no se descartan los remixes.
     */
    suspend fun related(videoUrl: String, seedTitle: String? = null): List<SongUi> = withContext(Dispatchers.IO) {
        val viaMusic = safe<List<SongUi>>(emptyList()) { musicRadio(videoUrl, seedTitle) }
        if (viaMusic.size >= 5) return@withContext viaMusic

        // Respaldo: videos relacionados de YouTube, con los mismos filtros de título
        val viaVideos = safe<List<SongUi>>(emptyList()) {
            streamInfo(videoUrl).relatedItems
                .toSongs()
                .filter { it.durationSec in 60..720 && allowedByTitle(it.title, seedTitle) }
        }
        (viaMusic + viaVideos).distinctBy { it.url }
    }

    private fun musicRadio(videoUrl: String, seedTitle: String?): List<SongUi> {
        val vid = videoUrl.substringAfter("v=", "").substringBefore('&')
        if (vid.isBlank()) return emptyList()

        // Varias páginas de la radio (como Metrolist): la primera trae ~25, las continuaciones el resto
        val all = ArrayList<YtMusicApi.RadioItem>()
        var root = YtMusicApi.radio(vid)
        all += YtMusicApi.parseRadio(root)
        var token = YtMusicApi.radioContinuation(root)
        var pages = 0
        while (token != null && all.size < 150 && pages++ < 4) {
            root = try {
                YtMusicApi.radio(vid, token)
            } catch (e: Exception) {
                break
            }
            all += YtMusicApi.parseRadio(root)
            token = YtMusicApi.radioContinuation(root)
        }
        val usable = all.filter {
            it.videoId != vid && it.durationSec in 60..720 && allowedByTitle(it.title, seedTitle)
        }
        // Primero las pistas de audio oficiales; los videos musicales oficiales solo si faltan canciones
        val tracks = usable.filter { it.musicVideoType == "MUSIC_VIDEO_TYPE_ATV" }
        val pool = if (tracks.size >= 6) tracks
        else tracks + usable.filter {
            it.musicVideoType == "MUSIC_VIDEO_TYPE_OMV" || it.musicVideoType.isEmpty()
        }

        val perArtist = HashMap<String, Int>()
        val seenSongs = HashSet<String>()
        return pool
            .filter { seenSongs.add((it.title + "|" + it.artist).normalizeName()) }
            .filter { perArtist.merge(it.artist.normalizeName(), 1, Int::plus)!! <= 3 } // variedad
            .take(80)
            .map {
                SongUi(
                    url = "https://www.youtube.com/watch?v=${it.videoId}",
                    title = it.title,
                    artist = it.artist.ifBlank { "Desconocido" },
                    durationSec = it.durationSec,
                    thumb = it.thumb
                )
            }
    }

    private fun List<InfoItem>.toSongs(): List<SongUi> =
        filterIsInstance<StreamInfoItem>().map { it.toSong() }

    private fun StreamInfoItem.toSong() = SongUi(
        url = url,
        title = name ?: "Sin título",
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
        artist = (uploaderName ?: "Desconocido").removeSuffix(" - Topic"),
        durationSec = duration,
        thumb = thumbnails.bestThumb()
    )
}
