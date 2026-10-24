package com.lumodsound.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumodsound.app.data.SpotifyService
import com.lumodsound.app.data.SpotifyStore
import kotlinx.coroutines.launch
import com.lumodsound.app.R
import com.lumodsound.app.str

private val SpotifyGreen = Color(0xFF1DB954)

/** Ajustes de Spotify: inicio de sesión (cookie sp_dc), letras sincronizadas y Canvas. */
@Composable
fun SpotifySettingsScreen(
    store: SpotifyStore,
    service: SpotifyService,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    val s by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }

    SettingsScaffold(title = "Spotify", onBack = onBack) {
        if (!s.connected) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        str(R.string.sign_in_to_spotify_to),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SettingsGroup(str(R.string.account)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (s.connected) SpotifyGreen else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = if (s.connected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                !s.connected -> str(R.string.not_connected)
                                s.userName != null -> str(R.string.signed_in_as, s.userName)
                                else -> str(R.string.cookie_saved_session_not_verified)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (s.connected) {
                    if (s.userName == null) {
                        Button(
                            onClick = {
                                testing = true
                                store.setLastResult(str(R.string.verifying_the_session))
                                scope.launch {
                                    val result = service.verifySession()
                                    store.setLastResult(result)
                                    testing = false
                                }
                            },
                            enabled = !testing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (testing) str(R.string.verifying) else str(R.string.verify_session)) }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            store.disconnect()
                            Toast.makeText(context, str(R.string.spotify_disconnected), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(str(R.string.disconnect)) }
                } else {
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.sign_in_to_spotify)) }
                }
            }
        }

        if (!s.connected) {
            SettingsGroup(str(R.string.or_paste_the_sp_dc)) {
                var pasted by rememberSaveable { mutableStateOf("") }
                Column(Modifier.padding(20.dp)) {
                    Text(
                        str(R.string.if_signing_in_doesn_t),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = { Text("sp_dc") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            if (store.saveSpDc(pasted)) {
                                pasted = ""
                                store.setLastResult(str(R.string.verifying_the_session))
                                scope.launch {
                                    val result = service.verifySession()
                                    store.setLastResult(result)
                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, str(R.string.couldn_t_save_the_cookie), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = pasted.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(str(R.string.save)) }
                }
            }
        }

        SettingsGroup(str(R.string.features)) {
            SettingsSwitchItem(
                Icons.Default.MusicNote, str(R.string.spotify_synced_lyrics), s.lyrics, s.connected, store::setLyrics
            )
            GroupDivider()
            SettingsSwitchItem(
                Icons.Default.Videocam, str(R.string.canvas_in_the_player), s.canvas, s.connected, store::setCanvas
            )
            if (s.connected) {
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.History,
                    title = str(R.string.last_result),
                    value = s.lastResult ?: str(R.string.none_yet_it_s_tested)
                )
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    OutlinedButton(
                        onClick = {
                            testing = true
                            store.setLastResult(str(R.string.testing))
                            scope.launch {
                                val result = service.diagnose()
                                store.setLastResult(result)
                                testing = false
                            }
                        },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (testing) str(R.string.testing) else str(R.string.test_connection)) }
                }
            }
        }
    }
}
