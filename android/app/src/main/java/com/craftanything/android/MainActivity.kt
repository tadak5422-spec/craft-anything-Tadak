package com.craftanything.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val CraftDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    secondary = Color(0xFF818CF8),
    background = Color(0xFF0B1120),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1E293B),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFE2E8F0)
)

data class NavTab(val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = CraftDarkColors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CraftAnythingApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraftAnythingApp() {
    val tabs = listOf(
        NavTab("Home", Icons.Filled.Home),
        NavTab("Agent", Icons.Filled.Build),
        NavTab("Projects", Icons.Filled.List),
        NavTab("Keys", Icons.Filled.Lock),
        NavTab("More", Icons.Filled.Settings)
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Craft Anything",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Android Edition • Engine v0.6.1",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 2) {
                FloatingActionButton(onClick = { selectedTab = 1 }) {
                    Icon(Icons.Filled.Add, contentDescription = "New Project")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> HomeDashboard(onOpenAgent = { selectedTab = 1 }, onOpenKeys = { selectedTab = 3 })
                1 -> AgentWorkspacePlaceholder()
                2 -> ProjectsExplorerPlaceholder()
                3 -> ApiKeysPlaceholder()
                else -> MoreSettingsPlaceholder()
            }
        }
    }
}

@Composable
fun HomeDashboard(onOpenAgent: () -> Unit, onOpenKeys: () -> Unit) {
    val modules = listOf(
        "Engine Core (crates/engine)" to "Ready for Phase 6 JNI/UniFFI bridge",
        "Format & Binary Inspector (formats, disasm, js)" to "Pure Rust — runs directly on Android",
        "AI Multi-Provider Client (crates/ai)" to "Custom API keys via Android Keystore",
        "Cloud Worker & CDP Inspector" to "Scheduled for Phase 5 Remote API"
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Phase 4 Build Verified",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Understand how any software works, inspect projects, and craft your own on Android.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenAgent) {
                            Text("Open AI Workspace")
                        }
                        OutlinedButton(onClick = onOpenKeys) {
                            Text("API Keys")
                        }
                    }
                }
            }
        }

        items(modules) { (title, status) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = title, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
fun AgentWorkspacePlaceholder() {
    InfoScreenCard(
        title = "AI Coding Agent & Inspector",
        body = "Next phases will connect this workspace to custom AI providers, clean-room specs, live preview, and remote cloud builds."
    )
}

@Composable
fun ProjectsExplorerPlaceholder() {
    InfoScreenCard(
        title = "Project Explorer & Code Editor",
        body = "Create, inspect, edit, test, and export projects. Local workspace + sync history will be wired here."
    )
}

@Composable
fun ApiKeysPlaceholder() {
    InfoScreenCard(
        title = "Custom API Key Manager",
        body = "Phase 7 will add encrypted Android Keystore storage for OpenAI, Anthropic, and custom OpenAI-compatible providers."
    )
}

@Composable
fun MoreSettingsPlaceholder() {
    InfoScreenCard(
        title = "Remote Engine & Export Settings",
        body = "Configure your free Cloud Worker URL, test connection, inspect logs, and manage project exports."
    )
}

@Composable
fun InfoScreenCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
