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
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SessionManager.isLoggedIn(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages.firstOrNull()?.originatingAddress ?: return

        val parsed = PaymentParser.parse(sender, fullBody)
        if (parsed == null) {
            Log.d("SmsReceiver", "Ignored message from $sender (not a received-payment message)")
            return
        }

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