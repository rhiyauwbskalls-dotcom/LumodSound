package com.sonaris.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonaris.app.HomeState
import com.sonaris.app.Shelf
import com.sonaris.app.ShelfStatus
import com.sonaris.app.data.AlbumUi
import com.sonaris.app.data.GENRE_CHIPS
import com.sonaris.app.data.SongUi
import com.sonaris.app.data.YtMusicApi

@Composable
fun HomeScreen(
    home: HomeState,
    recents: List<SongUi>,
    currentUrl: String?,
    onSongClick: (List<SongUi>, Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    onGenreClick: (String) -> Unit,
    onChipClick: (YtMusicApi.FeedChip) -> Unit,
    onRefresh: () -> Unit,
    onRetry: (String) -> Unit,
    onLoadShelf: (String) -> Unit,
    loggedIn: Boolean,
    onAccountClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val greeting = remember { greetingForNow() }
                Text(
                    greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                }
                IconButton(onClick = onAccountClick) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = if (loggedIn) "Cuenta" else "Iniciar sesión",
                        tint = if (loggedIn) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }

        item(key = "chips") {
            if (home.chips.isEmpty() && !home.feedDone) {
                // Mientras YouTube responde se deja el hueco para que el Inicio no salte al llegar los chips
                Spacer(Modifier.height(40.dp))
            } else {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (home.chips.isNotEmpty()) {
                        // Chips de YouTube Music (Relajar, Energizar, Entrenar...)
                        items(home.chips, key = { it.params }) { chip ->
                            HomeChip(
                                label = chip.label,
                                selected = home.selectedChip == chip.params,
                                onClick = { onChipClick(chip) }
                            )
                        }
                    } else {
                        // Si YouTube no manda chips, quedan los géneros de siempre (abren la búsqueda)
                        items(GENRE_CHIPS) { (label, query) ->
                            HomeChip(label = label, selected = false, onClick = { onGenreClick(query) })
                        }
                    }
                }
            }
        }

        if (home.selectedChip != null) {
            // Un chip está elegido: solo lo que YouTube recomienda para él
            when {
                home.chipLoading -> item(key = "chip-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                home.chipFailed -> item(key = "chip-failed") {
                    Text(
                        "No se pudo cargar esta selección. Toca el chip otra vez para quitarlo o inténtalo de nuevo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
                else -> items(home.chipShelves, key = { it.id }) { shelf ->
                    ShelfSection(shelf, currentUrl, onSongClick, onRetry, onLoadShelf, onAlbumClick)
                }
            }
            return@LazyColumn
        }

        if (recents.isNotEmpty()) {
            // Acceso rápido: cuadrícula de 2 columnas con lo último que sonó
            item(key = "recents-grid") {
                QuickAccessGrid(recents.take(6), currentUrl) { i -> onSongClick(recents, i) }
            }
            if (recents.size > 6) {
                // "Escuchado recientemente": columnas de 3 canciones, como las Selecciones rápidas de YouTube Music
                item(key = "recents") {
                    Column(Modifier.padding(top = 24.dp)) {
                        ShelfTitle("Escuchado recientemente")
                        Spacer(Modifier.height(8.dp))
                        QuickPicks(recents.drop(6).take(30), currentUrl, onSongClick)
                    }
                }
            }
        }

        // Recomendaciones de YouTube Music
        items(home.feed, key = { it.id }) { shelf ->
            ShelfSection(shelf, currentUrl, onSongClick, onRetry, onLoadShelf, onAlbumClick)
        }

        if (!home.feedDone) {
            // Mientras YouTube responde no se muestra nada más: así el Inicio no cambia de orden al cargar
            item(key = "feed-loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Si YouTube trae pocos estantes, debajo siguen los de géneros para poder seguir bajando
            if (home.feed.size < 6) {
                items(home.shelves, key = { it.id }) { shelf ->
                    ShelfSection(shelf, currentUrl, onSongClick, onRetry, onLoadShelf)
                }
            }
        }
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * Cada estante pide sus canciones solo cuando aparece en pantalla (LazyColumn solo compone
 * lo que está a la vista), así no se descargan diez listas y cientos de portadas de golpe.
 */
@Composable
fun ShelfSection(
    shelf: Shelf,
    currentUrl: String?,
    onSongClick: (List<SongUi>, Int) -> Unit,
    onRetry: (String) -> Unit,
    onLoad: (String) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit = {}
) {
    LaunchedEffect(shelf.id, shelf.status) {
        if (shelf.status == ShelfStatus.IDLE) onLoad(shelf.id)
    }

    // "Accesos directos" y "Selecciones rápidas" de YouTube Music: páginas de 3 x 3 portadas con el título encima
    if ((shelf.speedDial || shelf.compact) && (shelf.songs.isNotEmpty() || shelf.albums.isNotEmpty())) {
        Column(Modifier.padding(top = 24.dp)) {
            SpeedDial(shelf.title, shelf.strapline, shelf.songs, shelf.albums, onSongClick, onAlbumClick)
        }
        return
    }

    Column(Modifier.padding(top = 24.dp)) {
        ShelfTitle(shelf.title)
        Spacer(Modifier.height(8.dp))
        when {
            shelf.songs.isNotEmpty() || shelf.albums.isNotEmpty() -> Column {
                if (shelf.songs.isNotEmpty()) {
                    if (shelf.compact) QuickPicks(shelf.songs, currentUrl, onSongClick)
                    else SongRow(shelf.songs, currentUrl, onSongClick)
                }
                if (shelf.albums.isNotEmpty()) {
                    if (shelf.songs.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shelf.albums, key = { it.url }) { album ->
                            AlbumCard(album, 140.dp) { onAlbumClick(album) }
                        }
                    }
                }
            }

            shelf.status == ShelfStatus.FAILED -> Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("No se pudo cargar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onRetry(shelf.id) }) { Text("Reintentar") }
            }

            else -> Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Spacer(
                        Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
fun SongRow(
    songs: List<SongUi>,
    currentUrl: String?,
    onSongClick: (List<SongUi>, Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs, key = { _, s -> s.url }) { index, song ->
            SongCard(song, song.url == currentUrl) { onSongClick(songs, index) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCard(song: SongUi, isCurrent: Boolean, onClick: () -> Unit) {
    val openMenu = LocalSongMenu.current
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { openMenu(SongMenuTarget(song)) }
            )
    ) {
        AsyncImage(
            model = song.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Selecciones rápidas: columnas de 3 filas pequeñas (portada, título y artista) que se deslizan de lado
 * y se detienen columna por columna; la siguiente asoma a la derecha.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPicks(
    songs: List<SongUi>,
    currentUrl: String?,
    onSongClick: (List<SongUi>, Int) -> Unit
) {
    val columns = remember(songs) { songs.chunked(3) }
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val columnWidth = LocalConfiguration.current.screenWidthDp.dp - 56.dp

    LazyRow(
        state = listState,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(columns) { columnIndex, column ->
            Column(Modifier.width(columnWidth)) {
                column.forEachIndexed { i, song ->
                    QuickPickItem(song, song.url == currentUrl) {
                        onSongClick(songs, columnIndex * 3 + i)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPickItem(song: SongUi, isCurrent: Boolean, onClick: () -> Unit) {
    val openMenu = LocalSongMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { openMenu(SongMenuTarget(song)) }
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { openMenu(SongMenuTarget(song)) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
        }
    }
}


private fun greetingForNow(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 6..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

/** Chip en forma de píldora: el elegido se rellena con el color de acento. */
@Composable
private fun HomeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh,
        label = "homeChipBg"
    )
    val fg = if (selected) scheme.onPrimary else scheme.onSurface
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Cuadrícula de acceso rápido (2 columnas) con las últimas canciones escuchadas. */
@Composable
private fun QuickAccessGrid(
    songs: List<SongUi>,
    currentUrl: String?,
    onClick: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        songs.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, song ->
                    QuickAccessTile(song, song.url == currentUrl, Modifier.weight(1f)) {
                        onClick(row * 2 + col)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessTile(
    song: SongUi,
    isCurrent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val openMenu = LocalSongMenu.current
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { openMenu(SongMenuTarget(song)) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}
