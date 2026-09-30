/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gemini/Codex-flavoured ambience: slowly drifting gradient light plus
 * twinkling star particles. Everything is drawn procedurally, so it follows
 * the theme (tint via [Baic2Aurora.colors]), costs no assets and loops forever.
 */
object Baic2Aurora {
    val colors: List<Color> = listOf(
        Color(0xFFC084FC),
        Color(0xFF7AA2F7),
        Color(0xFF3DDCC4),
    )
    val base: Color = Color(0xFF121218)
}

private data class Spark(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float,
    val speed: Float,
    val drift: Float,
    val star: Boolean,
)

@Composable
private fun rememberSparks(count: Int): List<Spark> = remember(count) {
    val random = Random(20260930)
    List(count) {
        Spark(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 0.5f + random.nextFloat() * 1.5f,
            phase = random.nextFloat(),
            speed = 0.45f + random.nextFloat() * 1.1f,
            drift = 0.02f + random.nextFloat() * 0.07f,
            star = random.nextFloat() < 0.45f,
        )
    }
}

/** True while the system animator duration scale is above zero. */
@Composable
fun rememberBaic2AnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

@Composable
private fun rememberAuroraPhase(durationMillis: Int = 16_000): Float {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "aurora-phase",
    )
    return phase
}

/** Shared four-point sparkle path, normalized to a 1-unit radius. */
internal fun auroraStarPath(): Path = Path().apply {
    moveTo(0f, -1f)
    quadraticBezierTo(0.14f, -0.14f, 1f, 0f)
    quadraticBezierTo(0.14f, 0.14f, 0f, 1f)
    quadraticBezierTo(-0.14f, 0.14f, -1f, 0f)
    quadraticBezierTo(-0.14f, -0.14f, 0f, -1f)
    close()
}

/**
 * Draws the ambience behind [content]. Clip is applied via [shape]; the
 * surface itself stays transparent so callers keep their own background.
 */
@Composable
fun AuroraSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: List<Color> = Baic2Aurora.colors,
    particleCount: Int = 18,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val phase = rememberAuroraPhase()
    val animationsEnabled = rememberBaic2AnimationsEnabled()
    val sparks = rememberSparks(particleCount)
    val star = remember { auroraStarPath() }
    val t = if (animationsEnabled) phase else 0.37f

    Box(modifier = modifier.clip(shape)) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.minDimension / 220f

            colors.forEachIndexed { index, color ->
                val speed = 0.55f + index * 0.17f
                val angle = (t * speed + index * 0.33f) * (2f * PI.toFloat())
                val cx = size.width * (0.5f + 0.34f * cos(angle))
                val cy = size.height * (0.5f + 0.28f * sin(angle * 1.23f + index))
                val radius = size.minDimension * (0.62f + 0.12f * sin(angle * 0.8f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.5f * intensity),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                    blendMode = BlendMode.Plus,
                )
            }

            sparks.forEach { spark ->
                val cycle = (t * spark.speed + spark.phase) % 1f
                val twinkle = sin(cycle * PI.toFloat())
                val alpha = (0.06f + 0.94f * twinkle * twinkle) * intensity
                val y = ((spark.y - t * spark.drift) % 1f + 1f) % 1f
                val center = Offset(spark.x * size.width, y * size.height)
                if (spark.star) {
                    val scale = spark.radius * unit * (1.1f + 1.6f * twinkle)
                    withTransform({
                        translate(center.x, center.y)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        drawPath(star, color = Color.White, alpha = alpha)
                    }
                } else {
                    drawCircle(
                        color = Color.White,
                        radius = spark.radius * unit * (0.6f + 0.9f * twinkle),
                        center = center,
                        alpha = alpha,
                    )
                }
            }
        }
        content()
    }
}

/**
 * Small live orb for "thinking" states: gradient light inside a dark glass
 * ball with a few twinkling sparks.
 */
@Composable
fun ThinkingOrb(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Baic2Aurora.base)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
    ) {
        AuroraSurface(
            modifier = Modifier.matchParentSize(),
            shape = CircleShape,
            particleCount = 7,
            intensity = 1.3f,
        )
    }
}

/**
 * Sweeping highlight for a single text label (e.g. "Thinking 3s"), roughly in
 * the spirit of the Gemini shimmer. Falls back to a static brush when system
 * animations are disabled.
 */
@Composable
fun shimmerTextBrush(
    colors: List<Color> = listOf(
        Color(0xFF9BB8FF),
        Color.White,
        Color(0xFF9BB8FF),
    ),
    durationMillis: Int = 2_600,
): Brush {
    val phase = rememberAuroraPhase(durationMillis)
    val animationsEnabled = rememberBaic2AnimationsEnabled()
    val t = if (animationsEnabled) phase else 0.5f
    val start = -180f + t * 560f
    return Brush.linearGradient(
        colors = colors,
        start = Offset(start, 0f),
        end = Offset(start + 220f, 0f),
    )
}
