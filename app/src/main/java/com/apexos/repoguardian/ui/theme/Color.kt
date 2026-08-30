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

// ─── Primary Accent — Electric Cyan / Teal (#00E5C3) ─────────────────────────
// Used with surgical restraint only where emphasis is necessary.
val BrandEmerald           = Color(0xFF00E5C3)   // Primary Accent #00E5C3
val BrandEmeraldLight      = Color(0xFF38EFD6)   // Cyan-400: Active glow, status badges, highlight
val BrandEmeraldDark       = Color(0xFF00BFA2)   // Cyan-600: Pressed container state
val BrandEmeraldMuted      = Color(0x1F00E5C3)   // 12% alpha subtle accent backing
val OnEmerald              = Color(0xFF041210)   // High-contrast dark text on cyan accent

// ─── Semantic Status ─────────────────────────────────────────────────────────
val StatusPass             = Color(0xFF00E5C3)   // Electric Cyan success / pass
val StatusFail             = Color(0xFFFF5C7A)   // Error #FF5C7A
val StatusPending          = Color(0xFFF59E0B)   // Amber-500 warning
val StatusInfo             = Color(0xFF38BDF8)   // Sky-400 information

// ─── Severity Semantic ───────────────────────────────────────────────────────
val SeverityCritical       = Color(0xFFFF5C7A)
val SeverityWarning        = Color(0xFFF59E0B)
val SeverityInfo           = Color(0xFF38BDF8)

// ─── Frosted Glassmorphism Tokens ────────────────────────────────────────────
val GlassBackground        = Color(0xEB111113)   // Frosted primary surface
val GlassBackgroundElev    = Color(0xF218181B)   // Frosted elevated surface
val GlassBorder            = Color(0x3327272A)   // Hairline border

// ─── Code & Terminal Blocks ──────────────────────────────────────────────────
val CodeBackground         = Color(0xFF0E0E10)   // Matte deep code container
val CodeAddition           = Color(0x1A00E5C3)   // Subtle cyan diff bg
val CodeDeletion           = Color(0x24FF5C7A)   // Subtle red diff bg
val CodeLineNumber         = BrandGreige         // Line numbers

// ─── Backward Compatibility Aliases ──────────────────────────────────────────
val Primary                = BrandEmerald
val PrimaryDark            = BrandEmeraldDark
val PrimaryLight           = BrandEmeraldMuted
val OnPrimary              = OnEmerald
val Secondary              = Color(0xFF38BDF8)
val SecondaryDark          = Color(0xFF0284C7)
val OnSecondary            = BrandOnBg
val DarkBackground         = BrandBackground
val DarkSurface            = BrandSurface
val DarkSurfaceVariant     = BrandSurfaceHigh
val DarkOnBackground       = BrandOnBg
val DarkOnSurface          = BrandOnBg
