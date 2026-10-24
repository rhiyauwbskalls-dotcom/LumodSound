package com.lumodsound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumodsound.app.data.DiscordSettings
import com.lumodsound.app.data.DiscordStore
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private val Online = Color(0xFF23A55A)
private val Idle = Color(0xFFF0B232)
private val Dnd = Color(0xFFF23F43)

/** Ajustes de la presencia en Discord, agrupados por secciones. */
@Composable
fun DiscordSettingsScreen(
    store: DiscordStore,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val s by store.settings.collectAsStateWithLifecycle()

    SettingsScaffold(title = "Discord", onBack = onBack) {
        if (!s.noticeHidden) {
            PrivacyNotice(onHide = { store.setNoticeHidden(true) })
        }

        SettingsGroup(str(R.string.account)) {
            AccountCard(s, onLogin, onLogout)
        }

        SettingsGroup(str(R.string.presence)) {
            SettingsSwitchItem(Icons.Default.Visibility, str(R.string.show_on_discord), s.enabled, s.connected, store::setEnabled)
            GroupDivider()
            SettingsSwitchItem(Icons.Default.Timeline, str(R.string.progress_bar), s.showProgress, s.connected, store::setShowProgress)
            GroupDivider()
            SettingsSwitchItem(Icons.Default.TouchApp, str(R.string.listen_button), s.showButton, s.connected, store::setShowButton)
            GroupDivider()
            SettingsSwitchItem(Icons.Default.PauseCircle, str(R.string.hide_when_paused), s.hideWhenPaused, s.connected, store::setHideWhenPaused)
        }

        SettingsGroup(str(R.string.status)) {
            listOf(
                Triple("online", str(R.string.online), Online),
                Triple("idle", str(R.string.away), Idle),
                Triple("dnd", str(R.string.do_not_disturb), Dnd)
            ).forEachIndexed { i, (key, label, color) ->
                if (i > 0) GroupDivider()
                StatusRow(label, color, selected = s.status == key, enabled = s.connected) { store.setStatus(key) }
            }
        }
    }
}

@Composable
private fun PrivacyNotice(onHide: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    str(R.string.we_use_oauth2_to_connect),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onHide) { Text(str(R.string.hide)) }
            }
        }
    }
}

@Composable
private fun AccountCard(s: DiscordSettings, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (s.connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (s.connected) {
                    Text(
                        (s.username ?: "D").take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.connected) (s.username ?: "Discord") else "Discord",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (s.connected) Online else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.connected) str(R.string.connected) else str(R.string.not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (s.connected) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.disconnect)) }
        } else {
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.connect)) }
=======

private val Online = Color(0xFF23A55A)
private val Idle = Color(0xFFF0B232)
private val Dnd = Color(0xFFF23F43)

/** Ajustes de la presencia en Discord, agrupados por secciones. */
@Composable
fun DiscordSettingsScreen(
    store: DiscordStore,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val s by store.settings.collectAsStateWithLifecycle()

    SettingsScaffold(title = "Discord", onBack = onBack) {
        if (!s.noticeHidden) {
            PrivacyNotice(onHide = { store.setNoticeHidden(true) })
        }

        SettingsGroup("Cuenta") {
            AccountCard(s, onLogin, onLogout)
        }

        SettingsGroup("Presencia") {
            SettingsSwitchItem(Icons.Default.Visibility, "Mostrar en Discord", s.enabled, s.connected, store::setEnabled)
            GroupDivider()
            SettingsSwitchItem(Icons.Default.Timeline, "Barra de progreso", s.showProgress, s.connected, store::setShowProgress)
            GroupDivider()
            SettingsSwitchItem(Icons.Default.TouchApp, "Botón para escuchar", s.showButton, s.connected, store::setShowButton)
            GroupDivider()
            SettingsSwitchItem(Icons.Default.PauseCircle, "Ocultar al pausar", s.hideWhenPaused, s.connected, store::setHideWhenPaused)
        }

        SettingsGroup("Estado") {
            listOf(
                Triple("online", "En línea", Online),
                Triple("idle", "Ausente", Idle),
                Triple("dnd", "No molestar", Dnd)
            ).forEachIndexed { i, (key, label, color) ->
                if (i > 0) GroupDivider()
                StatusRow(label, color, selected = s.status == key, enabled = s.connected) { store.setStatus(key) }
            }
        }
    }
}

@Composable
private fun PrivacyNotice(onHide: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    "Usamos OAuth2 para conectar con Discord. Tu token no se guarda en ningún servidor y se cifra en la app.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onHide) { Text("Ocultar") }
            }
        }
    }
}

@Composable
private fun AccountCard(s: DiscordSettings, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (s.connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (s.connected) {
                    Text(
                        (s.username ?: "D").take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.connected) (s.username ?: "Discord") else "Discord",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (s.connected) Online else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (s.connected) "Conectado" else "Sin conectar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (s.connected) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
        } else {
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
        }
    }
}

@Composable
private fun StatusRow(label: String, color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}
