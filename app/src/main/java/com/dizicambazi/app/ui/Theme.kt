package com.dizicambazi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colour tokens. Backed by a snapshot state so that flipping [dark] recomposes every screen.
 * Call sites keep using C.bg, C.coral … unchanged; only the values here branch on the theme.
 */
object C {
    private var darkState by mutableStateOf(true)
    var dark: Boolean
        get() = darkState
        set(v) { darkState = v }

    private fun c(darkHex: Long, lightHex: Long) = if (dark) Color(darkHex) else Color(lightHex)

    val bg get() = c(0xFF150A20, 0xFFFFF3EE)
    val surface get() = c(0xFF21142F, 0xFFFFFFFF)
    val surface2 get() = c(0xFF2E1E40, 0xFFF6E9F0)
    val ink get() = c(0xFFFBF0FF, 0xFF25112F)
    val muted get() = c(0xFFB9A6CB, 0xFF6F5E7A)
    val line get() = c(0xFF3A2950, 0xFFEBD9E2)
    val coral get() = c(0xFFFF5C6E, 0xFFF0364C)
    val amber get() = c(0xFFFFC247, 0xFFE89400)
    val heart get() = c(0xFFFF6E95, 0xFFE8265E)
    val ok get() = c(0xFF4BD6A4, 0xFF14946B)
    val grad get() = Brush.linearGradient(listOf(Color(0xFFFF4D5E), Color(0xFFFF8A3D), Color(0xFFFFB020)))
    val ring get() = Brush.sweepGradient(listOf(Color(0xFFFF4D5E), Color(0xFFFFB020), Color(0xFFB44CFF), Color(0xFFFF4D5E)))
    val scrim get() = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0140620)), startY = 120f)

    fun channel(c: String) = if (dark) when (c) {
        "atv" -> Color(0xFFFFB84D); "Show TV" -> Color(0xFFFF6B7B); "Star TV" -> Color(0xFFF2CB4C); "Kanal D" -> Color(0xFF3FD3C6)
        "NOW" -> Color(0xFFB287FF); "TRT 1" -> Color(0xFF6FA3FF); "Kanal 7" -> Color(0xFF66CF7A); else -> muted
    } else when (c) {
        "atv" -> Color(0xFFE89400); "Show TV" -> Color(0xFFF0364C); "Star TV" -> Color(0xFFC99A00); "Kanal D" -> Color(0xFF0E9F95)
        "NOW" -> Color(0xFF7B3FD0); "TRT 1" -> Color(0xFF2B6FE0); "Kanal 7" -> Color(0xFF2F9E44); else -> muted
    }
}

object T {
    val brand = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)
    val h2 get() = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, color = C.ink)
    val title get() = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, lineHeight = 22.sp, color = C.ink)
    val body get() = TextStyle(fontSize = 14.sp, color = C.ink)
    val meta get() = TextStyle(fontSize = 13.sp, color = C.muted)
}

@Composable
fun DcTheme(dark: Boolean, content: @Composable () -> Unit) {
    C.dark = dark
    val scheme = if (dark) darkColorScheme(primary = C.coral, background = C.bg, surface = C.surface,
        onSurface = C.ink, onBackground = C.ink, surfaceVariant = C.surface2, secondary = C.amber)
    else lightColorScheme(primary = C.coral, background = C.bg, surface = C.surface,
        onSurface = C.ink, onBackground = C.ink, surfaceVariant = C.surface2, secondary = C.amber)
    MaterialTheme(colorScheme = scheme, content = content)
}
