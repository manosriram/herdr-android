package dev.herdr.android.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import dev.herdr.android.R

data class Palette(
    val bg: Color,
    val surface: Color,
    val line: Color,
    val term: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val blocked: Color,
    val blockedBg: Color,
    val working: Color,
    val done: Color,
)

val Midnight = Palette(
    bg = Color(0xFF0F1115), surface = Color(0xFF171A20), line = Color(0xFF262A33), term = Color(0xFF0B0D10),
    text = Color(0xFFE8E6E1), muted = Color(0xFF9AA0A6), accent = Color(0xFF6EA8FE), onAccent = Color(0xFF0B1220),
    blocked = Color(0xFFF2A93B), blockedBg = Color(0xFF2A2113), working = Color(0xFF6EA8FE), done = Color(0xFF7FD1A6),
)

val Paper = Palette(
    bg = Color(0xFFF6F4EE), surface = Color(0xFFFFFFFF), line = Color(0xFFDDD8CC), term = Color(0xFFEFECE4),
    text = Color(0xFF1A1A18), muted = Color(0xFF5E5B54), accent = Color(0xFF1F5FD1), onAccent = Color(0xFFFFFFFF),
    blocked = Color(0xFFA65300), blockedBg = Color(0xFFF7E9D7), working = Color(0xFF1F5FD1), done = Color(0xFF2F7A4B),
)

val LocalPalette = staticCompositionLocalOf { Midnight }

val Plex = FontFamily(
    Font(R.font.ibm_plex_mono_regular),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.Bold),
)

private fun Typography.plex(): Typography {
    fun TextStyle.p() = copy(fontFamily = Plex)
    return Typography(
        displayLarge.p(), displayMedium.p(), displaySmall.p(),
        headlineLarge.p(), headlineMedium.p(), headlineSmall.p(),
        titleLarge.p(), titleMedium.p(), titleSmall.p(),
        bodyLarge.p(), bodyMedium.p(), bodySmall.p(),
        labelLarge.p(), labelMedium.p(), labelSmall.p(),
    )
}

@Composable
fun HerdrTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) Midnight else Paper
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface, onSurfaceVariant = p.muted,
        outline = p.line, outlineVariant = p.line,
    )
    val square = RoundedCornerShape(0)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(square, square, square, square, square),
            typography = Typography().plex(),
            content = content,
        )
    }
}
