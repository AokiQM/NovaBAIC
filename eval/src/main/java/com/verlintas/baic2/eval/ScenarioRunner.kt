package com.verlintas.baic2.eval

import com.verlintas.baic2.core.engine.AgentEvent
import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.ConfirmationGate
import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlinx.coroutines.flow.toList

/**
 * Runs a [Scenario] through the real [AgentLoop] with a scripted provider and
 * scripted tools, then grades the outcome. Metrics (rounds, tool calls, text
 * size) are the currency for proving capability changes.
 */
class ScenarioRunner(
    private val config: ProviderConfig = ProviderConfig(
        provider = ProviderId.OPENAI_COMPATIBLE,
        baseUrl = "http://127.0.0.1/mock/v1",
        apiKey = "scenario",
        model = "scenario-model",
    ),
) {

    suspend fun run(scenario: Scenario): ScenarioResult {
        val provider = ScriptedProvider(scenario.rounds)
        val resultByTool = scenario.tools.associate { tool -> tool.spec.name to tool.results.toMutableList() }
        val callLog = mutableListOf<String>()

        val catalog = object : ToolCatalog {
            override fun specs(mode: AppMode): List<ToolSpec> = scenario.tools.map { it.spec }

            override fun find(name: String): ToolSpec? =
                scenario.tools.firstOrNull { it.spec.name == name }?.spec
        }

        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = catalog,
            toolRunner = { call: ToolCall, _ ->
                callLog += call.name
                val queue = resultByTool[call.name]
                when {
                    queue.isNullOrEmpty() -> ToolResult.Failure("unknown tool ${call.name}")
                    queue.size == 1 -> queue[0]
                    else -> queue.removeAt(0)
                }
            },
            confirmationGate = ConfirmationGate { true },
        )

        val events = loop.run(
            config = config,
            mode = scenario.mode,
            history = listOf(ChatMessage(role = ChatRole.USER, content = scenario.description)),
        ).toList()

        return grade(scenario, events, callLog, provider.requests)
    }

    private fun grade(
        scenario: Scenario,
        events: List<AgentEvent>,
        toolCallLog: List<String>,
        requests: List<com.verlintas.baic2.core.model.ChatRequest>,
    ): ScenarioResult {
        val failures = mutableListOf<String>()
        val completed = events.lastOrNull() == AgentEvent.Completed
        val finalText = events.filterIsInstance<AgentEvent.AssistantMessage>()
            .lastOrNull { it.message.toolCalls.isEmpty() }
            ?.message?.content
            .orEmpty()
        val rounds = events.filterIsInstance<AgentEvent.RoundStarted>().size
        val toolCalls = events.filterIsInstance<AgentEvent.ToolCallFinished>().size

        if (completed != scenario.expect.completed) {
            failures += "completed expected=${scenario.expect.completed} actual=$completed"
        }
        scenario.expect.finalTextContains.forEach { needle ->
            if (!finalText.contains(needle)) failures += "final text missing '$needle' (got: $finalText)"
        }
        scenario.expect.requiredToolCalls?.let { expected ->
            if (toolCallLog != expected) {
                failures += "tool calls expected=$expected actual=$toolCallLog"
            }
        }
        scenario.expect.maxRounds?.let { max ->
            if (rounds > max) failures += "rounds $rounds exceeded max $max"
        }
        scenario.expect.failureKind?.let { expectedKind ->
            val actual = events.filterIsInstance<AgentEvent.Failed>().lastOrNull()?.error?.kind
            if (actual != expectedKind) failures += "failure kind expected=$expectedKind actual=$actual"
        }

        return ScenarioResult(
            id = scenario.id,
            passed = failures.isEmpty(),
            failures = failures,
            metrics = ScenarioMetrics(rounds = rounds, toolCalls = toolCalls, textChars = finalText.length),
            finalText = finalText,
            toolCallLog = toolCallLog,
            requests = requests,
        )
    }
}
