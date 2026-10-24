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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumodsound.app.ExploreState
import com.lumodsound.app.data.AlbumUi
import com.lumodsound.app.data.SongUi
import com.lumodsound.app.data.YtMusicApi
import kotlin.math.abs
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/**
 * Lo que se ve en Buscar cuando no hay texto: "Explorar todo", la rejilla de estados de ánimo y géneros de
 * YouTube Music (cada tarjeta con su color y la portada de una lista de esa categoría). Al tocar una
 * tarjeta se abre la página de la categoría con sus listas.
 */
@Composable
fun ExploreContent(
    explore: ExploreState,
    currentUrl: String?,
    onLoad: (force: Boolean) -> Unit,
    onNeedThumb: (YtMusicApi.ExploreCategory) -> Unit,
    onCategoryClick: (YtMusicApi.ExploreCategory) -> Unit,
    onBack: () -> Unit,
    onSongClick: (List<SongUi>, Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onLoad(false) }

    val selected = explore.selected
    if (selected != null) {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.back))
                }
                Text(
                    selected.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                explore.shelvesLoading -> Center { CircularProgressIndicator() }
                explore.shelvesFailed -> Center {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            str(R.string.couldn_t_load_this_category),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onCategoryClick(selected) }) { Text(str(R.string.retry)) }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalBarsBottomInset.current)) {
                    items(explore.shelves, key = { it.id }) { shelf ->
                        ShelfSection(shelf, currentUrl, onSongClick, {}, {}, onAlbumClick)
                    }
                }
            }
        }
        return
    }

    when {
        explore.loading || (explore.categories.isEmpty() && !explore.failed) ->
            Center(modifier) { CircularProgressIndicator() }

        explore.failed -> Center(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    str(R.string.couldn_t_load_the_categories),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onLoad(true) }) { Text(str(R.string.retry)) }
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBarsBottomInset.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    str(R.string.explore_all),
=======

/**
 * Lo que se ve en Buscar cuando no hay texto: "Explorar todo", la rejilla de estados de ánimo y géneros de
 * YouTube Music (cada tarjeta con su color y la portada de una lista de esa categoría). Al tocar una
 * tarjeta se abre la página de la categoría con sus listas.
 */
@Composable
fun ExploreContent(
    explore: ExploreState,
    currentUrl: String?,
    onLoad: (force: Boolean) -> Unit,
    onNeedThumb: (YtMusicApi.ExploreCategory) -> Unit,
    onCategoryClick: (YtMusicApi.ExploreCategory) -> Unit,
    onBack: () -> Unit,
    onSongClick: (List<SongUi>, Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onLoad(false) }

    val selected = explore.selected
    if (selected != null) {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    selected.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                explore.shelvesLoading -> Center { CircularProgressIndicator() }
                explore.shelvesFailed -> Center {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            "No se pudo cargar esta categoría.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onCategoryClick(selected) }) { Text("Reintentar") }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(explore.shelves, key = { it.id }) { shelf ->
                        ShelfSection(shelf, currentUrl, onSongClick, {}, {}, onAlbumClick)
                    }
                }
            }
        }
        return
    }

    when {
        explore.loading || (explore.categories.isEmpty() && !explore.failed) ->
            Center(modifier) { CircularProgressIndicator() }

        explore.failed -> Center(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    "No se pudieron cargar las categorías.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onLoad(true) }) { Text("Reintentar") }
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Explorar todo",
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            gridItems(explore.categories, key = { it.params }) { cat ->
                LaunchedEffect(cat.params) { onNeedThumb(cat) }
                CategoryCard(cat, explore.thumbs[cat.params]) { onCategoryClick(cat) }
            }
        }
    }
}

@Composable
private fun Center(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private val FALLBACK_COLORS = listOf(
    Color(0xFF4E8C5A), Color(0xFFA04C9C), Color(0xFFB5481F),
    Color(0xFF3F6FA8), Color(0xFF8A6D1E), Color(0xFF2F8F8F)
)

private fun cardColor(cat: YtMusicApi.ExploreCategory): Color =
    cat.color?.let { Color(((it and 0xFFFFFFL) or 0xFF000000L).toInt()) }
        ?: FALLBACK_COLORS[abs(cat.title.hashCode()) % FALLBACK_COLORS.size]

@Composable
private fun CategoryCard(cat: YtMusicApi.ExploreCategory, thumb: String?, onClick: () -> Unit) {
    val base = cardColor(cat)
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(base.copy(alpha = 0.85f), base)))
            .clickable(onClick = onClick)
    ) {
        Text(
            cat.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.55f)
                .padding(start = 16.dp, bottom = 14.dp)
        )
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(82.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}
