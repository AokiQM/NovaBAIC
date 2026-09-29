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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.verlintas.baic2.core.model.AccentColor

// Console-inspired palette: near-black layered surfaces, low-contrast
// outlines, one cool blue accent. Codex-like density, zero decoration.

private val BlueDark = Color(0xFF7AA2F7)
private val BlueLight = Color(0xFF2E5FD0)

val Baic2DarkColorScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF071426),
    primaryContainer = Color(0xFF1B2A4A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF2E5FD0),
    secondary = Color(0xFF9AA3B2),
    onSecondary = Color(0xFF11151C),
    secondaryContainer = Color(0xFF232A36),
    onSecondaryContainer = Color(0xFFDCE2EC),
    tertiary = Color(0xFF7DCFFF),
    onTertiary = Color(0xFF06202B),
    tertiaryContainer = Color(0xFF12303D),
    onTertiaryContainer = Color(0xFFCDEBFA),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFE8E8EA),
    surface = Color(0xFF101013),
    onSurface = Color(0xFFE8E8EA),
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = Color(0xFFA0A0A8),
    surfaceTint = BlueDark,
    surfaceBright = Color(0xFF26262C),
    surfaceDim = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF151519),
    surfaceContainerHigh = Color(0xFF1A1A1F),
    surfaceContainerHighest = Color(0xFF202026),
    surfaceContainerLow = Color(0xFF121216),
    surfaceContainerLowest = Color(0xFF0E0E11),
    inverseSurface = Color(0xFFE8E8EA),
    inverseOnSurface = Color(0xFF1A1A1E),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFDAD8),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF26262C),
    scrim = Color(0xFF000000),
)

val Baic2LightColorScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A1B3D),
    inversePrimary = BlueDark,
    secondary = Color(0xFF5A606C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6EC),
    onSecondaryContainer = Color(0xFF171B22),
    tertiary = Color(0xFF0F7C8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCECF1),
    onTertiaryContainer = Color(0xFF04242B),
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF1A1A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFEFF0F3),
    onSurfaceVariant = Color(0xFF55555E),
    surfaceTint = BlueLight,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFECECEF),
    surfaceContainer = Color(0xFFF4F4F6),
    surfaceContainerHigh = Color(0xFFEEEEF1),
    surfaceContainerHighest = Color(0xFFE8E8EC),
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2A2A30),
    inverseOnSurface = Color(0xFFF2F2F4),
    error = Color(0xFFD93A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFC4C4CC),
    outlineVariant = Color(0xFFE0E0E6),
    scrim = Color(0xFF000000),
)


/** Per-accent palette: dark primaries are pastel, light primaries are deep. */
data class AccentSpec(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
)

fun accentSpec(accent: AccentColor): AccentSpec = when (accent) {
    AccentColor.ORANGE -> AccentSpec(
        Color(0xFFFFA07A), Color(0xFF2A120A), Color(0xFF4A2317), Color(0xFFFFDCCB),
        Color(0xFFC2410C), Color(0xFFFFFFFF), Color(0xFFFFE0D3), Color(0xFF3A1608),
    )
    AccentColor.RED -> AccentSpec(
        Color(0xFFFF8A84), Color(0xFF2A0E0E), Color(0xFF4A1B1B), Color(0xFFFFDAD7),
        Color(0xFFC62F2F), Color(0xFFFFFFFF), Color(0xFFFFDAD7), Color(0xFF410002),
    )
    AccentColor.PINK -> AccentSpec(
        Color(0xFFFF9FCE), Color(0xFF2A0E1D), Color(0xFF4A1B36), Color(0xFFFFD9EC),
        Color(0xFFC2187E), Color(0xFFFFFFFF), Color(0xFFFFD9EC), Color(0xFF3B0025),
    )
    AccentColor.INDIGO -> AccentSpec(
        Color(0xFF9FAEFF), Color(0xFF12173A), Color(0xFF1E2853), Color(0xFFDDE1FF),
        Color(0xFF3F51B5), Color(0xFFFFFFFF), Color(0xFFDDE1FF), Color(0xFF0A1A54),
    )
    AccentColor.BLUE -> AccentSpec(
        Color(0xFF7AA2F7), Color(0xFF071426), Color(0xFF1B2A4A), Color(0xFFD6E4FF),
        Color(0xFF2E5FD0), Color(0xFFFFFFFF), Color(0xFFDCE6FF), Color(0xFF0A1B3D),
    )
    AccentColor.PURPLE -> AccentSpec(
        Color(0xFFCBA6FF), Color(0xFF1F0F3A), Color(0xFF35215A), Color(0xFFEDDCFF),
        Color(0xFF7A3FE0), Color(0xFFFFFFFF), Color(0xFFEDDCFF), Color(0xFF2A0B54),
    )
    AccentColor.GREEN -> AccentSpec(
        Color(0xFF93DF9B), Color(0xFF0C2412), Color(0xFF173D22), Color(0xFFD8F0DA),
        Color(0xFF2E7D32), Color(0xFFFFFFFF), Color(0xFFD8F0DA), Color(0xFF072C10),
    )
    AccentColor.TEAL -> AccentSpec(
        Color(0xFF6FDCD0), Color(0xFF0A2320), Color(0xFF123A36), Color(0xFFD0F0EC),
        Color(0xFF00796B), Color(0xFFFFFFFF), Color(0xFFD0F0EC), Color(0xFF00251F),
    )
}

/** Base scheme with accent roles applied; everything else keeps Baic2 tokens. */
fun baic2ColorScheme(dark: Boolean, accent: AccentColor): ColorScheme {
    val spec = accentSpec(accent)
    return if (dark) {
        Baic2DarkColorScheme.copy(
            primary = spec.darkPrimary,
            onPrimary = spec.darkOnPrimary,
            primaryContainer = spec.darkContainer,
            onPrimaryContainer = spec.darkOnContainer,
            tertiary = spec.darkPrimary,
            onTertiary = spec.darkOnPrimary,
            tertiaryContainer = spec.darkContainer,
            onTertiaryContainer = spec.darkOnContainer,
            surfaceTint = spec.darkPrimary,
        )
    } else {
        Baic2LightColorScheme.copy(
            primary = spec.lightPrimary,
            onPrimary = spec.lightOnPrimary,
            primaryContainer = spec.lightContainer,
            onPrimaryContainer = spec.lightOnContainer,
            tertiary = spec.lightPrimary,
            onTertiary = spec.lightOnPrimary,
            tertiaryContainer = spec.lightContainer,
            onTertiaryContainer = spec.lightOnContainer,
            surfaceTint = spec.lightPrimary,
        )
    }
}
