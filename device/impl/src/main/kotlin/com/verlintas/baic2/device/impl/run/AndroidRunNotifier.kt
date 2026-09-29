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

package com.verlintas.baic2.device.impl.run

import android.content.Context
import com.verlintas.baic2.device.api.RunNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidRunNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : RunNotifier {

    override fun setStopHandler(handler: (() -> Unit)?) {
        RunControl.stopHandler = handler
    }

    override fun startRunning(title: String, runId: Long?) {
        RunService.start(context, title, runId)
    }

    override fun stopRunning() {
        RunService.stop(context)
    }
}
