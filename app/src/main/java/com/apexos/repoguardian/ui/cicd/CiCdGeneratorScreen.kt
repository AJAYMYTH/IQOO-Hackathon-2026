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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CiCdGeneratorScreen(
    navController: NavController,
    viewModel: CiCdGeneratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CI/CD Generator") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.regenerate() },
                        enabled = !uiState.isGenerating
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.generatedYaml.isNotBlank() && !uiState.committed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { viewModel.commitWorkflow() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = !uiState.isCommitting
                    ) {
                        if (uiState.isCommitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Committing...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Commit Workflow to Repo")
                        }
                    }
                }
            }

            if (uiState.committed) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Repo info
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${uiState.owner}/${uiState.repo}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (uiState.detectedLanguage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Detected: ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusInfo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = uiState.detectedLanguage ?: "Unknown",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusInfo,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Loading state
            if (uiState.isGenerating) {
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
                                text = "Generating CI/CD Workflow",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Using on-device AI...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // YAML preview
            if (uiState.generatedYaml.isNotBlank()) {
                Text(
                    text = "Generated Workflow",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Editable YAML
                OutlinedTextField(
                    value = uiState.generatedYaml,
                    onValueChange = { viewModel.updateYaml(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(".github/workflows/ci.yml") }
                )
            }

            // Error
            uiState.error?.let { error ->
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
