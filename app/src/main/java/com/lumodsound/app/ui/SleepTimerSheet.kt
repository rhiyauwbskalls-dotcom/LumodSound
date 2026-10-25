package com.lumodsound.app.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumodsound.app.player.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lumodsound.app.R
import com.lumodsound.app.str

private val TimerOptionsMin = listOf(5, 15, 30, 45, 60, 90)

/** Hoja para programar el temporizador de sueño (o ver cuánto falta y cancelarlo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(state: SleepTimer.State, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun closeThen(action: () -> Unit) {
        action()
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    // Reloj para la cuenta atrás: solo corre mientras la hoja está abierta
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.endAtElapsedMs) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0B0B),
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                str(R.string.timer),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            when {
                state.endAtElapsedMs > 0L -> {
                    val remaining = (state.endAtElapsedMs - now).coerceAtLeast(0L)
                    ActiveRow(str(R.string.music_will_stop_in, formatCountdown(remaining))) {
                        closeThen { SleepTimer.cancel() }
                    }
                }
                state.untilSongEnd -> ActiveRow(str(R.string.music_will_stop_when_this)) {
                    closeThen { SleepTimer.cancel() }
                }
            }

            TimerOptionsMin.forEach { min ->
                OptionRow(
                    label = if (min >= 60 && min % 60 == 0) "${min / 60} h" else str(R.string.minutes, min),
                    selected = false
                ) { closeThen { SleepTimer.startMinutes(min) } }
            }
            OptionRow(
                label = str(R.string.when_the_song_ends),
                selected = state.untilSongEnd
            ) { closeThen { SleepTimer.startAtSongEnd() } }
        }
    }
}

@Composable
private fun ActiveRow(text: String, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onCancel) {
            Icon(Icons.Default.TimerOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(("  " + str(R.string.cancel)))
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = null)
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
