package com.sonaris.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
<<<<<<< HEAD
import com.sonaris.app.player.CanvasCache
import com.sonaris.app.player.Crossfade
import com.sonaris.app.data.LyricsCache
import com.sonaris.app.player.Downloads
import com.sonaris.app.player.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonaris.app.data.DiscordSettings
import com.sonaris.app.data.LastfmSettings
import com.sonaris.app.data.SpotifySettings
import com.sonaris.app.data.TranslateEngine
import com.sonaris.app.data.TranslationSettings
import com.sonaris.app.data.HistorySync

/** Ajustes generales de la app, agrupados por secciones. Aquí se van sumando las opciones nuevas. */
@Composable
fun SettingsScreen(
    loggedIn: Boolean,
    discord: DiscordSettings,
    lastfm: LastfmSettings,
    spotify: SpotifySettings,
    translation: TranslationSettings,
    onAccount: () -> Unit,
    onDiscord: () -> Unit,
    onLastfm: () -> Unit,
    onSpotify: () -> Unit,
    onTranslation: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember(context) { PlayerSettings.load(context) }

    // Caché de canciones: uso actual, límite elegido y los diálogos de ajuste / borrado
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var cacheMaxMb by remember { mutableIntStateOf(MediaCache.maxMb(context)) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearDownloads by remember { mutableStateOf(false) }
    var showClearLyrics by remember { mutableStateOf(false) }
    var showClearCanvas by remember { mutableStateOf(false) }
    var historySync by remember { mutableStateOf(HistorySync.isEnabled(context)) }
    var crossfadeOn by remember { mutableStateOf(Crossfade.isEnabled(context)) }
    var crossfadeSec by remember { mutableIntStateOf(Crossfade.seconds(context)) }
    val cacheUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                MediaCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    val downloadsUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                Downloads.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    val lyricsUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                LyricsCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }
    val canvasUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                CanvasCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }
    // La tarjeta de Canvas solo sale si hay sesión de Spotify iniciada o ya hay algo guardado
    val canvasHasData by produceState(false, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                CanvasCache.hasData(context)
            } catch (e: Exception) {
                false
            }
        }
    }

    SettingsScaffold(title = "Ajustes", onBack = onBack) {
        SettingsGroup("Cuenta") {
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "YouTube Music",
                value = if (loggedIn) "Conectada" else "Sin conectar",
                onClick = onAccount,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Integraciones") {
            SettingsItem(
                icon = Icons.Default.Headphones,
                title = "Discord",
                value = when {
                    !discord.connected -> "Sin conectar"
                    discord.enabled -> discord.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onDiscord,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Last.fm",
                value = when {
                    !lastfm.connected -> "Sin conectar"
                    lastfm.enabled -> lastfm.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onLastfm,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Album,
                title = "Spotify",
                value = when {
                    !spotify.connected -> "Sin conectar"
                    spotify.lyrics || spotify.canvas -> "Conectado"
                    else -> "Desactivado"
                },
                onClick = onSpotify,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Apariencia") {
            SettingsSwitchItem(
                icon = Icons.Default.Palette,
                title = "Colores del sistema (Material You)",
                checked = ThemeSettings.dynamicColor,
                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                onChange = { ThemeSettings.setDynamicColor(context, it) }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.BlurOn,
                title = "Fondo difuminado en el reproductor",
                checked = PlayerSettings.blurBackground,
                onChange = { PlayerSettings.setBlurBackground(context, it) }
            )
        }
        SettingsGroup("Reproducción") {
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Crossfade",
                value = if (crossfadeOn) "$crossfadeSec s" else "Desactivado",
                onClick = {
                    crossfadeOn = !crossfadeOn
                    Crossfade.setEnabled(context, crossfadeOn)
                },
                trailing = { Switch(checked = crossfadeOn, onCheckedChange = null) }
            )
            if (crossfadeOn) {
                GroupDivider()
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Duración del cruce: $crossfadeSec s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = crossfadeSec.toFloat(),
                        onValueChange = { crossfadeSec = it.roundToInt() },
                        onValueChangeFinished = { Crossfade.setSeconds(context, crossfadeSec) },
                        valueRange = Crossfade.MIN_SEC.toFloat()..Crossfade.MAX_SEC.toFloat(),
                        steps = Crossfade.MAX_SEC - Crossfade.MIN_SEC - 1
                    )
                }
            }
        }
        SettingsGroup("Letras") {
            SettingsItem(
                icon = Icons.Default.Translate,
                title = "Letras traducción",
                value = if (translation.engine == TranslateEngine.AI) translation.provider.label else "ML Kit",
                onClick = onTranslation,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Historial") {
            SettingsItem(
                icon = Icons.Default.History,
                title = "Enviar historial a YouTube Music",
                value = if (historySync && loggedIn) HistorySync.lastResult(context)?.let { "Último envío: $it" } else null,
                enabled = loggedIn,
                onClick = {
                    historySync = !historySync
                    HistorySync.setEnabled(context, historySync)
                },
                trailing = { Switch(checked = historySync && loggedIn, onCheckedChange = null, enabled = loggedIn) }
            )
        }
        SettingsGroup("Almacenamiento") {
            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Caché de canciones",
                value = "${formatBytes(cacheUsed)} / ${cacheLimitLabel(cacheMaxMb)}",
                onClick = { showSizeDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Borrar caché de canciones",
                onClick = { showClearDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Caché de letras",
                value = formatBytes(lyricsUsed),
                onClick = { showClearLyrics = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            if (spotify.connected || canvasHasData) {
                SettingsItem(
                    icon = Icons.Default.Videocam,
                    title = "Caché de canvas",
                    value = formatBytes(canvasUsed),
                    onClick = { showClearCanvas = true },
                    trailing = { ChevronIcon() }
                )
                GroupDivider()
            }
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Borrar descargas",
                value = formatBytes(downloadsUsed),
                onClick = { showClearDownloads = true },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Batería") {
            SettingsItem(
                icon = Icons.Default.BatteryAlert,
                title = "Uso de batería de la app",
                onClick = { openAppBatterySettings(context) },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Acerca de") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Acerca de Sonaris",
                onClick = onAbout,
                trailing = { ChevronIcon() }
            )
        }
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            title = { Text("Tamaño de la caché") },
            text = {
                androidx.compose.foundation.layout.Column {
                    MediaCache.SIZE_OPTIONS_MB.forEach { mb ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cacheMaxMb = mb
                                    MediaCache.setMaxMb(context, mb)
                                    cacheRefresh++
                                    showSizeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cacheMaxMb == mb, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(cacheLimitLabel(mb))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSizeDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showClearDownloads) {
        AlertDialog(
            onDismissRequest = { showClearDownloads = false },
            title = { Text("¿Borrar las descargas?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(downloadsUsed)} de canciones descargadas. " +
                        "Tendrás que descargarlas de nuevo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDownloads = false
                    Downloads.removeAll(context)
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloads = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearLyrics) {
        AlertDialog(
            onDismissRequest = { showClearLyrics = false },
            title = { Text("¿Borrar la caché de letras?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(lyricsUsed)} de letras guardadas. " +
                        "Volverán a descargarse cuando las abras."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearLyrics = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                LyricsCache.clear(context)
                            } catch (e: Exception) {
                                // sin acceso a la caché: no hay nada que borrar
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLyrics = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearCanvas) {
        AlertDialog(
            onDismissRequest = { showClearCanvas = false },
            title = { Text("¿Borrar la caché de canvas?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(canvasUsed)} de videos guardados. " +
                        "Volverán a descargarse cuando los veas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCanvas = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                CanvasCache.clear(context)
                            } catch (e: Exception) {
                                // un archivo en uso: se queda y el límite lo recogerá más adelante
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCanvas = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("¿Borrar la caché?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(cacheUsed)} de canciones guardadas. " +
                        "Volverán a descargarse cuando las escuches."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                MediaCache.clear(context)
                            } catch (e: Exception) {
                                // sin acceso a la caché: no hay nada que borrar
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Abre la ficha de la app en los ajustes del sistema (ahí está "Batería"); Android no deja cambiarlo desde la app. */
private fun openAppBatterySettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // sin pantalla de ajustes disponible: no hay nada más que hacer
        }
    }
}

private fun cacheLimitLabel(mb: Int): String = when {
    mb == MediaCache.UNLIMITED -> "Sin límite"
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / 1024} KB"
=======
<<<<<<< HEAD
import com.sonaris.app.player.CanvasCache
import com.sonaris.app.player.Crossfade
import com.sonaris.app.data.LyricsCache
import com.sonaris.app.player.Downloads
import com.sonaris.app.player.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonaris.app.data.DiscordSettings
import com.sonaris.app.data.LastfmSettings
import com.sonaris.app.data.SpotifySettings
import com.sonaris.app.data.TranslateEngine
import com.sonaris.app.data.TranslationSettings
import com.sonaris.app.data.HistorySync

/** Ajustes generales de la app, agrupados por secciones. Aquí se van sumando las opciones nuevas. */
@Composable
fun SettingsScreen(
    loggedIn: Boolean,
    discord: DiscordSettings,
    lastfm: LastfmSettings,
    spotify: SpotifySettings,
    translation: TranslationSettings,
    onAccount: () -> Unit,
    onDiscord: () -> Unit,
    onLastfm: () -> Unit,
    onSpotify: () -> Unit,
    onTranslation: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Caché de canciones: uso actual, límite elegido y los diálogos de ajuste / borrado
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var cacheMaxMb by remember { mutableIntStateOf(MediaCache.maxMb(context)) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearDownloads by remember { mutableStateOf(false) }
    var showClearLyrics by remember { mutableStateOf(false) }
    var showClearCanvas by remember { mutableStateOf(false) }
    var historySync by remember { mutableStateOf(HistorySync.isEnabled(context)) }
    var crossfadeOn by remember { mutableStateOf(Crossfade.isEnabled(context)) }
    var crossfadeSec by remember { mutableIntStateOf(Crossfade.seconds(context)) }
    val cacheUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                MediaCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    val downloadsUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                Downloads.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    val lyricsUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                LyricsCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }
    val canvasUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                CanvasCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }
    // La tarjeta de Canvas solo sale si hay sesión de Spotify iniciada o ya hay algo guardado
    val canvasHasData by produceState(false, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                CanvasCache.hasData(context)
            } catch (e: Exception) {
                false
            }
        }
    }

    SettingsScaffold(title = "Ajustes", onBack = onBack) {
        SettingsGroup("Cuenta") {
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "YouTube Music",
                value = if (loggedIn) "Conectada" else "Sin conectar",
                onClick = onAccount,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Integraciones") {
            SettingsItem(
                icon = Icons.Default.Headphones,
                title = "Discord",
                value = when {
                    !discord.connected -> "Sin conectar"
                    discord.enabled -> discord.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onDiscord,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Last.fm",
                value = when {
                    !lastfm.connected -> "Sin conectar"
                    lastfm.enabled -> lastfm.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onLastfm,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Album,
                title = "Spotify",
                value = when {
                    !spotify.connected -> "Sin conectar"
                    spotify.lyrics || spotify.canvas -> "Conectado"
                    else -> "Desactivado"
                },
                onClick = onSpotify,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Apariencia") {
            SettingsSwitchItem(
                icon = Icons.Default.Palette,
                title = "Colores del sistema (Material You)",
                checked = ThemeSettings.dynamicColor,
                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                onChange = { ThemeSettings.setDynamicColor(context, it) }
            )
        }
        SettingsGroup("Reproducción") {
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Crossfade",
                value = if (crossfadeOn) "$crossfadeSec s" else "Desactivado",
                onClick = {
                    crossfadeOn = !crossfadeOn
                    Crossfade.setEnabled(context, crossfadeOn)
                },
                trailing = { Switch(checked = crossfadeOn, onCheckedChange = null) }
            )
            if (crossfadeOn) {
                GroupDivider()
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Duración del cruce: $crossfadeSec s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = crossfadeSec.toFloat(),
                        onValueChange = { crossfadeSec = it.roundToInt() },
                        onValueChangeFinished = { Crossfade.setSeconds(context, crossfadeSec) },
                        valueRange = Crossfade.MIN_SEC.toFloat()..Crossfade.MAX_SEC.toFloat(),
                        steps = Crossfade.MAX_SEC - Crossfade.MIN_SEC - 1
                    )
                }
            }
        }
        SettingsGroup("Letras") {
            SettingsItem(
                icon = Icons.Default.Translate,
                title = "Letras traducción",
                value = if (translation.engine == TranslateEngine.AI) translation.provider.label else "ML Kit",
                onClick = onTranslation,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Historial") {
            SettingsItem(
                icon = Icons.Default.History,
                title = "Enviar historial a YouTube Music",
                value = if (historySync && loggedIn) HistorySync.lastResult(context)?.let { "Último envío: $it" } else null,
                enabled = loggedIn,
                onClick = {
                    historySync = !historySync
                    HistorySync.setEnabled(context, historySync)
                },
                trailing = { Switch(checked = historySync && loggedIn, onCheckedChange = null, enabled = loggedIn) }
            )
        }
        SettingsGroup("Almacenamiento") {
            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Caché de canciones",
                value = "${formatBytes(cacheUsed)} / ${cacheLimitLabel(cacheMaxMb)}",
                onClick = { showSizeDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Borrar caché de canciones",
                onClick = { showClearDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Caché de letras",
                value = formatBytes(lyricsUsed),
                onClick = { showClearLyrics = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            if (spotify.connected || canvasHasData) {
                SettingsItem(
                    icon = Icons.Default.Videocam,
                    title = "Caché de canvas",
                    value = formatBytes(canvasUsed),
                    onClick = { showClearCanvas = true },
                    trailing = { ChevronIcon() }
                )
                GroupDivider()
            }
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Borrar descargas",
                value = formatBytes(downloadsUsed),
                onClick = { showClearDownloads = true },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Batería") {
            SettingsItem(
                icon = Icons.Default.BatteryAlert,
                title = "Uso de batería de la app",
                onClick = { openAppBatterySettings(context) },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Acerca de") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Acerca de Sonaris",
                onClick = onAbout,
                trailing = { ChevronIcon() }
            )
        }
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            title = { Text("Tamaño de la caché") },
            text = {
                androidx.compose.foundation.layout.Column {
                    MediaCache.SIZE_OPTIONS_MB.forEach { mb ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cacheMaxMb = mb
                                    MediaCache.setMaxMb(context, mb)
                                    cacheRefresh++
                                    showSizeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cacheMaxMb == mb, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(cacheLimitLabel(mb))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSizeDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showClearDownloads) {
        AlertDialog(
            onDismissRequest = { showClearDownloads = false },
            title = { Text("¿Borrar las descargas?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(downloadsUsed)} de canciones descargadas. " +
                        "Tendrás que descargarlas de nuevo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDownloads = false
                    Downloads.removeAll(context)
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloads = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearLyrics) {
        AlertDialog(
            onDismissRequest = { showClearLyrics = false },
            title = { Text("¿Borrar la caché de letras?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(lyricsUsed)} de letras guardadas. " +
                        "Volverán a descargarse cuando las abras."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearLyrics = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                LyricsCache.clear(context)
                            } catch (e: Exception) {
                                // sin acceso a la caché: no hay nada que borrar
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLyrics = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearCanvas) {
        AlertDialog(
            onDismissRequest = { showClearCanvas = false },
            title = { Text("¿Borrar la caché de canvas?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(canvasUsed)} de videos guardados. " +
                        "Volverán a descargarse cuando los veas."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCanvas = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                CanvasCache.clear(context)
                            } catch (e: Exception) {
                                // un archivo en uso: se queda y el límite lo recogerá más adelante
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCanvas = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("¿Borrar la caché?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(cacheUsed)} de canciones guardadas. " +
                        "Volverán a descargarse cuando las escuches."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                MediaCache.clear(context)
                            } catch (e: Exception) {
                                // sin acceso a la caché: no hay nada que borrar
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Abre la ficha de la app en los ajustes del sistema (ahí está "Batería"); Android no deja cambiarlo desde la app. */
private fun openAppBatterySettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // sin pantalla de ajustes disponible: no hay nada más que hacer
        }
    }
}

private fun cacheLimitLabel(mb: Int): String = when {
    mb == MediaCache.UNLIMITED -> "Sin límite"
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / 1024} KB"
=======
import com.sonaris.app.player.Crossfade
import com.sonaris.app.player.Downloads
import com.sonaris.app.player.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
<<<<<<< HEAD
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonaris.app.data.DiscordSettings
import com.sonaris.app.data.LastfmSettings
import com.sonaris.app.data.SpotifySettings
import com.sonaris.app.data.TranslateEngine
import com.sonaris.app.data.TranslationSettings
import com.sonaris.app.data.HistorySync

/** Ajustes generales de la app, agrupados por secciones. Aquí se van sumando las opciones nuevas. */
@Composable
fun SettingsScreen(
    loggedIn: Boolean,
    discord: DiscordSettings,
    lastfm: LastfmSettings,
    spotify: SpotifySettings,
    translation: TranslationSettings,
    onAccount: () -> Unit,
    onDiscord: () -> Unit,
    onLastfm: () -> Unit,
    onSpotify: () -> Unit,
    onTranslation: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Caché de canciones: uso actual, límite elegido y los diálogos de ajuste / borrado
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var cacheMaxMb by remember { mutableIntStateOf(MediaCache.maxMb(context)) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearDownloads by remember { mutableStateOf(false) }
    var historySync by remember { mutableStateOf(HistorySync.isEnabled(context)) }
    var crossfadeOn by remember { mutableStateOf(Crossfade.isEnabled(context)) }
    var crossfadeSec by remember { mutableIntStateOf(Crossfade.seconds(context)) }
    val cacheUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                MediaCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    val downloadsUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                Downloads.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    SettingsScaffold(title = "Ajustes", onBack = onBack) {
        SettingsGroup("Cuenta") {
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "YouTube Music",
                value = if (loggedIn) "Conectada" else "Sin conectar",
                onClick = onAccount,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Integraciones") {
            SettingsItem(
                icon = Icons.Default.Headphones,
                title = "Discord",
                value = when {
                    !discord.connected -> "Sin conectar"
                    discord.enabled -> discord.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onDiscord,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Last.fm",
                value = when {
                    !lastfm.connected -> "Sin conectar"
                    lastfm.enabled -> lastfm.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onLastfm,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Album,
                title = "Spotify",
                value = when {
                    !spotify.connected -> "Sin conectar"
                    spotify.lyrics || spotify.canvas -> "Conectado"
                    else -> "Desactivado"
                },
                onClick = onSpotify,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Apariencia") {
            SettingsSwitchItem(
                icon = Icons.Default.Palette,
                title = "Colores del sistema (Material You)",
                checked = ThemeSettings.dynamicColor,
                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                onChange = { ThemeSettings.setDynamicColor(context, it) }
            )
        }
        SettingsGroup("Reproducción") {
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Crossfade",
                value = if (crossfadeOn) "$crossfadeSec s" else "Desactivado",
                onClick = {
                    crossfadeOn = !crossfadeOn
                    Crossfade.setEnabled(context, crossfadeOn)
                },
                trailing = { Switch(checked = crossfadeOn, onCheckedChange = null) }
            )
            if (crossfadeOn) {
                GroupDivider()
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Duración del cruce: $crossfadeSec s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = crossfadeSec.toFloat(),
                        onValueChange = { crossfadeSec = it.roundToInt() },
                        onValueChangeFinished = { Crossfade.setSeconds(context, crossfadeSec) },
                        valueRange = Crossfade.MIN_SEC.toFloat()..Crossfade.MAX_SEC.toFloat(),
                        steps = Crossfade.MAX_SEC - Crossfade.MIN_SEC - 1
                    )
                }
            }
        }
        SettingsGroup("Letras") {
            SettingsItem(
                icon = Icons.Default.Translate,
                title = "Letras traducción",
                value = if (translation.engine == TranslateEngine.AI) translation.provider.label else "ML Kit",
                onClick = onTranslation,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Historial") {
            SettingsItem(
                icon = Icons.Default.History,
                title = "Enviar historial a YouTube Music",
                value = if (historySync && loggedIn) HistorySync.lastResult(context)?.let { "Último envío: $it" } else null,
                enabled = loggedIn,
                onClick = {
                    historySync = !historySync
                    HistorySync.setEnabled(context, historySync)
                },
                trailing = { Switch(checked = historySync && loggedIn, onCheckedChange = null, enabled = loggedIn) }
            )
        }
        SettingsGroup("Almacenamiento") {
            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Caché de canciones",
                value = "${formatBytes(cacheUsed)} / ${cacheLimitLabel(cacheMaxMb)}",
                onClick = { showSizeDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Borrar caché de canciones",
                onClick = { showClearDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Borrar descargas",
                value = formatBytes(downloadsUsed),
                onClick = { showClearDownloads = true },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Batería") {
            SettingsItem(
                icon = Icons.Default.BatteryAlert,
                title = "Uso de batería de la app",
                onClick = { openAppBatterySettings(context) },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Acerca de") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Acerca de Sonaris",
                onClick = onAbout,
=======
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonaris.app.data.DiscordSettings
import com.sonaris.app.data.LastfmSettings
import com.sonaris.app.data.SpotifySettings
import com.sonaris.app.data.TranslateEngine
import com.sonaris.app.data.TranslationSettings
import com.sonaris.app.data.HistorySync

/** Ajustes generales de la app, agrupados por secciones. Aquí se van sumando las opciones nuevas. */
@Composable
fun SettingsScreen(
    loggedIn: Boolean,
    discord: DiscordSettings,
    lastfm: LastfmSettings,
    spotify: SpotifySettings,
    translation: TranslationSettings,
    onAccount: () -> Unit,
    onDiscord: () -> Unit,
    onLastfm: () -> Unit,
    onSpotify: () -> Unit,
    onTranslation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Caché de canciones: uso actual, límite elegido y los diálogos de ajuste / borrado
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var cacheMaxMb by remember { mutableIntStateOf(MediaCache.maxMb(context)) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearDownloads by remember { mutableStateOf(false) }
    var historySync by remember { mutableStateOf(HistorySync.isEnabled(context)) }
    var crossfadeOn by remember { mutableStateOf(Crossfade.isEnabled(context)) }
    var crossfadeSec by remember { mutableIntStateOf(Crossfade.seconds(context)) }
    val cacheUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                MediaCache.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    val downloadsUsed by produceState(0L, cacheRefresh) {
        value = withContext(Dispatchers.IO) {
            try {
                Downloads.usedBytes(context)
            } catch (e: Exception) {
                0L
            }
        }
    }

    SettingsScaffold(title = "Ajustes", onBack = onBack) {
        SettingsGroup("Cuenta") {
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "YouTube Music",
                value = if (loggedIn) "Conectada" else "Sin conectar",
                onClick = onAccount,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Integraciones") {
            SettingsItem(
                icon = Icons.Default.Headphones,
                title = "Discord",
                value = when {
                    !discord.connected -> "Sin conectar"
                    discord.enabled -> discord.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onDiscord,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Last.fm",
                value = when {
                    !lastfm.connected -> "Sin conectar"
                    lastfm.enabled -> lastfm.username ?: "Activado"
                    else -> "Desactivado"
                },
                onClick = onLastfm,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Album,
                title = "Spotify",
                value = when {
                    !spotify.connected -> "Sin conectar"
                    spotify.lyrics || spotify.canvas -> "Conectado"
                    else -> "Desactivado"
                },
                onClick = onSpotify,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Apariencia") {
            SettingsSwitchItem(
                icon = Icons.Default.Palette,
                title = "Colores del sistema (Material You)",
                checked = ThemeSettings.dynamicColor,
                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                onChange = { ThemeSettings.setDynamicColor(context, it) }
            )
        }
        SettingsGroup("Reproducción") {
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Crossfade",
                value = if (crossfadeOn) "$crossfadeSec s" else "Desactivado",
                onClick = {
                    crossfadeOn = !crossfadeOn
                    Crossfade.setEnabled(context, crossfadeOn)
                },
                trailing = { Switch(checked = crossfadeOn, onCheckedChange = null) }
            )
            if (crossfadeOn) {
                GroupDivider()
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Duración del cruce: $crossfadeSec s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = crossfadeSec.toFloat(),
                        onValueChange = { crossfadeSec = it.roundToInt() },
                        onValueChangeFinished = { Crossfade.setSeconds(context, crossfadeSec) },
                        valueRange = Crossfade.MIN_SEC.toFloat()..Crossfade.MAX_SEC.toFloat(),
                        steps = Crossfade.MAX_SEC - Crossfade.MIN_SEC - 1
                    )
                }
            }
        }
        SettingsGroup("Letras") {
            SettingsItem(
                icon = Icons.Default.Translate,
                title = "Letras traducción",
                value = if (translation.engine == TranslateEngine.AI) translation.provider.label else "ML Kit",
                onClick = onTranslation,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Historial") {
            SettingsItem(
                icon = Icons.Default.History,
                title = "Enviar historial a YouTube Music",
                value = if (historySync && loggedIn) HistorySync.lastResult(context)?.let { "Último envío: $it" } else null,
                enabled = loggedIn,
                onClick = {
                    historySync = !historySync
                    HistorySync.setEnabled(context, historySync)
                },
                trailing = { Switch(checked = historySync && loggedIn, onCheckedChange = null, enabled = loggedIn) }
            )
        }
        SettingsGroup("Almacenamiento") {
            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Caché de canciones",
                value = "${formatBytes(cacheUsed)} / ${cacheLimitLabel(cacheMaxMb)}",
                onClick = { showSizeDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = "Borrar caché de canciones",
                onClick = { showClearDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Borrar descargas",
                value = formatBytes(downloadsUsed),
                onClick = { showClearDownloads = true },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup("Batería") {
            SettingsItem(
                icon = Icons.Default.BatteryAlert,
                title = "Uso de batería de la app",
                onClick = { openAppBatterySettings(context) },
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/Sonaris
                trailing = { ChevronIcon() }
            )
        }
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            title = { Text("Tamaño de la caché") },
            text = {
                androidx.compose.foundation.layout.Column {
                    MediaCache.SIZE_OPTIONS_MB.forEach { mb ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cacheMaxMb = mb
                                    MediaCache.setMaxMb(context, mb)
                                    cacheRefresh++
                                    showSizeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cacheMaxMb == mb, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(cacheLimitLabel(mb))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSizeDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showClearDownloads) {
        AlertDialog(
            onDismissRequest = { showClearDownloads = false },
            title = { Text("¿Borrar las descargas?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(downloadsUsed)} de canciones descargadas. " +
                        "Tendrás que descargarlas de nuevo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDownloads = false
                    Downloads.removeAll(context)
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloads = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("¿Borrar la caché?") },
            text = {
                Text(
                    "Se borrarán las ${formatBytes(cacheUsed)} de canciones guardadas. " +
                        "Volverán a descargarse cuando las escuches."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                MediaCache.clear(context)
                            } catch (e: Exception) {
                                // sin acceso a la caché: no hay nada que borrar
                            }
                        }
                        cacheRefresh++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Abre la ficha de la app en los ajustes del sistema (ahí está "Batería"); Android no deja cambiarlo desde la app. */
private fun openAppBatterySettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // sin pantalla de ajustes disponible: no hay nada más que hacer
        }
    }
}

private fun cacheLimitLabel(mb: Int): String = when {
    mb == MediaCache.UNLIMITED -> "Sin límite"
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    else -> "${bytes / (1024 * 1024)} MB"
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/Sonaris
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/Sonaris
}
