package id.branchworld.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import branchworld.shared.generated.resources.Res
import branchworld.shared.generated.resources.close_panel
import id.branchworld.game.terrain.Biome
import org.jetbrains.compose.resources.stringResource
import id.branchworld.game.terrain.IslandGenerator
import id.branchworld.game.terrain.TerrainKey
import id.branchworld.game.terrain.TerrainMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dark glass over the world, edged with a gold hairline and small corner diamonds. */
@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    corners: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(Atlas.InkGlass).border(1.dp, Atlas.Hairline, shape)
            .then(if (corners) Modifier.drawBehind { cornerDiamonds(Atlas.Gold.copy(alpha = 0.7f), 6.dp.toPx()) } else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides Atlas.Vellum) { content() }
    }
}

/** A vellum reading sheet with an ornamented title row. Used for every in-world panel. */
@Composable
internal fun VellumSheet(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(modifier, shape = shape, color = Atlas.Vellum, contentColor = Atlas.InkText, shadowElevation = 10.dp) {
        Column(
            Modifier.border(1.dp, Atlas.VellumDeep, shape).drawBehind {
                drawRoundRect(Atlas.Ember.copy(alpha = 0.35f), Offset(5.dp.toPx(), 5.dp.toPx()),
                    Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()), CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
                cornerDiamonds(Atlas.Ember.copy(alpha = 0.8f), 5.dp.toPx(), inset = 5.dp.toPx())
            }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    kicker?.let { SectionLabel(it, color = Atlas.Ember) }
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (onClose != null) IconAction(GameIcons.Close, closeLabel(), onClose, tint = Atlas.InkSoft)
            }
            OrnamentRule(Atlas.Ember.copy(alpha = 0.55f))
            content()
        }
    }
}

/** A thin rule with a diamond at its centre. */
@Composable
internal fun OrnamentRule(color: Color = Atlas.Hairline, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(9.dp)) {
        val y = size.height / 2
        val gap = 9.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width / 2 - gap, y), 1.dp.toPx())
        drawLine(color, Offset(size.width / 2 + gap, y), Offset(size.width, y), 1.dp.toPx())
        diamond(Offset(size.width / 2, y), 4.dp.toPx(), color)
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Atlas.Gold) {
    Text(text.uppercase(), modifier, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
}

/**
 * A title-menu entry: large story type, an optional line beneath, and a gold diamond that slides in when the entry is
 * hovered, focused or pressed. Keyboard and touch reach it the same way.
 */
@Composable
internal fun MenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    large: Boolean = true,
) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val lit = enabled && (hovered || focused || pressed)
    val reveal by animateFloatAsState(if (lit) 1f else 0f)
    val color by animateColorAsState(when {
        !enabled -> Atlas.Faded.copy(alpha = 0.6f)
        lit -> Atlas.Gold
        else -> Atlas.Vellum
    })
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .background(Atlas.Gold.copy(alpha = 0.10f * reveal))
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                ui.click(WorldSound.UiConfirm)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(16.dp).height(16.dp).alpha(0.25f + 0.75f * reveal)) {
            diamond(Offset(size.width / 2 + (reveal - 1f) * 6.dp.toPx(), size.height / 2), 5.dp.toPx(), Atlas.Gold)
        }
        Spacer(Modifier.width(10.dp))
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = color, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(it, color = if (enabled) Atlas.Mist else Atlas.Faded.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A labelled icon button for the action dock. [primary] marks the one action the moment is about. */
@Composable
internal fun DockButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lit = enabled && (hovered || focused || selected)
    val iconOnly = compact && !primary
    val container = when {
        primary -> if (lit) Color(0xFFF3C677) else Atlas.Gold
        lit -> Atlas.Vellum.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> Atlas.Faded.copy(alpha = 0.5f)
        primary -> Atlas.Ink
        lit -> Atlas.Gold
        else -> Atlas.Vellum
    }
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(container)
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                ui.click()
                onClick()
            }
            .padding(horizontal = if (primary) 16.dp else if (iconOnly) 12.dp else 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, if (iconOnly) label else null, Modifier.size(20.dp), tint = content)
        if (!iconOnly) Text(label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A square icon-only action with an accessible label. */
@Composable
internal fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Atlas.Vellum, enabled: Boolean = true) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (hovered && enabled) tint.copy(alpha = 0.12f) else Color.Transparent)
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label) {
                ui.click(WorldSound.UiBack)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.4f)) }
}

/** A gold primary or quiet secondary button for sheets and dialogs. */
@Composable
internal fun StoryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onDark: Boolean = false,
) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lit = hovered || focused
    val shape = RoundedCornerShape(24.dp)
    val quiet = if (onDark) Atlas.Vellum else Atlas.Moss
    val container = when {
        !enabled -> if (primary) Atlas.Faded.copy(alpha = 0.35f) else Color.Transparent
        primary -> if (lit) Color(0xFFF3C677) else Atlas.Gold
        lit -> quiet.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> quiet.copy(alpha = 0.45f)
        primary -> Atlas.Ink
        else -> quiet
    }
    Row(
        modifier.heightIn(min = 44.dp).clip(shape).background(container)
            .then(if (primary) Modifier else Modifier.border(1.dp, quiet.copy(alpha = if (enabled) 0.45f else 0.2f), shape))
            .hoverable(interaction, enabled)
            .focusable(enabled, interaction)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                ui.click(if (primary) WorldSound.UiConfirm else WorldSound.UiSelect)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = content) }
        Text(label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A small capsule of quiet information, such as the day or a status. */
@Composable
internal fun InfoChip(text: String, modifier: Modifier = Modifier, color: Color = Atlas.Mist, background: Color = Atlas.Vellum.copy(alpha = 0.10f)) {
    Text(text, modifier.clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

/** A selectable row for settings: label, explanation and an on/off lantern. */
@Composable
internal fun SettingRow(label: String, detail: String?, on: Boolean, onToggle: (Boolean) -> Unit) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Switch) {
                ui.click()
                onToggle(!on)
            }.padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Atlas.InkSoft) }
        }
        Spacer(Modifier.width(12.dp))
        Lantern(on)
    }
}

@Composable
private fun Lantern(on: Boolean) {
    val knob by animateFloatAsState(if (on) 1f else 0f)
    Canvas(Modifier.width(46.dp).height(26.dp)) {
        val radius = size.height / 2
        drawRoundRect(lerp(Color(0xFFCFC3A3), Atlas.Moss, knob), cornerRadius = CornerRadius(radius))
        val x = radius + (size.width - 2 * radius) * knob
        drawCircle(lerp(Atlas.Vellum, Atlas.Gold, knob), radius - 3.dp.toPx(), Offset(x, radius))
    }
}

/** A row of mutually exclusive choices, such as the text size. */
@Composable
internal fun ChoiceRow(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    val ui = LocalUi.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (label, selected) ->
            val shape = RoundedCornerShape(18.dp)
            Text(label, Modifier.clip(shape).background(if (selected) Atlas.Moss else Color.Transparent)
                .border(1.dp, if (selected) Atlas.Moss else Atlas.InkSoft.copy(alpha = 0.4f), shape)
                .clickable(role = Role.RadioButton) {
                    ui.click()
                    onSelect(index)
                }.padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (selected) Atlas.Vellum else Atlas.InkText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * A painted map of an island, drawn from its real generated terrain: water, beaches, meadows, forests, uplands and
 * wetlands, shaded by height. Generated off the main thread and cached for the session.
 */
@Composable
internal fun IslandMiniMap(key: TerrainKey, modifier: Modifier = Modifier, highlight: List<Pair<Int, Int>> = emptyList()) {
    val terrain by produceState<TerrainMap?>(MiniMaps[key], key) {
        value = MiniMaps[key] ?: withContext(Dispatchers.Default) { IslandGenerator.generate(key) }.also { MiniMaps[key] = it }
    }
    Canvas(modifier) {
        drawRoundRect(Color(0xFF1F4E55), cornerRadius = CornerRadius(12.dp.toPx()))
        val map = terrain ?: return@Canvas
        val cell = minOf(size.width, size.height) / TerrainMap.Size
        val left = (size.width - cell * TerrainMap.Size) / 2
        val top = (size.height - cell * TerrainMap.Size) / 2
        for (z in TerrainMap.MinCell..TerrainMap.MaxCell) for (x in TerrainMap.MinCell..TerrainMap.MaxCell) {
            val tile = map.cellAt(x, z) ?: continue
            val water = map.watershed?.waterAt(x, z)
            val base = when {
                water != null -> Color(0xFF3F8C8C)
                !tile.land -> continue
                else -> when (tile.biome) {
                    Biome.Water -> continue
                    Biome.Beach -> Color(0xFFE2D2A2)
                    Biome.Meadow -> Color(0xFF9CBF6E)
                    Biome.PineForest -> Color(0xFF3F6E4C)
                    Biome.RockyUpland -> Color(0xFFA9A48C)
                    Biome.Wetland -> Color(0xFF6F9F7C)
                }
            }
            val shade = (tile.elevationTwice.coerceIn(0, 60) / 60f) * 0.22f
            drawRect(lerp(base, Color.White, shade), Offset(left + (x - TerrainMap.MinCell) * cell, top + (z - TerrainMap.MinCell) * cell),
                Size(cell + 0.6f, cell + 0.6f))
        }
        for ((x, z) in highlight) {
            diamond(Offset(left + (x - TerrainMap.MinCell + 0.5f) * cell, top + (z - TerrainMap.MinCell + 0.5f) * cell), cell * 1.6f, Atlas.Gold)
        }
    }
}

private val MiniMaps = mutableMapOf<TerrainKey, TerrainMap>()

/** Generates the gallery's mini-maps ahead of the menus that show them. Results are stored on the main thread. */
internal suspend fun warmMiniMaps(keys: List<TerrainKey>) {
    for (key in keys) {
        if (key in MiniMaps) continue
        val map = withContext(Dispatchers.Default) { IslandGenerator.generate(key) }
        MiniMaps[key] = map
    }
}

internal fun DrawScope.diamond(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.cornerDiamonds(color: Color, radius: Float, inset: Float = 0f) {
    val pad = inset + radius * 1.6f
    for (center in listOf(Offset(pad, pad), Offset(size.width - pad, pad), Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad))) {
        diamond(center, radius * 0.55f, color)
    }
}

@Composable
internal fun closeLabel(): String = stringResource(Res.string.close_panel)

/** A horizontal row that spaces its children for the action dock and keeps them inside the safe width. */
@Composable
internal fun DockRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.widthIn(max = 980.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp), content = content)
}
