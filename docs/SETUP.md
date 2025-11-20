# Setup Guide - Android Life Companion MCP

Complete step-by-step guide to get the Android Life Companion MCP server running.

## Prerequisites

- **Android Device**: API 26+ (Android 8.0+)
- **Development Machine**: macOS, Linux, or Windows
- **Node.js**: 20.x or later
- **Android Studio**: Latest version (for building the app)
- **ADB**: Android Debug Bridge installed

## Part 1: Build and Install Android App

### 1.1 Clone Repository

```bash
git clone https://github.com/your-username/android-mcp.git
cd android-mcp
```

### 1.2 Build Android App

**Option A: Using Android Studio**

1. Open Android Studio
2. File → Open → Select `android-mcp/android` folder
3. Wait for Gradle sync to complete
4. Build → Make Project
5. Run → Run 'app' (select your device)

**Option B: Using Command Line**

```bash
cd android
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 1.3 Grant Permissions

On your Android device:

1. **Open Life Companion app**

2. **Grant SMS Permission:**
   - Settings → Apps → Life Companion → Permissions
   - Enable SMS permission

3. **Grant Contacts Permission:**
   - Settings → Apps → Life Companion → Permissions
   - Enable Contacts permission

4. **Enable Notification Listener:**
   - Settings → Notifications → Notification access
   - Enable "Life Companion"

5. **Grant Usage Stats Permission:**
   - Settings → Apps → Special app access → Usage access
   - Enable "Life Companion"

## Part 2: Build and Run MCP Server

### 2.1 Install Dependencies

```bash
cd mcp-server
npm install
```

### 2.2 Build TypeScript

```bash
npm run build
```

This compiles `src/index.ts` to `dist/index.js`.

### 2.3 Test Connection

Make sure your Android device/emulator is running the Life Companion app.

```bash
# Test health endpoint
curl http://localhost:8080/health
```

Expected response:
```json
{
  "status": "ok",
  "service": "android-life-companion"
}
```

### 2.4 Run MCP Server

```bash
npm start
```

You should see:
```
Android Life Companion MCP Server running on stdio
Waiting for Android API service at http://localhost:8080
```

## Part 3: Configure Claude Desktop to Use MCP

### 3.1 Locate Claude Config

**macOS:**
```bash
~/Library/Application Support/Claude/claude_desktop_config.json
```

**Windows:**
```
%APPDATA%\Claude\claude_desktop_config.json
```

**Linux:**
```bash
~/.config/Claude/claude_desktop_config.json
```

### 3.2 Add MCP Server

Edit `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "android-life-companion": {
      "command": "node",
      "args": [
        "/absolute/path/to/android-mcp/mcp-server/dist/index.js"
      ]
    }
  }
}
```

**Important:** Replace `/absolute/path/to/android-mcp` with your actual path.

### 3.3 Restart Claude Desktop

Quit and reopen Claude Desktop for changes to take effect.

## Part 4: Verify Installation

### 4.1 Check MCP Connection

In Claude Desktop, type:

```
Can you list the available tools from the Android Life Companion?
```

Claude should list all 20+ tools (get_relationship_insights, get_spending_insights, etc.).

### 4.2 Test a Query

```
Who are my priority contacts based on my phone usage?
```

Claude should call `get_relationship_insights` and return data.

### 4.3 Generate Some Test Data

To see meaningful results, you need some data:

1. **Use your phone normally** for a few days
2. **Receive some notifications** (WhatsApp, Instagram, etc.)
3. **Receive transaction SMS** (make a purchase)
4. **Use some apps** (the system tracks usage every 6 hours)

Or, for testing, you can insert sample data directly into the database.

## Part 5: Troubleshooting

### Problem: MCP Server Can't Connect to Android

**Symptoms:** `Failed to communicate with Android service`

**Solutions:**
1. Ensure Android app is running
2. Check if port 8080 is accessible:
   ```bash
   curl http://localhost:8080/health
   ```
3. Restart the Android app
4. Check Android logs:
   ```bash
   adb logcat | grep LifeCompanion
   ```

### Problem: No Data Returned

**Symptoms:** Tools return empty results

**Solutions:**
1. Check if permissions are granted (see 1.3)
2. Wait for data collection (notifications, SMS, app usage)
3. Manually trigger usage stats collection:
   ```bash
   adb shell am startservice \
     -n com.lifecompanion/.capture.UsageStatsWorker
   ```

### Problem: Notification Listener Not Working

**Symptoms:** No notification events captured

**Solutions:**
1. Re-enable Notification Listener:
   - Settings → Notifications → Notification access
   - Disable and re-enable "Life Companion"
2. Restart Android app
3. Check logcat for errors:
   ```bash
   adb logcat | grep NotificationListener
   ```

### Problem: SMS Transactions Not Detected

**Symptoms:** get_spending_insights returns no data

**Solutions:**
1. Check if SMS permission is granted
2. Send yourself a test transaction SMS (forward an old one)
3. Verify SMS format matches patterns in `MerchantExtractor.kt`
4. Check database:
   ```bash
   adb shell
   run-as com.lifecompanion
   sqlite3 databases/life_companion.db
   SELECT * FROM spending_events;
   ```

### Problem: Database Encryption Errors

**Symptoms:** App crashes on startup

**Solutions:**
1. Clear app data:
   ```bash
   adb shell pm clear com.lifecompanion
   ```
2. Reinstall app
3. Check Android Keystore is working

## Part 6: Advanced Configuration

### 6.1 Customize Scoring Weights

Edit `RelationshipScoringEngine.kt`:

```kotlin
// Change response rate weight from 30% to 40%
score += responseRate * 0.4f  // was 0.3f
```

Rebuild and reinstall app.

### 6.2 Add Custom Spending Categories

Edit `CategoryClassifier.kt`:

```kotlin
// Add new category
SpendingCategory.GAMING ->
    lower.containsAny("steam", "playstation", "xbox")
```

### 6.3 Adjust Noise Score Formula

Edit `NotificationNoiseEngine.kt`:

```kotlin
// Increase weight of dismiss rate
val noiseScore = (dismissRate * 0.7f) + (ignoreRate * 0.2f) + ((1 - openRate) * 0.1f)
```

### 6.4 Change API Server Port

Edit `ApiServer.kt`:

```kotlin
server = embeddedServer(Netty, port = 8081, host = "127.0.0.1") {
```

And update MCP server client:

```typescript
constructor(baseUrl: string = "http://localhost:8081/api") {
```

## Part 7: Data Management

### 7.1 Export Data

```bash
adb shell am broadcast \
  -a com.lifecompanion.EXPORT_DATA \
  -n com.lifecompanion/.ExportReceiver
```

Data exported to `/sdcard/Android/data/com.lifecompanion/files/backup/`

### 7.2 View Database

```bash
adb shell
run-as com.lifecompanion
cd databases
sqlite3 life_companion.db

# List tables
.tables

# View events
SELECT * FROM events ORDER BY timestamp DESC LIMIT 10;

# View contacts
SELECT * FROM contacts;

# View relationship scores
SELECT * FROM relationship_profiles ORDER BY priorityScore DESC;
```

### 7.3 Clear All Data

```bash
adb shell pm clear com.lifecompanion
```

**Warning:** This deletes all captured data permanently.

## Part 8: Development Workflow

### 8.1 Live Development

**Android:**
```bash
cd android
./gradlew installDebug
adb logcat | grep LifeCompanion
```

**MCP Server:**
```bash
cd mcp-server
npm run watch  # Auto-recompile on changes
```

### 8.2 Debugging

**Android App:**
- Use Android Studio debugger
- Attach to process: Run → Attach Debugger to Android Process

**MCP Server:**
- Add `console.error()` statements
- Logs appear in Claude Desktop's MCP logs

**Database:**
- Use Android Studio's App Inspection tool
- View Room database in real-time

## Next Steps

- Read [ARCHITECTURE.md](ARCHITECTURE.md) for implementation details
- Check [API.md](API.md) for complete API reference
- See [EXAMPLES.md](EXAMPLES.md) for sample conversations

## Support

- Open an issue on GitHub
- Check existing issues for solutions
- Read source code comments for implementation details

---

**Tip:** Start small. Grant one permission at a time, verify data is being captured, then move to the next component.
