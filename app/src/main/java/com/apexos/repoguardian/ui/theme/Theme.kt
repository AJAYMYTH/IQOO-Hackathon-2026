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
    secondary            = BrandEmerald,
    onSecondary          = OnEmerald,
    secondaryContainer   = BrandSurfaceElev,
    onSecondaryContainer = BrandEmeraldLight,

    // Canvas Background
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
    errorContainer       = Color(0x2BFF5C7A),
    onErrorContainer     = Color(0xFFFF859B),

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
