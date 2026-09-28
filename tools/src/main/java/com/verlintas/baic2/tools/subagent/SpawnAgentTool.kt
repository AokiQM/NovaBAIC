package com.verlintas.baic2.tools.subagent

import com.verlintas.baic2.core.engine.AgentEvent
import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.ConfirmationGate
import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.engine.ToolRunner
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.RunBudget
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.core.network.provider.ProviderFactory
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import javax.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Runs a scoped child agent with its own budget and context window, then
 * returns a compact report. Sub-agents cannot spawn further sub-agents, which
 * keeps depth at one and budgets predictable.
 */
class SpawnAgentTool @Inject constructor(
    private val providerFactory: ProviderFactory,
    private val toolCatalog: dagger.Lazy<ToolCatalog>,
    private val toolRunner: dagger.Lazy<ToolRunner>,
    private val confirmationGate: ConfirmationGate,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "spawn_agent",
        description = "Delegate a self-contained task to a sub-agent with its own context and budget. " +
            "mode=research (read-only tools, default) or mode=max (full tools). Returns the sub-agent's report. " +
            "Use several spawn_agent calls in one round for parallel work.",
        parametersJson = """{"type":"object","properties":{"task":{"type":"string"},"mode":{"type":"string","enum":["research","max"]}},"required":["task"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val task = (arguments["task"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'task' argument")
        val config = context.run.config
            ?: return ToolResult.Failure("Sub-agents need an active provider configuration.")
        val mode = when ((arguments["mode"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
            "max" -> AppMode.MAX
            else -> AppMode.CHAT_PLUS
        }
        val budget = if (mode == AppMode.CHAT_PLUS) {
            RunBudget(maxRounds = 8, maxToolCalls = 20, maxWallClockMs = 300_000)
        } else {
            RunBudget(maxRounds = 16, maxToolCalls = 40, maxWallClockMs = 600_000)
        }

        val excluded = setOf("spawn_agent")
        val parentCatalog = toolCatalog.get()
        val childCatalog = object : ToolCatalog {
            override fun specs(mode: AppMode) =
                parentCatalog.specs(mode).filter { it.name !in excluded }

            override fun find(name: String) =
                if (name in excluded) null else parentCatalog.find(name)
        }

        val loop = AgentLoop(
            providerFactory = { providerFactory.create(it) },
            toolCatalog = childCatalog,
            toolRunner = toolRunner.get(),
            confirmationGate = ConfirmationGate { false },
        )

        val events = try {
            loop.run(
                config = config,
                mode = mode,
                history = listOf(
                    com.verlintas.baic2.core.model.ChatMessage(
                        role = com.verlintas.baic2.core.model.ChatRole.USER,
                        content = task,
                    ),
                ),
            ).toList()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return ToolResult.Failure("Sub-agent crashed: ${e.message}")
        }

        val failure = events.filterIsInstance<AgentEvent.Failed>().lastOrNull()
        if (failure != null) {
            return ToolResult.Failure("Sub-agent failed (${failure.error.kind}): ${failure.error.message}")
        }
        val report = events.filterIsInstance<AgentEvent.AssistantMessage>()
            .lastOrNull { it.message.toolCalls.isEmpty() }
            ?.message?.content
            ?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Sub-agent finished without an answer.")
        val toolNames = events.filterIsInstance<AgentEvent.ToolCallFinished>()
            .map { it.call.name }
            .distinct()
        val rounds = events.filterIsInstance<AgentEvent.RoundStarted>().size

        return ToolResult.Success(
            buildString {
                append("Sub-agent report (${if (mode == AppMode.CHAT_PLUS) "research" else "max"}, ")
                append("$rounds round(s)")
                if (toolNames.isNotEmpty()) append(", tools: ${toolNames.joinToString()}")
                append("):\n")
                append(report.take(3_000))
            },
        )
    }
}
