package com.lifecompanion.intelligence

import com.lifecompanion.data.*
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.min

/**
 * Relationship Scoring Engine
 * Computes scores for relationships: priority, reciprocity, response speed, emotional closeness
 */
class RelationshipScoringEngine(private val db: LifeCompanionDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compute all scores for a contact
     */
    suspend fun computeScoresForContact(contactId: Long) {
        try {
            val events = db.eventDao().getEventsForContact(
                contactId = contactId,
                since = System.currentTimeMillis() - 90.days()
            )

            val messages = events.filter {
                it.type in listOf("MessageReceived", "MessageSent")
            }

            if (messages.isEmpty()) {
                Timber.d("No messages for contact $contactId, skipping scoring")
                return
            }

            val received = messages.filter { it.type == "MessageReceived" }
            val sent = messages.filter { it.type == "MessageSent" }

            // Compute scores
            val priorityScore = computePriorityScore(received, sent, events)
            val reciprocityScore = computeReciprocityScore(received, sent)
            val responseSpeedScore = computeResponseSpeed(messages)
            val emotionalClosenessScore = computeEmotionalCloseness(messages)

            // Compute statistics
            val avgResponseTime = computeAvgResponseTime(received, sent)
            val lateNightChats = countLateNightChats(messages)
            val immediateReplies = countImmediateReplies(messages)

            // Determine relationship type
            val relationshipType = determineRelationshipType(reciprocityScore, received, sent)

            val profile = RelationshipProfileEntity(
                contactId = contactId,
                priorityScore = priorityScore,
                reciprocityScore = reciprocityScore,
                responseSpeedScore = responseSpeedScore,
                emotionalClosenessScore = emotionalClosenessScore,
                totalMessagesReceived = received.size,
                totalMessagesSent = sent.size,
                totalCallsReceived = 0,
                totalCallsMade = 0,
                avgResponseTimeMs = avgResponseTime,
                avgInitiationTimeMs = 0L,
                lastMessageFromThem = received.maxOfOrNull { it.timestamp },
                lastMessageFromUs = sent.maxOfOrNull { it.timestamp },
                lastCallFromThem = null,
                lastCallFromUs = null,
                lateNightChatsCount = lateNightChats,
                immediateRepliesCount = immediateReplies,
                ignoredCount = 0,
                neglectedByUsCount = 0,
                relationshipType = relationshipType,
                lastUpdated = System.currentTimeMillis()
            )

            db.relationshipDao().insert(profile)
            Timber.d("Updated relationship profile for contact $contactId: priority=$priorityScore, reciprocity=$reciprocityScore")
        } catch (e: Exception) {
            Timber.e(e, "Error computing scores for contact $contactId")
        }
    }

    private fun computePriorityScore(
        received: List<LifeEventEntity>,
        sent: List<LifeEventEntity>,
        allEvents: List<LifeEventEntity>
    ): Float {
        var score = 0.5f

        if (received.isEmpty()) return score

        // Factor 1: Response rate (0-30%)
        val responseRate = sent.count { s ->
            received.any { r ->
                s.timestamp > r.timestamp &&
                        s.timestamp - r.timestamp < 24.hours()
            }
        }.toFloat() / received.size
        score += responseRate * 0.3f

        // Factor 2: Response speed (0-25%)
        val avgResponseTime = computeAvgResponseTime(received, sent)
        val speedScore = when {
            avgResponseTime < 5.minutes() -> 0.25f
            avgResponseTime < 1.hours() -> 0.20f
            avgResponseTime < 6.hours() -> 0.10f
            else -> 0f
        }
        score += speedScore

        // Factor 3: Interaction frequency (0-25%)
        val interactionsPerWeek = (received.size + sent.size) / 13f
        val frequencyScore = min(interactionsPerWeek / 20f, 0.25f)
        score += frequencyScore

        // Factor 4: Notification interactions (0-20%)
        val notificationInteractions = allEvents.filter { it.type == "NotificationInteracted" }
        if (notificationInteractions.isNotEmpty()) {
            val openRate = notificationInteractions.count {
                it.dataJson.contains("OPENED")
            }.toFloat() / notificationInteractions.size
            score += openRate * 0.2f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun computeReciprocityScore(
        received: List<LifeEventEntity>,
        sent: List<LifeEventEntity>
    ): Float {
        if (received.isEmpty() && sent.isEmpty()) return 0.5f

        val total = received.size + sent.size
        val balance = abs(received.size - sent.size).toFloat() / total

        // Perfect balance = 1.0, completely one-sided = 0.0
        val balanceScore = 1f - balance

        // Who initiates?
        val weInitiate = sent.count { s ->
            received.none { r ->
                r.timestamp > s.timestamp - 1.hours() &&
                        r.timestamp < s.timestamp
            }
        }
        val theyInitiate = received.count { r ->
            sent.none { s ->
                s.timestamp > r.timestamp - 1.hours() &&
                        s.timestamp < r.timestamp
            }
        }

        val initiationBalance = if (weInitiate + theyInitiate > 0) {
            1f - abs(weInitiate - theyInitiate).toFloat() / (weInitiate + theyInitiate)
        } else 0.5f

        return (balanceScore * 0.6f + initiationBalance * 0.4f).coerceIn(0f, 1f)
    }

    private fun computeResponseSpeed(messages: List<LifeEventEntity>): Float {
        val sorted = messages.sortedBy { it.timestamp }
        val responseTimes = mutableListOf<Long>()

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            if (prev.type != curr.type) {
                responseTimes.add(curr.timestamp - prev.timestamp)
            }
        }

        if (responseTimes.isEmpty()) return 0.5f

        val avgResponseTime = responseTimes.average().toLong()

        return when {
            avgResponseTime < 5.minutes() -> 1.0f
            avgResponseTime < 30.minutes() -> 0.8f
            avgResponseTime < 2.hours() -> 0.6f
            avgResponseTime < 12.hours() -> 0.4f
            avgResponseTime < 24.hours() -> 0.2f
            else -> 0.1f
        }.coerceIn(0f, 1f)
    }

    private fun computeEmotionalCloseness(messages: List<LifeEventEntity>): Float {
        var score = 0f

        // Late night conversations
        val lateNightChats = countLateNightChats(messages)
        score += min(lateNightChats.toFloat() / 20f, 0.3f)

        // Immediate replies
        val immediateReplies = countImmediateReplies(messages)
        score += min(immediateReplies.toFloat() / 30f, 0.3f)

        // Long conversations (10+ messages in 30 min)
        val longConversations = detectLongConversations(messages)
        score += min(longConversations.toFloat() / 10f, 0.2f)

        // Consistency
        val consistencyScore = measureConsistency(messages)
        score += consistencyScore * 0.2f

        return score.coerceIn(0f, 1f)
    }

    private fun computeAvgResponseTime(
        received: List<LifeEventEntity>,
        sent: List<LifeEventEntity>
    ): Long {
        val responseTimes = mutableListOf<Long>()

        for (s in sent) {
            val lastReceived = received
                .filter { it.timestamp < s.timestamp }
                .maxByOrNull { it.timestamp }

            lastReceived?.let {
                responseTimes.add(s.timestamp - it.timestamp)
            }
        }

        return if (responseTimes.isNotEmpty()) {
            responseTimes.average().toLong()
        } else {
            12.hours()
        }
    }

    private fun countLateNightChats(messages: List<LifeEventEntity>): Int {
        return messages.count {
            val hour = java.time.Instant.ofEpochMilli(it.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .hour
            hour >= 23 || hour <= 5
        }
    }

    private fun countImmediateReplies(messages: List<LifeEventEntity>): Int {
        val sorted = messages.sortedBy { it.timestamp }
        var count = 0

        for (i in 1 until sorted.size) {
            if (sorted[i].timestamp - sorted[i - 1].timestamp < 60_000) {
                count++
            }
        }

        return count
    }

    private fun detectLongConversations(messages: List<LifeEventEntity>): Int {
        val sorted = messages.sortedBy { it.timestamp }
        var count = 0
        var i = 0

        while (i < sorted.size) {
            val windowStart = sorted[i].timestamp
            val windowEnd = windowStart + 30.minutes()

            val messagesInWindow = sorted.drop(i).takeWhile {
                it.timestamp <= windowEnd
            }

            if (messagesInWindow.size >= 10) {
                count++
                i += messagesInWindow.size
            } else {
                i++
            }
        }

        return count
    }

    private fun measureConsistency(messages: List<LifeEventEntity>): Float {
        if (messages.size < 7) return 0f

        val sorted = messages.sortedBy { it.timestamp }
        val firstDay = sorted.first().timestamp
        val lastDay = sorted.last().timestamp
        val totalDays = ((lastDay - firstDay) / (24 * 60 * 60 * 1000)).toInt()

        if (totalDays < 7) return 0f

        val activeDays = sorted.map { it.timestamp / (24 * 60 * 60 * 1000) }.distinct().size

        return (activeDays.toFloat() / totalDays).coerceIn(0f, 1f)
    }

    private fun determineRelationshipType(
        reciprocityScore: Float,
        received: List<LifeEventEntity>,
        sent: List<LifeEventEntity>
    ): String {
        return when {
            reciprocityScore > 0.7f -> "mutual"
            received.size > sent.size * 2 -> "one_sided_incoming"
            sent.size > received.size * 2 -> "one_sided_outgoing"
            else -> "balanced"
        }
    }

    // Extension functions for readability
    private fun Int.days() = this * 24L * 60 * 60 * 1000
    private fun Int.hours() = this * 60L * 60 * 1000
    private fun Int.minutes() = this * 60L * 1000
    private fun Long.hours() = this * 60 * 60 * 1000
    private fun Long.minutes() = this * 60 * 1000
}
