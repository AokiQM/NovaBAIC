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

package com.verlintas.baic2.tools

import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.engine.ToolRunContext
import com.verlintas.baic2.core.engine.ToolRunner
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Singleton
class ToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards DeviceTool>,
) : ToolCatalog {

    private val byName: Map<String, DeviceTool> = tools.associateBy { it.spec.name }

    override fun specs(mode: AppMode): List<ToolSpec> = when (mode) {
        AppMode.CHAT -> emptyList()
        AppMode.CHAT_PLUS -> byName.values.filter { it.spec.readOnly }.map { it.spec }
        AppMode.ACT, AppMode.MAX -> byName.values.map { it.spec }
    }

    override fun find(name: String): ToolSpec? = byName[name]?.spec

    fun tool(name: String): DeviceTool? = byName[name]

    val toolNames: List<String> get() = byName.keys.sorted()
}

@Singleton
class DeviceToolRunner @Inject constructor(
    private val registry: ToolRegistry,
    private val context: ToolContext,
    private val json: Json,
) : ToolRunner {

    override suspend fun run(call: ToolCall, run: ToolRunContext): ToolResult {
        val tool = registry.tool(call.name)
            ?: return ToolResult.Failure("Unknown tool '${call.name}'. Use one of: ${registry.toolNames.joinToString()}")
        val arguments = runCatching {
            json.parseToJsonElement(call.argumentsJson) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        return try {
            tool.execute(arguments, context.copy(run = run))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.Failure("${call.name} crashed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
