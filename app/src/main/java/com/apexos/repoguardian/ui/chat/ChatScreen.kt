package com.apexos.repoguardian.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.apexos.repoguardian.data.voice.VoiceState
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.AiThinkingIndicator
import com.apexos.repoguardian.ui.components.AppBottomBar
import com.apexos.repoguardian.ui.components.MarkdownContent
import com.apexos.repoguardian.ui.components.NonTechGuideDialog
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isImeVisible = WindowInsets.isImeVisible

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceDictation()
        else Toast.makeText(context, "Microphone permission required for voice dictation", Toast.LENGTH_SHORT).show()
    }

    // Handle voice speech-to-text dictation result
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.Result -> {
                inputText = if (inputText.isBlank()) state.text else "$inputText ${state.text}"
                viewModel.resetVoiceState()
                Toast.makeText(context, "🎙️ \"${state.text}\"", Toast.LENGTH_SHORT).show()
            }
            is VoiceState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetVoiceState()
            }
            else -> {}
        }
    }

    // Analyze current generation & thinking state cleanly
    val lastMessage = uiState.messages.lastOrNull()
    val isAiCurrentlyThinking = uiState.isGenerating && lastMessage != null && !lastMessage.isUser && isMessageInThinkingPhase(lastMessage.content)

    val renderableMessages = remember(uiState.messages) {
        uiState.messages.filter { message ->
            if (message.isUser) {
                true
            } else {
                val cleanAnswer = extractCleanAnswer(message.content)
                cleanAnswer.isNotBlank()
            }
        }
    }

    // Auto-scroll continuously as new tokens stream in throughout the entire AI generation
    val lastRawContent = lastMessage?.content ?: ""
    val lastContentLength = lastRawContent.length

    LaunchedEffect(lastContentLength, uiState.messages.size, uiState.isGenerating) {
        val totalItems = renderableMessages.size + (if (isAiCurrentlyThinking) 1 else 0)
        if (totalItems > 0 && uiState.isGenerating) {
            // Scroll to the bottom of the latest streaming content
            listState.scrollToItem(totalItems - 1, 100000)
        }
    }

    // Auto-scroll smoothly when new messages are sent or added
    LaunchedEffect(uiState.messages.size) {
        val totalItems = renderableMessages.size + (if (isAiCurrentlyThinking) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1, 100000)
        }
    }

    // Auto-scroll when IME keyboard opens
    LaunchedEffect(isImeVisible) {
        val totalItems = renderableMessages.size + (if (isAiCurrentlyThinking) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1, 100000)
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
                    .background(Color.Transparent)
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
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
                                    .clickable {
                                        viewModel.toggleThinkMode()
                                        val msg = if (uiState.isThinkModeEnabled) "Deep Think OFF" else "Deep Think ON — model will reason before answering"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
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
                                        text = "Think",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.isThinkModeEnabled) BrandEmeraldLight else BrandOnBgMuted
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // ON/OFF dot indicator
                                    Box(
                                        modifier = androidx.compose.ui.Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isThinkModeEnabled) BrandEmeraldLight else BrandOnBgSubtle)
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

                            Spacer(modifier = Modifier.width(6.dp))

                            // Mic Voice Dictation Button
                            IconButton(
                                onClick = {
                                    if (voiceState is VoiceState.Listening) {
                                        viewModel.stopVoiceDictation()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (voiceState is VoiceState.Listening) StatusFail.copy(alpha = 0.2f)
                                        else BrandSurfaceElev
                                    )
                                    .border(
                                        1.dp,
                                        if (voiceState is VoiceState.Listening) StatusFail else BrandBorder,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (voiceState is VoiceState.Listening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice Dictation",
                                    tint = if (voiceState is VoiceState.Listening) StatusFail else BrandEmeraldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (uiState.isGenerating) {
                                        viewModel.stopGeneration()
                                    } else if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                enabled = inputText.isNotBlank() || uiState.isGenerating,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isGenerating) StatusFail
                                        else if (inputText.isNotBlank()) BrandEmerald
                                        else BrandSurfaceElev
                                    )
                            ) {
                                if (uiState.isGenerating) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "Stop Generation",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (inputText.isNotBlank()) OnEmerald else BrandOnBgSubtle,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isImeVisible) {
                    AppBottomBar(
                        currentRoute = Routes.CHAT,
                        navController = navController
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items = renderableMessages, key = { it.id }) { message ->
                val isStreaming = uiState.isGenerating &&
                        !message.isUser &&
                        message.id == uiState.messages.lastOrNull()?.id &&
                        !isAiCurrentlyThinking

                val displayContent = if (message.isUser) message.content else extractCleanAnswer(message.content)

                MessageBubble(
                    message = message,
                    displayContent = displayContent,
                    isStreaming = isStreaming,
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Code", code)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onCopyResponse = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("AI Response", text)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Response copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Show clean minimal thinking indicator ONLY while AI is actively thinking/processing
            if (isAiCurrentlyThinking) {
                item(key = "active_ai_thinking") {
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

/**
 * Checks whether an in-progress AI message is still in the internal thinking/reasoning phase.
 */
private fun isMessageInThinkingPhase(rawContent: String): Boolean {
    if (rawContent.isBlank()) return true
    val hasOpenThink = rawContent.contains("<think>")
    val hasCloseThink = rawContent.contains("</think>")

    if (hasOpenThink && !hasCloseThink) {
        // Still generating internal reasoning chain
        return true
    }
    if (hasOpenThink && hasCloseThink) {
        // Closed think, but answer text after </think> hasn't started streaming yet
        val answer = rawContent.substringAfter("</think>").trimStart('\n', ' ')
        return answer.isBlank()
    }
    return false
}

/**
 * Extracts the clean answer content for user display, removing internal thought tags.
 */
private fun extractCleanAnswer(rawContent: String): String {
    val hasOpenThink = rawContent.contains("<think>")
    val hasCloseThink = rawContent.contains("</think>")

    return when {
        hasOpenThink && hasCloseThink -> rawContent.substringAfter("</think>").trimStart('\n', ' ')
        hasOpenThink && !hasCloseThink -> "" // Still thinking -> no answer content yet
        else -> rawContent
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    displayContent: String,
    isStreaming: Boolean = false,
    onCopyCode: (String) -> Unit,
    onCopyResponse: (String) -> Unit
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
                    Icons.Outlined.AutoAwesome,
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
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = displayContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandOnBg
                    )
                } else {
                    MarkdownContent(
                        content = displayContent,
                        textColor = BrandOnBg,
                        onCopyCode = onCopyCode
                    )

                    if (isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "cursorPulse")
                            val cursorAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(450, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "cursor"
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 6.dp, height = 12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(BrandEmeraldLight.copy(alpha = cursorAlpha))
                            )
                        }
                    }

                    if (displayContent.isNotBlank() && !isStreaming) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.metrics != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandSurfaceHigh,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = BrandEmeraldLight
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${message.metrics.formattedDuration} • ${message.metrics.tokenCount} tok • ${message.metrics.formattedSpeed}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = BrandOnBgMuted,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandSurfaceElev,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCopyResponse(displayContent) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy Response",
                                        modifier = Modifier.size(12.dp),
                                        tint = BrandEmeraldLight
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = BrandEmeraldLight,
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
