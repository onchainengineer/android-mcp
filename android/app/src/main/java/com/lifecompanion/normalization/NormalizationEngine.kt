package com.lifecompanion.normalization

import com.lifecompanion.capture.*
import com.lifecompanion.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Normalization Engine
 * Converts raw OS signals into clean, typed LifeEvents
 */
class NormalizationEngine(private val db: LifeCompanionDatabase) {

    private val json = Json { ignoreUnknownKeys = true }
    private val contactResolver = ContactResolver(db)
    private val merchantExtractor = MerchantExtractor()
    private val categoryClassifier = CategoryClassifier()

    /**
     * Process notification received signal
     */
    suspend fun processNotificationReceived(signal: NotificationSignal) {
        val contact = contactResolver.resolve(signal.packageName, signal.title)
        val category = categoryClassifier.classifyNotification(signal)

        val event = NotificationReceived(
            id = signal.id,
            timestamp = signal.timestamp,
            app = signal.appName,
            sender = contact?.name ?: signal.title,
            category = category,
            preview = signal.text,
            priority = signal.priority,
            metadata = mapOf(
                "package" to signal.packageName,
                "contact_id" to (contact?.id?.toString() ?: ""),
                "can_dismiss" to signal.canDismiss.toString()
            )
        )

        saveEvent(event, contact?.id)

        // If it's a message notification, also create MessageReceived event
        if (category == NotificationCategory.MESSAGE && contact != null) {
            val messageEvent = MessageReceived(
                id = UUID.randomUUID().toString(),
                timestamp = signal.timestamp,
                contactId = contact.id,
                app = signal.appName,
                preview = signal.text,
                isGroup = detectGroupMessage(signal),
                metadata = mapOf("notification_id" to event.id)
            )
            saveEvent(messageEvent, contact.id)
        }
    }

    /**
     * Process notification interaction (opened, dismissed, ignored)
     */
    suspend fun processNotificationInteraction(
        notificationId: String,
        interaction: InteractionType,
        durationMs: Long
    ) {
        val event = NotificationInteracted(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            notificationId = notificationId,
            interaction = interaction,
            durationMs = durationMs,
            metadata = emptyMap()
        )

        saveEvent(event, null)
    }

    /**
     * Process SMS signal
     */
    suspend fun processSms(signal: SmsSignal) {
        when (signal.type) {
            SmsType.TRANSACTION_DEBIT -> {
                val transaction = merchantExtractor.extractDebit(signal.body)
                transaction?.let {
                    val event = MoneySpent(
                        id = UUID.randomUUID().toString(),
                        timestamp = signal.timestamp,
                        amount = it.amount,
                        currency = "INR",
                        merchant = it.merchant,
                        category = categoryClassifier.classifySpending(it.merchant),
                        source = "SMS",
                        metadata = mapOf(
                            "bank" to (it.bank ?: ""),
                            "account_last4" to (it.accountLast4 ?: ""),
                            "raw_sms" to signal.body
                        )
                    )
                    saveEvent(event, null)

                    // Also save to spending table
                    saveSpendingEvent(event, signal.body)
                }
            }

            SmsType.TRANSACTION_CREDIT -> {
                val transaction = merchantExtractor.extractCredit(signal.body)
                transaction?.let {
                    val event = MoneyReceived(
                        id = UUID.randomUUID().toString(),
                        timestamp = signal.timestamp,
                        amount = it.amount,
                        currency = "INR",
                        source = it.merchant,
                        metadata = mapOf(
                            "bank" to (it.bank ?: ""),
                            "raw_sms" to signal.body
                        )
                    )
                    saveEvent(event, null)
                }
            }

            SmsType.BILL_REMINDER -> {
                val bill = merchantExtractor.extractBill(signal.body)
                bill?.let {
                    val event = BillDue(
                        id = UUID.randomUUID().toString(),
                        timestamp = signal.timestamp,
                        amount = it.amount,
                        billType = it.type,
                        dueDate = it.dueDate,
                        metadata = mapOf("raw_sms" to signal.body)
                    )
                    saveEvent(event, null)
                }
            }

            else -> {
                // Other SMS types - could store as generic events
            }
        }
    }

    /**
     * Process app usage signal
     */
    suspend fun processAppUsage(signal: AppUsageSignal) {
        val startEvent = AppSessionStarted(
            id = UUID.randomUUID().toString(),
            timestamp = signal.sessionStart,
            app = signal.appName,
            metadata = mapOf("package" to signal.packageName)
        )
        saveEvent(startEvent, null)

        val endEvent = AppSessionEnded(
            id = UUID.randomUUID().toString(),
            timestamp = signal.sessionEnd,
            app = signal.appName,
            durationMs = signal.durationMs,
            metadata = mapOf(
                "package" to signal.packageName,
                "launch_count" to signal.launchCount.toString()
            )
        )
        saveEvent(endEvent, null)
    }

    private suspend fun saveEvent(event: LifeEvent, actorId: Long?) {
        val entity = LifeEventEntity(
            id = event.id,
            timestamp = event.timestamp,
            type = event::class.simpleName ?: "Unknown",
            actorId = actorId,
            appPackage = event.metadata["package"],
            dataJson = json.encodeToString(event),
            createdAt = System.currentTimeMillis(),
            eventType = event::class.simpleName ?: "Unknown",
            eventDate = formatDate(event.timestamp)
        )

        db.eventDao().insert(entity)
        Timber.d("Saved event: ${entity.type}")
    }

    private suspend fun saveSpendingEvent(event: MoneySpent, rawSms: String) {
        val entity = SpendingEventEntity(
            id = event.id,
            timestamp = event.timestamp,
            amount = event.amount,
            currency = event.currency,
            merchant = event.merchant,
            category = event.category?.name,
            autoCategory = event.category?.name,
            userCategory = null,
            linkedContactId = null,
            userNote = null,
            emotionTag = null,
            source = event.source,
            rawData = rawSms,
            date = formatDate(event.timestamp)
        )

        db.spendingDao().insert(entity)
        Timber.d("Saved spending event: ${event.merchant} - ${event.amount}")
    }

    private fun detectGroupMessage(signal: NotificationSignal): Boolean {
        // Heuristic: group messages often have ":" in the text
        return signal.text.contains(":") && signal.groupKey != null
    }

    private fun formatDate(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}

data class BillInfo(
    val amount: Double,
    val type: String,
    val dueDate: Long
)
