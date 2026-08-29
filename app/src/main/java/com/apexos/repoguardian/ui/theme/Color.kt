package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Curated Architectural Color Palette ──────────────────────────────────────
// Off-White (#F5F5F3): A clean, very light base tone.
val PaletteOffWhite        = Color(0xFFF5F5F3)

// Light Gray (#D9D7D3): A soft, pale neutral gray.
val PaletteLightGray       = Color(0xFFD9D7D3)

// Warm Greige (#A9A6A0): A balanced gray-beige transition shade.
val PaletteWarmGreige      = Color(0xFFA9A6A0)

// Slate Gray (#5B5E63): A medium-dark cool gray.
val PaletteSlateGray       = Color(0xFF5B5E63)

// Charcoal Black (#202124): A deep, dark anchor tone.
val PaletteCharcoalBlack   = Color(0xFF202124)

// ─── Surface & Background Hierarchy ──────────────────────────────────────────
val BrandBackground        = Color(0xFF000000)          // Pure black base anchor
val BrandSurface           = Color(0xFF131416)          // Deep charcoal surface
val BrandSurfaceHigh       = Color(0xFF1A1B1E)          // Mid-depth card container
val BrandSurfaceElev       = PaletteCharcoalBlack       // 0xFF202124 — Elevated surface, modals, floating pill
val BrandBorder            = Color(0xFF2B2C30)          // Subtle border lines
val BrandBorderHighlight   = PaletteSlateGray           // 0xFF5B5E63 — Highlight border & active focus outline

// ─── Frosted Glassmorphism Tokens ────────────────────────────────────────────
val GlassBackground        = PaletteCharcoalBlack.copy(alpha = 0.85f)  // 85% frosted charcoal
val GlassBackgroundElev    = PaletteCharcoalBlack.copy(alpha = 0.95f)  // 95% frosted charcoal
val GlassBorder            = PaletteSlateGray.copy(alpha = 0.30f)      // Frosted slate border

// ─── Primary Accent — Emerald ────────────────────────────────────────────────
val BrandEmerald           = Color(0xFF10B981)   // Emerald-500 — primary CTA
val BrandEmeraldLight      = Color(0xFF34D399)   // Emerald-400 — glowing text / badges
val BrandEmeraldDark       = Color(0xFF059669)   // Emerald-600 — pressed / container
val BrandEmeraldMuted      = Color(0xFF064E3B)   // Emerald-900 — chip / tag bg
val OnEmerald              = Color(0xFF022C22)   // Text on emerald

// ─── Typography & Content Hierarchy ──────────────────────────────────────────
val BrandOnBg              = PaletteOffWhite     // 0xFFF5F5F3 — Primary text, headings & active icons
val BrandOnBgMuted         = PaletteLightGray    // 0xFFD9D7D3 — Secondary text, body copy & descriptions
val BrandGreige            = PaletteWarmGreige   // 0xFFA9A6A0 — Metadata, commit dates, metrics & annotations
val BrandOnBgSubtle        = PaletteSlateGray    // 0xFF5B5E63 — Tertiary text, placeholders & inactive icons

// ─── Status Semantic ──────────────────────────────────────────────────────────
val StatusPass             = Color(0xFF10B981)   // Emerald success
val StatusFail             = Color(0xFFEF4444)   // Red error
val StatusPending          = Color(0xFFF59E0B)   // Amber warning
val StatusInfo             = Color(0xFF3B82F6)   // Blue information

// ─── Severity ────────────────────────────────────────────────────────────────
val SeverityCritical       = Color(0xFFEF4444)
val SeverityWarning        = Color(0xFFF59E0B)
val SeverityInfo           = Color(0xFF3B82F6)

// ─── Code Block ──────────────────────────────────────────────────────────────
val CodeBackground         = Color(0xFF0F1012)   // Charcoal code container
val CodeAddition           = Color(0xFF0D2818)   // Subtle green diff bg
val CodeDeletion           = Color(0xFF280D0D)   // Subtle red diff bg
val CodeLineNumber         = PaletteSlateGray    // 0xFF5B5E63 — Gutter line numbers

// ─── Backward Compatibility Aliases ──────────────────────────────────────────
val Primary                = BrandEmerald
val PrimaryDark            = BrandEmeraldDark
val PrimaryLight           = BrandEmeraldMuted
val OnPrimary              = OnEmerald
val Secondary              = Color(0xFF3B82F6)
val SecondaryDark          = Color(0xFF1D4ED8)
val OnSecondary            = PaletteOffWhite
val DarkBackground         = BrandBackground
val DarkSurface            = BrandSurface
val DarkSurfaceVariant     = BrandSurfaceHigh
val DarkOnBackground       = BrandOnBg
val DarkOnSurface          = BrandOnBg
