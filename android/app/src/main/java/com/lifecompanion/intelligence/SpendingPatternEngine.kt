package com.lifecompanion.intelligence

import com.lifecompanion.data.*
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId

/**
 * Spending Pattern Engine
 * Analyzes spending patterns to identify trends, anomalies, and emotional triggers
 */
class SpendingPatternEngine(private val db: LifeCompanionDatabase) {

    /**
     * Analyze spending patterns
     */
    suspend fun analyzeSpendingPatterns(): SpendingInsights {
        try {
            val events = db.spendingDao().getAllSpending()

            // 1. Category breakdown
            val categoryTotals = events.groupBy { it.category }
                .mapValues { (_, events) -> events.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }

            // 2. Late night spending
            val lateNightSpending = events.filter {
                val hour = Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .hour
                hour >= 22 || hour <= 5
            }

            // 3. Impulsive spending (rapid succession)
            val impulsiveSpending = detectImpulsiveSpending(events)

            // 4. Recurring merchants
            val recurringMerchants = events.groupBy { it.merchant }
                .filter { it.value.size >= 3 }
                .mapValues { it.value.size to it.value.sumOf { e -> e.amount } }

            // 5. Emotional spending
            val emotionalSpending = events.filter { it.emotionTag != null }
                .groupBy { it.emotionTag!! }
                .mapValues { it.value.sumOf { e -> e.amount } }

            Timber.d("Analyzed ${events.size} spending events")

            return SpendingInsights(
                categoryBreakdown = categoryTotals,
                lateNightCount = lateNightSpending.size,
                lateNightTotal = lateNightSpending.sumOf { it.amount },
                impulsiveCount = impulsiveSpending.size,
                impulsiveTotal = impulsiveSpending.sumOf { it.amount },
                recurringMerchants = recurringMerchants,
                emotionalBreakdown = emotionalSpending
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing spending patterns")
            return SpendingInsights(
                categoryBreakdown = emptyList(),
                lateNightCount = 0,
                lateNightTotal = 0.0,
                impulsiveCount = 0,
                impulsiveTotal = 0.0,
                recurringMerchants = emptyMap(),
                emotionalBreakdown = emptyMap()
            )
        }
    }

    /**
     * Detect spending anomalies
     */
    suspend fun detectAnomalies(): List<SpendingAnomaly> {
        try {
            val events = db.spendingDao().getAllSpending()
            val anomalies = mutableListOf<SpendingAnomaly>()

            // Baseline: average spending per category
            val categoryAverages = events.groupBy { it.category }
                .mapValues { (_, events) ->
                    events.map { it.amount }.average()
                }

            // Find outliers (3x average)
            for (event in events) {
                val avg = categoryAverages[event.category] ?: continue
                if (event.amount > avg * 3) {
                    anomalies.add(
                        SpendingAnomaly(
                            event = event,
                            type = AnomalyType.UNUSUALLY_HIGH,
                            expectedRange = 0.0..avg * 2,
                            actualAmount = event.amount
                        )
                    )
                }
            }

            Timber.d("Detected ${anomalies.size} spending anomalies")
            return anomalies
        } catch (e: Exception) {
            Timber.e(e, "Error detecting anomalies")
            return emptyList()
        }
    }

    private fun detectImpulsiveSpending(events: List<SpendingEventEntity>): List<SpendingEventEntity> {
        val sorted = events.sortedBy { it.timestamp }
        val impulsive = mutableListOf<SpendingEventEntity>()

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            // Impulsive = 2+ purchases within 30 minutes
            if (curr.timestamp - prev.timestamp < 30 * 60 * 1000) {
                impulsive.add(prev)
                impulsive.add(curr)
            }
        }

        return impulsive.distinct()
    }
}
