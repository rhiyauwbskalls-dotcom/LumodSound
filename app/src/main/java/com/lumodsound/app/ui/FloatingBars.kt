package com.lumodsound.app.ui

import androidx.compose.animation.AnimatedVisibility
<<<<<<< HEAD
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Medidas compartidas de las barras flotantes (para calcular el espacio que dejan al contenido). */
internal val FloatingNavHeight = 64.dp
internal val FloatingGap = 8.dp
internal val FloatingMargin = 12.dp

/**
 * Barra de navegación flotante: píldora redondeada con un borde finito; la pestaña activa muestra su nombre
 * y su icono pasa de contorno a relleno.
 */
@Composable
fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val onBar = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // Liquid Glass (Ajustes, Android 12+): cristal esmerilado en lugar de la píldora opaca
    val glass = LocalGlassSource.current != null
    val shape = RoundedCornerShape(32.dp)
    // Pulsación líquida: toda la barra crece un poco y el brillo sigue al dedo (solo con cristal)
    val press = rememberGlassPress()
    Surface(
        shape = shape,
        color = if (glass) Color.Transparent else surface.copy(alpha = 0.96f),
        contentColor = onBar,
        shadowElevation = if (glass) 0.dp else 14.dp,
        border = if (glass) null else BorderStroke(0.5.dp, onBar.copy(alpha = 0.10f)),
        modifier = modifier
            .fillMaxWidth()
            .height(FloatingNavHeight)
            .then(
                if (glass) Modifier
                    .glassPress(press, pressedScale = 1.03f)
                    // Con la refracción el tinte es más ligero: así se ve lo de atrás desenfocado a través del cristal
                    .liquidGlass(shape, tint = surface.copy(alpha = if (LiquidGlassSettings.refractActive) 0.10f else 0.30f), press = press)
                else Modifier
            )
    ) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val curTab by rememberUpdatedState(tab)
        val curOnTab by rememberUpdatedState(onTab)
        // Posición y ancho de cada pestaña dentro de la fila (la activa es más ancha porque muestra su nombre)
        val starts = remember { mutableStateListOf(0f, 0f, 0f) }
        val widths = remember { mutableStateListOf(0f, 0f, 0f) }
        var rowW by remember { mutableFloatStateOf(0f) }
        // Mientras el dedo desliza, centro del dedo en la fila; null = la burbuja sigue a la pestaña activa
        var dragX by remember { mutableStateOf<Float?>(null) }
        val bubbleX = remember { Animatable(0f) }
        val bubbleW = remember { Animatable(0f) }
        var placed by remember { mutableStateOf(false) }

        LaunchedEffect(tab, widths[tab]) {
            val w = widths[tab]
            if (w > 0f) {
                if (bubbleW.value == 0f) bubbleW.snapTo(w)
                else bubbleW.animateTo(w, spring(dampingRatio = 0.8f, stiffness = 500f))
            }
        }
        LaunchedEffect(tab, starts[tab], dragX == null) {
            if (dragX == null && widths[tab] > 0f) {
                if (!placed) {
                    bubbleX.snapTo(starts[tab])
                    placed = true
                } else {
                    bubbleX.animateTo(starts[tab], spring(dampingRatio = 0.75f, stiffness = 400f))
                }
            }
        }
        // Al soltar, la burbuja sigue desde donde estaba el dedo hasta la pestaña elegida
        val finishDrag: () -> Unit = {
            val x = dragX
            if (x != null) {
                scope.launch {
                    val w = bubbleW.value
                    bubbleX.snapTo(x.coerceIn(w / 2f, (rowW - w / 2f).coerceAtLeast(w / 2f)) - w / 2f)
                    dragX = null
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .onSizeChanged { rowW = it.width.toFloat() }
        ) {
            if (glass && bubbleW.value > 0f) {
                // Una sola burbuja que se desliza entre pestañas (y sigue al dedo al arrastrar)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            val w = bubbleW.value
                            val d = dragX
                            val x = if (d != null) {
                                d.coerceIn(w / 2f, (rowW - w / 2f).coerceAtLeast(w / 2f)) - w / 2f
                            } else {
                                bubbleX.value
                            }
                            IntOffset(x.roundToInt(), 0)
                        }
                        .width(with(density) { bubbleW.value.toDp() })
                        .height(44.dp)
                        .glassBubble(onBar) { 1f }
                )
            }
            Row(
                Modifier
                    .fillMaxSize()
                    // Deslizar el dedo por la barra cambia de pestaña (los toques sueltos siguen funcionando)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> if (glass) dragX = o.x },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x
                                if (glass) dragX = x
                                val idx = ((x / rowW.coerceAtLeast(1f)) * 3f).toInt().coerceIn(0, 2)
                                if (idx != curTab) curOnTab(idx)
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavPill(tab == 0, Icons.Filled.Home, Icons.Outlined.Home, str(R.string.home), { x, w -> starts[0] = x; widths[0] = w }) { onTab(0) }
                NavPill(tab == 1, Icons.Filled.Search, Icons.Outlined.Search, str(R.string.search), { x, w -> starts[1] = x; widths[1] = w }) { onTab(1) }
                NavPill(tab == 2, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, str(R.string.library), { x, w -> starts[2] = x; widths[2] = w }) { onTab(2) }
            }
        }
    }
}

@Composable
private fun NavPill(
    selected: Boolean,
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
    label: String,
    onBounds: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    val onBar = MaterialTheme.colorScheme.onSurface
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Al pulsar se encoge un poco y vuelve con un rebote suave
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navPillScale"
    )
    val bg by animateColorAsState(
        if (selected) onBar.copy(alpha = 0.16f) else Color.Transparent,
        tween(220),
        label = "navPill"
    )
    val tint by animateColorAsState(
        if (selected) onBar else onBar.copy(alpha = 0.62f),
        tween(220),
        label = "navPillTint"
    )
    // Con cristal, la pestaña activa es una burbuja de cristal con su propio borde de luz (como en SimpMusic)
    val glass = LocalGlassSource.current != null
    Row(
        Modifier
            .onGloballyPositioned { onBounds(it.positionInParent().x, it.size.width.toFloat()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (glass) Color.Transparent else bg)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab
            ) {
                // Vibración corta solo al cambiar de pestaña, no al tocar la que ya está abierta
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) filledIcon else outlinedIcon,
            // Con el nombre a la vista el icono no necesita descripción propia (evita que se lea dos veces)
            contentDescription = if (selected) null else label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120))
        ) {
            Text(
                label,
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Burbuja de la pestaña activa dibujada a mano: relleno translúcido con degradado y borde de luz.
 * No usa un segundo cristal dentro de la barra (un backdrop dentro de otro es lo que fallaba); el desenfoque
 * y la refracción los pone la barra que tiene detrás.
 */
private fun Modifier.glassBubble(color: Color, alpha: () -> Float): Modifier = this.drawWithCache {
    val stroke = 1.2.dp.toPx()
    val fill = Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0.08f)))
    val rim = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.32f)),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    onDrawBehind {
        val a = alpha()
        if (a > 0.001f) {
            val r = size.height / 2f
            drawRoundRect(fill, cornerRadius = CornerRadius(r, r), alpha = a)
            drawRoundRect(
                rim,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(r - stroke / 2f, r - stroke / 2f),
                style = Stroke(stroke),
                alpha = a
=======
<<<<<<< HEAD
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Medidas compartidas de las barras flotantes (para calcular el espacio que dejan al contenido). */
internal val FloatingNavHeight = 64.dp
internal val FloatingGap = 8.dp
internal val FloatingMargin = 12.dp

/**
 * Barra de navegación flotante: píldora redondeada con un borde finito; la pestaña activa muestra su nombre
 * y su icono pasa de contorno a relleno.
 */
@Composable
fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val onBar = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // Liquid Glass (Ajustes, Android 12+): cristal esmerilado en lugar de la píldora opaca
    val glass = LocalGlassSource.current != null
    val shape = RoundedCornerShape(32.dp)
    // Pulsación líquida: toda la barra crece un poco y el brillo sigue al dedo (solo con cristal)
    val press = rememberGlassPress()
    Surface(
        shape = shape,
        color = if (glass) Color.Transparent else surface.copy(alpha = 0.96f),
        contentColor = onBar,
        shadowElevation = if (glass) 0.dp else 14.dp,
        border = if (glass) null else BorderStroke(0.5.dp, onBar.copy(alpha = 0.10f)),
        modifier = modifier
            .fillMaxWidth()
            .height(FloatingNavHeight)
            .then(
                if (glass) Modifier
                    .glassPress(press, pressedScale = 1.03f)
                    // Con la refracción el tinte es más ligero: así se ve lo de atrás desenfocado a través del cristal
                    .liquidGlass(shape, tint = surface.copy(alpha = if (LiquidGlassSettings.refractActive) 0.10f else 0.30f), press = press)
                else Modifier
            )
    ) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val curTab by rememberUpdatedState(tab)
        val curOnTab by rememberUpdatedState(onTab)
        // Posición y ancho de cada pestaña dentro de la fila (la activa es más ancha porque muestra su nombre)
        val starts = remember { mutableStateListOf(0f, 0f, 0f) }
        val widths = remember { mutableStateListOf(0f, 0f, 0f) }
        var rowW by remember { mutableFloatStateOf(0f) }
        // Mientras el dedo desliza, centro del dedo en la fila; null = la burbuja sigue a la pestaña activa
        var dragX by remember { mutableStateOf<Float?>(null) }
        val bubbleX = remember { Animatable(0f) }
        val bubbleW = remember { Animatable(0f) }
        var placed by remember { mutableStateOf(false) }

        LaunchedEffect(tab, widths[tab]) {
            val w = widths[tab]
            if (w > 0f) {
                if (bubbleW.value == 0f) bubbleW.snapTo(w)
                else bubbleW.animateTo(w, spring(dampingRatio = 0.8f, stiffness = 500f))
            }
        }
        LaunchedEffect(tab, starts[tab], dragX == null) {
            if (dragX == null && widths[tab] > 0f) {
                if (!placed) {
                    bubbleX.snapTo(starts[tab])
                    placed = true
                } else {
                    bubbleX.animateTo(starts[tab], spring(dampingRatio = 0.75f, stiffness = 400f))
                }
            }
        }
        // Al soltar, la burbuja sigue desde donde estaba el dedo hasta la pestaña elegida
        val finishDrag: () -> Unit = {
            val x = dragX
            if (x != null) {
                scope.launch {
                    val w = bubbleW.value
                    bubbleX.snapTo(x.coerceIn(w / 2f, (rowW - w / 2f).coerceAtLeast(w / 2f)) - w / 2f)
                    dragX = null
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .onSizeChanged { rowW = it.width.toFloat() }
        ) {
            if (glass && bubbleW.value > 0f) {
                // Una sola burbuja que se desliza entre pestañas (y sigue al dedo al arrastrar)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            val w = bubbleW.value
                            val d = dragX
                            val x = if (d != null) {
                                d.coerceIn(w / 2f, (rowW - w / 2f).coerceAtLeast(w / 2f)) - w / 2f
                            } else {
                                bubbleX.value
                            }
                            IntOffset(x.roundToInt(), 0)
                        }
                        .width(with(density) { bubbleW.value.toDp() })
                        .height(44.dp)
                        .glassBubble(onBar) { 1f }
                )
            }
            Row(
                Modifier
                    .fillMaxSize()
                    // Deslizar el dedo por la barra cambia de pestaña (los toques sueltos siguen funcionando)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> if (glass) dragX = o.x },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x
                                if (glass) dragX = x
                                val idx = ((x / rowW.coerceAtLeast(1f)) * 3f).toInt().coerceIn(0, 2)
                                if (idx != curTab) curOnTab(idx)
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavPill(tab == 0, Icons.Filled.Home, Icons.Outlined.Home, str(R.string.home), { x, w -> starts[0] = x; widths[0] = w }) { onTab(0) }
                NavPill(tab == 1, Icons.Filled.Search, Icons.Outlined.Search, str(R.string.search), { x, w -> starts[1] = x; widths[1] = w }) { onTab(1) }
                NavPill(tab == 2, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, str(R.string.library), { x, w -> starts[2] = x; widths[2] = w }) { onTab(2) }
            }
        }
    }
}

@Composable
private fun NavPill(
    selected: Boolean,
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
    label: String,
    onBounds: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    val onBar = MaterialTheme.colorScheme.onSurface
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Al pulsar se encoge un poco y vuelve con un rebote suave
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navPillScale"
    )
    val bg by animateColorAsState(
        if (selected) onBar.copy(alpha = 0.16f) else Color.Transparent,
        tween(220),
        label = "navPill"
    )
    val tint by animateColorAsState(
        if (selected) onBar else onBar.copy(alpha = 0.62f),
        tween(220),
        label = "navPillTint"
    )
    // Con cristal, la pestaña activa es una burbuja de cristal con su propio borde de luz (como en SimpMusic)
    val glass = LocalGlassSource.current != null
    Row(
        Modifier
            .onGloballyPositioned { onBounds(it.positionInParent().x, it.size.width.toFloat()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (glass) Color.Transparent else bg)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab
            ) {
                // Vibración corta solo al cambiar de pestaña, no al tocar la que ya está abierta
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) filledIcon else outlinedIcon,
            // Con el nombre a la vista el icono no necesita descripción propia (evita que se lea dos veces)
            contentDescription = if (selected) null else label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120))
        ) {
            Text(
                label,
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Burbuja de la pestaña activa dibujada a mano: relleno translúcido con degradado y borde de luz.
 * No usa un segundo cristal dentro de la barra (un backdrop dentro de otro es lo que fallaba); el desenfoque
 * y la refracción los pone la barra que tiene detrás.
 */
private fun Modifier.glassBubble(color: Color, alpha: () -> Float): Modifier = this.drawWithCache {
    val stroke = 1.2.dp.toPx()
    val fill = Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0.08f)))
    val rim = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.32f)),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    onDrawBehind {
        val a = alpha()
        if (a > 0.001f) {
            val r = size.height / 2f
            drawRoundRect(fill, cornerRadius = CornerRadius(r, r), alpha = a)
            drawRoundRect(
                rim,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(r - stroke / 2f, r - stroke / 2f),
                style = Stroke(stroke),
                alpha = a
=======
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

/** Medidas compartidas de las barras flotantes (para calcular el espacio que dejan al contenido). */
internal val FloatingNavHeight = 64.dp
internal val FloatingGap = 8.dp
internal val FloatingMargin = 12.dp

/** Barra de navegación flotante: píldora redondeada; la pestaña activa muestra su nombre. */
@Composable
fun FloatingNavBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(FloatingNavHeight)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavPill(tab == 0, Icons.Default.Home, "Inicio") { onTab(0) }
            NavPill(tab == 1, Icons.Default.Search, "Buscar") { onTab(1) }
            NavPill(tab == 2, Icons.Default.LibraryMusic, "Biblioteca") { onTab(2) }
        }
    }
}

@Composable
private fun NavPill(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val onBar = MaterialTheme.colorScheme.onSurface
    val bg by animateColorAsState(
        if (selected) onBar.copy(alpha = 0.14f) else Color.Transparent,
        label = "navPill"
    )
    val tint = if (selected) onBar else onBar.copy(alpha = 0.6f)
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Text(
                label,
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
            )
        }
    }
}
