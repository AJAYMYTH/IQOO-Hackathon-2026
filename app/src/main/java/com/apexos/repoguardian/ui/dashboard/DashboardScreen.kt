package com.apexos.repoguardian.ui.dashboard

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.github.models.Commit
import com.apexos.repoguardian.data.voice.VoiceState
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.AppBottomBar
import com.apexos.repoguardian.ui.components.NonTechGuideDialog
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceTrigger()
        else Toast.makeText(context, "Microphone permission required for voice trigger", Toast.LENGTH_SHORT).show()
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
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setDropdownOpen(true) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = uiState.repoName.ifBlank { "Select Repository" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandOnBg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Switch Repository",
                                                tint = BrandEmeraldLight,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (uiState.repoOwner.isNotBlank()) {
                                            Text(
                                                text = uiState.repoOwner,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BrandOnBgMuted
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = uiState.isDropdownOpen,
                                    onDismissRequest = { viewModel.setDropdownOpen(false) },
                                    modifier = Modifier.background(BrandSurfaceHigh)
                                ) {
                                    Text(
                                        text = "Repositories",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandEmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    HorizontalDivider(color = BrandBorder)

                                    if (uiState.availableRepos.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No repositories loaded", color = BrandOnBgMuted) },
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
                                                                color = if (isSelected) BrandEmeraldLight else BrandOnBg,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = repo.owner.login,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = BrandOnBgMuted
                                                            )
                                                        }
                                                        if (isSelected) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = BrandEmerald,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = { viewModel.switchRepo(repo) }
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = BrandBorder)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Search, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Browse All Repositories", color = BrandOnBg)
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
                            IconButton(onClick = {
                                if (uiState.repoOwner.isNotBlank() && uiState.repoName.isNotBlank()) {
                                    navController.navigate(
                                        Routes.cicdGenerator(uiState.repoOwner, uiState.repoName)
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Terminal, contentDescription = "CI/CD Pipeline Generator")
                            }
                            IconButton(onClick = { viewModel.openGuide() }) {
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
                currentRoute = Routes.DASHBOARD,
                navController = navController
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                shape = CircleShape,
                containerColor = when (voiceState) {
                    is VoiceState.Listening -> StatusFail
                    else -> BrandEmerald
                },
                contentColor = OnEmerald,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
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
                .background(BrandBackground)
        ) {
            if (voiceState is VoiceState.Listening) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandSurfaceElev
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandEmerald
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Listening... Say 'review latest commit'",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBg
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandEmerald, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Loading repository data...", style = MaterialTheme.typography.bodySmall, color = BrandOnBgMuted)
                        }
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusFail, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.error ?: "Failed to load commits",
                                color = StatusFail,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadDashboard() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandSurfaceElev)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Retry", color = BrandOnBg)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Overview Card
                        item {
                            RepoSummaryCard(
                                owner = uiState.repoOwner,
                                name = uiState.repoName,
                                commitsCount = uiState.commits.size,
                                onBrowseClick = { navController.navigate(Routes.REPO_PICKER) }
                            )
                        }

                        // Section Title
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Commits",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandOnBg
                                )
                                Text(
                                    text = "${uiState.commits.size} commits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandOnBgMuted
                                )
                            }
                        }

                        // Commit list items
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
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }
                }
            }
        }
    }

    if (uiState.isGuideOpen) {
        NonTechGuideDialog(
            onDismiss = { dontShowAgain ->
                viewModel.dismissGuide(dontShowAgain)
            }
        )
    }
}

@Composable
private fun RepoSummaryCard(
    owner: String,
    name: String,
    commitsCount: Int,
    onBrowseClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandEmeraldMuted.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = BrandEmeraldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = name.ifBlank { "No Active Repo" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandOnBg
                        )
                        Text(
                            text = if (owner.isNotBlank()) "github.com/$owner" else "Select repository",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandOnBgMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = BrandSurfaceElev,
                    modifier = Modifier.clickable { onBrowseClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Switch", style = MaterialTheme.typography.labelSmall, color = BrandEmeraldLight, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = BrandBorder)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricPill(label = "Total Commits", value = "$commitsCount")
                MetricPill(label = "Analysis Engine", value = "llama.cpp (On-Device)")
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = BrandOnBgSubtle)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = BrandOnBg)
    }
}

@Composable
fun CommitItem(commit: Commit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Commit message
            Text(
                text = commit.commit.message.lines().firstOrNull() ?: "Commit",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = BrandOnBg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(BrandSurfaceElev),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = BrandOnBgMuted
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = commit.commit.author?.name ?: "Unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandOnBgMuted
                    )
                }

                // Monospace SHA badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text(
                        text = commit.sha.take(7),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = BrandEmeraldLight,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Date
            commit.commit.author?.date?.let { date ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandOnBgSubtle
                )
            }
        }
    }
}
