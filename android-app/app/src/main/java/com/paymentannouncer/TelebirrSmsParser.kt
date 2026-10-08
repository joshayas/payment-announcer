package com.paymentannouncer

data class ParsedPayment(
    val amount: Double,
    val senderName: String,
    val maskedPhone: String?,
    val txnDate: String?,
    val txnId: String?,
    val balanceAfter: Double?,
    val source: String = "telebirr", // "telebirr" or "cbe"
    val hasName: Boolean = true,     // false when the bank message has no payer name
)

object TelebirrSmsParser {

    private val AMOUNT_REGEX = Regex("received\\s+ETB\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val NAME_REGEX = Regex("from\\s+([^(]+?)\\s*\\(", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("\\((\\d{2,4}\\*{2,}\\d{2,4})\\)")
    private val DATE_REGEX = Regex("on\\s+([\\d/]+\\s+[\\d:]+)", RegexOption.IGNORE_CASE)
    private val TXN_REGEX = Regex("transaction number is\\s+([A-Z0-9]+)", RegexOption.IGNORE_CASE)
    private val BALANCE_REGEX = Regex("balance is\\s*ETB\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedPayment? {
        val amountMatch = AMOUNT_REGEX.find(rawText) ?: return null
        val nameMatch = NAME_REGEX.find(rawText) ?: return null

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val name = nameMatch.groupValues[1].trim()

        return ParsedPayment(
            amount = amount,
            senderName = name,
            maskedPhone = PHONE_REGEX.find(rawText)?.groupValues?.get(1),
            txnDate = DATE_REGEX.find(rawText)?.groupValues?.get(1),
            txnId = TXN_REGEX.find(rawText)?.groupValues?.get(1),
            balanceAfter = BALANCE_REGEX.find(rawText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull(),
        )
    }

    const val TELEBIRR_SENDER = "127"
}