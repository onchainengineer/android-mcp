# Android Life Companion - MCP Server

**Turn your Android phone's life signals into AI-powered insights.**

An MCP (Model Context Protocol) server that exposes Android's notification, messaging, spending, and app usage data to LLMs for intelligent life management.

## 🎯 What This Does

Your phone sees everything:
- Who you talk to (and who you ignore)
- What you spend money on
- Which apps steal your attention
- How you respond to notifications

This system turns those raw signals into actionable insights about:
- **Relationships**: Who matters most, one-sided connections, neglected friends
- **Spending**: Patterns, emotional triggers, impulsive purchases
- **Attention**: Productive vs distracting time, notification noise

## 🏗️ Architecture

**5-Layer On-Device Intelligence:**

```
┌─────────────────────────────────────────────┐
│  5. ACTION LAYER (MCP Server + UI)          │
│  ↓ Expose insights via MCP tools            │
├─────────────────────────────────────────────┤
│  4. INTELLIGENCE LAYER                      │
│  ↓ Score patterns, detect anomalies         │
├─────────────────────────────────────────────┤
│  3. STORAGE LAYER                           │
│  ↓ Encrypted SQLite (SQLCipher)             │
├─────────────────────────────────────────────┤
│  2. NORMALIZATION LAYER                     │
│  ↓ Convert to typed events                  │
├─────────────────────────────────────────────┤
│  1. CAPTURE LAYER                           │
│  ↓ NotificationListener, SMS, UsageStats    │
└─────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- Android device (API 26+)
- Node.js 20+ for MCP server
- Android Studio for building the app

### 1. Build MCP Server

```bash
cd mcp-server
npm install
npm run build
```

### 2. Build Android App

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant Permissions

On your Android device:
1. Open Life Companion app
2. Grant SMS, Contacts, Usage Stats permissions
3. Enable Notification Listener Service in Settings

### 4. Run MCP Server

```bash
cd mcp-server
npm start
```

The MCP server connects to the Android app via HTTP (localhost:8080).

## 🛠️ Available MCP Tools

### Relationship Tools

- `get_relationship_insights` - Get priority contacts, weak connections, neglected relationships
- `get_contact_profile` - Detailed relationship scores and statistics
- `tag_contact` - Tag contacts with labels and priorities

### Notification Tools

- `get_notification_stats` - Total notifications, open/dismiss rates
- `get_noisy_apps` - Apps with high notification noise
- `configure_app_notifications` - Mute or filter specific apps

### Spending Tools

- `get_spending_insights` - Category breakdown, patterns, emotional spending
- `get_spending_events` - Query transactions with filters
- `tag_spending_event` - Add context to transactions

### App Usage / Attention Tools

- `get_app_usage` - Screen time, launch counts
- `get_attention_report` - Productive vs distracting time

### Context & Memory Tools

- `get_device_context` - Current device state
- `get_life_events` - Query raw events
- `add_memory` - Save user reflections and goals
- `search_memories` - Search past reflections

## 📱 Android Components

### Capture Layer

**NotificationListenerService** (`capture/NotificationListener.kt`)
- Captures all notifications
- Tracks opened, dismissed, ignored interactions

**SMS Receiver** (`capture/SmsReceiver.kt`)
- Detects transaction SMS (debit/credit)
- Classifies bills, OTPs, promotional messages

**Usage Stats Collector** (`capture/UsageStatsCollector.kt`)
- Tracks app usage sessions
- Runs periodically via WorkManager

### Normalization Layer

**NormalizationEngine** (`normalization/NormalizationEngine.kt`)
- Converts raw signals to typed events
- Resolves contacts across apps
- Extracts merchants from SMS

### Intelligence Layer

**RelationshipScoringEngine** (`intelligence/RelationshipScoringEngine.kt`)
- Computes priority, reciprocity, response speed, emotional closeness scores
- Detects one-sided relationships

**NotificationNoiseEngine** (`intelligence/NotificationNoiseEngine.kt`)
- Calculates noise score per app
- Recommends apps to mute

**SpendingPatternEngine** (`intelligence/SpendingPatternEngine.kt`)
- Analyzes spending patterns
- Detects anomalies and impulsive purchases

### Storage Layer

**LifeCompanionDatabase** (`data/LifeCompanionDatabase.kt`)
- Encrypted SQLite with SQLCipher
- Room DAOs for type-safe queries

## 🔐 Privacy & Security

**100% On-Device:**
- All data stored locally on your phone
- No cloud sync, no external servers
- MCP server only accessible via localhost

**Encryption:**
- Database encrypted with SQLCipher
- Passphrase stored in Android Keystore
- EncryptedSharedPreferences for settings

**Permissions:**
- Granular control over what's captured
- Audit log of all system actions
- Export/import for data portability

## 💬 Example Conversations

**With Claude + MCP:**

```
User: Who are my closest friends based on my phone usage?

Claude: [Calls get_relationship_insights with filter="priority"]

Based on your communication patterns:

1. Riya - Priority score 0.92, you reply instantly (avg 2 min)
2. Arjun - Reciprocity 0.85, very balanced relationship
3. Priya - High frequency (50+ msgs/week)

Would you like to mark these as priority contacts?
```

```
User: Am I spending too much on food delivery?

Claude: [Calls get_spending_insights + get_spending_events]

Past month:
- Food delivery: ₹8,450 (31 transactions)
- Average: ₹272/day
- 12 late-night orders (after 10 PM)
- You tagged 3 as "impulse", 2 as "regret"

Pattern: Most late-night orders after long work days.

Want to set a weekly budget?
```

## 🧪 Development

### Project Structure

```
android-mcp/
├── mcp-server/          # TypeScript MCP server
│   └── src/
│       └── index.ts     # Main server with all tools
├── android/             # Android app
│   └── app/src/main/java/com/lifecompanion/
│       ├── capture/     # Notification, SMS, Usage collectors
│       ├── normalization/  # Event normalization
│       ├── intelligence/   # Scoring engines
│       ├── data/        # Database, DAOs, entities
│       ├── api/         # Ktor HTTP server
│       └── ui/          # Compose UI
└── docs/                # Documentation
```

### Running Tests

```bash
# Android tests
cd android
./gradlew test

# MCP server tests
cd mcp-server
npm test
```

## 📊 Data Models

### Life Events

All signals normalized to typed events:
- `NotificationReceived`, `NotificationInteracted`
- `MessageReceived`, `MessageSent`
- `AppSessionStarted`, `AppSessionEnded`
- `MoneySpent`, `MoneyReceived`, `BillDue`
- `UserNote`

### Relationship Profile

Computed scores for each contact:
- **Priority Score** (0-1): How much this person matters
- **Reciprocity Score** (0-1): Balance of communication
- **Response Speed Score** (0-1): How fast you reply
- **Emotional Closeness Score** (0-1): Late-night chats, long conversations

### Spending Categories

Auto-classified into:
- Food Delivery, Restaurant, Transport, Shopping
- Entertainment, Bills, Groceries, Personal Care
- Gifts, Transfers

## 🤝 Contributing

Contributions welcome! This is an early-stage project.

**Areas for improvement:**
- On-device LLM for local processing
- More sophisticated pattern detection
- UI improvements
- Better SMS parsing for more banks
- Multi-language support

## 📄 License

MIT License - see [LICENSE](LICENSE) file

## 🙏 Acknowledgments

Built on:
- [Model Context Protocol (MCP)](https://modelcontextprotocol.io)
- [Anthropic Claude](https://anthropic.com)
- Android Jetpack (Room, WorkManager, Compose)
- [Ktor](https://ktor.io) for API server
- [SQLCipher](https://www.zetetic.net/sqlcipher/) for encryption

---

**Note:** This is experimental software. Use at your own risk. Always review what data is being captured and shared.
