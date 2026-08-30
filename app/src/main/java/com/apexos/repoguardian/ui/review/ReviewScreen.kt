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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.llm.CodeIssue
import com.apexos.repoguardian.data.llm.ReviewResult
import com.apexos.repoguardian.data.llm.Severity
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
                                val context = LocalContext.current
                                IconButton(onClick = {
                                    val reportText = buildString {
                                        appendLine("## 🛡️ Repo Guardian Review Summary")
                                        appendLine("**Commit:** `${uiState.sha.take(7)}`")
                                        appendLine("**Summary:** ${review.summary}")
                                        if (review.issues.isNotEmpty()) {
                                            appendLine("\n### Issues Detected (${review.issues.size}):")
                                            review.issues.forEach { issue ->
                                                val loc = if (issue.file != null) "${issue.file}${if (issue.line != null) ":${issue.line}" else ""}" else "Global"
                                                appendLine("- **[${issue.severityEnum.uiLabel}]** $loc — ${issue.displayTitle}")
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
                            "Classifying issue severities (Critical, Warning, Info)...",
                            "Scanning for CVEs, logic flaws & memory leaks...",
                            "Synthesizing production review & remediation steps...",
                            "Finalizing on-device AI code audit..."
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
                        IssueCard(issue = issue)
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
}

// ─── Review Summary Card ───────────────────────────────────────────────────────
@Composable
fun ReviewSummaryCard(review: ReviewResult) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (review.hasIssue) "Review Complete" else "No Issues Detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
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

            Spacer(modifier = Modifier.height(10.dp))

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
fun IssueCard(issue: CodeIssue) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

    val (badgeBg, badgeText, badgeIcon) = when (issue.severityEnum) {
        Severity.CRITICAL -> Triple(SeverityCritical.copy(alpha = 0.15f), SeverityCritical, Icons.Default.Error)
        Severity.WARNING -> Triple(SeverityWarning.copy(alpha = 0.15f), SeverityWarning, Icons.Default.Warning)
        Severity.INFO, Severity.UNKNOWN -> Triple(BrandSurfaceHigh, BrandOnBgMuted, Icons.Default.Info)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Severity Badge + Category + Location + Verification Alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                contentDescription = "${issue.severityEnum.uiLabel} issue",
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

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrandSurfaceHigh
                    ) {
                        Text(
                            text = issue.categoryEnum.uiLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandOnBgMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
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
                            text = "Needs manual verification",
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
                        color = BrandOnBgMuted
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = fix,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBg,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Action bar: Copy & Expand
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val textToCopy = buildString {
                            appendLine("[${issue.severityEnum.uiLabel.uppercase()}] ${issue.displayTitle}")
                            if (issue.file != null) appendLine("File: ${issue.file}${if (issue.line != null) ":${issue.line}" else ""}")
                            appendLine(issue.description)
                            issue.displayFix?.let { appendLine("Fix: $it") }
                        }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        Toast.makeText(context, "Issue copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = BrandOnBgMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall, color = BrandOnBgMuted)
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
