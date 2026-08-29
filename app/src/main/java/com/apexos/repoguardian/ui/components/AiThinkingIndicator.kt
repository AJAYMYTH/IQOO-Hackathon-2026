package com.apexos.repoguardian.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexos.repoguardian.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AiThinkingIndicator(
    modifier: Modifier = Modifier,
    thinkingPhases: List<String> = listOf(
        "Analyzing repository context & AST...",
        "Evaluating code diffs & memory safety...",
        "Synthesizing on-device reasoning chain...",
        "Formatting response..."
    )
) {
    var phaseIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            phaseIndex = (phaseIndex + 1) % thinkingPhases.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val dotAnimationAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dotAnimationAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dotAnimationAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI Avatar Badge
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(BrandEmeraldMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "AI Thinking",
                modifier = Modifier.size(16.dp),
                tint = BrandEmeraldLight
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Thinking Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            color = BrandSurface,
            border = BorderStroke(1.dp, BrandBorder),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated Pulsing Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(BrandEmeraldLight.copy(alpha = dotAnimationAlpha1))
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(BrandEmeraldLight.copy(alpha = dotAnimationAlpha2))
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(BrandEmeraldLight.copy(alpha = dotAnimationAlpha3))
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Smooth Phase Cross-Fade
                AnimatedContent(
                    targetState = thinkingPhases[phaseIndex],
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "thinkingPhase"
                ) { phaseText ->
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = BrandOnBgSubtle
                    )
                }
            }
        }
    }
}
