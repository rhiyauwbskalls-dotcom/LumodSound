package com.lumodsound.app.ui

import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
<<<<<<< HEAD
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lumodsound.app.player.CanvasCache
import kotlin.math.max

/**
 * Video del Canvas de Spotify: sin sonido, en bucle y recortado para llenar todo el espacio (como un
 * "centerCrop"). Va en su propio reproductor, aparte del de la música; se pausa cuando la música se pausa.
 * [onReady] avisa con true cuando se pinta el primer cuadro, o con false si el video falla.
 */
@OptIn(UnstableApi::class)
@Composable
fun CanvasVideo(
    url: String,
    playing: Boolean,
    onReady: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestReady by rememberUpdatedState(onReady)
    var videoSize by remember(url) { mutableStateOf<VideoSize?>(null) }

    val player = remember(url) {
        // Los bytes del video se guardan en disco al verlo (ver CanvasCache): la próxima vez no gasta datos
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CanvasCache.dataSourceFactory(context)))
            .build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = latestReady(true)
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
            override fun onPlayerError(error: PlaybackException) = latestReady(false)
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // El video solo corre con la app a la vista: en segundo plano (o con la pantalla apagada) se pausa
    // aunque la música siga sonando, así no se decodifica video para nadie.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(playing, visible, player) { player.playWhenReady = playing && visible }
=======
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lumodsound.app.player.CanvasCache
import kotlin.math.max

/**
 * Video del Canvas de Spotify: sin sonido, en bucle y recortado para llenar todo el espacio (como un
 * "centerCrop"). Va en su propio reproductor, aparte del de la música; se pausa cuando la música se pausa.
 * [onReady] avisa con true cuando se pinta el primer cuadro, o con false si el video falla.
 */
@OptIn(UnstableApi::class)
@Composable
fun CanvasVideo(
    url: String,
    playing: Boolean,
    onReady: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestReady by rememberUpdatedState(onReady)
    var videoSize by remember(url) { mutableStateOf<VideoSize?>(null) }

    val player = remember(url) {
        // Los bytes del video se guardan en disco al verlo (ver CanvasCache): la próxima vez no gasta datos
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CanvasCache.dataSourceFactory(context)))
            .build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = latestReady(true)
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
            override fun onPlayerError(error: PlaybackException) = latestReady(false)
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(playing, player) { player.playWhenReady = playing }
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound

    BoxWithConstraints(modifier.clipToBounds()) {
        val vs = videoSize
        val rotated = vs != null && (vs.unappliedRotationDegrees == 90 || vs.unappliedRotationDegrees == 270)
        val vw = if (vs == null) 0 else if (rotated) vs.height else vs.width
        val vh = if (vs == null) 0 else if (rotated) vs.width else vs.height
        val sizeModifier = if (vw > 0 && vh > 0) {
            val scale = max(maxWidth.value / vw, maxHeight.value / vh)
            Modifier.requiredSize((vw * scale).dp, (vh * scale).dp)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            modifier = sizeModifier.align(Alignment.Center),
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            onRelease = { try { player.clearVideoTextureView(it) } catch (e: Exception) { /* el reproductor ya se liberó */ } }
        )
    }
}
