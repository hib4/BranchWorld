package id.branchworld.game

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.fredoka_bold
import branchworld.shared.generated.resources.fredoka_medium
import branchworld.shared.generated.resources.fredoka_semibold
import branchworld.shared.generated.resources.nunito_bold
import branchworld.shared.generated.resources.nunito_extrabold
import branchworld.shared.generated.resources.nunito_medium
import id.branchworld.game.ecology.Chapter
import org.jetbrains.compose.resources.Font

/**
 * The painted wooden toy set: oiled beech pieces in plant-dye paint, set down on the diorama.
 * Bare wood is available but quiet; paint marks the chosen or primary piece.
 */
internal object Wood {
    val Beech = Color(0xFFE9CDA2)
    val BeechLight = Color(0xFFF6E2C2)
    val PaleInk = Color(0xFFFFFAF2)
    val BeechEdge = Color(0xFFC99F6B)
    val BeechGrain = Color(0x22704A22)
    val Walnut = Color(0xFF2B1D12)
    val WalnutSoft = Color(0xFF5B4330)
    val Weld = Color(0xFFE2B13C)
    val Madder = Color(0xFFB5462F)
    val Woad = Color(0xFF2F5D8A)
    val Leaf = Color(0xFF4E7B3A)
    val Umber = Color(0xFF7A5A3C)
    val Shadow = Color(0x59201208)

    /** The text a paint carries: walnut on weld, beech light on the darker dyes. */
    fun inkOn(paint: Color): Color = when (paint) {
        Beech, BeechLight, Weld -> Walnut
        Leaf, Madder -> PaleInk
        else -> BeechLight
    }

    /** The darker band under a piece's face: the block's own thickness. */
    fun edgeOf(paint: Color): Color = when (paint) {
        Beech, BeechLight -> BeechEdge
        Weld -> Color(0xFFB08423)
        Madder -> Color(0xFF7E2C1C)
        Woad -> Color(0xFF1C3C5C)
        Leaf -> Color(0xFF30521F)
        Umber -> Color(0xFF4F3824)
        else -> Color(paint.red * 0.62f, paint.green * 0.62f, paint.blue * 0.62f, paint.alpha)
    }
}

/** One paint per chapter, used wherever that chapter appears. */
internal fun chapterPaint(chapter: Chapter?, sandbox: Boolean = false): Color = when {
    chapter == Chapter.FadingGrove -> Wood.Weld
    chapter == Chapter.LastCrossing -> Wood.Woad
    chapter == Chapter.EmberSeason -> Wood.Madder
    sandbox -> Wood.Leaf
    else -> Wood.Umber
}

/** Presentation preferences every component can read. */
internal data class UiPreferences(val sounds: Boolean = true, val reducedMotion: Boolean = false, val play: (WorldSound) -> Unit = {}) {
    fun click(sound: WorldSound = WorldSound.UiSelect) {
        if (sounds) play(sound)
    }
}

internal val LocalUi = staticCompositionLocalOf { UiPreferences() }

/**
 * How far wooden pieces sit from the screen edge and how much room boards keep inside. Phones (under 600 dp wide)
 * keep pieces 12 dp from the edge so the island and the boards get the width; larger windows keep each surface's
 * own gutter.
 */
internal data class Spacing(val compact: Boolean) {
    fun edge(regular: Dp): Dp = if (compact) 12.dp else regular
    val board: Dp get() = if (compact) 12.dp else 16.dp
}

internal val LocalSpacing = staticCompositionLocalOf { Spacing(compact = false) }

@Composable
internal fun GameTheme(textScale: Int, preferences: UiPreferences, content: @Composable () -> Unit) {
    val stamp = FontFamily(
        Font(Res.font.fredoka_medium, FontWeight.Medium),
        Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
        Font(Res.font.fredoka_bold, FontWeight.Bold),
    )
    val body = FontFamily(
        Font(Res.font.nunito_medium, FontWeight.Normal),
        Font(Res.font.nunito_medium, FontWeight.Medium),
        Font(Res.font.nunito_bold, FontWeight.SemiBold),
        Font(Res.font.nunito_bold, FontWeight.Bold),
        Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    )
    fun stamped(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontFamily = stamp, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.em)
    fun reading(size: Int, line: Int, weight: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
    val typography = Typography(
        displayLarge = stamped(52, 54, FontWeight.Bold),
        displayMedium = stamped(42, 46, FontWeight.Bold),
        displaySmall = stamped(34, 38, FontWeight.Bold),
        headlineLarge = stamped(30, 34),
        headlineMedium = stamped(26, 30),
        headlineSmall = stamped(23, 28),
        titleLarge = stamped(20, 24),
        titleMedium = stamped(17, 22),
        titleSmall = stamped(15, 20),
        bodyLarge = reading(17, 24),
        bodyMedium = reading(15, 21),
        bodySmall = reading(14, 19),
        labelLarge = stamped(17, 20),
        labelMedium = stamped(15, 18, FontWeight.Medium),
        labelSmall = stamped(13, 16, FontWeight.Medium),
    )
    val colors = lightColorScheme(
        primary = Wood.Woad, onPrimary = Wood.BeechLight,
        primaryContainer = Wood.Weld, onPrimaryContainer = Wood.Walnut,
        secondary = Wood.Madder, onSecondary = Wood.PaleInk,
        secondaryContainer = Color(0xFFF1DCB5), onSecondaryContainer = Wood.Walnut,
        tertiary = Wood.Leaf, onTertiary = Wood.PaleInk,
        background = Wood.Beech, onBackground = Wood.Walnut,
        surface = Wood.Beech, onSurface = Wood.Walnut,
        surfaceVariant = Color(0xFFE0BF8D), onSurfaceVariant = Wood.WalnutSoft,
        surfaceContainerLowest = Wood.BeechLight, surfaceContainerLow = Color(0xFFF0D9B3),
        surfaceContainer = Wood.Beech, surfaceContainerHigh = Color(0xFFE3C493), surfaceContainerHighest = Color(0xFFDDB985),
        outline = Color(0xFF9C7448), outlineVariant = Color(0xFFCBA676),
        error = Wood.Madder, onError = Wood.PaleInk,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp),
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * textScale / 100f),
        LocalUi provides preferences,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
    }
}
