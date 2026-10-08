function toNumber(text) {
  return parseFloat(String(text).replace(/,/g, ''));
}

function parseTelebirr(rawText) {
  const amountMatch = rawText.match(/received\s+ETB\s*([\d,]+(?:\.\d{1,2})?)/i);
  const nameMatch = rawText.match(/from\s+([^(]+?)\s*\(/i);
  const phoneMatch = rawText.match(/\((\d{2,4}\*{2,}\d{2,4})\)/);
  const dateMatch = rawText.match(/on\s+([\d/]+\s+[\d:]+)/i);
  const txnMatch = rawText.match(/transaction number is\s+([A-Z0-9]+)/i);
  const balanceMatch = rawText.match(/balance is\s*ETB\s*([\d,]+(?:\.\d{1,2})?)/i);

  if (!amountMatch || !nameMatch) return null;

  return {
    source: 'telebirr',
    amount: toNumber(amountMatch[1]),
    senderName: nameMatch[1].trim(),
    maskedPhone: phoneMatch ? phoneMatch[1] : null,
    txnDate: dateMatch ? dateMatch[1] : null,
    txnId: txnMatch ? txnMatch[1] : null,
    balanceAfter: balanceMatch ? toNumber(balanceMatch[1]) : null,
  };
}

function parseCbe(rawText) {
  const balanceMatch = rawText.match(/balance\s+is\s+ETB\s*([\d,]+(?:\.\d{1,2})?)/i);
  const balanceAfter = balanceMatch ? toNumber(balanceMatch[1]) : null;

  // "You have received ETB 1,020.00 from account 1**6573 (Payer Name) to your account ..."
  const named = rawText.match(/received\s+ETB\s*([\d,]+(?:\.\d{1,2})?)\s+from\s+account\s+\S+\s*\(([^)]+)\)/i);
  if (named) {
    return {
      source: 'cbe',
      amount: toNumber(named[1]),
      senderName: named[2].trim(),
      maskedPhone: null,
      txnDate: null,
      txnId: null,
      balanceAfter,
    };
  }

  // "Your Account 1****9289 has been credited with ETB 60000.00." (no payer name)
  const credited = rawText.match(/credited\s+with\s+ETB\s*([\d,]+(?:\.\d{1,2})?)/i);
  if (credited) {
    const receiptMatch = rawText.match(/BranchReceipt\/([A-Z0-9]+)/i);
    return {
      source: 'cbe',
      amount: toNumber(credited[1]),
      senderName: 'CBE transfer',
      maskedPhone: null,
      txnDate: null,
      txnId: receiptMatch ? receiptMatch[1] : null,
      balanceAfter,
    };
  }

  return null;
}

function parsePaymentSms(rawText) {
  if (!rawText || typeof rawText !== 'string') return null;
  if (/\bCBE\b/i.test(rawText)) return parseCbe(rawText);
  return parseTelebirr(rawText);
}

// server.js still imports the old name, so keep it as an alias.
module.exports = { parsePaymentSms, parseTelebirrSms: parsePaymentSms };