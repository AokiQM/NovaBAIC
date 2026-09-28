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

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Motion tokens in the spirit of M3 Expressive: spatial springs for
 * position/size, effects springs for color/alpha. Fast, functional, never
 * decorative.
 *
 * Note: material3 1.4.0 keeps MotionScheme internal, so these are ours.
 */
object Baic2Motion {

    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)

    fun <T> effectsDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

    fun <T> effectsSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
}
