package com.dizicambazi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF150A20); val surface = Color(0xFF21142F); val surface2 = Color(0xFF2E1E40)
    val ink = Color(0xFFFBF0FF); val muted = Color(0xFFB9A6CB); val line = Color(0xFF3A2950)
    val coral = Color(0xFFFF5C6E); val amber = Color(0xFFFFC247); val heart = Color(0xFFFF6E95); val ok = Color(0xFF4BD6A4)
    val grad = Brush.linearGradient(listOf(Color(0xFFFF4D5E), Color(0xFFFF8A3D), Color(0xFFFFB020)))
    val ring = Brush.sweepGradient(listOf(Color(0xFFFF4D5E), Color(0xFFFFB020), Color(0xFFB44CFF), Color(0xFFFF4D5E)))
    val scrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0140620)), startY = 120f)
    fun channel(c: String) = when (c) {
        "atv" -> Color(0xFFFFB84D); "Show TV" -> Color(0xFFFF6B7B); "Star TV" -> Color(0xFFF2CB4C); "Kanal D" -> Color(0xFF3FD3C6)
        "NOW" -> Color(0xFFB287FF); "TRT 1" -> Color(0xFF6FA3FF); "Kanal 7" -> Color(0xFF66CF7A); else -> muted
    }
}

object T {
    val brand = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
    val h2 = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, color = C.ink)
    val title = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, color = C.ink, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 14.sp, color = C.ink)
    val meta = TextStyle(fontSize = 13.sp, color = C.muted)
}

@Composable
fun DcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = C.coral, background = C.bg, surface = C.surface,
        onSurface = C.ink, onBackground = C.ink, surfaceVariant = C.surface2, secondary = C.amber), content = content)
}
