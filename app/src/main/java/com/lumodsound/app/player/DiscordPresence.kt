package com.lumodsound.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lumodsound.app.data.DiscordApi
import com.lumodsound.app.data.DiscordAuth
import com.lumodsound.app.data.DiscordSettings
import com.lumodsound.app.data.DiscordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Muestra en Discord lo que está sonando ("Escuchando LumodSound").
 * Escucha directamente al reproductor del servicio, así sigue funcionando con la app cerrada.
 */
class DiscordPresence(private val context: Context) {

    private class Snapshot(
        val title: String,
        val artist: String,
        val url: String,
        val thumb: String?,
        val playing: Boolean,
        val startMs: Long,
        val durationMs: Long
    )

    private val store = DiscordStore.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private var player: Player? = null
    @Volatile private var gateway: DiscordGateway? = null
    @Volatile private var gatewayToken: String? = null
    private var job: Job? = null
    private var failures = 0
    private val imageCache = HashMap<String, String>()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = schedule()
        override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = schedule()
        override fun onPlaybackStateChanged(playbackState: Int) = schedule()
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) schedule()
        }
    }

    fun attach(p: Player) {
        player = p
        p.addListener(listener)
        // Cualquier cambio en los ajustes (activar, cuenta, opciones) vuelve a sincronizar
        scope.launch { store.settings.collect { schedule() } }
    }

    fun release() {
        player?.removeListener(listener)
        player = null
        scope.cancel()
        gateway?.close()
        gateway = null
    }

    /** Junta varios eventos seguidos (cambio de canción + buffering + play) en una sola actualización. */
    private fun schedule(delayMs: Long = 700) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            sync()
        }
    }

    private suspend fun sync() {
        val p = player ?: return
        val s = store.settings.value
        val item = p.currentMediaItem
        val playing = p.playWhenReady &&
            (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)

        if (!s.enabled || !s.connected || s.appId.isBlank() || item == null || (!playing && s.hideWhenPaused)) {
            stop()
            return
        }

        val md = item.mediaMetadata
        val duration = if (p.duration > 0) p.duration else md.durationMs ?: 0L
        val snap = Snapshot(
            title = md.title?.toString() ?: "Sin título",
            artist = md.artist?.toString().orEmpty(),
            url = md.extras?.getString("yt_url") ?: item.localConfiguration?.uri?.toString().orEmpty(),
            thumb = md.artworkUri?.toString(),
            playing = playing,
            startMs = System.currentTimeMillis() - p.currentPosition.coerceAtLeast(0),
            durationMs = duration
        )
        withContext(Dispatchers.IO) { push(s, snap) }
    }

    private suspend fun push(s: DiscordSettings, snap: Snapshot) = mutex.withLock {
        val token = DiscordAuth.validAccessToken(context)
        if (token == null) {
            closeLocked()
            return@withLock
        }
        val authorization = "Bearer $token"

        var gw = gateway
        if (gw == null || !gw.ready || gatewayToken != token) {
            closeLocked()
            gw = DiscordGateway(s.appId, scope, ::onGatewayClosed)
            gateway = gw
            gatewayToken = token
            if (!gw.connect(authorization)) {
                closeLocked()
                retryLater()
                return@withLock
            }
        }
        failures = 0

        // Primero se envía sin imagen para que aparezca ya; cuando Discord entrega la imagen, se reenvía
        val cached = snap.thumb?.let { imageCache[it] }
        gw.send(presenceJson(s, snap, cached))
        if (cached == null && snap.thumb != null) {
            val resolved = DiscordApi.externalAsset(authorization, s.appId, snap.thumb)
            if (resolved != null) {
                imageCache[snap.thumb] = resolved
                gw.send(presenceJson(s, snap, resolved))
            }
        }
    }

    private fun presenceJson(s: DiscordSettings, snap: Snapshot, image: String?): String {
        val link = snap.url.replace("://www.youtube.com", "://music.youtube.com")
        val withButton = s.showButton && link.startsWith("http")

        val activity = JSONObject()
            .put("name", "LumodSound")
            .put("type", 2) // escuchando
            .put("details", snap.title)
        if (snap.artist.isNotBlank()) activity.put("state", snap.artist)
        if (s.showProgress && snap.playing && snap.durationMs > 0) {
            activity.put("timestamps", JSONObject().put("start", snap.startMs).put("end", snap.startMs + snap.durationMs))
        }
        if (image != null) {
            activity.put("assets", JSONObject().put("large_image", image).put("large_text", snap.title))
        }
        if (withButton) {
            activity.put("buttons", JSONArray().put("Escuchar en YouTube Music"))
            activity.put("metadata", JSONObject().put("button_urls", JSONArray().put(link)))
        }

        val d = JSONObject()
            .put("since", 0)
            .put("activities", JSONArray().put(activity))
            .put("status", s.status)
            .put("afk", false)
        return JSONObject().put("op", 3).put("d", d).toString()
    }

    /** La conexión se cayó sola: se renueva el token si hace falta y se reintenta con espera creciente. */
    private fun onGatewayClosed(code: Int, reason: String) {
        scope.launch {
            gateway = null
            gatewayToken = null
            if (code == 4004) store.markExpired() // token rechazado: se intentará renovar
            retryLater()
        }
    }

    private fun retryLater() {
        failures++
        if (failures > MAX_FAILURES) return
        schedule(delayMs = 5_000L * failures)
    }

    private suspend fun stop() = withContext(Dispatchers.IO) { mutex.withLock { closeLocked() } }

    private fun closeLocked() {
        gateway?.close() // cerrar la conexión borra la presencia en Discord
        gateway = null
        gatewayToken = null
    }

    private companion object {
        const val MAX_FAILURES = 5
    }
}
