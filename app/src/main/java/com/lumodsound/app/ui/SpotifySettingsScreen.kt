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
                        "Inicia sesión en Spotify para traer sus letras sincronizadas y el Canvas (el video " +
                            "en bucle) de cada canción. Solo se guarda la cookie de sesión, cifrada en el teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SettingsGroup("Cuenta") {
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
                                !s.connected -> "Sin conectar"
                                s.userName != null -> "Conectado como ${s.userName}"
                                else -> "Cookie guardada, sesión sin verificar"
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
                                store.setLastResult("Verificando la sesión…")
                                scope.launch {
                                    val result = service.verifySession()
                                    store.setLastResult(result)
                                    testing = false
                                }
                            },
                            enabled = !testing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (testing) "Verificando…" else "Verificar sesión") }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            store.disconnect()
                            Toast.makeText(context, "Spotify desconectado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Desconectar") }
                } else {
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión en Spotify") }
                }
            }
        }

        if (!s.connected) {
            SettingsGroup("O pega la cookie sp_dc") {
                var pasted by rememberSaveable { mutableStateOf("") }
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Si el inicio de sesión no te deja entrar (por ejemplo con Google o Facebook), copia el " +
                            "valor de la cookie sp_dc desde open.spotify.com en un navegador y pégalo aquí.",
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
                                store.setLastResult("Verificando la sesión…")
                                scope.launch {
                                    val result = service.verifySession()
                                    store.setLastResult(result)
                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "No se pudo guardar la cookie", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = pasted.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar") }
                }
            }
        }

        SettingsGroup("Funciones") {
            SettingsSwitchItem(
                Icons.Default.MusicNote, "Letras sincronizadas de Spotify", s.lyrics, s.connected, store::setLyrics
            )
            GroupDivider()
            SettingsSwitchItem(
                Icons.Default.Videocam, "Canvas en el reproductor", s.canvas, s.connected, store::setCanvas
            )
            if (s.connected) {
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Último resultado",
                    value = s.lastResult ?: "Todavía ninguno. Se prueba al abrir una letra o el reproductor."
                )
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    OutlinedButton(
                        onClick = {
                            testing = true
                            store.setLastResult("Probando…")
                            scope.launch {
                                val result = service.diagnose()
                                store.setLastResult(result)
                                testing = false
                            }
                        },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (testing) "Probando…" else "Probar conexión") }
                }
            }
        }
    }
}
