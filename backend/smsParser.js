// Parses the standard Telebirr "received payment" SMS, sent from short code 127:
//
// Dear Dawit
// You have received ETB 200.00 from bazet asere(2519****2144) on 23/07/2026 16:15:47.
// Your transaction number is DGN16ERWAR. Your current E-Money Account balance is ETB 206.84.
// Thank you for using telebirr
// Ethio telecom
//
// The Android app runs the equivalent regex logic in SmsReceiver.kt; keep the two in sync
// if Telebirr ever changes their message wording.

function parseTelebirrSms(rawText) {
  if (!rawText || typeof rawText !== 'string') return null;

  const amountMatch = rawText.match(/received\s+ETB\s*([\d,]+(?:\.\d{1,2})?)/i);
  const nameMatch = rawText.match(/from\s+([^(]+?)\s*\(/i);
  const phoneMatch = rawText.match(/\((\d{2,4}\*{2,}\d{2,4})\)/);
  const dateMatch = rawText.match(/on\s+([\d/]+\s+[\d:]+)/i);
  const txnMatch = rawText.match(/transaction number is\s+([A-Z0-9]+)/i);
  const balanceMatch = rawText.match(/balance is\s*ETB\s*([\d,]+(?:\.\d{1,2})?)/i);

  if (!amountMatch || !nameMatch) return null;

  return {
    amount: parseFloat(amountMatch[1].replace(/,/g, '')),
    senderName: nameMatch[1].trim(),
    maskedPhone: phoneMatch ? phoneMatch[1] : null,
    txnDate: dateMatch ? dateMatch[1] : null,
    txnId: txnMatch ? txnMatch[1] : null,
    balanceAfter: balanceMatch ? parseFloat(balanceMatch[1].replace(/,/g, '')) : null,
  };
}

module.exports = { parseTelebirrSms };
