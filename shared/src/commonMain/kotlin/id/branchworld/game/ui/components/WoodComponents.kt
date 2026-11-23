package id.branchworld.game.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.close_panel
import branchworld.shared.generated.resources.focus_island
import id.branchworld.game.platform.sound.WorldSound
import id.branchworld.game.ui.theme.GameIcons
import id.branchworld.game.ui.theme.LocalSpacing
import id.branchworld.game.ui.theme.LocalUi
import id.branchworld.game.ui.theme.Wood
import org.jetbrains.compose.resources.stringResource

private val Thickness = 6.dp

/**
 * Draws one wooden piece: a contact shadow on the table, the block's thickness band, then its painted or bare face
 * with a soft top highlight and, on bare wood, a few lines of grain. [sink] lowers the face into its thickness.
 */
private fun DrawScope.woodPiece(face: Color, radius: Float, thickness: Float, sink: Float, grain: Boolean) {
    val r = CornerRadius(radius)
    val bodyHeight = (size.height - thickness).coerceAtLeast(1f)
    val faceSize = Size(size.width, bodyHeight)
    // Two translucent skirts feather the contact edge without the cost of a blur layer.
    for (spread in listOf(3.dp, 1.5.dp)) {
        val px = spread.toPx()
        drawRoundRect(Wood.Shadow.copy(alpha = if (spread == 3.dp) 0.10f else 0.18f),
            Offset(-px / 2f, thickness + px), Size(size.width + px, bodyHeight), CornerRadius(radius + px))
    }
    drawRoundRect(Wood.edgeOf(face), Offset(0f, thickness), faceSize, r)
    drawRoundRect(face, Offset(0f, sink), faceSize, r)
    drawRoundRect(Color.White.copy(alpha = 0.21f), Offset(radius * 0.55f, sink + 1.dp.toPx()),
        Size((size.width - radius * 1.1f).coerceAtLeast(1f), 1.dp.toPx()), CornerRadius(1.dp.toPx()))
    // Reading boards stay quiet: grain only on pieces short enough to carry a label, not paragraphs.
    if (grain && bodyHeight < 150.dp.toPx()) {
        // Faint, uneven lengths flow across the face, not a ruled grid through the lettering.
        for (index in 0..7) {
            val fraction = GrainStrands[index]
            val y = sink + bodyHeight * fraction
            val wave = 1.5.dp.toPx() * (index % 3 + 1)
            val from = size.width * (0.025f + (index % 4) * 0.047f)
            val to = size.width * (0.97f - (index % 3) * 0.06f)
            val path = Path().apply {
                moveTo(from, y)
                cubicTo(size.width * 0.41f, y - wave, size.width * 0.62f, y + wave * 0.45f, to, y + wave)
            }
            drawPath(path, Wood.BeechGrain.copy(alpha = if (index % 3 == 0) 0.11f else 0.055f), style = Stroke(0.65.dp.toPx()))
        }
    }
}

private val GrainStrands = floatArrayOf(0.12f, 0.20f, 0.25f, 0.46f, 0.50f, 0.72f, 0.84f, 0.89f)

/** A piece is set down when it first appears: a short drop that settles from an already-visible start. */
@Composable
private fun Modifier.setDown(): Modifier {
    val ui = LocalUi.current
    val drop = remember { Animatable(if (ui.reducedMotion) 0f else -10f) }
    LaunchedEffect(ui.reducedMotion) {
        if (ui.reducedMotion) drop.snapTo(0f)
        else drop.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    val density = LocalDensity.current.density
    return graphicsLayer { translationY = drop.value * density }
}

/** A beech board laid on the island: the surface for cards, sheets and menus. */
@Composable
internal fun Board(
    modifier: Modifier = Modifier,
    padding: Dp = LocalSpacing.current.board,
    gap: Dp = 8.dp,
    face: Color = Wood.Beech,
    depth: Dp = Thickness,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val radius = with(density) { 18.dp.toPx() }
    val thickness = with(density) { depth.toPx() }
    Column(
        modifier.setDown().drawBehind { woodPiece(face, radius, thickness, 0f, grain = face == Wood.Beech) }
            .padding(start = padding, end = padding, top = padding, bottom = padding + depth),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        CompositionLocalProvider(LocalContentColor provides Wood.inkOn(face)) { content() }
    }
}

/** A titled board for in-world panels, with one round peg to put it away. */
@Composable
internal fun BoardSheet(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Board(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onClose != null) PegButton(GameIcons.Close, closeLabel(), onClose)
        }
        content()
    }
}

internal enum class BlockSize(val height: Dp, val radius: Dp, val horizontal: Dp) {
    Large(78.dp, 18.dp, 20.dp), Medium(56.dp, 14.dp, 18.dp), Small(52.dp, 14.dp, 12.dp), Compact(48.dp, 10.dp, 8.dp),
}

/**
 * A wooden block: painted when [paint] is set (the primary or chosen piece), bare beech otherwise. Pressing sinks the
 * face into its thickness; keyboard focus draws a walnut outline around the face.
 */
@Composable
internal fun Block(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    paint: Color? = null,
    detail: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    size: BlockSize = BlockSize.Medium,
    iconOnly: Boolean = false,
    sound: WorldSound = WorldSound.UiSelect,
    selected: Boolean? = null,
    checked: Boolean? = null,
) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val face = when {
        !enabled -> Color(0xFFD9C3A0)
        paint != null -> if (hovered) lerp(paint, if (Wood.inkOn(paint) == Wood.Walnut) Color.White else Color.Black, 0.07f) else paint
        hovered -> Wood.BeechLight
        else -> Wood.Beech
    }
    val ink = if (!enabled) Wood.WalnutSoft else Wood.inkOn(paint ?: Wood.Beech)
    val targetSink = if (pressed && enabled) Thickness - 1.dp else 0.dp
    val animatedSink by animateDpAsState(targetSink, spring(stiffness = Spring.StiffnessHigh))
    val sink = if (ui.reducedMotion) targetSink else animatedSink
    val activate = { ui.click(sound); onClick() }
    val input = when {
        selected != null -> Modifier.selectable(selected, interaction, indication = null, enabled = enabled,
            role = Role.RadioButton, onClick = activate)
        checked != null -> Modifier.toggleable(checked, interaction, indication = null, enabled = enabled,
            role = Role.Checkbox, onValueChange = { activate() })
        else -> Modifier.clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = activate)
    }
    val density = LocalDensity.current
    val radius = with(density) { size.radius.toPx() }
    val thickness = with(density) { Thickness.toPx() }
    Row(
        modifier.setDown().heightIn(min = size.height)
            .drawBehind {
                woodPiece(face, radius, thickness, sink.toPx(), grain = paint == null)
                if (focused) drawRoundRect(Wood.Walnut, Offset(-3f, -3f + sink.toPx()),
                    Size(this.size.width + 6f, this.size.height - thickness + 6f), CornerRadius(radius + 3f), style = Stroke(2.5f))
            }
            .hoverable(interaction, enabled)
            .then(input)
            .offset(y = sink)
            .padding(start = size.horizontal, end = size.horizontal, top = 6.dp, bottom = 6.dp + Thickness),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(icon, if (iconOnly) label else null, Modifier.size(if (size == BlockSize.Large) 26.dp else 22.dp), tint = ink)
        }
        if (!iconOnly) Column(Modifier.weight(1f, fill = false)) {
            Text(label, color = ink, style = when (size) {
                BlockSize.Large -> MaterialTheme.typography.headlineSmall
                BlockSize.Medium -> MaterialTheme.typography.titleLarge
                BlockSize.Small -> MaterialTheme.typography.labelLarge
                BlockSize.Compact -> MaterialTheme.typography.titleSmall
            }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(it, color = ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A round wooden peg with one icon: close, back and other small actions. Always 48 dp to touch. */
@Composable
internal fun PegButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val targetSink = if (pressed) 3.dp else 0.dp
    val animatedSink by animateDpAsState(targetSink)
    val sink = if (ui.reducedMotion) targetSink else animatedSink
    Box(
        Modifier.size(48.dp).hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                ui.click(WorldSound.UiBack)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(40.dp)) {
            woodPiece(if (hovered) Wood.BeechLight else Wood.Beech, size.minDimension / 2, 4.dp.toPx(), sink.toPx(), grain = false)
            if (focused) drawCircle(Wood.Walnut, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        Icon(icon, label, Modifier.offset(y = sink - 2.dp).size(18.dp), tint = if (enabled) Wood.Walnut else Wood.WalnutSoft.copy(alpha = 0.4f))
    }
}

/** The game-styled camera reset used by the platform render adapter. */
@Composable
fun WorldCameraReset(onClick: () -> Unit) {
    PegButton(GameIcons.Compass, stringResource(Res.string.focus_island), onClick)
}

/** A small stamped peg of quiet information, such as the day. */
@Composable
internal fun Peg(text: String, modifier: Modifier = Modifier, paint: Color? = null) {
    val ground = paint ?: Wood.BeechLight
    Text(text, modifier.clip(RoundedCornerShape(12.dp)).background(ground).padding(horizontal = 10.dp, vertical = 4.dp),
        color = Wood.inkOn(ground), style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

/** The chapter's inlaid numeral: flip the token from its bare back to its painted face. */
@Composable
internal fun ChapterStamp(numeral: String, paint: Color, unlocked: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalUi.current.reducedMotion
    val turn = remember { Animatable(if (reduced && unlocked) 1f else 0f) }
    LaunchedEffect(unlocked, reduced) {
        if (reduced || !unlocked) turn.snapTo(if (unlocked) 1f else 0f)
        else turn.animateTo(1f, tween(durationMillis = 340))
    }
    val phase = if (reduced || !unlocked) if (unlocked) 1f else 0f else turn.value
    Peg(numeral, modifier.graphicsLayer {
        rotationX = if (phase < 0.5f) phase * 160f else (phase - 1f) * 160f
    }, if (phase >= 0.5f) paint else Wood.BeechLight)
}

/** A setting: its name, one line of what it does, and a wooden slider in a groove. */
@Composable
internal fun WoodSwitchRow(label: String, detail: String?, on: Boolean, onToggle: (Boolean) -> Unit) {
    val ui = LocalUi.current
    val targetSlide = if (on) 1f else 0f
    val animatedSlide by animateFloatAsState(targetSlide)
    val slide = if (ui.reducedMotion) targetSlide else animatedSlide
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .toggleable(value = on, role = Role.Switch) {
                ui.click()
                onToggle(it)
            }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Wood.WalnutSoft) }
        }
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.width(54.dp).height(30.dp)) {
            val groove = size.height / 2
            drawRoundRect(Color(0xFFB98F5C), cornerRadius = CornerRadius(groove))
            drawRoundRect(Color(0x33000000), Offset.Zero, Size(size.width, 4f), CornerRadius(groove))
            val knob = size.height - 6f
            translate(3f + (size.width - knob - 6f) * slide, 2f) {
                val piece = Size(knob, knob)
                drawRoundRect(Wood.Shadow, Offset(1f, 3f), piece, CornerRadius(knob / 2))
                drawRoundRect(lerp(Wood.BeechLight, Wood.Woad, slide), Offset.Zero, piece, CornerRadius(knob / 2))
            }
        }
    }
}

/** Mutually exclusive choices as a row of blocks: the chosen one painted, the others bare wood. */
@Composable
internal fun BlockChoice(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, (label, selected) ->
            Block(label, { onSelect(index) }, Modifier.weight(1f), paint = if (selected) Wood.Woad else Wood.BeechLight,
                size = BlockSize.Small, selected = selected)
        }
    }
}

/**
 * Scrolling contents of a beech board. While more lies below, a small chevron sits under the content (never over
 * it), so a piece cut by the board's edge never looks like the end of the list.
 */
@Composable
internal fun ScrollColumn(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(modifier) {
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        if (scroll.canScrollForward) {
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                Icon(GameIcons.Back, null, Modifier.rotate(-90f).size(18.dp), tint = Wood.WalnutSoft)
            }
        }
    }
}

@Composable
internal fun Scrim(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0x8C1A120A)))
}

@Composable
internal fun closeLabel(): String = stringResource(Res.string.close_panel)
