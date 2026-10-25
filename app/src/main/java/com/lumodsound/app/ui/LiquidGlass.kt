package com.lumodsound.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ajuste "Liquid Glass": las barras flotantes (navegación y reproductor pequeño) se ven como cristal,
 * con el contenido de detrás difuminado y curvado en el borde. Ahora lo dibuja la librería Kyant "backdrop"
 * (la misma que usa SimpMusic) en lugar de un RenderEffect hecho a mano. Necesita Android 12+.
 */
object LiquidGlassSettings {
    private const val PREFS = "settings"
    private const val KEY = "liquid_glass"
    private const val KEY_REFRACT = "liquid_glass_refract"

    /** El teléfono puede dibujar el desenfoque (Android 12 / API 31 o más). */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Lo que eligió la persona en Ajustes (apagado por defecto). */
    var enabled by mutableStateOf(false)
        private set

    /** Cristal realmente activo: elegido en Ajustes y con un Android que lo soporta. */
    val active: Boolean get() = supported && enabled

    /** La refracción del borde (lente) usa un shader AGSL: Android 13 (API 33)+. */
    val refractSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Refracción elegida en Ajustes (encendida por defecto). */
    var refract by mutableStateOf(true)
        private set

    val refractActive: Boolean get() = active && refractSupported && refract

    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        enabled = prefs(context).getBoolean(KEY, false)
        refract = prefs(context).getBoolean(KEY_REFRACT, true)
        loaded = true
    }

    fun set(context: Context, on: Boolean) {
        load(context)
        enabled = on
        prefs(context).edit().putBoolean(KEY, on).apply()
    }

    fun setRefract(context: Context, on: Boolean) {
        load(context)
        refract = on
        prefs(context).edit().putBoolean(KEY_REFRACT, on).apply()
    }
}

/** Lo que queda detrás de las barras: el contenido de la pantalla, grabado por Kyant en una capa. */
typealias GlassSource = LayerBackdrop

/** null = sin cristal (ajuste apagado o Android < 12): las barras se dibujan opacas como siempre. */
val LocalGlassSource = compositionLocalOf<GlassSource?> { null }

/**
 * Espacio que las barras flotantes ocupan abajo. Con cristal, el contenido pasa por debajo de las barras
 * (para que se vea difuminado) y las listas usan este valor como relleno inferior para que el final no quede tapado.
 * Sin cristal vale 0: las pantallas ya terminan encima de las barras.
 */
val LocalBarsBottomInset = compositionLocalOf { 0.dp }

@Composable
fun rememberGlassSource(): GlassSource = rememberLayerBackdrop { drawContent() }

/**
 * Marca el contenido que irá detrás del cristal. Las barras de cristal deben ser HERMANAS de este
 * elemento, nunca hijas (si no, el cristal se dibuja a sí mismo y falla).
 */
fun Modifier.glassSource(source: GlassSource): Modifier = this.layerBackdrop(source)

/**
 * Pulsación "líquida" (como en SimpMusic): al tocar, la superficie crece un poco, la refracción se hace más profunda
 * y un brillo radial sigue al dedo; al soltar vuelve con un muelle. Solo observa: no consume eventos, así que los
 * botones de dentro siguen recibiendo sus toques.
 */
class GlassPress internal constructor(private val scope: CoroutineScope) {
    private val spec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val anim = Animatable(0f, 0.001f)

    /** 0 en reposo, 1 mientras se mantiene pulsado. Se lee al dibujar, así no recompone. */
    val progress: Float get() = anim.value

    /** Cuánto crece la superficie al pulsar (lo fija [glassPress]). */
    internal var pressedScale: Float = 1.05f

    /** Punto del toque (coordenadas locales) donde se centra el brillo. */
    var touch by mutableStateOf(Offset.Zero)
        internal set

    internal fun down(at: Offset) {
        touch = at
        scope.launch { anim.animateTo(1f, spec) }
    }

    internal fun up() {
        scope.launch { anim.animateTo(0f, spec) }
    }
}

@Composable
fun rememberGlassPress(): GlassPress {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPress(scope) }
}

/** Registra el toque en [press] (el crecimiento lo aplica `liquidGlass`). Ponlo ANTES de `.liquidGlass(...)`. */
fun Modifier.glassPress(press: GlassPress, pressedScale: Float = 1.05f): Modifier {
    press.pressedScale = pressedScale
    return this.pointerInput(press) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            press.down(down.position)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.id == down.id }?.let { press.touch = it.position }
            } while (event.changes.any { it.pressed })
            press.up()
        }
    }
}

/**
 * Cristal líquido con Kyant backdrop: desenfoque + saturación + lente en el borde, tinte, velo suave y brillo
 * que sigue al dedo. Sin [LocalGlassSource] no hace nada: el llamador debe dibujar su fondo normal en ese caso.
 *
 * [rim] y [chromatic] se conservan por compatibilidad con las llamadas anteriores, pero ya no se usan:
 * el borde de luz lo dibuja la librería ([outlineAlpha] > 0 pide un borde uniforme alrededor, útil en círculos).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color,
    blurRadius: Dp = 22.dp,
    press: GlassPress? = null,
    rim: Float = 1f,
    outlineAlpha: Float = 0f,
    chromatic: Boolean = false
): Modifier {
    val backdrop = LocalGlassSource.current ?: return this
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val refract = LiquidGlassSettings.refractActive
    val highlight = if (outlineAlpha > 0f) Highlight.Plain else Highlight.Default
    // Los 22 dp de antes dejaban el cristal sin imagen: se limita al rango que usa SimpMusic (8–16 dp)
    val blurBase = minOf(blurRadius, 10.dp)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = { highlight },
        effects = {
            val p = press?.progress ?: 0f
            vibrancy()
            colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
            blur(blurBase.toPx() + 2f.dp.toPx() * p)
            if (refract) {
                lens(size.minDimension / 4f + 2f.dp.toPx() * p, size.minDimension / 2f, false)
            }
        },
        onDrawSurface = {
            drawRect(tint)
            // Velo oscuro (claro en tema claro) para que el cristal no se lave a blanco sobre fondos brillantes
            drawRect((if (dark) Color.Black else Color.White).copy(alpha = 0.12f))
            val p = press?.progress ?: 0f
            if (p > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f * p), Color.Transparent),
                        center = press?.touch ?: Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 1.2f
                    ),
                    blendMode = BlendMode.Plus
                )
            }
        },
        layerBlock = if (press != null) {
            {
                val s = 1f + (press.pressedScale - 1f) * press.progress
                scaleX = s
                scaleY = s
            }
        } else {
            null
        }
    )
}
