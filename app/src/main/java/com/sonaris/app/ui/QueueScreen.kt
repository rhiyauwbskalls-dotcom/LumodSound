package com.sonaris.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.sonaris.app.QueueEntry
import com.sonaris.app.QueueState

private val QueueMuted = Color.White.copy(alpha = 0.65f)

/**
 * Cola de reproducción (estilo "A continuación" de Apple Music): fondo con el color de la portada,
 * botones redondos de aleatorio / repetir / radio infinita arriba, y filas limpias.
 * Toca una canción para saltar a ella, la X la quita y se reordena arrastrando el asa de la derecha.
 * La canción que suena lleva ondas animadas sobre la portada y lo ya escuchado se ve atenuado.
 */
@Composable
fun QueueScreen(
    queue: QueueState,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    autoRadio: Boolean,
    onToggleRadio: () -> Unit,
    shuffle: Boolean = false,
    repeat: Int = 0,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {}
) {
    // Copia local: durante el arrastre se reordena aquí y al soltar se confirma en el reproductor.
    val items = remember { mutableStateListOf<QueueEntry>().apply { addAll(queue.entries) } }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val latestQueue by rememberUpdatedState(queue)
    val latestOnMove by rememberUpdatedState(onMove)
    val currentId = queue.entries.getOrNull(queue.index)?.id

    LaunchedEffect(queue.entries) {
        if (dragIndex == null) {
            items.clear()
            items.addAll(queue.entries)
        }
    }
    LaunchedEffect(Unit) { listState.scrollToItem(maxOf(queue.index, 0)) }

    // Fondo con el color de la portada de la canción que suena (igual que el reproductor grande)
    val accent = rememberArtAccent(queue.entries.getOrNull(queue.index)?.song?.thumb)
    val bgTop = remember(accent) { tameForBackground(accent) }
    val currentPos = items.indexOfFirst { it.id == currentId }
    val hasUpcoming = queue.index in 0 until queue.entries.size - 1

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to bgTop, 0.7f to PlayerBase, 1f to PlayerBase))
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    "COLA DE REPRODUCCIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QueueMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(48.dp)) // equilibra el botón de cerrar para que el texto quede centrado
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "A continuación",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${items.size} canciones" + if (autoRadio) " · radio infinita" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = QueueMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QueueRoundButton(Icons.Default.Shuffle, "Aleatorio", shuffle, onShuffle)
                    QueueRoundButton(
                        if (repeat == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repetir",
                        repeat != 0,
                        onRepeat
                    )
                    QueueRoundButton(AppIcons.Radio, "Radio infinita", autoRadio, onToggleRadio)
                    if (hasUpcoming) {
                        QueueRoundButton(Icons.Default.Delete, "Vaciar siguientes", false, onClearUpcoming)
                    }
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("La cola está vacía", color = QueueMuted)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items, key = { _, e -> e.id }) { index, e ->
                        QueueRow(
                            entry = e,
                            isCurrent = e.id == currentId,
                            isPast = currentPos >= 0 && index < currentPos,
                            isPlaying = isPlaying,
                            isDragging = dragIndex == index,
                            dragOffset = dragOffset,
                            onClick = {
                                val i = items.indexOfFirst { it.id == e.id }
                                if (i >= 0) onPlayAt(i)
                            },
                            onRemove = {
                                val i = items.indexOfFirst { it.id == e.id }
                                if (i >= 0) {
                                    items.removeAt(i)
                                    onRemove(i)
                                }
                            },
                            handleModifier = Modifier.pointerInput(e.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        val i = items.indexOfFirst { it.id == e.id }
                                        dragFrom = i
                                        dragIndex = i
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val cur = dragIndex
                                        if (cur != null) {
                                            val visible = listState.layoutInfo.visibleItemsInfo
                                            val info = visible.firstOrNull { it.index == cur }
                                            if (info != null) {
                                                val center = info.offset + info.size / 2f + dragOffset
                                                val target = visible.firstOrNull {
                                                    it.index != cur &&
                                                        center >= it.offset &&
                                                        center <= it.offset + it.size
                                                }
                                                if (target != null) {
                                                    items.add(target.index, items.removeAt(cur))
                                                    dragOffset -= (target.offset - info.offset)
                                                    dragIndex = target.index
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val from = dragFrom
                                        val to = dragIndex
                                        dragIndex = null
                                        dragFrom = null
                                        dragOffset = 0f
                                        if (from != null && to != null && from != to) latestOnMove(from, to)
                                    },
                                    onDragCancel = {
                                        dragIndex = null
                                        dragFrom = null
                                        dragOffset = 0f
                                        items.clear()
                                        items.addAll(latestQueue.entries)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Botón redondo translúcido de la cabecera; encendido = relleno blanco con el icono oscuro. */
@Composable
private fun QueueRoundButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    isPast: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handleModifier: Modifier
) {
    val song = entry.song
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isDragging -> Color(0xFF2E2E2E)
            isCurrent -> Color.White.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffset else 0f
                val scale = if (isDragging) 1.02f else 1f
                scaleX = scale
                scaleY = scale
                // Lo que ya se escuchó se ve atenuado
                alpha = if (isPast && !isDragging) 0.5f else 1f
            }
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.08f))
                )
                if (isCurrent) {
                    // Ondas sobre la portada de la canción que suena (quietas si está en pausa)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) PlayingBars() else PausedBars()
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    song.title,
                    color = Color.White,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val status = if (isCurrent && !isPlaying) "En pausa · " else ""
                Text(
                    status + song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = QueueMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar de la cola",
                    tint = QueueMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(handleModifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(AppIcons.DragHandle, contentDescription = "Reordenar", tint = QueueMuted)
            }
        }
    }
}

/** Tres barras que suben y bajan (solo se anima mientras suena: en pausa no gasta batería). */
@Composable
private fun PlayingBars() {
    val transition = rememberInfiniteTransition(label = "queueBars")
    val a by transition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by transition.animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(430, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    val c by transition.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse), label = "c"
    )
    Bars(a, b, c)
}

@Composable
private fun PausedBars() = Bars(0.3f, 0.3f, 0.3f)

@Composable
private fun Bars(a: Float, b: Float, c: Float) {
    Row(
        Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(a, b, c).forEach { f ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }
    }
}
