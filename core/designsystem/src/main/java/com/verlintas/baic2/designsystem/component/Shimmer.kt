package com.verlintas.baic2.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Sweeping highlight used by skeleton placeholders. */
@Composable
fun Modifier.shimmerBackground(shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )

    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceBright

    return this
        .clip(shape)
        .drawWithCache {
            val width = size.width.coerceAtLeast(1f)
            val travelStart = -width * 2f + progress * width * 4f
            val brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(travelStart, 0f),
                end = Offset(travelStart + width, size.height.coerceAtLeast(1f)),
            )
            onDrawBehind { drawRect(brush) }
        }
}

@Composable
fun Baic2Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(modifier = modifier.shimmerBackground(shape))
}
