package com.apexos.repoguardian.ui.modelbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.huggingface.DownloadProgress
import com.apexos.repoguardian.data.huggingface.FeaturedModel
import com.apexos.repoguardian.data.huggingface.HfModelFile
import com.apexos.repoguardian.data.huggingface.HfModelSearchResult
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.AppBottomBar
import com.apexos.repoguardian.ui.components.NonTechGuideDialog
import com.apexos.repoguardian.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowserScreen(
    navController: NavController,
    viewModel: ModelBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        viewModel.refreshState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = uiState.downloadingFilename != null) {
        viewModel.setShowExitDialog(true)
    }

    if (uiState.showExitDialog) {
        val progressPercent = ((uiState.downloadProgress?.progressPercent ?: 0f) * 100).toInt()
        AlertDialog(
            onDismissRequest = { viewModel.setShowExitDialog(false) },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandEmeraldLight) },
            title = { Text("Download in Progress", color = BrandOnBg) },
            text = {
                Text(
                    "Downloading ${uiState.downloadingFilename ?: "model"} (${progressPercent}%).\n\nWould you like the download to continue safely in the background? You will receive a notification and the model will auto-load when done.",
                    color = BrandOnBgMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setShowExitDialog(false)
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Keep in Background", color = OnEmerald)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelDownload()
                        navController.popBackStack()
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text("Cancel Download", color = StatusFail)
                }
            },
            containerColor = BrandSurfaceHigh
        )
    }

    var showGuide by remember { mutableStateOf(false) }

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
                            Text(
                                text = if (uiState.selectedModel != null) "Select Quantization"
                                else "Model Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (uiState.downloadingFilename != null) {
                                    viewModel.setShowExitDialog(true)
                                } else if (uiState.selectedModel != null) {
                                    viewModel.clearSelection()
                                } else {
                                    navController.popBackStack()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showGuide = true }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help Guide")
                            }
                        }
                    )
                    HorizontalDivider(color = BrandBorder, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            AppBottomBar(
                currentRoute = Routes.MODEL_BROWSER,
                navController = navController
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BrandBackground)
        ) {
            // Success Notification Card
            uiState.successMessage?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = StatusPass.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPass, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = StatusPass, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = StatusPass, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Error Notification Card
            uiState.error?.let { err ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = StatusFail.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusFail, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = StatusFail, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = StatusFail, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (uiState.selectedModel == null) {
                // Tab Selection Row with Pure Dark & Emerald highlight
                Surface(
                    color = BrandSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = uiState.selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = BrandEmeraldLight,
                        divider = { HorizontalDivider(color = BrandBorder) }
                    ) {
                        Tab(
                            selected = uiState.selectedTab == 0,
                            onClick = { viewModel.selectTab(0) },
                            text = {
                                Text(
                                    "Featured",
                                    color = if (uiState.selectedTab == 0) BrandEmeraldLight else BrandOnBgMuted,
                                    fontWeight = if (uiState.selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = uiState.selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            text = {
                                Text(
                                    "Search HF",
                                    color = if (uiState.selectedTab == 1) BrandEmeraldLight else BrandOnBgMuted,
                                    fontWeight = if (uiState.selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = uiState.selectedTab == 2,
                            onClick = { viewModel.selectTab(2) },
                            text = {
                                Text(
                                    "Downloaded (${uiState.downloadedModels.size})",
                                    color = if (uiState.selectedTab == 2) BrandEmeraldLight else BrandOnBgMuted,
                                    fontWeight = if (uiState.selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing || (uiState.isSearching && uiState.selectedTab == 1),
                    onRefresh = { viewModel.refreshCurrentTab() },
                    state = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = refreshState,
                            isRefreshing = uiState.isRefreshing || (uiState.isSearching && uiState.selectedTab == 1),
                            containerColor = BrandSurfaceElev,
                            color = BrandEmeraldLight,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    when (uiState.selectedTab) {
                        0 -> FeaturedModelsTab(uiState = uiState, viewModel = viewModel)
                        1 -> SearchModelsTab(uiState = uiState, viewModel = viewModel)
                        2 -> DownloadedModelsTab(uiState = uiState, viewModel = viewModel)
                    }
                }
            } else {
                val selected = uiState.selectedModel!!
                ModelFilesView(selected = selected, uiState = uiState, viewModel = viewModel)
            }
        }
    }

    if (showGuide) {
        NonTechGuideDialog(
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
private fun FeaturedModelsTab(
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = BrandSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Curated on-device models with background download support and automatic activation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }
            }
        }

        items(uiState.featuredModels) { model ->
            val isDownloaded = uiState.downloadedModels.any { it.name == model.filename }
            val downloadedFile = uiState.downloadedModels.firstOrNull { it.name == model.filename }
            val isActive = isDownloaded && downloadedFile != null && !uiState.activeModelPath.isNullOrBlank() && downloadedFile.absolutePath == uiState.activeModelPath
            val isCurrentlyDownloading = uiState.downloadingFilename == model.filename

            FeaturedModelCard(
                model = model,
                isDownloaded = isDownloaded,
                isActive = isActive,
                isDownloading = isCurrentlyDownloading,
                progress = if (isCurrentlyDownloading) uiState.downloadProgress else null,
                isLoadingModel = uiState.isLoadingModel && isActive,
                onDownload = { viewModel.downloadFeaturedModel(model) },
                onActivate = { downloadedFile?.let { viewModel.loadAndActivateModel(it) } },
                onCancel = { viewModel.cancelDownload() }
            )
        }

        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
private fun FeaturedModelCard(
    model: FeaturedModel,
    isDownloaded: Boolean,
    isActive: Boolean,
    isDownloading: Boolean,
    progress: DownloadProgress?,
    isLoadingModel: Boolean,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) BrandSurfaceElev else BrandSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) BrandEmerald.copy(alpha = 0.5f) else BrandBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isActive) BrandEmeraldMuted else BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) BrandEmeraldLight.copy(alpha = 0.4f) else BrandBorder)
                ) {
                    Text(
                        text = model.categoryBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) BrandEmeraldLight else BrandOnBgMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Active Status Indicator
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusPass.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(StatusPass)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACTIVE ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusPass
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(12.dp), tint = BrandOnBgSubtle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = model.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = BrandOnBgMuted)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(12.dp), tint = BrandOnBgSubtle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = model.quant, style = MaterialTheme.typography.labelSmall, color = BrandEmeraldLight)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Recommended for
            Text(
                text = "Best for: ${model.recommendedFor}",
                style = MaterialTheme.typography.labelSmall,
                color = BrandGreige
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action / Progress Section
            if (isDownloading && progress != null) {
                DownloadProgressBar(progress = progress, onCancel = onCancel)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isActive -> {
                            OutlinedButton(
                                onClick = onActivate,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmeraldLight.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandEmeraldLight)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Active Model", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        isDownloaded -> {
                            Button(
                                onClick = onActivate,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                enabled = !isLoadingModel
                            ) {
                                if (isLoadingModel) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = OnEmerald,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Loading...", color = OnEmerald)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Model", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressBar(
    progress: DownloadProgress,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = BrandSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = BrandEmeraldLight
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading in background...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandOnBg
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = StatusFail,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandEmerald,
                trackColor = BrandSurfaceElev
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = progress.downloadedFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandOnBgMuted
                )

                Text(
                    text = "${progress.speedFormatted} • ETA: ${progress.etaFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandGreige
                )
            }
        }
    }
}

@Composable
private fun SearchModelsTab(
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
) {
    val quickTags = listOf("coder gguf", "qwen2.5-coder", "deepseek-coder", "starcoder", "phi-3.5")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search Hugging Face models...", color = BrandOnBgSubtle, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandOnBgSubtle) },
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
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.search() },
                colors = ButtonDefaults.buttonColors(containerColor = BrandSurfaceElev),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Search", color = BrandOnBg)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickTags) { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                    modifier = Modifier.clickable {
                        viewModel.updateSearchQuery(tag)
                        viewModel.search()
                    }
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandOnBgMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandEmerald)
                        Spacer(Modifier.height(12.dp))
                        Text("Searching Hugging Face models...", style = MaterialTheme.typography.bodySmall, color = BrandOnBgMuted)
                    }
                }
            }
            uiState.searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = BrandOnBgSubtle, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "No models found. Try a different search query.",
                            color = BrandOnBgSubtle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults) { model ->
                        ModelSearchItem(
                            model = model,
                            onClick = { viewModel.selectModel(model) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSearchItem(model: HfModelSearchResult, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = BrandOnBg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = BrandOnBgSubtle)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatNumber(model.downloads),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp), tint = BrandOnBgSubtle)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatNumber(model.likes),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelFilesView(
    selected: HfModelSearchResult,
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
) {
    val context = LocalContext.current
    val hfUrl = "https://huggingface.co/${selected.id}"
    val hfTreeUrl = "https://huggingface.co/${selected.id}/tree/main"

    // Check if model has gating or licensing restrictions
    val isGated = selected.tags.any { it.contains("gated", ignoreCase = true) }
    val hasCustomLicense = selected.tags.any { it.contains("license:other", ignoreCase = true) || it.contains("license:non-commercial", ignoreCase = true) }
    val hasNotice = isGated || hasCustomLicense

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Model Header Card with Direct Web Download Option
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = BrandSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selected.id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = BrandEmeraldLight)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selected.downloads} downloads",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusPending)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selected.likes} likes",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Web Actions Row (Always Available)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hfTreeUrl))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download via Web", color = OnEmerald, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hfUrl))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOnBg)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("HF Page", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Conditional Notice / Disclaimer (Displayed only if restriction exists)
        if (hasNotice) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StatusPending.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPending.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = StatusPending, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hugging Face Access Notice",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusPending
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isGated) "This model repository may require accepting terms on Hugging Face before download. You can still download directly or proceed through the browser."
                                else "This model includes custom licensing. Please ensure usage complies with the repository license.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Quantization Files Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Compatible Quantizations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandOnBg
                )
                Text(
                    text = "${uiState.modelFiles.size} available",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandGreige
                )
            }
        }

        // Files List / Loading / Fallback
        when {
            uiState.isLoadingFiles -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandEmerald, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching quantization files from Hugging Face...", style = MaterialTheme.typography.bodySmall, color = BrandOnBgMuted)
                        }
                    }
                }
            }
            uiState.modelFiles.isEmpty() -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = BrandSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.LayersClear, contentDescription = null, tint = BrandOnBgSubtle, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Direct GGUF Files (≤ 4GB) in Root",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandOnBg
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This repository may contain larger quantizations (>4GB), non-GGUF weights, or requires downloading directly from the Hugging Face web repository.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hfTreeUrl))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download via Hugging Face Web", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val modelShortName = selected.id.substringAfterLast('/').replace("-GGUF", "", ignoreCase = true)
                            OutlinedButton(
                                onClick = {
                                    viewModel.searchWithQuery("$modelShortName gguf")
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Search Alternative GGUF Quants", color = BrandOnBg, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            else -> {
                items(uiState.modelFiles) { file ->
                    val isDownloaded = uiState.downloadedModels.any { it.name == file.filename }
                    val downloadedFile = uiState.downloadedModels.firstOrNull { it.name == file.filename }
                    val isActive = isDownloaded && downloadedFile != null && !uiState.activeModelPath.isNullOrBlank() && downloadedFile.absolutePath == uiState.activeModelPath
                    val isCurrentlyDownloading = uiState.downloadingFilename == file.filename

                    ModelFileItem(
                        file = file,
                        modelId = selected.id,
                        isDownloaded = isDownloaded,
                        isActive = isActive,
                        isDownloading = isCurrentlyDownloading,
                        progress = if (isCurrentlyDownloading) uiState.downloadProgress else null,
                        onDownload = { viewModel.downloadFile(selected.id, file.filename) },
                        onActivate = { downloadedFile?.let { viewModel.loadAndActivateModel(it) } },
                        onCancel = { viewModel.cancelDownload() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelFileItem(
    file: HfModelFile,
    modelId: String,
    isDownloaded: Boolean,
    isActive: Boolean,
    isDownloading: Boolean,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val directDownloadUrl = "https://huggingface.co/$modelId/resolve/main/${file.filename}"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = BrandOnBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (file.sizeInGb >= 1.0) String.format("%.2f GB", file.sizeInGb) else "${file.sizeInMb} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBgMuted
                        )

                        file.quantType?.let { quant ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BrandSurfaceElev
                            ) {
                                Text(
                                    text = quant,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandEmeraldLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                when {
                    isActive -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusPass.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusPass,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    isDownloaded -> {
                        Button(
                            onClick = onActivate,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                        ) {
                            Text("Load", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    !isDownloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnEmerald)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", color = OnEmerald, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directDownloadUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Browser Download",
                                    tint = BrandOnBgMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isDownloading && progress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                DownloadProgressBar(progress = progress, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun DownloadedModelsTab(
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
) {
    if (uiState.downloadedModels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = BrandOnBgSubtle,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No models downloaded yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandOnBgMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Download a curated coding model from the Featured tab or search on Hugging Face.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgSubtle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.selectTab(0) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Explore Featured Models", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${uiState.downloadedModels.size} GGUF models saved in application internal storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBgMuted
                        )
                    }
                }
            }

            items(uiState.downloadedModels) { file ->
                val isActive = !uiState.activeModelPath.isNullOrBlank() && file.absolutePath == uiState.activeModelPath
                DownloadedModelItem(
                    file = file,
                    isActive = isActive,
                    isLoading = uiState.isLoadingModel && isActive,
                    onLoad = { viewModel.loadAndActivateModel(file) },
                    onDelete = { viewModel.deleteModel(file) }
                )
            }
        }
    }
}

@Composable
private fun DownloadedModelItem(
    file: File,
    isActive: Boolean,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Model?", color = BrandOnBg) },
            text = { Text("Are you sure you want to delete ${file.name}? This will free up storage.", color = BrandOnBgMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusFail)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = BrandOnBg)
                }
            },
            containerColor = BrandSurfaceHigh
        )
    }

    val mb = file.length() / (1024 * 1024)
    val formattedSize = if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else "$mb MB"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) BrandSurfaceElev else BrandSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) BrandEmerald.copy(alpha = 0.5f) else BrandBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandOnBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusPass.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusPass,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onLoad,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnEmerald, strokeWidth = 2.dp)
                            } else {
                                Text("Load", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusFail, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatNumber(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
