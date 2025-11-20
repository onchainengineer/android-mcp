package com.lifecompanion.data

import androidx.room.*

/**
 * Database entities for Life Companion
 */

// ==================== CONTACTS ====================

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String?,
    val email: String?,
    val photoUri: String?,

    // Manual tags
    val userTag: String?,
    val userPriority: Int,
    val userNotes: String?,

    val createdAt: Long,
    val lastInteractionAt: Long?
)

@Entity(
    tableName = "contact_handles",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("contact_id"), Index("app", "handle")]
)
data class ContactHandleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_id") val contactId: Long,
    val app: String,
    val handle: String,
    val verifiedAt: Long?
)

// ==================== RELATIONSHIP PROFILES ====================

@Entity(
    tableName = "relationship_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RelationshipProfileEntity(
    @PrimaryKey @ColumnInfo(name = "contact_id") val contactId: Long,

    // Scores (0.0 - 1.0)
    val priorityScore: Float,
    val reciprocityScore: Float,
    val responseSpeedScore: Float,
    val emotionalClosenessScore: Float,

    // Statistics
    val totalMessagesReceived: Int,
    val totalMessagesSent: Int,
    val totalCallsReceived: Int,
    val totalCallsMade: Int,

    val avgResponseTimeMs: Long,
    val avgInitiationTimeMs: Long,

    val lastMessageFromThem: Long?,
    val lastMessageFromUs: Long?,
    val lastCallFromThem: Long?,
    val lastCallFromUs: Long?,

    // Pattern flags
    val lateNightChatsCount: Int,
    val immediateRepliesCount: Int,
    val ignoredCount: Int,
    val neglectedByUsCount: Int,

    // Derived insights
    val relationshipType: String?,
    val lastUpdated: Long
)

// ==================== SPENDING ====================

@Entity(tableName = "spending_events")
data class SpendingEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val amount: Double,
    val currency: String,
    val merchant: String,

    val category: String?,
    val autoCategory: String?,
    val userCategory: String?,

    val linkedContactId: Long?,
    val userNote: String?,
    val emotionTag: String?,

    val source: String,
    val rawData: String?,

    @ColumnInfo(index = true) val date: String
)

// ==================== SETTINGS ====================

@Entity(tableName = "user_settings")
data class UserSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val type: String,
    val updatedAt: Long
)

// ==================== MEMORIES ====================

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val content: String,
    val linkedEventIds: String?,
    val tags: String?
)

// ==================== SUGGESTIONS ====================

@Entity(tableName = "suggestions")
data class SuggestionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val title: String,
    val body: String,
    val actionType: String,
    val actionData: String,
    val priority: Int,
    val status: String, // "pending", "accepted", "dismissed"
    val createdAt: Long
)

// ==================== AUDIT LOG ====================

@Entity(tableName = "audit_log")
data class AuditLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: String,
    val actor: String,
    val targetType: String,
    val targetId: String,
    val details: String?
)

// ==================== DATA CLASSES (not entities) ====================

data class ContactWithProfile(
    @Embedded val contact: ContactEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "contact_id"
    )
    val profile: RelationshipProfileEntity?
)

data class ContactWithHandles(
    @Embedded val contact: ContactEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "contact_id"
    )
    val handles: List<ContactHandleEntity>
)

// Transaction data extracted from SMS
data class TransactionData(
    val amount: Double,
    val merchant: String,
    val bank: String?,
    val accountLast4: String?
)

// Spending insights
data class SpendingInsights(
    val categoryBreakdown: List<Pair<String?, Double>>,
    val lateNightCount: Int,
    val lateNightTotal: Double,
    val impulsiveCount: Int,
    val impulsiveTotal: Double,
    val recurringMerchants: Map<String, Pair<Int, Double>>,
    val emotionalBreakdown: Map<String, Double>
)

data class SpendingAnomaly(
    val event: SpendingEventEntity,
    val type: AnomalyType,
    val expectedRange: ClosedRange<Double>,
    val actualAmount: Double
)

enum class AnomalyType {
    UNUSUALLY_HIGH, UNUSUALLY_FREQUENT, SUSPICIOUS_MERCHANT
}

// Notification analysis
data class NotificationAnalysis(
    val topNoiseApps: List<Pair<String, Float>>,
    val avgNoiseScore: Float,
    val recommendedMutes: List<String>
)

// App usage data
data class AppUsageData(
    val name: String,
    val packageName: String,
    val durationMinutes: Long,
    val launchCount: Int,
    val category: String?,
    val iconUrl: String?
)

// Suggestion types
enum class SuggestionType {
    RELATIONSHIP_PRIORITY,
    RELATIONSHIP_INSIGHT,
    RELATIONSHIP_NUDGE,
    NOTIFICATION_FILTER,
    SPENDING_INSIGHT,
    SPENDING_ANOMALY
}

sealed class SuggestionAction {
    data class TagContact(val contactId: Long, val tag: String) : SuggestionAction()
    data class Reflect(val contactId: Long) : SuggestionAction()
    data class OpenChat(val contactId: Long, val app: String) : SuggestionAction()
    data class ConfigureNotifications(
        val appPackage: String,
        val suggestedAction: String
    ) : SuggestionAction()
    object ViewSpendingReport : SuggestionAction()
    data class TagSpending(val eventId: String) : SuggestionAction()
}

data class Suggestion(
    val id: String,
    val timestamp: Long,
    val type: SuggestionType,
    val title: String,
    val body: String,
    val action: SuggestionAction,
    val priority: Int
)
