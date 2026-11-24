package com.example.pdfscan.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Fixed neutral canvas so white page edges stay visible (also with Dynamic Color). */
val ReviewCanvas = Color(0xFF14161B)

val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE7FE),
    onPrimaryContainer = Color(0xFF0B2A6B),
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF2F7),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFE2E8F0),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF1E3A6B),
    onPrimaryContainer = Color(0xFFDBE7FE),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF1C2028),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF262B36),
    onSurfaceVariant = Color(0xFF9AA3B2),
    outline = Color(0xFF2D3340),
    outlineVariant = Color(0xFF2D3340),
)
