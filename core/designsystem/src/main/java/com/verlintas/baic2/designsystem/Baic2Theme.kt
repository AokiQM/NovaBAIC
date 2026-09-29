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

package com.verlintas.baic2.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.verlintas.baic2.core.model.AccentColor

/**
 * BAIC2 theme. Dark-first: dark is the primary surface language, light is a
 * fully supported counterpart built from the same tokens.
 *
 * Note: material3 1.4.0 keeps the M3 Expressive theme/motion APIs internal,
 * so motion comes from [Baic2Motion] instead of MotionScheme. Revisit when
 * material3 1.5 goes stable.
 */
@Composable
fun Baic2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = baic2ColorScheme(dark = darkTheme, accent = accent),
        shapes = Baic2Shapes,
        typography = Baic2Typography,
        content = content,
    )
}
