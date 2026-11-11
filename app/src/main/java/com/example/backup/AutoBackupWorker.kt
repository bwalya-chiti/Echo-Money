package com.example.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.AppDatabase
import com.example.data.model.AppSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "echo_money_backup_channel"
        const val NOTIFICATION_ID = 4040
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val settingDao = db.appSettingDao()

        val scheduleSetting = settingDao.getSetting("auto_backup_schedule")?.settingValue ?: "DAILY"
        if (scheduleSetting.equals("DISABLED", ignoreCase = true)) {
            return@withContext Result.success()
        }

        // Retrieve auto-backup passkey or create a persistent device seed
        var backupPass = settingDao.getSetting("auto_backup_passkey")?.settingValue
        if (backupPass.isNullOrBlank()) {
            var seed = settingDao.getSetting("device_auto_backup_seed")?.settingValue
            if (seed.isNullOrBlank()) {
                seed = UUID.randomUUID().toString()
                settingDao.setSetting(AppSetting("device_auto_backup_seed", seed))
            }
            backupPass = "ECHO_AUTO_$seed"
        }

        val backupManager = BackupManager(context)
        val result = backupManager.createBackup(password = backupPass, isAuto = true)

        if (result.isSuccess) {
            val file = result.getOrNull()
            val now = System.currentTimeMillis()
            settingDao.setSetting(AppSetting("last_auto_backup_timestamp", now.toString()))
            settingDao.setSetting(AppSetting("last_auto_backup_status", "SUCCESS"))
            if (file != null) {
                settingDao.setSetting(AppSetting("last_auto_backup_size", file.length().toString()))
            }
            Result.success()
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            settingDao.setSetting(AppSetting("last_auto_backup_status", "FAILED: $error"))
            postFailureNotification(error)
            Result.retry()
        }
    }

    private fun postFailureNotification(errorMessage: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Echo Money Backups",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts regarding automated financial backups"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Echo Money: Auto Backup Failed")
                .setContentText("Automated financial backup could not complete: $errorMessage")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Notification failed (e.g. permission denied)
        }
    }
}
