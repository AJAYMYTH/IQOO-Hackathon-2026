package com.apexos.repoguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apexos.repoguardian.ui.theme.*

data class GuideStep(
    val stepNumber: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color
)

@Composable
fun NonTechGuideDialog(
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    val steps = listOf(
        GuideStep(
            stepNumber = "1",
            title = "Connect Your GitHub",
            description = "Authenticate securely with a one-time 8-character device code. Credentials are never sent to third-party servers.",
            icon = Icons.Default.VpnKey,
            iconTint = BrandEmeraldLight
        ),
        GuideStep(
            stepNumber = "2",
            title = "Select Repository",
            description = "Use the dropdown switcher at the top to select any project for security analysis, diff inspection, or CI/CD generation.",
            icon = Icons.Default.FolderOpen,
            iconTint = BrandEmeraldLight
        ),
        GuideStep(
            stepNumber = "3",
            title = "Choose AI Model (GGUF)",
            description = "Download a private on-device model from the Hugging Face manager. Runs 100% offline with zero cloud API costs.",
            icon = Icons.Default.Memory,
            iconTint = BrandEmeraldLight
        ),
        GuideStep(
            stepNumber = "4",
            title = "Activate Think Mode",
            description = "Enable Think Mode to inspect the AI's step-by-step chain-of-thought reasoning before viewing fixes.",
            icon = Icons.Default.Psychology,
            iconTint = BrandEmeraldLight
        ),
        GuideStep(
            stepNumber = "5",
            title = "Review & Auto-PR",
            description = "Inspect commits for bugs, explore diffs, and generate pull requests directly from AI suggestions in 1 tap.",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconTint = BrandEmeraldLight
        )
    )

    Dialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = BrandSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BrandEmeraldMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = BrandEmeraldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "How to Use Repo Guardian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandOnBg
                            )
                            Text(
                                text = "Developer & Architecture Guide",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandOnBgMuted
                            )
                        }
                    }

                    IconButton(onClick = { onDismiss(dontShowAgain) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BrandOnBgMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BrandBorder)

                // Scrollable Steps List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    steps.forEach { step ->
                        StepCard(step)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BrandBorder)

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain }
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandEmerald,
                                checkmarkColor = OnEmerald,
                                uncheckedColor = BrandBorder
                            )
                        )
                        Text(
                            text = "Don't show again",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBgMuted
                        )
                    }

                    Button(
                        onClick = { onDismiss(dontShowAgain) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Get Started", fontWeight = FontWeight.SemiBold, color = OnEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: GuideStep) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(step.iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = step.iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${step.stepNumber}: ${step.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandOnBg
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandOnBgMuted,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
