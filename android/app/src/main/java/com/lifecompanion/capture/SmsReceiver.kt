package com.lifecompanion.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lifecompanion.data.LifeCompanionDatabase
import com.lifecompanion.normalization.NormalizationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Captures incoming SMS messages, particularly for transaction detection.
 * Classifies SMS into: transactions (debit/credit), OTP, bills, promotional, personal.
 */
class SmsCaptureReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val db = LifeCompanionDatabase.getInstance(context)
        val normalizationEngine = NormalizationEngine(db)

        scope.launch {
            for (message in messages) {
                try {
                    val signal = SmsSignal(
                        timestamp = message.timestampMillis,
                        sender = message.displayOriginatingAddress,
                        body = message.messageBody,
                        type = classifySms(message.messageBody)
                    )

                    Timber.d("SMS received from ${signal.sender}: ${signal.type}")
                    normalizationEngine.processSms(signal)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing SMS")
                }
            }
        }
    }

    private fun classifySms(body: String): SmsType {
        val lowerBody = body.lowercase()

        return when {
            // Transaction patterns
            isDebitTransaction(body) -> SmsType.TRANSACTION_DEBIT
            isCreditTransaction(body) -> SmsType.TRANSACTION_CREDIT

            // OTP pattern
            lowerBody.contains("otp") ||
                    lowerBody.matches(Regex(".*\\b\\d{4,6}\\b.*")) &&
                    (lowerBody.contains("code") || lowerBody.contains("verification"))
            -> SmsType.OTP

            // Bill reminder
            lowerBody.contains("bill") && lowerBody.contains("due")
            -> SmsType.BILL_REMINDER

            // Promotional (common keywords)
            lowerBody.contains("offer") ||
                    lowerBody.contains("sale") ||
                    lowerBody.contains("discount") ||
                    lowerBody.contains("promo")
            -> SmsType.PROMOTIONAL

            // Default
            else -> SmsType.UNKNOWN
        }
    }

    private fun isDebitTransaction(body: String): Boolean {
        val patterns = listOf(
            Regex("(?:Rs\\.?|INR)\\s*[\\d,]+\\.?\\d*\\s+(?:debited|withdrawn)", RegexOption.IGNORE_CASE),
            Regex("(?:debited|withdrawn).*?(?:Rs\\.?|INR)\\s*[\\d,]+\\.?\\d*", RegexOption.IGNORE_CASE),
            Regex("Your a/c.*?(?:debited|withdrawn).*?Rs", RegexOption.IGNORE_CASE),
            Regex("Card.*?(?:XX|\\d{4}).*?Rs.*?(?:debited|spent)", RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(body) }
    }

    private fun isCreditTransaction(body: String): Boolean {
        val patterns = listOf(
            Regex("(?:Rs\\.?|INR)\\s*[\\d,]+\\.?\\d*\\s+credited", RegexOption.IGNORE_CASE),
            Regex("credited.*?(?:Rs\\.?|INR)\\s*[\\d,]+\\.?\\d*", RegexOption.IGNORE_CASE),
            Regex("salary.*?credited", RegexOption.IGNORE_CASE),
            Regex("received.*?(?:Rs\\.?|INR)\\s*[\\d,]+", RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(body) }
    }
}

data class SmsSignal(
    val timestamp: Long,
    val sender: String,
    val body: String,
    val type: SmsType
)

enum class SmsType {
    TRANSACTION_DEBIT,
    TRANSACTION_CREDIT,
    OTP,
    BILL_REMINDER,
    PROMOTIONAL,
    PERSONAL,
    UNKNOWN
}
