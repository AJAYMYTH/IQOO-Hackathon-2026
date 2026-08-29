package com.apexos.repoguardian.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.apexos.repoguardian.R
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // ── Pure Black background constant ───────────────────────────────────────
    val pureBlack = Color(0xFF000000)

    // ── Entrance & Netlify Zoom-Out Reveal Transitions ────────────────────────
    var animStarted by remember { mutableStateOf(false) }
    var isRevealing by remember { mutableStateOf(false) }

    val contentScale by animateFloatAsState(
        targetValue = when {
            isRevealing -> 1.42f
            animStarted -> 1.0f
            else -> 0.72f
        },
        animationSpec = if (isRevealing) {
            tween(420, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "content_scale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = when {
            isRevealing -> 0f
            animStarted -> 1f
            else -> 0f
        },
        animationSpec = if (isRevealing) {
            tween(380, easing = EaseInCubic)
        } else {
            tween(500, easing = EaseOutCubic)
        },
        label = "content_alpha"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (animStarted && !isRevealing) 1f else 0f,
        animationSpec = tween(450, delayMillis = 180, easing = EaseOutCubic),
        label = "title_alpha"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (animStarted && !isRevealing) 1f else 0f,
        animationSpec = tween(450, delayMillis = 300, easing = EaseOutCubic),
        label = "subtitle_alpha"
    )

    val statusAlpha by animateFloatAsState(
        targetValue = if (animStarted && !isRevealing) 1f else 0f,
        animationSpec = tween(400, delayMillis = 450, easing = EaseOutCubic),
        label = "status_alpha"
    )

    LaunchedEffect(Unit) {
        delay(50)
        animStarted = true
    }

    // ── Navigation with Netlify Zoom Reveal Transition ────────────────────────
    LaunchedEffect(uiState.isLoading, uiState.showOnboarding) {
        if (!uiState.isLoading && uiState.error == null) {
            delay(400) // Brief status pause
            isRevealing = true // Trigger Netlify zoom-out reveal
            delay(390) // Wait for smooth expansion transition

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

    // ── Ambient Emerald Glow Pulse ────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    // ── Layout on #000000 ─────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pureBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial emerald background glow
        Box(
            modifier = Modifier
                .size(360.dp)
                .scale(if (isRevealing) glowScale * 2.4f else glowScale)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandEmerald.copy(alpha = if (isRevealing) 0.35f else glowAlpha),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .scale(contentScale)
                .alpha(contentAlpha)
        ) {
            // Main Brand Logo Element (Black background logo seamlessly integrated)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                // Subtle glowing ring behind logo
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(BrandEmerald.copy(alpha = 0.10f))
                )

                Image(
                    painter = painterResource(id = R.drawable.app_logo_black),
                    contentDescription = "Repo Guardian Brand Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(Modifier.height(22.dp))

            // App Name
            Text(
                text = "Repo Guardian",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(titleAlpha),
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(6.dp))

            // Tagline Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = BrandEmeraldMuted,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmeraldLight.copy(alpha = 0.3f)),
                modifier = Modifier.alpha(subtitleAlpha)
            ) {
                Text(
                    text = "On-Device AI · Private · Offline",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandEmeraldLight,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(Modifier.height(42.dp))

            // Status Area
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

            // Error Retry
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
