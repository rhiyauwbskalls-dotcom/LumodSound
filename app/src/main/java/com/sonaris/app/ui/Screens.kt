package com.sonaris.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sonaris.app.MainViewModel
import com.sonaris.app.PlayerState
import com.sonaris.app.SearchState
import com.sonaris.app.data.AlbumUi
import com.sonaris.app.data.ArtistUi
import com.sonaris.app.data.SongUi

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, totalSec % 60)
    else "%d:%02d".format(m, totalSec % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    currentUrl: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onArtistClick: (ArtistUi) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    explore: com.sonaris.app.ExploreState,
    onLoadExplore: (Boolean) -> Unit,
    onNeedThumb: (com.sonaris.app.data.YtMusicApi.ExploreCategory) -> Unit,
    onCategoryClick: (com.sonaris.app.data.YtMusicApi.ExploreCategory) -> Unit,
    onCategoryBack: () -> Unit,
    onShelfSongClick: (List<SongUi>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    val listState = rememberLazyListState()

    val nearEnd by remember(state.results.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.results.isNotEmpty() && last >= state.results.size - 4
        }
    }
    LaunchedEffect(nearEnd, state.results.size, state.canLoadMore, filter) {
        val songsVisible = filter == SearchFilter.ALL || filter == SearchFilter.SONGS
        if (songsVisible && nearEnd && state.canLoadMore) onLoadMore()
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Buscar canciones, álbumes, artistas") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                onSearch()
            })
        )

        // Si no hay texto se ve "Explorar todo" (rejilla de categorías) y no hace falta el filtro
        if (state.query.isNotBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                items(SearchFilter.values().toList()) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) }
                    )
                }
            }
            // Búsqueda en tiempo real: barra fina mientras llegan los resultados nuevos
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        when {
            state.query.isBlank() -> ExploreContent(
                explore = explore,
                currentUrl = currentUrl,
                onLoad = onLoadExplore,
                onNeedThumb = onNeedThumb,
                onCategoryClick = onCategoryClick,
                onBack = onCategoryBack,
                onSongClick = onShelfSongClick,
                onAlbumClick = onAlbumClick
            )

            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSearch) { Text("Reintentar") }
            }

            state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Escribe algo y busca tu música",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                val showArtistsRow = filter == SearchFilter.ALL && state.artists.isNotEmpty()
                val showAlbumsRow = filter == SearchFilter.ALL && state.albums.isNotEmpty()
                if (showArtistsRow) {
                    item(key = "artists") { ArtistsRow(state.artists, onArtistClick) }
                }
                if (showAlbumsRow) {
                    item(key = "albums") { AlbumsRow(state.albums, onAlbumClick) }
                }
                if (filter == SearchFilter.ALBUMS) {
                    if (state.albums.isEmpty()) {
                        item(key = "no-albums") { EmptyFilterHint("No se encontraron álbumes") }
                    }
                    items(state.albums, key = { "al_" + it.url }) { album ->
                        AlbumListItem(album) { onAlbumClick(album) }
                    }
                }
                if (filter == SearchFilter.ARTISTS) {
                    if (state.artists.isEmpty()) {
                        item(key = "no-artists") { EmptyFilterHint("No se encontraron artistas") }
                    }
                    items(state.artists, key = { "ar_" + it.url }) { artist ->
                        ArtistListItem(artist) { onArtistClick(artist) }
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) {
                    itemsIndexed(state.results, key = { _, s -> s.url }) { index, song ->
                        val isCurrent = song.url == currentUrl
                        SongListItem(
                            song = song,
                            isCurrent = isCurrent,
                            onClick = { onSongClick(index) },
                            menuTarget = SongMenuTarget(song)
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(Modifier.size(28.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private enum class SearchFilter(val label: String) {
    ALL("Todo"), SONGS("Canciones"), ALBUMS("Álbumes"), ARTISTS("Artistas")
}

@Composable
private fun EmptyFilterHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Alto del reproductor pequeño flotante. */
internal val MiniPlayerHeight = 64.dp

/** Reproductor pequeño flotante: tarjeta redondeada teñida con el color de la portada y el progreso alrededor de la portada. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.current ?: return

    // Deslizar a los lados cambia de canción: a la izquierda la siguiente, a la derecha la anterior
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val nextState by rememberUpdatedState(onNext)
    val prevState by rememberUpdatedState(onPrevious)
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }

    val fraction = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f

    val accent = rememberArtAccent(song.thumb)
    // Mismo color de fondo que el reproductor grande: el de la portada, oscurecido para que el texto blanco se lea siempre
    val base = PlayerBase
    val cardColor = remember(accent) { tameForBackground(accent) }
    val tint = Brush.horizontalGradient(listOf(cardColor, lerp(cardColor, base, 0.6f)))

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = base,
        contentColor = Color.White,
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(tint)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val dx = offsetX.value
                            scope.launch {
                                if (dx > swipeThreshold) prevState() else if (dx < -swipeThreshold) nextState()
                                offsetX.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + amount) }
                    }
                }
                .clickable(onClick = onOpen)
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value * 0.6f
                        // Al deslizar se atenúa un poco, para notar que va a cambiar de canción
                        alpha = 1f - (kotlin.math.abs(offsetX.value) / (swipeThreshold * 4f)).coerceIn(0f, 0.5f)
                    }
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressCover(song.thumb, fraction)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    // Si el título es largo se desplaza en carrusel (y reinicia al cambiar de canción)
                    androidx.compose.runtime.key(song.url) {
                        Text(
                            song.title,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1500,
                                velocity = 40.dp
                            )
                        )
                    }
                    Text(
                        state.error ?: song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.7f)
                    )
                }
                if (state.isLoadingStream) {
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    }
                } else {
                    // Solo pausar/reproducir: círculo blanco con el icono negro
                    FilledIconButton(
                        onClick = onToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Portada con el progreso de la canción dando la vuelta completa alrededor (empieza arriba, en sentido horario). */
@Composable
private fun ProgressCover(thumb: String?, fraction: Float) {
    val track = Color.White.copy(alpha = 0.2f)
    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2f
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val r = 16.dp.toPx().coerceAtMost((right - left) / 2f)
            val path = Path().apply {
                moveTo(size.width / 2f, top)
                lineTo(right - r, top)
                arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)
                lineTo(right, bottom - r)
                arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
                lineTo(left + r, bottom)
                arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
                lineTo(left, top + r)
                arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
                close()
            }
            drawPath(path, track, style = Stroke(width = stroke, cap = StrokeCap.Round))
            if (fraction > 0f) {
                val measure = PathMeasure()
                measure.setPath(path, false)
                val seg = Path()
                measure.getSegment(0f, measure.length * fraction, seg, true)
                drawPath(seg, Color.White, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
        AsyncImage(
            model = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}
