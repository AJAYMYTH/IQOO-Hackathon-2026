package com.apexos.repoguardian.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.apexos.repoguardian.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // ── Entrance animation states ─────────────────────────────────────────────
    var animStarted by remember { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_scale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(600, easing = EaseOutCubic),
        label = "icon_alpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(500, delayMillis = 250, easing = EaseOutCubic),
        label = "title_alpha"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(500, delayMillis = 400, easing = EaseOutCubic),
        label = "subtitle_alpha"
    )
    val statusAlpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(400, delayMillis = 600, easing = EaseOutCubic),
        label = "status_alpha"
    )

    LaunchedEffect(Unit) {
        delay(80)
        animStarted = true
    }

    // ── Navigation after load ─────────────────────────────────────────────────
    LaunchedEffect(uiState.isLoading, uiState.showOnboarding) {
        if (!uiState.isLoading && uiState.error == null) {
            delay(600) // Let user see the "Ready" state briefly
            when {
                uiState.showOnboarding -> {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
                uiState.isAuthenticated -> {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
                else -> {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }
        }
    }

    // ── Animated glow pulse ───────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial glow
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(glowScale)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandEmerald.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Logo with ambient container
            Box(
                modifier = Modifier
                    .alpha(iconAlpha)
                    .scale(iconScale),
                contentAlignment = Alignment.Center
            ) {
                // Ambient glow outer ring
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(BrandEmerald.copy(alpha = 0.15f))
                )
                // Logo surface
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandEmeraldLight)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Repo Guardian Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // App name
            Text(
                text = "Repo Guardian",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = BrandOnBg,
                modifier = Modifier.alpha(titleAlpha),
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(6.dp))

            // Tagline pill
            Surface(
                shape = RoundedCornerShape(50),
                color = BrandEmeraldMuted,
                modifier = Modifier.alpha(subtitleAlpha)
            ) {
                Text(
                    text = "On-Device AI · Private · Offline",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandEmerald,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(Modifier.height(48.dp))

            // Status area
            Box(
                modifier = Modifier
                    .alpha(statusAlpha)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(CircleShape),
                            color = BrandEmerald,
                            trackColor = BrandSurfaceElev
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandOnBgMuted
                        )
                    }
                } else {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.error != null) StatusFail else BrandEmerald,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error retry
            if (uiState.error != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        navController.navigate(Routes.AUTH) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
                ) {
                    Text("Continue", color = BrandOnBgMuted)
                }
            }
        }

        // Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .navigationBarsPadding()
                .alpha(subtitleAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Team Apex OS",
                style = MaterialTheme.typography.labelSmall,
                color = BrandOnBgSubtle,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "iQOO Hackathon 2026",
                style = MaterialTheme.typography.labelSmall,
                color = BrandOnBgSubtle.copy(alpha = 0.6f)
            )
        }
    }
}
