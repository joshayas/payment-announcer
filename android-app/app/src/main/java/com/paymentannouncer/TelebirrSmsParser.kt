package com.paymentannouncer

/**
 * Parses the standard Telebirr "received payment" SMS, sent from short code 127:
 *
 * Dear Dawit
 * You have received ETB 200.00 from bazet asere(2519****2144) on 23/07/2026 16:15:47.
 * Your transaction number is DGN16ERWAR. Your current E-Money Account balance is ETB 206.84.
 * Thank you for using telebirr
 * Ethio telecom
 *
 * Keep this in sync with backend/smsParser.js if Telebirr changes their wording.
 */
data class ParsedPayment(
    val amount: Double,
    val senderName: String,
    val maskedPhone: String?,
    val txnDate: String?,
    val txnId: String?,
    val balanceAfter: Double?,
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

    /** Telebirr sends payment notifications from this short code. */
    const val TELEBIRR_SENDER = "127"
}
