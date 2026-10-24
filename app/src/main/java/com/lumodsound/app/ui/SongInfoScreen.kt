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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumodsound.app.SongInfoState
import com.lumodsound.app.data.AlbumUi
import java.text.NumberFormat
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Información de la canción: artista, álbum, año, duración y datos del audio. */
@Composable
fun SongInfoScreen(
    state: SongInfoState,
    onClose: () -> Unit,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenArtist: (String) -> Unit,
    onCopyLink: (String) -> Unit
) {
    val song = state.song ?: return
    val info = state.info

    // Surface fija el color del texto (onSurface); sin ella el texto sale negro sobre fondo oscuro
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .clickable(enabled = false) {} // que los toques no pasen a lo que hay debajo
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = str(R.string.close))
            }
            Text(str(R.string.song_information), style = MaterialTheme.typography.titleMedium)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) { HiResArtwork(song.thumb, Modifier.fillMaxSize()) }
            Spacer(Modifier.height(16.dp))
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            Column(Modifier.fillMaxWidth()) {
                InfoRow(str(R.string.artist), song.artist.ifBlank { str(R.string.unknown) },
                    onClick = if (song.artist.isNotBlank()) ({ onOpenArtist(song.artist) }) else null)

                val albumName = info?.albumName
                when {
                    albumName != null -> InfoRow(
                        str(R.string.album), albumName,
                        onClick = info?.album?.let { a -> { onOpenAlbum(a) } }
                    )
                    state.loading -> InfoRow(str(R.string.album), str(R.string.searching))
                    else -> InfoRow(str(R.string.album), str(R.string.no_album))
                }

                info?.year?.let { InfoRow(str(R.string.year), it) }

                val duration = info?.durationSec?.takeIf { it > 0 } ?: song.durationSec
                if (duration > 0) InfoRow(str(R.string.duration), formatDuration(duration))

                if (info?.audioFormat != null) {
                    InfoRow(str(R.string.audio_format), listOfNotNull(info.audioFormat, info.audioCodec).joinToString(" · "))
                }
                if (info != null && info.bitrateKbps > 0) InfoRow(str(R.string.quality), "${info.bitrateKbps} kbps")
                info?.channel?.let { InfoRow(str(R.string.youtube_channel), it) }
                if (info != null && info.views >= 0) {
                    InfoRow(str(R.string.youtube_views), NumberFormat.getIntegerInstance().format(info.views))
                }
                if (info != null && info.videoId.isNotBlank()) InfoRow(str(R.string.video_id), info.videoId, last = true)
            }

            if (state.loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { onCopyLink("https://music.youtube.com/watch?v=" + (info?.videoId ?: song.url.substringAfter("v=", ""))) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(str(R.string.copy_link))
=======

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Información de la canción: artista, álbum, año, duración y datos del audio. */
@Composable
fun SongInfoScreen(
    state: SongInfoState,
    onClose: () -> Unit,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenArtist: (String) -> Unit,
    onCopyLink: (String) -> Unit
) {
    val song = state.song ?: return
    val info = state.info

    // Surface fija el color del texto (onSurface); sin ella el texto sale negro sobre fondo oscuro
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .clickable(enabled = false) {} // que los toques no pasen a lo que hay debajo
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Cerrar")
            }
            Text("Información de la canción", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) { HiResArtwork(song.thumb, Modifier.fillMaxSize()) }
            Spacer(Modifier.height(16.dp))
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            Column(Modifier.fillMaxWidth()) {
                InfoRow("Artista", song.artist.ifBlank { "Desconocido" },
                    onClick = if (song.artist.isNotBlank()) ({ onOpenArtist(song.artist) }) else null)

                val albumName = info?.albumName
                when {
                    albumName != null -> InfoRow(
                        "Álbum", albumName,
                        onClick = info?.album?.let { a -> { onOpenAlbum(a) } }
                    )
                    state.loading -> InfoRow("Álbum", "Buscando…")
                    else -> InfoRow("Álbum", "Sin álbum")
                }

                info?.year?.let { InfoRow("Año", it) }

                val duration = info?.durationSec?.takeIf { it > 0 } ?: song.durationSec
                if (duration > 0) InfoRow("Duración", formatDuration(duration))

                if (info?.audioFormat != null) {
                    InfoRow("Formato de audio", listOfNotNull(info.audioFormat, info.audioCodec).joinToString(" · "))
                }
                if (info != null && info.bitrateKbps > 0) InfoRow("Calidad", "${info.bitrateKbps} kbps")
                info?.channel?.let { InfoRow("Canal de YouTube", it) }
                if (info != null && info.views >= 0) {
                    InfoRow("Reproducciones en YouTube", NumberFormat.getIntegerInstance().format(info.views))
                }
                if (info != null && info.videoId.isNotBlank()) InfoRow("ID del video", info.videoId, last = true)
            }

            if (state.loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { onCopyLink("https://music.youtube.com/watch?v=" + (info?.videoId ?: song.url.substringAfter("v=", ""))) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Copiar enlace")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null, last: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            if (onClick != null) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
