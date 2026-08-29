package com.apexos.repoguardian.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Single authoritative dark scheme ────────────────────────────────────────
private val RepoGuardianDarkScheme = darkColorScheme(
    // Brand
    primary            = BrandEmerald,
    onPrimary          = OnEmerald,
    primaryContainer   = BrandEmeraldMuted,
    onPrimaryContainer = BrandEmerald,

    // Secondary (blue accent for info states)
    secondary          = Secondary,
    onSecondary        = OnSecondary,
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFF93C5FD),

    // Backgrounds
    background         = BrandBackground,
    onBackground       = BrandOnBg,

    // Surfaces
    surface            = BrandSurface,
    onSurface          = BrandOnBg,
    surfaceVariant     = BrandSurfaceHigh,
    onSurfaceVariant   = BrandOnBgMuted,

    // Outline
    outline            = BrandBorder,
    outlineVariant     = BrandSurfaceElev,

    // Error
    error              = StatusFail,
    onError            = BrandBackground,
    errorContainer     = Color(0xFF3A1A1A),
    onErrorContainer   = StatusFail,

    // Inverse
    inverseSurface     = BrandOnBg,
    inverseOnSurface   = BrandBackground,
    inversePrimary     = BrandEmeraldDark,

    // Scrim / surface tint
    scrim              = BrandBackground.copy(alpha = 0.8f),
    surfaceTint        = BrandEmerald
)

@Composable
fun RepoGuardianTheme(
    content: @Composable () -> Unit
) {
    // Premium products own their design — no dynamic color override.
    MaterialTheme(
        colorScheme = RepoGuardianDarkScheme,
        typography  = Typography,
        content     = content
    )
}
