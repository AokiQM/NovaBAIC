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

package com.verlintas.baic2.tools.apps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResolverTest {

    @Test
    fun exactLabelAndPackageScoreBest() {
        assertEquals(0, AppResolver.scoreMatch("Settings", "com.android.settings", "settings"))
        assertEquals(0, AppResolver.scoreMatch("Settings", "com.android.settings", "com.android.settings"))
    }

    @Test
    fun partialLabelAndPackageMatch() {
        assertEquals(1, AppResolver.scoreMatch("YouTube", "com.google.android.youtube", "you"))
        assertEquals(3, AppResolver.scoreMatch("WeChat", "com.tencent.mm", "tencent"))
    }

    @Test
    fun typoInLabelStillResolves() {
        assertEquals(5, AppResolver.scoreMatch("Calculator", "com.android.calculator2", "calculater"))
    }

    @Test
    fun unrelatedQueriesDoNotMatch() {
        assertNull(AppResolver.scoreMatch("Camera", "com.android.camera", "zzzz"))
    }
}
