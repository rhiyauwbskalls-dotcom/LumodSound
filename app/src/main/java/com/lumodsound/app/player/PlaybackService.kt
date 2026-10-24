package com.lumodsound.app.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lumodsound.app.MainActivity
import com.lumodsound.app.data.AuthStore
import com.lumodsound.app.data.CachedSongsStore
import com.lumodsound.app.data.HistorySync
import com.lumodsound.app.data.YtMusicApi
import com.lumodsound.app.data.SongUi
import com.lumodsound.app.data.YtRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Servicio en primer plano. La cola completa vive aquí (por eso la notificación y la pantalla
 * de bloqueo tienen siguiente/anterior). Cada canción se guarda con su URL de YouTube y el
 * audio real se resuelve justo cuando el reproductor lo necesita.
 *
 * Fluidez (como Metrolist): todo lo que suena pasa por una caché de disco, y mientras suena una
 * canción se precarga el inicio de la siguiente, así el cambio es inmediato.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var discord: DiscordPresence? = null
    private var lastfm: LastfmScrobbler? = null
    private var crossfader: Crossfader? = null
    private var audioSettingsListener: (() -> Unit)? = null
    private val repo = YtRepository()
    private val prefetchExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val prefetched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private lateinit var cacheFactory: CacheDataSource.Factory

    // Espera de la precarga: se revisa cada pocos segundos si ya es buen momento (ver schedulePrefetch)
    private val handler = Handler(Looper.getMainLooper())
    private val prefetchCheck = Runnable { session?.player?.let { schedulePrefetch(it) } }
    private var lastRecordedUrl: String? = null

    // Historial de YouTube Music (opcional): se cuenta el tiempo realmente escuchado SIN sondeo; solo se
    // programa un aviso para el segundo en que se cumplen los 30 s, y se cancela al pausar o cambiar.
    private var listenUrl: String? = null
    private var listenedMs = 0L
    private var playingSinceMs = 0L // elapsedRealtime en que empezó a sonar; 0 = en pausa
    private var reportedUrl: String? = null
    private val reportRunnable = Runnable { reportListen() }
    private val historyExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()

        // Se lee primero de las descargas (solo lectura), luego de la caché de reproducción (que sí
        // guarda lo que se va escuchando) y por último de la red.
        val playerCacheFactory = CacheDataSource.Factory()
            .setCache(MediaCache.get(this))
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheFactory = CacheDataSource.Factory()
            .setCache(Downloads.cache(this))
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(playerCacheFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolver = ResolvingDataSource.Resolver { dataSpec -> resolveDataSpec(dataSpec) }
        val dataSourceFactory = ResolvingDataSource.Factory(cacheFactory, resolver)

        // La comparte el reproductor de la sesión y el auxiliar del crossfade (mismas cachés y mismo resolvedor)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        AudioSettings.load(this)
        // Con el normalizador de volumen en la cadena de audio (se activa o apaga en Ajustes)
        val player = ExoPlayer.Builder(this, NormalizingRenderersFactory(this))
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                // Arranca con 1 s de audio (en vez de 2,5 s) y guarda hasta 60 s por delante
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 60_000, 1_000, 2_500)
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Omitir silencio: se aplica ahora y cada vez que se cambia en Ajustes
        player.skipSilenceEnabled = AudioSettings.skipSilence
        val onAudioSettings: () -> Unit = { player.skipSilenceEnabled = AudioSettings.skipSilence }
        audioSettingsListener = onAudioSettings
        AudioSettings.addListener(onAudioSettings)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    recordSong(player.currentMediaItem)
                    schedulePrefetch(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                recordSong(mediaItem)
                schedulePrefetch(player)
                updateListenTracking(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateListenTracking(player)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                schedulePrefetch(player)
            }

            // Al reanudar tras una pausa también hay que volver a evaluar la precarga
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                schedulePrefetch(player)
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
        discord = DiscordPresence(this).also { it.attach(player) }
        lastfm = LastfmScrobbler(this).also { it.attach(player) }
        SleepTimer.attach(player)
        crossfader = Crossfader(this, player, mediaSourceFactory).also { it.attach() }
    }

    /** Lleva la cuenta del tiempo escuchado de la canción actual y programa el aviso a YouTube. */
    private fun updateListenTracking(player: Player) {
        handler.removeCallbacks(reportRunnable)
        if (!HistorySync.isEnabled(this)) return
        val url = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        val now = SystemClock.elapsedRealtime()
        if (url != listenUrl) {
            listenUrl = url
            listenedMs = 0L
            playingSinceMs = 0L
        }
        if (player.isPlaying) {
            if (playingSinceMs == 0L) playingSinceMs = now
        } else if (playingSinceMs != 0L) {
            listenedMs += now - playingSinceMs
            playingSinceMs = 0L
        }
        if (player.isPlaying && reportedUrl != url) {
            val total = listenedMs + (now - playingSinceMs)
            handler.postDelayed(reportRunnable, (HistorySync.THRESHOLD_MS - total).coerceAtLeast(0L))
        }
    }

    private fun reportListen() {
        val url = listenUrl ?: return
        if (reportedUrl == url) return
        reportedUrl = url
        val extra = if (playingSinceMs != 0L) SystemClock.elapsedRealtime() - playingSinceMs else 0L
        val seconds = (listenedMs + extra) / 1000
        historyExecutor.execute {
            try {
                // El servicio puede arrancar sin la pantalla: se carga la sesión guardada
                if (YtMusicApi.cookie == null) YtMusicApi.cookie = AuthStore(this).cookie()
                HistorySync.report(this, url, seconds)
            } catch (e: Exception) {
                // sin red o sesión caducada: no pasa nada, la canción suena igual
            }
        }
    }

    /** Guarda los datos de la canción que suena para poder listarla en Biblioteca → Caché. */
    private fun recordSong(item: MediaItem?) {
        item ?: return
        val url = item.localConfiguration?.uri?.toString() ?: return
        if (url == lastRecordedUrl) return
        lastRecordedUrl = url
        val md = item.mediaMetadata
        CachedSongsStore.record(
            this,
            SongUi(
                url = url,
                title = md.title?.toString() ?: str(R.string.untitled),
                artist = md.artist?.toString() ?: "",
                durationSec = (md.durationMs ?: 0L) / 1000,
                thumb = md.artworkUri?.toString()
            )
        )
    }

    /** Prepara cada petición de audio (ver [AudioResolver]). */
    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec =
        AudioResolver.resolve(this, repo, dataSpec)

    private fun isFullyCached(key: String, position: Long, length: Long): Boolean =
        AudioResolver.isFullyCached(this, key, position, length)

    /**
     * Precarga el inicio (~1 MB, casi un minuto de audio) de la siguiente canción, una vez por canción.
     * Para no competir con la que suena ni gastar datos de más, espera a que:
     *  - la actual lleve al menos [MIN_LISTEN_MS] sonando (si la saltas enseguida no se descarga nada), y
     *  - la actual tenga [MIN_CUSHION_MS] de colchón de audio (o ya esté completa en el buffer).
     * Si la red va lenta y el colchón no llega, se precarga igual cuando faltan [FORCE_BEFORE_END_MS]
     * para que termine, así el cambio sigue siendo fluido.
     */
    private fun schedulePrefetch(player: Player) {
        handler.removeCallbacks(prefetchCheck)
        if (player.playbackState != Player.STATE_READY || !player.playWhenReady) return
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return
        val videoUrl = player.getMediaItemAt(next).localConfiguration?.uri?.toString() ?: return
        if (videoUrl in prefetched) return

        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val position = player.currentPosition
        val listenedEnough = position >= MIN_LISTEN_MS
        val cushion = player.totalBufferedDuration >= MIN_CUSHION_MS ||
            (duration != null && player.bufferedPosition >= duration - 1_000)
        val nearEnd = duration != null && duration - position <= FORCE_BEFORE_END_MS

        if (!(listenedEnough && (cushion || nearEnd))) {
            handler.postDelayed(prefetchCheck, RECHECK_MS) // se vuelve a mirar en unos segundos
            return
        }

        if (!prefetched.add(videoUrl)) return
        prefetchExecutor.execute {
            try {
                prefetch(videoUrl)
            } catch (e: Exception) {
                prefetched.remove(videoUrl) // se reintenta en el próximo evento
            }
        }
    }

    private fun prefetch(videoUrl: String) {
        // Ya está entera en disco: no hace falta resolver ni descargar nada
        if (isFullyCached(videoUrl, 0, C.LENGTH_UNSET.toLong())) return
        val audio = repo.resolveAudioBlocking(videoUrl, AudioSettings.effectiveQuality(this)) // también deja lista la info para el cambio
        MediaCache.checkFormat(this, audio.key, audio.itag)
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(audio.url))
            .setKey(audio.key)
            .setPosition(0)
            .setLength(PREFETCH_BYTES)
            .build()
        CacheWriter(cacheFactory.createDataSource(), spec, null, null).cache()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        audioSettingsListener?.let { AudioSettings.removeListener(it) }
        audioSettingsListener = null
        crossfader?.release()
        crossfader = null
        SleepTimer.detach()
        handler.removeCallbacksAndMessages(null)
        discord?.release()
        discord = null
        lastfm?.release()
        lastfm = null
        prefetchExecutor.shutdownNow()
        session?.run {
            player.release()
            release()
        }
        session = null
        // La caché de reproducción NO se libera aquí: el gestor de descargas la sigue usando
        super.onDestroy()
    }

    private companion object {
        const val PREFETCH_BYTES = 1_000_000L
        const val MIN_LISTEN_MS = 10_000L        // tiempo mínimo sonando antes de precargar la siguiente
        const val MIN_CUSHION_MS = 15_000L       // colchón de audio de la actual antes de precargar
        const val FORCE_BEFORE_END_MS = 30_000L  // si la red es lenta, precarga igual al faltar esto
        const val RECHECK_MS = 2_000L            // cada cuánto se revisa mientras se espera
    }
}
