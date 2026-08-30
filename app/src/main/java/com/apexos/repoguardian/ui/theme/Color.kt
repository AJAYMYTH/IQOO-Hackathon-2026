package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Slate Dev Dark — Professional Developer Tool Theme ───────────────────────
// Inspired by: GitHub Mobile dark, Linear, Vercel dashboard
// Logic: Near-black slate canvas → 3-level surface lift → one indigo accent
// Accent principle: #6E78FF (indigo-periwinkle) — trusted, developer-native,
//                   not purple-AI-cliché, not neon, not teal
// ─────────────────────────────────────────────────────────────────────────────

// ─── Canvas & Surface Hierarchy ──────────────────────────────────────────────
// Each level is ~8 luminance points lighter — enough to feel distinct without
// looking washed out. The subtle blue undertone (0D0E11) lets cards lift.
val BrandBackground        = Color(0xFF0D0E11)  // Root canvas — near-black, cool slate
val BrandSurface           = Color(0xFF161820)  // Cards, list rows
val BrandSurfaceHigh       = Color(0xFF1E2030)  // Elevated cards, selected states
val BrandSurfaceElev       = Color(0xFF252840)  // Sheets, modals, bottom nav pill
val BrandBorder            = Color(0xFF2A2D3E)  // Default hairline border
val BrandBorderHighlight   = Color(0xFF3D4163)  // Active / focused border

// ─── Typography — 3 deliberate levels ────────────────────────────────────────
val BrandOnBg              = Color(0xFFF0F0F4)  // Primary text — warm off-white, not blinding
val BrandOnBgMuted         = Color(0xFF8B8B9B)  // Secondary labels, descriptions
val BrandGreige            = Color(0xFF52525F)  // Muted — timestamps, placeholders
val BrandOnBgSubtle        = Color(0xFF3A3A47)  // Disabled, inactive tab icons

// ─── Primary Accent — Indigo/Periwinkle #6E78FF ──────────────────────────────
// Why: GitHub uses this family for links, Linear uses it for active states.
// Reads "developer tooling" without AI-glow. Stands out on slate without neon.
val BrandEmerald           = Color(0xFF6E78FF)  // Primary accent — indigo
val BrandEmeraldLight      = Color(0xFF9AA0FF)  // Light accent — hover, secondary badges
val BrandEmeraldDark       = Color(0xFF4A55E0)  // Pressed state
val BrandEmeraldMuted      = Color(0x286E78FF)  // 16% alpha — tinted containers, tags
val OnEmerald              = Color(0xFFFFFFFF)  // Text/icon on accent-colored surfaces

// ─── Semantic Status Colors — distinct and unambiguous ───────────────────────
val StatusPass             = Color(0xFF3FB950)  // GitHub green — success / pass
val StatusFail             = Color(0xFFFF5C7A)  // Error — red
val StatusPending          = Color(0xFFD29922)  // Warning — amber (GitHub amber)
val StatusInfo             = Color(0xFF58A6FF)  // Info — GitHub blue

// ─── Severity Badges ─────────────────────────────────────────────────────────
val SeverityCritical       = Color(0xFFFF5C7A)  // Red — critical issues
val SeverityWarning        = Color(0xFFD29922)  // Amber — warnings
val SeverityInfo           = Color(0xFF58A6FF)  // Blue — info / suggestions

// ─── Code & Diff Blocks ──────────────────────────────────────────────────────
val CodeBackground         = Color(0xFF0A0B0E)  // Slightly deeper than canvas for code
val CodeAddition           = Color(0x283FB950)  // 16% green tint — added lines
val CodeDeletion           = Color(0x28FF5C7A)  // 16% red tint — removed lines
val CodeLineNumber         = BrandGreige        // Line numbers

// ─── Glassmorphism / Frosted Tokens (used in bottom nav, overlays) ───────────
val GlassBackground        = Color(0xE8161820)  // Frosted top-bar — 91% opaque, subtle translucency
val GlassBackgroundElev    = Color(0xEC252840)  // Frosted sheet — slightly more opaque
val GlassBorder            = Color(0x3A2A2D3E)  // Frosted hairline

// ─── Backward Compatibility Aliases ──────────────────────────────────────────
val NeutralTitanium        = BrandOnBg
val NeutralGraphite        = BrandOnBgMuted
val NeutralMetadata        = BrandGreige
val NeutralSubtle          = BrandOnBgSubtle
val Primary                = BrandEmerald
val PrimaryDark            = BrandEmeraldDark
val PrimaryLight           = BrandEmeraldMuted
val OnPrimary              = OnEmerald
val Secondary              = BrandEmeraldLight
val SecondaryDark          = BrandEmeraldDark
val OnSecondary            = OnEmerald
val DarkBackground         = BrandBackground
val DarkSurface            = BrandSurface
val DarkSurfaceVariant     = BrandSurfaceHigh
val DarkOnBackground       = BrandOnBg
val DarkOnSurface          = BrandOnBg
