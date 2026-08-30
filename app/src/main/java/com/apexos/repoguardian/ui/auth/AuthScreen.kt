package com.apexos.repoguardian.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.painterResource
import com.apexos.repoguardian.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.data.github.AuthState
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    var codeCopied by remember { mutableStateOf(false) }

    fun copyCodeToClipboard(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val cleanCode = code.trim().uppercase()
        val clip = ClipData.newPlainText("GitHub Auth Code", cleanCode)
        clipboard?.setPrimaryClip(clip)
        codeCopied = true
        Toast.makeText(context, "Code copied — ready to paste on GitHub", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { viewModel.startAuth() }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            navController.navigate(Routes.REPO_PICKER) {
                popUpTo(Routes.AUTH) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BrandBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_black),
                        contentDescription = "Repo Guardian Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Connect GitHub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandOnBg
                    )
                    Text(
                        "Secure Device Flow — no password stored",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBgMuted
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Main content area
            when (val state = authState) {
                is AuthState.Idle -> LoadingState("Starting secure connection...")

                is AuthState.WaitingForUser -> {
                    LaunchedEffect(state.response.userCode) {
                        copyCodeToClipboard(state.response.userCode)
                    }
                    WaitingState(
                        userCode = state.response.userCode,
                        verificationUri = state.response.verificationUri,
                        transitionCountdown = state.transitionCountdown,
                        codeCopied = codeCopied,
                        onCopyCode = { copyCodeToClipboard(state.response.userCode) },
                        onProceedEarly = { viewModel.proceedToVerificationEarly() },
                        context = context
                    )
                }

                is AuthState.Verifying -> {
                    VerifyingState(
                        userCode = state.response.userCode,
                        verificationUri = state.response.verificationUri,
                        remainingSeconds = state.remainingSeconds,
                        onGenerateNewCode = { viewModel.startAuth() },
                        context = context
                    )
                }

                is AuthState.Success -> {
                    SuccessState()
                }

                is AuthState.Timeout -> {
                    TimeoutState(
                        message = state.message,
                        onGenerateNewCode = { viewModel.startAuth() }
                    )
                }

                is AuthState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.startAuth() }
                    )
                }
            }
        }
    }
}

// ─── States ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator(
            color = BrandEmerald,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = BrandOnBgMuted)
    }
}

@Composable
private fun VerifyingState(
    userCode: String,
    verificationUri: String,
    remainingSeconds: Int,
    onGenerateNewCode: () -> Unit,
    context: Context
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp)
    ) {
        CircularProgressIndicator(
            color = BrandEmerald,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Verifying on GitHub...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandOnBg
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BrandSurfaceElev,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = BrandEmeraldLight,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Verification expires in ${remainingSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandEmeraldLight
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Approve code $userCode in your browser to complete authorization.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandOnBgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri)))
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
        ) {
            Icon(
                Icons.Filled.OpenInBrowser,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = BrandOnBg
            )
            Spacer(Modifier.width(8.dp))
            Text("Open GitHub Login", color = BrandOnBg, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onGenerateNewCode) {
            Text("Cancel & Generate New Code", color = BrandOnBgMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TimeoutState(
    message: String,
    onGenerateNewCode: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(StatusFail.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = StatusFail
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Authorization Expired",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandOnBg
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = StatusFail,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "This authorization code has expired. Generate a new code to continue.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandOnBgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onGenerateNewCode,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnEmerald)
            Spacer(Modifier.width(8.dp))
            Text("Generate New Code", color = OnEmerald, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SuccessState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(StatusPass.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = StatusPass
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Connected!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = StatusPass
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(StatusFail.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = StatusFail
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Connection Failed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StatusFail
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = BrandOnBgMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandSurfaceElev)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again", color = BrandOnBg)
        }
    }
}

@Composable
private fun WaitingState(
    userCode: String,
    verificationUri: String,
    transitionCountdown: Int,
    codeCopied: Boolean,
    onCopyCode: () -> Unit,
    onProceedEarly: () -> Unit,
    context: Context
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Code card ─────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopyCode() },
            shape = RoundedCornerShape(20.dp),
            color = BrandSurface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your Auth Code",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandOnBgMuted,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(12.dp))

                // Clean Monospace Code Display
                Text(
                    text = userCode,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp
                    ),
                    color = BrandEmerald
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (codeCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (codeCopied) StatusPass else BrandOnBgMuted
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (codeCopied) "Copied to clipboard" else "Tap to copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (codeCopied) StatusPass else BrandOnBgMuted
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Expiry / transition countdown badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandEmeraldMuted,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmeraldLight.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = BrandEmeraldLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Code expires in ${transitionCountdown}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandEmeraldLight
                        )
                    }
                }
            }
        }

        // ── Action buttons ────────────────────────────────────────────────────
        Button(
            onClick = onCopyCode,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (codeCopied) StatusPass.copy(alpha = 0.15f) else BrandEmerald
            )
        ) {
            Icon(
                if (codeCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (codeCopied) StatusPass else OnEmerald
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (codeCopied) "Code Copied to Clipboard" else "Copy Auth Code",
                fontWeight = FontWeight.SemiBold,
                color = if (codeCopied) StatusPass else OnEmerald
            )
        }

        OutlinedButton(
            onClick = {
                onProceedEarly()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri)))
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
        ) {
            Icon(
                Icons.Filled.OpenInBrowser,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = BrandOnBg
            )
            Spacer(Modifier.width(8.dp))
            Text("Open GitHub Login", color = BrandOnBg, fontWeight = FontWeight.Medium)
        }

        // ── Steps card ────────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BrandSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "How to connect",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandOnBgMuted,
                    letterSpacing = 0.5.sp
                )
                listOf(
                    "1" to "Tap 'Copy Auth Code' above",
                    "2" to "Open GitHub login and sign in",
                    "3" to "Long-press the code field → Paste",
                    "4" to "Tap Continue. You're in!"
                ).forEach { (num, step) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(BrandEmeraldMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                num,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandOnBg
                        )
                    }
                }
            }
        }

        // ── Transition countdown indicator ────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.8.dp,
                color = BrandEmeraldLight
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Redirecting to verification in ${transitionCountdown}s...",
                style = MaterialTheme.typography.labelSmall,
                color = BrandOnBgMuted
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
