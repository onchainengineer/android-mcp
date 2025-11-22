package com.lifecompanion.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

/**
 * Core life events that flow through the system.
 * All raw signals (notifications, SMS, app usage) are normalized into these event types.
 */

@Serializable
sealed class LifeEvent {
    abstract val id: String
    abstract val timestamp: Long
    abstract val metadata: Map<String, String>
}

// Notification Events
@Serializable
data class NotificationReceived(
    override val id: String,
    override val timestamp: Long,
    val app: String,
    val sender: String?,
    val category: NotificationCategory,
    val preview: String,
    val priority: Int,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

@Serializable
data class NotificationInteracted(
    override val id: String,
    override val timestamp: Long,
    val notificationId: String,
    val interaction: InteractionType,
    val durationMs: Long,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

// Communication Events
@Serializable
data class MessageReceived(
    override val id: String,
    override val timestamp: Long,
    val contactId: Long,
    val app: String,
    val preview: String,
    val isGroup: Boolean,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

@Serializable
data class MessageSent(
    override val id: String,
    override val timestamp: Long,
    val contactId: Long,
    val app: String,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

// App Usage Events
@Serializable
data class AppSessionStarted(
    override val id: String,
    override val timestamp: Long,
    val app: String,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

@Serializable
data class AppSessionEnded(
    override val id: String,
    override val timestamp: Long,
    val app: String,
    val durationMs: Long,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

// Financial Events
@Serializable
data class MoneySpent(
    override val id: String,
    override val timestamp: Long,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: SpendingCategory?,
    val source: String,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

@Serializable
data class MoneyReceived(
    override val id: String,
    override val timestamp: Long,
    val amount: Double,
    val currency: String,
    val source: String,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

@Serializable
data class BillDue(
    override val id: String,
    override val timestamp: Long,
    val amount: Double,
    val billType: String,
    val dueDate: Long,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

// User-Generated Events
@Serializable
data class UserNote(
    override val id: String,
    override val timestamp: Long,
    val type: NoteType,
    val refEventId: String?,
    val content: String,
    override val metadata: Map<String, String> = emptyMap()
) : LifeEvent()

// Enums
@Serializable
enum class NotificationCategory {
    MESSAGE, SOCIAL, FINANCE, SHOPPING, PROMOTIONAL, SYSTEM, REMINDER, OTHER
}

@Serializable
enum class InteractionType {
    OPENED, DISMISSED, IGNORED, UNKNOWN
}

@Serializable
enum class SpendingCategory {
    FOOD_DELIVERY, RESTAURANT, TRANSPORT, SHOPPING,
    ENTERTAINMENT, BILLS, GROCERIES, PERSONAL_CARE,
    GIFTS, TRANSFERS, UNKNOWN
}

@Serializable
enum class NoteType {
    REFLECTION, GOAL, PREFERENCE, SPENDING_NOTE, RELATIONSHIP_NOTE
}

// Room Entity for storing events
@Entity(tableName = "events")
data class LifeEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val actorId: Long?,
    val appPackage: String?,
    val dataJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(index = true) val eventType: String,
    @ColumnInfo(index = true) val eventDate: String
)

// Direction for messages
enum class Direction {
    INCOMING, OUTGOING
}
