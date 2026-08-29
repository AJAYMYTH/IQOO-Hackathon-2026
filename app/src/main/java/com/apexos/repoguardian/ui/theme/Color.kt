package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Brand Core ──────────────────────────────────────────────────────────────
// Deep dark surfaces — Zinc editorial hierarchy
val BrandBackground   = Color(0xFF0C0C0E)   // Near-black base
val BrandSurface      = Color(0xFF131317)   // Card level 1
val BrandSurfaceHigh  = Color(0xFF1C1C22)   // Card level 2
val BrandSurfaceElev  = Color(0xFF26262F)   // Elevated / modal
val BrandBorder       = Color(0xFF2E2E3A)   // Dividers / outlines

// Primary accent — Emerald
val BrandEmerald      = Color(0xFF34D399)   // Emerald-400 — primary CTA
val BrandEmeraldDark  = Color(0xFF059669)   // Emerald-600 — pressed / container
val BrandEmeraldMuted = Color(0xFF065F46)   // Emerald-900 — chip / tag bg
val OnEmerald         = Color(0xFF022C22)   // Text on emerald

// Text hierarchy
val BrandOnBg         = Color(0xFFEDEDF2)   // Primary text
val BrandOnBgMuted    = Color(0xFF9B9BAD)   // Secondary text
val BrandOnBgSubtle   = Color(0xFF54546A)   // Tertiary / placeholder

// ─── Status Semantic ──────────────────────────────────────────────────────────
val StatusPass        = Color(0xFF34D399)   // Same as emerald — success
val StatusFail        = Color(0xFFF87171)   // Red-400
val StatusPending     = Color(0xFFFBBF24)   // Amber-400
val StatusInfo        = Color(0xFF60A5FA)   // Blue-400

// ─── Severity ────────────────────────────────────────────────────────────────
val SeverityCritical  = Color(0xFFF87171)   // Red-400
val SeverityWarning   = Color(0xFFFBBF24)   // Amber-400
val SeverityInfo      = Color(0xFF60A5FA)   // Blue-400

// ─── Code Block ──────────────────────────────────────────────────────────────
val CodeBackground    = Color(0xFF0D1117)   // GitHub dark code bg
val CodeAddition      = Color(0xFF1A3A2A)   // Green diff bg
val CodeDeletion      = Color(0xFF3A1A1A)   // Red diff bg
val CodeLineNumber    = Color(0xFF484860)   // Line number gutter

// ─── Legacy aliases (kept for compatibility) ─────────────────────────────────
val Primary           = BrandEmerald
val PrimaryDark       = BrandEmeraldDark
val PrimaryLight      = BrandEmeraldMuted
val OnPrimary         = OnEmerald
val Secondary         = Color(0xFF60A5FA)   // Blue accent
val SecondaryDark     = Color(0xFF2563EB)
val OnSecondary       = Color(0xFF000000)
val DarkBackground    = BrandBackground
val DarkSurface       = BrandSurface
val DarkSurfaceVariant= BrandSurfaceHigh
val DarkOnBackground  = BrandOnBg
val DarkOnSurface     = BrandOnBg
