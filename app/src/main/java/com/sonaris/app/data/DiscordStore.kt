package com.sonaris.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** ID de la aplicación de Discord que usa la app (fijo, ya no se puede cambiar desde Ajustes). */
const val DEFAULT_DISCORD_APP_ID = "1561239330767507527"

data class DiscordSettings(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtSec: Long = 0,
    val username: String? = null,
    val enabled: Boolean = false,
    val showProgress: Boolean = true,
    val showButton: Boolean = true,
    val hideWhenPaused: Boolean = true,
    val status: String = "online", // online | idle | dnd
    /** ID de la aplicación de Discord (la del usuario, creada en el Developer Portal). */
    val appId: String = DEFAULT_DISCORD_APP_ID,
    /** El usuario ocultó el aviso de privacidad de la pantalla de Discord. */
    val noticeHidden: Boolean = false
) {
    val connected: Boolean get() = !accessToken.isNullOrBlank()
}

/** Inicio de sesión a medias: se guarda por si el sistema cierra la app mientras el usuario está en el navegador. */
data class PendingAuth(val clientId: String, val verifier: String, val state: String)

/**
 * Cuenta de Discord y opciones de la presencia. Es único para toda la app (la pantalla de ajustes
 * y el servicio de reproducción comparten el mismo flujo). Los tokens van cifrados (ver [TokenCipher]).
 */
class DiscordStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("discord_oauth", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<DiscordSettings> = _settings.asStateFlow()

    private fun load() = DiscordSettings(
        accessToken = prefs.getString("access_enc", null)?.let(TokenCipher::decrypt)?.takeIf { it.isNotBlank() },
        refreshToken = prefs.getString("refresh_enc", null)?.let(TokenCipher::decrypt)?.takeIf { it.isNotBlank() },
        expiresAtSec = prefs.getLong("expiresAt", 0),
        username = prefs.getString("username", null),
        enabled = prefs.getBoolean("enabled", false),
        showProgress = prefs.getBoolean("showProgress", true),
        showButton = prefs.getBoolean("showButton", true),
        hideWhenPaused = prefs.getBoolean("hideWhenPaused", true),
        status = prefs.getString("status", "online") ?: "online",
        appId = DEFAULT_DISCORD_APP_ID,
        noticeHidden = prefs.getBoolean("noticeHidden", false)
    )

    private fun edit(change: DiscordSettings.() -> DiscordSettings) {
        _settings.update { it.change() }
        val s = _settings.value
        prefs.edit()
            // Los tokens se guardan cifrados; si el cifrado fallara, no se guardan (nunca en texto plano)
            .remove("access").remove("refresh")
            .putString("access_enc", s.accessToken?.let(TokenCipher::encrypt))
            .putString("refresh_enc", s.refreshToken?.let(TokenCipher::encrypt))
            .putLong("expiresAt", s.expiresAtSec)
            .putString("username", s.username)
            .putBoolean("enabled", s.enabled)
            .putBoolean("showProgress", s.showProgress)
            .putBoolean("showButton", s.showButton)
            .putBoolean("hideWhenPaused", s.hideWhenPaused)
            .putString("status", s.status)
            .putBoolean("noticeHidden", s.noticeHidden)
            .apply()
    }

    fun connect(access: String, refresh: String?, expiresInSec: Long, username: String) = edit {
        copy(
            accessToken = access,
            refreshToken = refresh ?: refreshToken,
            expiresAtSec = nowSec() + expiresInSec,
            username = username,
            enabled = true
        )
    }

    fun saveTokens(access: String, refresh: String?, expiresInSec: Long) = edit {
        copy(
            accessToken = access,
            refreshToken = refresh ?: refreshToken,
            expiresAtSec = nowSec() + expiresInSec
        )
    }

    /** El gateway rechazó el token: se fuerza a renovarlo la próxima vez. */
    fun markExpired() = edit { copy(expiresAtSec = 1) }

    fun disconnect() = edit {
        copy(accessToken = null, refreshToken = null, expiresAtSec = 0, username = null, enabled = false)
    }

    fun setEnabled(v: Boolean) = edit { copy(enabled = v) }
    fun setShowProgress(v: Boolean) = edit { copy(showProgress = v) }
    fun setShowButton(v: Boolean) = edit { copy(showButton = v) }
    fun setHideWhenPaused(v: Boolean) = edit { copy(hideWhenPaused = v) }
    fun setStatus(v: String) = edit { copy(status = v) }
    fun setNoticeHidden(v: Boolean) = edit { copy(noticeHidden = v) }

    fun savePending(p: PendingAuth) {
        prefs.edit()
            .putString("p_client", p.clientId)
            .putString("p_verifier", p.verifier)
            .putString("p_state", p.state)
            .apply()
    }

    fun takePending(): PendingAuth? {
        val client = prefs.getString("p_client", null)
        val verifier = prefs.getString("p_verifier", null)
        val state = prefs.getString("p_state", null)
        prefs.edit().remove("p_client").remove("p_verifier").remove("p_state").apply()
        return if (client != null && verifier != null && state != null) PendingAuth(client, verifier, state) else null
    }

    companion object {
        private fun nowSec() = System.currentTimeMillis() / 1000

        @Volatile private var instance: DiscordStore? = null
        fun get(context: Context): DiscordStore =
            instance ?: synchronized(this) { instance ?: DiscordStore(context).also { instance = it } }
    }
}
