package com.lumodsound.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumodsound.app.data.AlbumUi
import com.lumodsound.app.data.SongUi

private const val DIAL_PAGE_SIZE = 9
private const val DIAL_MAX_PAGES = 3

private class DialItem(val title: String, val thumb: String?, val song: SongUi?, val onClick: () -> Unit)

/**
 * "Accesos directos" como en YouTube Music: encabezado con el nombre de la cuenta y páginas de 3 x 3 portadas
 * con el título encima; se desliza de lado y abajo hay puntitos con la página actual.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDial(
    title: String,
    strapline: String?,
    songs: List<SongUi>,
    albums: List<AlbumUi>,
    onSongClick: (List<SongUi>, Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit
) {
    val items = remember(songs, albums) {
        val list = ArrayList<DialItem>()
        songs.forEachIndexed { i, s -> list.add(DialItem(s.title, s.thumb, s) { onSongClick(songs, i) }) }
        albums.forEach { a -> list.add(DialItem(a.name, a.thumb, null) { onAlbumClick(a) }) }
        list.take(DIAL_PAGE_SIZE * DIAL_MAX_PAGES)
    }
    if (items.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!strapline.isNullOrBlank()) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7B1FA2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strapline.trim().take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            Column {
                if (!strapline.isNullOrBlank()) {
                    Text(
                        strapline.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 10.dp
            val tile = (maxWidth - 32.dp - gap * 2) / 3
            val pageHeight = tile * 3 + gap * 2
            val pageCount = (items.size + DIAL_PAGE_SIZE - 1) / DIAL_PAGE_SIZE
            val pagerState = rememberPagerState(pageCount = { pageCount })
            Column {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight)
                ) { page ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        items.drop(page * DIAL_PAGE_SIZE).take(DIAL_PAGE_SIZE).chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { DialTile(it, Modifier.size(tile)) }
                                repeat(3 - row.size) { Spacer(Modifier.size(tile)) }
                            }
                        }
                    }
                }
                if (pageCount > 1) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(pageCount) { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialTile(item: DialItem, modifier: Modifier) {
    val openMenu = LocalSongMenu.current
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = item.onClick,
                onLongClick = { item.song?.let { openMenu(SongMenuTarget(it)) } }
            )
    ) {
        AsyncImage(
            model = item.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
        )
        Text(
            item.title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
