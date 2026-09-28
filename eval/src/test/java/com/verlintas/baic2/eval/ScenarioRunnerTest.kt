package com.verlintas.baic2.eval

import com.verlintas.baic2.core.engine.AgentFailure
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ScenarioRunnerTest {

    private val runner = ScenarioRunner()

    @Test
    fun builtInScenariosAllPass() = runTest {
        val results = ScenarioCatalog.builtIn().map { runner.run(it) }

        val failures = results.filterNot { it.passed }
        assertTrue(
            failures.isEmpty(),
            "failing scenarios: " + failures.joinToString { "${it.id} -> ${it.failures}" },
        )
        assertTrue(results.all { it.metrics.rounds > 0 })
    }

    @Test
    fun failureRecoveryFeedsActionableErrorBackToTheModel() = runTest {
        val scenario = ScenarioCatalog.builtIn().first { it.id == "tool_failure_recovery" }

        val result = runner.run(scenario)

        assertTrue(result.passed)
        assertEquals(listOf("set_volume", "set_volume"), result.toolCallLog)
        val secondRound = result.requests[1]
        val lastTool = secondRound.messages.last { it.role == ChatRole.TOOL }
        assertTrue(
            lastTool.content.contains("permission missing"),
            "the model must see the actionable failure, got: ${lastTool.content}",
        )
    }

    @Test
    fun metricsExposeCost() = runTest {
        val scenario = ScenarioCatalog.builtIn().first { it.id == "tool_then_answer" }

        val result = runner.run(scenario)

        assertEquals(2, result.metrics.rounds)
        assertEquals(1, result.metrics.toolCalls)
        assertTrue(result.metrics.textChars > 0)
    }

    @Test
    fun graderCatchesMissingExpectations() = runTest {
        val scenario = Scenario(
            id = "intentionally_wrong",
            description = "model answers 'no' but the scenario expects 'yes'",
            mode = AppMode.CHAT,
            rounds = listOf(listOf(StreamEvent.TextDelta("no"), StreamEvent.Done)),
            expect = Expectations(finalTextContains = listOf("yes")),
        )

        val result = runner.run(scenario)

        assertFalse(result.passed)
        assertTrue(result.failures.single().contains("missing 'yes'"))
    }

    @Test
    fun graderCatchesWrongToolOrder() = runTest {
        val scenario = Scenario(
            id = "wrong_order",
            description = "expects a tool that is never called",
            mode = AppMode.MAX,
            rounds = listOf(
                listOf(StreamEvent.TextDelta("I will not use tools"), StreamEvent.Done),
            ),
            tools = listOf(
                FakeTool(
                    ToolSpec(name = "read_weather", description = "w", readOnly = true),
                    listOf(ToolResult.Success("x")),
                ),
            ),
            expect = Expectations(requiredToolCalls = listOf("read_weather")),
        )

        val result = runner.run(scenario)

        assertFalse(result.passed)
    }

    @Test
    fun budgetScenarioIsMeasuredNotHung() = runTest {
        val scenario = ScenarioCatalog.builtIn().first { it.id == "budget_guard" }

        val result = runner.run(scenario)

        assertTrue(result.passed)
        assertEquals(
            com.verlintas.baic2.core.model.RunBudget.forMode(AppMode.ACT).maxRounds,
            result.metrics.rounds,
        )
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun scriptedToolCallArgumentsReachTheRunner() = runTest {
        val scenario = ScenarioCatalog.builtIn().first { it.id == "tool_then_answer" }

        val result = runner.run(scenario)

        val assistant = result.requests.last().messages
            .first { it.role == ChatRole.ASSISTANT && it.toolCalls.isNotEmpty() }
        val call = assistant.toolCalls.first()
        assertTrue(call.argumentsJson.contains("SF"))
    }
}
