package com.apexos.repoguardian.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.AiThinkingIndicator
import com.apexos.repoguardian.ui.components.AppBottomBar
import com.apexos.repoguardian.ui.components.MarkdownContent
import com.apexos.repoguardian.ui.components.NonTechGuideDialog
import com.apexos.repoguardian.ui.theme.*

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

    // Auto-scroll to newest message
    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
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
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Repo context dropdown
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
                                                    text = uiState.repoName.ifBlank { "Repository Chat" },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandOnBg,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = "Switch Repo Context",
                                                    tint = BrandEmeraldLight,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = uiState.repoOwner.ifBlank { "Select Context" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BrandOnBgMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = uiState.isRepoDropdownOpen,
                                        onDismissRequest = { viewModel.setRepoDropdownOpen(false) },
                                        modifier = Modifier.background(BrandSurfaceHigh)
                                    ) {
                                        Text(
                                            text = "Repository Context",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandEmeraldLight,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        HorizontalDivider(color = BrandBorder)
                                        uiState.availableRepos.forEach { repo ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = repo.fullName,
                                                            color = if (repo.name == uiState.repoName) BrandEmeraldLight else BrandOnBg,
                                                            fontWeight = if (repo.name == uiState.repoName) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (repo.name == uiState.repoName) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Active",
                                                                tint = BrandEmerald,
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

                                // Active model indicator badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandSurfaceElev,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setModelDropdownOpen(true) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Memory,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = BrandEmeraldLight
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = uiState.activeModelName.take(12),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = BrandOnBg
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = BrandOnBgMuted
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = uiState.isModelDropdownOpen,
                                    onDismissRequest = { viewModel.setModelDropdownOpen(false) },
                                    modifier = Modifier.background(BrandSurfaceHigh)
                                ) {
                                    Text(
                                        text = "Active AI Model",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandEmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    HorizontalDivider(color = BrandBorder)

                                    if (uiState.downloadedModels.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No Models Downloaded", color = BrandOnBgMuted) },
                                            onClick = { }
                                        )
                                    } else {
                                        uiState.downloadedModels.forEach { modelFile ->
                                            val isActive = modelFile.nameWithoutExtension == uiState.activeModelName
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = modelFile.nameWithoutExtension,
                                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isActive) BrandEmeraldLight else BrandOnBg
                                                        )
                                                        if (isActive) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Active",
                                                                tint = BrandEmerald,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = { viewModel.switchModel(modelFile) }
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = BrandBorder)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Download Models", color = BrandOnBg)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setModelDropdownOpen(false)
                                            navController.navigate(Routes.MODEL_BROWSER)
                                        }
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { showGuide = true }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help Guide")
                            }
                            IconButton(onClick = { viewModel.clearChat() }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Conversation")
                            }
                        }
                    )
                    HorizontalDivider(color = BrandBorder, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandBackground)
                    .imePadding()
            ) {
                Surface(
                    color = GlassBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        // Think Mode Toggle & Quick Prompt Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.isThinkModeEnabled) BrandEmeraldMuted else BrandSurfaceElev,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (uiState.isThinkModeEnabled) BrandEmeraldLight.copy(alpha = 0.5f) else BrandBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.toggleThinkMode() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (uiState.isThinkModeEnabled) BrandEmeraldLight else BrandOnBgMuted
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (uiState.isThinkModeEnabled) "Think ON" else "Think OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.isThinkModeEnabled) BrandEmeraldLight else BrandOnBgMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(viewModel.quickPrompts) { quickPrompt ->
                                    val icon = getCategoryIcon(quickPrompt.category)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BrandSurfaceHigh,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.sendMessage(quickPrompt.prompt) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = BrandEmeraldLight
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = quickPrompt.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BrandOnBg
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Input Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text("Ask about architecture, diffs, security, CI/CD...", color = BrandOnBgSubtle, fontSize = 13.sp)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = BrandSurface,
                                    unfocusedContainerColor = BrandSurface,
                                    focusedBorderColor = BrandEmerald,
                                    unfocusedBorderColor = BrandBorder,
                                    focusedTextColor = BrandOnBg,
                                    unfocusedTextColor = BrandOnBg
                                ),
                                maxLines = 4,
                                shape = RoundedCornerShape(16.dp)
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
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (inputText.isNotBlank() && !uiState.isGenerating) BrandEmerald
                                        else BrandSurfaceElev
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank() && !uiState.isGenerating) OnEmerald else BrandOnBgSubtle,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                AppBottomBar(
                    currentRoute = Routes.CHAT,
                    navController = navController
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(uiState.messages) { message ->
                MessageBubble(
                    message = message,
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Code", code)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (uiState.isGenerating) {
                item {
                    AiThinkingIndicator()
                }
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
private fun MessageBubble(
    message: ChatMessage,
    onCopyCode: (String) -> Unit
) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(BrandEmeraldMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = "AI",
                    modifier = Modifier.size(16.dp),
                    tint = BrandEmeraldLight
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
            color = if (isUser) BrandSurfaceElev else BrandSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandOnBg
                    )
                } else {
                    MarkdownContent(
                        content = message.content,
                        textColor = BrandOnBg,
                        onCopyCode = onCopyCode
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(BrandSurfaceElev),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "User",
                    modifier = Modifier.size(16.dp),
                    tint = BrandOnBgMuted
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
