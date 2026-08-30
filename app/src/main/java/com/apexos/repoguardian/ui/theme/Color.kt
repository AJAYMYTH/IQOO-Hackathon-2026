package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Obsidian Mono + Monochrome One-Accent Palette ────────────────────────────
// Minimal · Technical · Premium · Professional · Clean

// ─── Surface & Background Hierarchy ──────────────────────────────────────────
val BrandBackground        = Color(0xFF000000)   // Complete Pitch Black #000000
val BrandSurface           = Color(0xFF0D0D0F)   // Primary Surface / Cards #0D0D0F
val BrandSurfaceHigh       = Color(0xFF141417)   // Mid Surface #141417
val BrandSurfaceElev       = Color(0xFF18181C)   // Elevated Surface / Floating Nav #18181C
val BrandBorder            = Color(0xFF222226)   // Thin Clean Minimal Hairline Border #222226
val BrandBorderHighlight   = Color(0xFF38383E)   // Active focus / selected border #38383E

// ─── Typography & Content Neutral Tokens ─────────────────────────────────────
val BrandOnBg              = Color(0xFFFFFFFF)   // Complete White #FFFFFF for Fonts & Primary Icons
val BrandOnBgMuted         = Color(0xFFA1A1AA)   // Secondary Text #A1A1AA
val BrandGreige            = Color(0xFF71717A)   // Muted Text #71717A
val BrandOnBgSubtle        = Color(0xFF52525B)   // Inactive tab icons, subtle placeholders #52525B

// ─── Neutral Scale Aliases ───────────────────────────────────────────────────
val NeutralTitanium        = BrandOnBg
val NeutralGraphite        = BrandOnBgMuted
val NeutralMetadata        = BrandGreige
val NeutralSubtle          = BrandOnBgSubtle

// ─── Primary Accent — Pure White (#FFFFFF) & Pure Black (#000000) ────────────
val BrandEmerald           = Color(0xFFFFFFFF)   // Pure White Accent for CTAs & Active Elements
val BrandEmeraldLight      = Color(0xFFFFFFFF)   // Pure White for Highlighting, Active Badges & Icons
val BrandEmeraldDark       = Color(0xFFE4E4E7)   // Pressed container state
val BrandEmeraldMuted      = Color(0x24FFFFFF)   // 14% alpha subtle white backing
val OnEmerald              = Color(0xFF000000)   // High-contrast complete Black on White buttons/containers

// ─── Semantic Status ─────────────────────────────────────────────────────────
val StatusPass             = Color(0xFFFFFFFF)   // Complete White for Success / Pass
val StatusFail             = Color(0xFFFF5C7A)   // Error #FF5C7A
val StatusPending          = Color(0xFFF59E0B)   // Amber-500 warning
val StatusInfo             = Color(0xFFFFFFFF)   // Pure White information

// ─── Severity Semantic ───────────────────────────────────────────────────────
val SeverityCritical       = Color(0xFFFF5C7A)
val SeverityWarning        = Color(0xFFF59E0B)
val SeverityInfo           = Color(0xFFFFFFFF)

// ─── Frosted Glassmorphism Tokens ────────────────────────────────────────────
val GlassBackground        = Color(0xEB0D0D0F)   // Frosted primary surface
val GlassBackgroundElev    = Color(0xF218181C)   // Frosted elevated surface
val GlassBorder            = Color(0x33222226)   // Hairline border

// ─── Code & Terminal Blocks ──────────────────────────────────────────────────
val CodeBackground         = Color(0xFF0A0A0C)   // Matte deep code container
val CodeAddition           = Color(0x20FFFFFF)   // Subtle white diff bg
val CodeDeletion           = Color(0x24FF5C7A)   // Subtle red diff bg
val CodeLineNumber         = BrandGreige         // Line numbers

// ─── Backward Compatibility Aliases ──────────────────────────────────────────
val Primary                = BrandEmerald
val PrimaryDark            = BrandEmeraldDark
val PrimaryLight           = BrandEmeraldMuted
val OnPrimary              = OnEmerald
val Secondary              = Color(0xFFFFFFFF)
val SecondaryDark          = Color(0xFFE4E4E7)
val OnSecondary            = Color(0xFF000000)
val DarkBackground         = BrandBackground
val DarkSurface            = BrandSurface
val DarkSurfaceVariant     = BrandSurfaceHigh
val DarkOnBackground       = BrandOnBg
val DarkOnSurface          = BrandOnBg
