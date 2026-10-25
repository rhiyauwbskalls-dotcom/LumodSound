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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumodsound.app.data.LastfmAuth
import com.lumodsound.app.data.LastfmSettings
import com.lumodsound.app.data.LastfmStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private val Connected = Color(0xFF23A55A)

/** Ajustes de Last.fm: inicio de sesión y qué se manda. */
@Composable
fun LastfmSettingsScreen(
    store: LastfmStore,
    onBack: () -> Unit
) {
    val s by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // El usuario ya abrió Last.fm para aprobar el acceso: al volver se termina el inicio de sesión
    var awaiting by rememberSaveable { mutableStateOf(store.pendingToken() != null) }
    var busy by remember { mutableStateOf(false) }

    fun tryComplete(silentIfWaiting: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { LastfmAuth.completePending(context.applicationContext) }
            busy = false
            if (r.connected) {
                awaiting = false
                toast(str(R.string.last_fm_connected))
            } else {
                if (!r.waiting) awaiting = false
                if (!(r.waiting && silentIfWaiting)) r.message?.let { toast(it) }
            }
        }
    }

    // Al volver del navegador se intenta terminar solo (en silencio si Last.fm aún no tiene la aprobación)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaiting && !s.connected) tryComplete(silentIfWaiting = true)
    }

    SettingsScaffold(title = "Last.fm", onBack = onBack) {
        if (!s.connected) {
            SetupNotice()
        }

        SettingsGroup(str(R.string.account)) {
            AccountCard(
                s = s,
                busy = busy,
                onLogin = {
                    busy = true
                    scope.launch {
                        val failure = withContext(Dispatchers.IO) { LastfmAuth.begin(context.applicationContext) }
                        busy = false
                        if (failure == null) awaiting = true else toast(failure)
                    }
                },
                onLogout = {
                    store.disconnect()
                    awaiting = false
                    toast(str(R.string.last_fm_disconnected))
                }
            )
        }

        if (awaiting && !s.connected) {
            SettingsGroup(str(R.string.after_approving)) {
                WaitingCard(busy = busy, onDone = { tryComplete(silentIfWaiting = false) })
            }
        }

        SettingsGroup(str(R.string.sending)) {
            SettingsSwitchItem(
                Icons.Default.Sync, str(R.string.send_what_i_listen_to), s.enabled, s.connected, store::setEnabled
            )
            GroupDivider()
            SettingsSwitchItem(
                Icons.Default.MusicNote, str(R.string.show_now_playing), s.nowPlaying, s.connected, store::setNowPlaying
            )
            if (s.connected) {
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.History,
                    title = str(R.string.last_scrobble),
                    value = s.lastResult ?: str(R.string.none_yet_a_song_counts)
                )
            }
        }
    }
}

@Composable
private fun SetupNotice() {
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
                (str(R.string.tap_sign_in_last_fm) + " ") +
                    str(R.string.your_password_never_goes_through),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountCard(s: LastfmSettings, busy: Boolean, onLogin: () -> Unit, onLogout: () -> Unit) {
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
                        (s.username ?: "L").take(1).uppercase(),
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
                    if (s.connected) (s.username ?: "Last.fm") else "Last.fm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (s.connected) Connected else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            busy -> str(R.string.connecting)
                            s.connected -> str(R.string.connected)
                            else -> str(R.string.not_connected)
                        },
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
            Button(
                onClick = onLogin,
                enabled = s.hasCredentials && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(str(R.string.sign_in_to_last_fm)) }
        }
    }
}

@Composable
private fun WaitingCard(busy: Boolean, onDone: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(
            (str(R.string.approve_access_on_the_last) + " ") +
                str(R.string.if_nothing_happens_tap_the),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(str(R.string.i_ve_approved_finish))
=======
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

private val Connected = Color(0xFF23A55A)

/** Ajustes de Last.fm: inicio de sesión y qué se manda. */
@Composable
fun LastfmSettingsScreen(
    store: LastfmStore,
    onBack: () -> Unit
) {
    val s by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // El usuario ya abrió Last.fm para aprobar el acceso: al volver se termina el inicio de sesión
    var awaiting by rememberSaveable { mutableStateOf(store.pendingToken() != null) }
    var busy by remember { mutableStateOf(false) }

    fun tryComplete(silentIfWaiting: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { LastfmAuth.completePending(context.applicationContext) }
            busy = false
            if (r.connected) {
                awaiting = false
                toast(str(R.string.last_fm_connected))
            } else {
                if (!r.waiting) awaiting = false
                if (!(r.waiting && silentIfWaiting)) r.message?.let { toast(it) }
            }
        }
    }

    // Al volver del navegador se intenta terminar solo (en silencio si Last.fm aún no tiene la aprobación)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaiting && !s.connected) tryComplete(silentIfWaiting = true)
    }

    SettingsScaffold(title = "Last.fm", onBack = onBack) {
        if (!s.connected) {
            SetupNotice()
        }

        SettingsGroup(str(R.string.account)) {
            AccountCard(
                s = s,
                busy = busy,
                onLogin = {
                    busy = true
                    scope.launch {
                        val failure = withContext(Dispatchers.IO) { LastfmAuth.begin(context.applicationContext) }
                        busy = false
                        if (failure == null) awaiting = true else toast(failure)
                    }
                },
                onLogout = {
                    store.disconnect()
                    awaiting = false
                    toast(str(R.string.last_fm_disconnected))
                }
            )
        }

        if (awaiting && !s.connected) {
            SettingsGroup(str(R.string.after_approving)) {
                WaitingCard(busy = busy, onDone = { tryComplete(silentIfWaiting = false) })
            }
        }

        SettingsGroup(str(R.string.sending)) {
            SettingsSwitchItem(
                Icons.Default.Sync, str(R.string.send_what_i_listen_to), s.enabled, s.connected, store::setEnabled
            )
            GroupDivider()
            SettingsSwitchItem(
                Icons.Default.MusicNote, str(R.string.show_now_playing), s.nowPlaying, s.connected, store::setNowPlaying
            )
            if (s.connected) {
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.History,
                    title = str(R.string.last_scrobble),
                    value = s.lastResult ?: str(R.string.none_yet_a_song_counts)
                )
            }
        }
    }
}

@Composable
private fun SetupNotice() {
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
                (str(R.string.tap_sign_in_last_fm) + " ") +
                    str(R.string.your_password_never_goes_through),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountCard(s: LastfmSettings, busy: Boolean, onLogin: () -> Unit, onLogout: () -> Unit) {
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
                        (s.username ?: "L").take(1).uppercase(),
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
                    if (s.connected) (s.username ?: "Last.fm") else "Last.fm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (s.connected) Connected else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            busy -> str(R.string.connecting)
                            s.connected -> str(R.string.connected)
                            else -> str(R.string.not_connected)
                        },
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
            Button(
                onClick = onLogin,
                enabled = s.hasCredentials && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(str(R.string.sign_in_to_last_fm)) }
        }
    }
}

@Composable
private fun WaitingCard(busy: Boolean, onDone: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(
            (str(R.string.approve_access_on_the_last) + " ") +
                str(R.string.if_nothing_happens_tap_the),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(str(R.string.i_ve_approved_finish))
=======

private val Connected = Color(0xFF23A55A)

/** Ajustes de Last.fm: inicio de sesión y qué se manda. */
@Composable
fun LastfmSettingsScreen(
    store: LastfmStore,
    onBack: () -> Unit
) {
    val s by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // El usuario ya abrió Last.fm para aprobar el acceso: al volver se termina el inicio de sesión
    var awaiting by rememberSaveable { mutableStateOf(store.pendingToken() != null) }
    var busy by remember { mutableStateOf(false) }

    fun tryComplete(silentIfWaiting: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { LastfmAuth.completePending(context.applicationContext) }
            busy = false
            if (r.connected) {
                awaiting = false
                toast("Last.fm conectado")
            } else {
                if (!r.waiting) awaiting = false
                if (!(r.waiting && silentIfWaiting)) r.message?.let { toast(it) }
            }
        }
    }

    // Al volver del navegador se intenta terminar solo (en silencio si Last.fm aún no tiene la aprobación)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaiting && !s.connected) tryComplete(silentIfWaiting = true)
    }

    SettingsScaffold(title = "Last.fm", onBack = onBack) {
        if (!s.connected) {
            SetupNotice()
        }

        SettingsGroup("Cuenta") {
            AccountCard(
                s = s,
                busy = busy,
                onLogin = {
                    busy = true
                    scope.launch {
                        val failure = withContext(Dispatchers.IO) { LastfmAuth.begin(context.applicationContext) }
                        busy = false
                        if (failure == null) awaiting = true else toast(failure)
                    }
                },
                onLogout = {
                    store.disconnect()
                    awaiting = false
                    toast("Last.fm desconectado")
                }
            )
        }

        if (awaiting && !s.connected) {
            SettingsGroup("Después de aprobar") {
                WaitingCard(busy = busy, onDone = { tryComplete(silentIfWaiting = false) })
            }
        }

        SettingsGroup("Envío") {
            SettingsSwitchItem(
                Icons.Default.Sync, "Enviar lo que escucho (scrobble)", s.enabled, s.connected, store::setEnabled
            )
            GroupDivider()
            SettingsSwitchItem(
                Icons.Default.MusicNote, "Mostrar «ahora suena»", s.nowPlaying, s.connected, store::setNowPlaying
            )
            if (s.connected) {
                GroupDivider()
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Último envío",
                    value = s.lastResult ?: "Todavía ninguno. Una canción cuenta al escuchar la mitad o 4 minutos."
                )
            }
        }
    }
}

@Composable
private fun SetupNotice() {
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
                "Toca «Iniciar sesión»: se abre Last.fm en el navegador, apruebas el acceso y vuelves a la app. " +
                    "Tu contraseña no pasa por aquí.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountCard(s: LastfmSettings, busy: Boolean, onLogin: () -> Unit, onLogout: () -> Unit) {
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
                        (s.username ?: "L").take(1).uppercase(),
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
                    if (s.connected) (s.username ?: "Last.fm") else "Last.fm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (s.connected) Connected else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            busy -> "Conectando…"
                            s.connected -> "Conectado"
                            else -> "Sin conectar"
                        },
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
            Button(
                onClick = onLogin,
                enabled = s.hasCredentials && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Iniciar sesión en Last.fm") }
        }
    }
}

@Composable
private fun WaitingCard(busy: Boolean, onDone: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Text(
            "Aprueba el acceso en la página de Last.fm y vuelve a LumodSound: se conecta solo. " +
                "Si no avanza, toca el botón.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Ya aprobé, completar")
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
        }
    }
}
