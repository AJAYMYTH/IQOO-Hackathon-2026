package com.apexos.repoguardian.ui.modelbrowser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.huggingface.DownloadProgress
import com.apexos.repoguardian.data.huggingface.FeaturedModel
import com.apexos.repoguardian.data.huggingface.HfModelFile
import com.apexos.repoguardian.data.huggingface.HfModelSearchResult
import com.apexos.repoguardian.ui.theme.SeverityWarning
import com.apexos.repoguardian.ui.theme.StatusInfo
import com.apexos.repoguardian.ui.theme.StatusPass
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowserScreen(
    navController: NavController,
    viewModel: ModelBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Request Notification Permission on Android 13+
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

    // Handle Back Navigation when downloading
    BackHandler(enabled = uiState.downloadingFilename != null) {
        viewModel.setShowExitDialog(true)
    }

    // Exit & Background Download Permission Dialog
    if (uiState.showExitDialog) {
        val progressPercent = ((uiState.downloadProgress?.progressPercent ?: 0f) * 100).toInt()
        AlertDialog(
            onDismissRequest = { viewModel.setShowExitDialog(false) },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Download in Progress") },
            text = {
                Text("Downloading ${uiState.downloadingFilename ?: "model"} (${progressPercent}%).\n\nWould you like the download to continue safely in the background? You will receive a progress notification and the model will auto-load when done.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setShowExitDialog(false)
                        navController.popBackStack()
                    }
                ) {
                    Text("Keep in Background")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelDownload()
                        navController.popBackStack()
                    }
                ) {
                    Text("Cancel Download")
                }
            }
        )
    }

    var showGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.selectedModel != null) "Select Quantization"
                        else "AI Models & GGUF Manager"
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
                        Icon(Icons.Default.HelpOutline, contentDescription = "Quick Guide")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Success Notification
            uiState.successMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusPass.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPass, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = msg, style = MaterialTheme.typography.bodySmall, color = StatusPass, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = StatusPass, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Error Notification
            uiState.error?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (uiState.selectedModel == null) {
                // Tab Selection Row
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text("Featured") }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text("Search HF") }
                    )
                    Tab(
                        selected = uiState.selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        text = { Text("Saved (${uiState.downloadedModels.size})") }
                    )
                }

                when (uiState.selectedTab) {
                    0 -> FeaturedModelsTab(uiState = uiState, viewModel = viewModel)
                    1 -> SearchModelsTab(uiState = uiState, viewModel = viewModel)
                    2 -> DownloadedModelsTab(uiState = uiState, viewModel = viewModel)
                }
            } else {
                // File Explorer View for selected HuggingFace repository
                val selected = uiState.selectedModel!!
                ModelFilesView(selected = selected, uiState = uiState, viewModel = viewModel)
            }
        }
    }

    if (showGuide) {
        com.apexos.repoguardian.ui.components.NonTechGuideDialog(
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
                color = StatusInfo.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = StatusInfo, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Curated on-device models with background download support and automatic activation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusInfo
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = model.quant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = model.categoryBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = model.sizeFormatted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = model.recommendedFor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Download Progress Section
            if (isDownloading && progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                DownloadProgressBar(progress = progress, onCancel = onCancel)
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    when {
                        isActive -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusPass.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPass, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Active & Loaded", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusPass)
                                }
                            }
                        }
                        isDownloaded -> {
                            FilledTonalButton(
                                onClick = onActivate,
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isLoadingModel
                            ) {
                                if (isLoadingModel) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Load & Set Active")
                            }
                        }
                        else -> {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download (${model.sizeFormatted})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressBar(progress: DownloadProgress, onCancel: () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading in Background...",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${(progress.progressPercent * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${progress.downloadedFormatted} • ${progress.speedFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = progress.etaFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = SeverityWarning
                    )
                }

                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SearchModelsTab(
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
) {
    val quickTags = listOf("coder gguf", "qwen2.5-coder", "deepseek-coder", "starcoder", "codellama")

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search Hugging Face GGUF...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = { viewModel.search() }) {
                Text("Search")
            }
        }

        // Quick Tag Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickTags) { tag ->
                SuggestionChip(
                    onClick = {
                        viewModel.updateSearchQuery(tag)
                        viewModel.search()
                    },
                    label = { Text(tag) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No models found. Try a different search query.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatNumber(model.downloads),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatNumber(model.likes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = selected.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${selected.downloads} total downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        when {
            uiState.isLoadingFiles -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.modelFiles.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No compatible GGUF files found (<= 4GB)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.modelFiles) { file ->
                        val isDownloaded = uiState.downloadedModels.any { it.name == file.filename }
                        val downloadedFile = uiState.downloadedModels.firstOrNull { it.name == file.filename }
                        val isActive = isDownloaded && downloadedFile != null && !uiState.activeModelPath.isNullOrBlank() && downloadedFile.absolutePath == uiState.activeModelPath
                        val isCurrentlyDownloading = uiState.downloadingFilename == file.filename

                        ModelFileItem(
                            file = file,
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
}

@Composable
private fun ModelFileItem(
    file: HfModelFile,
    isDownloaded: Boolean,
    isActive: Boolean,
    isDownloading: Boolean,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onCancel: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (file.sizeInGb >= 1.0) String.format("%.2f GB", file.sizeInGb) else "${file.sizeInMb} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        file.quantType?.let { quant ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = StatusInfo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = quant,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusInfo,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                when {
                    isActive -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusPass.copy(alpha = 0.15f)
                        ) {
                            Text("Active", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusPass, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    isDownloaded -> {
                        FilledTonalButton(onClick = onActivate, shape = RoundedCornerShape(8.dp)) {
                            Text("Load")
                        }
                    }
                    !isDownloading -> {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
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
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Models Downloaded Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Browse the Featured or Search tabs to download a GGUF model for on-device AI code reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.downloadedModels) { file ->
                val isActive = !uiState.activeModelPath.isNullOrBlank() && file.absolutePath == uiState.activeModelPath
                val sizeMb = file.length().toDouble() / (1024.0 * 1024.0)
                val sizeFormatted = if (sizeMb >= 1024.0) String.format("%.2f GB", sizeMb / 1024.0) else String.format("%.1f MB", sizeMb)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sizeFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = StatusPass.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Active Model",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusPass,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!isActive) {
                            FilledTonalButton(
                                onClick = { viewModel.loadAndActivateModel(file) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Set Active")
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(onClick = { viewModel.deleteModel(file) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}
