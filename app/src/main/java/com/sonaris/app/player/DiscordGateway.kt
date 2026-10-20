package com.sonaris.app.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Conexión mínima al gateway de Discord: HELLO → latidos → IDENTIFY → READY.
 * Una vez lista ([ready]), se le envían actualizaciones de presencia con [send].
 * Si la conexión se cae sin que la hayamos cerrado nosotros, avisa con [onClosed].
 */
class DiscordGateway(
    private val appId: String,
    private val scope: CoroutineScope,
    private val onClosed: (code: Int, reason: String) -> Unit
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var ws: WebSocket? = null
    @Volatile var ready = false
        private set
    @Volatile private var closedByUs = false
    @Volatile private var seq: Int? = null
    private var heartbeat: Job? = null
    private val readySignal = CompletableDeferred<Boolean>()

    /** Abre la conexión y espera al READY. Devuelve false si no se logró. */
    suspend fun connect(authorization: String): Boolean {
        ws = http.newWebSocket(Request.Builder().url(URL).build(), Listener(authorization))
        return withTimeoutOrNull(15_000) { readySignal.await() } == true
    }

    fun send(json: String): Boolean = if (ready) ws?.send(json) ?: false else false

    fun close() {
        closedByUs = true
        ready = false
        heartbeat?.cancel()
        runCatching { ws?.close(1000, null) }
        ws = null
        readySignal.complete(false)
        runCatching { http.dispatcher.executorService.shutdown() }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            delay((intervalMs * Random.nextDouble()).toLong()) // el primer latido va con retraso aleatorio
            while (isActive) {
                sendHeartbeat()
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat() {
        val d = seq?.let { it as Any } ?: JSONObject.NULL
        ws?.send(JSONObject().put("op", OP_HEARTBEAT).put("d", d).toString())
    }

    private fun identify(authorization: String) {
        val props = JSONObject()
            .put("os", "android")
            .put("browser", "Discord Android")
            .put("device", appId)
        val d = JSONObject()
            .put("token", authorization)
            .put("intents", 0)
            .put("properties", props)
            .put("compress", false)
        ws?.send(JSONObject().put("op", OP_IDENTIFY).put("d", d).toString())
    }

    private fun ended(code: Int, reason: String) {
        val wasClosedByUs = closedByUs
        ready = false
        heartbeat?.cancel()
        readySignal.complete(false)
        if (!wasClosedByUs) onClosed(code, reason)
    }

    private inner class Listener(private val authorization: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (!json.isNull("s")) seq = json.optInt("s")
            when (json.optInt("op", -1)) {
                OP_HELLO -> {
                    val interval = json.optJSONObject("d")?.optLong("heartbeat_interval", 41_250L) ?: 41_250L
                    startHeartbeat(interval)
                    identify(authorization)
                }
                OP_HEARTBEAT -> sendHeartbeat()
                OP_DISPATCH -> if (json.optString("t") == "READY") {
                    ready = true
                    readySignal.complete(true)
                }
                OP_RECONNECT, OP_INVALID_SESSION -> webSocket.close(4000, "reinicio pedido por Discord")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            runCatching { webSocket.close(1000, null) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = ended(code, reason)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            ended(response?.code ?: 4000, t.message ?: "fallo de conexión")
    }

    private companion object {
        const val URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        const val OP_DISPATCH = 0
        const val OP_HEARTBEAT = 1
        const val OP_IDENTIFY = 2
        const val OP_RECONNECT = 7
        const val OP_INVALID_SESSION = 9
        const val OP_HELLO = 10
    }
}
