package com.apexos.repoguardian.ui.review

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.llm.CodeIssue
import com.apexos.repoguardian.data.llm.IssueCategory
import com.apexos.repoguardian.data.llm.ReviewResult
import com.apexos.repoguardian.data.llm.Severity
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.components.AiThinkingIndicator
import com.apexos.repoguardian.ui.components.CodeSnippetView
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    navController: NavController,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Show toast on commit success
    LaunchedEffect(uiState.commitSuccessMessage) {
        uiState.commitSuccessMessage?.let { msg ->
            Toast.makeText(context, "✅ $msg", Toast.LENGTH_LONG).show()
            viewModel.clearSuccessMessage()
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
                            actionIconContentColor = BrandOnBg
                        ),
                        title = {
                            Text("Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            val review = uiState.reviewResult
                            if (review != null) {
                                val clipboard = LocalClipboardManager.current
                                IconButton(onClick = {
                                    val reportText = buildString {
                                        appendLine("## 🛡️ Repo Guardian Code Review Summary")
                                        appendLine("**Commit:** `${uiState.sha.take(7)}`")
                                        appendLine("**Repository:** ${uiState.owner}/${uiState.repo}")
                                        appendLine("**Summary:** ${review.summary}")
                                        if (review.issues.isNotEmpty()) {
                                            appendLine("\n### Issues Detected (${review.issues.size}):")
                                            review.issues.forEach { issue ->
                                                val loc = if (issue.file != null) "${issue.file}${if (issue.line != null) ":${issue.line}" else ""}" else "Global"
                                                appendLine("- **[${issue.severityEnum.uiLabel}] [${issue.categoryEnum.uiLabel}]** $loc — ${issue.displayTitle}")
                                                appendLine("  ${issue.description}")
                                                issue.displayFix?.let { appendLine("  *Fix:* $it") }
                                            }
                                        }
                                        if (!review.fixedCode.isNullOrBlank()) {
                                            appendLine("\n### Proposed Remediation Fix:")
                                            appendLine("```")
                                            appendLine(review.fixedCode)
                                            appendLine("```")
                                        }
                                    }
                                    clipboard.setText(AnnotatedString(reportText))
                                    Toast.makeText(context, "Review report copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Review Report", tint = BrandEmeraldLight)
                                }
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
                                text = "${files.size} file(s) changed in this commit",
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
                            Text("Loading commit diff from GitHub...", color = BrandOnBgMuted, style = MaterialTheme.typography.bodySmall)
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
                            "Scanning for bugs, logic errors & merge conflicts...",
                            "Auditing security vulnerabilities & credential leaks...",
                            "Classifying issue severities (Critical, Warning, Info)...",
                            "Synthesizing precision remediations & fixes..."
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

            // Review Summary & Severity Filters
            uiState.reviewResult?.let { review ->
                item {
                    ReviewSummaryCard(review = review)
                }

                item {
                    SeverityFilterRow(
                        selectedFilter = uiState.selectedSeverityFilter,
                        totalCount = review.totalCount,
                        criticalCount = review.criticalCount,
                        warningCount = review.warningCount,
                        infoCount = review.infoCount,
                        onSelectFilter = { viewModel.setSeverityFilter(it) }
                    )
                }

                // Filtered issues list or empty state
                val filtered = uiState.filteredIssues
                if (filtered.isEmpty()) {
                    item {
                        EmptyFilterState(
                            filter = uiState.selectedSeverityFilter,
                            totalIssues = review.totalCount
                        )
                    }
                } else {
                    items(filtered, key = { "${it.file}:${it.line}:${it.displayTitle}" }) { issue ->
                        val isAiSolving = uiState.solvingIssueKey == "${issue.file}:${issue.line}:${issue.displayTitle}"
                        val isGeneratingTest = uiState.testingIssueKey == "${issue.file}:${issue.line}:${issue.displayTitle}"
                        IssueCard(
                            issue = issue,
                            isAiSolving = isAiSolving,
                            isGeneratingTest = isGeneratingTest,
                            onEditClick = { viewModel.startManualEdit(issue) },
                            onAiSolveClick = { viewModel.solveIssueWithAi(issue) },
                            onGenerateTestClick = { viewModel.generateVerificationTest(issue) },
                            onTrustApplyClick = { viewModel.openTrustPreview(issue) },
                            onApplySolutionClick = {
                                viewModel.startManualEdit(issue)
                                viewModel.applyAiFixToManualEditor(issue)
                            }
                        )
                    }
                }

                if (!review.fixedCode.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BrandSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = BrandEmeraldLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Proposed Remediation Patch",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandOnBg
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                val clipboard = LocalClipboardManager.current
                                val ctx = LocalContext.current
                                CodeSnippetView(
                                    code = review.fixedCode,
                                    language = "kotlin",
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(it))
                                        Toast.makeText(ctx, "Patch copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Error state
            uiState.error?.let { error ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusFail.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFail.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = error,
                                color = StatusFail,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.retryAnalysis() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry Analysis", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Trust Before Apply Preview Dialog ────────────────────────────────────────
    if (uiState.previewingFixIssue != null) {
        TrustBeforeApplyDialog(
            issue = uiState.previewingFixIssue!!,
            isSubmitting = uiState.isSubmittingCommit,
            onDismiss = { viewModel.dismissTrustPreview() },
            onApply = { isPrMode ->
                viewModel.applyTrustPreview(uiState.previewingFixIssue!!, isPrMode)
            }
        )
    }

    // ─── Manual Code Editor Dialog ────────────────────────────────────────────────
    if (uiState.editingIssue != null) {
        ManualCodeEditorDialog(
            issue = uiState.editingIssue!!,
            filePath = uiState.editingFilePath,
            content = uiState.editingContent,
            commitMessage = uiState.editingCommitMessage,
            isPrMode = uiState.isEditingPrMode,
            isLoadingFile = uiState.isLoadingFileContent,
            isSubmitting = uiState.isSubmittingCommit,
            onContentChange = { viewModel.updateManualContent(it) },
            onCommitMessageChange = { viewModel.updateCommitMessage(it) },
            onPrModeChange = { viewModel.setEditingPrMode(it) },
            onUseAiFix = { viewModel.applyAiFixToManualEditor(uiState.editingIssue!!) },
            onDismiss = { viewModel.dismissManualEdit() },
            onCommit = { viewModel.commitManualEdit() }
        )
    }
}

// ─── Review Summary Card ───────────────────────────────────────────────────────
@Composable
fun ReviewSummaryCard(review: ReviewResult) {
    val risk = review.computedRiskScore
    val riskBadgeColor = Color(risk.riskLevel.badgeColorHex)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Privacy Mode Guarantee Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BrandEmeraldMuted.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmeraldLight.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(BrandEmeraldLight)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "0 BYTES CLOUD UPLOAD",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = BrandEmeraldLight
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• Private On-Device Neural Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandOnBgMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Header: Status Icon + Review Status + Severity Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (review.hasIssue) Icons.Default.Shield else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (review.criticalCount > 0) SeverityCritical else if (review.hasIssue) SeverityWarning else StatusPass,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (review.hasIssue) "Review Complete" else "No Issues Detected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandOnBg
                        )
                        Text(
                            text = "${review.totalCount} issues identified across diff",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandOnBgMuted
                        )
                    }
                }

                // Severity Counts Pill
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (review.criticalCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SeverityCritical.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SeverityCritical.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${review.criticalCount} Critical",
                                color = SeverityCritical,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (review.warningCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SeverityWarning.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SeverityWarning.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${review.warningCount} Warning",
                                color = SeverityWarning,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (review.infoCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SeverityInfo.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SeverityInfo.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${review.infoCount} Info",
                                color = BrandOnBgMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── Commit Health & Risk Score Gauge ──────────────────────────────
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BrandSurfaceHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "COMMIT HEALTH",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = BrandOnBgMuted
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = riskBadgeColor.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, riskBadgeColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${risk.overallScore}/100 • ${risk.riskLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = riskBadgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 4 Pillar Progress Bars (Security, Reliability, Performance, Maintainability)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScoreBar(label = "Security", score = risk.securityScore, color = if (risk.securityScore >= 80) StatusPass else SeverityCritical)
                        ScoreBar(label = "Reliability", score = risk.reliabilityScore, color = if (risk.reliabilityScore >= 80) StatusPass else SeverityWarning)
                        ScoreBar(label = "Performance", score = risk.performanceScore, color = BrandEmeraldLight)
                        ScoreBar(label = "Maintainability", score = risk.maintainabilityScore, color = BrandGreige)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = review.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandOnBg
            )

            review.metrics?.let { metrics ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BrandBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hardware: ${metrics.backend}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandOnBgMuted
                    )
                    Text(
                        text = "${metrics.formattedDuration} • ${metrics.formattedSpeed}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = BrandOnBgMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BrandOnBgMuted,
            modifier = Modifier.width(95.dp)
        )
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = BrandSurfaceElev
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$score",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = BrandOnBg,
            modifier = Modifier.width(26.dp)
        )
    }
}

// ─── Severity Filter Row ───────────────────────────────────────────────────────
@Composable
fun SeverityFilterRow(
    selectedFilter: SeverityFilter,
    totalCount: Int,
    criticalCount: Int,
    warningCount: Int,
    infoCount: Int,
    onSelectFilter: (SeverityFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == SeverityFilter.ALL,
            onClick = { onSelectFilter(SeverityFilter.ALL) },
            label = { Text("All ($totalCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BrandSurfaceHigh,
                selectedLabelColor = BrandOnBg,
                containerColor = BrandSurface,
                labelColor = BrandOnBgMuted
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (selectedFilter == SeverityFilter.ALL) BrandBorderHighlight else BrandBorder,
                enabled = true,
                selected = selectedFilter == SeverityFilter.ALL
            )
        )

        FilterChip(
            selected = selectedFilter == SeverityFilter.CRITICAL,
            onClick = { onSelectFilter(SeverityFilter.CRITICAL) },
            label = { Text("Critical ($criticalCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = SeverityCritical.copy(alpha = 0.15f),
                selectedLabelColor = SeverityCritical,
                containerColor = BrandSurface,
                labelColor = if (criticalCount > 0) SeverityCritical else BrandGreige
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (selectedFilter == SeverityFilter.CRITICAL) SeverityCritical.copy(alpha = 0.5f) else BrandBorder,
                enabled = true,
                selected = selectedFilter == SeverityFilter.CRITICAL
            )
        )

        FilterChip(
            selected = selectedFilter == SeverityFilter.WARNING,
            onClick = { onSelectFilter(SeverityFilter.WARNING) },
            label = { Text("Warning ($warningCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = SeverityWarning.copy(alpha = 0.15f),
                selectedLabelColor = SeverityWarning,
                containerColor = BrandSurface,
                labelColor = if (warningCount > 0) SeverityWarning else BrandGreige
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (selectedFilter == SeverityFilter.WARNING) SeverityWarning.copy(alpha = 0.5f) else BrandBorder,
                enabled = true,
                selected = selectedFilter == SeverityFilter.WARNING
            )
        )

        FilterChip(
            selected = selectedFilter == SeverityFilter.INFO,
            onClick = { onSelectFilter(SeverityFilter.INFO) },
            label = { Text("Info ($infoCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BrandSurfaceHigh,
                selectedLabelColor = BrandOnBg,
                containerColor = BrandSurface,
                labelColor = BrandOnBgMuted
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (selectedFilter == SeverityFilter.INFO) BrandBorderHighlight else BrandBorder,
                enabled = true,
                selected = selectedFilter == SeverityFilter.INFO
            )
        )
    }
}

// ─── Issue Card Component ──────────────────────────────────────────────────────
@Composable
fun IssueCard(
    issue: CodeIssue,
    isAiSolving: Boolean = false,
    isGeneratingTest: Boolean = false,
    onEditClick: () -> Unit = {},
    onAiSolveClick: () -> Unit = {},
    onGenerateTestClick: () -> Unit = {},
    onTrustApplyClick: () -> Unit = {},
    onApplySolutionClick: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val (badgeBg, badgeText, badgeIcon) = when (issue.severityEnum) {
        Severity.CRITICAL -> Triple(SeverityCritical.copy(alpha = 0.15f), SeverityCritical, Icons.Default.Error)
        Severity.WARNING -> Triple(SeverityWarning.copy(alpha = 0.15f), SeverityWarning, Icons.Default.Warning)
        Severity.INFO, Severity.UNKNOWN -> Triple(BrandSurfaceHigh, BrandOnBgMuted, Icons.Default.Info)
    }

    val (catBg, catText, catIcon) = when (issue.categoryEnum) {
        IssueCategory.SECURITY -> Triple(SeverityCritical.copy(alpha = 0.12f), SeverityCritical, Icons.Default.Security)
        IssueCategory.BUG -> Triple(SeverityCritical.copy(alpha = 0.12f), SeverityCritical, Icons.Default.BugReport)
        IssueCategory.CONFLICT -> Triple(SeverityWarning.copy(alpha = 0.15f), SeverityWarning, Icons.AutoMirrored.Filled.CallMerge)
        IssueCategory.SYNTAX_ERROR -> Triple(SeverityWarning.copy(alpha = 0.15f), SeverityWarning, Icons.Default.Code)
        IssueCategory.LOGIC_ERROR -> Triple(SeverityWarning.copy(alpha = 0.15f), SeverityWarning, Icons.Default.Psychology)
        IssueCategory.PERFORMANCE -> Triple(BrandEmeraldMuted, BrandEmeraldLight, Icons.Default.Speed)
        IssueCategory.MAINTAINABILITY -> Triple(BrandSurfaceHigh, BrandOnBgMuted, Icons.Default.Build)
        IssueCategory.STYLE -> Triple(BrandSurfaceHigh, BrandGreige, Icons.Default.Palette)
        IssueCategory.TESTING -> Triple(BrandSurfaceHigh, StatusPass, Icons.Default.CheckCircleOutline)
        IssueCategory.UNKNOWN -> Triple(BrandSurfaceHigh, BrandOnBgMuted, Icons.Default.Info)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (issue.isFixed) StatusPass.copy(alpha = 0.6f) else BrandBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Severity Badge + Category Badge + Confidence / Verification Alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Severity Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeText.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeText,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = issue.severityEnum.uiLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }
                    }

                    // Category Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = catBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, catText.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                tint = catText,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = issue.categoryEnum.uiLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = catText
                            )
                        }
                    }

                    if (issue.isFixed) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StatusPass.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "FIXED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusPass,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Confidence pill or manual verification alert
                if (issue.needsManualVerification) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SeverityWarning.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SeverityWarning.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Needs verification",
                            style = MaterialTheme.typography.labelSmall,
                            color = SeverityWarning,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Confidence: ${issue.confidenceLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandGreige
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = issue.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )

            // Location
            if (issue.file != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = BrandOnBgMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${issue.file}${if (issue.line != null) ":${issue.line}" else ""}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = BrandEmeraldLight
                    )
                }
            }

            // Description
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandOnBg,
                lineHeight = 20.sp
            )

            // Remediation Suggestion Box
            issue.displayFix?.let { fix ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorderHighlight)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = BrandEmeraldLight
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Remediation / Fix",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandEmeraldLight
                                )
                            }

                            if (issue.aiSolution != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandEmerald.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "AI Solved ✨",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandEmeraldLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (fix.contains("\n") || fix.contains("```") || fix.contains("(") || fix.contains("fun ") || fix.contains("val ") || fix.contains("export ") || fix.contains("const ")) {
                            val cleanCode = fix.removePrefix("```kotlin").removePrefix("```typescript").removePrefix("```").removeSuffix("```").trim()
                            val lang = issue.file?.substringAfterLast('.', "") ?: "kotlin"
                            CodeSnippetView(
                                code = cleanCode,
                                language = lang,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(it))
                                    Toast.makeText(context, "Fix code copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            Text(
                                text = fix,
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBg,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Automated Verification Test Section (if generated)
            issue.verificationTest?.let { testSnippet ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSurfaceElev,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPass, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "🧪 Automated Verification Test (Regression Prevention)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusPass
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val cleanTest = testSnippet.removePrefix("```kotlin").removePrefix("```typescript").removePrefix("```").removeSuffix("```").trim()
                        CodeSnippetView(
                            code = cleanTest,
                            language = issue.file?.substringAfterLast('.', "kt") ?: "kt",
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Test code copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Action Bar: Edit & Commit, Ask AI to Solve, Verify Test, Trust & Apply
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BrandBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Manually Edit and Commit
                    FilledTonalButton(
                        onClick = onEditClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandSurfaceHigh,
                            contentColor = BrandEmeraldLight
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit & Commit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }

                    // Option 2: Ask AI to Solve That
                    Button(
                        onClick = onAiSolveClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (issue.aiSolution != null) BrandSurfaceHigh else BrandEmeraldMuted,
                            contentColor = BrandEmeraldLight
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        enabled = !isAiSolving
                    ) {
                        if (isAiSolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = BrandEmeraldLight
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Solving...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(
                                if (issue.aiSolution != null) Icons.Default.AutoFixHigh else Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (issue.aiSolution != null) "Re-solve AI" else "Ask AI to Solve",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Row 2: Verify (Gen Test) & Trust & Apply
                if (issue.displayFix != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Generate Test button
                        OutlinedButton(
                            onClick = onGenerateTestClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp),
                            enabled = !isGeneratingTest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPass.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusPass)
                        ) {
                            if (isGeneratingTest) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = StatusPass)
                                Spacer(Modifier.width(4.dp))
                                Text("Testing...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (issue.verificationTest != null) "Re-gen Test" else "Verify (Gen Test)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Trust & Apply button
                        Button(
                            onClick = onTrustApplyClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald, contentColor = OnEmerald)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Trust & Apply", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─── Trust Before Apply Dialog ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustBeforeApplyDialog(
    issue: CodeIssue,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onApply: (isPrMode: Boolean) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BrandSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Trust Before Apply", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandOnBg)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BrandOnBgMuted)
                    }
                }

                Text(
                    text = "AI-assisted autonomy with human-in-the-loop validation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgMuted
                )

                Spacer(Modifier.height(14.dp))

                // Issue summary box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSurfaceHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = issue.displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandOnBg)
                        if (issue.file != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Target: ${issue.file}${if (issue.line != null) ":${issue.line}" else ""}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = BrandEmeraldLight)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Proposed Remediation Snippet
                Text("Proposed Fix:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BrandOnBg)
                Spacer(Modifier.height(6.dp))
                val solution = issue.aiSolution ?: issue.displayFix ?: ""
                val cleanCode = solution.removePrefix("```kotlin").removePrefix("```typescript").removePrefix("```").removeSuffix("```").trim()
                CodeSnippetView(
                    code = cleanCode,
                    language = issue.file?.substringAfterLast('.', "kt") ?: "kt",
                    onCopy = {}
                )

                // Automated Verification Test
                if (issue.verificationTest != null) {
                    Spacer(Modifier.height(14.dp))
                    Text("Automated Verification Test:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusPass)
                    Spacer(Modifier.height(6.dp))
                    val cleanTest = issue.verificationTest.removePrefix("```kotlin").removePrefix("```typescript").removePrefix("```").removeSuffix("```").trim()
                    CodeSnippetView(
                        code = cleanTest,
                        language = issue.file?.substringAfterLast('.', "kt") ?: "kt",
                        onCopy = {}
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Actions: Create PR or Direct Commit
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onApply(true) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text("Processing...", color = OnEmerald)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = OnEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Approve & Create Fix PR", color = OnEmerald, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { onApply(false) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSubmitting,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorderHighlight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandOnBg)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Approve & Direct Commit", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Manual Code Editor Dialog ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualCodeEditorDialog(
    issue: CodeIssue,
    filePath: String,
    content: String,
    commitMessage: String,
    isPrMode: Boolean,
    isLoadingFile: Boolean,
    isSubmitting: Boolean,
    onContentChange: (String) -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onPrModeChange: (Boolean) -> Unit,
    onUseAiFix: () -> Unit,
    onDismiss: () -> Unit,
    onCommit: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BrandSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manual Code Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandOnBg
                        )
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = BrandEmeraldLight
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BrandOnBgMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Issue banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorderHighlight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fixing: ${issue.displayTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandOnBg
                            )
                            if (issue.line != null) {
                                Text(
                                    text = "Line ${issue.line}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandOnBgMuted
                                )
                            }
                        }

                        if (issue.displayFix != null) {
                            TextButton(
                                onClick = onUseAiFix,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = BrandEmeraldLight, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Insert AI Fix", color = BrandEmeraldLight, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Code Editor text area
                if (isLoadingFile) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(CodeBackground, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandEmerald, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Loading file content from GitHub...", style = MaterialTheme.typography.bodySmall, color = BrandOnBgMuted)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = BrandOnBg
                        ),
                        placeholder = { Text("Enter or edit file code...", color = BrandGreige) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CodeBackground,
                            unfocusedContainerColor = CodeBackground,
                            focusedBorderColor = BrandBorderHighlight,
                            unfocusedBorderColor = BrandBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Commit message
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = onCommitMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Commit Message", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BrandSurfaceHigh,
                        unfocusedContainerColor = BrandSurfaceHigh,
                        focusedBorderColor = BrandBorderHighlight,
                        unfocusedBorderColor = BrandBorder
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // PR vs Direct Branch Commit Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isPrMode,
                            onCheckedChange = onPrModeChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandEmerald,
                                checkmarkColor = OnEmerald,
                                uncheckedColor = BrandBorderHighlight
                            )
                        )
                        Text(
                            text = "Create Fix Branch & Pull Request",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBg
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Cancel & Commit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = BrandOnBgMuted)
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onCommit,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSubmitting && content.isNotBlank()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OnEmerald
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPrMode) "Creating PR..." else "Committing...", color = OnEmerald)
                        } else {
                            Icon(
                                if (isPrMode) Icons.AutoMirrored.Filled.CallMerge else Icons.Default.Check,
                                contentDescription = null,
                                tint = OnEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isPrMode) "Create PR" else "Commit Changes",
                                color = OnEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Empty Filter State ────────────────────────────────────────────────────────
@Composable
fun EmptyFilterState(filter: SeverityFilter, totalIssues: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StatusPass,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (totalIssues == 0) {
                    "No issues detected"
                } else {
                    "No ${filter.label.lowercase()} issues found"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (totalIssues == 0) {
                    "Repo Guardian did not find any critical, warning, or informational issues in this diff."
                } else {
                    "There are no issues matching the ${filter.label} filter in this review."
                },
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBgMuted
            )
        }
    }
}

// ─── Diff Card Component ───────────────────────────────────────────────────────
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
