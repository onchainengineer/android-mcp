package com.lifecompanion.api

import android.content.Context
import com.lifecompanion.data.LifeCompanionDatabase
import com.lifecompanion.intelligence.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * MCP API Server
 * Runs on Android device, exposes life companion data via HTTP
 * Listens on localhost:8080
 */
class MCPApiServer(private val context: Context) {

    private var server: NettyApplicationEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            try {
                server = embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
                    install(ContentNegotiation) {
                        json()
                    }

                    val db = LifeCompanionDatabase.getInstance(context)
                    val controllers = Controllers(db)

                    routing {
                        // Health check
                        get("/health") {
                            call.respond(mapOf("status" to "ok", "service" to "android-life-companion"))
                        }

                        // Relationship endpoints
                        post("/api/relationships/insights") {
                            val params = call.receive<RelationshipInsightsParams>()
                            val result = controllers.relationship.getInsights(params)
                            call.respond(result)
                        }

                        post("/api/contacts/profile") {
                            val params = call.receive<ContactProfileParams>()
                            val result = controllers.contact.getProfile(params.contact_id)
                            call.respond(result)
                        }

                        post("/api/contacts/tag") {
                            val params = call.receive<TagContactParams>()
                            controllers.contact.tagContact(params)
                            call.respond(mapOf("success" to true))
                        }

                        // Notification endpoints
                        post("/api/notifications/stats") {
                            val params = call.receive<NotificationStatsParams>()
                            val result = controllers.notification.getStats(params)
                            call.respond(result)
                        }

                        post("/api/notifications/noisy-apps") {
                            val params = call.receive<NoisyAppsParams>()
                            val result = controllers.notification.getNoisyApps(params)
                            call.respond(result)
                        }

                        post("/api/notifications/configure") {
                            val params = call.receive<ConfigureNotificationsParams>()
                            controllers.notification.configure(params)
                            call.respond(mapOf("success" to true))
                        }

                        // Spending endpoints
                        post("/api/spending/insights") {
                            val params = call.receive<SpendingInsightsParams>()
                            val result = controllers.spending.getInsights(params)
                            call.respond(result)
                        }

                        post("/api/spending/events") {
                            val params = call.receive<SpendingEventsParams>()
                            val result = controllers.spending.getEvents(params)
                            call.respond(result)
                        }

                        post("/api/spending/tag") {
                            val params = call.receive<TagSpendingParams>()
                            controllers.spending.tagEvent(params)
                            call.respond(mapOf("success" to true))
                        }

                        // Usage endpoints
                        post("/api/usage/apps") {
                            val params = call.receive<AppUsageParams>()
                            val result = controllers.usage.getAppUsage(params)
                            call.respond(result)
                        }

                        post("/api/usage/attention-report") {
                            val params = call.receive<AttentionReportParams>()
                            val result = controllers.usage.getAttentionReport(params)
                            call.respond(result)
                        }

                        // Context endpoints
                        post("/api/context/current") {
                            val result = controllers.context.getCurrentContext()
                            call.respond(result)
                        }

                        // Events endpoints
                        post("/api/events/query") {
                            val params = call.receive<QueryEventsParams>()
                            val result = controllers.events.queryEvents(params)
                            call.respond(result)
                        }

                        // Suggestions endpoints
                        post("/api/suggestions/current") {
                            val params = call.receive<SuggestionsParams>()
                            val result = controllers.suggestion.getSuggestions(params)
                            call.respond(result)
                        }

                        post("/api/suggestions/accept") {
                            val params = call.receive<AcceptSuggestionParams>()
                            val result = controllers.suggestion.acceptSuggestion(params)
                            call.respond(result)
                        }

                        post("/api/suggestions/dismiss") {
                            val params = call.receive<DismissSuggestionParams>()
                            controllers.suggestion.dismissSuggestion(params)
                            call.respond(mapOf("success" to true))
                        }

                        // Memory endpoints
                        post("/api/memories/add") {
                            val params = call.receive<AddMemoryParams>()
                            val result = controllers.memory.addMemory(params)
                            call.respond(result)
                        }

                        post("/api/memories/search") {
                            val params = call.receive<SearchMemoriesParams>()
                            val result = controllers.memory.searchMemories(params)
                            call.respond(result)
                        }
                    }
                }.start(wait = false)

                Timber.i("MCP API Server started on http://127.0.0.1:8080")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start API server")
            }
        }
    }

    fun stop() {
        server?.stop(1000, 5000)
        Timber.i("MCP API Server stopped")
    }
}

// Controllers container
private class Controllers(db: LifeCompanionDatabase) {
    val relationship = RelationshipController(db)
    val contact = ContactController(db)
    val notification = NotificationController(db)
    val spending = SpendingController(db)
    val usage = UsageController(db)
    val context = ContextController(db)
    val events = EventsController(db)
    val suggestion = SuggestionController(db)
    val memory = MemoryController(db)
}

// Request/Response models
@Serializable
data class RelationshipInsightsParams(
    val filter: String? = "all",
    val limit: Int? = 10
)

@Serializable
data class ContactProfileParams(
    val contact_id: Long
)

@Serializable
data class TagContactParams(
    val contact_id: Long,
    val tag: String? = null,
    val priority: Int? = null,
    val notes: String? = null
)

@Serializable
data class NotificationStatsParams(
    val time_range: String? = "week",
    val group_by: String? = "app"
)

@Serializable
data class NoisyAppsParams(
    val min_noise_score: Float? = 0.7f,
    val limit: Int? = 10
)

@Serializable
data class ConfigureNotificationsParams(
    val app_package: String,
    val action: String
)

@Serializable
data class SpendingInsightsParams(
    val time_range: String? = "month"
)

@Serializable
data class SpendingEventsParams(
    val category: String? = null,
    val min_amount: Double? = null,
    val max_amount: Double? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val limit: Int? = 50
)

@Serializable
data class TagSpendingParams(
    val event_id: String,
    val note: String? = null,
    val category: String? = null,
    val emotion_tag: String? = null,
    val linked_contact_id: Long? = null
)

@Serializable
data class AppUsageParams(
    val date: String? = null,
    val top_n: Int? = 10
)

@Serializable
data class AttentionReportParams(
    val time_range: String? = "today"
)

@Serializable
data class QueryEventsParams(
    val event_type: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val actor_id: Long? = null,
    val limit: Int? = 100
)

@Serializable
data class SuggestionsParams(
    val type: String? = "all"
)

@Serializable
data class AcceptSuggestionParams(
    val suggestion_id: String
)

@Serializable
data class DismissSuggestionParams(
    val suggestion_id: String
)

@Serializable
data class AddMemoryParams(
    val type: String,
    val content: String,
    val linked_event_ids: List<String>? = null,
    val tags: List<String>? = null
)

@Serializable
data class SearchMemoriesParams(
    val query: String? = null,
    val type: String? = "all",
    val limit: Int? = 20
)
