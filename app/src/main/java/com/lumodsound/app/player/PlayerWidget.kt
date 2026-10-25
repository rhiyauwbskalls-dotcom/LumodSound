package com.lumodsound.app.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lumodsound.app.MainActivity
import com.lumodsound.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Widget de pantalla de inicio: portada, título, artista y botones anterior / reproducir-pausar / siguiente.
 *
 * Pensado para no gastar batería: no hay actualización periódica ni servicio propio. El widget se redibuja solo
 * cuando el reproductor avisa de un cambio (canción o play/pausa), y sin ningún widget colocado no se hace nada.
 * Lo último que sonó queda guardado para que el widget no aparezca vacío cuando el servicio no está activo.
 */
object PlayerWidget {
    private const val PREFS = "player_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val ART_FILE = "widget_art.png"
    private const val ART_SIZE_PX = 192

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun component(context: Context) =
        ComponentName(context, PlayerWidgetProvider::class.java)

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    fun save(context: Context, title: String?, artist: String, playing: Boolean) {
        prefs(context).edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .putBoolean(KEY_PLAYING, playing)
            .apply()
    }

    fun savePlaying(context: Context, playing: Boolean) {
        prefs(context).edit().putBoolean(KEY_PLAYING, playing).apply()
    }

    fun saveArt(context: Context, bitmap: Bitmap?) {
        val file = File(context.filesDir, ART_FILE)
        if (bitmap == null) {
            file.delete()
            return
        }
        runCatching { FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /** Dibuja el widget (todas sus instancias) con lo último guardado. */
    fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(component(context))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, build(context))
    }

    private fun build(context: Context): RemoteViews {
        val sp = prefs(context)
        val title = sp.getString(KEY_TITLE, null)
        val artist = sp.getString(KEY_ARTIST, "").orEmpty()
        val playing = sp.getBoolean(KEY_PLAYING, false)

        val rv = RemoteViews(context.packageName, R.layout.widget_player)
        rv.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_idle_title))
        rv.setTextViewText(
            R.id.widget_artist,
            if (title == null || artist.isBlank()) context.getString(R.string.app_name) else artist
        )
        rv.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)

        val art = File(context.filesDir, ART_FILE)
        val bmp = if (title != null && art.exists()) BitmapFactory.decodeFile(art.path) else null
        if (bmp != null) rv.setImageViewBitmap(R.id.widget_art, bmp)
        else rv.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_art_placeholder)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        rv.setOnClickPendingIntent(R.id.widget_root, open)
        rv.setOnClickPendingIntent(R.id.widget_prev, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 1, false))
        rv.setOnClickPendingIntent(R.id.widget_play, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 2, !playing))
        rv.setOnClickPendingIntent(R.id.widget_next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT, 3, false))
        return rv
    }

    /**
     * Los botones mandan una "tecla multimedia" directo al servicio de música, igual que los botones de la
     * notificación. Así no hay que conectarse al reproductor desde un receptor (eso cerraba la app).
     * Para reanudar desde pausa se usa el modo en primer plano, que Android permite al tocar un widget.
     */
    private fun mediaKey(context: Context, keyCode: Int, code: Int, foreground: Boolean): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, code, intent, flags)
        } else {
            PendingIntent.getService(context, code, intent, flags)
        }
    }

    /** Portada cuadrada con esquinas redondeadas (RemoteViews no sabe recortar imágenes). */
    internal fun roundedSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, ART_SIZE_PX, ART_SIZE_PX, true)
        val out = Bitmap.createBitmap(ART_SIZE_PX, ART_SIZE_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val r = ART_SIZE_PX * 0.2f
        Canvas(out).drawRoundRect(RectF(0f, 0f, ART_SIZE_PX.toFloat(), ART_SIZE_PX.toFloat()), r, r, paint)
        return out
    }
}

/** Dibuja el widget al colocarlo o redimensionarlo; los botones van directo al servicio de música. */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidget.render(context)
    }
}

/**
 * Vive dentro del servicio de música: escucha al reproductor y mantiene al día lo que muestra el widget.
 * Junta varios eventos seguidos en una sola actualización y no hace nada si no hay ningún widget colocado.
 */
class WidgetUpdater(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: Player? = null
    private var artJob: Job? = null
    private var lastThumb: String? = null
    private val refresh = Runnable { refreshNow() }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED
                )
            ) schedule()
        }
    }

    fun attach(p: Player) {
        player = p
        p.addListener(listener)
        schedule()
    }

    fun release() {
        player?.removeListener(listener)
        player = null
        handler.removeCallbacks(refresh)
        scope.cancel()
        // Sin servicio no suena nada: el widget no debe quedarse con el botón de pausa
        if (PlayerWidget.hasWidgets(context)) {
            PlayerWidget.savePlaying(context, false)
            PlayerWidget.render(context)
        }
    }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 300L)
    }

    private fun refreshNow() {
        val p = player ?: return
        if (!PlayerWidget.hasWidgets(context)) return
        val md = p.currentMediaItem?.mediaMetadata
        val title = md?.title?.toString()
        val artist = md?.artist?.toString().orEmpty()
        val state = p.playbackState
        val playing = p.playWhenReady && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
        val thumb = md?.artworkUri?.toString()

        PlayerWidget.save(context, title, artist, playing)
        if (thumb == lastThumb) {
            PlayerWidget.render(context) // misma portada: solo cambian textos o el botón
            return
        }
        lastThumb = thumb
        artJob?.cancel()
        if (thumb == null) {
            PlayerWidget.saveArt(context, null)
            PlayerWidget.render(context)
            return
        }
        PlayerWidget.render(context) // textos ya; la portada llega en cuanto se descargue
        artJob = scope.launch {
            val bmp = loadArt(thumb)
            PlayerWidget.saveArt(context, bmp)
            withContext(Dispatchers.Main) { PlayerWidget.render(context) }
        }
    }

    private suspend fun loadArt(url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        PlayerWidget.roundedSquare(result.drawable.toBitmap())
    }.getOrNull()
}
