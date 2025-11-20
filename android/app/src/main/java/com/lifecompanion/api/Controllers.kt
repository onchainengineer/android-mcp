package com.lifecompanion.api

import com.lifecompanion.data.*
import com.lifecompanion.intelligence.*
import kotlinx.serialization.Serializable

/**
 * API Controllers implementing business logic
 */

class RelationshipController(private val db: LifeCompanionDatabase) {
    private val scoringEngine = RelationshipScoringEngine(db)

    suspend fun getInsights(params: RelationshipInsightsParams): Map<String, Any> {
        val contacts = when (params.filter) {
            "priority" -> db.relationshipDao().getTopPriorityContacts(params.limit ?: 10)
            "weak" -> db.relationshipDao().getOneSidedRelationships()
            "neglected" -> db.relationshipDao().getNeglectedHighPriorityContacts(
                threshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000,
                limit = params.limit ?: 10
            )
            else -> db.relationshipDao().getContacts().take(params.limit ?: 10)
        }

        return mapOf(
            "filter" to params.filter,
            "count" to contacts.size,
            "contacts" to contacts.map { serializeContactWithProfile(it) }
        )
    }

    private fun serializeContactWithProfile(cwp: ContactWithProfile): Map<String, Any?> {
        return mapOf(
            "id" to cwp.contact.id,
            "name" to cwp.contact.name,
            "user_tag" to cwp.contact.userTag,
            "priority_score" to cwp.profile?.priorityScore,
            "reciprocity_score" to cwp.profile?.reciprocityScore,
            "response_speed_score" to cwp.profile?.responseSpeedScore,
            "emotional_closeness_score" to cwp.profile?.emotionalClosenessScore,
            "total_messages_received" to cwp.profile?.totalMessagesReceived,
            "total_messages_sent" to cwp.profile?.totalMessagesSent,
            "relationship_type" to cwp.profile?.relationshipType
        )
    }
}

class ContactController(private val db: LifeCompanionDatabase) {
    suspend fun getProfile(contactId: Long): Map<String, Any?> {
        val contact = db.contactDao().getWithProfile(contactId)
            ?: return mapOf("error" to "Contact not found")

        return mapOf(
            "id" to contact.contact.id,
            "name" to contact.contact.name,
            "phone" to contact.contact.phone,
            "user_tag" to contact.contact.userTag,
            "user_priority" to contact.contact.userPriority,
            "user_notes" to contact.contact.userNotes,
            "profile" to contact.profile?.let {
                mapOf(
                    "priority_score" to it.priorityScore,
                    "reciprocity_score" to it.reciprocityScore,
                    "response_speed_score" to it.responseSpeedScore,
                    "emotional_closeness_score" to it.emotionalClosenessScore,
                    "total_messages_received" to it.totalMessagesReceived,
                    "total_messages_sent" to it.totalMessagesSent,
                    "avg_response_time_ms" to it.avgResponseTimeMs,
                    "last_message_from_them" to it.lastMessageFromThem,
                    "last_message_from_us" to it.lastMessageFromUs,
                    "late_night_chats_count" to it.lateNightChatsCount,
                    "immediate_replies_count" to it.immediateRepliesCount,
                    "relationship_type" to it.relationshipType
                )
            }
        )
    }

    suspend fun tagContact(params: TagContactParams) {
        params.tag?.let { tag ->
            db.contactDao().updateTag(params.contact_id, tag, params.priority ?: 5)
        }
        params.notes?.let { notes ->
            db.contactDao().updateNotes(params.contact_id, notes)
        }
    }
}

class NotificationController(private val db: LifeCompanionDatabase) {
    private val noiseEngine = NotificationNoiseEngine(db)

    suspend fun getStats(params: NotificationStatsParams): Map<String, Any> {
        val (start, end) = getTimeRange(params.time_range ?: "week")
        val events = db.eventDao().getEventsByType("NotificationReceived", start, end)

        return mapOf(
            "time_range" to params.time_range,
            "total" to events.size,
            "by_app" to events.groupBy { it.appPackage }.mapValues { it.value.size }
        )
    }

    suspend fun getNoisyApps(params: NoisyAppsParams): Map<String, Any> {
        val analysis = noiseEngine.analyzeNotificationPatterns()
        val filtered = analysis.topNoiseApps.filter { it.second >= (params.min_noise_score ?: 0.7f) }
            .take(params.limit ?: 10)

        return mapOf(
            "noisy_apps" to filtered.map {
                mapOf("app_package" to it.first, "noise_score" to it.second)
            },
            "avg_noise_score" to analysis.avgNoiseScore
        )
    }

    suspend fun configure(params: ConfigureNotificationsParams) {
        val key = when (params.action) {
            "mute" -> "muted_apps"
            "never_filter" -> "never_filter_apps"
            else -> return
        }

        // Update settings (simplified)
        db.settingsDao().insert(
            UserSettingEntity(
                key = "${key}_${params.app_package}",
                value = "true",
                type = "boolean",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun getTimeRange(range: String): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val start = when (range) {
            "today" -> now - 24 * 60 * 60 * 1000
            "week" -> now - 7 * 24 * 60 * 60 * 1000
            "month" -> now - 30 * 24 * 60 * 60 * 1000
            else -> now - 7 * 24 * 60 * 60 * 1000
        }
        return start to now
    }
}

class SpendingController(private val db: LifeCompanionDatabase) {
    private val patternEngine = SpendingPatternEngine(db)

    suspend fun getInsights(params: SpendingInsightsParams): Map<String, Any> {
        val insights = patternEngine.analyzeSpendingPatterns()

        return mapOf(
            "time_range" to params.time_range,
            "category_breakdown" to insights.categoryBreakdown.map {
                mapOf("category" to it.first, "total" to it.second)
            },
            "late_night" to mapOf(
                "count" to insights.lateNightCount,
                "total" to insights.lateNightTotal
            ),
            "impulsive" to mapOf(
                "count" to insights.impulsiveCount,
                "total" to insights.impulsiveTotal
            ),
            "recurring_merchants" to insights.recurringMerchants.map {
                mapOf("merchant" to it.key, "count" to it.value.first, "total" to it.value.second)
            },
            "emotional_breakdown" to insights.emotionalBreakdown
        )
    }

    suspend fun getEvents(params: SpendingEventsParams): Map<String, Any> {
        var events = db.spendingDao().getAllSpending()

        params.category?.let { cat ->
            events = events.filter { it.category == cat }
        }
        params.min_amount?.let { min ->
            events = events.filter { it.amount >= min }
        }
        params.max_amount?.let { max ->
            events = events.filter { it.amount <= max }
        }

        return mapOf(
            "count" to events.size,
            "events" to events.take(params.limit ?: 50).map {
                mapOf(
                    "id" to it.id,
                    "timestamp" to it.timestamp,
                    "amount" to it.amount,
                    "currency" to it.currency,
                    "merchant" to it.merchant,
                    "category" to it.category,
                    "user_note" to it.userNote,
                    "emotion_tag" to it.emotionTag,
                    "date" to it.date
                )
            }
        )
    }

    suspend fun tagEvent(params: TagSpendingParams) {
        db.spendingDao().updateTags(
            eventId = params.event_id,
            note = params.note,
            emotionTag = params.emotion_tag,
            category = params.category
        )
    }
}

class UsageController(private val db: LifeCompanionDatabase) {
    suspend fun getAppUsage(params: AppUsageParams): Map<String, Any> {
        val date = params.date ?: java.time.LocalDate.now().toString()
        val events = db.eventDao().getEventsForDay(date)
        val appSessions = events.filter { it.type == "AppSessionEnded" }

        val topApps = appSessions.groupBy { it.appPackage }
            .mapValues { (_, sessions) ->
                sessions.sumOf { /* duration from JSON */ 0L }
            }
            .toList()
            .sortedByDescending { it.second }
            .take(params.top_n ?: 10)

        return mapOf(
            "date" to date,
            "total_sessions" to appSessions.size,
            "top_apps" to topApps.map {
                mapOf("app" to it.first, "duration_ms" to it.second)
            }
        )
    }

    suspend fun getAttentionReport(params: AttentionReportParams): Map<String, Any> {
        return mapOf(
            "time_range" to params.time_range,
            "screen_time_minutes" to 0,
            "productive_time_minutes" to 0,
            "distracting_time_minutes" to 0,
            "app_switches" to 0
        )
    }
}

class ContextController(private val db: LifeCompanionDatabase) {
    suspend fun getCurrentContext(): Map<String, Any> {
        return mapOf(
            "timestamp" to System.currentTimeMillis(),
            "hour" to java.time.LocalTime.now().hour,
            "device_context" to "active"
        )
    }
}

class EventsController(private val db: LifeCompanionDatabase) {
    suspend fun queryEvents(params: QueryEventsParams): Map<String, Any> {
        val now = System.currentTimeMillis()
        val start = params.start_date?.let { parseDate(it) } ?: (now - 7 * 24 * 60 * 60 * 1000)
        val end = params.end_date?.let { parseDate(it) } ?: now

        val events = db.eventDao().getEvents(start, end, params.limit ?: 100)

        return mapOf(
            "count" to events.size,
            "events" to events.map {
                mapOf(
                    "id" to it.id,
                    "timestamp" to it.timestamp,
                    "type" to it.type,
                    "event_date" to it.eventDate
                )
            }
        )
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            java.time.LocalDate.parse(dateStr)
                .atStartOfDay()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

class SuggestionController(private val db: LifeCompanionDatabase) {
    suspend fun getSuggestions(params: SuggestionsParams): Map<String, Any> {
        val suggestions = if (params.type == "all") {
            db.suggestionDao().getPending()
        } else {
            db.suggestionDao().getPendingByType(params.type ?: "all")
        }

        return mapOf(
            "count" to suggestions.size,
            "suggestions" to suggestions.map {
                mapOf(
                    "id" to it.id,
                    "timestamp" to it.timestamp,
                    "type" to it.type,
                    "title" to it.title,
                    "body" to it.body,
                    "priority" to it.priority
                )
            }
        )
    }

    suspend fun acceptSuggestion(params: AcceptSuggestionParams): Map<String, Any> {
        db.suggestionDao().updateStatus(params.suggestion_id, "accepted")
        return mapOf("success" to true, "suggestion_id" to params.suggestion_id)
    }

    suspend fun dismissSuggestion(params: DismissSuggestionParams) {
        db.suggestionDao().updateStatus(params.suggestion_id, "dismissed")
    }
}

class MemoryController(private val db: LifeCompanionDatabase) {
    suspend fun addMemory(params: AddMemoryParams): Map<String, Any> {
        val memory = UserMemoryEntity(
            timestamp = System.currentTimeMillis(),
            type = params.type,
            content = params.content,
            linkedEventIds = params.linked_event_ids?.joinToString(","),
            tags = params.tags?.joinToString(",")
        )

        val id = db.memoryDao().insert(memory)
        return mapOf("success" to true, "id" to id)
    }

    suspend fun searchMemories(params: SearchMemoriesParams): Map<String, Any> {
        val memories = if (params.query != null) {
            db.memoryDao().search("%${params.query}%", params.limit ?: 20)
        } else if (params.type != "all") {
            db.memoryDao().getByType(params.type ?: "all", params.limit ?: 20)
        } else {
            db.memoryDao().getRecent(params.limit ?: 20)
        }

        return mapOf(
            "count" to memories.size,
            "memories" to memories.map {
                mapOf(
                    "id" to it.id,
                    "timestamp" to it.timestamp,
                    "type" to it.type,
                    "content" to it.content,
                    "tags" to it.tags?.split(",") ?: emptyList<String>()
                )
            }
        )
    }
}
