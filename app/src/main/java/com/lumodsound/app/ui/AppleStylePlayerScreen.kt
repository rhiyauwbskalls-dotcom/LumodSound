package com.lumodsound.app.ui

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumodsound.app.PlayerState
import com.lumodsound.app.data.LyricsState
import kotlinx.coroutines.delay
import com.lumodsound.app.R
import com.lumodsound.app.str
import kotlin.math.roundToInt

/** Color de fondo de abajo: el de la portada, apagado y de brillo medio (azul grisáceo en la portada de ejemplo). */
internal fun appleTint(c: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(0.45f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.40f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Versión clara del color de la portada para el nombre del artista. */
internal fun appleLight(c: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0f, 0.65f)
    hsl[2] = 0.78f
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Globo de letra con comillas (hueco), como el icono de letras del reproductor de Apple. */
internal val QuoteBubble: ImageVector by lazy {
    ImageVector.Builder(
        name = "QuoteBubble",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(
            "M4,3H20A2,2 0 0 1 22,5V15A2,2 0 0 1 20,17H12L7,21V17H4A2,2 0 0 1 2,15V5A2,2 0 0 1 4,3Z " +
                "M7,7.5H10V10.5L8.6,12.5H7.4L8.4,10.5H7Z " +
                "M12.5,7.5H15.5V10.5L14.1,12.5H12.9L13.9,10.5H12.5Z"
        ),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black)
    ).build()
}

/**
 * Reproductor grande con la portada a pantalla completa que se funde con un fondo difuminado del color de la portada,
 * título/artista, barra fina, controles grandes sin fondo, volumen y fila inferior (letra, temporizador, cola).
 * Se activa en Ajustes (PlayerSettings.style). El desenfoque real usa RenderEffect (Android 12+); en versiones
 * anteriores la portada pequeña estirada ya se ve borrosa, así que queda parecido.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleStylePlayerScreen(
    state: PlayerState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onMore: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    timerActive: Boolean,
    onTimer: () -> Unit,
    canvasUrl: String? = null,
    lyrics: LyricsState? = null
) {
    val song = state.current ?: return
    val context = LocalContext.current

    val accent = rememberArtAccent(song.thumb)
    val tint = remember(accent) { appleTint(accent) }
    val artistColor = remember(accent) { appleLight(accent) }

    val latestClose by rememberUpdatedState(onClose)
    val latestNext by rememberUpdatedState(onNext)
    val latestPrev by rememberUpdatedState(onPrev)

    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val shownFraction = dragging ?: fraction
    val shownPos = (shownFraction * state.durationMs).toLong()
    val remaining = (state.durationMs - shownPos).coerceAtLeast(0L)

    var canvasReady by remember(canvasUrl) { mutableStateOf(false) }

    // Modo inmersivo (como el reproductor normal): con Canvas, tras unos segundos sin tocar se ocultan los botones
    // y el video pasa a pantalla completa. Un toque vuelve a la vista normal sin pausar la canción.
    var immersive by remember(canvasUrl) { mutableStateOf(false) }
    var touchTick by remember { mutableStateOf(0) }
    var hiddenHeightPx by remember { mutableStateOf(0f) }
    val immersiveF by animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(450),
        label = "immersive"
    )
    LaunchedEffect(canvasReady, state.isPlaying) {
        if (!canvasReady || !state.isPlaying) immersive = false
    }
    LaunchedEffect(canvasReady, immersive, touchTick, state.isPlaying, dragging) {
        if (canvasReady && !immersive && state.isPlaying && dragging == null) {
            delay(4000)
            immersive = true
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Cada toque reinicia la cuenta atrás (solo se observa, no se consume)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) touchTick++
                    }
                }
            }
    ) {
        // La portada / el video crece hasta ocupar toda la pantalla en modo inmersivo
        val artHeight = maxHeight * (0.66f + 0.34f * immersiveF)

        // 1) Fondo: la portada pequeña, muy difuminada
        val backdropRequest = remember(song.thumb) {
            ImageRequest.Builder(context).data(song.thumb).size(64).crossfade(400).build()
        }
        AsyncImage(
            model = backdropRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp, BlurredEdgeTreatment.Unbounded)
        )
        // 2) Tinte del color de la portada que va subiendo hacia abajo
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.30f to Color.Transparent,
                        0.62f to tint.copy(alpha = 0.60f),
                        1f to tint.copy(alpha = 0.94f)
                    )
                )
        )

        // 3) Portada nítida arriba, desvanecida hacia abajo; deslizar a los lados = anterior / siguiente
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val w = size.width.toFloat()
                            when {
                                total < -w * 0.25f -> latestNext()
                                total > w * 0.25f -> latestPrev()
                            }
                        },
                        onDragCancel = { total = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        }
                    )
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            (0.55f + 0.45f * immersiveF) to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            HiResArtwork(song.thumb, Modifier.fillMaxSize().alpha(if (canvasReady) 0f else 1f))
            if (canvasUrl != null) {
                CanvasVideo(
                    url = canvasUrl,
                    playing = state.isPlaying,
                    onReady = { canvasReady = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Velo suave arriba para que se vea la barra de estado
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent)))
        )

        // Velo oscuro abajo para que se lea el título cuando el video ocupa toda la pantalla
        if (immersiveF > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .alpha(immersiveF)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            )
        }

        // Asa de arriba: toque o deslizar hacia abajo para cerrar
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer { alpha = 1f - immersiveF }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 60.dp.toPx()) latestClose() },
                        onDragCancel = { total = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        }
                    )
                }
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
                    .size(width = 38.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.45f))
            )
        }

        // 4) Bloque de abajo: título, progreso, controles, volumen y fila inferior
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp)
                .padding(bottom = 20.dp)
        ) {
            // Bloque de información (letra, título, progreso): en modo inmersivo baja al fondo
            Column(Modifier.fillMaxWidth().graphicsLayer { translationY = hiddenHeightPx * immersiveF }) {
            if (lyrics is LyricsState.Synced && lyrics.lines.isNotEmpty()) {
                LyricsPreview(lines = lyrics.lines, positionMs = state.positionMs, onClick = onLyrics)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    androidx.compose.runtime.key(song.url) {
                        Text(
                            song.title,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                                velocity = 40.dp
                            )
                        )
                    }
                    Text(
                        song.artist,
                        fontSize = 21.sp,
                        color = artistColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PressableIcon(
                    onClick = onToggleFavorite,
                    label = if (isFavorite) str(R.string.remove_from_favorites) else str(R.string.add_to_favorites),
                    modifier = Modifier.size(44.dp).graphicsLayer { alpha = 1f - immersiveF }
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isFavorite) str(R.string.remove_from_favorites) else str(R.string.add_to_favorites),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                PressableIcon(
                    onClick = onMore,
                    label = str(R.string.more_options),
                    modifier = Modifier.size(44.dp).graphicsLayer { alpha = 1f - immersiveF }
                ) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = str(R.string.more_options),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            AppleSlider(
                fraction = shownFraction,
                enabled = state.durationMs > 0 && !state.isLoadingStream,
                onChange = { dragging = it },
                onFinished = {
                    dragging?.let { onSeek((it * state.durationMs).toLong()) }
                    dragging = null
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppleTimeLabel(formatTime(shownPos))
                AppleTimeLabel("-" + formatTime(remaining))
            }

            }

            // Botones, volumen y fila inferior: se ocultan en modo inmersivo
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { hiddenHeightPx = it.height.toFloat() }
                    .graphicsLayer { alpha = 1f - immersiveF }
            ) {
            Spacer(Modifier.height(26.dp))
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
                    enabled = !state.isLoadingStream,
                    label = if (state.isPlaying) str(R.string.pause) else str(R.string.play),
                    modifier = Modifier.size(84.dp)
                ) {
                    if (state.isLoadingStream) {
                        CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp, color = Color.White)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) str(R.string.pause) else str(R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(62.dp)
                        )
                    }
                }
                PressableIcon(onClick = onNext, label = str(R.string.next), modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Filled.FastForward, str(R.string.next), tint = Color.White, modifier = Modifier.size(46.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            AppleVolumeBar()

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: letra
                Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterStart) {
                    PressableIcon(onClick = onLyrics, label = str(R.string.lyrics), modifier = Modifier.size(48.dp)) {
                        Icon(QuoteBubble, str(R.string.lyrics), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
                    }
                }
                // Centro: temporizador de sueño
                PressableIcon(onClick = onTimer, label = str(R.string.timer), modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (timerActive) Icons.Filled.Timer else Icons.Outlined.Timer,
                        contentDescription = str(R.string.timer),
                        tint = if (timerActive) Color.White else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                // Derecha: cola, con las insignias de aleatorio y repetir (como la insignia de la cola de Apple)
                Box(Modifier.width(76.dp).height(52.dp), contentAlignment = Alignment.CenterEnd) {
                    PressableIcon(onClick = onQueue, label = str(R.string.queue), modifier = Modifier.align(Alignment.Center).size(48.dp)) {
                        Icon(AppIcons.Queue, str(R.string.queue), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
                    }
                    ModeBadge(
                        icon = Icons.Filled.Shuffle,
                        active = state.shuffle,
                        label = str(R.string.shuffle),
                        onClick = onShuffle,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    ModeBadge(
                        icon = if (state.repeat == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        active = state.repeat != 0,
                        label = str(R.string.repeat),
                        onClick = onRepeat,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }

            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
        }

        // En modo inmersivo los botones están ocultos: esta capa recibe el toque (vuelve a la vista normal sin pausar)
        // y el deslizamiento horizontal (izquierda = siguiente, derecha = anterior)
        if (immersive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { immersive = false } }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                val w = size.width.toFloat()
                                when {
                                    total < -w * 0.25f -> latestNext()
                                    total > w * 0.25f -> latestPrev()
                                }
                            },
                            onDragCancel = { total = 0f },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                total += amount
                            }
                        )
                    }
            )
        }
    }
}

/** Botón sin fondo ni onda: al pulsarlo se encoge un poco con un rebote, como en iOS. */
@Composable
internal fun PressableIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Círculo pequeño con el icono de aleatorio / repetir: blanco pleno si está activo, translúcido si no. */
@Composable
private fun ModeBadge(
    icon: ImageVector,
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Color(0xFF1C1C1E) else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Barra fina redondeada, sin punto: el relleno va de blanco translúcido a blanco y la barra engorda mientras se arrastra.
 */
@Composable
internal fun AppleSlider(
    fraction: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    fillStartAlpha: Float = 0.55f
) {
    var touching by remember { mutableStateOf(false) }
    val barHeight by animateDpAsState(if (touching) 10.dp else 6.dp, label = "appleSliderHeight")
    val latestChange by rememberUpdatedState(onChange)
    val latestFinished by rememberUpdatedState(onFinished)
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    latestChange((offset.x / size.width).coerceIn(0f, 1f))
                    latestFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        touching = true
                        latestChange((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        touching = false
                        latestFinished()
                    },
                    onDragCancel = {
                        touching = false
                        latestFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val a = if (enabled) 1f else 0.5f
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f * a))
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(barHeight)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = fillStartAlpha * a), Color.White.copy(alpha = 0.95f * a))
                    )
                )
        )
    }
}

/** Volumen de música del teléfono con el estilo de Apple: altavoz bajo a la izquierda, con ondas a la derecha. */
@Composable
internal fun AppleVolumeBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    var changing by remember { mutableStateOf(false) }

    DisposableEffect(audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!changing) level = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.VolumeMute, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            AppleSlider(
                fraction = level,
                enabled = true,
                fillStartAlpha = 0.85f,
                onChange = { f ->
                    changing = true
                    level = f
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0)
                    } catch (e: SecurityException) {
                        // el sistema no deja cambiarlo ahora (por ejemplo, No molestar): se queda como estaba
                    }
                },
                onFinished = { changing = false }
            )
        }
        Icon(Icons.Filled.VolumeUp, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
    }
}
