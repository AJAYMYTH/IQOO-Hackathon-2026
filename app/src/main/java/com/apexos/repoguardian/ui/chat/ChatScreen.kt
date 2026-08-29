package com.apexos.repoguardian.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.MarkdownContent
import com.apexos.repoguardian.ui.theme.StatusPass
import com.apexos.repoguardian.ui.theme.StatusPending

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Auto-scroll on new message
    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Repo Switcher Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setRepoDropdownOpen(true) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = uiState.repoName.ifBlank { "AI Assistant" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Switch Repo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = uiState.repoOwner.ifBlank { "AJAYMYTH" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = uiState.isRepoDropdownOpen,
                                onDismissRequest = { viewModel.setRepoDropdownOpen(false) }
                            ) {
                                Text(
                                    text = "Switch Repository Context",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                HorizontalDivider()
                                uiState.availableRepos.forEach { repo ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = repo.fullName,
                                                    fontWeight = if (repo.name == uiState.repoName) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (repo.name == uiState.repoName) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Active",
                                                        tint = StatusPass,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { viewModel.switchRepo(repo) }
                                    )
                                }
                            }
                        }

                        // Model Selector Button
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.setModelDropdownOpen(true) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = "Model",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (uiState.activeModelName != "No Model Loaded") StatusPass else StatusPending
                                    )
                                },
                                label = {
                                    Text(
                                        text = uiState.activeModelName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )

                            DropdownMenu(
                                expanded = uiState.isModelDropdownOpen,
                                onDismissRequest = { viewModel.setModelDropdownOpen(false) }
                            ) {
                                Text(
                                    text = "Select On-Device Model",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                HorizontalDivider()

                                if (uiState.downloadedModels.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No Models Downloaded") },
                                        onClick = { }
                                    )
                                } else {
                                    uiState.downloadedModels.forEach { modelFile ->
                                        val isActive = modelFile.nameWithoutExtension == uiState.activeModelName
                                        val sizeMb = modelFile.length().toDouble() / (1024.0 * 1024.0)
                                        val sizeText = if (sizeMb >= 1024.0) String.format("%.2f GB", sizeMb / 1024.0) else String.format("%.0f MB", sizeMb)

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = modelFile.nameWithoutExtension,
                                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        Text(
                                                            text = sizeText,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                    if (isActive) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "Active",
                                                            tint = StatusPass,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = { viewModel.switchModel(modelFile) }
                                        )
                                    }
                                }

                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Manage & Download Models")
                                        }
                                    },
                                    onClick = {
                                        viewModel.setModelDropdownOpen(false)
                                        navController.navigate(Routes.MODEL_BROWSER)
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showGuide = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Quick Guide")
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
                HorizontalDivider()

                // Think Mode Toggle (Enabled for Reasoning Models like DeepSeek-R1) & Quick Prompts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isThinkingModel) {
                        FilterChip(
                            selected = uiState.isThinkModeEnabled,
                            onClick = { viewModel.toggleThinkMode() },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (uiState.isThinkModeEnabled) "Deep Think ON" else "Deep Think OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.quickPrompts) { quickPrompt ->
                            val icon = getCategoryIcon(quickPrompt.category)
                            SuggestionChip(
                                onClick = { viewModel.sendMessage(quickPrompt.prompt) },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                label = { Text(quickPrompt.title, fontWeight = FontWeight.Medium) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about architecture, diffs, tests, CI/CD...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !uiState.isGenerating,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !uiState.isGenerating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (uiState.showDownloadBanner) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No Model Downloaded",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Download a GGUF model to start chatting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { navController.navigate(Routes.MODEL_BROWSER) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Download")
                            }
                        }
                    }
                }
            }

            items(uiState.messages) { message ->
                MessageBubble(
                    message = message,
                    isGenerating = uiState.isGenerating,
                    isThinkingModel = uiState.isThinkingModel,
                    onNavigateToModels = { navController.navigate(Routes.MODEL_BROWSER) },
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Code", code)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
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
private fun MessageBubble(
    message: ChatMessage,
    isGenerating: Boolean,
    isThinkingModel: Boolean,
    onNavigateToModels: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    if (message.isSystem) {
        // System Notice Card (Distinct from AI / User responses)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "System Notice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Notice",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.content.contains("AI Models") || message.content.contains("Download")) {
                    OutlinedButton(
                        onClick = onNavigateToModels,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open AI Models Manager")
                    }
                }
            }
        }
        return
    }

    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = "AI",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    if (message.content.isEmpty() && isGenerating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val statusText = if (isThinkingModel) "Thinking..." else "Working..."
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Column {
                            MarkdownContent(
                                content = message.content,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onCopyCode = onCopyCode
                            )
                            message.metrics?.let { metrics ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${metrics.formattedDuration} • ${metrics.formattedSpeed} • ${metrics.backend}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "User",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun getCategoryIcon(category: PromptCategory): ImageVector = when (category) {
    PromptCategory.EXPLAIN -> Icons.AutoMirrored.Filled.MenuBook
    PromptCategory.REVIEW -> Icons.Default.BugReport
    PromptCategory.CICD -> Icons.Default.Terminal
    PromptCategory.TESTS -> Icons.AutoMirrored.Filled.FactCheck
    PromptCategory.RELEASE -> Icons.Default.RocketLaunch
    PromptCategory.SECURITY -> Icons.Default.Security
    PromptCategory.PERFORMANCE -> Icons.Default.Speed
}
