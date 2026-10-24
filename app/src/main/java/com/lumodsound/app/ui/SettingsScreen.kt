package com.lumodsound.app.ui

import com.lumodsound.app.LanguageSettings
import com.lumodsound.app.AppLanguage
import androidx.compose.material.icons.filled.Language
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.HighQuality
import com.lumodsound.app.player.AudioSettings
import com.lumodsound.app.player.AudioQuality
import com.lumodsound.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import com.lumodsound.app.player.CanvasCache
import com.lumodsound.app.player.Crossfade
import com.lumodsound.app.data.LyricsCache
import com.lumodsound.app.player.Downloads
import com.lumodsound.app.player.MediaCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import com.lumodsound.app.data.DiscordSettings
import com.lumodsound.app.data.LastfmSettings
import com.lumodsound.app.data.LyricsProvider
import com.lumodsound.app.data.LyricsProviderSettings
import com.lumodsound.app.data.SpotifySettings
import com.lumodsound.app.data.TranslateEngine
import com.lumodsound.app.data.TranslationSettings
import com.lumodsound.app.data.HistorySync
import com.lumodsound.app.str

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
    remember(context) { LiquidGlassSettings.load(context) }

    // Caché de canciones: uso actual, límite elegido y los diálogos de ajuste / borrado
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var cacheMaxMb by remember { mutableIntStateOf(MediaCache.maxMb(context)) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearDownloads by remember { mutableStateOf(false) }
    var showClearLyrics by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showLyricsProvider by remember { mutableStateOf(false) }
    remember(context) { LyricsProviderSettings.load(context) }
    remember(context) { LanguageSettings.load(context) }
    remember(context) { AudioSettings.load(context) }
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

    SettingsScaffold(title = str(R.string.settings), onBack = onBack) {
        AppHeaderCard(onClick = onAbout)
        SettingsGroup(str(R.string.account)) {
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "YouTube Music",
                subtitle = str(R.string.your_account_history_and_recommendations),
                value = if (loggedIn) str(R.string.connected_feminine) else str(R.string.not_connected),
                highlight = loggedIn,
                onClick = onAccount,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup(str(R.string.integrations)) {
            SettingsItem(
                icon = Icons.Default.Headphones,
                title = "Discord",
                subtitle = str(R.string.shows_what_you_re_listening),
                highlight = discord.connected && discord.enabled,
                value = when {
                    !discord.connected -> str(R.string.not_connected)
                    discord.enabled -> discord.username ?: str(R.string.on)
                    else -> str(R.string.off)
                },
                onClick = onDiscord,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Last.fm",
                subtitle = str(R.string.saves_what_you_listen_to),
                highlight = lastfm.connected && lastfm.enabled,
                value = when {
                    !lastfm.connected -> str(R.string.not_connected)
                    lastfm.enabled -> lastfm.username ?: str(R.string.on)
                    else -> str(R.string.off)
                },
                onClick = onLastfm,
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Album,
                title = "Spotify",
                subtitle = str(R.string.synced_lyrics_and_canvas),
                highlight = spotify.connected && (spotify.lyrics || spotify.canvas),
                value = when {
                    !spotify.connected -> str(R.string.not_connected)
                    spotify.lyrics || spotify.canvas -> str(R.string.connected)
                    else -> str(R.string.off)
                },
                onClick = onSpotify,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup(str(R.string.appearance)) {
            SettingsItem(
                icon = Icons.Default.Language,
                title = str(R.string.language),
                subtitle = str(R.string.language_subtitle),
                value = languageLabel(LanguageSettings.current),
                onClick = { showLanguage = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.Palette,
                title = str(R.string.system_colors_material_you),
                subtitle = str(R.string.uses_your_wallpaper_colors_android),
                checked = ThemeSettings.dynamicColor,
                enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                onChange = { ThemeSettings.setDynamicColor(context, it) }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.BlurOn,
                title = str(R.string.blurred_background_in_the_player),
                subtitle = str(R.string.uses_the_blurred_cover_as),
                checked = PlayerSettings.blurBackground,
                onChange = { PlayerSettings.setBlurBackground(context, it) }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.Album,
                title = str(R.string.full_cover_player),
                subtitle = str(R.string.full_cover_player_subtitle),
                checked = PlayerSettings.style == PlayerStyle.APPLE,
                onChange = { PlayerSettings.setStyle(context, if (it) PlayerStyle.APPLE else PlayerStyle.CLASSIC) }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.BlurOn,
                title = str(R.string.liquid_glass),
                subtitle = str(R.string.liquid_glass_subtitle),
                checked = LiquidGlassSettings.active,
                enabled = LiquidGlassSettings.supported,
                onChange = { LiquidGlassSettings.set(context, it) }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.BlurOn,
                title = str(R.string.liquid_glass_refract),
                subtitle = str(R.string.liquid_glass_refract_subtitle),
                checked = LiquidGlassSettings.refractActive,
                enabled = LiquidGlassSettings.active && LiquidGlassSettings.refractSupported,
                onChange = { LiquidGlassSettings.setRefract(context, it) }
            )
        }
        SettingsGroup(str(R.string.playback)) {
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Crossfade",
                subtitle = str(R.string.blends_the_end_of_a),
                value = if (crossfadeOn) "$crossfadeSec s" else str(R.string.off),
                highlight = crossfadeOn,
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
                        str(R.string.crossfade_length_s, crossfadeSec),
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
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.VolumeUp,
                title = str(R.string.normalize_audio),
                subtitle = str(R.string.evens_out_volume_between_songs),
                checked = AudioSettings.normalize,
                onChange = { AudioSettings.setNormalize(context, it) }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.HighQuality,
                title = str(R.string.audio_quality),
                subtitle = str(R.string.lower_quality_uses_less_data),
                value = str(AudioSettings.quality.labelRes),
                onClick = { showQuality = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsSwitchItem(
                icon = Icons.Default.FastForward,
                title = str(R.string.skip_silence),
                subtitle = str(R.string.skips_long_silences_in_songs),
                checked = AudioSettings.skipSilence,
                onChange = { AudioSettings.setSkipSilence(context, it) }
            )
        }
        SettingsGroup(str(R.string.lyrics_section)) {
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = str(R.string.lyrics_provider),
                subtitle = str(R.string.lyrics_provider_subtitle),
                value = LyricsProviderSettings.main.label,
                onClick = { showLyricsProvider = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Translate,
                title = str(R.string.lyrics_translation),
                subtitle = str(R.string.translates_lyrics_with_ai_or),
                value = if (translation.engine == TranslateEngine.AI) translation.provider.label else "ML Kit",
                onClick = onTranslation,
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup(str(R.string.history)) {
            SettingsItem(
                icon = Icons.Default.History,
                title = str(R.string.send_history_to_youtube_music),
                subtitle = str(R.string.improves_your_recommendations),
                value = if (historySync && loggedIn) HistorySync.lastResult(context)?.let { str(R.string.last_sent, it) } else null,
                enabled = loggedIn,
                onClick = {
                    historySync = !historySync
                    HistorySync.setEnabled(context, historySync)
                },
                trailing = { Switch(checked = historySync && loggedIn, onCheckedChange = null, enabled = loggedIn) }
            )
        }
        SettingsGroup(str(R.string.storage)) {
            SettingsItem(
                icon = Icons.Default.Storage,
                title = str(R.string.song_cache),
                value = "${formatBytes(cacheUsed)} / ${cacheLimitLabel(cacheMaxMb)}",
                onClick = { showSizeDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.DeleteSweep,
                title = str(R.string.clear_song_cache),
                onClick = { showClearDialog = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = str(R.string.lyrics_cache),
                value = formatBytes(lyricsUsed),
                onClick = { showClearLyrics = true },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            if (spotify.connected || canvasHasData) {
                SettingsItem(
                    icon = Icons.Default.Videocam,
                    title = str(R.string.canvas_cache),
                    value = formatBytes(canvasUsed),
                    onClick = { showClearCanvas = true },
                    trailing = { ChevronIcon() }
                )
                GroupDivider()
            }
            SettingsItem(
                icon = Icons.Default.Download,
                title = str(R.string.delete_downloads),
                value = formatBytes(downloadsUsed),
                onClick = { showClearDownloads = true },
                trailing = { ChevronIcon() }
            )
        }
        SettingsGroup(str(R.string.battery)) {
            SettingsItem(
                icon = Icons.Default.BatteryAlert,
                title = str(R.string.app_battery_usage),
                onClick = { openAppBatterySettings(context) },
                trailing = { ChevronIcon() }
            )
        }
    }

    if (showSizeDialog) {
        AlertDialog(
            onDismissRequest = { showSizeDialog = false },
            title = { Text(str(R.string.cache_size)) },
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
                TextButton(onClick = { showSizeDialog = false }) { Text(str(R.string.close)) }
            }
        )
    }

    if (showClearDownloads) {
        AlertDialog(
            onDismissRequest = { showClearDownloads = false },
            title = { Text(str(R.string.delete_downloads_title)) },
            text = {
                Text(
                    (str(R.string.the_of_downloaded_songs_will, formatBytes(downloadsUsed)) + " ") +
                        str(R.string.you_ll_have_to_download)
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
                }) { Text(str(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloads = false }) { Text(str(R.string.cancel)) }
            }
        )
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(str(R.string.language)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    AppLanguage.values().forEach { lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showLanguage = false
                                    if (lang != LanguageSettings.current) {
                                        LanguageSettings.set(context, lang)
                                        // Se recrea la pantalla para que todos los textos se vuelvan a pedir en el idioma nuevo
                                        context.findActivity()?.recreate()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = LanguageSettings.current == lang, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(languageLabel(lang), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(str(R.string.close)) } }
        )
    }

    if (showLyricsProvider) {
        AlertDialog(
            onDismissRequest = { showLyricsProvider = false },
            title = { Text(str(R.string.lyrics_provider)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    LyricsProvider.values().forEach { provider ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showLyricsProvider = false
                                    LyricsProviderSettings.set(context, provider)
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = LyricsProviderSettings.main == provider, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(provider.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLyricsProvider = false }) { Text(str(R.string.close)) } }
        )
    }

    if (showQuality) {
        AlertDialog(
            onDismissRequest = { showQuality = false },
            title = { Text(str(R.string.audio_quality)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    AudioQuality.values().forEach { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    AudioSettings.setQuality(context, q)
                                    showQuality = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = AudioSettings.quality == q, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.foundation.layout.Column {
                                Text(str(q.labelRes), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    str(q.hintRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQuality = false }) { Text(str(R.string.close)) } }
        )
    }

    if (showClearLyrics) {
        AlertDialog(
            onDismissRequest = { showClearLyrics = false },
            title = { Text(str(R.string.clear_the_lyrics_cache)) },
            text = {
                Text(
                    (str(R.string.the_of_saved_lyrics_will, formatBytes(lyricsUsed)) + " ") +
                        str(R.string.redownload_when_opened)
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
                }) { Text(str(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearLyrics = false }) { Text(str(R.string.cancel)) }
            }
        )
    }

    if (showClearCanvas) {
        AlertDialog(
            onDismissRequest = { showClearCanvas = false },
            title = { Text(str(R.string.clear_the_canvas_cache)) },
            text = {
                Text(
                    (str(R.string.the_of_saved_videos_will, formatBytes(canvasUsed)) + " ") +
                        str(R.string.redownload_when_watched)
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
                }) { Text(str(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCanvas = false }) { Text(str(R.string.cancel)) }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(str(R.string.clear_the_cache)) },
            text = {
                Text(
                    (str(R.string.the_of_saved_songs_will, formatBytes(cacheUsed)) + " ") +
                        str(R.string.redownload_when_listened)
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
                }) { Text(str(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(str(R.string.cancel)) }
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
    mb == MediaCache.UNLIMITED -> str(R.string.no_limit)
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / 1024} KB"
}

/** Encabezado de Ajustes: logo, nombre y versión de la app; al tocarla se abre "Acerca de". */
@Composable
private fun AppHeaderCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersionName(context) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "LumodSound",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    str(R.string.version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChevronIcon()
        }
    }
}

/** Nombre de cada idioma: en su propio idioma, para que se reconozca aunque la app esté en otro. */
private fun languageLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.SYSTEM -> str(R.string.language_system_default)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.SPANISH -> "Español"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
