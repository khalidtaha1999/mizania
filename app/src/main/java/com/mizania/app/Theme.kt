@file:OptIn(ExperimentalTextApi::class)

package com.mizania.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Mizania "Stickers": a playful, colorful look.
 *   Light: lilac-mist page, white cards with an ink outline and a hard drop shadow,
 *   a sunny yellow card for the day's number, an orange add button, and a pastel
 *   tile per budget line made from that line's own color.
 *   Dark: deep indigo page; the same stickers, outlines in soft indigo, shadows in black.
 *
 * Field names are kept from the earlier palette so every screen picks the new look up:
 *   stone = page, pine = selected things and main buttons, dinar = soft highlight,
 *   mint = money in / within budget, pomegranate = over / negative.
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
    /** The sunny card behind the day's number, and the ink that sits on it. */
    val sun: Color,
    val onSun: Color,
    /** The add button. */
    val accent: Color,
    val onAccent: Color,
    /** Sticker outline and the hard shadow under it. */
    val edge: Color,
    val shadow: Color,
    val warning: Color,
    val info: Color,
    /** Kept for older call sites: both ends are the sunny card, there is no gradient any more. */
    val heroStart: Color = sun,
    val heroEnd: Color = sun,
    val sheenTop: Color = Color.Transparent,
    val sheenBottom: Color = Color.Transparent,
    val dark: Boolean,
)

/** The day's card: one solid sunny color. */
val Palette.hero: Brush get() = SolidColor(sun)

/** Cards are flat now; kept so older call sites still compile. */
val Palette.glass: Brush get() = SolidColor(Color.Transparent)

/** A soft tile behind a budget line's icon, made from the line's own color. */
fun Palette.pastel(c: Color): Color = if (dark) lerp(c, raised, 0.70f) else lerp(c, Color.White, 0.74f)

/** The line's color deep enough to read on its pastel tile. */
fun Palette.deep(c: Color): Color = if (dark) lerp(c, Color.White, 0.55f) else lerp(c, Color(0xFF1E1B3A), 0.38f)

val NightPalette = Palette(
    stone = Color(0xFF12101F), pine = Color(0xFFFFD84D), onPine = Color(0xFF1E1B3A),
    dinar = Color(0xFFC4B5FF), mint = Color(0xFF7EE2B0), pomegranate = Color(0xFFFF8A8A),
    muted = Color(0xFFB3AFD0), line = Color(0x26FFFFFF), raised = Color(0xFF1C1A33),
    sun = Color(0xFFFFE27A), onSun = Color(0xFF1E1B3A), accent = Color(0xFFFF8A3D), onAccent = Color(0xFF1E1B3A),
    edge = Color(0xFF3A3660), shadow = Color(0xFF000000), warning = Color(0xFFFFD84D), info = Color(0xFF8EC5FF),
    dark = true,
)

val DayPalette = Palette(
    stone = Color(0xFFF6F4FF), pine = Color(0xFF1E1B3A), onPine = Color(0xFFFFFFFF),
    dinar = Color(0xFF5B3FC4), mint = Color(0xFF0F6B45), pomegranate = Color(0xFFC62828),
    muted = Color(0xFF5E5A7A), line = Color(0x1F1E1B3A), raised = Color(0xFFFFFFFF),
    sun = Color(0xFFFFE27A), onSun = Color(0xFF1E1B3A), accent = Color(0xFFFF8A3D), onAccent = Color(0xFF1E1B3A),
    edge = Color(0xFF1E1B3A), shadow = Color(0xFF1E1B3A), warning = Color(0xFF8A6A00), info = Color(0xFF1E4E8C),
    dark = false,
)

/** Look settings: theme ("" = follow the phone, "dark", "light") and bigger text. */
object Look {
    fun theme(ctx: Context): String = Db.get(ctx).setting("theme", "light")
    fun bigText(ctx: Context): Boolean = Db.get(ctx).setting("big_text", "0") == "1"
    fun isDark(ctx: Context): Boolean = when (theme(ctx)) {
        "light" -> false
        "dark" -> true
        else -> (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

val LocalPalette = staticCompositionLocalOf { DayPalette }

/** Numbers face: Rubik with even-width digits so amounts line up. */
val LocalNumbers = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

object Ui {
    val p: Palette @Composable get() = LocalPalette.current
    val numbers: FontFamily @Composable get() = LocalNumbers.current
}

/** One variable font file as a family with four weights. */
private fun variableFamily(ctx: Context, file: String): FontFamily? = try {
    if (ctx.assets.list("fonts")?.contains(file) != true) null
    else FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { w ->
        Font("fonts/$file", ctx.assets, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    })
} catch (e: Exception) {
    null
}

@Composable
fun MizaniaTheme(dark: Boolean = false, bigText: Boolean = false, content: @Composable () -> Unit) {
    val p = if (dark) NightPalette else DayPalette
    val ctx = LocalContext.current
    val text = remember { variableFamily(ctx, "rubik.ttf") ?: FontFamily.Default }

    val scheme = if (!dark) lightColorScheme(
        primary = p.pine, onPrimary = p.onPine,
        primaryContainer = p.sun, onPrimaryContainer = p.onSun,
        secondary = p.mint, onSecondary = Color.White,
        secondaryContainer = Color(0xFFC9F2DD), onSecondaryContainer = Color(0xFF0B3D27),
        tertiary = p.info, onTertiary = Color.White, tertiaryContainer = Color(0xFFCFE6FF), onTertiaryContainer = Color(0xFF0E2C52),
        error = p.pomegranate, onError = Color.White, errorContainer = Color(0xFFFFDCDC), onErrorContainer = Color(0xFF5C0E0E),
        background = p.stone, onBackground = Color(0xFF1E1B3A), surface = p.stone, onSurface = Color(0xFF1E1B3A),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFEDEAFB),
        surfaceContainerHigh = Color(0xFFE6E2F7), surfaceContainerHighest = Color(0xFFDFDAF2),
        surfaceVariant = Color(0xFFE6E2F7), surfaceTint = Color.Transparent,
        onSurfaceVariant = p.muted, outline = Color(0xFF8E89AC), outlineVariant = Color(0xFFD9D5EC),
        inverseSurface = Color(0xFF1E1B3A), inverseOnSurface = Color(0xFFF6F4FF), inversePrimary = p.sun,
        scrim = Color(0xFF1E1B3A),
    ) else darkColorScheme(
        primary = p.pine, onPrimary = p.onPine,
        primaryContainer = Color(0xFF4A3F12), onPrimaryContainer = Color(0xFFFFE9A0),
        secondary = p.mint, onSecondary = Color(0xFF0B2E1E),
        secondaryContainer = Color(0xFF1C3D2E), onSecondaryContainer = Color(0xFFC9F2DD),
        tertiary = p.info, onTertiary = Color(0xFF0E2C52), tertiaryContainer = Color(0xFF1F3550), onTertiaryContainer = Color(0xFFCFE6FF),
        error = p.pomegranate, onError = Color(0xFF3A0A0A), errorContainer = Color(0xFF4A1A1E), onErrorContainer = Color(0xFFFFDCDC),
        background = p.stone, onBackground = Color(0xFFF3F1FF), surface = p.stone, onSurface = Color(0xFFF3F1FF),
        surfaceContainerLowest = Color(0xFF0D0B18), surfaceContainerLow = p.raised, surfaceContainer = Color(0xFF231F3D),
        surfaceContainerHigh = Color(0xFF2A2748), surfaceContainerHighest = Color(0xFF332F55),
        surfaceVariant = Color(0xFF2A2748), surfaceTint = Color.Transparent,
        onSurfaceVariant = p.muted, outline = Color(0xFF5A5680), outlineVariant = p.edge,
        inverseSurface = Color(0xFFF3F1FF), inverseOnSurface = p.stone, inversePrimary = Color(0xFF1E1B3A),
        scrim = Color.Black,
    )

    val t = Typography()
    fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = text, fontWeight = w ?: fontWeight)
    val nums = "tnum"
    val typography = Typography(
        displayLarge = t.displayLarge.copy(fontFamily = text, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = nums),
        displayMedium = t.displayMedium.copy(fontFamily = text, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = nums),
        displaySmall = t.displaySmall.copy(fontFamily = text, fontWeight = FontWeight.Bold, fontFeatureSettings = nums),
        headlineLarge = t.headlineLarge.f(FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.f(FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.f(FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 17.sp),
        titleSmall = t.titleSmall.copy(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = t.bodyLarge.copy(fontFamily = text, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = text, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = t.bodySmall.copy(fontFamily = text, fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = t.labelLarge.copy(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = t.labelMedium.f(FontWeight.Medium),
        labelSmall = t.labelSmall.f(FontWeight.Medium),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp),
    )

    // Bigger text: scale every sp size on top of the phone's own font size.
    val d = androidx.compose.ui.platform.LocalDensity.current
    val density = if (bigText) androidx.compose.ui.unit.Density(d.density, d.fontScale * 1.18f) else d
    CompositionLocalProvider(
        LocalPalette provides p, LocalNumbers provides text,
        androidx.compose.ui.platform.LocalDensity provides density,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
