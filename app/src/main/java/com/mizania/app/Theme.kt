@file:OptIn(ExperimentalTextApi::class)

package com.mizania.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Mizania "Night" palette — deep ink, violet-to-blue glow, neon money colors.
 *   stone  #0B0B14  the night background
 *   pine   #7C5CFF  violet: the accent for selected things and buttons
 *   heroStart→heroEnd  violet → blue gradient for the hero cards
 *   dinar  #B9A6FF  soft lavender for highlights and tints
 *   mint   #34E0A1  money in / under the limit
 *   pomegranate #FF5C7A  over the limit
 */
@Immutable
data class Palette(
    val stone: Color,
    val pine: Color,
    val onPine: Color,
    val dinar: Color,
    val mint: Color,
    val pomegranate: Color,
    val muted: Color,
    val line: Color,
    val raised: Color,
    val heroStart: Color,
    val heroEnd: Color,
    /** Top and bottom of the faint sheen on cards. */
    val sheenTop: Color = Color(0x14FFFFFF),
    val sheenBottom: Color = Color(0x08FFFFFF),
    val dark: Boolean = true,
)

/** The violet → blue gradient behind hero cards. */
val Palette.hero: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))

/** Glassy card fill: a faint white sheen fading downwards. */
val Palette.glass: Brush get() = Brush.verticalGradient(listOf(sheenTop, sheenBottom))

val NightPalette = Palette(
    stone = Color(0xFF0B0B14), pine = Color(0xFF7C5CFF), onPine = Color(0xFFFFFFFF),
    dinar = Color(0xFFB9A6FF), mint = Color(0xFF34E0A1), pomegranate = Color(0xFFFF5C7A),
    muted = Color(0xFF9C9AB8), line = Color(0x1FFFFFFF), raised = Color(0xFF15141F),
    heroStart = Color(0xFF7C5CFF), heroEnd = Color(0xFF3B82F6),
)

/** "Day": the same violet accents on a soft white page. */
val DayPalette = Palette(
    stone = Color(0xFFF6F5FB), pine = Color(0xFF6A4DF5), onPine = Color(0xFFFFFFFF),
    dinar = Color(0xFF8B74FF), mint = Color(0xFF0E9F6E), pomegranate = Color(0xFFE0445F),
    muted = Color(0xFF6B6880), line = Color(0x1F000000), raised = Color(0xFFFFFFFF),
    heroStart = Color(0xFF7C5CFF), heroEnd = Color(0xFF3B82F6),
    sheenTop = Color(0x00FFFFFF), sheenBottom = Color(0x00FFFFFF), dark = false,
)

/** Look settings: theme ("" = follow the phone, "dark", "light") and bigger text. */
object Look {
    fun theme(ctx: Context): String = Db.get(ctx).setting("theme", "dark")
    fun bigText(ctx: Context): Boolean = Db.get(ctx).setting("big_text", "0") == "1"
    fun isDark(ctx: Context): Boolean = when (theme(ctx)) {
        "light" -> false
        "dark" -> true
        else -> (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

val LocalPalette = staticCompositionLocalOf { NightPalette }

/** Numbers face (Readex Pro) — wide, open digits for amounts. */
val LocalNumbers = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

object Ui {
    val p: Palette @Composable get() = LocalPalette.current
    val numbers: FontFamily @Composable get() = LocalNumbers.current
}

private fun assetFamily(ctx: Context, vararg files: Pair<String, FontWeight>): FontFamily? = try {
    val have = ctx.assets.list("fonts")?.toSet() ?: emptySet()
    val fonts = files.filter { it.first in have }.map { (f, w) -> Font("fonts/$f", ctx.assets, w) }
    if (fonts.isEmpty()) null else FontFamily(fonts)
} catch (e: Exception) {
    null
}

@Composable
fun MizaniaTheme(dark: Boolean = true, bigText: Boolean = false, content: @Composable () -> Unit) {
    val p = if (dark) NightPalette else DayPalette
    val ctx = LocalContext.current

    val text = remember {
        assetFamily(
            ctx,
            "plex-regular.ttf" to FontWeight.Normal,
            "plex-medium.ttf" to FontWeight.Medium,
            "plex-semibold.ttf" to FontWeight.SemiBold,
            "plex-bold.ttf" to FontWeight.Bold,
        ) ?: FontFamily.Default
    }
    val numbers = remember {
        assetFamily(
            ctx,
            "readex.ttf" to FontWeight.Normal,
            "readex.ttf" to FontWeight.Medium,
            "readex.ttf" to FontWeight.SemiBold,
            "readex.ttf" to FontWeight.Bold,
        ) ?: text
    }

    val scheme = if (!dark) lightColorScheme(
        primary = p.pine, onPrimary = Color.White,
        primaryContainer = Color(0xFFE6E0FF), onPrimaryContainer = Color(0xFF22125E),
        secondary = p.mint, onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3F5E6), onSecondaryContainer = Color(0xFF00382A),
        tertiary = Color(0xFF2563EB), tertiaryContainer = Color(0xFFDBE7FF), onTertiaryContainer = Color(0xFF0B2A66),
        error = p.pomegranate, onError = Color.White, errorContainer = Color(0xFFFFDDE3), onErrorContainer = Color(0xFF5C0E1E),
        background = p.stone, onBackground = Color(0xFF1A1830), surface = p.stone, onSurface = Color(0xFF1A1830),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFEFEDF7),
        surfaceContainerHigh = Color(0xFFE9E7F2), surfaceContainerHighest = Color(0xFFE2E0EC),
        surfaceVariant = Color(0xFFE7E5F0), surfaceTint = p.pine,
        onSurfaceVariant = p.muted, outline = Color(0xFFB4B1C8), outlineVariant = Color(0xFFDAD8E6),
        inverseSurface = Color(0xFF2A2840), inverseOnSurface = Color(0xFFF1F0FA), inversePrimary = Color(0xFFB9A6FF),
        scrim = Color.Black,
    ) else darkColorScheme(
        primary = p.pine, onPrimary = Color.White,
        primaryContainer = Color(0xFF2A2350), onPrimaryContainer = Color(0xFFE4DCFF),
        secondary = p.mint, onSecondary = Color(0xFF00281A),
        secondaryContainer = Color(0xFF123A2C), onSecondaryContainer = Color(0xFFC6F7E3),
        tertiary = Color(0xFF60A5FA), tertiaryContainer = Color(0xFF1B2D4E), onTertiaryContainer = Color(0xFFD6E6FF),
        error = p.pomegranate, onError = Color.White, errorContainer = Color(0xFF4A1524), onErrorContainer = Color(0xFFFFD9DF),
        background = p.stone, onBackground = Color(0xFFF1F0FA), surface = p.stone, onSurface = Color(0xFFF1F0FA),
        surfaceContainerLowest = Color(0xFF08080F), surfaceContainerLow = p.raised, surfaceContainer = Color(0xFF1A1928),
        surfaceContainerHigh = Color(0xFF201F30), surfaceContainerHighest = Color(0xFF282739),
        surfaceVariant = Color(0xFF232234), surfaceTint = p.pine,
        onSurfaceVariant = p.muted, outline = Color(0xFF4A4866), outlineVariant = Color(0xFF2A2940),
        inverseSurface = Color(0xFFF1F0FA), inverseOnSurface = p.stone, inversePrimary = Color(0xFF5B3FD9),
        scrim = Color.Black,
    )

    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = text)
    val typography = Typography(
        displayLarge = t.displayLarge.copy(fontFamily = numbers),
        displayMedium = t.displayMedium.copy(fontFamily = numbers),
        displaySmall = t.displaySmall.copy(fontFamily = numbers),
        headlineLarge = t.headlineLarge.f(),
        headlineMedium = t.headlineMedium.f(),
        headlineSmall = t.headlineSmall.copy(fontFamily = text, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = t.titleSmall.copy(fontFamily = text, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = text, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = text, fontSize = 15.sp, lineHeight = 23.sp),
        bodySmall = t.bodySmall.copy(fontFamily = text, fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = t.labelLarge.copy(fontFamily = text, fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.f(),
        labelSmall = t.labelSmall.f(),
    )

    // Bigger text: scale every sp size on top of the phone's own font size.
    val d = androidx.compose.ui.platform.LocalDensity.current
    val density = if (bigText) androidx.compose.ui.unit.Density(d.density, d.fontScale * 1.18f) else d
    CompositionLocalProvider(
        LocalPalette provides p, LocalNumbers provides numbers,
        androidx.compose.ui.platform.LocalDensity provides density,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
