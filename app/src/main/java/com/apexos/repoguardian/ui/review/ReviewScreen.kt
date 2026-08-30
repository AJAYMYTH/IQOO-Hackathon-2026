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
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import com.apexos.repoguardian.ui.components.AiThinkingIndicator
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    navController: NavController,
    viewModel: ReviewViewModel = hiltViewModel()
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
                        title = {
                            Text("Code Review & Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    HorizontalDivider(color = BrandBorder, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            val review = uiState.reviewResult
            if (review != null && review.hasIssue && uiState.createdPr == null) {
                Surface(
                    color = GlassBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Button(
                        onClick = { viewModel.openPullRequest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        enabled = !uiState.isCreatingPr
                    ) {
                        if (uiState.isCreatingPr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = OnEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creating Pull Request...", color = OnEmerald)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = OnEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Pull Request from Fixes", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // PR created banner
            if (uiState.createdPr != null) {
                Surface(
                    color = BrandSurfaceElev,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusPass,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PR #${uiState.createdPr?.number} Created",
                                fontWeight = FontWeight.Bold,
                                color = StatusPass
                            )
                            Text(
                                text = uiState.createdPr?.title ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )
                        }
                        TextButton(onClick = {
                            val pr = uiState.createdPr!!
                            navController.navigate(
                                Routes.prStatus(uiState.owner, uiState.repo, pr.number)
                            )
                        }) {
                            Text("View Status", color = BrandEmeraldLight, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Commit info card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Commit",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandOnBgSubtle
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BrandSurfaceHigh
                            ) {
                                Text(
                                    text = uiState.sha.take(7),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = BrandOnBg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        uiState.commitDiff?.commit?.message?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandOnBg
                            )
                        }
                        uiState.commitDiff?.files?.let { files ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${files.size} file(s) changed",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )
                        }
                    }
                }
            }

            // Loading states
            if (uiState.isLoadingDiff) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandEmerald)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading commit diff...", color = BrandOnBgMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (uiState.isAnalyzing) {
                item {
                    AiThinkingIndicator(
                        modifier = Modifier.padding(bottom = 8.dp),
                        thinkingPhases = listOf(
                            "Analyzing diff hunks and syntax trees...",
                            "Scanning for CVEs, logic flaws & memory leaks...",
                            "Synthesizing production review & remediation steps...",
                            "Finalizing AI code audit..."
                        )
                    )
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
                        text = "AI Review Findings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                }

                // Summary
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (review.hasIssue) SeverityWarning.copy(alpha = 0.08f)
                                else StatusPass.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (review.hasIssue) SeverityWarning.copy(alpha = 0.3f)
                            else StatusPass.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (review.hasIssue) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (review.hasIssue) SeverityWarning else StatusPass,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = review.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandOnBg
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusFail.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = StatusFail,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiffCard(filename: String, patch: String?, status: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column {
            Surface(
                color = BrandSurfaceHigh
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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = BrandOnBg
                    )
                }
            }

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
                                line.startsWith("+") -> CodeAddition to BrandEmeraldLight
                                line.startsWith("-") -> CodeDeletion to StatusFail
                                line.startsWith("@@") -> Color.Transparent to StatusInfo
                                else -> Color.Transparent to BrandOnBgMuted
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
                    text = "(Binary file content)",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgSubtle
                )
            }
        }
    }
}

@Composable
fun IssueCard(issue: CodeIssue) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        color = BrandOnBgMuted
                    )
                }

                issue.line?.let { line ->
                    Text(
                        text = ":$line",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = BrandOnBgMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandOnBg
            )

            issue.fix?.let { fix ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandEmeraldMuted.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = BrandEmeraldLight
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fix,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBg
                        )
                    }
                }
            }
        }
    }
}
