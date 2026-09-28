package com.verlintas.baic2.eval

import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A deterministic model backend: each round plays one scripted event list. */
class ScriptedProvider(private val rounds: List<List<StreamEvent>>) : ChatProvider {

    val requests = mutableListOf<ChatRequest>()

    private var cursor = 0

    override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        requests += request
        val script = rounds.getOrElse(cursor) { emptyList() }
        cursor++
        script.forEach { emit(it) }
    }
}

/** A scripted tool: consecutive invocations consume [results], the last one repeats. */
data class FakeTool(
    val spec: ToolSpec,
    val results: List<ToolResult>,
)

/** One reproducible agent task with its expectations. */
data class Scenario(
    val id: String,
    val description: String,
    val mode: AppMode,
    val rounds: List<List<StreamEvent>>,
    val tools: List<FakeTool> = emptyList(),
    val expect: Expectations = Expectations(),
)

data class Expectations(
    val completed: Boolean = true,
    val finalTextContains: List<String> = emptyList(),
    val requiredToolCalls: List<String>? = null,
    val maxRounds: Int? = null,
    val failureKind: com.verlintas.baic2.core.engine.AgentFailure.Kind? = null,
)

data class ScenarioMetrics(
    val rounds: Int,
    val toolCalls: Int,
    val textChars: Int,
)

data class ScenarioResult(
    val id: String,
    val passed: Boolean,
    val failures: List<String>,
    val metrics: ScenarioMetrics,
    val finalText: String,
    val toolCallLog: List<String>,
    val requests: List<ChatRequest>,
)
