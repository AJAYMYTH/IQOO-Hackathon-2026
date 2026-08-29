package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Anti-Slop Calibrated Neutral Scale (Carbon Titanium) ───────────────────
// Refined, non-glaring typography and structural neutral steps.
val NeutralTitanium        = Color(0xFFEDEDEF)   // Matte Titanium: Crisp, glare-free primary text & titles
val NeutralGraphite        = Color(0xFF9CA0AB)   // Graphite-400: Balanced body text & descriptions
val NeutralMetadata        = Color(0xFF686C78)   // Graphite-500: Metadata, commit timestamps & metrics
val NeutralSubtle          = Color(0xFF484B55)   // Graphite-600: Inactive tab icons, gutters & placeholders

// ─── Surface & Background Hierarchy (Matte Carbon Architecture) ──────────────
val BrandBackground        = Color(0xFF000000)   // 100% Pure AMOLED pitch black anchor
val BrandSurface           = Color(0xFF0F1013)   // Layer 1: Deep matte obsidian surface
val BrandSurfaceHigh       = Color(0xFF16181D)   // Layer 2: Card container with subtle depth
val BrandSurfaceElev       = Color(0xFF1E2028)   // Layer 3: Floating pill navigation, dialogs & popups
val BrandBorder            = Color(0xFF272A33)   // 1px hairline boundary (anti-glow, surgical)
val BrandBorderHighlight   = Color(0xFF383C48)   // Active focus / selected container outline

// ─── Frosted Glassmorphism Tokens ────────────────────────────────────────────
val GlassBackground        = Color(0xE616181D)   // 90% alpha frosted carbon card
val GlassBackgroundElev    = Color(0xF21E2028)   // 95% alpha frosted elevated surface
val GlassBorder            = Color(0x2E383C48)   // 18% alpha frosted slate hairline

// ─── Precision Accent — Alpine Mint (Anti-Slop Surgical Green) ──────────────
// Restrained, desaturated alpine tone — avoids neon AI-slop green.
val BrandEmerald           = Color(0xFF10B981)   // Emerald-500: Primary CTA & security indicator
val BrandEmeraldLight      = Color(0xFF34D399)   // Alpine Mint-400: Active glow, status badges, pill highlight
val BrandEmeraldDark       = Color(0xFF059669)   // Emerald-600: Pressed container state
val BrandEmeraldMuted      = Color(0xFF064E3B)   // Deep emerald container backing
val OnEmerald              = Color(0xFF022C22)   // High-contrast dark text on accent

// ─── Semantic Status (Strictly Functional) ───────────────────────────────────
val StatusPass             = Color(0xFF10B981)   // Alpine Mint success
val StatusFail             = Color(0xFFF43F5E)   // Rose-500 error (calibrated, not pure red)
val StatusPending          = Color(0xFFF59E0B)   // Amber-500 warning
val StatusInfo             = Color(0xFF38BDF8)   // Sky-400 information

// ─── Severity Semantic ───────────────────────────────────────────────────────
val SeverityCritical       = Color(0xFFF43F5E)
val SeverityWarning        = Color(0xFFF59E0B)
val SeverityInfo           = Color(0xFF38BDF8)

// ─── Typography & Content Tokens ─────────────────────────────────────────────
val BrandOnBg              = NeutralTitanium     // 0xFFEDEDEF: Primary text & prominent titles
val BrandOnBgMuted         = NeutralGraphite     // 0xFF9CA0AB: Secondary text & descriptions
val BrandGreige            = NeutralMetadata     // 0xFF686C78: Metadata, commit dates & metrics
val BrandOnBgSubtle        = NeutralSubtle       // 0xFF484B55: Tertiary text, placeholders & inactive icons

// ─── Code & Terminal Blocks ──────────────────────────────────────────────────
val CodeBackground         = Color(0xFF0C0D10)   // Matte deep code container
val CodeAddition           = Color(0xFF0E2316)   // Subtle green diff bg
val CodeDeletion           = Color(0xFF260E0E)   // Subtle red diff bg
val CodeLineNumber         = NeutralSubtle       // 0xFF484B55: Gutter text

// ─── Backward Compatibility Aliases ──────────────────────────────────────────
val Primary                = BrandEmerald
val PrimaryDark            = BrandEmeraldDark
val PrimaryLight           = BrandEmeraldMuted
val OnPrimary              = OnEmerald
val Secondary              = Color(0xFF38BDF8)
val SecondaryDark          = Color(0xFF0284C7)
val OnSecondary            = NeutralTitanium
val DarkBackground         = BrandBackground
val DarkSurface            = BrandSurface
val DarkSurfaceVariant     = BrandSurfaceHigh
val DarkOnBackground       = BrandOnBg
val DarkOnSurface          = BrandOnBg
