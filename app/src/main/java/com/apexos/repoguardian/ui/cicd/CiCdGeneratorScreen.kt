package com.apexos.repoguardian.ui.cicd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.apexos.repoguardian.ui.theme.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiCdGeneratorScreen(
    navController: NavController,
    viewModel: CiCdGeneratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                        title = { Text("CI/CD Pipeline Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (uiState.generatedYaml.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("CI/CD Workflow YAML", uiState.generatedYaml)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Workflow YAML copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy YAML", tint = BrandEmeraldLight)
                                }
                            }
                            IconButton(
                                onClick = { viewModel.regenerate() },
                                enabled = !uiState.isGenerating
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                            }
                        }
                    )
                    HorizontalDivider(color = BrandBorder, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            if (uiState.generatedYaml.isNotBlank() && !uiState.committed) {
                Surface(
                    color = GlassBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Button(
                        onClick = { viewModel.commitWorkflow() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        enabled = !uiState.isCommitting
                    ) {
                        if (uiState.isCommitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = OnEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Committing to GitHub...", color = OnEmerald)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = OnEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Commit Workflow to Repository", color = OnEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (uiState.committed) {
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
                        Text(
                            text = "Workflow committed to .github/workflows/ci.yml",
                            fontWeight = FontWeight.Medium,
                            color = StatusPass
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BrandBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Repo info
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

                    if (uiState.detectedLanguage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Detected Stack: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BrandSurfaceHigh,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                            ) {
                                Text(
                                    text = uiState.detectedLanguage ?: "Unknown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandEmeraldLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Loading state
            if (uiState.isGenerating) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandSurfaceElev,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = BrandEmerald
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Synthesizing CI/CD Pipeline",
                                fontWeight = FontWeight.SemiBold,
                                color = BrandOnBg
                            )
                            Text(
                                text = "Generating tailored GitHub Actions workflow...",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandOnBgMuted
                            )
                        }
                    }
                }
            }

            // YAML preview
            if (uiState.generatedYaml.isNotBlank()) {
                Text(
                    text = "Generated Workflow (.github/workflows/ci.yml)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandOnBg
                )

                OutlinedTextField(
                    value = uiState.generatedYaml,
                    onValueChange = { viewModel.updateYaml(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CodeBackground,
                        unfocusedContainerColor = CodeBackground,
                        focusedBorderColor = BrandEmerald,
                        unfocusedBorderColor = BrandBorder,
                        focusedTextColor = BrandOnBg,
                        unfocusedTextColor = BrandOnBg
                    )
                )
            }

            // Error
            uiState.error?.let { error ->
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
