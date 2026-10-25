package com.lumodsound.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumodsound.app.SuggestState
import com.lumodsound.app.data.AlbumUi
import com.lumodsound.app.data.ArtistUi
import com.lumodsound.app.data.SongUi
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Lo que se ve mientras escribes (como Metrolist): primero el historial que coincide, luego las consultas sugeridas
 * por YouTube Music y, debajo, resultados directos (canciones, álbumes y artistas) que se pueden tocar sin buscar.
 * Con la barra vacía se muestra el historial completo.
 */
@Composable
internal fun SearchSuggestionsList(
    query: String,
    history: List<String>,
    suggest: SuggestState,
    currentUrl: String?,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSongClick: (SongUi) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    onArtistClick: (ArtistUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val q = query.trim()
    val shownHistory = if (q.isEmpty()) history.take(15)
    else history.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }.take(3)
    val queries = suggest.queries.filter { s -> shownHistory.none { it.equals(s, ignoreCase = true) } }
    val hasItems = suggest.songs.isNotEmpty() || suggest.albums.isNotEmpty() || suggest.artists.isNotEmpty()

    LazyColumn(modifier.fillMaxSize()) {
        if (q.isEmpty() && shownHistory.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        str(R.string.recent_searches),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearHistory) { Text(str(R.string.clear_all)) }
                }
            }
        }
        items(shownHistory, key = { "h_$it" }) { text ->
            SuggestionRow(
                text = text,
                icon = Icons.Default.History,
                onClick = { onSubmit(text) },
                onFill = { onFill(text) },
                onDelete = { onDeleteHistory(text) }
            )
        }
        items(queries, key = { "q_$it" }) { text ->
            SuggestionRow(
                text = text,
                icon = Icons.Default.Search,
                onClick = { onSubmit(text) },
                onFill = { onFill(text) },
                onDelete = null
            )
        }
        if (hasItems && (shownHistory.isNotEmpty() || queries.isNotEmpty())) {
            item(key = "divider") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
        items(suggest.artists, key = { "ar_" + it.url }) { artist ->
            ArtistListItem(artist) { onArtistClick(artist) }
        }
        items(suggest.albums, key = { "al_" + it.url }) { album ->
            AlbumListItem(album) { onAlbumClick(album) }
        }
        items(suggest.songs, key = { "s_" + it.url }) { song ->
            SongListItem(
                song = song,
                isCurrent = song.url == currentUrl,
                onClick = { onSongClick(song) },
                menuTarget = SongMenuTarget(song)
            )
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SuggestionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = str(R.string.remove_from_history),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onFill) {
            Icon(
                Icons.Default.NorthWest,
                contentDescription = str(R.string.use_in_the_search_bar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
