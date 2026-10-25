package com.lumodsound.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Alto reservado abajo en la pantalla de letra (progreso, controles, volumen y fila inferior). */
internal val AppleLyricsControlsHeight = 290.dp

/**
 * Fondo del estilo Apple: portada pequeña muy difuminada + tinte del color de la portada subiendo desde abajo.
 * [hide] (0..1, se lee al dibujar) es cuánto está en pantalla completa: el tinte se aclara, sobre todo abajo,
 * para que se vean mejor las líneas de la letra que quedan más abajo.
 */
@Composable
internal fun AppleBackdrop(thumb: String?, accent: Color, modifier: Modifier = Modifier, hide: () -> Float = { 0f }) {
    val context = LocalContext.current
    val tint = remember(accent) { appleTint(accent) }
    val request = remember(thumb) {
        ImageRequest.Builder(context).data(thumb).size(64).crossfade(400).build()
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp, BlurredEdgeTreatment.Unbounded)
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val f = hide().coerceIn(0f, 1f)
                    fun mix(a: Float, b: Float) = a + (b - a) * f
                    drawRect(
                        Brush.verticalGradient(
                            0f to tint.copy(alpha = mix(0.55f, 0.50f)),
                            0.5f to tint.copy(alpha = mix(0.70f, 0.50f)),
                            1f to tint.copy(alpha = mix(0.94f, 0.42f))
                        )
                    )
                }
        )
    }
}

/** Cabecera: asa para cerrar, portada pequeña con sombra, título, artista, traducir y "···". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppleLyricsHeader(
    title: String,
    artist: String,
    thumb: String?,
    artistColor: Color,
    onClose: () -> Unit,
    onMore: () -> Unit,
    translateFab: @Composable () -> Unit,
    actionsAlpha: Float = 1f,
    actionsHidden: Boolean = false
) {
    val latestClose by rememberUpdatedState(onClose)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInputCloseOnSwipeDown { latestClose() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = str(R.string.close),
                    role = Role.Button,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .graphicsLayer { alpha = actionsAlpha }
                    .size(width = 38.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.45f))
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .shadow(8.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = 1)
                )
                Text(
                    artist,
                    fontSize = 18.sp,
                    color = artistColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                Modifier.graphicsLayer { alpha = actionsAlpha }.blockTouches(actionsHidden),
                verticalAlignment = Alignment.CenterVertically
            ) {
                translateFab()
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClickLabel = str(R.string.more_options), role = Role.Button, onClick = onMore),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreHoriz, str(R.string.more_options), tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

private fun Modifier.pointerInputCloseOnSwipeDown(onClose: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total > 60.dp.toPx()) onClose() },
                onDragCancel = { total = 0f },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    total += amount
                }
            )
        }
    )

/** Progreso, controles grandes, volumen y fila inferior (letra activa, temporizador, cola). */
@Composable
internal fun AppleLyricsControls(
    position: State<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    timerActive: Boolean,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onCloseLyrics: () -> Unit,
    onQueue: () -> Unit,
    onTimer: () -> Unit,
    buttonsAlpha: Float = 1f,
    buttonsHidden: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(bottom = 8.dp)
    ) {
        Box(Modifier.graphicsLayer { alpha = buttonsAlpha }.blockTouches(buttonsHidden)) {
            AppleLyricsProgress(position, durationMs, enabled = !isLoading && durationMs > 0, onSeek = onSeek)
        }
        Column(Modifier.graphicsLayer { alpha = buttonsAlpha }.blockTouches(buttonsHidden)) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PressableIcon(onClick = onPrev, label = str(R.string.previous), modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.FastRewind, str(R.string.previous), tint = Color.White, modifier = Modifier.size(46.dp))
            }
            PressableIcon(
                onClick = onToggle,
                enabled = !isLoading,
                label = if (isPlaying) str(R.string.pause) else str(R.string.play),
                modifier = Modifier.size(84.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp, color = Color.White)
                } else {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) str(R.string.pause) else str(R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(62.dp)
                    )
                }
            }
            PressableIcon(onClick = onNext, label = str(R.string.next), modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.FastForward, str(R.string.next), tint = Color.White, modifier = Modifier.size(46.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        AppleVolumeBar()
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Letra activa: cuadrado relleno; al tocarlo se cierra la letra
            PressableIcon(onClick = onCloseLyrics, label = str(R.string.lyrics), modifier = Modifier.size(48.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(QuoteBubble, str(R.string.lyrics), tint = Color(0xFF3A3A3A), modifier = Modifier.size(26.dp))
                }
            }
            PressableIcon(onClick = onTimer, label = str(R.string.timer), modifier = Modifier.size(48.dp)) {
                Icon(
                    if (timerActive) Icons.Filled.Timer else Icons.Outlined.Timer,
                    contentDescription = str(R.string.timer),
                    tint = if (timerActive) Color.White else Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(28.dp)
                )
            }
            PressableIcon(onClick = onQueue, label = str(R.string.queue), modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.Queue, str(R.string.queue), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
            }
        }
        }
    }
}

/** Es un composable aparte a propósito: es el único que lee la posición (4 veces por segundo). */
@Composable
private fun AppleLyricsProgress(
    position: State<Long>,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (position.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    val shownPos = (shown * durationMs).toLong()
    // Column: el slider y los tiempos van uno debajo del otro (antes se apilaban encima porque el padre es un Box)
    Column(Modifier.fillMaxWidth()) {
        AppleSlider(
            fraction = shown,
            enabled = enabled,
            onChange = { dragging = it },
            onFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppleTimeLabel(formatTime(shownPos))
            AppleTimeLabel("-" + formatTime((durationMs - shownPos).coerceAtLeast(0L)))
        }
    }
}

/** Tiempo bajo la barra; con dígitos de ancho fijo para que no "bailen" al avanzar. */
@Composable
internal fun AppleTimeLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.6f),
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}

/** Con los botones ocultos, se tragan los toques para que no se pulse nada invisible por error. */
internal fun Modifier.blockTouches(enabled: Boolean): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
            }
        }
    }
