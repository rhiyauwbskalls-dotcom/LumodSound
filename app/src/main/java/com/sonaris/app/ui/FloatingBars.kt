package com.sonaris.app.ui

import androidx.compose.animation.AnimatedVisibility
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
            )
        }
    }
}
