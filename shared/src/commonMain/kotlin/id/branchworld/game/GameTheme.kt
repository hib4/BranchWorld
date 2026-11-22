package id.branchworld.game

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.fraunces_semibold
import branchworld.shared.generated.resources.nunito_bold
import branchworld.shared.generated.resources.nunito_extrabold
import branchworld.shared.generated.resources.nunito_medium
import org.jetbrains.compose.resources.Font

/** The Keeper's Atlas palette: night-forest ink, moss, vellum and a warm ember gold. */
internal object Atlas {
    val Ink = Color(0xFF0E241F)
    val InkGlass = Color(0xE6102A24)
    val Moss = Color(0xFF2B5A4B)
    val Fern = Color(0xFF3F7A63)
    val Vellum = Color(0xFFF7F0DE)
    val VellumDeep = Color(0xFFEDE2C6)
    val Gold = Color(0xFFE8B45C)
    val Ember = Color(0xFFC7832C)
    val Rust = Color(0xFF9C4A2A)
    val Mist = Color(0xFFD6E2D8)
    val Faded = Color(0xFF9FB3A9)
    val InkText = Color(0xFF15302A)
    val InkSoft = Color(0xFF4A5F57)
    val Hairline = Color(0x66E8B45C)
}

/** Presentation preferences every component can read. */
internal data class UiPreferences(val sounds: Boolean = true, val reducedMotion: Boolean = false, val play: (WorldSound) -> Unit = {}) {
    fun click(sound: WorldSound = WorldSound.UiSelect) {
        if (sounds) play(sound)
    }
}

internal val LocalUi = staticCompositionLocalOf { UiPreferences() }

@Composable
private fun storyFamilies(): Pair<FontFamily, FontFamily> {
    val display = FontFamily(Font(Res.font.fraunces_semibold, FontWeight.SemiBold))
    val body = FontFamily(
        Font(Res.font.nunito_medium, FontWeight.Normal),
        Font(Res.font.nunito_medium, FontWeight.Medium),
        Font(Res.font.nunito_bold, FontWeight.SemiBold),
        Font(Res.font.nunito_bold, FontWeight.Bold),
        Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    )
    return display to body
}

@Composable
internal fun GameTheme(textScale: Int, preferences: UiPreferences, content: @Composable () -> Unit) {
    val (display, body) = storyFamilies()
    val base = Typography()
    fun TextStyle.body(weight: FontWeight = FontWeight.Medium) = copy(fontFamily = body, fontWeight = weight)
    fun TextStyle.story(spacing: Double = -0.01) = copy(fontFamily = display, fontWeight = FontWeight.SemiBold, letterSpacing = spacing.em)
    val typography = Typography(
        displayLarge = base.displayLarge.story(-0.03).copy(fontSize = 60.sp, lineHeight = 62.sp),
        displayMedium = base.displayMedium.story(-0.025),
        displaySmall = base.displaySmall.story(-0.02),
        headlineLarge = base.headlineLarge.story(),
        headlineMedium = base.headlineMedium.story(),
        headlineSmall = base.headlineSmall.story(),
        titleLarge = base.titleLarge.story(),
        titleMedium = base.titleMedium.body(FontWeight.Bold),
        titleSmall = base.titleSmall.body(FontWeight.Bold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body().copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.body(FontWeight.ExtraBold).copy(letterSpacing = 0.02.em),
        labelMedium = base.labelMedium.body(FontWeight.Bold).copy(letterSpacing = 0.06.em),
        labelSmall = base.labelSmall.body(FontWeight.ExtraBold).copy(letterSpacing = 0.12.em),
    )
    val colors = lightColorScheme(
        primary = Atlas.Moss, onPrimary = Atlas.Vellum,
        primaryContainer = Atlas.Gold, onPrimaryContainer = Atlas.Ink,
        secondary = Atlas.Ember, onSecondary = Atlas.Ink,
        secondaryContainer = Color(0xFFDDE6CF), onSecondaryContainer = Atlas.InkText,
        tertiary = Atlas.Fern,
        background = Atlas.Vellum, onBackground = Atlas.InkText,
        surface = Atlas.Vellum, onSurface = Atlas.InkText,
        surfaceVariant = Atlas.VellumDeep, onSurfaceVariant = Atlas.InkSoft,
        surfaceContainer = Atlas.VellumDeep, surfaceContainerHigh = Color(0xFFEFE5CB), surfaceContainerHighest = Color(0xFFE6D9B8),
        outline = Color(0xFF8A7F62), outlineVariant = Color(0xFFCDBF99),
        error = Atlas.Rust,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(26.dp),
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * textScale / 100f),
        LocalUi provides preferences,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
    }
}
