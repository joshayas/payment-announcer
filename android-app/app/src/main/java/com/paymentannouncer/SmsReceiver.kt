package com.paymentannouncer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "onReceive fired, action=${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d("SmsReceiver", "Ignored: wrong action")
            return
        }
        if (!SessionManager.isLoggedIn(context)) {
            Log.d("SmsReceiver", "Ignored: no driver logged in")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages.firstOrNull()?.originatingAddress ?: return

        Log.d("SmsReceiver", "Sender=$sender Body=$fullBody")

        if (!sender.contains(TelebirrSmsParser.TELEBIRR_SENDER)) {
            Log.d("SmsReceiver", "Ignored: sender does not contain '127'")
            return
        }

        val parsed = TelebirrSmsParser.parse(fullBody)
        if (parsed == null) {
            Log.d("SmsReceiver", "Ignored: parser returned null for this body")
            return
        }
        Log.d("SmsReceiver", "Parsed OK: $parsed")

        PaymentEventBus.post(parsed)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.api.logPayment(SessionManager.bearer(context), PaymentLogRequest(fullBody))
            } catch (e: Exception) {
                Log.d("SmsReceiver", "Backend logging failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
