package com.apexos.repoguardian.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Pure Black Brand Hierarchy ──────────────────────────────────────────────
val BrandBackground       = Color(0xFF000000)   // 100% Pure Black base
val BrandSurface          = Color(0xFF0C0C0E)   // Deep surface
val BrandSurfaceHigh      = Color(0xFF141418)   // Card surface
val BrandSurfaceElev      = Color(0xFF1E1E24)   // Elevated / modal
val BrandBorder           = Color(0xFF22222A)   // Subtle border lines
val BrandBorderHighlight  = Color(0xFF2E2E38)   // Highlight border

// Glassmorphism tokens
val GlassBackground       = Color(0xCC0C0C0E)   // 80% alpha frosted surface
val GlassBackgroundElev   = Color(0xE6141418)   // 90% alpha frosted surface
val GlassBorder           = Color(0x33FFFFFF)   // Frosted 20% white border

// Primary accent — Emerald
val BrandEmerald          = Color(0xFF10B981)   // Emerald-500 — primary CTA
val BrandEmeraldLight     = Color(0xFF34D399)   // Emerald-400 — glowing text / badges
val BrandEmeraldDark      = Color(0xFF059669)   // Emerald-600 — pressed / container
val BrandEmeraldMuted     = Color(0xFF064E3B)   // Emerald-900 — chip / tag bg
val OnEmerald             = Color(0xFF022C22)   // Text on emerald

// Text hierarchy
val BrandOnBg             = Color(0xFFF3F4F6)   // Primary text (Zinc-100)
val BrandOnBgMuted        = Color(0xFF9CA3AF)   // Secondary text (Zinc-400)
val BrandOnBgSubtle       = Color(0xFF4B5563)   // Tertiary / placeholder (Zinc-600)

// ─── Status Semantic ──────────────────────────────────────────────────────────
val StatusPass            = Color(0xFF10B981)   // Emerald success
val StatusFail            = Color(0xFFEF4444)   // Red error
val StatusPending         = Color(0xFFF59E0B)   // Amber warning
val StatusInfo            = Color(0xFF3B82F6)   // Blue information

// ─── Severity ────────────────────────────────────────────────────────────────
val SeverityCritical      = Color(0xFFEF4444)
val SeverityWarning       = Color(0xFFF59E0B)
val SeverityInfo          = Color(0xFF3B82F6)

// ─── Code Block ──────────────────────────────────────────────────────────────
val CodeBackground        = Color(0xFF08080A)   // Near-black code container
val CodeAddition          = Color(0xFF0D2818)   // Subtle green diff bg
val CodeDeletion          = Color(0xFF280D0D)   // Subtle red diff bg
val CodeLineNumber        = Color(0xFF3F3F4E)   // Gutter text

// ─── Legacy aliases for full backward compatibility ──────────────────────────
val Primary               = BrandEmerald
val PrimaryDark           = BrandEmeraldDark
val PrimaryLight          = BrandEmeraldMuted
val OnPrimary             = OnEmerald
val Secondary             = Color(0xFF3B82F6)
val SecondaryDark         = Color(0xFF1D4ED8)
val OnSecondary           = Color(0xFFFFFFFF)
val DarkBackground        = BrandBackground
val DarkSurface           = BrandSurface
val DarkSurfaceVariant    = BrandSurfaceHigh
val DarkOnBackground      = BrandOnBg
val DarkOnSurface         = BrandOnBg
