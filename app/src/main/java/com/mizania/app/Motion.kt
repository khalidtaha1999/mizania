package com.mizania.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Counts up to [value] the first time it shows, then glides to each new value. */
@Composable
fun animatedNumber(value: Double, durationMs: Int = 900): Double {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.animateTo(value.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return anim.value.toDouble()
}

/** An amount that counts up, in the numbers face, with a smaller currency after it. */
@Composable
fun AnimatedAmount(
    v: Double, size: TextUnit, color: Color = Color.Unspecified,
    sign: String = "", weight: FontWeight = FontWeight.SemiBold,
) {
    val shown = animatedNumber(v)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(sign + String.format(Locale.US, "%,.2f", shown), fontFamily = Ui.numbers, fontSize = size,
            fontWeight = weight, color = color, maxLines = 1)
        Text(" " + currency(), fontSize = size * 0.5f,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = (size.value * 0.12f).dp))
    }
}

/** 0 → 1 once when first shown: drives bars and rings filling in. */
@Composable
fun appearProgress(delayMs: Int = 0, durationMs: Int = 800): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return a.value
}

/** Shrinks a little while pressed, like a real button. Pass the same source to clickable(). */
fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}
