package com.paymentannouncer

object CbeSmsParser {

    // "You have received ETB 1,020.00 from account 1**6573 (Payer Name) to your account ..."
    private val NAMED_REGEX = Regex(
        "received\\s+ETB\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+from\\s+account\\s+\\S+\\s*\\(([^)]+)\\)",
        RegexOption.IGNORE_CASE
    )
    // "Your Account 1****9289 has been credited with ETB 60000.00." (no payer name)
    private val CREDITED_REGEX = Regex(
        "credited\\s+with\\s+ETB\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val BALANCE_REGEX = Regex(
        "balance\\s+is\\s+ETB\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val RECEIPT_REGEX = Regex("BranchReceipt/([A-Z0-9]+)", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedPayment? {
        val balance = BALANCE_REGEX.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        val named = NAMED_REGEX.find(text)
        if (named != null) {
            val amount = named.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            return ParsedPayment(
                amount = amount,
                senderName = named.groupValues[2].trim(),
                maskedPhone = null,
                txnDate = null,
                txnId = null,
                balanceAfter = balance,
                source = "cbe",
                hasName = true,
            )
        }

        val credited = CREDITED_REGEX.find(text) ?: return null
        val amount = credited.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        return ParsedPayment(
            amount = amount,
            senderName = "CBE transfer",
            maskedPhone = null,
            txnDate = null,
            txnId = RECEIPT_REGEX.find(text)?.groupValues?.get(1),
            balanceAfter = balance,
            source = "cbe",
            hasName = false,
        )
    }
}

object PaymentParser {
    // Works out which bank sent the message and parses it. Money going out is ignored.
    fun parse(sender: String?, body: String): ParsedPayment? {
        val from = sender ?: ""
        val looksCbe = from.contains("CBE", ignoreCase = true) ||
            Regex("\\bCBE\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val looksTelebirr = from.contains(TelebirrSmsParser.TELEBIRR_SENDER) ||
            body.contains("telebirr", ignoreCase = true)

        if (looksCbe) return CbeSmsParser.parse(body)
        if (looksTelebirr) return TelebirrSmsParser.parse(body)
        return null
    }
}