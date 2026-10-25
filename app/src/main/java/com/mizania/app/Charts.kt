package com.mizania.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Slice(val label: String, val value: Double, val color: Color)

/** Ring chart with a total in the middle and a legend underneath. */
@Composable
fun DonutChart(slices: List<Slice>, centerTop: String, centerBottom: String, size: Dp = 190.dp, showLegend: Boolean = true) {
    val total = slices.sumOf { it.value }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val grow = appearProgress(100, 1000)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = this.size.minDimension * 0.11f
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (total > 0) {
                    var start = -90f
                    val gap = if (slices.count { it.value > 0 } > 1) 2.5f else 0f
                    for (s in slices) {
                        if (s.value <= 0) continue
                        val sweep = (s.value / total * 360.0).toFloat() * grow
                        drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize,
                            style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTop, fontWeight = FontWeight.SemiBold, fontSize = (size.value / 10).sp, fontFamily = Ui.numbers)
                Text(centerBottom, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showLegend && total > 0) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                slices.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                        Text(s.label, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${(s.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(s.value), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * Daily bars with an optional dashed limit line. Bars over the limit turn red;
 * [highlight] marks today. Labels are drawn under the bars, every [labelEvery]th one.
 */
@Composable
fun BarChart(values: DoubleArray, labels: List<String>, limit: Double?, highlight: Int = -1, labelEvery: Int = 1) {
    val normal = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val today = MaterialTheme.colorScheme.tertiary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val maxV = maxOf(values.maxOrNull() ?: 0.0, limit ?: 0.0).let { if (it <= 0) 1.0 else it * 1.1 }
    val grow = appearProgress(100, 900)

    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            for (i in values.indices) {
                val v = values[i]
                val h = (v / maxV * size.height).toFloat() * grow
                // Index 0 sits on the right: the chart reads right-to-left like the rest of the app.
                val x = size.width - (i + 1) * slot + (slot - barW) / 2
                val c = when {
                    limit != null && v > limit -> over
                    i == highlight -> today
                    else -> normal
                }
                if (h > 0) {
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(4.dp.toPx()))
                }
            }
            if (limit != null && limit > 0) {
                val y = (size.height - limit / maxV * size.height).toFloat()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        // In an RTL Row the first child is on the right, matching index 0 above.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % labelEvery == 0) l else "",
                    Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
