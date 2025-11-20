#!/usr/bin/env node

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  ListToolsRequestSchema,
  CallToolRequestSchema,
  Tool,
} from "@modelcontextprotocol/sdk/types.js";

/**
 * Android Life Companion MCP Server
 *
 * Exposes Android life signals (relationships, spending, attention) to LLMs
 * via the Model Context Protocol.
 *
 * Architecture: Communicates with Android device via HTTP API (localhost:8080)
 */

// Connection to Android service
class AndroidServiceClient {
  private baseUrl: string;

  constructor(baseUrl: string = "http://localhost:8080/api") {
    this.baseUrl = baseUrl;
  }

  async request(endpoint: string, params?: any): Promise<any> {
    try {
      const response = await fetch(`${this.baseUrl}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(params || {}),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error: any) {
      console.error(`[AndroidClient] Error calling ${endpoint}:`, error.message);
      throw new Error(`Failed to communicate with Android service: ${error.message}`);
    }
  }
}

const client = new AndroidServiceClient();

// Initialize MCP server
const server = new Server(
  {
    name: "android-life-companion",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// Define all available tools
const tools: Tool[] = [
  // ==================== RELATIONSHIP TOOLS ====================
  {
    name: "get_relationship_insights",
    description:
      "Get insights about user's relationships including priority contacts, one-sided connections, and neglected relationships. " +
      "Returns relationship scores (priority, reciprocity, response speed, emotional closeness) and interaction statistics.",
    inputSchema: {
      type: "object",
      properties: {
        filter: {
          type: "string",
          enum: ["all", "priority", "weak", "neglected"],
          description: "Filter contacts by relationship type: all (default), priority (high-value relationships), weak (one-sided or low engagement), neglected (haven't contacted in a while)",
        },
        limit: {
          type: "number",
          description: "Maximum number of contacts to return (default: 10)",
          default: 10,
        },
      },
    },
  },
  {
    name: "get_contact_profile",
    description:
      "Get detailed profile for a specific contact including all relationship scores, interaction statistics, " +
      "communication patterns, and derived insights like relationship type (mutual, one-sided incoming, one-sided outgoing).",
    inputSchema: {
      type: "object",
      properties: {
        contact_id: {
          type: "number",
          description: "Unique contact identifier",
        },
      },
      required: ["contact_id"],
    },
  },
  {
    name: "tag_contact",
    description:
      "Add a tag or priority level to a contact to help organize and prioritize relationships. " +
      "Tags like 'best_friend', 'family', 'colleague' affect notification filtering and suggestions.",
    inputSchema: {
      type: "object",
      properties: {
        contact_id: {
          type: "number",
          description: "Contact ID to tag",
        },
        tag: {
          type: "string",
          description: "Tag to add (e.g., 'best_friend', 'family', 'colleague', 'priority')",
        },
        priority: {
          type: "number",
          description: "Priority level 0-10 (10 = highest priority, affects notification filtering)",
          minimum: 0,
          maximum: 10,
        },
        notes: {
          type: "string",
          description: "Optional personal notes about this contact",
        },
      },
      required: ["contact_id"],
    },
  },

  // ==================== NOTIFICATION TOOLS ====================
  {
    name: "get_notification_stats",
    description:
      "Get statistics about notifications: total count, open rate, dismiss rate, ignore rate. " +
      "Can be grouped by app, category, or contact to identify noise patterns.",
    inputSchema: {
      type: "object",
      properties: {
        time_range: {
          type: "string",
          enum: ["today", "week", "month"],
          description: "Time range for statistics (default: week)",
          default: "week",
        },
        group_by: {
          type: "string",
          enum: ["app", "category", "contact"],
          description: "How to group statistics (default: app)",
          default: "app",
        },
      },
    },
  },
  {
    name: "get_noisy_apps",
    description:
      "Get list of apps with high notification noise (frequently dismissed, low engagement). " +
      "Noise score ranges from 0 (always useful) to 1 (always dismissed). Useful for suggesting muting or filtering.",
    inputSchema: {
      type: "object",
      properties: {
        min_noise_score: {
          type: "number",
          description: "Minimum noise score (0-1) to include in results (default: 0.7)",
          default: 0.7,
          minimum: 0,
          maximum: 1,
        },
        limit: {
          type: "number",
          description: "Maximum number of apps to return (default: 10)",
          default: 10,
        },
      },
    },
  },
  {
    name: "configure_app_notifications",
    description:
      "Configure notification behavior for an app (mute, low priority, normal, never filter). " +
      "This affects how the companion handles future notifications from this app.",
    inputSchema: {
      type: "object",
      properties: {
        app_package: {
          type: "string",
          description: "App package name (e.g., 'com.whatsapp', 'com.instagram.android')",
        },
        action: {
          type: "string",
          enum: ["mute", "low_priority", "normal", "never_filter"],
          description:
            "mute: Automatically dismiss, low_priority: Suppress sound/vibration, " +
            "normal: Default behavior, never_filter: Always show regardless of noise score",
        },
      },
      required: ["app_package", "action"],
    },
  },

  // ==================== SPENDING TOOLS ====================
  {
    name: "get_spending_insights",
    description:
      "Get comprehensive spending insights including category breakdown, time patterns (late night spending), " +
      "impulsive spending detection, recurring merchants, and emotional spending patterns.",
    inputSchema: {
      type: "object",
      properties: {
        time_range: {
          type: "string",
          enum: ["week", "month", "quarter"],
          description: "Time range for analysis (default: month)",
          default: "month",
        },
      },
    },
  },
  {
    name: "get_spending_events",
    description:
      "Get list of spending events with optional filters. Returns transaction details including " +
      "amount, merchant, category, user notes, and emotional tags.",
    inputSchema: {
      type: "object",
      properties: {
        category: {
          type: "string",
          description:
            "Filter by category (e.g., 'FOOD_DELIVERY', 'SHOPPING', 'TRANSPORT')",
        },
        min_amount: {
          type: "number",
          description: "Minimum transaction amount to include",
        },
        max_amount: {
          type: "number",
          description: "Maximum transaction amount to include",
        },
        start_date: {
          type: "string",
          description: "Start date in ISO format (YYYY-MM-DD)",
        },
        end_date: {
          type: "string",
          description: "End date in ISO format (YYYY-MM-DD)",
        },
        limit: {
          type: "number",
          description: "Maximum number of events to return (default: 50)",
          default: 50,
        },
      },
    },
  },
  {
    name: "tag_spending_event",
    description:
      "Add context to a spending event: notes, category override, emotional tag, or link to a person. " +
      "This helps build understanding of spending patterns and emotional triggers.",
    inputSchema: {
      type: "object",
      properties: {
        event_id: {
          type: "string",
          description: "Spending event ID",
        },
        note: {
          type: "string",
          description: "User note about this transaction (e.g., 'Gift for colleague's birthday')",
        },
        category: {
          type: "string",
          description: "Category override (e.g., 'GIFTS', 'PERSONAL_CARE')",
        },
        emotion_tag: {
          type: "string",
          enum: ["regret", "necessary", "impulse", "joy", "neutral"],
          description: "Emotional context for this spending",
        },
        linked_contact_id: {
          type: "number",
          description: "Contact ID if this spending was related to a person (e.g., treating someone)",
        },
      },
      required: ["event_id"],
    },
  },

  // ==================== APP USAGE / ATTENTION TOOLS ====================
  {
    name: "get_app_usage",
    description:
      "Get app usage statistics: screen time, launch counts, session durations. " +
      "Returns top apps by usage and detailed session information.",
    inputSchema: {
      type: "object",
      properties: {
        date: {
          type: "string",
          description: "Date to query in ISO format (YYYY-MM-DD), defaults to today",
        },
        top_n: {
          type: "number",
          description: "Return top N apps by usage time (default: 10)",
          default: 10,
        },
      },
    },
  },
  {
    name: "get_attention_report",
    description:
      "Get comprehensive attention report including productive vs distracting time, " +
      "focus patterns, app switching behavior, and recommendations for attention management.",
    inputSchema: {
      type: "object",
      properties: {
        time_range: {
          type: "string",
          enum: ["today", "week", "month"],
          description: "Time range for report (default: today)",
          default: "today",
        },
      },
    },
  },

  // ==================== CONTEXT TOOLS ====================
  {
    name: "get_device_context",
    description:
      "Get current device context including time, current app in use, battery level, " +
      "and inferred activity (working, relaxing, commuting). Useful for context-aware suggestions.",
    inputSchema: {
      type: "object",
      properties: {},
    },
  },
  {
    name: "get_life_events",
    description:
      "Query raw life events by type, date range, or related contact. Events include notifications, " +
      "messages, app sessions, spending, and more. This is the foundation data for all insights.",
    inputSchema: {
      type: "object",
      properties: {
        event_type: {
          type: "string",
          description:
            "Filter by event type (e.g., 'NotificationReceived', 'MessageSent', 'MoneySpent')",
        },
        start_date: {
          type: "string",
          description: "Start date in ISO format (YYYY-MM-DD)",
        },
        end_date: {
          type: "string",
          description: "End date in ISO format (YYYY-MM-DD)",
        },
        actor_id: {
          type: "number",
          description: "Filter events related to a specific contact ID",
        },
        limit: {
          type: "number",
          description: "Maximum number of events to return (default: 100)",
          default: 100,
        },
      },
    },
  },

  // ==================== SUGGESTION TOOLS ====================
  {
    name: "get_suggestions",
    description:
      "Get current suggestions from the companion system. Suggestions include relationship nudges " +
      "(neglected contacts), notification filters (noisy apps), and spending insights (patterns/anomalies).",
    inputSchema: {
      type: "object",
      properties: {
        type: {
          type: "string",
          enum: ["relationship", "notification", "spending", "all"],
          description: "Filter by suggestion type (default: all)",
          default: "all",
        },
      },
    },
  },
  {
    name: "accept_suggestion",
    description:
      "Accept and apply a suggestion. This executes the suggested action " +
      "(e.g., tags a contact as priority, mutes a noisy app, etc.).",
    inputSchema: {
      type: "object",
      properties: {
        suggestion_id: {
          type: "string",
          description: "Suggestion ID to accept",
        },
      },
      required: ["suggestion_id"],
    },
  },
  {
    name: "dismiss_suggestion",
    description:
      "Dismiss a suggestion without taking action. The system learns from dismissals.",
    inputSchema: {
      type: "object",
      properties: {
        suggestion_id: {
          type: "string",
          description: "Suggestion ID to dismiss",
        },
      },
      required: ["suggestion_id"],
    },
  },

  // ==================== MEMORY TOOLS ====================
  {
    name: "add_memory",
    description:
      "Add a user reflection, goal, or preference to long-term memory. " +
      "These memories help the companion understand user values and make better suggestions.",
    inputSchema: {
      type: "object",
      properties: {
        type: {
          type: "string",
          enum: ["reflection", "goal", "preference"],
          description:
            "reflection: User's thoughts about their behavior, goal: What they want to achieve, " +
            "preference: Explicit preferences for how the system should behave",
        },
        content: {
          type: "string",
          description: "Content of the memory (user's words)",
        },
        linked_event_ids: {
          type: "array",
          items: { type: "string" },
          description: "Optional: Event IDs this memory relates to",
        },
        tags: {
          type: "array",
          items: { type: "string" },
          description: "Optional: Tags for categorization (e.g., 'spending', 'relationships')",
        },
      },
      required: ["type", "content"],
    },
  },
  {
    name: "search_memories",
    description:
      "Search user memories and reflections. Useful for understanding user context and preferences.",
    inputSchema: {
      type: "object",
      properties: {
        query: {
          type: "string",
          description: "Search query (searches content and tags)",
        },
        type: {
          type: "string",
          enum: ["reflection", "goal", "preference", "all"],
          description: "Filter by memory type (default: all)",
          default: "all",
        },
        limit: {
          type: "number",
          description: "Maximum number of memories to return (default: 20)",
          default: 20,
        },
      },
    },
  },
];

// Register tool list handler
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return { tools };
});

// Register tool call handler
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    let result: any;

    switch (name) {
      // Relationship tools
      case "get_relationship_insights":
        result = await client.request("/relationships/insights", args);
        break;

      case "get_contact_profile":
        result = await client.request("/contacts/profile", args);
        break;

      case "tag_contact":
        result = await client.request("/contacts/tag", args);
        break;

      // Notification tools
      case "get_notification_stats":
        result = await client.request("/notifications/stats", args);
        break;

      case "get_noisy_apps":
        result = await client.request("/notifications/noisy-apps", args);
        break;

      case "configure_app_notifications":
        result = await client.request("/notifications/configure", args);
        break;

      // Spending tools
      case "get_spending_insights":
        result = await client.request("/spending/insights", args);
        break;

      case "get_spending_events":
        result = await client.request("/spending/events", args);
        break;

      case "tag_spending_event":
        result = await client.request("/spending/tag", args);
        break;

      // App usage tools
      case "get_app_usage":
        result = await client.request("/usage/apps", args);
        break;

      case "get_attention_report":
        result = await client.request("/usage/attention-report", args);
        break;

      // Context tools
      case "get_device_context":
        result = await client.request("/context/current", args);
        break;

      case "get_life_events":
        result = await client.request("/events/query", args);
        break;

      // Suggestion tools
      case "get_suggestions":
        result = await client.request("/suggestions/current", args);
        break;

      case "accept_suggestion":
        result = await client.request("/suggestions/accept", args);
        break;

      case "dismiss_suggestion":
        result = await client.request("/suggestions/dismiss", args);
        break;

      // Memory tools
      case "add_memory":
        result = await client.request("/memories/add", args);
        break;

      case "search_memories":
        result = await client.request("/memories/search", args);
        break;

      default:
        throw new Error(`Unknown tool: ${name}`);
    }

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(result, null, 2),
        },
      ],
    };
  } catch (error: any) {
    console.error(`[MCP Server] Error executing ${name}:`, error);
    return {
      content: [
        {
          type: "text",
          text: `Error: ${error.message}`,
        },
      ],
      isError: true,
    };
  }
});

// Start server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Android Life Companion MCP Server running on stdio");
  console.error("Waiting for Android API service at http://localhost:8080");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});
