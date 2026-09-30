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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Five centred bars whose heights follow the microphone level. */
@Composable
fun VoiceLevelBars(
    level: Float,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    barCount: Int = 5,
) {
    val smooth by animateFloatAsState(
        targetValue = if (active) level.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "voice-level",
    )
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Canvas(modifier = modifier.height(16.dp)) {
        val barWidth = 3.dp.toPx()
        val gap = 3.dp.toPx()
        val total = barCount * barWidth + (barCount - 1) * gap
        var x = (size.width - total) / 2f
        val center = (barCount - 1) / 2f
        repeat(barCount) { index ->
            val distance = if (center == 0f) 0f else abs(index - center) / center
            val shape = 1f - 0.45f * distance
            val barHeight = (0.18f + 0.82f * smooth * shape) * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
            x += barWidth + gap
        }
    }
}
