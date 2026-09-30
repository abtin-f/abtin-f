package com.abtinf.glassmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.R

/** Vivid pink/red used for selection and primary actions. */
val AmAccent = Color(0xFFE8384A)

@Immutable
data class AmColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val glass: Color,
    val glassHighlight: Color,
    val isDark: Boolean,
)

val LightAm = AmColors(
    background = Color.White,
    surface = Color(0xFFF2F2F7),
    text = Color(0xFF111114),
    secondary = Color(0xFF8E8E93),
    tertiary = Color(0xFFC7C7CC),
    separator = Color(0x1F3C3C43),
    glass = Color(0xFFFAFAFA).copy(alpha = 0.4f),
    glassHighlight = Color.White,
    isDark = false,
)

val DarkAm = AmColors(
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    text = Color.White,
    secondary = Color(0xFF8E8E93),
    tertiary = Color(0xFF48484A),
    separator = Color(0x26FFFFFF),
    glass = Color(0xFF121212).copy(alpha = 0.45f),
    glassHighlight = Color.White,
    isDark = true,
)

val LocalAm = staticCompositionLocalOf { LightAm }

/** SF Pro cannot be bundled; Android's system sans (Roboto / Google Sans) is the closest match. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: FontWeight) = Font(
    R.font.inter, w,
    variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
)

/** Inter (SIL OFL) variable font, the closest free match to the reference typography. */
val Sans = FontFamily(inter(FontWeight.Normal), inter(FontWeight.Medium), inter(FontWeight.SemiBold), inter(FontWeight.Bold))

object AmType {
    val LargeTitle = TextStyle(fontFamily = Sans, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val Section = TextStyle(fontFamily = Sans, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val Title = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
    val Tiny = TextStyle(fontFamily = Sans, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
    val Lyric = TextStyle(fontFamily = Sans, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
}

@Composable
fun GlassMusicTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val am = if (dark) DarkAm else LightAm
    val scheme = if (dark) {
        darkColorScheme(primary = AmAccent, background = am.background, surface = am.surface, onSurface = am.text)
    } else {
        lightColorScheme(primary = AmAccent, background = am.background, surface = am.surface, onSurface = am.text)
    }
    CompositionLocalProvider(LocalAm provides am) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
