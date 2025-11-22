package com.lifecompanion.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.lifecompanion.data.LifeCompanionDatabase
import com.lifecompanion.normalization.NormalizationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Listens to all notifications and captures them for analysis.
 * Tracks when notifications are received, opened, dismissed, or ignored.
 */
class LifeCompanionNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: LifeCompanionDatabase
    private lateinit var normalizationEngine: NormalizationEngine

    private val activeNotifications = mutableMapOf<String, NotificationState>()

    override fun onCreate() {
        super.onCreate()
        db = LifeCompanionDatabase.getInstance(applicationContext)
        normalizationEngine = NormalizationEngine(db)
        Timber.d("NotificationListener service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            try {
                val signal = extractNotificationSignal(sbn)
                Timber.d("Notification received: ${signal.appName} - ${signal.title}")

                // Send to normalization layer
                normalizationEngine.processNotificationReceived(signal)

                // Track for interaction measurement
                activeNotifications[sbn.key] = NotificationState(
                    signal = signal,
                    receivedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Timber.e(e, "Error processing notification")
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        scope.launch {
            try {
                val state = activeNotifications.remove(sbn.key) ?: return@launch
                val duration = System.currentTimeMillis() - state.receivedAt

                val interaction = when (reason) {
                    REASON_CLICK -> InteractionType.OPENED
                    REASON_CANCEL, REASON_CANCEL_ALL -> InteractionType.DISMISSED
                    REASON_TIMEOUT -> InteractionType.IGNORED
                    else -> InteractionType.UNKNOWN
                }

                Timber.d("Notification removed: ${state.signal.appName} - $interaction (${duration}ms)")

                normalizationEngine.processNotificationInteraction(
                    notificationId = state.signal.id,
                    interaction = interaction,
                    durationMs = duration
                )
            } catch (e: Exception) {
                Timber.e(e, "Error processing notification removal")
            }
        }
    }

    private fun extractNotificationSignal(sbn: StatusBarNotification): NotificationSignal {
        val notification = sbn.notification
        val extras = notification.extras

        return NotificationSignal(
            id = "${sbn.packageName}_${sbn.postTime}",
            timestamp = sbn.postTime,
            packageName = sbn.packageName,
            appName = getAppName(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
            category = notification.category,
            priority = when {
                notification.priority >= NotificationCompat.PRIORITY_HIGH -> 3
                notification.priority == NotificationCompat.PRIORITY_DEFAULT -> 2
                notification.priority == NotificationCompat.PRIORITY_LOW -> 1
                else -> 0
            },
            groupKey = notification.group,
            actions = notification.actions?.map { it.title.toString() } ?: emptyList(),
            isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            canDismiss = sbn.isClearable
        )
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("NotificationListener service destroyed")
    }
}

// Data classes
data class NotificationSignal(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val category: String?,
    val priority: Int,
    val groupKey: String?,
    val actions: List<String>,
    val isOngoing: Boolean,
    val canDismiss: Boolean
)

data class NotificationState(
    val signal: NotificationSignal,
    val receivedAt: Long
)

enum class InteractionType {
    OPENED, DISMISSED, IGNORED, UNKNOWN
}
