package com.apexos.repoguardian.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.llm.ModelState
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.AppBottomBar
import com.apexos.repoguardian.ui.theme.*
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
        description = "Runs inference on the main processor (ARM Cortex cores). Default and most compatible option.",
        pros = listOf(
            "Compatible across all Android devices",
            "Highest stability and precision",
            "Zero driver dependencies"
        ),
        cons = listOf(
            "Standard inference speed",
            "Higher power consumption during sustained generation"
        ),
        recommended = "Safe baseline. Start here and switch to GPU/NPU after benchmarking."
    ),
    BackendOption(
        id = "gpu",
        name = "GPU (Adreno OpenCL)",
        description = "Offloads matrix computation to the Adreno GPU via OpenCL for accelerated throughput on Snapdragon chipsets.",
        pros = listOf(
            "2-3x speedup over CPU for larger models",
            "High parallelism for prompt ingestion",
            "Supported across modern Snapdragon devices"
        ),
        cons = listOf(
            "Higher VRAM footprint",
            "Requires Snapdragon Adreno compute drivers"
        ),
        recommended = "Recommended for fast interactive review generation on supported devices."
    ),
    BackendOption(
        id = "npu",
        name = "NPU (Hexagon HTP)",
        description = "Leverages Qualcomm Hexagon Neural Processing Unit for hardware-accelerated INT4/INT8 tensor math with minimal thermal throttling.",
        pros = listOf(
            "Highest power efficiency and throughput",
            "Dedicated tensor acceleration pipeline",
            "Native Snapdragon 8 Elite / Gen architecture support"
        ),
        cons = listOf(
            "Requires supported quantized tensor formats",
            "Platform specific acceleration"
        ),
        recommended = "Optimal for Snapdragon 8 Elite and iQOO high-performance execution."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = StatusFail) },
            title = { Text("Log Out from GitHub?", color = BrandOnBg) },
            text = {
                Text("This will clear your stored GitHub access token and reset the active repository session. You will be redirected to the sign-in screen.", color = BrandOnBgMuted)
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusFail)
                ) {
                    Text("Log Out", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text("Cancel", color = BrandOnBg)
                }
            },
            containerColor = BrandSurfaceHigh
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = BrandEmeraldLight) },
            title = { Text("Clear Application Cache?", color = BrandOnBg) },
            text = {
                Text("This will delete temporary HTTP responses, diff caches, and speech audio buffers (${uiState.appCacheSizeFormatted}).", color = BrandOnBgMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearAppCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Clear Cache", color = OnEmerald)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearCacheDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text("Cancel", color = BrandOnBg)
                }
            },
            containerColor = BrandSurfaceHigh
        )
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            Surface(
                color = GlassBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = BrandOnBg,
                            actionIconContentColor = BrandOnBgMuted
                        ),
                        title = {
                            Text("Settings & Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { viewModel.saveSettings() },
                                enabled = !uiState.isSaving
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = BrandEmerald
                                    )
                                } else {
                                    Text("Save", fontWeight = FontWeight.Bold, color = BrandEmeraldLight)
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = BrandBorder, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = Routes.SETTINGS,
                navController = navController
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BrandBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Profile section
            UserProfileCard(
                username = uiState.user?.login ?: uiState.selectedRepoOwner.ifBlank { "GitHub User" },
                displayName = uiState.user?.name,
                activeRepo = if (uiState.selectedRepoName.isNotBlank()) "${uiState.selectedRepoOwner}/${uiState.selectedRepoName}" else null,
                publicRepos = uiState.user?.publicRepos ?: 0,
                onLogoutClick = { showLogoutDialog = true }
            )

            // Storage and cache section
            StorageCacheCard(
                cacheSize = uiState.appCacheSizeFormatted,
                modelsSize = uiState.modelsSizeFormatted,
                downloadedModels = uiState.downloadedModels,
                onClearCacheClick = { showClearCacheDialog = true },
                onDeleteModelClick = { viewModel.deleteDownloadedModel(it) },
                onBrowseModelsClick = { navController.navigate(Routes.MODEL_BROWSER) }
            )

            HorizontalDivider(color = BrandBorder)

            // Model status card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (uiState.modelState) {
                    is ModelState.Loaded -> StatusPass.copy(alpha = 0.08f)
                    is ModelState.Error -> StatusFail.copy(alpha = 0.08f)
                    is ModelState.Loading -> StatusPending.copy(alpha = 0.08f)
                    else -> BrandSurface
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (uiState.modelState) {
                        is ModelState.Loaded -> StatusPass.copy(alpha = 0.3f)
                        is ModelState.Error -> StatusFail.copy(alpha = 0.3f)
                        is ModelState.Loading -> StatusPending.copy(alpha = 0.3f)
                        else -> BrandBorder
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (uiState.modelState) {
                            is ModelState.Loaded -> Icons.Default.CheckCircle
                            is ModelState.Error -> Icons.Default.ErrorOutline
                            is ModelState.Loading -> Icons.Default.HourglassTop
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (uiState.modelState) {
                            is ModelState.Loaded -> StatusPass
                            is ModelState.Error -> StatusFail
                            is ModelState.Loading -> StatusPending
                            else -> BrandOnBgMuted
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (val state = uiState.modelState) {
                            is ModelState.Loaded -> state.modelInfo
                            is ModelState.Error -> "Error: ${state.message}"
                            is ModelState.Loading -> "Loading model..."
                            else -> "No model loaded. Download or select a GGUF model"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBg
                    )
                }
            }

            // Model path input
            Text(
                text = "Model Path Configuration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )

            OutlinedTextField(
                value = uiState.modelPath,
                onValueChange = { viewModel.updateModelPath(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Active GGUF Model Path", color = BrandOnBgSubtle) },
                placeholder = { Text("/sdcard/models/qwen2.5-coder-3b-q4_k_m.gguf", color = BrandOnBgSubtle) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BrandSurface,
                    unfocusedContainerColor = BrandSurface,
                    focusedBorderColor = BrandEmerald,
                    unfocusedBorderColor = BrandBorder,
                    focusedTextColor = BrandOnBg,
                    unfocusedTextColor = BrandOnBg
                )
            )

            HorizontalDivider(color = BrandBorder)

            // Hardware acceleration & backend options
            Text(
                text = "Inference Backend & Acceleration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )

            Text(
                text = "Select the hardware execution backend for local llama.cpp computation.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
            )

            backendOptions.forEach { option ->
                BackendCard(
                    option = option,
                    isSelected = uiState.selectedBackend == option.id,
                    onSelect = { viewModel.updateBackend(option.id) }
                )
            }

            HorizontalDivider(color = BrandBorder)

            // Custom review rules
            Text(
                text = "Custom Review Rules",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )

            Text(
                text = "Custom rules injected into the prompt for on-device code reviews.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
            )

            OutlinedTextField(
                value = uiState.customRules,
                onValueChange = { viewModel.updateCustomRules(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp),
                label = { Text("Review Rules", color = BrandOnBgSubtle) },
                placeholder = {
                    Text("- Flag hardcoded credentials\n- Enforce Kotlin immutability\n- Check Android lifecycle memory safety", color = BrandOnBgSubtle)
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BrandSurface,
                    unfocusedContainerColor = BrandSurface,
                    focusedBorderColor = BrandEmerald,
                    unfocusedBorderColor = BrandBorder,
                    focusedTextColor = BrandOnBg,
                    unfocusedTextColor = BrandOnBg
                )
            )

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OnEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...", color = OnEmerald)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, tint = OnEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Configuration", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = BrandBorder)

            // Architecture Info
            Text(
                text = "Architecture & Engine Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )
            LlamaInfoCard()

            HorizontalDivider(color = BrandBorder)

            // Open source credits
            Text(
                text = "Open Source Software Acknowledgments",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )
            OpenSourceCreditsCard()

            Spacer(Modifier.height(16.dp))
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(BrandEmeraldMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandEmeraldLight
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: username,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusPass.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.25f))
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
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (activeRepo != null) {
                HorizontalDivider(color = BrandBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = BrandEmeraldLight
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active Repository:",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBgMuted
                        )
                    }
                    Text(
                        text = activeRepo,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandEmeraldLight
                    )
                }
            }

            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFail)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out from GitHub")
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
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
                    tint = BrandEmeraldLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Storage & Cache",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandOnBg
                )
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("App Cache", style = MaterialTheme.typography.labelSmall, color = BrandOnBgSubtle)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(cacheSize, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandOnBg)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("GGUF Models", style = MaterialTheme.typography.labelSmall, color = BrandOnBgSubtle)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(modelsSize, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandOnBg)
                    }
                }
            }

            // Downloaded models list
            if (downloadedModels.isNotEmpty()) {
                Text(
                    text = "Downloaded Models (${downloadedModels.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandOnBgMuted
                )

                downloadedModels.forEach { file ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = BrandSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = BrandOnBg
                                )
                                Text(
                                    text = "${file.length() / (1024 * 1024)} MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandOnBgMuted
                                )
                            }
                            IconButton(
                                onClick = { onDeleteModelClick(file) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete model",
                                    tint = StatusFail.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
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
                Button(
                    onClick = onClearCacheClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandSurfaceElev,
                        contentColor = BrandOnBg
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandOnBg)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Cache", color = BrandOnBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Button(
                    onClick = onBrowseModelsClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandSurfaceElev),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandEmeraldLight)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse Models", color = BrandOnBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandOnBg
                )
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect() },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = BrandEmerald,
                        unselectedColor = BrandOnBgSubtle
                    )
                )
            }

            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
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
                    text = "+ $pro",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgMuted
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
                    text = "- $con",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgMuted
                )
            }

            // Recommendation
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BrandSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
            ) {
                Text(
                    text = "Recommendation: ${option.recommended}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    color = BrandOnBgMuted
                )
            }
        }
    }
}

@Composable
fun LlamaInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "1. Why llama.cpp for On-Device Inference?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BrandEmeraldLight
            )
            Text(
                text = "llama.cpp is a pure C/C++ inference engine with zero heavy runtime dependencies. It supports ARM NEON SIMD, FP16/INT4 quantization (Q4_K_M, Q8_0), and enables 100% offline, privacy-first AI code reviews directly on mobile hardware without sending code to remote servers.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
            )

            Text(
                text = "2. Snapdragon Hardware Acceleration",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BrandEmeraldLight
            )
            Text(
                text = "On the iQOO 15 (Snapdragon 8 Elite / Gen series), llama.cpp utilizes OpenCL GPU compute and Hexagon NPU matrix multiplication (HTP) for accelerated token generation and ultra-low latency.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
            )

            Text(
                text = "3. Hugging Face GGUF Integration",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BrandEmeraldLight
            )
            Text(
                text = "Allows one-tap discovery, size-filtered downloading, and instant switching of mobile-optimized code models.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ossLibraries.forEach { (name, details) ->
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandOnBgMuted
                    )
                }
            }
        }
    }
}
