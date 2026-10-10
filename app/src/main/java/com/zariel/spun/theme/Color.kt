package com.zariel.spun.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Spun palette
//
// Design intent:
//   – Dark-first surfaces: near-black with subtle warm undertone, not pure black.
//   – Off-white content: warm white for primary text, cooler grey for secondary.
//   – Amber accent: the "record" color — warm, physical, not garish.
//   – Orange/red for destructive or strong emphasis.
//   – No purple. No corporate blue.
// ---------------------------------------------------------------------------

// Surfaces (dark hierarchy)
val SpunBlack        = Color(0xFF0D0C0B)   // deepest background
val SpunSurface      = Color(0xFF161412)   // primary surface cards
val SpunSurfaceMid   = Color(0xFF1E1C1A)   // secondary surface / bottom sheets
val SpunSurfaceHigh  = Color(0xFF2A2826)   // elevated surface / chips

// Dividers / outlines
val SpunOutline      = Color(0xFF3A3836)
val SpunOutlineWeak  = Color(0xFF252321)

// Content (on-dark)
val SpunOnSurface    = Color(0xFFF5F0EA)   // primary text — warm white
val SpunOnSurfaceMed = Color(0xFF9C9790)   // secondary text / metadata
val SpunOnSurfaceLow = Color(0xFF5A5752)   // disabled / placeholder

// Accent — amber/gold, inspired by tape and analogue hardware
val SpunAmber        = Color(0xFFDDA04A)   // primary accent (play indicator, active control)
val SpunAmberDim     = Color(0xFF7A5822)   // dimmed accent (inactive track)
val SpunAmberGlow    = Color(0x33DDA04A)   // ambient glow layer (subtle)

// State colors
val SpunError        = Color(0xFFCF6679)
val SpunSuccess      = Color(0xFF5DBB8A)

// Light scheme (system light mode — secondary priority)
val SpunLightBg      = Color(0xFFF4F0EA)
val SpunLightSurface = Color(0xFFFFFFFF)
val SpunLightSurfaceMid = Color(0xFFEEEAE3)
val SpunLightOnSurface   = Color(0xFF1A1916)
val SpunLightOnSurfaceMed = Color(0xFF6B6660)
val SpunLightOutline = Color(0xFFD0CBC3)
