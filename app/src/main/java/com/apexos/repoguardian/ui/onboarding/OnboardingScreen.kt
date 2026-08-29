package com.apexos.repoguardian.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.apexos.repoguardian.navigation.Routes
import com.apexos.repoguardian.ui.theme.*
import kotlinx.coroutines.launch

// ─── Data model for onboarding pages ─────────────────────────────────────────
data class OnboardingPage(
    val icon: ImageVector,
    val headline: String,
    val subhead: String,
    val body: String,
    val accentColor: Color,
    val features: List<Pair<ImageVector, String>>
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Security,
        headline = "Your Private AI\nCode Reviewer",
        subhead = "On-device. Offline. Yours.",
        body = "Repo Guardian runs powerful AI models directly on your Android phone — no cloud, no API key, no cost. Every analysis stays on your device, completely private.",
        accentColor = BrandEmerald,
        features = listOf(
            Icons.Filled.Lock to "100% offline — no data leaves your device",
            Icons.Filled.Speed to "On-device LLM inference via llama.cpp",
            Icons.Filled.PhoneAndroid to "Optimized for iQOO & Android hardware"
        )
    ),
    OnboardingPage(
        icon = Icons.Filled.Code,
        headline = "GitHub Connected.\nAI Powered.",
        subhead = "Your repos. Intelligent insights.",
        body = "Connect your GitHub account once via secure Device Flow OAuth. Then pick any repo and let the AI analyze commits, detect bugs, review diffs, and generate pull requests — all in one tap.",
        accentColor = Color(0xFF60A5FA),
        features = listOf(
            Icons.Filled.AccountTree to "Browse all your GitHub repositories",
            Icons.Filled.BugReport to "AI code review with bug & security detection",
            Icons.AutoMirrored.Filled.MergeType to "1-tap Pull Request creation from AI suggestions"
        )
    ),
    OnboardingPage(
        icon = Icons.Filled.Psychology,
        headline = "Think Mode.\nDeep Reasoning.",
        subhead = "See how the AI thinks.",
        body = "Enable Think Mode to watch the AI reason step-by-step through your code — chain-of-thought visible, CI/CD pipeline generation, and a full-context chat assistant baked in.",
        accentColor = Color(0xFFA78BFA),
        features = listOf(
            Icons.Filled.Lightbulb to "Chain-of-thought reasoning you can read",
            Icons.Filled.Terminal to "CI/CD YAML pipeline generator",
            Icons.AutoMirrored.Filled.Chat to "Full-context AI assistant chat"
        )
    )
)

// ─── Screen ───────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    onOnboardingComplete: suspend () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground)
    ) {
        // ── Ambient glow blob behind content ─────────────────────────────────
        val currentAccent = onboardingPages[currentPage].accentColor
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            currentAccent.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Skip button ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = currentPage < onboardingPages.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                onOnboardingComplete()
                                navController.navigate(Routes.AUTH) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            }
                        }
                    ) {
                        Text(
                            "Skip",
                            color = BrandOnBgMuted,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // ── Pager content ─────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPageContent(
                    page = onboardingPages[page],
                    isActive = page == currentPage
                )
            }

            // ── Bottom controls ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 48.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    repeat(onboardingPages.size) { idx ->
                        val isSelected = idx == currentPage
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 6.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) onboardingPages[currentPage].accentColor
                                    else BrandBorder
                                )
                        )
                    }
                }

                // CTA button
                val isLastPage = currentPage == onboardingPages.size - 1
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (isLastPage) {
                                onOnboardingComplete()
                                navController.navigate(Routes.AUTH) {
                                    popUpTo(Routes.SPLASH) { inclusive = true }
                                }
                            } else {
                                pagerState.animateScrollToPage(currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastPage) BrandEmerald else BrandSurfaceElev
                    )
                ) {
                    if (isLastPage) {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Connect GitHub & Get Started",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = OnEmerald
                        )
                    } else {
                        Text(
                            "Next",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = BrandOnBg
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = BrandOnBg
                        )
                    }
                }
            }
        }
    }
}

// ─── Page Content Composable ──────────────────────────────────────────────────
@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    isActive: Boolean
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.85f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "icon_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.3f,
        animationSpec = tween(300),
        label = "content_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with tinted background circle
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(iconScale)
                .clip(CircleShape)
                .background(page.accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = page.accentColor
            )
        }

        Spacer(Modifier.height(32.dp))

        // Headline
        Text(
            text = page.headline,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = BrandOnBg,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(Modifier.height(8.dp))

        // Subhead pill
        Surface(
            shape = RoundedCornerShape(50),
            color = page.accentColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = page.subhead,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = page.accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))

        // Body text
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandOnBgMuted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(28.dp))

        // Feature list
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            page.features.forEach { (icon, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(page.accentColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = page.accentColor
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandOnBg,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
