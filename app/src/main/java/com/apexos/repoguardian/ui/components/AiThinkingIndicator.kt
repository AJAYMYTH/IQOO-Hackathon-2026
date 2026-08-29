package com.apexos.repoguardian.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexos.repoguardian.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    showShimmer: Boolean = true
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            BrandSurfaceElev,
            BrandEmeraldMuted.copy(alpha = 0.6f),
            BrandSurfaceElev
        )

        val transition = rememberInfiniteTransition(label = "shimmerTransition")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

@Composable
fun AiThinkingIndicator(
    modifier: Modifier = Modifier,
    thinkingPhases: List<String> = listOf(
        "Analyzing repository context & AST...",
        "Evaluating code diffs & memory safety...",
        "Synthesizing on-device reasoning chain...",
        "Formatting verified patch response..."
    )
) {
    var phaseIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            phaseIndex = (phaseIndex + 1) % thinkingPhases.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = BrandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(BrandEmeraldMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = "AI Thinking",
                        tint = BrandEmeraldLight,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "AI Thinking & Reasoning",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandEmeraldLight
                    )
                    Text(
                        text = "Local llama.cpp inference",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = BrandOnBgSubtle
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { index ->
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = index * 180, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandEmeraldLight.copy(alpha = dotAlpha))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shimmering Reasoning Status Text
            Text(
                text = thinkingPhases[phaseIndex],
                style = MaterialTheme.typography.bodySmall,
                color = BrandOnBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush())
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Animated Shimmer Skeleton Bars
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}
