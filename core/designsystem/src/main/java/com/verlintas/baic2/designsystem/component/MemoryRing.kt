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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The biomimetic-memory emblem: a ring of rainbow dots that rotates and
 * breathes. Each dot is one association; the ring never starts or stops, it
 * just keeps circulating, exactly like recall does. Rotation and breathing use
 * full-cycle periodic functions so the loop is seamless, and the whole thing
 * freezes into a static rainbow ring when animations are disabled.
 */
@Composable
fun MemoryRing(
    modifier: Modifier = Modifier,
    dotCount: Int = 12,
    rotationMillis: Int = 16_000,
    breathMillis: Int = 3_200,
) {
    val animationsEnabled = rememberBaic2AnimationsEnabled()
    val angle: Float
    val breath: Float
    if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "memory-ring")
        angle = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(rotationMillis, easing = LinearEasing)),
            label = "memory-ring-rotation",
        ).value
        breath = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(breathMillis, easing = LinearEasing)),
            label = "memory-ring-breath",
        ).value
    } else {
        angle = 0f
        breath = 0f
    }

    Canvas(modifier) {
        val dotRadius = size.minDimension * 0.085f
        val ringRadius = size.minDimension / 2f - dotRadius * 1.9f
        val center = Offset(size.width / 2f, size.height / 2f)
        val twoPi = (2f * PI).toFloat()
        for (index in 0 until dotCount) {
            val hue = index * 360f / dotCount
            val theta = Math.toRadians((angle + hue).toDouble())
            val position = Offset(
                center.x + ringRadius * cos(theta).toFloat(),
                center.y + ringRadius * sin(theta).toFloat(),
            )
            val pulse = 0.72f + 0.28f * sin(breath + index * twoPi / dotCount)
            val color = Color.hsv(hue, 0.60f, 1f)
            drawCircle(
                color = color.copy(alpha = 0.26f * pulse),
                radius = dotRadius * 2.0f * pulse,
                center = position,
            )
            drawCircle(
                color = color.copy(alpha = 0.95f),
                radius = dotRadius * pulse,
                center = position,
            )
        }
    }
}
