package com.lumodsound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumodsound.app.data.AlbumUi
import com.lumodsound.app.data.Playlist
import com.lumodsound.app.data.SongUi
import com.lumodsound.app.player.Downloads
import kotlinx.coroutines.launch
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Qué canción abrió el menú y (opcional) cómo quitarla de la lista donde está. */
class SongMenuTarget(
    val song: SongUi,
    val onRemove: (() -> Unit)? = null,
    val removeLabel: String = str(R.string.remove_from_list),
    /** En el menú del reproductor grande no tiene sentido "agregar a la cola" de lo que ya suena. */
    val showQueueActions: Boolean = true,
    /** Si se abrió desde el reproductor grande (o la letra): usa su propia hoja, con los colores de la portada. */
    val fromPlayer: Boolean = false
)

class NewPlaylistRequest(val song: SongUi?)

/** Cualquier pantalla puede abrir el menú de una canción llamando a esto. */
val LocalSongMenu = staticCompositionLocalOf<(SongMenuTarget) -> Unit> { {} }

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    enabled: Boolean = true
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (supporting != null) {
            { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            leadingIconColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim
        ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

/** Estado de la búsqueda del álbum de la canción del menú. */
sealed interface AlbumLookup {
    data object Loading : AlbumLookup
    class Found(val album: AlbumUi) : AlbumLookup
    data object None : AlbumLookup
}

/** Menú de tres puntos: una hoja para el reproductor grande y otra para el resto de la app. */
@Composable
fun SongMenuSheet(
    target: SongMenuTarget,
    isFavorite: Boolean,
    album: AlbumLookup,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenInfo: () -> Unit,
    onStartArtistRadio: () -> Unit,
    downloadState: Int,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    if (target.fromPlayer) {
        PlayerMenuSheet(
            target, isFavorite, album,
            onToggleFavorite, onAddToPlaylist, onOpenArtist, onOpenAlbum, onOpenInfo, onStartArtistRadio,
            downloadState, onDownload, onRemoveDownload, onDismiss
        )
    } else {
        DefaultMenuSheet(
            target, isFavorite, album,
            onToggleFavorite, onAddToPlaylist, onPlayNext, onAddToQueue, onOpenArtist, onOpenAlbum, onOpenInfo,
            onStartArtistRadio, downloadState, onDownload, onRemoveDownload, onDismiss
        )
    }
}

private val PlayerHeart = Color(0xFFFF5A7A)

@Composable
private fun PlayerMenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val color = if (enabled) tint else Color.White.copy(alpha = 0.4f)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Hoja del reproductor grande: mismo degradado de la portada, acciones rápidas en una fila. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMenuSheet(
    target: SongMenuTarget,
    isFavorite: Boolean,
    album: AlbumLookup,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenInfo: () -> Unit,
    onStartArtistRadio: () -> Unit,
    downloadState: Int,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    val accent = rememberArtAccent(target.song.thumb)
    val top = lerp(Color(0xFF101010), accent, 0.42f)
    val bottom = Color(0xFF0B0B0B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bottom,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 18.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Box(
                Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) { HiResArtwork(target.song.thumb, Modifier.fillMaxSize()) }
            Spacer(Modifier.height(14.dp))
            Text(
                target.song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                target.song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PlayerMenuAction(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (isFavorite) str(R.string.in_favorites) else str(R.string.favorite),
                    onClick = { closeThen(onToggleFavorite) },
                    modifier = Modifier.weight(1f),
                    tint = if (isFavorite) PlayerHeart else Color.White
                )
                PlayerMenuAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = str(R.string.playlist),
                    onClick = { closeThen(onAddToPlaylist) },
                    modifier = Modifier.weight(1f)
                )
                if (target.song.artist.isNotBlank()) {
                    PlayerMenuAction(
                        icon = Icons.Default.Person,
                        label = str(R.string.artist),
                        onClick = { closeThen(onOpenArtist) },
                        modifier = Modifier.weight(1f)
                    )
                    PlayerMenuAction(
                        icon = Icons.Default.Radio,
                        label = str(R.string.radio),
                        onClick = { closeThen(onStartArtistRadio) },
                        modifier = Modifier.weight(1f)
                    )
                }
                when (album) {
                    AlbumLookup.Loading -> PlayerMenuAction(
                        icon = Icons.Default.Album, label = str(R.string.searching), onClick = {},
                        modifier = Modifier.weight(1f), enabled = false, loading = true
                    )
                    is AlbumLookup.Found -> PlayerMenuAction(
                        icon = Icons.Default.Album, label = str(R.string.album),
                        onClick = { closeThen { onOpenAlbum(album.album) } },
                        modifier = Modifier.weight(1f)
                    )
                    AlbumLookup.None -> PlayerMenuAction(
                        icon = Icons.Default.Album, label = str(R.string.no_album), onClick = {},
                        modifier = Modifier.weight(1f), enabled = false
                    )
                }
                PlayerMenuAction(
                    icon = Icons.Default.Info,
                    label = str(R.string.info),
                    onClick = { closeThen(onOpenInfo) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(4.dp))
            when (downloadState) {
                Downloads.DONE -> PlayerMenuAction(
                    icon = Icons.Default.DownloadDone,
                    label = str(R.string.remove_download),
                    onClick = { closeThen(onRemoveDownload) },
                    modifier = Modifier.width(140.dp)
                )
                Downloads.IN_PROGRESS -> PlayerMenuAction(
                    icon = Icons.Default.Downloading,
                    label = str(R.string.cancel_download),
                    onClick = { closeThen(onRemoveDownload) },
                    modifier = Modifier.width(140.dp)
                )
                else -> PlayerMenuAction(
                    icon = Icons.Default.Download,
                    label = str(R.string.download),
                    onClick = { closeThen(onDownload) },
                    modifier = Modifier.width(140.dp)
                )
            }

            target.onRemove?.let { remove ->
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { closeThen { remove(); onDismiss() } }) {
                    Text(target.removeLabel, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** Hoja inferior del resto de la app: se desliza hacia abajo para cerrarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultMenuSheet(
    target: SongMenuTarget,
    isFavorite: Boolean,
    album: AlbumLookup,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenInfo: () -> Unit,
    onStartArtistRadio: () -> Unit,
    downloadState: Int,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    /** Cierra la hoja con su animación y recién después ejecuta la acción. */
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            // Cabecera: portada + título + artista, sin tarjeta
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) { HiResArtwork(target.song.thumb, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        target.song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        target.song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Column(Modifier.verticalScroll(rememberScrollState())) {
                MenuRow(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isFavorite) str(R.string.remove_from_favorites) else str(R.string.add_to_favorites),
                    { closeThen(onToggleFavorite) }
                )
                MenuRow(Icons.AutoMirrored.Filled.PlaylistAdd, str(R.string.add_to_a_playlist), { closeThen(onAddToPlaylist) })
                if (target.showQueueActions) {
                    MenuRow(Icons.Default.SkipNext, str(R.string.play_next), { closeThen(onPlayNext) })
                    MenuRow(AppIcons.Queue, str(R.string.add_to_queue), { closeThen(onAddToQueue) })
                }
                if (target.song.artist.isNotBlank()) {
                    MenuRow(Icons.Default.Person, str(R.string.go_to_artist), { closeThen(onOpenArtist) })
                    MenuRow(
                        Icons.Default.Radio,
                        str(R.string.artist_radio),
                        { closeThen(onStartArtistRadio) },
                        supporting = str(R.string.only_songs_by, target.song.artist)
                    )
                }
                when (downloadState) {
                    Downloads.DONE -> MenuRow(
                        Icons.Default.DownloadDone,
                        str(R.string.remove_download),
                        { closeThen(onRemoveDownload) },
                        supporting = str(R.string.saved_on_this_device)
                    )
                    Downloads.IN_PROGRESS -> MenuRow(
                        Icons.Default.Downloading,
                        str(R.string.downloading),
                        { closeThen(onRemoveDownload) },
                        supporting = str(R.string.tap_to_cancel)
                    )
                    else -> MenuRow(
                        Icons.Default.Download,
                        str(R.string.download),
                        { closeThen(onDownload) }
                    )
                }
                when (album) {
                    AlbumLookup.Loading -> ListItem(
                        headlineContent = { Text(str(R.string.searching_album)) },
                        leadingContent = {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    is AlbumLookup.Found -> MenuRow(
                        Icons.Default.Album,
                        str(R.string.go_to_album),
                        { closeThen { onOpenAlbum(album.album) } },
                        supporting = album.album.name
                    )
                    AlbumLookup.None -> MenuRow(Icons.Default.Album, str(R.string.no_album), {}, enabled = false)
                }
                MenuRow(Icons.Default.Info, str(R.string.song_information), { closeThen(onOpenInfo) })
                target.onRemove?.let { remove ->
                    MenuRow(Icons.Default.Delete, target.removeLabel, {
                        closeThen {
                            remove()
                            onDismiss()
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
        title = { Text(str(R.string.add_to_playlist)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MenuRow(Icons.Default.Add, str(R.string.new_playlist), onNew)
                playlists.forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(str(R.string.songs_count, p.songs.size)) },
                        leadingContent = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(p) }
                    )
                }
            }
        }
    )
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(str(R.string.name)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(str(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } }
    )
}

/** Hoja de la cuenta: iniciar sesión, o ver que ya está iniciada y poder cerrarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (loggedIn) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                contentDescription = null,
                tint = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (loggedIn) str(R.string.already_signed_in) else str(R.string.sign_in),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (loggedIn) {
                    str(R.string.home_and_quick_picks_are)
                } else {
                    str(R.string.sign_in_with_your_google)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            if (loggedIn) {
                OutlinedButton(onClick = { closeThen(onLogout) }, modifier = Modifier.fillMaxWidth()) {
                    Text(str(R.string.sign_out))
                }
            } else {
                Button(onClick = { closeThen(onLogin) }, modifier = Modifier.fillMaxWidth()) {
                    Text(str(R.string.sign_in))
                }
            }
        }
    }
}
