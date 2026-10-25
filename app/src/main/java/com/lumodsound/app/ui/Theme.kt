package com.lumodsound.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Paleta propia de la app (negros profundos y grises neutros, un solo color de acento).
 * Para cambiar el color de marca basta con tocar [AccentDark] y [AccentLight].
 */
private val AccentDark = Color(0xFF3DDC97)
private val AccentLight = Color(0xFF087A4F)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0F3D2B),
    onPrimaryContainer = Color(0xFFB7F5D8),

    secondary = Color(0xFFCFCFCF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2F2F2F),
    onSecondaryContainer = Color(0xFFFFFFFF),

    tertiary = Color(0xFF8FB8FF),
    onTertiary = Color(0xFF00203F),
    tertiaryContainer = Color(0xFF1E2F4D),
    onTertiaryContainer = Color(0xFFC9DDFF),

    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceTint = Color.Transparent,

    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),

    outline = Color(0xFF727272),
    outlineVariant = Color(0xFF3A3A3A)
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F7E0),
    onPrimaryContainer = Color(0xFF00391F),

    secondary = Color(0xFF5F5F5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF1A1A1A),

    tertiary = Color(0xFF2F5FA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E6FF),
    onTertiaryContainer = Color(0xFF001B3F),

    background = Color(0xFFF6F6F6),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFF6F6F6),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF5F5F5F),
    surfaceTint = Color.Transparent,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE4E4E4),

    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFD0D0D0)
)

/**
 * Preferencia de apariencia. Por defecto se usa la paleta propia de la app;
 * "Colores del sistema" activa los colores dinámicos de Android 12+ (Material You).
 */
object ThemeSettings {
    private const val PREFS = "settings"
    private const val KEY_DYNAMIC = "dynamic_color"

    var dynamicColor by mutableStateOf(false)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        dynamicColor = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC, false)
        loaded = true
    }

    fun setDynamicColor(context: Context, on: Boolean) {
        dynamicColor = on
        loaded = true
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC, on).apply()
    }
}

@Composable
fun LumodSoundTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    remember(context) { ThemeSettings.load(context) }
    val useDynamic = ThemeSettings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
