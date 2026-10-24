package com.lumodsound.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumodsound.app.R
<<<<<<< HEAD
import com.lumodsound.app.str

/** Enlaces de la pantalla «Acerca de». Cambia GITHUB_USER o REPO si tu usuario o el nombre del repositorio cambian. */
object AboutInfo {
    private const val GITHUB_USER = "rhiyauwbskalls-dotcom"
    private const val REPO = "LumodSound"

    const val PROJECT_URL = "https://github.com/$GITHUB_USER/$REPO"
    const val PROFILE_URL = "https://github.com/$GITHUB_USER"
    const val LICENSE_NAME = "GNU GPL v3.0"
    const val LICENSE_URL = "https://github.com/$GITHUB_USER/$REPO/blob/main/LICENSE"
}

/** «Acerca de»: logo arriba, enlaces (proyecto, perfil de GitHub, licencia) y abajo una nota con la versión. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersionName(context) }

    SettingsScaffold(title = str(R.string.about), onBack = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = str(R.string.lumodsound_logo),
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text("LumodSound", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        SettingsGroup(str(R.string.links)) {
            SettingsItem(
                icon = Icons.Default.Code,
                title = str(R.string.project),
                value = AboutInfo.PROJECT_URL.removePrefix("https://"),
                onClick = { openUrl(context, AboutInfo.PROJECT_URL) },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Person,
                title = str(R.string.github_profile),
                value = AboutInfo.PROFILE_URL.removePrefix("https://"),
                onClick = { openUrl(context, AboutInfo.PROFILE_URL) },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Gavel,
                title = str(R.string.license),
                value = AboutInfo.LICENSE_NAME,
                onClick = { openUrl(context, AboutInfo.LICENSE_URL) },
                trailing = { ChevronIcon() }
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                (str(R.string.unofficial_open_source_youtube_music) + " ") +
                    str(R.string.not_affiliated_with_youtube_google),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                str(R.string.version, version),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Suppress("DEPRECATION")
internal fun appVersionName(context: Context): String = try {
=======

/** Enlaces de la pantalla «Acerca de». Cambia GITHUB_USER o REPO si tu usuario o el nombre del repositorio cambian. */
object AboutInfo {
    private const val GITHUB_USER = "rhiyauwbskalls-dotcom"
    private const val REPO = "LumodSound"

    const val PROJECT_URL = "https://github.com/$GITHUB_USER/$REPO"
    const val PROFILE_URL = "https://github.com/$GITHUB_USER"
    const val LICENSE_NAME = "GNU GPL v3.0"
    const val LICENSE_URL = "https://github.com/$GITHUB_USER/$REPO/blob/main/LICENSE"
}

/** «Acerca de»: logo arriba, enlaces (proyecto, perfil de GitHub, licencia) y abajo una nota con la versión. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersionName(context) }

    SettingsScaffold(title = "Acerca de", onBack = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "Logo de LumodSound",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text("LumodSound", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        SettingsGroup("Enlaces") {
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Proyecto",
                value = AboutInfo.PROJECT_URL.removePrefix("https://"),
                onClick = { openUrl(context, AboutInfo.PROJECT_URL) },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Perfil de GitHub",
                value = AboutInfo.PROFILE_URL.removePrefix("https://"),
                onClick = { openUrl(context, AboutInfo.PROFILE_URL) },
                trailing = { ChevronIcon() }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Default.Gavel,
                title = "Licencia",
                value = AboutInfo.LICENSE_NAME,
                onClick = { openUrl(context, AboutInfo.LICENSE_URL) },
                trailing = { ChevronIcon() }
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Cliente de YouTube Music no oficial y de código abierto, hecho con Kotlin y Jetpack Compose. " +
                    "No está afiliado a YouTube, Google, Spotify, Discord ni Last.fm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Versión $version",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String = try {
>>>>>>> branch 'main' of https://github.com/rhiyauwbskalls-dotcom/LumodSound
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
} catch (e: Exception) {
    "—"
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // sin navegador instalado: no hay nada más que hacer
    }
}
