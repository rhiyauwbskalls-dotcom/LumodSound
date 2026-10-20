package com.sonaris.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.sonaris.app.data.AlbumUi
import com.sonaris.app.data.ArtistUi
import com.sonaris.app.data.AuthStore
import com.sonaris.app.data.FOR_YOU_ID
import com.sonaris.app.data.HOME_SHELVES
import com.sonaris.app.data.HistoryStore
import com.sonaris.app.data.LibraryStore
import com.sonaris.app.data.LrcLibClient
import com.sonaris.app.data.LyricsCache
import com.sonaris.app.player.CanvasCache
import com.sonaris.app.data.LyricsState
import com.sonaris.app.data.SpotifySettings
import com.sonaris.app.data.SpotifyService
import com.sonaris.app.data.SpotifyStore
import com.sonaris.app.data.Playlist
import com.sonaris.app.data.SearchCursor
import com.sonaris.app.data.ShelfSpec
import com.sonaris.app.data.SongInfo
import com.sonaris.app.data.SongUi
import com.sonaris.app.data.YtMusicApi
import com.sonaris.app.data.YtRepository
import com.sonaris.app.player.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

data class SearchState(
    val query: String = "",
    val results: List<SongUi> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val artists: List<ArtistUi> = emptyList(),
    val albums: List<AlbumUi> = emptyList(),
    /** Búsqueda en tiempo real: hay resultados a la vista y se está pidiendo la siguiente tanda. */
    val refreshing: Boolean = false
)

/** Pantalla de búsqueda vacía: rejilla "Explorar todo" con las categorías de YouTube Music. */
data class ExploreState(
    val categories: List<YtMusicApi.ExploreCategory> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** Portada de cada categoría (params -> url), se va rellenando según se ven en pantalla. */
    val thumbs: Map<String, String> = emptyMap(),
    val selected: YtMusicApi.ExploreCategory? = null,
    val shelves: List<Shelf> = emptyList(),
    val shelvesLoading: Boolean = false,
    val shelvesFailed: Boolean = false
)

data class PlayerState(
    val current: SongUi? = null,
    val isPlaying: Boolean = false,
    val isLoadingStream: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val shuffle: Boolean = false,
    val repeat: Int = 0 // 0 = no repetir, 1 = repetir lista, 2 = repetir canción
)

data class SongInfoState(
    val song: SongUi? = null,
    val loading: Boolean = false,
    val info: SongInfo? = null
)

data class ArtistState(
    val artist: ArtistUi? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val heroImage: String? = null,
    val description: String? = null,
    val subscribers: Long = -1,
    val popular: List<SongUi> = emptyList(),
    /** Todo junto mientras se averigua cuáles son álbumes y cuáles sencillos. */
    val releases: List<AlbumUi> = emptyList(),
    val classifying: Boolean = false,
    val albums: List<AlbumUi> = emptyList(),
    val singles: List<AlbumUi> = emptyList(),
    val popularFailed: Boolean = false,
    val releasesFailed: Boolean = false
)

data class AlbumState(
    val album: AlbumUi? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val tracks: List<SongUi> = emptyList()
)

/** Un elemento de la cola; el id es único aunque la misma canción esté dos veces. */
data class QueueEntry(val id: String, val song: SongUi)

data class QueueState(val entries: List<QueueEntry> = emptyList(), val index: Int = -1)

/** IDLE = todavía no se pidió (se pide cuando el estante aparece en pantalla). */
enum class ShelfStatus { IDLE, LOADING, LOADED, FAILED }

data class Shelf(
    val id: String,
    val title: String,
    val songs: List<SongUi> = emptyList(),
    val status: ShelfStatus = ShelfStatus.IDLE,
    /** Álbumes y listas (estantes del Inicio de YouTube Music). */
    val albums: List<AlbumUi> = emptyList(),
    /** Se muestra como columnas de 3 filas pequeñas (Selecciones rápidas). */
    val compact: Boolean = false
)

data class HomeState(
    val shelves: List<Shelf> = emptyList(),
    val forYou: Shelf? = null,
    /** Estantes recomendados por YouTube Music; si vienen, reemplazan a los de géneros. */
    val feed: List<Shelf> = emptyList(),
    /** true cuando ya terminó de pedirse el Inicio de YouTube (con o sin resultados). */
    val feedDone: Boolean = false,
    /** Chips de YouTube Music (Relajar, Energizar...); si no llegan, el Inicio usa los de géneros de siempre. */
    val chips: List<YtMusicApi.FeedChip> = emptyList(),
    /** params del chip elegido (null = ninguno) y los estantes que YouTube devolvió para él. */
    val selectedChip: String? = null,
    val chipShelves: List<Shelf> = emptyList(),
    val chipLoading: Boolean = false,
    val chipFailed: Boolean = false
)

private const val EXTRA_URL = "yt_url"
private const val MAX_QUEUE_WINDOW = 100

/** Cada canción viaja al servicio con su URL de YouTube; el servicio resuelve el audio al reproducir. */
private fun SongUi.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(UUID.randomUUID().toString())
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumb?.let { Uri.parse(it) })
                .setDurationMs(durationSec * 1000)
                .setExtras(Bundle().apply { putString(EXTRA_URL, url) })
                .build()
        )
        .build()

private fun MediaItem.toSong(): SongUi {
    val md = mediaMetadata
    return SongUi(
        url = md.extras?.getString(EXTRA_URL) ?: mediaId,
        title = md.title?.toString() ?: "Sin título",
        artist = md.artist?.toString() ?: "",
        durationSec = (md.durationMs ?: 0L) / 1000,
        thumb = md.artworkUri?.toString()
    )
}

private fun Int.toUiRepeat(): Int = when (this) {
    Player.REPEAT_MODE_ALL -> 1
    Player.REPEAT_MODE_ONE -> 2
    else -> 0
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = YtRepository()

    // Sesión de Google: con ella YouTube Music personaliza el Inicio y la selección rápida
    private val auth = AuthStore(app).also { YtMusicApi.cookie = it.cookie() }
    private val _loggedIn = MutableStateFlow(auth.cookie() != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var cursor: SearchCursor? = null
    private var searchJob: Job? = null
    private var typeJob: Job? = null
    private var categoryJob: Job? = null
    private var moreJob: Job? = null
    private var errorStreak = 0
    private var radioJob: Job? = null

    // Radio infinita: cuando quedan pocas canciones, la cola sigue sola con recomendaciones
    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _autoRadio = MutableStateFlow(settings.getBoolean("auto_radio", true))
    val autoRadio: StateFlow<Boolean> = _autoRadio.asStateFlow()
    private val _artist = MutableStateFlow(ArtistState())
    val artist: StateFlow<ArtistState> = _artist.asStateFlow()
    private val _album = MutableStateFlow(AlbumState())
    val album: StateFlow<AlbumState> = _album.asStateFlow()
    private var artistJob: Job? = null
    private var classifyJob: Job? = null
    private var albumJob: Job? = null
    private val artistCache = HashMap<String, ArtistState>()

    private var radioGen = 0
    private var extending = false
    private var holdRadio = false // true tras "vaciar siguientes": no rellenar la cola a los 2 segundos

    // Radio del artista: la cola solo se llena con canciones del mismo artista (null = radio normal)
    private var artistRadioPool: List<SongUi>? = null

    // Sesión de la radio normal de la cola: cada tanda continúa la anterior en vez de buscar otra vez
    private var radioSession: YtRepository.RadioSession? = null

    // Ahorro de batería: la barra de progreso solo se refresca con la app a la vista y sonando
    private val uiActive = MutableStateFlow(true)
    private val fastProgress = MutableStateFlow(false)

    // Inicio: los estantes se cargan de uno en uno y solo al aparecer en pantalla
    private val homeGate = Semaphore(2)
    private val shelfJobs = HashMap<String, Job>()
    private var forYouSeed: String? = null

    private val appCtx: Application = app
    private val lrclib = LrcLibClient()
    private val spotifyStore = SpotifyStore.get(app)
    private val spotify = SpotifyService(app)

    // Canvas de Spotify (video corto en bucle) de la canción que suena; null si no hay o está desactivado
    private val _canvas = MutableStateFlow<String?>(null)
    val canvas: StateFlow<String?> = _canvas.asStateFlow()
    private val canvasCache = HashMap<String, String?>()
    private var canvasKey: String? = null
    private var canvasJob: Job? = null
    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()
    private val lyricsCache = HashMap<String, LyricsState>()
    private var lyricsJob: Job? = null
    private var lyricsFor: String? = null

    private val library = LibraryStore(app)
    private val _favorites = MutableStateFlow(library.loadFavorites())
    val favorites: StateFlow<List<SongUi>> = _favorites.asStateFlow()
    private val _playlists = MutableStateFlow(library.loadPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val history = HistoryStore(app)
    private val _recents = MutableStateFlow(history.load())
    val recents: StateFlow<List<SongUi>> = _recents.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()
    private val _songInfo = MutableStateFlow(SongInfoState())
    val songInfo: StateFlow<SongInfoState> = _songInfo.asStateFlow()
    private var songInfoJob: Job? = null

    private var homeJob: Job? = null
    private var feedJob: Job? = null
    private var chipJob: Job? = null

    /** Escucha al reproductor del servicio: también reacciona a lo que se toca en la notificación. */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _player.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val c = controller
            if (playbackState == Player.STATE_READY) errorStreak = 0
            _player.update {
                it.copy(
                    isLoadingStream = playbackState == Player.STATE_BUFFERING,
                    durationMs = if (playbackState == Player.STATE_READY && c != null && c.duration > 0) c.duration
                    else it.durationMs
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.toSong()
            _player.update {
                it.copy(
                    current = song,
                    positionMs = 0,
                    durationMs = (song?.durationSec ?: 0L) * 1000,
                    error = null
                )
            }
            if (song != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) addToHistory(song)
            syncQueue()
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) holdRadio = false
            maybeExtendQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            syncQueue()
            maybeExtendQueue()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _player.update { it.copy(repeat = repeatMode.toUiRepeat()) }
            maybeExtendQueue()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _player.update { it.copy(shuffle = shuffleModeEnabled) }
            maybeExtendQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller
            errorStreak++
            // Si una canción falla, salta a la siguiente (máximo 3 fallos seguidos)
            if (c != null && errorStreak <= 3 && c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            } else {
                _player.update {
                    it.copy(isLoadingStream = false, error = "No se pudo reproducir (${error.errorCodeName})")
                }
            }
        }
    }

    init {
        connectController()
        startProgressLoop()
        loadHome()
    }

    // ---------- Conexión con el servicio de reproducción ----------

    private fun connectController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                syncAll(c)
            } catch (e: Exception) {
                _player.update { it.copy(error = "No se pudo iniciar el reproductor") }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun syncAll(c: MediaController) {
        val song = c.currentMediaItem?.toSong()
        _player.update {
            it.copy(
                current = song,
                isPlaying = c.isPlaying,
                isLoadingStream = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (c.duration > 0) c.duration else (song?.durationSec ?: 0L) * 1000,
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode.toUiRepeat()
            )
        }
        syncQueue()
    }

    private fun syncQueue() {
        val c = controller ?: return
        val entries = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueEntry(item.mediaId, item.toSong())
        }
        _queue.value = QueueState(entries, c.currentMediaItemIndex)
    }

    fun setUiActive(active: Boolean) {
        uiActive.value = active
    }

    /** La pantalla de letra necesita más precisión (250 ms); el resto basta con 500 ms. */
    fun setLyricsVisible(visible: Boolean) {
        fastProgress.value = visible
    }

    private fun syncPosition() {
        val c = controller ?: return
        val d = c.duration
        _player.update {
            it.copy(
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (d > 0) d else it.durationMs
            )
        }
    }

    /**
     * Solo hay "reloj" cuando la app está a la vista Y hay música sonando. En pausa, con la
     * pantalla apagada o en segundo plano no se despierta la CPU para nada.
     */
    private fun startProgressLoop() {
        viewModelScope.launch {
            combine(uiActive, _player.map { it.isPlaying }.distinctUntilChanged()) { ui, playing ->
                ui && playing
            }
                .distinctUntilChanged()
                .collectLatest { running ->
                    syncPosition()
                    if (running) {
                        while (true) {
                            delay(if (fastProgress.value) 250L else 500L)
                            syncPosition()
                        }
                    }
                }
        }
    }

    // ---------- Búsqueda ----------

    /** Cada letra que escribes busca sola (con una pequeña pausa para no pedir en cada tecla). */
    fun onQueryChange(q: String) {
        _search.update { it.copy(query = q) }
        typeJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            searchJob?.cancel()
            moreJob?.cancel()
            cursor = null
            _search.update {
                it.copy(
                    loading = false, loadingMore = false, refreshing = false, canLoadMore = false,
                    error = null, results = emptyList(), artists = emptyList(), albums = emptyList()
                )
            }
            return
        }
        if (trimmed.length < 2) return
        typeJob = viewModelScope.launch {
            delay(350)
            runSearch(live = true)
        }
    }

    /** Buscar al pulsar la lupa del teclado: al momento y sin esperar. */
    fun search() {
        typeJob?.cancel()
        runSearch(live = false)
    }

    private fun runSearch(live: Boolean) {
        val q = _search.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        moreJob?.cancel()
        cursor = null
        searchJob = viewModelScope.launch {
            // En vivo, los resultados anteriores se quedan a la vista hasta que lleguen los nuevos
            val keep = live && _search.value.results.isNotEmpty()
            _search.update {
                if (keep) it.copy(refreshing = true, loadingMore = false, canLoadMore = false, error = null)
                else it.copy(
                    loading = true, refreshing = false, loadingMore = false, canLoadMore = false,
                    error = null, results = emptyList(), artists = emptyList(), albums = emptyList()
                )
            }
            try {
                // Los artistas se piden aparte: si tardan o fallan, las canciones no se retrasan
                launch {
                    val found = try {
                        repo.searchArtists(q)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _search.update { it.copy(artists = found) }
                }
                // Álbumes también aparte, igual que los artistas
                launch {
                    val found = try {
                        repo.searchAlbums(q)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _search.update { it.copy(albums = found) }
                }
                val page = repo.search(q)
                cursor = page.cursor
                _search.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        results = page.items.distinctBy { s -> s.url },
                        canLoadMore = page.cursor != null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update {
                    it.copy(
                        loading = false, refreshing = false,
                        error = "No se pudo buscar: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val c = cursor ?: return
        val s = _search.value
        if (s.loading || s.loadingMore) return
        moreJob = viewModelScope.launch {
            _search.update { it.copy(loadingMore = true) }
            try {
                val page = repo.more(c)
                cursor = page.cursor
                _search.update { st ->
                    st.copy(
                        loadingMore = false,
                        results = (st.results + page.items).distinctBy { it.url },
                        canLoadMore = page.cursor != null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update { it.copy(loadingMore = false, canLoadMore = false) }
            }
        }
    }

    // ---------- Reproducción ----------

    /** Resultados de búsqueda: suena la elegida y lo que sigue son recomendaciones, no el resto de resultados. */
    fun playFromResults(index: Int) {
        val list = _search.value.results
        playFromDiscovery(list, index)
    }

    /** Búsqueda e Inicio: igual que YouTube Music, la cola se arma como "radio" de la canción elegida. */
    fun playFromDiscovery(list: List<SongUi>, index: Int) {
        val song = list.getOrNull(index) ?: return
        playRadio(song, fallback = list.drop(index + 1))
    }

    /**
     * Suena [song] de inmediato y arma la cola con recomendaciones (canciones relacionadas).
     * Solo si no hay recomendaciones se usa [fallback] para no quedarse sin música.
     */
    private fun playRadio(song: SongUi, fallback: List<SongUi>) {
        val c = controller
        if (c == null) {
            _player.update { it.copy(error = "Reproductor no listo, intenta de nuevo") }
            return
        }
        errorStreak = 0
        cancelRadio()
        _player.update {
            it.copy(
                current = song,
                isLoadingStream = true,
                error = null,
                positionMs = 0,
                durationMs = song.durationSec * 1000
            )
        }
        c.setMediaItems(listOf(song.toMediaItem()), 0, 0L)
        c.prepare()
        c.play()
        extendQueue(seeds = listOf(song), fallback = fallback)
    }

    /** Reproduce una canción de cualquier lista; la lista (hasta 100 canciones) pasa a ser la cola. */
    fun playFromList(list: List<SongUi>, index: Int) {
        if (index !in list.indices) return
        val c = controller
        if (c == null) {
            _player.update { it.copy(error = "Reproductor no listo, intenta de nuevo") }
            return
        }
        val song = list[index]
        errorStreak = 0
        cancelRadio()
        // Ventana alrededor de la elegida: la cola viaja por IPC y no conviene que sea enorme
        val from = maxOf(0, index - 20)
        val window = list.subList(from, minOf(list.size, from + MAX_QUEUE_WINDOW))
        _player.update {
            it.copy(
                current = song,
                isLoadingStream = true,
                error = null,
                positionMs = 0,
                durationMs = song.durationSec * 1000
            )
        }
        c.setMediaItems(window.map { it.toMediaItem() }, index - from, 0L)
        c.prepare()
        c.play()
    }

    private fun cancelRadio() {
        radioJob?.cancel()
        radioGen++
        extending = false
        holdRadio = false
        artistRadioPool = null
        radioSession = null
    }

    fun toggleAutoRadio() {
        val on = !_autoRadio.value
        _autoRadio.value = on
        holdRadio = false
        settings.edit().putBoolean("auto_radio", on).apply()
        if (on) maybeExtendQueue()
    }

    /**
     * Radio del artista: suena [song] y la cola se arma solo con canciones del mismo artista
     * (en orden aleatorio). Al acabarse, se vuelven a mezclar para que no termine.
     */
    fun startArtistRadio(song: SongUi) {
        val c = controller
        if (c == null) {
            _player.update { it.copy(error = "Reproductor no listo, intenta de nuevo") }
            return
        }
        errorStreak = 0
        cancelRadio()
        _player.update {
            it.copy(
                current = song,
                isLoadingStream = true,
                error = null,
                positionMs = 0,
                durationMs = song.durationSec * 1000
            )
        }
        c.setMediaItems(listOf(song.toMediaItem()), 0, 0L)
        c.prepare()
        c.play()

        val gen = radioGen
        extending = true
        radioJob = viewModelScope.launch {
            try {
                val pool = try {
                    loadArtistSongs(song)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                if (gen != radioGen) return@launch // cambió la cola mientras buscábamos
                if (pool.isEmpty()) {
                    _player.update { it.copy(error = "No se encontraron más canciones de ${song.artist}") }
                    return@launch
                }
                artistRadioPool = pool
                addFromArtistPool(50)
            } finally {
                if (gen == radioGen) extending = false
            }
        }
    }

    /** "A, B & C" / "A feat. B" -> "A": el artista principal de la canción. */
    private fun primaryArtist(name: String): String =
        name.split(Regex("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b)\\s*", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim().orEmpty().ifBlank { name.trim() }

    /** Canciones del artista de [song]: su página de YouTube Music y, si son pocas, una búsqueda de respaldo. */
    private suspend fun loadArtistSongs(song: SongUi): List<SongUi> {
        val name = primaryArtist(song.artist)
        if (name.isBlank()) return emptyList()
        val key = name.lowercase()
        val found = repo.searchArtists(name)
        val artist = found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull()

        val fromPage = if (artist != null) {
            try {
                repo.artistPage(artist).popular
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        var all = fromPage
        if (all.size < 20) {
            val extra = try {
                repo.search(name).items.filter { it.artist.lowercase().contains(key) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            all = all + extra
        }
        return all.distinctBy { it.url }.filter { it.url != song.url }
    }

    /** Agrega hasta [count] canciones del artista a la cola, sin repetir las que ya están. */
    private fun addFromArtistPool(count: Int) {
        val ctl = controller ?: return
        val pool = artistRadioPool ?: return
        val inQueue = (0 until ctl.mediaItemCount).map { ctl.getMediaItemAt(it).toSong().url }.toSet()
        var candidates = pool.filter { it.url !in inQueue }.shuffled()
        if (candidates.isEmpty()) {
            // Ya se usaron todas: se vuelven a mezclar, evitando lo último que sonó
            val recent = _recents.value.take(15).map { it.url }.toSet()
            val current = ctl.currentMediaItem?.toSong()?.url
            candidates = pool.filter { it.url !in recent && it.url != current }.shuffled()
                .ifEmpty { pool.filter { it.url != current }.shuffled() }
        }
        val toAdd = candidates.take(count)
        if (toAdd.isEmpty()) return
        ctl.addMediaItems(toAdd.map { it.toMediaItem() })
        if (ctl.playbackState == Player.STATE_ENDED && ctl.hasNextMediaItem()) {
            ctl.seekToNextMediaItem()
            ctl.play()
        }
        val drop = ctl.currentMediaItemIndex - 20
        if (drop > 0) ctl.removeMediaItems(0, drop)
    }

    /** Radio infinita: si quedan 2 canciones o menos (y no hay repetir), agrega más parecidas. */
    private fun maybeExtendQueue() {
        if (!_autoRadio.value || holdRadio) return
        val c = controller ?: return
        if (extending || c.mediaItemCount == 0) return
        if (c.repeatMode != Player.REPEAT_MODE_OFF) return
        val remaining = c.mediaItemCount - c.currentMediaItemIndex - 1
        val needsMore = if (c.shuffleModeEnabled) !c.hasNextMediaItem() else remaining <= 2
        if (needsMore) {
            if (artistRadioPool != null) addFromArtistPool(25)
            else extendQueue(seeds = null, fallback = emptyList())
        }
    }

    /**
     * Busca canciones relacionadas con [seeds] (o con las últimas de la cola) y las agrega al final,
     * sin repetir lo que ya está en la cola ni lo último que escuchaste.
     */
    private fun extendQueue(seeds: List<SongUi>?, fallback: List<SongUi>) {
        val c = controller ?: return
        if (extending) return
        extending = true
        val gen = radioGen
        val queueSongs = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
        val seedList = seeds ?: listOfNotNull(
            queueSongs.lastOrNull(),
            c.currentMediaItem?.toSong(),
            queueSongs.firstOrNull()
        ).distinctBy { it.url }

        radioJob = viewModelScope.launch {
            try {
                val seen = HashSet<String>()
                seen.addAll(queueSongs.map { it.url })
                seen.addAll(seedList.map { it.url })
                seen.addAll(_recents.value.take(15).map { it.url })

                var found: List<SongUi> = emptyList()
                var usedSession: YtRepository.RadioSession? = null

                // Pide páginas de una sesión hasta juntar ~30 canciones nuevas (máx. 4 páginas)
                suspend fun pull(session: YtRepository.RadioSession): List<SongUi> {
                    val out = ArrayList<SongUi>()
                    var pages = 0
                    while (out.size < 30 && pages++ < 4 && !session.exhausted) {
                        val batch = try {
                            repo.radioMore(session)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            break
                        }
                        out += batch.filter { seen.add(it.url) }
                    }
                    return out
                }

                // 1) Seguir la radio en curso: cada página continúa la anterior (como Metrolist)
                radioSession?.takeIf { !it.exhausted }?.let { s ->
                    found = pull(s)
                    if (found.isNotEmpty()) usedSession = s
                }
                // 2) Radio nueva desde la última canción de la cola (o la actual / la primera)
                if (found.isEmpty()) {
                    for (seed in seedList) {
                        val vid = seed.url.substringAfter("v=", "").substringBefore('&')
                        if (vid.isBlank()) continue
                        val s = YtRepository.RadioSession(vid)
                        found = pull(s)
                        if (found.isNotEmpty()) {
                            usedSession = s
                            break
                        }
                    }
                }
                // 3) Respaldo: el método anterior, con sus filtros
                if (found.isEmpty()) {
                    for (seed in seedList) {
                        val related = try {
                            repo.related(seed.url, seed.title)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                        found = related.filter { seen.add(it.url) }
                        if (found.isNotEmpty()) break
                    }
                }
                if (found.isEmpty()) found = fallback.filter { seen.add(it.url) }
                if (found.isEmpty()) {
                    // Último recurso: más canciones del mismo artista
                    val artist = seedList.firstOrNull()?.artist.orEmpty()
                    if (artist.isNotBlank()) {
                        val byArtist = try {
                            repo.search(artist).items
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                        found = byArtist.filter { seen.add(it.url) }
                    }
                }

                val ctl = controller ?: return@launch
                if (gen != radioGen) return@launch // cambió la cola mientras buscábamos
                radioSession = usedSession
                val inQueue = (0 until ctl.mediaItemCount).map { ctl.getMediaItemAt(it).toSong().url }.toSet()
                val toAdd = found.filter { it.url !in inQueue }.take(50)
                if (toAdd.isEmpty()) return@launch

                ctl.addMediaItems(toAdd.map { it.toMediaItem() })
                // Si la cola ya había terminado, reanuda con lo nuevo
                if (ctl.playbackState == Player.STATE_ENDED && ctl.hasNextMediaItem()) {
                    ctl.seekToNextMediaItem()
                    ctl.play()
                }
                // La cola viaja completa al servicio: se olvidan las ya escuchadas para que no crezca sin fin
                val drop = ctl.currentMediaItemIndex - 20
                if (drop > 0) ctl.removeMediaItems(0, drop)
            } finally {
                if (gen == radioGen) extending = false
            }
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        when (c.playbackState) {
            Player.STATE_ENDED -> {
                c.seekToDefaultPosition(0)
                c.play()
            }
            Player.STATE_IDLE -> {
                c.prepare()
                c.play()
            }
            else -> if (c.playWhenReady) c.pause() else c.play()
        }
    }

    fun playNext() {
        controller?.seekToNextMediaItem()
    }

    fun playPrevious() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _player.update { it.copy(positionMs = ms) }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---------- Cola de reproducción ----------

    fun playQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        errorStreak = 0
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) {
            c.moveMediaItem(from, to)
        }
    }

    /** Quita todo lo que viene después de la canción actual. */
    fun clearUpcoming() {
        val c = controller ?: return
        cancelRadio()
        holdRadio = true
        val start = c.currentMediaItemIndex + 1
        if (start < c.mediaItemCount) c.removeMediaItems(start, c.mediaItemCount)
    }

    /** "Reproducir a continuación": entra justo después de la actual (o suena ya si no hay nada). */
    fun playNextInQueue(song: SongUi) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playFromList(listOf(song), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun addToQueue(song: SongUi) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playFromList(listOf(song), 0)
        } else {
            c.addMediaItem(song.toMediaItem())
        }
    }

    // ---------- Artistas y álbumes ----------

    fun openArtist(artist: ArtistUi) = startArtist(artist.name, artist)

    /** Desde el menú de una canción: busca al artista por nombre (el primero si hay varios). */
    fun openArtistByName(rawName: String) {
        val name = rawName.split(",", " & ", " x ", " feat.", " ft.").first().trim()
        if (name.isNotBlank()) startArtist(name, null)
    }

    /** Botón "Reintentar" de la pantalla de artista: vuelve a pedir todo sin usar lo guardado. */
    fun retryArtist() {
        val a = _artist.value.artist ?: return
        artistCache.remove(a.url)
        startArtist(a.name, a.takeIf { it.url.isNotBlank() })
    }

    fun openArtistOfAlbum(album: AlbumUi) {
        val url = album.artistUrl
        if (!url.isNullOrBlank()) startArtist(album.artist, ArtistUi(url, album.artist, null, -1))
        else openArtistByName(album.artist)
    }

    private fun startArtist(name: String, known: ArtistUi?) {
        artistJob?.cancel()
        classifyJob?.cancel()
        if (known != null) {
            val cached = artistCache[known.url]
            if (cached != null) {
                _artist.value = cached
                return
            }
        }
        _artist.value = ArtistState(artist = known ?: ArtistUi("", name, null, -1), loading = true)
        artistJob = viewModelScope.launch {
            try {
                val artist = known ?: repo.searchArtists(name).let { found ->
                    found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull()
                }
                if (artist == null) {
                    _artist.value = ArtistState(artist = ArtistUi("", name, null, -1), error = true)
                    return@launch
                }
                val cached = artistCache[artist.url]
                if (cached != null) {
                    _artist.value = cached
                    return@launch
                }
                val page = repo.artistPage(artist)
                _artist.value = ArtistState(
                    artist = page.artist,
                    heroImage = page.hero,
                    description = page.description,
                    subscribers = page.subscribers,
                    popular = page.popular,
                    releases = page.releases,
                    classifying = page.albums == null && page.releases.isNotEmpty(),
                    albums = page.albums.orEmpty(),
                    singles = page.singles.orEmpty(),
                    popularFailed = page.popularFailed,
                    releasesFailed = page.releasesFailed
                )
                if (page.albums != null || page.releases.isEmpty()) {
                    // Solo se recuerda si no hubo errores: una página incompleta no debe quedarse "guardada"
                    if (!page.popularFailed && !page.releasesFailed) artistCache[artist.url] = _artist.value
                } else {
                    classifyReleases(artist.url, page.releases)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _artist.value = ArtistState(artist = known ?: ArtistUi("", name, null, -1), error = true)
            }
        }
    }

    /**
     * YouTube Music no dice si un lanzamiento es álbum o sencillo, así que se deduce por el número de
     * canciones (hasta 6 = sencillo o EP). Se piden de 3 en 3 y como máximo 14, solo al abrir al artista.
     */
    private fun classifyReleases(artistUrl: String, releases: List<AlbumUi>) {
        classifyJob?.cancel()
        classifyJob = viewModelScope.launch {
            val gate = Semaphore(3)
            val counts = coroutineScope {
                releases.take(14).map { r ->
                    async {
                        gate.withPermit {
                            val n = try {
                                repo.albumTracks(r.url).size
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            r.url to n
                        }
                    }
                }.awaitAll()
            }.toMap()

            val albums = ArrayList<AlbumUi>()
            val singles = ArrayList<AlbumUi>()
            releases.forEach { r ->
                val n = counts[r.url]
                val item = r.copy(trackCount = n)
                if (n != null && n <= 6) singles.add(item) else albums.add(item)
            }
            _artist.update { st ->
                if (st.artist?.url != artistUrl) st
                else st.copy(classifying = false, albums = albums, singles = singles)
            }
            val done = _artist.value
            if (done.artist?.url == artistUrl && !done.popularFailed && !done.releasesFailed) {
                artistCache[artistUrl] = done
            }
        }
    }

    /** Álbum de una canción para el menú de tres puntos; null si no tiene o no se pudo averiguar. */
    suspend fun findAlbum(song: SongUi): AlbumUi? =
        try {
            repo.albumOfSong(song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Abre la información de una canción: lo básico sale al instante y el resto se completa al llegar. */
    fun openSongInfo(song: SongUi) {
        songInfoJob?.cancel()
        _songInfo.value = SongInfoState(song = song, loading = true)
        songInfoJob = viewModelScope.launch {
            val info = try {
                repo.songInfo(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _songInfo.value = SongInfoState(song = song, loading = false, info = info)
        }
    }

    fun openAlbum(album: AlbumUi) {
        albumJob?.cancel()
        _album.value = AlbumState(album = album, loading = true)
        albumJob = viewModelScope.launch {
            try {
                val tracks = repo.albumTracks(album.url)
                _album.value = AlbumState(album = album.copy(trackCount = tracks.size), tracks = tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _album.value = AlbumState(album = album, error = true)
            }
        }
    }

    // ---------- Letras (LRCLIB) ----------

    fun loadLyrics(song: SongUi, force: Boolean = false) {
        if (!force && lyricsFor == song.url) return
        lyricsFor = song.url
        lyricsJob?.cancel()
        val cached = lyricsCache[song.url]
        if (cached != null && !force) {
            _lyrics.value = cached
            return
        }
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            if (!force) {
                // ¿Ya se bajó antes? Sale de disco al instante y sin internet
                val onDisk = withContext(Dispatchers.IO) { LyricsCache.get(appCtx, song.url) }
                if (onDisk != null) {
                    lyricsCache[song.url] = onDisk
                    _lyrics.value = onDisk
                    return@launch
                }
            }
            val result: LyricsState = try {
                val sp = spotifyStore.settings.value
                // Spotify primero (si está conectado y activado); si no trae letra sincronizada, LRCLIB
                val fromSpotify = if (sp.connected && sp.lyrics) spotify.lyrics(song) else null
                if (fromSpotify is LyricsState.Synced) {
                    fromSpotify
                } else {
                    val fromLrc = lrclib.fetch(song.title, song.artist, song.durationSec)
                    if (fromLrc is LyricsState.Synced) fromLrc
                    else if (fromSpotify is LyricsState.Plain) fromSpotify
                    else fromLrc
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LyricsState.Error
            }
            if (result !is LyricsState.Error) lyricsCache[song.url] = result
            if (result is LyricsState.Synced || result is LyricsState.Plain || result is LyricsState.Instrumental) {
                withContext(Dispatchers.IO) { LyricsCache.put(appCtx, song.url, result) }
            }
            _lyrics.value = result
        }
    }

    // ---------- Canvas (Spotify) ----------

    fun loadCanvas(song: SongUi) {
        val sp: SpotifySettings = spotifyStore.settings.value
        val enabled = sp.connected && sp.canvas
        val key = "${song.url}|$enabled"
        if (key == canvasKey) return
        canvasKey = key
        canvasJob?.cancel()
        if (!enabled) {
            _canvas.value = null
            return
        }
        if (canvasCache.containsKey(song.url)) {
            _canvas.value = canvasCache[song.url]
            return
        }
        _canvas.value = null
        canvasJob = viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { CanvasCache.getSaved(appCtx, song.url) }
            if (saved != null && saved.fresh) {
                // dirección guardada hace poco: no hace falta preguntar a Spotify
                canvasCache[song.url] = saved.url
                _canvas.value = saved.url
                return@launch
            }
            val fetched = try {
                spotify.canvas(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // Si Spotify no respondió (sin internet, etc.) y había una dirección guardada que aún sirve, se usa esa
            val url = fetched ?: saved?.takeIf { it.usable }?.url
            withContext(Dispatchers.IO) {
                if (fetched != null) CanvasCache.saveUrl(appCtx, song.url, fetched)
                else if (saved != null && !saved.usable) CanvasCache.forgetUrl(appCtx, song.url)
            }
            canvasCache[song.url] = url
            _canvas.value = url
        }
    }

    // ---------- Biblioteca (favoritos y playlists) ----------

    fun toggleFavorite(song: SongUi) {
        val cur = _favorites.value
        val updated = if (cur.any { it.url == song.url }) cur.filter { it.url != song.url }
        else listOf(song) + cur
        _favorites.value = updated
        library.saveFavorites(updated)
    }

    fun createPlaylist(name: String, firstSong: SongUi? = null) {
        val p = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Mi playlist" },
            songs = listOfNotNull(firstSong)
        )
        savePlaylists(listOf(p) + _playlists.value)
    }

    fun addToPlaylist(id: String, song: SongUi) = savePlaylists(
        _playlists.value.map { p ->
            if (p.id == id && p.songs.none { it.url == song.url }) p.copy(songs = p.songs + song) else p
        }
    )

    fun removeFromPlaylist(id: String, song: SongUi) = savePlaylists(
        _playlists.value.map { p ->
            if (p.id == id) p.copy(songs = p.songs.filter { it.url != song.url }) else p
        }
    )

    fun deletePlaylist(id: String) = savePlaylists(_playlists.value.filter { it.id != id })

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        library.savePlaylists(list)
    }

    // ---------- Inicio (recomendaciones) ----------

    private fun addToHistory(song: SongUi) {
        val updated = (listOf(song) + _recents.value.filter { it.url != song.url }).take(30)
        _recents.value = updated
        history.save(updated)
    }

    /** Busca desde un chip de género. */
    fun searchFor(query: String) {
        _search.update { it.copy(query = query) }
        search()
    }

    // ---------- Explorar (estados de ánimo y géneros de YouTube Music) ----------

    private val _explore = MutableStateFlow(ExploreState())
    val explore: StateFlow<ExploreState> = _explore.asStateFlow()

    private val categoryCache = HashMap<String, List<YtMusicApi.FeedShelf>>()
    private val thumbTried = HashSet<String>()
    private val categorySem = Semaphore(3)

    fun loadExplore(force: Boolean = false) {
        val s = _explore.value
        if (s.loading || (!force && s.categories.isNotEmpty())) return
        _explore.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val cats = try {
                repo.exploreCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _explore.update { it.copy(categories = cats, loading = false, failed = cats.isEmpty()) }
        }
    }

    private fun thumbOf(shelves: List<YtMusicApi.FeedShelf>): String? =
        shelves.firstNotNullOfOrNull { s ->
            s.albums.firstNotNullOfOrNull { it.thumb } ?: s.songs.firstNotNullOfOrNull { it.thumb }
        }

    /** Se pide cuando una tarjeta se ve en pantalla: baja la categoría (3 a la vez) y usa su primera portada. */
    fun ensureCategoryThumb(cat: YtMusicApi.ExploreCategory) {
        if (!thumbTried.add(cat.params)) return
        viewModelScope.launch {
            categorySem.withPermit {
                val shelves = categoryCache[cat.params] ?: try {
                    repo.categoryFeed(cat).also { if (it.isNotEmpty()) categoryCache[cat.params] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val url = thumbOf(shelves)
                if (url != null) _explore.update { it.copy(thumbs = it.thumbs + (cat.params to url)) }
            }
        }
    }

    private fun toShelves(list: List<YtMusicApi.FeedShelf>): List<Shelf> =
        list.mapIndexed { i, s -> Shelf("cat-$i", s.title, s.songs, ShelfStatus.LOADED, s.albums, s.compact) }

    fun openCategory(cat: YtMusicApi.ExploreCategory) {
        categoryJob?.cancel()
        val cached = categoryCache[cat.params]
        if (cached != null) {
            _explore.update {
                it.copy(selected = cat, shelves = toShelves(cached), shelvesLoading = false, shelvesFailed = false)
            }
            return
        }
        _explore.update { it.copy(selected = cat, shelves = emptyList(), shelvesLoading = true, shelvesFailed = false) }
        categoryJob = viewModelScope.launch {
            val shelves = try {
                repo.categoryFeed(cat)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (shelves.isNotEmpty()) categoryCache[cat.params] = shelves
            _explore.update {
                if (it.selected?.params != cat.params) it
                else it.copy(shelves = toShelves(shelves), shelvesLoading = false, shelvesFailed = shelves.isEmpty())
            }
        }
    }

    fun closeCategory() {
        categoryJob?.cancel()
        _explore.update { it.copy(selected = null, shelves = emptyList(), shelvesLoading = false, shelvesFailed = false) }
    }

    /**
     * Deja los estantes listos pero NO descarga nada: cada uno se pide con [ensureShelf]
     * cuando aparece en pantalla. Al actualizar se conservan las canciones viejas a la vista
     * hasta que lleguen las nuevas (sin parpadeos ni todo de golpe).
     */
    /** Llega la cookie del inicio de sesión: se guarda y el Inicio se vuelve a pedir ya como tu cuenta. */
    fun onLoginSuccess(cookie: String) {
        auth.save(cookie)
        YtMusicApi.cookie = cookie
        _loggedIn.value = true
        reloadHomeForAccount()
    }

    fun logout() {
        auth.clear()
        YtMusicApi.cookie = null
        _loggedIn.value = false
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        reloadHomeForAccount()
    }

    private fun reloadHomeForAccount() {
        _home.update { it.copy(feed = emptyList(), feedDone = false) }
        loadHome(force = true)
    }

    fun loadHome(force: Boolean = false) {
        if (!force && _home.value.shelves.isNotEmpty()) return
        shelfJobs.values.forEach { it.cancel() }
        shelfJobs.clear()

        val old = _home.value

        _home.value = HomeState(
            shelves = HOME_SHELVES.map { spec ->
                Shelf(
                    spec.id,
                    spec.title,
                    songs = old.shelves.firstOrNull { it.id == spec.id }?.songs.orEmpty()
                )
            },
            feed = old.feed, // se conserva a la vista hasta que lleguen los nuevos
            feedDone = old.feedDone,
            chips = old.chips
        )
        loadFeed()
    }

    /**
     * Estantes recomendados por YouTube Music (FEmusic_home). Se van mostrando según llegan; lo demás
     * del Inicio ("Parecido a...", géneros) espera a que termine para que la pantalla no salte.
     */
    private fun loadFeed() {
        feedJob?.cancel()
        val hadFeed = _home.value.feed.isNotEmpty()
        feedJob = viewModelScope.launch {
            fun apply(shelves: List<YtMusicApi.FeedShelf>) {
                _home.update { h ->
                    h.copy(
                        feed = shelves.mapIndexed { i, s ->
                            Shelf("feed-$i", s.title, s.songs, ShelfStatus.LOADED, s.albums, s.compact)
                        }
                    )
                }
            }
            val all = try {
                // Si ya había estantes (al actualizar), se reemplazan solo al final para evitar parpadeos
                repo.homeFeed(
                    onProgress = { partial -> if (!hadFeed && partial.isNotEmpty()) apply(partial) },
                    onChips = { chips -> _home.update { it.copy(chips = chips) } }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (all.isNotEmpty()) apply(all)
            _home.update { it.copy(feedDone = true) }
        }
    }

    /** Toca un chip del Inicio: muestra lo que YouTube Music recomienda para él. Tocarlo otra vez lo quita. */
    fun selectChip(chip: YtMusicApi.FeedChip?) {
        chipJob?.cancel()
        if (chip == null || chip.params == _home.value.selectedChip) {
            _home.update { it.copy(selectedChip = null, chipShelves = emptyList(), chipLoading = false, chipFailed = false) }
            return
        }
        _home.update {
            it.copy(selectedChip = chip.params, chipShelves = emptyList(), chipLoading = true, chipFailed = false)
        }
        chipJob = viewModelScope.launch {
            val shelves = try {
                repo.homeChipFeed(chip)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _home.update { h ->
                if (h.selectedChip != chip.params) h
                else h.copy(
                    chipShelves = shelves.mapIndexed { i, s ->
                        Shelf("chip-$i", s.title, s.songs, ShelfStatus.LOADED, s.albums, s.compact)
                    },
                    chipLoading = false,
                    chipFailed = shelves.isEmpty()
                )
            }
        }
    }

    /**
     * Carga un estante si todavía está pendiente y adelanta los dos siguientes, para que al
     * deslizar ya estén listos. Hasta 2 cargas a la vez (no las 10 de golpe).
     */
    fun ensureShelf(id: String) {
        startShelf(id)
        val order = HOME_SHELVES.map { it.id }
        val i = order.indexOf(id)
        if (i >= 0) order.drop(i + 1).take(2).forEach { startShelf(it) }
    }

    private fun startShelf(id: String) {
        val h = _home.value
        val shelf = if (id == FOR_YOU_ID) h.forYou else h.shelves.firstOrNull { it.id == id }
        if (shelf == null || shelf.status != ShelfStatus.IDLE) return
        updateShelf(id) { it.copy(status = ShelfStatus.LOADING) }
        shelfJobs[id] = viewModelScope.launch {
            if (id == FOR_YOU_ID) {
                // "Parecido a..." va aparte para no quitarle el turno a los demás estantes
                val seed = _recents.value.firstOrNull()
                if (seed != null) loadForYou(seed)
                else updateShelf(id) { it.copy(status = ShelfStatus.FAILED) }
            } else {
                homeGate.withPermit {
                    HOME_SHELVES.firstOrNull { it.id == id }?.let { loadShelf(it) }
                }
            }
        }
    }

    fun retryShelf(id: String) {
        updateShelf(id) { it.copy(status = ShelfStatus.IDLE) }
        startShelf(id)
    }

    private suspend fun loadShelf(spec: ShelfSpec) {
        try {
            val songs = if (spec.query == null) repo.trendingMusic() else repo.search(spec.query).items
            val clean = songs.distinctBy { it.url }.take(20)
            updateShelf(spec.id) {
                it.copy(
                    songs = if (clean.isEmpty()) it.songs else clean,
                    status = if (clean.isEmpty() && it.songs.isEmpty()) ShelfStatus.FAILED else ShelfStatus.LOADED
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateShelf(spec.id) { it.copy(status = ShelfStatus.FAILED) }
        }
    }

    private suspend fun loadForYou(seed: SongUi) {
        try {
            val songs = repo.related(seed.url, seed.title)
                .filter { it.url != seed.url }
                .distinctBy { it.url }
                .take(20)
            updateShelf(FOR_YOU_ID) {
                it.copy(
                    songs = if (songs.isEmpty()) it.songs else songs,
                    status = if (songs.isEmpty() && it.songs.isEmpty()) ShelfStatus.FAILED else ShelfStatus.LOADED
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateShelf(FOR_YOU_ID) { it.copy(status = ShelfStatus.FAILED) }
        }
    }

    private fun updateShelf(id: String, change: (Shelf) -> Shelf) {
        _home.update { h ->
            if (id == FOR_YOU_ID) h.copy(forYou = h.forYou?.let(change))
            else h.copy(shelves = h.shelves.map { if (it.id == id) change(it) else it })
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
