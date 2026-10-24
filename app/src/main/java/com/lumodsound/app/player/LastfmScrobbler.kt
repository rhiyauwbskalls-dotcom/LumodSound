package com.lumodsound.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lumodsound.app.data.LastfmApi
import com.lumodsound.app.data.LastfmApi.Outcome
import com.lumodsound.app.data.LastfmStore
import java.util.concurrent.Executors
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Manda a Last.fm lo que se escucha. Escucha directamente al reproductor del servicio, así funciona con la app cerrada.
 *
 * Regla de Last.fm: una canción de más de 30 s cuenta cuando se ha escuchado la mitad o 4 minutos, lo que ocurra
 * antes. Se cuenta el tiempo realmente sonando (no la posición), sin sondeo: solo se programa un aviso para el
 * segundo en que se cumple, y se cancela al pausar o cambiar de canción.
 */
class LastfmScrobbler(private val context: Context) {

    private val store = LastfmStore.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var player: Player? = null

    private var key: String? = null
    private var track: LastfmApi.Track? = null
    private var startedAtSec = 0L
    private var listenedMs = 0L
    private var playingSinceMs = 0L // elapsedRealtime en que empezó a sonar; 0 = en pausa
    private var nowPlayingSent = false
    private var scrobbled = false

    private val check = Runnable { scrobbleNow() }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Repetir la misma canción cuenta como una escucha nueva
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) key = null
            update()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = update()
        override fun onPlaybackStateChanged(playbackState: Int) = update()
    }

    fun attach(p: Player) {
        player = p
        p.addListener(listener)
    }

    fun release() {
        handler.removeCallbacks(check)
        player?.removeListener(listener)
        player = null
        executor.shutdown()
    }

    private fun update() {
        handler.removeCallbacks(check)
        val p = player ?: return
        val s = store.settings.value
        if (!s.connected || !s.hasCredentials || !(s.enabled || s.nowPlaying)) return
        val item = p.currentMediaItem ?: return
        val md = item.mediaMetadata
        val itemKey = md.extras?.getString("yt_url") ?: item.localConfiguration?.uri?.toString() ?: return
        val now = SystemClock.elapsedRealtime()

        if (itemKey != key) {
            key = itemKey
            listenedMs = 0L
            playingSinceMs = 0L
            nowPlayingSent = false
            scrobbled = false
            startedAtSec = System.currentTimeMillis() / 1000 - p.currentPosition.coerceAtLeast(0) / 1000
            val title = md.title?.toString()?.trim().orEmpty()
            // En YouTube Music los artistas "oficiales" vienen como "Nombre - Topic"
            val artist = md.artist?.toString()?.removeSuffix(" - Topic")?.trim().orEmpty()
            track = if (title.isBlank() || artist.isBlank()) {
                null
            } else {
                LastfmApi.Track(
                    artist = artist,
                    title = title,
                    album = md.albumTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                    durationSec = ((md.durationMs ?: 0L) / 1000).toInt().takeIf { it > 0 }
                )
            }
        }
        val current = track ?: return

        if (p.isPlaying) {
            if (playingSinceMs == 0L) playingSinceMs = now
        } else if (playingSinceMs != 0L) {
            listenedMs += now - playingSinceMs
            playingSinceMs = 0L
        }
        if (!p.isPlaying) return

        if (s.nowPlaying && !nowPlayingSent) {
            nowPlayingSent = true
            val creds = s
            executor.execute {
                LastfmApi.updateNowPlaying(creds.apiKey, creds.secret, creds.sessionKey ?: return@execute, current)
            }
        }

        if (s.enabled && !scrobbled) {
            val durationMs = p.duration.takeIf { it > 0 } ?: (current.durationSec?.times(1000L) ?: 0L)
            // Las canciones de 30 s o menos no cuentan para Last.fm
            if (durationMs in 1..30_000L) return
            val neededMs = if (durationMs > 0) minOf(durationMs / 2, MAX_NEEDED_MS) else MAX_NEEDED_MS
            val total = listenedMs + (now - playingSinceMs)
            handler.postDelayed(check, (neededMs - total).coerceAtLeast(0L))
        }
    }

    private fun scrobbleNow() {
        if (scrobbled) return
        val current = track ?: return
        scrobbled = true
        submit(current, startedAtSec, attempt = 1)
    }

    /** Manda la escucha; si falla por algo temporal (sin red, límite), reintenta una vez medio minuto después. */
    private fun submit(current: LastfmApi.Track, startedAt: Long, attempt: Int) {
        executor.execute {
            val s = store.settings.value
            val sk = s.sessionKey
            if (sk == null || !s.hasCredentials) return@execute
            val outcome = LastfmApi.scrobble(s.apiKey, s.secret, sk, current, startedAt)
            val label = "${current.artist} - ${current.title}"
            when (outcome) {
                is Outcome.Ok -> store.setLastResult("scrobble correcto: $label")
                is Outcome.Ignored ->
                    store.setLastResult("Last.fm ignoró «$label» (código ${outcome.code}${outcome.message.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""})")
                is Outcome.Error -> when {
                    outcome.needsReauth -> {
                        store.disconnect()
                        store.setLastResult(str(R.string.the_last_fm_session_expired))
                    }
                    outcome.retryable && attempt < 2 ->
                        handler.postDelayed({ submit(current, startedAt, attempt + 1) }, RETRY_MS)
                    else -> store.setLastResult("falló «$label» (${outcome.code}: ${outcome.message})")
                }
            }
        }
    }

    private companion object {
        const val MAX_NEEDED_MS = 4 * 60 * 1000L
        const val RETRY_MS = 30_000L
    }
}
