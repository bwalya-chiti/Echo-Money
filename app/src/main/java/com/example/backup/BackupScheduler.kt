package com.example.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {

    const val WORK_NAME_PERIODIC = "echo_money_auto_backup"
    const val WORK_NAME_IMMEDIATE = "echo_money_immediate_backup"

    fun scheduleAutoBackup(context: Context, frequency: String) {
        val workManager = WorkManager.getInstance(context)

        if (frequency.equals("DISABLED", ignoreCase = true)) {
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            return
        }

        val (repeatInterval, timeUnit) = when (frequency.uppercase()) {
            "DAILY" -> 1L to TimeUnit.DAYS
            "WEEKLY" -> 7L to TimeUnit.DAYS
            "MONTHLY" -> 30L to TimeUnit.DAYS
            else -> 1L to TimeUnit.DAYS
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            repeatInterval, timeUnit
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    fun triggerImmediateAutoBackup(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
    }
}
