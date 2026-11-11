package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.EchoMoneyApplication
import com.example.data.repository.IngestionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = StringBuilder()
        var sender: String? = null
        for (msg in messages) {
            sender = msg.displayOriginatingAddress ?: msg.originatingAddress
            fullBody.append(msg.displayMessageBody ?: msg.messageBody)
        }

        val bodyText = fullBody.toString()
        if (bodyText.isBlank()) return

        Log.d("SmsReceiver", "Received SMS from: $sender: $bodyText")

        val pendingResult = goAsync()
        val app = context.applicationContext as? EchoMoneyApplication
        val repository = app?.repository ?: com.example.data.repository.TransactionRepository(
            com.example.data.AppDatabase.getDatabase(context.applicationContext)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.processIncomingSms(sender, bodyText)
                when (result) {
                    is IngestionResult.Success -> {
                        val t = result.transaction
                        val title = if (result.isInternalTransfer) {
                            "Linked Internal Transfer: ZMW ${"%.2f".format(t.amount)}"
                        } else {
                            "${t.provider} ${t.categoryName}: ZMW ${"%.2f".format(t.amount)}"
                        }
                        val desc = t.recipient?.let { "To: $it" } ?: t.sender?.let { "From: $it" } ?: t.description ?: "Auto-recorded"
                        NotificationHelper.showTransactionNotification(context, title, desc)
                    }
                    is IngestionResult.ReviewNeeded -> {
                        val t = result.transaction
                        NotificationHelper.showTransactionNotification(
                            context,
                            "Review Transaction: ZMW ${"%.2f".format(t.amount)}",
                            "Uncertain parsing. Tap to review in Echo Money."
                        )
                    }
                    is IngestionResult.Merged -> {
                        Log.d("SmsReceiver", "Merged duplicate SMS: ${result.reason}")
                    }
                    is IngestionResult.BalanceUpdated -> {
                        Log.d("SmsReceiver", "Balance updated for ${result.provider}: ${result.newBalance}")
                    }
                    is IngestionResult.Duplicate -> {
                        Log.d("SmsReceiver", "Duplicate SMS ignored: ${result.reason}")
                    }
                    is IngestionResult.Failed -> {
                        Log.d("SmsReceiver", "SMS parsing failed: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
