package com.sonaris.app.data

import android.content.Context

/**
 * Sesión de Google guardada en el teléfono: las cookies de music.youtube.com que deja el inicio de sesión.
 * Se guarda en SharedPreferences privadas de la app (sin cifrar): quien tenga acceso root al teléfono
 * podría leerlas. Cerrar sesión las borra.
 */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun cookie(): String? = prefs.getString("cookie", null)?.takeIf { it.isNotBlank() }

    fun save(cookie: String) {
        prefs.edit().putString("cookie", cookie).apply()
    }

    fun clear() {
        prefs.edit().remove("cookie").apply()
    }
}
