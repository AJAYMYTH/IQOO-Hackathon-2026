package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Precision Neutral Scale (Zinc-Slate Spectral Harmony) ────────────────────
val NeutralWhite           = Color(0xFFF8FAFC)   // Slate-50: Crisp primary foreground text & headings
val NeutralLight           = Color(0xFF94A3B8)   // Slate-400: Balanced secondary text & body copy
val NeutralMetadata        = Color(0xFF64748B)   // Slate-500: Metadata, timestamps & metrics
val NeutralMuted           = Color(0xFF475569)   // Slate-600: Inactive icons, line gutters & placeholders

// ─── Surface & Background Hierarchy (Obsidian Zinc Depth) ────────────────────
val BrandBackground        = Color(0xFF000000)   // 100% Pure AMOLED pitch black base
val BrandSurface           = Color(0xFF0E1013)   // Level 1: Deep obsidian surface
val BrandSurfaceHigh       = Color(0xFF15181E)   // Level 2: Layered card container
val BrandSurfaceElev       = Color(0xFF1E222A)   // Level 3: Elevated floating pill, modals & popups
val BrandBorder            = Color(0xFF262A34)   // Clean hairline border
val BrandBorderHighlight   = Color(0xFF3B4252)   // Active focus / selected border

// ─── Frosted Glassmorphism Tokens ────────────────────────────────────────────
val GlassBackground        = Color(0xE615181E)   // 90% alpha frosted surface
val GlassBackgroundElev    = Color(0xF21E222A)   // 95% alpha frosted elevated surface
val GlassBorder            = Color(0x333B4252)   // 20% alpha subtle glass border

// ─── Primary Brand Accent — Precision Mint / Emerald ────────────────────────
val BrandEmerald           = Color(0xFF10B981)   // Emerald-500: Primary CTA & active indicator
val BrandEmeraldLight      = Color(0xFF34D399)   // Mint-400: Glowing text, active tab, status badges
val BrandEmeraldDark       = Color(0xFF059669)   // Emerald-600: Pressed container state
val BrandEmeraldMuted      = Color(0xFF064E3B)   // Emerald-900: Subtle chip / tag container bg
val OnEmerald              = Color(0xFF022C22)   // High-contrast text on primary emerald

// ─── Typography & Content Tokens ─────────────────────────────────────────────
val BrandOnBg              = NeutralWhite        // 0xFFF8FAFC: Primary text & prominent titles
val BrandOnBgMuted         = NeutralLight        // 0xFF94A3B8: Secondary text & descriptions
val BrandGreige            = NeutralMetadata     // 0xFF64748B: Metadata, commit dates & metrics
val BrandOnBgSubtle        = NeutralMuted        // 0xFF475569: Tertiary text, placeholders & inactive icons

// ─── Status Semantic ──────────────────────────────────────────────────────────
val StatusPass             = Color(0xFF10B981)   // Emerald success
val StatusFail             = Color(0xFFEF4444)   // Red error
val StatusPending          = Color(0xFFF59E0B)   // Amber warning
val StatusInfo             = Color(0xFF38BDF8)   // Sky Blue information

// ─── Severity ────────────────────────────────────────────────────────────────
val SeverityCritical       = Color(0xFFEF4444)
val SeverityWarning        = Color(0xFFF59E0B)
val SeverityInfo           = Color(0xFF38BDF8)

// ─── Code Block ──────────────────────────────────────────────────────────────
val CodeBackground         = Color(0xFF0B0C0E)   // Deep code container
val CodeAddition           = Color(0xFF0D2818)   // Green diff bg
val CodeDeletion           = Color(0xFF280D0D)   // Red diff bg
val CodeLineNumber         = NeutralMuted        // 0xFF475569: Gutter text

// ─── Backward Compatibility Aliases ──────────────────────────────────────────
val Primary                = BrandEmerald
val PrimaryDark            = BrandEmeraldDark
val PrimaryLight           = BrandEmeraldMuted
val OnPrimary              = OnEmerald
val Secondary              = Color(0xFF38BDF8)
val SecondaryDark          = Color(0xFF0284C7)
val OnSecondary            = NeutralWhite
val DarkBackground         = BrandBackground
val DarkSurface            = BrandSurface
val DarkSurfaceVariant     = BrandSurfaceHigh
val DarkOnBackground       = BrandOnBg
val DarkOnSurface          = BrandOnBg
