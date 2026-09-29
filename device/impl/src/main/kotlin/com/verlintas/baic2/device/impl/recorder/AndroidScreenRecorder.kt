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

package com.verlintas.baic2.device.impl.recorder

import com.verlintas.baic2.device.api.RecordingResult
import com.verlintas.baic2.device.api.ScreenRecorderBridge
import com.verlintas.baic2.device.impl.projection.ProjectionHolder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AndroidScreenRecorder @Inject constructor() : ScreenRecorderBridge {

    override val ready: StateFlow<Boolean> = ProjectionHolder.ready

    override suspend fun record(durationMs: Long): RecordingResult =
        when (val outcome = ProjectionHolder.record(durationMs)) {
            is ProjectionHolder.RecordOutcome.Recorded -> RecordingResult.Recorded(
                filePath = outcome.filePath,
                durationMs = outcome.durationMs,
                sizeBytes = outcome.sizeBytes,
            )

            is ProjectionHolder.RecordOutcome.Unavailable -> RecordingResult.Unavailable(outcome.reason)
            is ProjectionHolder.RecordOutcome.Failed -> RecordingResult.Failed(outcome.reason)
        }
}
