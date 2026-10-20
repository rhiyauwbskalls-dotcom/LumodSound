package com.sonaris.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.sonaris.app.player.Downloads
import com.sonaris.app.player.MediaCache
import androidx.compose.runtime.produceState
import com.sonaris.app.data.Playlist
import com.sonaris.app.data.SongUi

const val FAVORITES_DETAIL = "favorites"
const val CACHE_DETAIL = "cache"
const val DOWNLOADS_DETAIL = "downloads"

/** Fila de canción con botón de "más opciones" (comparte menú con el resto de la app). */
@Composable
fun SongListItem(
    song: SongUi,
    isCurrent: Boolean,
    onClick: () -> Unit,
    menuTarget: SongMenuTarget
) {
    val openMenu = LocalSongMenu.current
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val dur = if (song.durationSec > 0) " · ${formatTime(song.durationSec * 1000)}" else ""
            Text(song.artist + dur, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            AsyncImage(
                model = song.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        trailingContent = {
            IconButton(onClick = { openMenu(menuTarget) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
            else Color.Transparent
        ),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun LibraryScreen(
    favorites: List<SongUi>,
    playlists: List<Playlist>,
    currentUrl: String?,
    detail: String?,
    onOpenDetail: (String?) -> Unit,
    onPlay: (List<SongUi>, Int) -> Unit,
    onNewPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveFromPlaylist: (String, SongUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlist = playlists.firstOrNull { it.id == detail }

    // Canciones descargadas al 100% en la caché; se vuelve a leer al entrar a la Biblioteca o a un detalle
    val context = LocalContext.current
    val cachedSongs by produceState(emptyList<SongUi>(), detail) {
        value = withContext(Dispatchers.IO) {
            try {
                MediaCache.fullyCachedSongs(context)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // Descargas (permanentes): se recalculan cuando cambia lo descargado
    val downloadStates by Downloads.states.collectAsStateWithLifecycle()
    val downloadedSongs by produceState(emptyList<SongUi>(), detail, downloadStates) {
        value = withContext(Dispatchers.IO) {
            try {
                Downloads.completedSongs(context)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    when {
        detail == FAVORITES_DETAIL -> SongListDetail(
            title = "Favoritos",
            songs = favorites,
            currentUrl = currentUrl,
            onBack = { onOpenDetail(null) },
            onPlay = onPlay,
            targetFor = { SongMenuTarget(it) },
            modifier = modifier
        )

        detail == DOWNLOADS_DETAIL -> SongListDetail(
            title = "Descargas",
            songs = downloadedSongs,
            currentUrl = currentUrl,
            onBack = { onOpenDetail(null) },
            onPlay = onPlay,
            targetFor = { SongMenuTarget(it) },
            emptyText = "Sin canciones",
            modifier = modifier
        )

        detail == CACHE_DETAIL -> SongListDetail(
            title = "Caché",
            songs = cachedSongs,
            currentUrl = currentUrl,
            onBack = { onOpenDetail(null) },
            onPlay = onPlay,
            targetFor = { SongMenuTarget(it) },
            emptyText = "Sin canciones",
            modifier = modifier
        )

        playlist != null -> SongListDetail(
            title = playlist.name,
            songs = playlist.songs,
            currentUrl = currentUrl,
            onBack = { onOpenDetail(null) },
            onPlay = onPlay,
            targetFor = { song ->
                SongMenuTarget(
                    song,
                    onRemove = { onRemoveFromPlaylist(playlist.id, song) },
                    removeLabel = "Quitar de esta playlist"
                )
            },
            modifier = modifier
        )

        else -> LibraryHome(
            favorites = favorites,
            downloadedCount = downloadedSongs.size,
            cachedCount = cachedSongs.size,
            playlists = playlists,
            onOpenDetail = onOpenDetail,
            onNewPlaylist = onNewPlaylist,
            onDeletePlaylist = onDeletePlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun LibraryHome(
    favorites: List<SongUi>,
    downloadedCount: Int,
    cachedCount: Int,
    playlists: List<Playlist>,
    onOpenDetail: (String?) -> Unit,
    onNewPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    modifier: Modifier
) {
    var toDelete by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Biblioteca",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = onNewPlaylist) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nueva")
                }
            }
        }

        item(key = "favorites") {
            ListItem(
                headlineContent = { Text("Favoritos") },
                supportingContent = { Text("${favorites.size} canciones") },
                leadingContent = {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                modifier = Modifier.clickable { onOpenDetail(FAVORITES_DETAIL) }
            )
        }

        item(key = "downloads") {
            ListItem(
                headlineContent = { Text("Descargas") },
                supportingContent = { Text("$downloadedCount canciones") },
                leadingContent = {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                },
                modifier = Modifier.clickable { onOpenDetail(DOWNLOADS_DETAIL) }
            )
        }

        item(key = "cache") {
            ListItem(
                headlineContent = { Text("Caché") },
                supportingContent = { Text("$cachedCount canciones · disponibles sin internet") },
                leadingContent = {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                },
                modifier = Modifier.clickable { onOpenDetail(CACHE_DETAIL) }
            )
        }

        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Aún no tienes playlists. Crea una con el botón \"Nueva\" o desde el menú ⋮ de cualquier canción.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }
        }

        items(playlists, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${p.songs.size} canciones") },
                leadingContent = {
                    val thumb = p.songs.firstOrNull()?.thumb
                    if (thumb != null) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.LibraryMusic, contentDescription = null) }
                    }
                },
                trailingContent = {
                    IconButton(onClick = { toDelete = p }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar playlist")
                    }
                },
                modifier = Modifier.clickable { onOpenDetail(p.id) }
            )
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar playlist") },
            text = { Text("¿Eliminar \"${p.name}\"? Las canciones no se borran de YouTube.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(p.id)
                    toDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SongListDetail(
    title: String,
    songs: List<SongUi>,
    currentUrl: String?,
    onBack: () -> Unit,
    onPlay: (List<SongUi>, Int) -> Unit,
    targetFor: (SongUi) -> SongMenuTarget,
    modifier: Modifier,
    emptyText: String = "Todavía no hay canciones aquí"
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    emptyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { onPlay(songs, 0) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reproducir")
                }
                FilledTonalButton(onClick = { onPlay(songs.shuffled(), 0) }) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aleatorio")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs, key = { _, s -> s.url }) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrent = song.url == currentUrl,
                        onClick = { onPlay(songs, index) },
                        menuTarget = targetFor(song)
                    )
                }
            }
        }
    }
}
