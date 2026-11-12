package com.example.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.PowerPapiloApp
import com.example.R
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.example.download.ACTION_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        observeActiveDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            if (!id.isNullOrBlank()) {
                DownloadManager.cancel(id)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val initialNotification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                serviceType
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun observeActiveDownloads() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            DownloadManager.activeItem.collectLatest { item ->
                if (item != null) {
                    val notification = buildNotification(item)
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    // Verificar si quedan elementos en cola
                    val hasPending = DownloadManager.downloads.value.any { it.status == DownloadStatus.QUEUED }
                    if (!hasPending) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun buildNotification(item: DownloadItem?): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, PowerPapiloApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (item != null) {
            builder.setContentTitle(item.title)
            val etaStr = if (item.etaSeconds > 0) " • ETA: ${FileUtils.formatEta(item.etaSeconds)}" else ""
            val speedStr = if (item.downloadSpeed.isNotBlank()) " • ${item.downloadSpeed}" else ""
            val statusText = when (item.status) {
                DownloadStatus.FETCHING_INFO -> "Obteniendo información..."
                DownloadStatus.DOWNLOADING -> "Descargando: ${item.progress.toInt()}%$speedStr$etaStr"
                DownloadStatus.CONVERTING -> "Procesando y convirtiendo..."
                else -> "Procesando..."
            }
            builder.setContentText(statusText)

            if (item.status == DownloadStatus.DOWNLOADING) {
                builder.setProgress(100, item.progress.toInt().coerceIn(0, 100), false)
            } else {
                builder.setProgress(100, 0, true)
            }

            // Botón de cancelación
            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, item.id)
            }
            val cancelPendingIntent = PendingIntent.getService(
                this,
                item.id.hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPendingIntent)
        } else {
            builder.setContentTitle("POWER PAPILO")
                .setContentText("Preparando descargas...")
                .setProgress(100, 0, true)
        }

        return builder.build()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        super.onDestroy()
    }
}
