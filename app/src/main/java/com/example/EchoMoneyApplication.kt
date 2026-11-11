package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.repository.TransactionRepository
import com.example.sms.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EchoMoneyApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { TransactionRepository(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.createNotificationChannel(this)
    }

    companion object {
        lateinit var instance: EchoMoneyApplication
            private set
    }
}
