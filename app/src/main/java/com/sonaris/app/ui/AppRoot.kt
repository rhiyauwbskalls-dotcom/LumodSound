package com.sonaris.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonaris.app.MainViewModel
import com.sonaris.app.data.DiscordAuth
import com.sonaris.app.data.DiscordStore
import com.sonaris.app.data.LastfmStore
import com.sonaris.app.data.LyricsState
import com.sonaris.app.data.SpotifyService
import com.sonaris.app.data.SpotifyStore
import com.sonaris.app.data.TranslationStore
import com.sonaris.app.data.SongUi
import com.sonaris.app.player.Downloads
import com.sonaris.app.player.SleepTimer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val search by vm.search.collectAsStateWithLifecycle()
    val explore by vm.explore.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val autoRadio by vm.autoRadio.collectAsStateWithLifecycle()
    val artistState by vm.artist.collectAsStateWithLifecycle()
    val albumState by vm.album.collectAsStateWithLifecycle()
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val songInfoState by vm.songInfo.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val appContext = LocalContext.current.applicationContext
    val discordStore = remember { DiscordStore.get(appContext) }
    val discordSettings by discordStore.settings.collectAsStateWithLifecycle()
    val lastfmStore = remember { LastfmStore.get(appContext) }
    val lastfmSettings by lastfmStore.settings.collectAsStateWithLifecycle()
    val spotifyStore = remember { SpotifyStore.get(appContext) }
    val spotifyService = remember { SpotifyService(appContext) }
    val spotifySettings by spotifyStore.settings.collectAsStateWithLifecycle()
    val translationStore = remember { TranslationStore.get(appContext) }
    val translationSettings by translationStore.settings.collectAsStateWithLifecycle()
    val canvasUrl by vm.canvas.collectAsStateWithLifecycle()

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    val sleepTimer by SleepTimer.state.collectAsStateWithLifecycle()

    // Descargas: el gestor se crea al abrir la app para conocer lo que ya está descargado
    val downloadStates by Downloads.states.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        try {
            Downloads.ensureReady(appContext)
        } catch (e: Exception) {
            // sin gestor de descargas: el resto de la app sigue funcionando
        }
    }
    var artistOpen by rememberSaveable { mutableStateOf(false) }
    var albumOpen by rememberSaveable { mutableStateOf(false) }
    var loginOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var discordOpen by rememberSaveable { mutableStateOf(false) }
    var lastfmOpen by rememberSaveable { mutableStateOf(false) }
    var spotifyOpen by rememberSaveable { mutableStateOf(false) }
    var spotifyLoginOpen by rememberSaveable { mutableStateOf(false) }
    var translationOpen by rememberSaveable { mutableStateOf(false) }
    var songInfoOpen by rememberSaveable { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var libraryDetail by rememberSaveable { mutableStateOf<String?>(null) }

    var menu by remember { mutableStateOf<SongMenuTarget?>(null) }
    var pickFor by remember { mutableStateOf<SongUi?>(null) }
    var newPlaylist by remember { mutableStateOf<NewPlaylistRequest?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(msg: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    // Al bajar por una lista la barra se esconde y el reproductor pequeño ocupa su lugar; al subir vuelve
    var barsHidden by remember { mutableStateOf(false) }
    val barsScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -12f) barsHidden = true
                else if (available.y > 12f) barsHidden = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(tab, albumOpen, artistOpen, showPlayer) { barsHidden = false }
    val hideFrac by animateFloatAsState(
        targetValue = if (barsHidden) 1f else 0f,
        animationSpec = tween(300),
        label = "barsHide"
    )

    // La posición de la letra va aparte: así las letras no recomponen toda la pantalla en cada tick
    val lyricsPosition = remember { mutableLongStateOf(0L) }
    SideEffect { lyricsPosition.longValue = player.positionMs }

    // Con letras o cola encima, el reproductor grande queda tapado: se le congela la posición
    // para que no se redibuje 2-4 veces por segundo sin que nadie lo vea.
    val bigPlayerCovered = showLyrics || showQueue
    val frozenPositionMs = remember(bigPlayerCovered) { player.positionMs }
    val bigPlayerState = if (bigPlayerCovered) player.copy(positionMs = frozenPositionMs) else player

    CompositionLocalProvider(LocalSongMenu provides { target -> menu = target }) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(barsScroll)
            ) { scaffoldPadding ->
                // Las barras ahora flotan sobre el contenido: se les deja el espacio por abajo
                // El espacio reservado se anima junto con las barras: sin nada sonando y con la barra
                // escondida, el contenido ocupa hasta abajo (sin banda negra).
                val hasMini = player.current != null
                val miniSpace = if (hasMini) MiniPlayerHeight + FloatingGap else 0.dp
                val shownSpace = FloatingMargin + FloatingNavHeight + FloatingGap + miniSpace
                val hiddenSpace = if (hasMini) FloatingMargin + miniSpace else 0.dp
                val barsHeight = lerp(shownSpace, hiddenSpace, hideFrac)
                val padding = PaddingValues(
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding() + barsHeight
                )
                val belowBar = Modifier.padding(bottom = padding.calculateBottomPadding())
                if (albumOpen) {
                    AlbumScreen(
                        state = albumState,
                        currentUrl = player.current?.url,
                        onBack = { albumOpen = false },
                        onPlay = vm::playFromList,
                        onArtistClick = {
                            albumState.album?.let { vm.openArtistOfAlbum(it) }
                            albumOpen = false
                            artistOpen = true
                        },
                        modifier = belowBar
                    )
                } else if (artistOpen) {
                    ArtistScreen(
                        state = artistState,
                        currentUrl = player.current?.url,
                        onBack = { artistOpen = false },
                        onPlay = vm::playFromList,
                        onAlbumClick = { album ->
                            vm.openAlbum(album)
                            albumOpen = true
                        },
                        onRetry = vm::retryArtist,
                        modifier = belowBar
                    )
                } else when (tab) {
                    0 -> HomeScreen(
                        home = home,
                        recents = recents,
                        currentUrl = player.current?.url,
                        onSongClick = vm::playFromDiscovery,
                        onAlbumClick = { album ->
                            vm.openAlbum(album)
                            albumOpen = true
                        },
                        onGenreClick = { q ->
                            vm.searchFor(q)
                            tab = 1
                            artistOpen = false
                            albumOpen = false
                        },
                        onChipClick = { vm.selectChip(it) },
                        onRefresh = { vm.loadHome(force = true) },
                        onRetry = vm::retryShelf,
                        onLoadShelf = vm::ensureShelf,
                        loggedIn = loggedIn,
                        onAccountClick = { accountSheet = true },
                        onSettingsClick = { settingsOpen = true },
                        modifier = Modifier.padding(padding)
                    )

                    1 -> SearchScreen(
                        state = search,
                        currentUrl = player.current?.url,
                        onQueryChange = vm::onQueryChange,
                        onSearch = vm::search,
                        onSongClick = vm::playFromResults,
                        onLoadMore = vm::loadMore,
                        onArtistClick = { artist ->
                            vm.openArtist(artist)
                            artistOpen = true
                            albumOpen = false
                        },
                        onAlbumClick = { album ->
                            vm.openAlbum(album)
                            albumOpen = true
                        },
                        explore = explore,
                        onLoadExplore = { force -> vm.loadExplore(force) },
                        onNeedThumb = vm::ensureCategoryThumb,
                        onCategoryClick = vm::openCategory,
                        onCategoryBack = vm::closeCategory,
                        onShelfSongClick = vm::playFromDiscovery,
                        modifier = Modifier.padding(padding)
                    )

                    else -> LibraryScreen(
                        favorites = favorites,
                        playlists = playlists,
                        currentUrl = player.current?.url,
                        detail = libraryDetail,
                        onOpenDetail = { libraryDetail = it },
                        onPlay = vm::playFromList,
                        onNewPlaylist = { newPlaylist = NewPlaylistRequest(null) },
                        onDeletePlaylist = vm::deletePlaylist,
                        onRemoveFromPlaylist = vm::removeFromPlaylist,
                        modifier = Modifier.padding(padding)
                    )
                }
            }

            // Barras flotantes: reproductor pequeño arriba y navegación abajo
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = FloatingMargin)
                    .padding(bottom = FloatingMargin),
                verticalArrangement = Arrangement.spacedBy(FloatingGap)
            ) {
                if (player.current != null) {
                    MiniPlayer(
                        state = player,
                        onOpen = { showPlayer = true },
                        onToggle = vm::togglePlay,
                        onNext = vm::playNext,
                        onPrevious = vm::playPrevious,
                        // Al esconderse la navegación, baja a ocupar su lugar
                        modifier = Modifier.graphicsLayer {
                            translationY = (FloatingNavHeight + FloatingGap).toPx() * hideFrac
                        }
                    )
                }
                Box(Modifier.height(FloatingNavHeight)) {
                    if (hideFrac < 0.98f) {
                        FloatingNavBar(
                            tab = tab,
                            onTab = { t ->
                                tab = t
                                artistOpen = false
                                albumOpen = false
                            },
                            modifier = Modifier.graphicsLayer {
                                translationY = (FloatingNavHeight + 40.dp).toPx() * hideFrac
                                alpha = 1f - hideFrac
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                PlayerScreen(
                    state = bigPlayerState,
                    isFavorite = favorites.any { it.url == player.current?.url },
                    onClose = { showPlayer = false },
                    onToggle = vm::togglePlay,
                    onNext = vm::playNext,
                    onPrev = vm::playPrevious,
                    onSeek = vm::seekTo,
                    onToggleFavorite = { player.current?.let { vm.toggleFavorite(it) } },
                    onShuffle = vm::toggleShuffle,
                    onRepeat = vm::cycleRepeat,
                    onMore = { player.current?.let { menu = SongMenuTarget(it, showQueueActions = false, fromPlayer = true) } },
                    onInfo = {
                        player.current?.let {
                            vm.openSongInfo(it)
                            songInfoOpen = true
                        }
                    },
                    onLyrics = { showLyrics = true },
                    onQueue = { showQueue = true },
                    timerActive = sleepTimer.active,
                    onTimer = { showTimer = true },
                    canvasUrl = canvasUrl,
                    lyrics = lyrics
                )
            }

            if (showTimer) {
                SleepTimerSheet(state = sleepTimer, onDismiss = { showTimer = false })
            }

            AnimatedVisibility(
                visible = showLyrics,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                LyricsScreen(
                    state = player.copy(positionMs = 0L),
                    position = lyricsPosition,
                    lyrics = lyrics,
                    onClose = { showLyrics = false },
                    onToggle = vm::togglePlay,
                    onNext = vm::playNext,
                    onPrev = vm::playPrevious,
                    onSeek = vm::seekTo,
                    onMore = { player.current?.let { menu = SongMenuTarget(it, showQueueActions = false, fromPlayer = true) } },
                    onRetry = { player.current?.let { vm.loadLyrics(it, force = true) } }
                )
            }

            AnimatedVisibility(
                visible = showQueue,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                QueueScreen(
                    queue = queue,
                    isPlaying = player.isPlaying,
                    onClose = { showQueue = false },
                    onPlayAt = vm::playQueueItem,
                    onRemove = vm::removeFromQueue,
                    onMove = vm::moveInQueue,
                    onClearUpcoming = {
                        vm.clearUpcoming()
                        toast("Se quitó lo que seguía en la cola")
                    },
                    autoRadio = autoRadio,
                    onToggleRadio = {
                        vm.toggleAutoRadio()
                        toast(if (autoRadio) "Radio infinita desactivada" else "Radio infinita activada")
                    },
                    shuffle = player.shuffle,
                    repeat = player.repeat,
                    onShuffle = vm::toggleShuffle,
                    onRepeat = vm::cycleRepeat
                )
            }

            AnimatedVisibility(
                visible = songInfoOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                SongInfoScreen(
                    state = songInfoState,
                    onClose = { songInfoOpen = false },
                    onOpenAlbum = { album ->
                        vm.openAlbum(album)
                        albumOpen = true
                        artistOpen = false
                        songInfoOpen = false
                        showPlayer = false
                        showLyrics = false
                        showQueue = false
                    },
                    onOpenArtist = { name ->
                        vm.openArtistByName(name)
                        artistOpen = true
                        albumOpen = false
                        songInfoOpen = false
                        showPlayer = false
                        showLyrics = false
                        showQueue = false
                    },
                    onCopyLink = { link ->
                        clipboard.setText(AnnotatedString(link))
                        toast("Enlace copiado")
                    }
                )
            }

            if (settingsOpen) {
                SettingsScreen(
                    loggedIn = loggedIn,
                    discord = discordSettings,
                    lastfm = lastfmSettings,
                    spotify = spotifySettings,
                    translation = translationSettings,
                    onAccount = { accountSheet = true },
                    onDiscord = { discordOpen = true },
                    onLastfm = { lastfmOpen = true },
                    onSpotify = { spotifyOpen = true },
                    onTranslation = { translationOpen = true },
                    onBack = { settingsOpen = false }
                )
            }

            if (loginOpen) {
                LoginScreen(
                    onCookie = { cookie ->
                        vm.onLoginSuccess(cookie)
                        loginOpen = false
                        toast("Sesión iniciada")
                    },
                    onClose = { loginOpen = false }
                )
            }

            if (discordOpen) {
                DiscordSettingsScreen(
                    store = discordStore,
                    onLogin = {
                        val id = discordSettings.appId
                        if (id.isBlank()) {
                            toast("Falta el ID de aplicación")
                        } else {
                            try {
                                DiscordAuth.start(appContext, id)
                            } catch (e: Exception) {
                                toast("No se pudo abrir el navegador")
                            }
                        }
                    },
                    onLogout = {
                        discordStore.disconnect()
                        toast("Discord desconectado")
                    },
                    onBack = { discordOpen = false }
                )
            }

            if (lastfmOpen) {
                LastfmSettingsScreen(
                    store = lastfmStore,
                    onBack = { lastfmOpen = false }
                )
            }

            if (spotifyOpen) {
                SpotifySettingsScreen(
                    store = spotifyStore,
                    service = spotifyService,
                    onLogin = { spotifyLoginOpen = true },
                    onBack = { spotifyOpen = false }
                )
            }

            if (translationOpen) {
                TranslationSettingsScreen(
                    store = translationStore,
                    onBack = { translationOpen = false }
                )
            }

            if (spotifyLoginOpen) {
                SpotifyLoginScreen(
                    onSpDc = { spDc ->
                        spotifyLoginOpen = false
                        if (spotifyStore.saveSpDc(spDc)) {
                            spotifyStore.setLastResult("Verificando la sesión…")
                            scope.launch {
                                val result = spotifyService.verifySession()
                                spotifyStore.setLastResult(result)
                                toast(result)
                            }
                        } else toast("No se pudo guardar la sesión")
                    },
                    onClose = { spotifyLoginOpen = false }
                )
            }

            val bottomPad = when {
                showPlayer -> 24.dp
                player.current != null && !barsHidden ->
                    FloatingMargin + FloatingNavHeight + FloatingGap + MiniPlayerHeight + FloatingGap
                else -> FloatingMargin + FloatingNavHeight + FloatingGap
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = bottomPad)
            )
        }

        menu?.let { target ->
            val isFav = favorites.any { it.url == target.song.url }
            val albumLookup by produceState<AlbumLookup>(AlbumLookup.Loading, target.song.url) {
                value = AlbumLookup.Loading
                val found = vm.findAlbum(target.song)
                value = if (found != null) AlbumLookup.Found(found) else AlbumLookup.None
            }
            SongMenuSheet(
                target = target,
                isFavorite = isFav,
                album = albumLookup,
                onToggleFavorite = {
                    vm.toggleFavorite(target.song)
                    toast(if (isFav) "Quitada de favoritos" else "Agregada a favoritos")
                    menu = null
                },
                onAddToPlaylist = {
                    pickFor = target.song
                    menu = null
                },
                onPlayNext = {
                    vm.playNextInQueue(target.song)
                    toast("Se reproducirá a continuación")
                    menu = null
                },
                onAddToQueue = {
                    vm.addToQueue(target.song)
                    toast("Agregada a la cola")
                    menu = null
                },
                onOpenArtist = {
                    vm.openArtistByName(target.song.artist)
                    artistOpen = true
                    albumOpen = false
                    showPlayer = false
                    showLyrics = false
                    showQueue = false
                    menu = null
                },
                onOpenAlbum = { album ->
                    vm.openAlbum(album)
                    albumOpen = true
                    artistOpen = false
                    showPlayer = false
                    showLyrics = false
                    showQueue = false
                    menu = null
                },
                onStartArtistRadio = {
                    vm.startArtistRadio(target.song)
                    toast("Radio de ${target.song.artist}")
                    menu = null
                },
                downloadState = downloadStates[target.song.url] ?: Downloads.NONE,
                onDownload = {
                    Downloads.download(appContext, target.song)
                    toast("Descargando…")
                    menu = null
                },
                onRemoveDownload = {
                    Downloads.remove(appContext, target.song.url)
                    toast("Descarga quitada")
                    menu = null
                },
                onOpenInfo = {
                    vm.openSongInfo(target.song)
                    songInfoOpen = true
                    menu = null
                },
                onDismiss = { menu = null }
            )
        }

        if (accountSheet) {
            AccountSheet(
                loggedIn = loggedIn,
                onLogin = {
                    accountSheet = false
                    loginOpen = true
                },
                onLogout = {
                    vm.logout()
                    accountSheet = false
                    toast("Sesión cerrada")
                },
                onDismiss = { accountSheet = false }
            )
        }

        pickFor?.let { song ->
            PlaylistPickerDialog(
                playlists = playlists,
                onPick = { p ->
                    vm.addToPlaylist(p.id, song)
                    toast("Agregada a ${p.name}")
                    pickFor = null
                },
                onNew = {
                    newPlaylist = NewPlaylistRequest(song)
                    pickFor = null
                },
                onDismiss = { pickFor = null }
            )
        }

        newPlaylist?.let { req ->
            NewPlaylistDialog(
                onCreate = { name ->
                    vm.createPlaylist(name, req.song)
                    toast(if (req.song != null) "Playlist creada con la canción" else "Playlist creada")
                    newPlaylist = null
                },
                onDismiss = { newPlaylist = null }
            )
        }
    }

    BackHandler(
        enabled = !showPlayer && !showLyrics && !showQueue && !artistOpen && !albumOpen &&
            tab == 1 && explore.selected != null
    ) { vm.closeCategory() }
    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && tab == 2 && libraryDetail != null) { libraryDetail = null }
    BackHandler(enabled = !showPlayer && !showLyrics && !showQueue && artistOpen && !albumOpen) { artistOpen = false }
    BackHandler(enabled = !showPlayer && !showLyrics && !showQueue && albumOpen) { albumOpen = false }

    // Carga la letra con el reproductor o la pantalla de letras abiertos, y cada vez que cambia la canción
    // (así la letra de la vista previa del reproductor aparece sin tener que abrir la pantalla de letras)
    LaunchedEffect(player.current?.url, showLyrics, showPlayer) {
        val song = player.current
        if ((showLyrics || showPlayer) && song != null) vm.loadLyrics(song)
    }
    // Canvas de Spotify: se busca al abrir el reproductor y cada vez que cambia la canción
    LaunchedEffect(player.current?.url, showPlayer, spotifySettings.connected, spotifySettings.canvas) {
        val song = player.current
        if (showPlayer && song != null) vm.loadCanvas(song)
    }
    // La letra necesita refrescar la posición más seguido; el resto del tiempo, menos
    LaunchedEffect(showLyrics, showPlayer, lyrics) {
        vm.setLyricsVisible(showLyrics || (showPlayer && lyrics is LyricsState.Synced))
    }
    BackHandler(enabled = showLyrics) { showLyrics = false }
    BackHandler(enabled = spotifyLoginOpen) { spotifyLoginOpen = false }
    BackHandler(enabled = spotifyOpen && !spotifyLoginOpen) { spotifyOpen = false }
    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = songInfoOpen) { songInfoOpen = false }
}
