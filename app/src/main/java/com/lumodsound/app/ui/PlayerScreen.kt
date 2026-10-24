package com.lumodsound.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lumodsound.app.data.LyricLine
import com.lumodsound.app.data.LyricsState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt
import com.lumodsound.app.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lumodsound.app.R
import com.lumodsound.app.str

private val Heart = Color(0xFFFF5A7A)
internal val PlayerBase = Color(0xFF121212)

/**
 * Oscurece y apaga un poco el color de la portada para usarlo de fondo: así el texto blanco siempre
 * se lee (aunque la portada sea amarilla o blanca) y el degradado se ve más elegante.
 */
internal fun tameForBackground(c: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(0.70f)
    hsl[2] = hsl[2].coerceIn(0.18f, 0.34f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Color del degradado de fondo a [f] (0 = arriba, 1 = abajo): llega al fondo oscuro al 70 % de la altura. */
private fun bgColorAt(top: Color, bottom: Color, f: Float): Color =
    lerp(top, bottom, (f / 0.7f).coerceIn(0f, 1f))

/** Color dominante de la portada (animado); gris neutro mientras carga o si no hay. */
@Composable
internal fun rememberArtAccent(thumb: String?): Color {
    val context = LocalContext.current
    var accent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(thumb) {
        val url = thumb ?: return@LaunchedEffect
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(128)
                .allowHardware(false)
                .build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                ?: return@LaunchedEffect
            val bitmap = drawable.toBitmap()
            val rgb = withContext(Dispatchers.Default) {
                val p = Palette.from(bitmap).generate()
                p.getVibrantColor(p.getMutedColor(p.getDominantColor(0)))
            }
            accent = if (rgb != 0) Color(rgb) else null
        } catch (e: Exception) {
            // sin color dominante: se queda el fondo neutro
        }
    }
    val animated by animateColorAsState(
        targetValue = accent ?: Color(0xFF3A3A3A),
        animationSpec = tween(700),
        label = "accent"
    )
    return animated
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
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
    onInfo: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    timerActive: Boolean,
    onTimer: () -> Unit,
    canvasUrl: String? = null,
    lyrics: LyricsState? = null
) {
    val song = state.current ?: return
    val context = LocalContext.current
    remember(context) { PlayerSettings.load(context) }

    // Color dominante de la portada -> fondo con degradado
    val animatedAccent = rememberArtAccent(song.thumb)
    val top = remember(animatedAccent) { tameForBackground(animatedAccent) }
    val bottom = PlayerBase
    // Fondo difuminado (opcional, en Ajustes). Con Canvas se queda el degradado, porque el video se funde con él
    val blurOn = PlayerSettings.blurBackground && canvasUrl == null

    val content = Color.White
    val muted = Color.White.copy(alpha = 0.65f)

    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val shownFraction = dragging ?: fraction

    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artScale"
    )
    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.92f else 1f,
        label = "playScale"
    )

    // Deslizar la portada: izquierda = siguiente, derecha = anterior
    val swipeX = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val latestNext by rememberUpdatedState(onNext)
    val latestPrev by rememberUpdatedState(onPrev)

    // Posiciones (en px, dentro de esta pantalla) de donde acaba la barra de arriba y donde empieza el título:
    // el Canvas solo se dibuja entre las dos; arriba y abajo se ve el fondo con el color de la portada.
    val density = LocalDensity.current
    var boxTop by remember { mutableStateOf(0f) }
    var boxHeight by remember { mutableStateOf(0f) }
    var headerBottom by remember { mutableStateOf(0f) }
    var infoTop by remember { mutableStateOf(0f) }
    var infoBottom by remember { mutableStateOf(0f) }
    // Cuánto hay que bajar título + progreso para que queden abajo del todo (encima de la barra de navegación)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val bottomMarginPx = with(density) { 28.dp.toPx() }
    val infoShift = if (boxHeight > 0f && infoBottom > 0f) {
        (boxHeight - navBottomPx - bottomMarginPx - infoBottom).coerceAtLeast(0f)
    } else 0f

    // Modo inmersivo: con Canvas, tras unos segundos sin tocar se ocultan los botones y el video pasa a pantalla completa.
    // Un toque vuelve a la vista normal (con el fondo del color de la portada) sin pausar la canción.
    var canvasReady by remember(canvasUrl) { mutableStateOf(false) }
    var immersive by remember(canvasUrl) { mutableStateOf(false) }
    var touchTick by remember { mutableStateOf(0) }
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

    Box(
        Modifier
            .fillMaxSize()
            // Cada toque reinicia la cuenta atrás (solo se observa, no se consume)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) touchTick++
                    }
                }
            }
            .onGloballyPositioned {
                boxTop = it.positionInRoot().y
                boxHeight = it.size.height.toFloat()
            }
            .background(if (blurOn) SolidColor(Color.Black) else Brush.verticalGradient(0f to top, 0.7f to bottom, 1f to bottom))
    ) {
        if (blurOn) BlurredBackdrop(song.thumb)

        // Canvas de Spotify: video en la franja del medio (entre la barra de arriba y el título/controles). Las franjas
        // de arriba y de abajo conservan el degradado con el color de la portada, y el video se funde con ellas.
        if (canvasUrl != null) {
            val measured = headerBottom > 0f && infoTop > headerBottom + 50f
            val baseTopPx = if (measured) headerBottom else boxHeight * 0.11f
            val baseBottomPx = if (measured) infoTop else boxHeight * 0.55f
            // En modo inmersivo la franja crece hasta ocupar toda la pantalla
            val bandTopPx = baseTopPx * (1f - immersiveF)
            val bandBottomPx = baseBottomPx + (boxHeight - baseBottomPx) * immersiveF
            val bandTop = with(density) { bandTopPx.toDp() }
            val bandHeight = with(density) { (bandBottomPx - bandTopPx).coerceAtLeast(1f).toDp() }
            CanvasVideo(
                url = canvasUrl,
                playing = state.isPlaying,
                onReady = { canvasReady = it },
                modifier = Modifier.fillMaxWidth().padding(top = bandTop).height(bandHeight)
            )
            if (canvasReady && boxHeight > 0f) {
                val fade = 44.dp
                val fadePx = with(density) { fade.toPx() }
                // Color del fondo justo en cada borde de la franja, para que el video se funda sin corte
                val bgTop = bgColorAt(top, bottom, bandTopPx / boxHeight)
                val bgBottom = bgColorAt(top, bottom, bandBottomPx / boxHeight)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = bandTop)
                        .height(fade)
                        .alpha(1f - immersiveF)
                        .background(Brush.verticalGradient(listOf(bgTop, Color.Transparent)))
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = with(density) { (bandBottomPx - fadePx).coerceAtLeast(0f).toDp() })
                        .height(fade)
                        .alpha(1f - immersiveF)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, bgBottom)))
                )
                // Velo suave abajo para que se lea el título cuando el video ocupa toda la pantalla
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
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerBottom = it.positionInRoot().y - boxTop + it.size.height }
                    .graphicsLayer { alpha = 1f - immersiveF },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = str(R.string.close), tint = content)
                }
                Text(
                    str(R.string.now_playing),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                PlayerChip(onClick = onMore, label = str(R.string.more_options), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = str(R.string.more_options), tint = content)
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(PlayerCorner),
                border = if (canvasReady) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                color = if (canvasReady) Color.Transparent else Color.Black,
                shadowElevation = if (canvasReady) 0.dp else 32.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val w = size.width.toFloat()
                                val x = swipeX.value
                                swipeScope.launch {
                                    when {
                                        x < -w * 0.25f -> {
                                            swipeX.animateTo(-w, tween(180))
                                            latestNext()
                                            swipeX.snapTo(w)
                                            swipeX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                        x > w * 0.25f -> {
                                            swipeX.animateTo(w, tween(180))
                                            latestPrev()
                                            swipeX.snapTo(-w)
                                            swipeX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                        else -> swipeX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            },
                            onDragCancel = {
                                swipeScope.launch { swipeX.animateTo(0f, spring()) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                swipeScope.launch { swipeX.snapTo(swipeX.value + dragAmount) }
                            }
                        )
                    }
                    .offset { IntOffset(swipeX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                        rotationZ = swipeX.value / 60f
                        alpha = 1f - (abs(swipeX.value) / 1500f).coerceIn(0f, 0.5f)
                    }
            ) {
                HiResArtwork(song.thumb, Modifier.fillMaxSize().alpha(if (canvasReady) 0f else 1f))
            }

            // Los dos Spacer con medida no se mueven: marcan dónde empieza y dónde acaba el bloque título + progreso
            Spacer(Modifier.height(20.dp).onGloballyPositioned { infoTop = it.positionInRoot().y - boxTop + it.size.height })
            // En modo inmersivo este bloque baja al fondo de la pantalla
            Column(Modifier.fillMaxWidth().graphicsLayer { translationY = infoShift * immersiveF }) {
            // Letra sincronizada (si la hay) encima del título; baja con el bloque en el modo de pantalla completa
            if (lyrics is LyricsState.Synced && lyrics.lines.isNotEmpty()) {
                LyricsPreview(lines = lyrics.lines, positionMs = state.positionMs, onClick = onLyrics)
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Una sola línea: si el título es largo se desplaza en carrusel (y reinicia al cambiar de canción)
                    androidx.compose.runtime.key(song.url) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = content,
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
                        style = MaterialTheme.typography.bodyLarge,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayerChip(
                    onClick = onToggleFavorite,
                    label = if (isFavorite) str(R.string.remove_from_favorites) else str(R.string.add_to_favorites),
                    modifier = Modifier.size(52.dp).graphicsLayer { alpha = 1f - immersiveF }
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) str(R.string.remove_from_favorites) else str(R.string.add_to_favorites),
                        tint = if (isFavorite) Heart else content,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SeekBar(
                fraction = shownFraction,
                active = dragging != null,
                enabled = state.durationMs > 0 && !state.isLoadingStream,
                onSeek = { dragging = it },
                onSeekFinished = {
                    dragging?.let { onSeek((it * state.durationMs).toLong()) }
                    dragging = null
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatTime((shownFraction * state.durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted
                )
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium, color = muted)
            }

            }
            Spacer(Modifier.height(12.dp).onGloballyPositioned { infoBottom = it.positionInRoot().y - boxTop })
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - immersiveF },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerToggleButton(
                    icon = Icons.Default.Shuffle,
                    label = str(R.string.shuffle),
                    active = state.shuffle,
                    onClick = onShuffle
                )
                IconButton(onClick = onPrev, modifier = Modifier.size(60.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = str(R.string.previous),
                        tint = content,
                        modifier = Modifier.size(40.dp)
                    )
                }
                FilledIconButton(
                    onClick = onToggle,
                    enabled = !state.isLoadingStream,
                    shape = RoundedCornerShape(28.dp),
                    interactionSource = playInteraction,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.85f),
                        disabledContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                ) {
                    if (state.isLoadingStream) {
                        CircularProgressIndicator(
                            Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) str(R.string.pause) else str(R.string.play),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(60.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = str(R.string.next),
                        tint = content,
                        modifier = Modifier.size(40.dp)
                    )
                }
                PlayerToggleButton(
                    icon = if (state.repeat == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    label = str(R.string.repeat),
                    active = state.repeat != 0,
                    onClick = onRepeat
                )
            }

            // Volumen del teléfono
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - immersiveF }) { VolumeBar() }

            // Fila inferior: cola, letras y temporizador (abre una hoja)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - immersiveF },
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerChip(onClick = onQueue, label = str(R.string.queue), modifier = Modifier.size(width = 64.dp, height = 56.dp)) {
                    Icon(AppIcons.Queue, contentDescription = str(R.string.queue), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                PlayerChip(onClick = onLyrics, label = str(R.string.lyrics), modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(AppIcons.Lyrics, contentDescription = str(R.string.lyrics), tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                PlayerChip(onClick = onTimer, label = str(R.string.timer), modifier = Modifier.size(width = 64.dp, height = 56.dp)) {
                    Icon(
                        if (timerActive) Icons.Filled.Timer else Icons.Outlined.Timer,
                        contentDescription = str(R.string.timer),
                        tint = if (timerActive) Color.White else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
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
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                total += dragAmount
                            }
                        )
                    }
            )
        }
    }
}

/** Botón de modo (aleatorio / repetir): sin fondo; encendido = blanco pleno con un puntito debajo, apagado = atenuado. */
@Composable
private fun PlayerToggleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp)
            )
        }
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Barra de progreso fina con un punto: se toca o se arrastra para saltar a otro punto.
 * Mientras se arrastra, la barra y el punto crecen un poco.
 */
@Composable
internal fun SeekBar(
    fraction: Float,
    active: Boolean,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    var widthPx by remember { mutableStateOf(0) }
    val barHeight by animateDpAsState(if (active) 6.dp else 4.dp, label = "seekBarHeight")
    val thumbSize by animateDpAsState(if (active) 16.dp else 12.dp, label = "seekThumbSize")
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    onSeekFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onSeekFinished,
                    onDragCancel = onSeekFinished,
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val barAlpha = if (enabled) 1f else 0.5f
        val f = fraction.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f * barAlpha))
        )
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = barAlpha))
        )
        if (enabled) {
            Box(
                Modifier
                    .offset { IntOffset((widthPx * f - thumbSize.toPx() / 2f).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Abajo va la portada que ya estaba cargada en las listas (aparece al instante, aunque es pequeña);
 * encima se pide una versión grande que se desvanece cuando llega. Así nunca se ve vacío ni borroso.
 */
@Composable
internal fun HiResArtwork(thumb: String?, modifier: Modifier, alignment: Alignment = Alignment.Center) {
    val hi = remember(thumb) { thumb?.hiRes() }
    Box(modifier) {
        AsyncImage(
            model = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier.fillMaxSize()
        )
        if (hi != null && hi != thumb) {
            AsyncImage(
                model = hi,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Las portadas de Google/YouTube Music aceptan el tamaño en la URL (=w544-h544...): pedimos una grande. */
internal fun String.hiRes(px: Int = 1080): String {
    if (!contains("googleusercontent.com") && !contains("ggpht.com")) return this
    return this
        .replace(Regex("=w\\d+-h\\d+"), "=w$px-h$px")
        .replace(Regex("=s\\d+"), "=s$px")
}

/**
 * Vista previa de la letra sincronizada: solo la línea que suena, en una sola línea. Si es más larga que el ancho
 * disponible se desplaza en carrusel (marquesina) en vez de cortarse. Tiene altura fija para que el título de abajo
 * no se mueva cada vez que cambia la línea, y es justo la de una línea para no dejar huecos. Al tocarla se abre la
 * letra completa.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LyricsPreview(lines: List<LyricLine>, positionMs: Long, onClick: () -> Unit) {
    // Mismo adelanto de 250 ms que usa la pantalla de letras
    val index = lines.indexOfLast { it.timeMs <= positionMs + 250 }
    val current = when {
        index < 0 -> "♪"
        lines[index].text.isBlank() -> "♪"
        else -> lines[index].text
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center
    ) {
        // key(current): al cambiar de línea el carrusel empieza de nuevo desde el principio
        androidx.compose.runtime.key(current) {
            Text(
                current,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                // Solo se mueve si la línea no cabe; si cabe, queda quieta
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 300,
                    velocity = 70.dp
                )
            )
        }
    }
}
