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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.R

/** Compact monospace badge for the current autonomy mode. */
@Composable
fun Baic2ModeChip(
    mode: AppMode,
    modifier: Modifier = Modifier,
) {
    val container: Color
    val content: Color
    when (mode) {
        AppMode.CHAT -> {
            container = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        AppMode.CHAT_PLUS -> {
            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
            content = MaterialTheme.colorScheme.tertiary
        }
        AppMode.ACT -> {
            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            content = MaterialTheme.colorScheme.primary
        }
        AppMode.MAX -> {
            container = MaterialTheme.colorScheme.primary
            content = MaterialTheme.colorScheme.onPrimary
        }
    }
    val label = when (mode) {
        AppMode.CHAT -> stringResource(R.string.mode_chat)
        AppMode.CHAT_PLUS -> stringResource(R.string.mode_chat_plus)
        AppMode.ACT -> stringResource(R.string.mode_act)
        AppMode.MAX -> stringResource(R.string.mode_max)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = Baic2Mono.label,
            color = content,
        )
    }
}
