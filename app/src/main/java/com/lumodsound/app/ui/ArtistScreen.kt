package com.lumodsound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumodsound.app.AlbumState
import com.lumodsound.app.ArtistState
import com.lumodsound.app.data.AlbumUi
import com.lumodsound.app.data.ArtistUi
import com.lumodsound.app.data.SongUi
import java.util.Locale
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private fun compactCount(value: Double, unit: String): String {
    // 150,0 -> "150"; 12,5 -> "12,5"
    val text = String.format(Locale.getDefault(), "%.1f", value).replace(Regex("[.,]0$"), "")
    return str(R.string.subscribers, text, unit)
}

private fun subscribersText(n: Long): String? = when {
    n < 0 -> null
    n >= 1_000_000 -> compactCount(n / 1_000_000.0, "M")
    n >= 1_000 -> compactCount(n / 1_000.0, "k")
    else -> str(R.string.subscribers_count, n)
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text(str(R.string.retry)) }
    }
}

/** "Sencillo", "EP · 5 canciones" o "Álbum · 12 canciones", según cuántas canciones tiene. */
private fun albumLabel(a: AlbumUi): String {
    a.subtitle?.takeIf { it.isNotBlank() }?.let { return it }
    val n = a.trackCount ?: return str(R.string.release)
    return when {
        n <= 3 -> str(R.string.single)
        n <= 6 -> str(R.string.ep_songs, n)
        else -> str(R.string.album_songs, n)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// ------------------------------------------------------------------
// Fila de artistas (arriba de los resultados de búsqueda)
// ------------------------------------------------------------------

@Composable
fun ArtistsRow(artists: List<ArtistUi>, onClick: (ArtistUi) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionTitle(str(R.string.artists))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.url }) { a ->
                Column(
                    Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick(a) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = a.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        a.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Carrusel de álbumes (resultados de búsqueda, sección "Todo"). */
@Composable
fun AlbumsRow(albums: List<AlbumUi>, onClick: (AlbumUi) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionTitle(str(R.string.albums))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums.take(12), key = { it.url }) { album ->
                AlbumCard(album, 130.dp) { onClick(album) }
            }
        }
    }
}

/** Álbum en lista vertical (filtro "Álbumes" de la búsqueda): portada, nombre y "Álbum · Artista". */
@Composable
fun AlbumListItem(album: AlbumUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                album.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(albumLabel(album), album.artist).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Artista en lista vertical (filtro "Artistas" de la búsqueda). */
@Composable
fun ArtistListItem(artist: ArtistUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
        )
    }
}

// ------------------------------------------------------------------
// Pantalla de artista
// ------------------------------------------------------------------

@Composable
fun ArtistScreen(
    state: ArtistState,
    currentUrl: String?,
    onBack: () -> Unit,
    onPlay: (List<SongUi>, Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllSongs by remember(state.artist?.url) { mutableStateOf(false) }
    var aboutExpanded by remember(state.artist?.url) { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBarsBottomInset.current)) {
        item(key = "hero") { ArtistHero(state, onBack) }

        when {
            state.error -> item(key = "error") {
                Text(
                    str(R.string.couldn_t_load_this_artist),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }

            state.loading -> item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            else -> {
                if (state.popular.isNotEmpty()) {
                    item(key = "actions") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = { onPlay(state.popular, 0) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(str(R.string.play))
                            }
                            FilledTonalButton(onClick = { onPlay(state.popular.shuffled(), 0) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(str(R.string.shuffle))
                            }
                        }
                    }

                    item(key = "popular-title") {
                        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) { SectionTitle(str(R.string.popular_songs)) }
                    }
                    val shown = if (showAllSongs) state.popular else state.popular.take(5)
                    itemsIndexed(shown, key = { _, s -> "p-" + s.url }) { index, song ->
                        SongListItem(
                            song = song,
                            isCurrent = song.url == currentUrl,
                            onClick = { onPlay(state.popular, index) },
                            menuTarget = SongMenuTarget(song)
                        )
                    }
                    if (state.popular.size > 5) {
                        item(key = "popular-more") {
                            TextButton(
                                onClick = { showAllSongs = !showAllSongs },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) { Text(if (showAllSongs) str(R.string.show_less) else str(R.string.show_more)) }
                        }
                    }
                }

                if (state.popularFailed && state.popular.isEmpty()) {
                    item(key = "popular-failed") {
                        RetryRow(str(R.string.couldn_t_load_the_popular), onRetry)
                    }
                }

                if (state.classifying) {
                    if (state.releases.isNotEmpty()) {
                        item(key = "releases") {
                            AlbumShelf(
                                title = str(R.string.releases),
                                albums = state.releases,
                                cardSize = 150.dp,
                                onClick = onAlbumClick,
                                hint = str(R.string.separating_albums_and_singles)
                            )
                        }
                    }
                } else {
                    if (state.releasesFailed) {
                        item(key = "releases-failed") {
                            RetryRow(str(R.string.couldn_t_load_the_albums), onRetry)
                        }
                    }
                    if (state.albums.isNotEmpty()) {
                        item(key = "albums") {
                            AlbumShelf(str(R.string.albums), state.albums, 160.dp, onAlbumClick)
                        }
                    }
                    if (state.singles.isNotEmpty()) {
                        item(key = "singles") {
                            AlbumShelf(str(R.string.singles_and_eps), state.singles, 130.dp, onAlbumClick)
                        }
                    }
                }

                if (!state.description.isNullOrBlank()) {
                    item(key = "about") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionTitle(str(R.string.about))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .clickable { aboutExpanded = !aboutExpanded }
                            ) {
                                Text(
                                    state.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (aboutExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ArtistHero(state: ArtistState, onBack: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val image = state.heroImage ?: state.artist?.thumb
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (image != null) HiResArtwork(image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
        // Degradado hacia el fondo para que el nombre siempre se lea
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to surface))
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.back), tint = Color.White)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                state.artist?.name.orEmpty(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subs = if (state.subscribers >= 0) state.subscribers else state.artist?.subscribers ?: -1L
            subscribersText(subs)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<AlbumUi>,
    cardSize: Dp,
    onClick: (AlbumUi) -> Unit,
    hint: String? = null
) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionTitle(title)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.url }) { album ->
                AlbumCard(album, cardSize) { onClick(album) }
            }
        }
    }
}

@Composable
fun AlbumCard(album: AlbumUi, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            albumLabel(album),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ------------------------------------------------------------------
// Pantalla de álbum / sencillo / EP
// ------------------------------------------------------------------

@Composable
fun AlbumScreen(
    state: AlbumState,
    currentUrl: String?,
    onBack: () -> Unit,
    onPlay: (List<SongUi>, Int) -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val album = state.album

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBarsBottomInset.current)) {
        item(key = "top") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.back))
                }
            }
        }

        if (album != null) {
            item(key = "cover") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 12.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(240.dp)
                    ) {
                        HiResArtwork(album.thumb, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        album.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onArtistClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (album.trackCount != null) {
                        Text(
                            albumLabel(album),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when {
            state.loading -> item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            state.error -> item(key = "error") {
                Text(
                    str(R.string.couldn_t_load_this_album),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }

            else -> {
                item(key = "actions") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        Button(onClick = { onPlay(state.tracks, 0) }, enabled = state.tracks.isNotEmpty()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str(R.string.play))
                        }
                        FilledTonalButton(
                            onClick = { onPlay(state.tracks.shuffled(), 0) },
                            enabled = state.tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str(R.string.shuffle))
=======

private fun compactCount(value: Double, unit: String): String {
    // 150,0 -> "150"; 12,5 -> "12,5"
    val text = String.format(Locale.getDefault(), "%.1f", value).replace(Regex("[.,]0$"), "")
    return "$text $unit suscriptores"
}

private fun subscribersText(n: Long): String? = when {
    n < 0 -> null
    n >= 1_000_000 -> compactCount(n / 1_000_000.0, "M")
    n >= 1_000 -> compactCount(n / 1_000.0, "k")
    else -> "$n suscriptores"
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

/** "Sencillo", "EP · 5 canciones" o "Álbum · 12 canciones", según cuántas canciones tiene. */
private fun albumLabel(a: AlbumUi): String {
    a.subtitle?.takeIf { it.isNotBlank() }?.let { return it }
    val n = a.trackCount ?: return "Lanzamiento"
    return when {
        n <= 3 -> "Sencillo"
        n <= 6 -> "EP · $n canciones"
        else -> "Álbum · $n canciones"
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// ------------------------------------------------------------------
// Fila de artistas (arriba de los resultados de búsqueda)
// ------------------------------------------------------------------

@Composable
fun ArtistsRow(artists: List<ArtistUi>, onClick: (ArtistUi) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionTitle("Artistas")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.url }) { a ->
                Column(
                    Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick(a) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = a.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        a.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Carrusel de álbumes (resultados de búsqueda, sección "Todo"). */
@Composable
fun AlbumsRow(albums: List<AlbumUi>, onClick: (AlbumUi) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionTitle("Álbumes")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums.take(12), key = { it.url }) { album ->
                AlbumCard(album, 130.dp) { onClick(album) }
            }
        }
    }
}

/** Álbum en lista vertical (filtro "Álbumes" de la búsqueda): portada, nombre y "Álbum · Artista". */
@Composable
fun AlbumListItem(album: AlbumUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                album.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(albumLabel(album), album.artist).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Artista en lista vertical (filtro "Artistas" de la búsqueda). */
@Composable
fun ArtistListItem(artist: ArtistUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
        )
    }
}

// ------------------------------------------------------------------
// Pantalla de artista
// ------------------------------------------------------------------

@Composable
fun ArtistScreen(
    state: ArtistState,
    currentUrl: String?,
    onBack: () -> Unit,
    onPlay: (List<SongUi>, Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllSongs by remember(state.artist?.url) { mutableStateOf(false) }
    var aboutExpanded by remember(state.artist?.url) { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize()) {
        item(key = "hero") { ArtistHero(state, onBack) }

        when {
            state.error -> item(key = "error") {
                Text(
                    "No se pudo cargar este artista",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }

            state.loading -> item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            else -> {
                if (state.popular.isNotEmpty()) {
                    item(key = "actions") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = { onPlay(state.popular, 0) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reproducir")
                            }
                            FilledTonalButton(onClick = { onPlay(state.popular.shuffled(), 0) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }
                    }

                    item(key = "popular-title") {
                        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) { SectionTitle("Canciones populares") }
                    }
                    val shown = if (showAllSongs) state.popular else state.popular.take(5)
                    itemsIndexed(shown, key = { _, s -> "p-" + s.url }) { index, song ->
                        SongListItem(
                            song = song,
                            isCurrent = song.url == currentUrl,
                            onClick = { onPlay(state.popular, index) },
                            menuTarget = SongMenuTarget(song)
                        )
                    }
                    if (state.popular.size > 5) {
                        item(key = "popular-more") {
                            TextButton(
                                onClick = { showAllSongs = !showAllSongs },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) { Text(if (showAllSongs) "Ver menos" else "Ver más") }
                        }
                    }
                }

                if (state.popularFailed && state.popular.isEmpty()) {
                    item(key = "popular-failed") {
                        RetryRow("No se pudieron cargar las canciones populares", onRetry)
                    }
                }

                if (state.classifying) {
                    if (state.releases.isNotEmpty()) {
                        item(key = "releases") {
                            AlbumShelf(
                                title = "Lanzamientos",
                                albums = state.releases,
                                cardSize = 150.dp,
                                onClick = onAlbumClick,
                                hint = "Separando álbumes y sencillos…"
                            )
                        }
                    }
                } else {
                    if (state.releasesFailed) {
                        item(key = "releases-failed") {
                            RetryRow("No se pudieron cargar los álbumes", onRetry)
                        }
                    }
                    if (state.albums.isNotEmpty()) {
                        item(key = "albums") {
                            AlbumShelf("Álbumes", state.albums, 160.dp, onAlbumClick)
                        }
                    }
                    if (state.singles.isNotEmpty()) {
                        item(key = "singles") {
                            AlbumShelf("Sencillos y EPs", state.singles, 130.dp, onAlbumClick)
                        }
                    }
                }

                if (!state.description.isNullOrBlank()) {
                    item(key = "about") {
                        Column(Modifier.padding(top = 24.dp)) {
                            SectionTitle("Acerca de")
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .clickable { aboutExpanded = !aboutExpanded }
                            ) {
                                Text(
                                    state.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (aboutExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ArtistHero(state: ArtistState, onBack: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val image = state.heroImage ?: state.artist?.thumb
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (image != null) HiResArtwork(image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
        // Degradado hacia el fondo para que el nombre siempre se lea
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to surface))
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                state.artist?.name.orEmpty(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subs = if (state.subscribers >= 0) state.subscribers else state.artist?.subscribers ?: -1L
            subscribersText(subs)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<AlbumUi>,
    cardSize: Dp,
    onClick: (AlbumUi) -> Unit,
    hint: String? = null
) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionTitle(title)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.url }) { album ->
                AlbumCard(album, cardSize) { onClick(album) }
            }
        }
    }
}

@Composable
fun AlbumCard(album: AlbumUi, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            albumLabel(album),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ------------------------------------------------------------------
// Pantalla de álbum / sencillo / EP
// ------------------------------------------------------------------

@Composable
fun AlbumScreen(
    state: AlbumState,
    currentUrl: String?,
    onBack: () -> Unit,
    onPlay: (List<SongUi>, Int) -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val album = state.album

    LazyColumn(modifier.fillMaxSize()) {
        item(key = "top") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        }

        if (album != null) {
            item(key = "cover") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 12.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(240.dp)
                    ) {
                        HiResArtwork(album.thumb, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        album.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onArtistClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (album.trackCount != null) {
                        Text(
                            albumLabel(album),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when {
            state.loading -> item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            state.error -> item(key = "error") {
                Text(
                    "No se pudo cargar este álbum",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }

            else -> {
                item(key = "actions") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        Button(onClick = { onPlay(state.tracks, 0) }, enabled = state.tracks.isNotEmpty()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reproducir")
                        }
                        FilledTonalButton(
                            onClick = { onPlay(state.tracks.shuffled(), 0) },
                            enabled = state.tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Aleatorio")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
                        }
                    }
                }
                itemsIndexed(state.tracks, key = { i, s -> "t$i-${s.url}" }) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrent = song.url == currentUrl,
                        onClick = { onPlay(state.tracks, index) },
                        menuTarget = SongMenuTarget(song)
                    )
                }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
