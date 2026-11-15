package com.craftanything.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engineSummary = remember { NativeEngine.getSummary(context.filesDir.absolutePath) }
    val engineTools = remember { NativeEngine.listTools() }

    var profiles by remember { mutableStateOf(SecureKeyStore.loadProfiles(context)) }
    var activeProfileId by remember { mutableStateOf(SecureKeyStore.getActiveId(context) ?: profiles.firstOrNull()?.id) }

    var projects by remember {
        mutableStateOf(
            ProjectManager.listProjects(context).ifEmpty {
                listOf(ProjectManager.createWebStarter(context, "my-first-app"))
            }
        )
    }
    var activeProject by remember { mutableStateOf(projects.first()) }
    var projectFiles by remember { mutableStateOf(ProjectManager.listFiles(context, activeProject)) }
    var activeFile by remember { mutableStateOf(projectFiles.firstOrNull() ?: "index.html") }
    var editorText by remember { mutableStateOf(ProjectManager.readFile(context, activeProject, activeFile)) }

    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var previewErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    val projectProfileId = ProjectManager.getProjectProfileId(context, activeProject) ?: activeProfileId
    val selectedProfile = profiles.firstOrNull { it.id == projectProfileId } ?: profiles.firstOrNull()

    fun refreshProject(name: String) {
        activeProject = name
        projects = ProjectManager.listProjects(context)
        projectFiles = ProjectManager.listFiles(context, name)
        activeFile = projectFiles.firstOrNull() ?: "index.html"
        editorText = ProjectManager.readFile(context, name, activeFile)
    }

    val tabs = listOf(
        NavTab("Home", Icons.Filled.Home),
        NavTab("Agent", Icons.Filled.Build),
        NavTab("Projects", Icons.Filled.List),
        NavTab("Keys", Icons.Filled.Lock),
        NavTab("Tools", Icons.Filled.Settings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Craft Anything", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (engineSummary.loaded)
                                "Rust Engine v${engineSummary.engineVersion} • ${engineSummary.toolCount} tools • $activeProject"
                            else
                                "Project: $activeProject • ${engineSummary.error ?: "No native lib"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    engineSummary = engineSummary,
                    activeProject = activeProject,
                    activeProfile = selectedProfile,
                    onGoAgent = { selectedTab = 1 },
                    onGoProjects = { selectedTab = 2 },
                    onGoKeys = { selectedTab = 3 },
                    onGoTools = { selectedTab = 4 },
                    onCreateProject = { newName ->
                        val created = ProjectManager.createWebStarter(context, newName)
                        refreshProject(created)
                        selectedTab = 2
                    }
                )
                1 -> AgentScreen(
                    activeProject = activeProject,
                    profile = selectedProfile,
                    messages = chatMessages,
                    previewErrors = previewErrors,
                    onSend = { prompt ->
                        if (selectedProfile == null) return@AgentScreen
                        chatMessages.add(ChatMessage("user", prompt))
                        scope.launch {
                            val ctx = ProjectManager.buildContextSummary(context, activeProject)
                            val res = AiClient.send(selectedProfile, chatMessages.toList(), ctx)
                            res.onSuccess { reply ->
                                chatMessages.add(ChatMessage("assistant", reply))
                                val saved = ProjectManager.applyAiFiles(context, activeProject, reply)
                                if (saved.isNotEmpty()) {
                                    refreshProject(activeProject)
                                }
                            }.onFailure { err ->
                                chatMessages.add(ChatMessage("assistant", "Error: ${err.message}"))
                            }
                        }
                    },
                    onOpenKeys = { selectedTab = 3 }
                )
                2 -> ProjectsScreen(
                    projects = projects,
                    activeProject = activeProject,
                    files = projectFiles,
                    activeFile = activeFile,
                    editorText = editorText,
                    profiles = profiles,
                    selectedProfileId = projectProfileId,
                    previewErrors = previewErrors,
                    onSelectProject = { refreshProject(it) },
                    onSelectFile = { f ->
                        activeFile = f
                        editorText = ProjectManager.readFile(context, activeProject, f)
                    },
                    onEditorChange = { editorText = it },
                    onSaveFile = {
                        ProjectManager.writeFile(context, activeProject, activeFile, editorText)
                        projectFiles = ProjectManager.listFiles(context, activeProject)
                    },
                    onNewFile = { name ->
                        ProjectManager.writeFile(context, activeProject, name, "")
                        projectFiles = ProjectManager.listFiles(context, activeProject)
                        activeFile = name
                        editorText = ""
                    },
                    onAssignProfile = { pid ->
                        ProjectManager.setProjectProfileId(context, activeProject, pid)
                    },
                    onPreviewError = { err ->
                        previewErrors = (previewErrors + err).distinct().takeLast(10)
                    },
                    onClearPreviewErrors = { previewErrors = emptyList() },
                    onFixErrorsWithAi = { selectedTab = 1 },
                    onImported = { refreshProject(activeProject) }
                )
                3 -> KeysScreen(
                    profiles = profiles,
                    activeId = activeProfileId,
                    onSave = { updated, newActive ->
                        profiles = updated
                        activeProfileId = newActive
                        SecureKeyStore.saveProfiles(context, updated)
                        SecureKeyStore.setActiveId(context, newActive)
                    }
                )
                else -> ToolsAndCloudScreen(engineTools)
            }
        }
    }
}

@Composable
fun HomeScreen(
    engineSummary: EngineSummary,
    activeProject: String,
    activeProfile: ProviderProfile?,
    onGoAgent: () -> Unit,
    onGoProjects: () -> Unit,
    onGoKeys: () -> Unit,
    onGoTools: () -> Unit,
    onCreateProject: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Craft Anything Workspace", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (engineSummary.loaded)
                            "Native Rust Engine active: ${engineSummary.toolCount} tools across ${engineSummary.toolsetCount} toolsets (clean-room=${engineSummary.cleanRoom})."
                        else
                            "Engine status: ${engineSummary.error}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Active Project: $activeProject", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "AI Model: ${activeProfile?.let { "${it.name} (${it.model})" } ?: "None — add key in Keys tab"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGoAgent) { Text("AI Agent") }
                        OutlinedButton(onClick = onGoKeys) { Text("API Keys") }
                        OutlinedButton(onClick = onGoTools) { Text("Rust Tools") }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Create New Project", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Project name (e.g. portfolio-site)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onCreateProject(newName)
                                    newName = ""
                                }
                            }
                        ) { Text("Create Web Project") }
                        OutlinedButton(onClick = onGoProjects) { Text("Open Explorer") }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentScreen(
    activeProject: String,
    profile: ProviderProfile?,
    messages: List<ChatMessage>,
    previewErrors: List<String>,
    onSend: (String) -> Unit,
    onOpenKeys: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        if (profile == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("No AI API Key Configured", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Add your own API key in the Keys tab to start generating and editing projects.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenKeys) { Text("Add API Key") }
                }
            }
            return
        }
        Text(
            "Project: $activeProject • ${profile.name} (${profile.model})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (previewErrors.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    onSend("Fix these runtime preview errors in my project:\n" + previewErrors.joinToString("\n"))
                }
            ) { Text("Auto-Fix ${previewErrors.size} Preview Errors") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.role == "user")
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (msg.role == "user") "You" else "Craft Agent",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(msg.content, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask AI to build, recreate, or fix code...") },
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onSend(input.trim())
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ProjectsScreen(
    projects: List<String>,
    activeProject: String,
    files: List<String>,
    activeFile: String,
    editorText: String,
    profiles: List<ProviderProfile>,
    selectedProfileId: String?,
    previewErrors: List<String>,
    onSelectProject: (String) -> Unit,
    onSelectFile: (String) -> Unit,
    onEditorChange: (String) -> Unit,
    onSaveFile: () -> Unit,
    onNewFile: (String) -> Unit,
    onAssignProfile: (String) -> Unit,
    onPreviewError: (String) -> Unit,
    onClearPreviewErrors: () -> Unit,
    onFixErrorsWithAi: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }
    var newFileName by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = ProjectManager.exportZip(context, activeProject, uri)
            statusMsg = if (ok) "Exported $activeProject.zip" else "Export failed"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val res = ProjectManager.importFile(context, activeProject, uri)
            statusMsg = res?.let { "Imported $it" } ?: "Import failed"
            onImported()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            projects.forEach { p ->
                FilterChip(
                    selected = p == activeProject,
                    onClick = { onSelectProject(p) },
                    label = { Text(p) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zipLauncher.launch("$activeProject.zip") }) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(" Export ZIP")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(" Import File")
            }
        }
        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        TabRow(selectedTabIndex = subTab) {
            listOf("Files", "Editor", "Preview", "History").forEachIndexed { idx, title ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (subTab) {
            0 -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newFileName,
                                onValueChange = { newFileName = it },
                                label = { Text("New file (e.g. script.js)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (newFileName.isNotBlank()) {
                                        onNewFile(newFileName.trim())
                                        newFileName = ""
                                        subTab = 1
                                    }
                                }
                            ) { Text("Add") }
                        }
                    }
                    if (profiles.isNotEmpty()) {
                        item {
                            Text("Project AI Model:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                profiles.forEach { prof ->
                                    FilterChip(
                                        selected = prof.id == selectedProfileId,
                                        onClick = { onAssignProfile(prof.id); statusMsg = "Model set to ${prof.model}" },
                                        label = { Text("${prof.name}: ${prof.model}") }
                                    )
                                }
                            }
                        }
                    }
                    items(files) { file ->
                        Card(
                            onClick = { onSelectFile(file); subTab = 1 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(file, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(activeFile, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSaveFile(); statusMsg = "Saved $activeFile" }) { Text("Save") }
                            OutlinedButton(onClick = { onSaveFile(); subTab = 2 }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("Run")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editorText,
                        onValueChange = onEditorChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    )
                }
            }
            2 -> {
                val baseDir = ProjectManager.projectDir(context, activeProject)
                val indexFile = java.io.File(baseDir, "index.html")
                Column(modifier = Modifier.fillMaxSize()) {
                    if (previewErrors.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("JS Error: ${previewErrors.last()}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onFixErrorsWithAi) { Text("Fix with AI") }
                                    OutlinedButton(onClick = onClearPreviewErrors) { Text("Clear") }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!indexFile.exists()) {
                        Text("No index.html in $activeProject. Create index.html to preview.")
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    webViewClient = WebViewClient()
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                                            if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                                onPreviewError("${cm.message()} (line ${cm.lineNumber()})")
                                            }
                                            return true
                                        }
                                    }
                                    loadUrl("file://${indexFile.absolutePath}")
                                }
                            },
                            update = { wv -> wv.loadUrl("file://${indexFile.absolutePath}") }
                        )
                    }
                }
            }
            else -> {
                val history = ProjectManager.getHistory(context, activeProject)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(history) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(item, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeysScreen(
    profiles: List<ProviderProfile>,
    activeId: String?,
    onSave: (List<ProviderProfile>, String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var presetIdx by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(SecureKeyStore.presets[0].name) }
    var kind by remember { mutableStateOf(SecureKeyStore.presets[0].kind) }
    var baseUrl by remember { mutableStateOf(SecureKeyStore.presets[0].baseUrl) }
    var model by remember { mutableStateOf(SecureKeyStore.presets[0].defaultModel) }
    var apiKey by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add AI Provider & Key (Android Keystore AES-256)", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SecureKeyStore.presets.forEachIndexed { idx, p ->
                            FilterChip(
                                selected = presetIdx == idx,
                                onClick = {
                                    presetIdx = idx
                                    name = p.name
                                    kind = p.kind
                                    baseUrl = p.baseUrl
                                    model = p.defaultModel
                                },
                                label = { Text(p.name) }
                            )
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Provider Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("HTTPS Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key (Stored Encrypted)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (apiKey.isNotBlank() && model.isNotBlank()) {
                                    val np = ProviderProfile(name = name, kind = kind, baseUrl = baseUrl, model = model, apiKey = apiKey)
                                    val updated = profiles + np
                                    onSave(updated, np.id)
                                    apiKey = ""
                                    testResult = "Saved securely!"
                                }
                            }
                        ) { Text("Save Key") }
                        OutlinedButton(
                            onClick = {
                                testResult = "Testing..."
                                scope.launch {
                                    val temp = ProviderProfile(name = name, kind = kind, baseUrl = baseUrl, model = model, apiKey = apiKey)
                                    testResult = SecureKeyStore.testConnection(temp)
                                }
                            }
                        ) { Text("Test Connection") }
                    }
                    if (testResult.isNotBlank()) {
                        Text(testResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        items(profiles, key = { it.id }) { prof ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (prof.id == activeId) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${prof.name} (${prof.model})", fontWeight = FontWeight.Bold)
                        Text("Key: ••••${prof.apiKey.takeLast(4)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    testResult = "Testing ${prof.name}..."
                                    testResult = SecureKeyStore.testConnection(prof)
                                }
                            }
                        ) { Text("Test") }
                        IconButton(
                            onClick = {
                                val next = profiles.filterNot { it.id == prof.id }
                                onSave(next, if (activeId == prof.id) next.firstOrNull()?.id else activeId)
                            }
                        ) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsAndCloudScreen(tools: List<EngineTool>) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var workerUrl by remember { mutableStateOf(SecureKeyStore.getWorkerUrl(context)) }
    var workerSaved by remember { mutableStateOf("") }

    val filtered = remember(query, tools) {
        if (query.isBlank()) tools else tools.filter {
            it.id.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cloud Worker URL (Phase 5 Remote API)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = workerUrl,
                        onValueChange = { workerUrl = it },
                        label = { Text("https://your-worker-url") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            SecureKeyStore.setWorkerUrl(context, workerUrl)
                            workerSaved = "Saved worker URL"
                        }
                    ) { Text("Save Worker URL") }
                    if (workerSaved.isNotBlank()) Text(workerSaved, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search ${tools.size} Original Rust Engine Tools") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        items(filtered) { tool ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${tool.title} (${tool.id})", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(tool.beginner.ifBlank { tool.summary }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
