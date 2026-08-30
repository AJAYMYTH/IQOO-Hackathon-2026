package com.apexos.repoguardian.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RepoGuardianDarkScheme = darkColorScheme(
    // Primary — Indigo accent for CTAs, active states, links
    primary              = BrandEmerald,
    onPrimary            = OnEmerald,
    primaryContainer     = BrandEmeraldMuted,
    onPrimaryContainer   = BrandEmeraldLight,

    // Secondary — lighter indigo for secondary actions
    secondary            = BrandEmeraldLight,
    onSecondary          = OnEmerald,
    secondaryContainer   = BrandSurfaceHigh,
    onSecondaryContainer = BrandOnBg,

    // Canvas — the root background
    background           = BrandBackground,
    onBackground         = BrandOnBg,

    // Surface hierarchy — 3 levels for proper card depth
    surface              = BrandSurface,
    onSurface            = BrandOnBg,
    surfaceVariant       = BrandSurfaceHigh,
    onSurfaceVariant     = BrandOnBgMuted,

    // Borders
    outline              = BrandBorder,
    outlineVariant       = BrandBorderHighlight,

    // Error
    error                = StatusFail,
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0x28FF5C7A),
    onErrorContainer     = Color(0xFFFF859B),

    // Inverse (snackbars, tooltips)
    inverseSurface       = BrandOnBg,
    inverseOnSurface     = BrandBackground,
    inversePrimary       = BrandEmeraldDark,

    // Scrim & tint
    scrim                = Color(0xCC0D0E11),
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
