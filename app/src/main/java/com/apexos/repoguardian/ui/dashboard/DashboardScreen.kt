package com.apexos.repoguardian.ui.dashboard

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.github.models.Commit
import com.apexos.repoguardian.data.voice.VoiceState
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val context = LocalContext.current

    // Handle voice trigger result
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.Result -> {
                if (state.isTrigger && uiState.commits.isNotEmpty()) {
                    val latestCommit = uiState.commits.first()
                    viewModel.resetVoiceState()
                    navController.navigate(
                        Routes.review(uiState.repoOwner, uiState.repoName, latestCommit.sha)
                    )
                } else if (!state.isTrigger) {
                    Toast.makeText(context, "Say 'review' or 'check' to trigger analysis", Toast.LENGTH_SHORT).show()
                    viewModel.resetVoiceState()
                }
            }
            is VoiceState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetVoiceState()
            }
            else -> {}
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceTrigger()
        else Toast.makeText(context, "Microphone permission required for voice trigger", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { viewModel.setDropdownOpen(true) }
                                .padding(vertical = 4.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = uiState.repoName.ifBlank { "Dashboard" },
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch Repository",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (uiState.repoOwner.isNotBlank()) {
                                    Text(
                                        text = uiState.repoOwner,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // Repository switcher dropdown
                        DropdownMenu(
                            expanded = uiState.isDropdownOpen,
                            onDismissRequest = { viewModel.setDropdownOpen(false) }
                        ) {
                            Text(
                                text = "Switch Repository",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider()

                            if (uiState.availableRepos.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No repositories loaded") },
                                    onClick = { viewModel.loadAvailableRepos() }
                                )
                            } else {
                                uiState.availableRepos.take(8).forEach { repo ->
                                    val isSelected = repo.name == uiState.repoName && repo.owner.login == uiState.repoOwner
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                                    Text(
                                                        text = repo.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = repo.owner.login,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.switchRepo(repo)
                                        }
                                    )
                                }
                            }

                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Browse All Repositories...")
                                    }
                                },
                                onClick = {
                                    viewModel.setDropdownOpen(false)
                                    navController.navigate(Routes.REPO_PICKER)
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.CHAT) }) {
                        Icon(Icons.Default.Chat, contentDescription = "AI Chat Assistant", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        navController.navigate(
                            Routes.cicdGenerator(uiState.repoOwner, uiState.repoName)
                        )
                    }) {
                        Icon(Icons.Default.Build, contentDescription = "CI/CD Generator")
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                shape = CircleShape,
                containerColor = when (voiceState) {
                    is VoiceState.Listening -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Icon(
                    imageVector = when (voiceState) {
                        is VoiceState.Listening -> Icons.Default.MicOff
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Voice Trigger"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Voice listening indicator
            if (voiceState is VoiceState.Listening) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Listening... Say 'review latest commit'",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error ?: "Error",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadDashboard() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {
                    // Section header
                    Text(
                        text = "Recent Commits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.commits) { commit ->
                            CommitItem(
                                commit = commit,
                                onClick = {
                                    navController.navigate(
                                        Routes.review(uiState.repoOwner, uiState.repoName, commit.sha)
                                    )
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommitItem(commit: Commit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Commit message
            Text(
                text = commit.commit.message.lines().first(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author
                Text(
                    text = commit.commit.author?.name ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // SHA
                Text(
                    text = commit.sha.take(7),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Date
            commit.commit.author?.date?.let { date ->
                Text(
                    text = date.take(10), // Just the date part
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
