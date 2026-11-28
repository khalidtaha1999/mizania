package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The sticker look: a flat fill, a 2dp outline, and a hard shadow straight down.
 * Lift 0 = outline only, for lists and quieter cards.
 */
fun Modifier.sticker(
    fill: Color, edge: Color, shadow: Color,
    radius: Dp = 24.dp, lift: Dp = 4.dp, outline: Dp = 2.dp,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .padding(bottom = lift)
        .drawBehind {
            if (lift > 0.dp) drawRoundRect(shadow, topLeft = Offset(0f, lift.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()))
        }
        .clip(shape).background(fill).border(outline, edge, shape)
}

/** The sticker in the theme's colors. */
@Composable
fun Modifier.sticker(fill: Color = Ui.p.raised, radius: Dp = 24.dp, lift: Dp = 4.dp): Modifier =
    sticker(fill, Ui.p.edge, Ui.p.shadow, radius, lift)

/** A card with the sticker look; clickable when [onClick] is given. */
@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    fill: Color = Ui.p.raised,
    lift: Dp = 4.dp,
    radius: Dp = 24.dp,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().sticker(fill, radius, lift)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** A heading with an optional action at the end (a "see all" link and the like). */
@Composable
fun StickerHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 8.dp).size(22.dp))
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

/** A budget line's icon on a pastel tile of its own color. Offered emoji become line icons. */
@Composable
fun LineIcon(icon: String, color: Color, size: Dp = 40.dp) {
    val p = Ui.p
    val vec = AppIcons.forEmoji(icon)
    val unknown = icon.isBlank() || icon == "❔"
    val base = if (unknown) p.pomegranate else color
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(p.pastel(base)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            unknown -> Text("؟", fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.ExtraBold, color = p.deep(base))
            vec != null -> Icon(vec, null, tint = p.deep(base), modifier = Modifier.size(size * 0.55f))
            else -> Text(icon, fontSize = (size.value * 0.46f).sp)
        }
    }
}

/** A plain vector on a pastel tile. */
@Composable
fun VecTile(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    val p = Ui.p
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(p.pastel(color)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = p.deep(color), modifier = Modifier.size(size * 0.55f)) }
}

/** A pill-shaped choice: a budget line to file a spend under, a filter, and so on. */
@Composable
fun PillButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    fill: Color = Ui.p.raised, selected: Boolean = false, leading: (@Composable () -> Unit)? = null,
) {
    val p = Ui.p
    val bg = if (selected) p.pine else fill
    val fg = if (selected) p.onPine else if (fill == p.raised) MaterialTheme.colorScheme.onSurface else Color(0xFF1E1B3A)
    Row(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(bg)
            .border(2.dp, p.edge, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A small rounded label: "paid", "12 left", "over by 3". */
@Composable
fun Tag(text: String, color: Color) {
    val p = Ui.p
    Text(
        text,
        Modifier.clip(RoundedCornerShape(10.dp)).background(p.pastel(color)).padding(horizontal = 9.dp, vertical = 3.dp),
        color = p.deep(color), fontFamily = Ui.numbers, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
    )
}

/** Thin progress bar on a track, in the sticker style. */
@Composable
fun StickerBar(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(height / 2)).background(color)
        )
    }
}

/** A block of rows on one outlined card, with thin dividers. */
@Composable
fun StickerList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().sticker(lift = 0.dp), content = content)
}
