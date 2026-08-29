package com.apexos.repoguardian.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RepoGuardianDarkScheme = darkColorScheme(
    // Brand Primary
    primary              = BrandEmerald,
    onPrimary            = OnEmerald,
    primaryContainer     = BrandEmeraldMuted,
    onPrimaryContainer   = BrandEmeraldLight,

    // Secondary Accent
    secondary            = Secondary,
    onSecondary          = OnSecondary,
    secondaryContainer   = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFF93C5FD),

    // Pure Black Background
    background           = BrandBackground,
    onBackground         = BrandOnBg,

    // Refined Dark Surfaces
    surface              = BrandSurface,
    onSurface            = BrandOnBg,
    surfaceVariant       = BrandSurfaceHigh,
    onSurfaceVariant     = BrandOnBgMuted,

    // Outlines & Borders
    outline              = BrandBorder,
    outlineVariant       = BrandBorderHighlight,

    // Error
    error                = StatusFail,
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFF3B1212),
    onErrorContainer     = Color(0xFFFCA5A5),

    // Inverse
    inverseSurface       = BrandOnBg,
    inverseOnSurface     = BrandBackground,
    inversePrimary       = BrandEmeraldDark,

    // Scrim
    scrim                = BrandBackground.copy(alpha = 0.85f),
    surfaceTint          = BrandEmerald
)

@Composable
fun RepoGuardianTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RepoGuardianDarkScheme,
        typography  = Typography,
        content     = content
    )
}
