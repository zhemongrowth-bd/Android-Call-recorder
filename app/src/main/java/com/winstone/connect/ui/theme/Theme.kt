package com.winstone.connect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// কালো-সোনালি ব্র্যান্ড রং — ওয়েব CRM-এর সাথে এক।
// পুরনো নামগুলো রাখা হয়েছে, শুধু মানগুলো কালো-সোনালিতে বদলেছে।
val WinGreen = Color(0xFFD4AF37)
val WinGreenSoft = Color(0xFF211C10)
val WinCream = Color(0xFF0B0B0C)
val WinCreamDeep = Color(0xFF141416)
val WinBorder = Color(0xFF2C2720)
val WinInk = Color(0xFFF6F2E7)
val WinInkMuted = Color(0xFF9E9683)
val WinAmber = Color(0xFFE2B94F)
val WinRed = Color(0xFFE0705B)
val WinLive = Color(0xFF4FC98A)
val WinSurface = Color(0xFF15151A)

private val WinstoneDark = darkColorScheme(
    primary = WinGreen,
    onPrimary = Color(0xFF17130A),
    primaryContainer = WinGreenSoft,
    onPrimaryContainer = WinGreen,
    secondary = WinAmber,
    background = WinCream,
    onBackground = WinInk,
    surface = WinSurface,
    onSurface = WinInk,
    surfaceVariant = WinCreamDeep,
    onSurfaceVariant = WinInkMuted,
    outline = WinBorder,
    outlineVariant = WinBorder,
    error = WinRed,
)

@Composable
fun WinstoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WinstoneDark, content = content)
}
