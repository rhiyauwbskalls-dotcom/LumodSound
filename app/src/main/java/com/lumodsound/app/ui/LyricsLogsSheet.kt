package com.lumodsound.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumodsound.app.R
import com.lumodsound.app.data.LyricsOutcome
import com.lumodsound.app.data.LyricsProviderStatus
import com.lumodsound.app.data.LyricsSource
import com.lumodsound.app.str

/**
 * Hoja de "logs" de la pantalla de letras: qué respondió cada proveedor en la última búsqueda y por qué falló.
 * Spotify solo sale si está conectado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsLogsSheet(showSpotify: Boolean, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val attempts = LyricsProviderStatus.attempts
    val sources = LyricsSource.values().filter { it != LyricsSource.SPOTIFY || showSpotify }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0B0B),
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            Text(
                str(R.string.lyrics_logs),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Text(
                str(R.string.lyrics_logs_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)
            )
            sources.forEach { source ->
                val a = attempts[source]
                val found = a?.outcome == LyricsOutcome.FOUND || a?.outcome == LyricsOutcome.FOUND_PLAIN
                val status = when (a?.outcome) {
                    null -> if (attempts.isEmpty()) str(R.string.lyr_status_idle) else str(R.string.lyr_status_skipped)
                    LyricsOutcome.FOUND -> str(R.string.lyr_status_found)
                    LyricsOutcome.FOUND_PLAIN -> str(R.string.lyr_status_found_plain)
                    LyricsOutcome.INSTRUMENTAL -> str(R.string.lyr_reason_instrumental)
                    LyricsOutcome.NOT_FOUND -> str(R.string.lyr_status_not_found, a?.reason ?: str(R.string.lyr_reason_unknown))
                    LyricsOutcome.ERROR -> str(R.string.lyr_status_error, a?.reason ?: str(R.string.lyr_reason_unknown))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (found) Icons.Default.CheckCircle else if (a == null) Icons.Default.Info else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (found) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(source.label, style = MaterialTheme.typography.titleMedium)
                        Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                        a?.let {
                            Text(
                                str(R.string.lyrics_last_lookup, it.song),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}
