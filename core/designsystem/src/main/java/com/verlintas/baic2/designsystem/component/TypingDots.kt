package com.verlintas.baic2.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/** Three dots that pulse in sequence while the model is thinking. */
@Composable
fun Baic2TypingDots(
    modifier: Modifier = Modifier,
    dotSize: androidx.compose.ui.unit.Dp = 7.dp,
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSize / 2),
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0f at 0 with LinearEasing
                        1f at 220 + index * 140
                        0f at 520 + index * 140
                        0f at 900 with LinearEasing
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot-$index",
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(0.72f + 0.28f * phase)
                    .alpha(0.35f + 0.65f * phase)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
