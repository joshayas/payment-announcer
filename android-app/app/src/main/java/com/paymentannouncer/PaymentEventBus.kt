package com.paymentannouncer

/**
 * SmsReceiver runs as a short-lived broadcast receiver and can't hold a reference
 * to an Activity directly. PaymentActivity registers itself here while it's on screen;
 * SmsReceiver posts through it whenever it parses a matching Telebirr SMS.
 */
object PaymentEventBus {
    private var listener: ((ParsedPayment) -> Unit)? = null

    fun setListener(l: ((ParsedPayment) -> Unit)?) {
        listener = l
    }

    fun post(payment: ParsedPayment) {
        listener?.invoke(payment)
    }
}
