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
import android.content.Intent
import android.net.Uri
import com.apexos.repoguardian.BuildConfig
import com.apexos.repoguardian.data.github.models.ReleaseAsset
import com.apexos.repoguardian.data.llm.ModelState
import com.apexos.repoguardian.data.update.UpdateUiState
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
    val updateUiState by viewModel.updateUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageSizes()
    }

    LaunchedEffect(updateUiState) {
        if (updateUiState is UpdateUiState.UpdateAvailable) {
            showUpdateDialog = true
        }
    }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }

    val availableUpdate = updateUiState as? UpdateUiState.UpdateAvailable
    if (showUpdateDialog && availableUpdate != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = BrandEmeraldLight) },
            title = {
                Text(
                    text = "New Update Available: v${availableUpdate.newVersion}",
                    color = BrandOnBg,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "A new version of Repo Guardian is ready to install directly on your device.\n\n" +
                                "• Current Installed: v${availableUpdate.currentVersion}\n" +
                                "• Latest Release: v${availableUpdate.newVersion}" +
                                (availableUpdate.apkAsset?.let { "\n• Package Size: ${it.sizeFormatted}" } ?: ""),
                        color = BrandOnBgMuted,
                        fontSize = 13.sp
                    )

                    if (!availableUpdate.release.body.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandSurfaceElev,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                        ) {
                            Text(
                                text = availableUpdate.release.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBg,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        availableUpdate.apkAsset?.let { asset ->
                            viewModel.downloadAndInstallUpdate(asset, availableUpdate.newVersion)
                        } ?: run {
                            availableUpdate.release.htmlUrl?.let { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                navController.context.startActivity(intent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install Update", color = OnEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUpdateDialog = false
                        viewModel.dismissUpdateState()
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text("Later", color = BrandOnBg)
                }
            },
            containerColor = BrandSurfaceHigh
        )
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandSurfaceElev),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text("Clear Cache", color = BrandOnBg, fontWeight = FontWeight.SemiBold)
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
                onBrowseModelsClick = { navController.navigate(Routes.MODEL_BROWSER) },
                onRefreshCache = { viewModel.refreshStorageSizes() }
            )

            // App Updates & Versioning section
            AppUpdateCard(
                updateState = updateUiState,
                onCheckForUpdates = { viewModel.checkForUpdates() },
                onInstallUpdate = { asset, newVersion ->
                    viewModel.downloadAndInstallUpdate(asset, newVersion)
                },
                onDismissUpdate = { viewModel.dismissUpdateState() },
                onRetryInstallDownloaded = { file -> viewModel.retryInstallDownloadedApk(file) }
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
    onBrowseModelsClick: () -> Unit,
    onRefreshCache: () -> Unit
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandEmeraldMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = BrandEmeraldLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Storage & Cache",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                    Text(
                        text = "Live app cache, HTTP buffers & models",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandOnBgMuted
                    )
                }

                IconButton(
                    onClick = onRefreshCache,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh cache stats",
                        tint = BrandEmeraldLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                color = BrandOnBg
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
                color = BrandOnBg
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
                color = BrandOnBg
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

@Composable
fun AppUpdateCard(
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: (asset: ReleaseAsset, newVersion: String) -> Unit,
    onDismissUpdate: () -> Unit,
    onRetryInstallDownloaded: (File) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

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
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandEmeraldMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = BrandEmeraldLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Updates & Releases",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                    Text(
                        text = "Repo Guardian v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text(
                        text = "ARM64-v8a",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandEmeraldLight,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = BrandBorder)

            // Dynamic State Body
            when (updateState) {
                is UpdateUiState.Idle -> {
                    Text(
                        text = "Check GitHub releases for the latest updates, performance enhancements, and on-device model optimizations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )

                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                    }
                }

                is UpdateUiState.Checking -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = BrandEmeraldLight
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Checking for new releases on GitHub...",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBg
                            )
                        }
                    }
                }

                is UpdateUiState.UpToDate -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusPass.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPass, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Up to date (v${updateState.currentVersion})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPass
                                )
                                Text(
                                    text = "Checked: ${updateState.checkedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandOnBgMuted
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOnBg)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Again", fontSize = 12.5.sp)
                    }
                }

                is UpdateUiState.UpdateAvailable -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandEmeraldMuted,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Upgrade, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Version v${updateState.newVersion} Available",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldLight
                                )
                            }

                            Text(
                                text = "Current: v${updateState.currentVersion} • Package: ${updateState.apkAsset?.name ?: "app-release.apk"} (${updateState.apkAsset?.sizeFormatted ?: "Ready"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandOnBgMuted
                            )

                            if (!updateState.release.body.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandSurfaceHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = updateState.release.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BrandOnBg,
                                        maxLines = 4,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (updateState.apkAsset != null) {
                                    Button(
                                        onClick = { onInstallUpdate(updateState.apkAsset, updateState.newVersion) },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Install Update", color = OnEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            updateState.release.htmlUrl?.let { url ->
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View on GitHub", color = OnEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = onDismissUpdate,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOnBg)
                                ) {
                                    Text("Later", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                is UpdateUiState.Downloading -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmeraldLight.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading Update: ${updateState.fileName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandOnBg
                                )
                                Text(
                                    text = "${(updateState.progressPercent * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldLight
                                )
                            }

                            LinearProgressIndicator(
                                progress = { updateState.progressPercent },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = BrandEmerald,
                                trackColor = BrandSurfaceElev
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dlMb = updateState.downloadedBytes.toDouble() / (1024.0 * 1024.0)
                                val totMb = updateState.totalBytes.toDouble() / (1024.0 * 1024.0)
                                Text(
                                    text = String.format("%.1f / %.1f MB", dlMb, totMb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandOnBgMuted
                                )
                                Text(
                                    text = updateState.speedFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandGreige
                                )
                            }
                        }
                    }
                }

                is UpdateUiState.DownloadReady -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusPass.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPass, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Update Downloaded & Ready to Install",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPass
                                )
                            }

                            Text(
                                text = "The package installer has been launched. Tap below if you need to reopen the installation prompt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )

                            Button(
                                onClick = { onRetryInstallDownloaded(updateState.apkFile) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Package Installer", color = OnEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is UpdateUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusFail.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusFail, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusFail
                            )
                        }
                    }

                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnEmerald)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
