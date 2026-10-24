package com.lumodsound.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumodsound.app.PlayerState
import com.lumodsound.app.data.LyricLine
import com.lumodsound.app.data.LyricsState
import com.lumodsound.app.data.LyricsTranslation
import com.lumodsound.app.data.TranslateResult
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.lumodsound.app.data.LyricWord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import com.lumodsound.app.R
import com.lumodsound.app.str

private val Muted = Color.White.copy(alpha = 0.65f)

/** Idiomas de destino que se ofrecen al mantener pulsado el botón de traducir. */
private val TRANSLATE_TARGETS = listOf(
    "es" to "Español", "en" to "English", "pt" to "Português", "fr" to "Français",
    "it" to "Italiano", "de" to "Deutsch", "ja" to "日本語", "ko" to "한국어",
    "zh" to "中文", "ru" to "Русский"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsScreen(
    state: PlayerState,
    position: State<Long>,
    lyrics: LyricsState,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onQueue: () -> Unit = {},
    onTimer: () -> Unit = {},
    timerActive: Boolean = false,
    spotifyConnected: Boolean = false
) {
    val song = state.current ?: return

    // ---- Traducción (ML Kit) ----
    val context = LocalContext.current
    remember(context) { PlayerSettings.load(context) }
    val apple = PlayerSettings.style == PlayerStyle.APPLE
    val controlsHeight = if (apple) AppleLyricsControlsHeight else LyricsControlsHeight
    val scope = rememberCoroutineScope()
    var translations by remember(lyrics) { mutableStateOf<List<String>?>(null) }
    var showTranslation by remember(lyrics) { mutableStateOf(false) }
    var translating by remember(lyrics) { mutableStateOf(false) }
    val canTranslate = lyrics is LyricsState.Synced || lyrics is LyricsState.Plain
    val shownTranslations = if (showTranslation) translations else null
    val haptics = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE) }
    var targetLang by remember {
        mutableStateOf(prefs.getString("target_lang", null) ?: Locale.getDefault().language)
    }
    var showLangPicker by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var translateJob by remember { mutableStateOf<Job?>(null) }

    val startTranslation: (String) -> Unit = { lang ->
        val texts = when (lyrics) {
            is LyricsState.Synced -> lyrics.lines.map { it.text }
            is LyricsState.Plain -> lyrics.lines
            else -> emptyList()
        }
        translateJob?.cancel()
        translating = true
        translateJob = scope.launch {
            val msg = when (val r = LyricsTranslation.translate(context, texts, lang)) {
                is TranslateResult.Success -> {
                    translations = r.lines
                    showTranslation = true
                    null
                }
                TranslateResult.SameLanguage -> str(R.string.the_lyrics_are_already_in)
                TranslateResult.UnknownLanguage -> str(R.string.couldn_t_detect_the_language)
                TranslateResult.Failure ->
                    str(R.string.couldn_t_translate_the_first)
                is TranslateResult.Error -> r.message
            }
            translating = false
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    if (showLogs) LyricsLogsSheet(showSpotify = spotifyConnected, onDismiss = { showLogs = false })

    if (showLangPicker) {
        AlertDialog(
            onDismissRequest = { showLangPicker = false },
            title = { Text(str(R.string.translate_to)) },
            text = {
                Column {
                    TRANSLATE_TARGETS.forEach { (code, name) ->
                        Text(
                            text = if (code == targetLang) "✓  $name" else "     $name",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (code == targetLang) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLangPicker = false
                                    targetLang = code
                                    prefs.edit().putString("target_lang", code).apply()
                                    translations = null
                                    showTranslation = false
                                    startTranslation(code)
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLangPicker = false }) { Text(str(R.string.cancel)) } }
        )
    }

    val onTranslateClick: () -> Unit = {
        val cached = translations
        when {
            translating -> Unit
            cached != null -> showTranslation = !showTranslation
            else -> startTranslation(targetLang)
        }
    }
    val onTranslateLong: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        showLangPicker = true
    }

    // Fondo con el color de la portada (igual que el reproductor grande y la cola)
    val accent = rememberArtAccent(song.thumb)
    val bgTop = remember(accent) { tameForBackground(accent) }

    val artistColor = remember(accent) { appleLight(accent) }

    // Los botones se ocultan solos tras unos segundos sin tocar (con la canción sonando); un toque los devuelve
    var controlsHidden by remember { mutableStateOf(false) }
    val lastTouch = remember { longArrayOf(System.currentTimeMillis()) }
    val hideF by animateFloatAsState(if (controlsHidden) 1f else 0f, tween(400), label = "lyricsHide")
    LaunchedEffect(state.isPlaying, controlsHidden) {
        if (!state.isPlaying) {
            controlsHidden = false
        } else if (!controlsHidden) {
            lastTouch[0] = System.currentTimeMillis()
            while (true) {
                delay(500)
                if (System.currentTimeMillis() - lastTouch[0] >= 4000L) {
                    controlsHidden = true
                    break
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Solo observa los toques (no los consume): cada uno reinicia la cuenta y muestra los botones
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.any { it.pressed }) {
                            lastTouch[0] = System.currentTimeMillis()
                            if (ev.changes.any { it.pressed && !it.previousPressed }) controlsHidden = false
                        }
                    }
                }
            }
            .background(
                if (apple) Brush.verticalGradient(0f to Color.Black, 1f to Color.Black)
                else Brush.verticalGradient(0f to bgTop, 0.75f to PlayerBase, 1f to PlayerBase)
            )
    ) {
        if (apple) AppleBackdrop(song.thumb, accent, hide = { hideF })
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- Cabecera: cerrar, portada, título/artista, traducir y más ----
            Box(Modifier.fillMaxWidth()) {
            if (apple) AppleLyricsHeader(
                title = song.title,
                artist = song.artist,
                thumb = song.thumb,
                artistColor = artistColor,
                onClose = onClose,
                onMore = onMore,
                translateFab = { TranslateFab(canTranslate, showTranslation, translating, onTranslateClick, onTranslateLong) },
                actionsAlpha = 1f - hideF,
                actionsHidden = controlsHidden
            ) else Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.graphicsLayer { alpha = 1f - hideF }.blockTouches(controlsHidden)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = str(R.string.close),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                AsyncImage(
                    model = song.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(iterations = 1)
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ---- Traducir letra (toque: mostrar/ocultar · mantener: elegir idioma) ----
                Row(
                    Modifier.graphicsLayer { alpha = 1f - hideF }.blockTouches(controlsHidden),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TranslateFab(canTranslate, showTranslation, translating, onTranslateClick, onTranslateLong)
                    IconButton(onClick = onMore) {
                        Icon(Icons.Default.MoreVert, contentDescription = str(R.string.more_options), tint = Color.White)
                    }
                }
            }
            }

            // ---- Letra + controles ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (lyrics) {
                    is LyricsState.Synced -> SyncedLyrics(lyrics.lines, position, onSeek, shownTranslations, apple, controlsHeight, artistColor, hideF)
                    is LyricsState.Plain -> PlainLyrics(lyrics.lines, shownTranslations, controlsHeight, hideF, apple)
                    LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                    LyricsState.Instrumental -> CenterMessage(str(R.string.instrumental), null, controlsHeight)
                    LyricsState.NotFound -> CenterMessage(str(R.string.we_couldn_t_find_the), onRetry, controlsHeight)
                    LyricsState.Error -> CenterMessage(str(R.string.couldn_t_load_the_lyrics), onRetry, controlsHeight)
                }

                // Logs: qué respondió cada proveedor en la última búsqueda (hoja aparte de la letra)
                IconButton(
                    onClick = { showLogs = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                        .size(40.dp)
                        .graphicsLayer { alpha = 1f - hideF }
                        .blockTouches(controlsHidden)
                ) {
                    Icon(Icons.Default.Info, contentDescription = str(R.string.lyrics_logs), tint = Muted, modifier = Modifier.size(20.dp))
                }

                // Velo suave abajo: las líneas se funden con el fondo antes de llegar a los controles
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(controlsHeight + 24.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, if (apple) Color.Transparent else PlayerBase)))
                )

                if (apple) AppleLyricsControls(
                    position = position,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    isLoading = state.isLoadingStream,
                    timerActive = timerActive,
                    onSeek = onSeek,
                    onPrev = onPrev,
                    onToggle = onToggle,
                    onNext = onNext,
                    onCloseLyrics = onClose,
                    onQueue = onQueue,
                    onTimer = onTimer,
                    buttonsAlpha = 1f - hideF,
                    buttonsHidden = controlsHidden,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) else Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Box(Modifier.graphicsLayer { alpha = 1f - hideF }.blockTouches(controlsHidden)) {
                        LyricsProgress(
                            position = position,
                            durationMs = state.durationMs,
                            enabled = !state.isLoadingStream,
                            onSeek = onSeek
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - hideF }.blockTouches(controlsHidden),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrev, modifier = Modifier.size(64.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = str(R.string.previous),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = onToggle,
                            enabled = !state.isLoadingStream,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.White.copy(alpha = 0.85f),
                                disabledContentColor = Color.Black
                            ),
                            modifier = Modifier.size(68.dp)
                        ) {
                            if (state.isLoadingStream) {
                                CircularProgressIndicator(
                                    Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.Black
                                )
                            } else {
                                Icon(
                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) str(R.string.pause) else str(R.string.play),
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = str(R.string.next),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Alto reservado abajo para la barra de progreso y los controles (la letra no pasa por debajo). */
private val LyricsControlsHeight = 176.dp

/**
 * Barra de progreso con los tiempos. Es un composable aparte a propósito: es el único que lee la
 * posición (4 veces por segundo), así el resto de la pantalla no se recompone.
 */
@Composable
private fun LyricsProgress(
    position: State<Long>,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (position.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    SeekBar(
        fraction = shown,
        active = dragging != null,
        enabled = enabled && durationMs > 0,
        onSeek = { dragging = it },
        onSeekFinished = {
            dragging?.let { onSeek((it * durationMs).toLong()) }
            dragging = null
        }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            formatTime((shown * durationMs).toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
        Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

/**
 * Letra sincronizada (estilo Spotify / Apple Music): todas las líneas del mismo tamaño y en negrita,
 * la que suena en blanco pleno y las demás más tenues cuanto más lejos están. Tocar una línea salta a ella.
 * El cambio de tamaño y de brillo se hace en la capa gráfica (sin volver a medir el texto), que gasta mucha menos batería.
 */
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    position: State<Long>,
    onSeek: (Long) -> Unit,
    translations: List<String>?,
    apple: Boolean = false,
    controlsHeight: Dp = LyricsControlsHeight,
    tintColor: Color = Color.White,
    hideF: Float = 0f
) {
    // La posición cambia 4 veces por segundo, pero la línea activa solo cada pocos segundos:
    // con derivedStateOf solo se recompone la letra cuando cambia de línea.
    val active by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= position.value + 250 } }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val listState = rememberLazyListState()
        var userTouchedAt by remember { mutableLongStateOf(0L) }

        // Si el usuario desliza la letra, no se la quitamos de las manos durante unos segundos.
        LaunchedEffect(listState) {
            listState.interactionSource.interactions.collect {
                if (it is DragInteraction) userTouchedAt = System.currentTimeMillis()
            }
        }
        LaunchedEffect(active, lines) {
            if (System.currentTimeMillis() - userTouchedAt < 3500) return@LaunchedEffect
            listState.animateScrollToItem(maxOf(if (apple) active - 1 else active, 0))
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = if (apple) h * 0.04f else h * 0.18f, bottom = h * 0.75f),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = lyricsBottomReserve(controlsHeight, hideF, apple))
                .fadeEdges(bottomFadeStart = 0.80f + 0.14f * hideF)
        ) {
            itemsIndexed(lines) { index, line ->
                val distance = kotlin.math.abs(index - active)
                val lineAlpha by animateFloatAsState(
                    when (distance) {
                        0 -> 1f
                        1 -> 0.55f
                        2 -> 0.42f
                        else -> 0.32f
                    },
                    tween(350),
                    label = "lineAlpha"
                )
                // Estilo Apple: las líneas que no suenan se ven desenfocadas, más cuanto más lejos están (Android 12+)
                val lineBlur by animateDpAsState(
                    if (!apple) 0.dp else when (distance) {
                        0 -> 0.dp
                        1 -> 2.dp
                        2 -> 5.dp
                        else -> 8.dp
                    },
                    tween(350),
                    label = "lineBlur"
                )
                val lineScale by animateFloatAsState(
                    if (distance == 0) 1f else 0.9f,
                    tween(350),
                    label = "lineScale"
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = lineAlpha
                            scaleX = lineScale
                            scaleY = lineScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .blur(lineBlur, BlurredEdgeTreatment.Unbounded)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSeek(line.timeMs) }
                        .padding(vertical = 12.dp)
                ) {
                    if (apple && distance == 0 && line.words.isNotEmpty()) {
                        WordSyncedText(line.words, position)
                    } else {
                        Text(
                            text = line.text.ifBlank { "♪" },
                            fontSize = if (apple) 34.sp else 30.sp,
                            lineHeight = if (apple) 41.sp else 37.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    val tr = translations?.getOrNull(index).orEmpty()
                    if (tr.isNotBlank() && tr != line.text) {
                        Text(
                            text = tr,
                            fontSize = if (apple) 22.sp else 18.sp,
                            lineHeight = if (apple) 28.sp else 23.sp,
                            fontWeight = if (apple) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (apple) tintColor else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Letra sin tiempos (no hay versión sincronizada para esta canción). */
@Composable
private fun PlainLyrics(lines: List<String>, translations: List<String>?, controlsHeight: Dp = LyricsControlsHeight, hideF: Float = 0f, apple: Boolean = false) {
    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = lyricsBottomReserve(controlsHeight, hideF, apple))
            .fadeEdges(bottomFadeStart = 0.80f + 0.14f * hideF)
    ) {
        item {
            Text(
                str(R.string.unsynced_lyrics),
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        itemsIndexed(lines) { index, line ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    line,
                    fontSize = 26.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
                val tr = translations?.getOrNull(index).orEmpty()
                if (tr.isNotBlank() && tr != line) {
                    Text(
                        tr,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, onRetry: (() -> Unit)?, controlsHeight: Dp = LyricsControlsHeight) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = controlsHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = Muted, fontSize = 20.sp, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(str(R.string.retry)) }
        }
    }
}

/**
 * Espacio que se deja abajo para los controles. En pantalla completa (controles ocultos, [hideF] hacia 1) en el estilo
 * Apple se reduce para que se vean más líneas de la letra por debajo de la que suena.
 */
private fun lyricsBottomReserve(controlsHeight: Dp, hideF: Float, apple: Boolean): Dp =
    if (!apple) controlsHeight else controlsHeight + (56.dp - controlsHeight) * hideF

/** Desvanece arriba y abajo para que las líneas "entren y salgan" suavemente. */
private fun Modifier.fadeEdges(bottomFadeStart: Float = 0.80f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.10f to Color.Black,
                    bottomFadeStart to Color.Black,
                    1f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }

/** Botón redondo de traducir (toque: mostrar/ocultar · mantener: elegir idioma). Se usa en los dos estilos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranslateFab(
    visible: Boolean,
    showTranslation: Boolean,
    translating: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    if (!visible) return
    val fabBg = if (showTranslation) Color.White else Color.White.copy(alpha = 0.16f)
    val fabFg = if (showTranslation) Color.Black else Color.White
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fabBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (translating) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = fabFg)
        } else {
            Icon(
                Icons.Default.Translate,
                contentDescription = str(R.string.translate_lyrics),
                tint = fabFg,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Línea activa con sincronía por palabra (BetterLyrics): cada palabra se ilumina de tenue a blanco mientras suena.
 * Es un composable aparte para que solo esta línea se redibuje con cada cambio de posición.
 */
@Composable
private fun WordSyncedText(words: List<LyricWord>, position: State<Long>) {
    val pos = position.value + 250L // mismo adelanto que usa la línea activa
    val text = buildAnnotatedString {
        for (w in words) {
            val dur = (w.endMs - w.beginMs).coerceAtLeast(1L)
            val p = ((pos - w.beginMs).toFloat() / dur).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.40f + 0.60f * p))) { append(w.text) }
        }
    }
    Text(
        text = text,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold
    )
}
