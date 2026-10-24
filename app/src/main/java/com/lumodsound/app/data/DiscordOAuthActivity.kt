package com.lumodsound.app.data

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
<<<<<<< HEAD
import com.lumodsound.app.R
import com.lumodsound.app.str

/** Recibe la redirección de Discord (ytmusicdiscord://oauth2/callback), termina el login y se cierra. */
class DiscordOAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val uri = intent?.data
        val code = uri?.getQueryParameter("code")
        val state = uri?.getQueryParameter("state")
        val error = uri?.getQueryParameter("error")
        val description = uri?.getQueryParameter("error_description")

        if (uri == null || code == null || error != null) {
            DiscordStore.get(app).takePending()
            toast(
                when {
                    uri == null -> str(R.string.discord_didn_t_return_any)
                    error != null -> "Discord: $error" + (description?.let { " ($it)" } ?: "")
                    else -> str(R.string.discord_didn_t_return_the_2, uri.toString().take(120))
=======

/** Recibe la redirección de Discord (ytmusicdiscord://oauth2/callback), termina el login y se cierra. */
class DiscordOAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val uri = intent?.data
        val code = uri?.getQueryParameter("code")
        val state = uri?.getQueryParameter("state")
        val error = uri?.getQueryParameter("error")
        val description = uri?.getQueryParameter("error_description")

        if (uri == null || code == null || error != null) {
            DiscordStore.get(app).takePending()
            toast(
                when {
                    uri == null -> "Discord no devolvió ningún dato"
                    error != null -> "Discord: $error" + (description?.let { " ($it)" } ?: "")
                    else -> "Discord no devolvió el código: ${uri.toString().take(120)}"
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
                }
            )
        } else {
            Thread {
                val failure = DiscordAuth.complete(app, code, state)
                toast(failure ?: "Discord conectado")
            }.start()
        }
        finish()
    }

    private fun toast(msg: String) {
        val app = applicationContext
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
    }
}
