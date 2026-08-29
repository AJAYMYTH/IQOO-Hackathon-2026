package com.apexos.repoguardian.ui.prstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.github.models.CheckRun
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrStatusScreen(
    navController: NavController,
    viewModel: PrStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        title = { Text("PR #${uiState.prNumber} Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    HorizontalDivider(color = BrandBorder, thickness = 1.dp)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BrandBackground),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PR info header
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${uiState.owner}/${uiState.repo}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandOnBg
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Pull Request #${uiState.prNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandEmeraldLight
                        )
                    }
                }
            }

            // Polling indicator
            if (uiState.isPolling) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = BrandEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-refreshing status...",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandOnBgMuted
                        )
                    }
                }
            }

            // Loading
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandEmerald)
                    }
                }
            }

            // No runner message
            uiState.noRunnerMessage?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusPending.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPending.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = StatusPending
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandOnBg
                            )
                        }
                    }
                }
            }

            // Check runs
            if (uiState.checkRuns.isNotEmpty()) {
                item {
                    Text(
                        text = "CI Check Runs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                }

                items(uiState.checkRuns) { checkRun ->
                    CheckRunItem(checkRun = checkRun)
                }
            }

            // Error
            uiState.error?.let { error ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusFail.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = StatusFail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CheckRunItem(checkRun: CheckRun) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                checkRun.status == "completed" && checkRun.conclusion == "success" -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Passed",
                        tint = StatusPass,
                        modifier = Modifier.size(22.dp)
                    )
                }
                checkRun.status == "completed" && checkRun.conclusion == "failure" -> {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Failed",
                        tint = StatusFail,
                        modifier = Modifier.size(22.dp)
                    )
                }
                checkRun.status == "in_progress" -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = StatusPending
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Queued",
                        tint = StatusPending,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = checkRun.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = BrandOnBg
                )
                Text(
                    text = when {
                        checkRun.status == "completed" -> checkRun.conclusion?.replaceFirstChar { it.uppercase() } ?: "Completed"
                        checkRun.status == "in_progress" -> "Running..."
                        else -> "Queued"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgMuted
                )
            }
        }
    }
}
