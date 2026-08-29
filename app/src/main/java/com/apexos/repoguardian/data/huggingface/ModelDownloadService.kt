package com.apexos.repoguardian.data.huggingface

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apexos.repoguardian.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject
    lateinit var modelDownloadManager: ModelDownloadManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "ModelDownloadService"
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.apexos.repoguardian.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.apexos.repoguardian.CANCEL_DOWNLOAD"

        const val EXTRA_MODEL_ID = "extra_model_id"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_MODEL_NAME = "extra_model_name"

        fun startDownload(context: Context, modelId: String, filename: String, modelName: String = filename) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_FILENAME, filename)
                putExtra(EXTRA_MODEL_NAME, modelName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RepoGuardian:ModelDownloadWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: return START_NOT_STICKY
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: filename

                startForegroundDownload(modelId, filename, modelName)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundDownload(modelId: String, filename: String, modelName: String) {
        downloadJob?.cancel()
        wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max

        val initialNotification = buildNotification(
            title = "Downloading $modelName",
            content = "Starting background download...",
            progress = 0,
            indeterminate = true
        )
        startForeground(NOTIFICATION_ID, initialNotification)

        downloadJob = serviceScope.launch {
            try {
                modelDownloadManager.downloadModel(modelId, filename).collect { progress ->
                    if (progress.isComplete) {
                        showCompletedNotification(modelName, filename)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    } else if (progress.error != null) {
                        showErrorNotification(modelName, progress.error)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    } else {
                        val percent = (progress.progressPercent * 100).toInt()
                        val content = "${progress.downloadedFormatted} (${percent}%) • ${progress.speedFormatted} • ${progress.etaFormatted}"
                        val updatedNotification = buildNotification(
                            title = "Downloading $modelName",
                            content = content,
                            progress = percent,
                            indeterminate = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Download job cancelled")
                showCancelledNotification(modelName)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in download service", e)
                showErrorNotification(modelName, e.message ?: "Download failed")
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun cancelCurrentDownload() {
        downloadJob?.cancel()
        modelDownloadManager.cancelActiveDownload()
        wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress for on-device GGUF AI models"
                setShowBadge(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(content)
        .setSmallIcon(com.apexos.repoguardian.R.drawable.ic_notification)
        .setColor(0xFF10B981.toInt())
        .setProgress(100, progress, indeterminate)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(createContentIntent())
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            createCancelPendingIntent()
        )
        .build()

    private fun showCompletedNotification(modelName: String, filename: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("✓ Download Complete")
            .setContentText("$modelName is ready and loaded for AI code review")
            .setSmallIcon(com.apexos.repoguardian.R.drawable.ic_notification)
            .setColor(0xFF10B981.toInt())
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(modelName: String, error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed: $modelName")
            .setContentText(error)
            .setSmallIcon(com.apexos.repoguardian.R.drawable.ic_notification)
            .setColor(0xFFEF4444.toInt())
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .build()
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    private fun showCancelledNotification(modelName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Cancelled")
            .setContentText("Download for $modelName was stopped")
            .setSmallIcon(com.apexos.repoguardian.R.drawable.ic_notification)
            .setColor(0xFF9CA3AF.toInt())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 3, notification)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCancelPendingIntent(): PendingIntent {
        val intent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
