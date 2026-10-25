package com.lumodsound.app.ui

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

/** Radio de las esquinas "gruesas" de la portada y de los botones del reproductor. */
internal val PlayerCorner = 32.dp
internal val PlayerChipCorner = 20.dp

/** Botón del reproductor: fondo blanco semitransparente y esquinas gruesas. */
@Composable
internal fun PlayerChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(PlayerChipCorner))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Barra del volumen de música del teléfono; sigue a los botones físicos de volumen. */
@Composable
internal fun VolumeBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    DisposableEffect(audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!dragging) level = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.VolumeDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(24.dp)
        )
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            SeekBar(
                fraction = level,
                active = dragging,
                enabled = true,
                onSeek = { f ->
                    dragging = true
                    level = f
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0)
                    } catch (e: SecurityException) {
                        // el sistema no deja cambiarlo ahora (por ejemplo, No molestar): se queda como estaba
                    }
                },
                onSeekFinished = { dragging = false }
            )
        }
        Icon(
            Icons.Default.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Fondo difuminado: la portada pedida en pequeño (se ve borrosa al estirarla), con desenfoque extra en Android 12+
 * y oscurecida para que el texto blanco siempre se lea.
 */
@Composable
internal fun BlurredBackdrop(thumb: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(thumb) {
        ImageRequest.Builder(context).data(thumb).size(64).crossfade(400).build()
    }
    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(36.dp, BlurredEdgeTreatment.Unbounded)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.9f))
                    )
                )
        )
    }
}
