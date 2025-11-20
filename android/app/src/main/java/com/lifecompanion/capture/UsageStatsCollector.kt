package com.lifecompanion.capture

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.work.*
import com.lifecompanion.data.LifeCompanionDatabase
import com.lifecompanion.normalization.NormalizationEngine
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Collects app usage statistics from Android's UsageStatsManager.
 * Tracks which apps are used, when, and for how long.
 */
class UsageStatsCollector(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Collect app usage for a specific date
     */
    suspend fun collectDailyStats(date: LocalDate): List<AppUsageSignal> {
        val startMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val usageEvents = usageStatsManager.queryEvents(startMillis, endMillis)
        val sessions = mutableMapOf<String, MutableList<UsageEvent>>()

        // Group events by package into sessions
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    sessions.getOrPut(event.packageName) { mutableListOf() }
                        .add(UsageEvent.Start(event.timeStamp))
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    sessions.getOrPut(event.packageName) { mutableListOf() }
                        .add(UsageEvent.End(event.timeStamp))
                }
            }
        }

        // Convert to signals
        return sessions.flatMap { (pkg, events) ->
            pairStartEndEvents(events).map { (start, end) ->
                AppUsageSignal(
                    packageName = pkg,
                    appName = getAppName(context, pkg),
                    sessionStart = start,
                    sessionEnd = end,
                    durationMs = end - start,
                    foregroundTime = end - start,
                    launchCount = 1
                )
            }
        }
    }

    private fun pairStartEndEvents(events: List<UsageEvent>): List<Pair<Long, Long>> {
        val pairs = mutableListOf<Pair<Long, Long>>()
        var currentStart: Long? = null

        for (event in events) {
            when (event) {
                is UsageEvent.Start -> {
                    if (currentStart == null) {
                        currentStart = event.timestamp
                    }
                }
                is UsageEvent.End -> {
                    currentStart?.let {
                        pairs.add(it to event.timestamp)
                        currentStart = null
                    }
                }
            }
        }

        return pairs
    }

    /**
     * Schedule periodic collection (every 6 hours)
     */
    fun schedulePeriodicCollection() {
        val workRequest = PeriodicWorkRequestBuilder<UsageStatsWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "usage_stats_collection",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Timber.d("Scheduled periodic usage stats collection")
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

/**
 * Worker that runs periodically to collect usage stats
 */
class UsageStatsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = LifeCompanionDatabase.getInstance(applicationContext)
            val collector = UsageStatsCollector(applicationContext)
            val normalizationEngine = NormalizationEngine(db)

            // Collect yesterday's data
            val yesterday = LocalDate.now().minusDays(1)
            val signals = collector.collectDailyStats(yesterday)

            Timber.d("Collected ${signals.size} app usage sessions for $yesterday")

            // Process through normalization
            for (signal in signals) {
                normalizationEngine.processAppUsage(signal)
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error collecting usage stats")
            Result.retry()
        }
    }
}

data class AppUsageSignal(
    val packageName: String,
    val appName: String,
    val sessionStart: Long,
    val sessionEnd: Long,
    val durationMs: Long,
    val foregroundTime: Long,
    val launchCount: Int
)

sealed class UsageEvent {
    abstract val timestamp: Long

    data class Start(override val timestamp: Long) : UsageEvent()
    data class End(override val timestamp: Long) : UsageEvent()
}
