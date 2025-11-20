package com.lifecompanion.normalization

import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Extracts transaction details from SMS messages.
 * Supports Indian banks and payment apps.
 */
class MerchantExtractor {

    private val dateFormatter = DateTimeFormatter.ofPattern("ddMMyy")

    /**
     * Extract debit transaction from SMS
     */
    fun extractDebit(sms: String): TransactionData? {
        // Pattern 1: Rs.XXX debited from A/c XXXX at MERCHANT
        val pattern1 = Regex(
            "(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)\\s+debited.*?(?:from|at)\\s+(.+?)(?:\\s+on|\\.|$)",
            RegexOption.IGNORE_CASE
        )
        pattern1.find(sms)?.let { match ->
            val amount = parseAmount(match.groupValues[1])
            val merchant = extractMerchantName(match.groupValues[2])
            return TransactionData(
                amount = amount,
                merchant = merchant,
                bank = extractBank(sms),
                accountLast4 = extractAccountLast4(sms)
            )
        }

        // Pattern 2: A/c XXXX debited with Rs.XXX on DATE at MERCHANT
        val pattern2 = Regex(
            "(?:A/c|Card).*?(?:XX|\\d{4}).*?debited.*?(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*).*?(?:at|for)\\s+(.+?)(?:\\.|$)",
            RegexOption.IGNORE_CASE
        )
        pattern2.find(sms)?.let { match ->
            val amount = parseAmount(match.groupValues[1])
            val merchant = extractMerchantName(match.groupValues[2])
            return TransactionData(
                amount = amount,
                merchant = merchant,
                bank = extractBank(sms),
                accountLast4 = extractAccountLast4(sms)
            )
        }

        // Pattern 3: Generic fallback
        val amountMatch = Regex("(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
            .find(sms)
        if (amountMatch != null && sms.contains("debit", ignoreCase = true)) {
            return TransactionData(
                amount = parseAmount(amountMatch.groupValues[1]),
                merchant = "Unknown",
                bank = extractBank(sms),
                accountLast4 = extractAccountLast4(sms)
            )
        }

        return null
    }

    /**
     * Extract credit transaction from SMS
     */
    fun extractCredit(sms: String): TransactionData? {
        val pattern = Regex(
            "(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)\\s+credited.*?(?:by|from)\\s+(.+?)(?:\\.|$)",
            RegexOption.IGNORE_CASE
        )
        pattern.find(sms)?.let { match ->
            val amount = parseAmount(match.groupValues[1])
            val source = match.groupValues[2].trim()
            return TransactionData(
                amount = amount,
                merchant = source,
                bank = extractBank(sms),
                accountLast4 = extractAccountLast4(sms)
            )
        }

        // Salary pattern
        if (sms.contains("salary", ignoreCase = true) && sms.contains("credit", ignoreCase = true)) {
            val amountMatch = Regex("(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
                .find(sms)
            amountMatch?.let {
                return TransactionData(
                    amount = parseAmount(it.groupValues[1]),
                    merchant = "Salary",
                    bank = extractBank(sms),
                    accountLast4 = extractAccountLast4(sms)
                )
            }
        }

        return null
    }

    /**
     * Extract bill reminder from SMS
     */
    fun extractBill(sms: String): BillInfo? {
        val amountPattern = Regex("(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)
        val dueDatePattern = Regex("due.*?(\\d{2}[-/]\\d{2}[-/]\\d{2,4})", RegexOption.IGNORE_CASE)

        val amount = amountPattern.find(sms)?.let { parseAmount(it.groupValues[1]) } ?: return null
        val dueDateStr = dueDatePattern.find(sms)?.groupValues?.get(1) ?: return null

        val billType = when {
            sms.contains("electricity", ignoreCase = true) -> "electricity"
            sms.contains("credit card", ignoreCase = true) -> "credit_card"
            sms.contains("mobile", ignoreCase = true) -> "mobile"
            sms.contains("internet", ignoreCase = true) -> "internet"
            else -> "other"
        }

        return BillInfo(
            amount = amount,
            type = billType,
            dueDate = parseDueDate(dueDateStr)
        )
    }

    private fun parseAmount(amountStr: String): Double {
        return amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    private fun extractMerchantName(raw: String): String {
        // Clean up common patterns
        var merchant = raw.trim()

        // Remove account/card references
        merchant = merchant.replace(Regex("(?:A/c|Card).*?(?:XX|\\d{4})"), "")
        merchant = merchant.replace(Regex("on \\d{2}"), "")

        // Remove dates
        merchant = merchant.replace(Regex("\\d{2}[-/]\\d{2}[-/]\\d{2,4}"), "")

        // Capitalize known merchants
        val knownMerchants = mapOf(
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "uber" to "Uber",
            "ola" to "Ola"
        )

        for ((key, value) in knownMerchants) {
            if (merchant.contains(key, ignoreCase = true)) {
                return value
            }
        }

        return merchant.trim().take(50)
    }

    private fun extractBank(sms: String): String? {
        val banks = listOf("HDFC", "ICICI", "SBI", "AXIS", "Kotak", "HSBC", "Citi", "YES", "IndusInd")
        return banks.firstOrNull { sms.contains(it, ignoreCase = true) }
    }

    private fun extractAccountLast4(sms: String): String? {
        val pattern = Regex("(?:XX|\\*{2,4})(\\d{4})")
        return pattern.find(sms)?.groupValues?.get(1)
    }

    private fun parseDueDate(dateStr: String): Long {
        return try {
            val normalized = dateStr.replace("/", "-")
            val parts = normalized.split("-")

            val day = parts[0].toInt()
            val month = parts[1].toInt()
            val year = if (parts[2].length == 2) {
                2000 + parts[2].toInt()
            } else {
                parts[2].toInt()
            }

            LocalDate.of(year, month, day)
                .atStartOfDay()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            Timber.e(e, "Error parsing due date: $dateStr")
            System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000) // Default: 7 days from now
        }
    }
}
