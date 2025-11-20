package com.lifecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifecompanion.api.MCPApiServer
import com.lifecompanion.ui.theme.LifeCompanionTheme
import timber.log.Timber

/**
 * Main Activity for Life Companion
 * Starts the MCP API server and provides basic UI
 */
class MainActivity : ComponentActivity() {

    private lateinit var apiServer: MCPApiServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start API server
        apiServer = MCPApiServer(applicationContext)
        apiServer.start()

        setContent {
            LifeCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        apiServer.stop()
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Android Life Companion",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "MCP Server Running",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Listening on http://localhost:8080",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "This app exposes your Android life signals\n(relationships, spending, attention)\nto AI assistants via the MCP protocol.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { /* TODO: Open settings */ }) {
            Text("Configure Permissions")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = { /* TODO: View insights */ }) {
            Text("View Insights")
        }
    }
}
