package com.lifecompanion.intelligence

import com.lifecompanion.data.LifeCompanionDatabase
import com.lifecompanion.data.NotificationAnalysis
import timber.log.Timber
import kotlin.math.max

/**
 * Notification Noise Engine
 * Analyzes notification patterns to identify noisy apps
 */
class NotificationNoiseEngine(private val db: LifeCompanionDatabase) {

    /**
     * Compute noise score for a specific app (0 = always useful, 1 = always dismissed)
     */
    suspend fun computeNoiseScoreForApp(appPackage: String): Float {
        try {
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30 * 24 * 60 * 60 * 1000L

            val events = db.eventDao().getEventsByType(
                eventType = "NotificationReceived",
                start = thirtyDaysAgo,
                end = now
            ).filter {
                it.appPackage == appPackage
            }

            if (events.isEmpty()) return 0.5f

            val interactions = db.eventDao().getEventsByType(
                eventType = "NotificationInteracted",
                start = thirtyDaysAgo,
                end = now
            )

            val totalNotifications = events.size
            val opened = interactions.count { it.dataJson.contains("OPENED") }
            val dismissed = interactions.count { it.dataJson.contains("DISMISSED") }
            val ignored = totalNotifications - (opened + dismissed)

            // Calculate rates
            val dismissRate = dismissed.toFloat() / max(totalNotifications, 1)
            val openRate = opened.toFloat() / max(totalNotifications, 1)
            val ignoreRate = ignored.toFloat() / max(totalNotifications, 1)

            // Noise formula: high dismiss + low open + high ignore = high noise
            val noiseScore = (dismissRate * 0.5f) + (ignoreRate * 0.3f) + ((1 - openRate) * 0.2f)

            Timber.d("Noise score for $appPackage: $noiseScore (open=$openRate, dismiss=$dismissRate, ignore=$ignoreRate)")
            return noiseScore.coerceIn(0f, 1f)
        } catch (e: Exception) {
            Timber.e(e, "Error computing noise score for $appPackage")
            return 0.5f
        }
    }

    /**
     * Analyze all apps and return insights
     */
    suspend fun analyzeNotificationPatterns(): NotificationAnalysis {
        try {
            val allApps = db.eventDao().getUniqueAppPackages()
            val appScores = allApps.mapNotNull { app ->
                try {
                    app to computeNoiseScoreForApp(app)
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.second }

            val avgNoiseScore = appScores.map { it.second }.average().toFloat()
            val recommendedMutes = appScores.filter { it.second > 0.8f }.map { it.first }

            Timber.d("Analyzed ${appScores.size} apps, avg noise: $avgNoiseScore, recommended mutes: ${recommendedMutes.size}")

            return NotificationAnalysis(
                topNoiseApps = appScores.take(10),
                avgNoiseScore = avgNoiseScore,
                recommendedMutes = recommendedMutes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing notification patterns")
            return NotificationAnalysis(
                topNoiseApps = emptyList(),
                avgNoiseScore = 0f,
                recommendedMutes = emptyList()
            )
        }
    }
}
