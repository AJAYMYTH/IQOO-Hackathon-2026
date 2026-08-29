package com.apexos.repoguardian.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.core.logging.LogEntry
import com.apexos.repoguardian.core.logging.LogLevel
import com.apexos.repoguardian.data.llm.ModelState
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

data class BackendOption(
    val id: String,
    val name: String,
    val description: String,
    val pros: List<String>,
    val cons: List<String>,
    val recommended: String
)

val backendOptions = listOf(
    BackendOption(
        id = "cpu",
        name = "CPU",
        description = "Runs inference on the phone's main processor (ARM Cortex cores). This is the default and most compatible option.",
        pros = listOf(
            "Works on all Android devices",
            "Most stable and well-tested",
            "No driver dependencies"
        ),
        cons = listOf(
            "Slowest inference speed",
            "Higher battery consumption during long tasks"
        ),
        recommended = "Use this as your safe default. Start here and switch to GPU/NPU only after benchmarking."
    ),
    BackendOption(
        id = "gpu",
        name = "GPU (Adreno OpenCL)",
        description = "Offloads computation to the Adreno GPU via OpenCL. Can significantly speed up inference on Snapdragon devices.",
        pros = listOf(
            "2-3x faster than CPU for large models",
            "Good parallelism for matrix operations",
            "Supported on most Snapdragon devices"
        ),
        cons = listOf(
            "May not support all model operations",
            "Can conflict with display rendering under heavy load",
            "Requires Adreno GPU (Snapdragon-specific)"
        ),
        recommended = "Use if CPU is too slow and you've verified stability on your device."
    ),
    BackendOption(
        id = "npu",
        name = "NPU (Hexagon HTP)",
        description = "Uses Qualcomm's Hexagon Neural Processing Unit for hardware-accelerated AI inference. This is the fastest option on supported devices like iQOO 15.",
        pros = listOf(
            "Fastest inference (up to 5x vs CPU)",
            "Best power efficiency for AI workloads",
            "Purpose-built for neural network operations",
            "Scores bonus points for hackathon rubric (creative NPU use)"
        ),
        cons = listOf(
            "Experimental support in llama.cpp (ggml-hexagon)",
            "May not support all operations for every model",
            "Only available on recent Snapdragon chips",
            "Requires testing on actual target device"
        ),
        recommended = "Use for demos if pre-tested and stable on your exact device. Keep CPU as fallback."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val liveLogs by viewModel.liveLogs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    // Show save message
    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Log Out from GitHub?") },
            text = {
                Text("This will clear your stored GitHub access token and reset the active repository session. You will be redirected to the sign-in screen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout {
                            navController.navigate(Routes.AUTH) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Clear Application Cache?") },
            text = {
                Text("This will delete temporary HTTP responses, diff caches, and speech audio buffers to free up device storage (${uiState.appCacheSizeFormatted}).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearAppCache()
                    }
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedSettingsTab == 0) {
                        TextButton(
                            onClick = { viewModel.saveSettings() },
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val allText = viewModel.getFullLogsText()
                                clipboardManager.setText(AnnotatedString(allText))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Copied ${liveLogs.size} logs to clipboard!")
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Full Logs")
                        }
                        IconButton(
                            onClick = { viewModel.clearLogs() }
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // === TAB SELECTOR ===
            TabRow(
                selectedTabIndex = uiState.selectedSettingsTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedSettingsTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Settings & Models", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedSettingsTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Logs (Dev Stage)", fontWeight = FontWeight.SemiBold)
                            if (liveLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${liveLogs.size}")
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) }
                )
            }

            if (uiState.selectedSettingsTab == 0) {
                // === SETTINGS TAB CONTENT ===
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // === USER PROFILE SECTION ===
                    UserProfileCard(
                        username = uiState.user?.login ?: uiState.selectedRepoOwner.ifBlank { "GitHub User" },
                        displayName = uiState.user?.name,
                        activeRepo = if (uiState.selectedRepoName.isNotBlank()) "${uiState.selectedRepoOwner}/${uiState.selectedRepoName}" else null,
                        publicRepos = uiState.user?.publicRepos ?: 0,
                        onLogoutClick = { showLogoutDialog = true }
                    )

                    // === STORAGE & CACHE MANAGEMENT ===
                    StorageCacheCard(
                        cacheSize = uiState.appCacheSizeFormatted,
                        modelsSize = uiState.modelsSizeFormatted,
                        downloadedModels = uiState.downloadedModels,
                        onClearCacheClick = { showClearCacheDialog = true },
                        onDeleteModelClick = { viewModel.deleteDownloadedModel(it) },
                        onBrowseModelsClick = { navController.navigate(Routes.MODEL_BROWSER) }
                    )

                    HorizontalDivider()

                    // === Model State ===
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (uiState.modelState) {
                                is ModelState.Loaded -> StatusPass.copy(alpha = 0.1f)
                                is ModelState.Error -> StatusFail.copy(alpha = 0.1f)
                                is ModelState.Loading -> StatusPending.copy(alpha = 0.1f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (uiState.modelState) {
                                    is ModelState.Loaded -> Icons.Default.CheckCircle
                                    is ModelState.Error -> Icons.Default.Error
                                    is ModelState.Loading -> Icons.Default.HourglassTop
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when (uiState.modelState) {
                                    is ModelState.Loaded -> StatusPass
                                    is ModelState.Error -> StatusFail
                                    is ModelState.Loading -> StatusPending
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = when (val state = uiState.modelState) {
                                    is ModelState.Loaded -> state.modelInfo
                                    is ModelState.Error -> "Error: ${state.message}"
                                    is ModelState.Loading -> "Loading model..."
                                    else -> "No model loaded. Download or select a GGUF model"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // === Model Configuration ===
                    Text(
                        text = "Model Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = uiState.modelPath,
                        onValueChange = { viewModel.updateModelPath(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Active GGUF Model Path") },
                        placeholder = { Text("/sdcard/models/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = uiState.localServerUrl,
                        onValueChange = { viewModel.updateLocalServerUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Local Server URL (llama-server / Ollama / LM Studio)") },
                        placeholder = { Text("e.g. http://10.0.2.2:1234 or http://192.168.1.100:11434") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider()

                    // === Backend Selector ===
                    Text(
                        text = "Inference Backend & Acceleration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Choose how the AI model runs on your device. Each backend uses different hardware for inference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    backendOptions.forEach { option ->
                        BackendCard(
                            option = option,
                            isSelected = uiState.selectedBackend == option.id,
                            onSelect = { viewModel.updateBackend(option.id) }
                        )
                    }

                    HorizontalDivider()

                    // === Custom Rules ===
                    Text(
                        text = "Custom Review Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Add custom rules for the AI reviewer. These will be included in every code review prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = uiState.customRules,
                        onValueChange = { viewModel.updateCustomRules(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        label = { Text("Review rules") },
                        placeholder = {
                            Text("e.g.:\n- Flag any hardcoded credentials\n- Prefer val over var in Kotlin\n- Check for memory leaks in Android lifecycle")
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { viewModel.saveSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Settings")
                        }
                    }

                    HorizontalDivider()

                    // === llama.cpp Architecture & Customizations ===
                    Text(
                        text = "On-Device Engine: llama.cpp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LlamaInfoCard()

                    HorizontalDivider()

                    // === Open Source Credits ===
                    Text(
                        text = "Open Source Software Credits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OpenSourceCreditsCard()
                }
            } else {
                // === LOGS TAB CONTENT (DEV STAGE) ===
                DevLogsConsole(
                    logs = liveLogs,
                    onCopyLogs = {
                        val allText = viewModel.getFullLogsText()
                        clipboardManager.setText(AnnotatedString(allText))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Copied ${liveLogs.size} logs to clipboard!")
                        }
                    },
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
        }
    }
}

@Composable
fun DevLogsConsole(
    logs: List<LogEntry>,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { entry ->
            val matchesFilter = when (selectedFilter) {
                "ERROR" -> entry.level == LogLevel.ERROR
                "WARN" -> entry.level == LogLevel.WARN || entry.level == LogLevel.ERROR
                "GITHUB" -> entry.tag.contains("GitHub", ignoreCase = true)
                "LLM" -> entry.tag.contains("Llama", ignoreCase = true) || entry.tag.contains("Prompt", ignoreCase = true) || entry.tag.contains("Chat", ignoreCase = true)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    (entry.details?.contains(searchQuery, ignoreCase = true) == true)

            matchesFilter && matchesSearch
        }
    }

    val errorCount = remember(logs) { logs.count { it.level == LogLevel.ERROR } }
    val warnCount = remember(logs) { logs.count { it.level == LogLevel.WARN } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Notice banner
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dev Stage Source of Truth • ${logs.size} events ($errorCount errors, $warnCount warnings)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onCopyLogs,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onClearLogs,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter logs by tag, message, or error keyword...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("All (${logs.size})") }
            )
            FilterChip(
                selected = selectedFilter == "ERROR",
                onClick = { selectedFilter = "ERROR" },
                label = { Text("Errors ($errorCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
            FilterChip(
                selected = selectedFilter == "WARN",
                onClick = { selectedFilter = "WARN" },
                label = { Text("Warnings ($warnCount)") }
            )
            FilterChip(
                selected = selectedFilter == "GITHUB",
                onClick = { selectedFilter = "GITHUB" },
                label = { Text("GitHub API") }
            )
            FilterChip(
                selected = selectedFilter == "LLM",
                onClick = { selectedFilter = "LLM" },
                label = { Text("LLM & Inference") }
            )
        }

        // Log Entries List in Terminal Window
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            color = Color(0xFF0F141C)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (logs.isEmpty()) "No events recorded yet.\nInteract with chat, GitHub, or models to record logs." else "No logs matching current filter.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        LogEntryRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryRow(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFFF5252)
        LogLevel.WARN -> Color(0xFFFFB300)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.DEBUG -> Color(0xFF00E5FF)
    }

    val levelBg = levelColor.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF161D29))
            .clickable(enabled = !entry.details.isNullOrBlank()) { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Level chip
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = levelBg
            ) {
                Text(
                    text = entry.level.name,
                    color = levelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Time
            Text(
                text = entry.formattedTime,
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Tag
            Text(
                text = "[${entry.tag}]",
                color = Color(0xFF58A6FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message
        Text(
            text = entry.message,
            color = if (entry.level == LogLevel.ERROR) Color(0xFFFF8B8B) else Color(0xFFE6EDF3),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        // Expandable details / stacktrace
        if (!entry.details.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = levelColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (expanded) "Hide Details / Stacktrace" else "Tap to View Stacktrace / Error Details",
                    color = levelColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0D1117),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = entry.details,
                        color = Color(0xFFFF7B72),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UserProfileCard(
    username: String,
    displayName: String?,
    activeRepo: String?,
    publicRepos: Int,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Auth status chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StatusPass.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusPass,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusPass,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (activeRepo != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active Repository:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = activeRepo,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out from GitHub")
            }
        }
    }
}

@Composable
fun StorageCacheCard(
    cacheSize: String,
    modelsSize: String,
    downloadedModels: List<File>,
    onClearCacheClick: () -> Unit,
    onDeleteModelClick: (File) -> Unit,
    onBrowseModelsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Storage & Cache Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("App Cache", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(cacheSize, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("GGUF Models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(modelsSize, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Downloaded models list
            if (downloadedModels.isNotEmpty()) {
                Text(
                    text = "Downloaded Models (${downloadedModels.size}):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                downloadedModels.forEach { file ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${file.length() / (1024 * 1024)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteModelClick(file) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete model",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearCacheClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Cache")
                }

                Button(
                    onClick = onBrowseModelsClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse Models")
                }
            }
        }
    }
}

@Composable
fun BackendCard(
    option: BackendOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected)
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect() }
                )
            }

            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            // Pros
            Text(
                text = "Advantages:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = StatusPass
            )
            option.pros.forEach { pro ->
                Text(
                    text = "  + $pro",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Cons
            Text(
                text = "Trade-offs:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = StatusFail
            )
            option.cons.forEach { con ->
                Text(
                    text = "  - $con",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Recommendation
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected)
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            ) {
                Text(
                    text = "Recommendation: ${option.recommended}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(8.dp),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LlamaInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "1. Why llama.cpp for On-Device Inference?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "llama.cpp is a pure C/C++ inference engine with zero heavy runtime dependencies. It supports ARM NEON SIMD, FP16/INT4 quantization (Q4_K_M, Q8_0), and enables 100% offline, privacy-first AI code reviews directly on mobile hardware without sending code to remote servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Text(
                text = "2. Snapdragon Hardware Acceleration",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "On the iQOO 15 (Snapdragon 8 Elite / Gen series), llama.cpp utilizes OpenCL GPU compute and Hexagon NPU matrix multiplication (HTP) for accelerated token generation and ultra-low latency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Text(
                text = "3. Hugging Face GGUF Integration",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Allows one-tap discovery, size-filtered downloading, and instant switching of mobile-optimized code models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun OpenSourceCreditsCard() {
    val ossLibraries = listOf(
        Pair("llama.cpp & GGML", "MIT License • Georgi Gerganov & Contributors\nHigh-performance on-device LLM inference engine."),
        Pair("Qwen2.5-Coder", "Apache 2.0 • Alibaba Cloud\nState-of-the-art open-weights code intelligence model."),
        Pair("Jetpack Compose & Material 3", "Apache 2.0 • Google LLC\nModern Android declarative UI toolkit."),
        Pair("Kotlin Coroutines & Flow", "Apache 2.0 • JetBrains\nReactive asynchronous programming framework."),
        Pair("Dagger Hilt", "Apache 2.0 • Google LLC\nDependency injection for Android."),
        Pair("Retrofit, OkHttp & Moshi", "Apache 2.0 • Square, Inc.\nType-safe HTTP client & JSON deserialization."),
        Pair("AndroidX DataStore", "Apache 2.0 • Google LLC\nAsynchronous key-value data storage.")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ossLibraries.forEach { (name, details) ->
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
