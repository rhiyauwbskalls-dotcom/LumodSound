package com.sonaris.app.player

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.sonaris.app.R

/** Servicio en primer plano que mantiene vivas las descargas y muestra su progreso. */
@OptIn(UnstableApi::class)
class SonarisDownloadService : DownloadService(
    NOTIFICATION_ID,
    1000L,
    CHANNEL_ID,
    R.string.download_channel,
    0
) {
    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)

    // Sin planificador: las descargas pendientes se retoman al volver a abrir la app
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val message = if (downloads.size == 1) {
            Util.fromUtf8Bytes(downloads[0].request.data)
        } else {
            "${downloads.size} canciones"
        }
        return DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            message,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2001
    }
}
