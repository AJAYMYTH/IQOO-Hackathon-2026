package com.apexos.repoguardian.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.llm.CodeIssue
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    navController: NavController,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code Review") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            // Open PR button
            val review = uiState.reviewResult
            if (review != null && review.hasIssue && uiState.createdPr == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { viewModel.openPullRequest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = !uiState.isCreatingPr
                    ) {
                        if (uiState.isCreatingPr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creating PR...")
                        } else {
                            Icon(Icons.Default.CallMerge, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Pull Request")
                        }
                    }
                }
            }

            // PR created banner
            if (uiState.createdPr != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusPass.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusPass
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PR #${uiState.createdPr?.number} Created!",
                                fontWeight = FontWeight.Bold,
                                color = StatusPass
                            )
                            Text(
                                text = uiState.createdPr?.title ?: "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            val pr = uiState.createdPr!!
                            navController.navigate(
                                Routes.prStatus(uiState.owner, uiState.repo, pr.number)
                            )
                        }) {
                            Text("View Status")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Commit info card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Commit ${uiState.sha.take(7)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        uiState.commitDiff?.commit?.message?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        uiState.commitDiff?.files?.let { files ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${files.size} file(s) changed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Loading states
            if (uiState.isLoadingDiff) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading diff...")
                        }
                    }
                }
            }

            if (uiState.isAnalyzing) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "AI Analysis in Progress",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Running on-device inference...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Diff viewer
            uiState.commitDiff?.files?.let { files ->
                items(files) { file ->
                    DiffCard(filename = file.filename, patch = file.patch, status = file.status)
                }
            }

            // Review results
            uiState.reviewResult?.let { review ->
                item {
                    Text(
                        text = "AI Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Summary
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (review.hasIssue) SeverityWarning.copy(alpha = 0.1f)
                                            else StatusPass.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (review.hasIssue) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (review.hasIssue) SeverityWarning else StatusPass
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = review.summary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Issues
                items(review.issues) { issue ->
                    IssueCard(issue = issue)
                }
            }

            // Error
            uiState.error?.let { error ->
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiffCard(filename: String, patch: String?, status: String) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column {
            // File header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (status) {
                            "added" -> Icons.Default.Add
                            "removed" -> Icons.Default.Remove
                            else -> Icons.Default.Edit
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (status) {
                            "added" -> StatusPass
                            "removed" -> StatusFail
                            else -> StatusInfo
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = filename,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Patch content
            if (patch != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeBackground)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Column {
                        patch.lines().forEach { line ->
                            val (bgColor, textColor) = when {
                                line.startsWith("+") -> CodeAddition.copy(alpha = 0.3f) to Color.Green.copy(alpha = 0.9f)
                                line.startsWith("-") -> CodeDeletion.copy(alpha = 0.3f) to Color.Red.copy(alpha = 0.9f)
                                line.startsWith("@@") -> Color.Transparent to Color.Cyan.copy(alpha = 0.6f)
                                else -> Color.Transparent to Color.White.copy(alpha = 0.8f)
                            }
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "(Binary file)",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun IssueCard(issue: CodeIssue) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Severity badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (issue.severity.lowercase()) {
                        "critical" -> SeverityCritical
                        "warning" -> SeverityWarning
                        else -> SeverityInfo
                    }.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = issue.severity.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (issue.severity.lowercase()) {
                            "critical" -> SeverityCritical
                            "warning" -> SeverityWarning
                            else -> SeverityInfo
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                issue.file?.let { file ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                issue.line?.let { line ->
                    Text(
                        text = ":$line",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodyMedium
            )

            issue.fix?.let { fix ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusPass.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = StatusPass
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fix,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
